package com.pingwei.lengkubao.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import androidx.room.withTransaction
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.fiscal.FiscalYearManager
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.data.db.entity.PackagingType
import com.pingwei.lengkubao.data.db.entity.SyncAppliedOp
import com.pingwei.lengkubao.data.db.entity.SyncDeviceCursor
import com.pingwei.lengkubao.data.db.entity.SyncLocalOpLog
import com.pingwei.lengkubao.service.PreSaleSyncApplier
import com.pingwei.lengkubao.service.StockService
import com.pingwei.lengkubao.utils.SourceRecordIdUtils
import com.pingwei.lengkubao.utils.Constant
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pingwei.lengkubao.sync.mdns.MdnsDeviceDiscovery
import com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

data class PackagingSyncResult(
    val success: Boolean,
    val duplicateNotice: String? = null,
    val errorMessage: String? = null,
)

class TcpSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    // 全量反向同步缓冲区
    private val fullSyncDataBuffer = StringBuilder()
    private var fullSyncTotalChunks = 0
    private var fullSyncReceivedChunks = 0

    // ✅ 全量同步消息监听器（用于转发消息）
    private var fullSyncListener: (suspend (String) -> Unit)? = null
    private val fullSyncInProgress = AtomicBoolean(false)

    companion object {
        const val TAG = "TcpSyncManager"

        private val IPV4_PATTERN = Regex(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
        )

        const val DEFAULT_SERVER_IP = "192.168.1.100"

        fun isValidServerIp(ip: String): Boolean = IPV4_PATTERN.matches(ip.trim())

        fun isUntrustedCachedIp(context: Context, ip: String): Boolean {
            if (!isValidServerIp(ip)) return true
            val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
            val everConnected = prefs.getBoolean(Constant.PREF_SERVER_EVER_CONNECTED, false)
            if (!everConnected && ip == DEFAULT_SERVER_IP) return true
            return !isServerOnLocalSubnet(ip)
        }

        private fun ipPrefix24(ip: String): String? {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return null
            return "${parts[0]}.${parts[1]}.${parts[2]}."
        }

        private fun getLocalIpv4Addresses(): List<String> {
            return try {
                NetworkInterface.getNetworkInterfaces().toList().flatMap { nic ->
                    if (!nic.isUp || nic.isLoopback) return@flatMap emptyList()
                    nic.inetAddresses.toList().mapNotNull { addr ->
                        if (addr is Inet4Address && !addr.isLoopbackAddress) addr.hostAddress else null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun isServerOnLocalSubnet(serverIp: String): Boolean {
            val serverPrefix = ipPrefix24(serverIp) ?: return true
            val localIps = getLocalIpv4Addresses()
            if (localIps.isEmpty()) return true
            return localIps.any { ipPrefix24(it) == serverPrefix }
        }

        @Volatile
        private var instance: TcpSyncManager? = null

        fun getInstance(context: Context, database: AppDatabase): TcpSyncManager {
            return instance ?: synchronized(this) {
                instance ?: TcpSyncManager(context.applicationContext, database).also {
                    instance = it
                    Log.i(TAG, "✅ TcpSyncManager 单例已创建")
                }
            }
        }

        fun destroyInstance() {
            instance?.run {
                try {
                    val intent = Intent(SyncConnectionObserver.ACTION_TCP_CONNECTION_STATUS).apply {
                        putExtra(SyncConnectionObserver.EXTRA_IS_CONNECTED, false)
                        putExtra(SyncConnectionObserver.EXTRA_MESSAGE, "同步管理器已重置")
                        putExtra("timestamp", System.currentTimeMillis())
                    }
                    context.sendBroadcast(intent)
                } catch (_: Exception) {
                }
                disconnect()
                stopBackgroundDiscovery()
                discoveryScope.cancel()
                scope.cancel()
                _messageChannel.close()
                lastKnownPcActiveYear = null
                Log.i(TAG, "🔴 TcpSyncManager 单例已销毁，资源全部释放")
            }
            instance = null
        }
    }

    data class SyncConfig(
        val serverIp: String = "192.168.1.100",
        val serverPort: Int = 8080,
        val reconnectInterval: Long = 5000,
        val heartbeatInterval: Long = 30000,
        val sendTimeout: Int = 10000,
        val receiveTimeout: Int = 30000
    )

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, SYNCING, ERROR, WAITING_RECONNECT
    }

    private var syncConfig = SyncConfig()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val discoveryMutex = Mutex()
    private val syncDao = database.syncDao()
    private val preSaleSyncApplier by lazy {
        PreSaleSyncApplier(
            database,
            StockService(database.stockDao(), database.stockChangeDao()),
        )
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val isConnecting = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)
    private val isRegistered = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _messageChannel = Channel<String>(Channel.UNLIMITED)
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd")
        .create()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun buildSourceRecordId(type: String, localKey: String): String {
        val safeDeviceId = getDeviceId().replace("|", "_")
        val safeLocalKey = localKey.replace("|", "_")
        return "SRC_${safeDeviceId}_${type}_$safeLocalKey"
    }

    private val reconnectJob = AtomicReference<Job?>(null)
    private var lastConnectionAttempt: Long = 0
    private var reconnectDelay = syncConfig.reconnectInterval
    private val maxReconnectDelay = 60000L
    private var connectionAttemptCount = 0
    private val maxAttemptCount = 10
    private var coldStartUntilMs: Long = 0L
    private val coldStartMaxAttempts = 30
    private val coldStartRetryIntervalMs = 2000L
    private var backgroundDiscoveryJob: Job? = null
    private val backgroundDiscoveryIntervalMs = 5_000L
    private var lastRediscoverMs = 0L
    private val minRediscoverIntervalMs = 2_000L
    private var lastDiscoveryAppliedMs = 0L
    private val discoveryApplyGraceMs = 30_000L
    private var consecutiveConnectFailures = 0
    private val discoverFirstRetryIntervalMs = 3_000L

    private var heartbeatJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var postConnectSyncJob: Job? = null
    private var registrationTimeoutJob: Job? = null
    private val postConnectSyncRunning = AtomicBoolean(false)
    private val confirmedItems = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingAckMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pendingUnifiedAckMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val packagingIdempotentNotices = ConcurrentHashMap<String, String>()
    private val packagingAckFailureMessages = ConcurrentHashMap<String, String>()
    private val pendingCommandMap = ConcurrentHashMap<String, CompletableDeferred<String>>()
    @Volatile
    private var lastKnownPcActiveYear: Int? = null
    @Volatile
    private var lastKnownServerCommitSeq: Long? = null
    @Volatile
    private var lastFullSyncBaselineSeq: Long? = null

    init {
        loadConfig()
        CrsqlHelper.tryLoad(context)
        Log.i(TAG, "🔄 TcpSyncManager 初始化完成，已加载本地配置：${syncConfig.serverIp}:${syncConfig.serverPort}")
    }

    private fun loadConfig() {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        val pairedIp = prefs.getString(Constant.PREF_PAIRED_SERVER_IP, null)?.trim()
        val pairedPort = prefs.getInt(Constant.PREF_PAIRED_SERVER_PORT, 0)
        val defaultIp = prefs.getString("server_ip", DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
        val defaultPort = prefs.getInt("server_port", 8080)
        syncConfig = SyncConfig(
            serverIp = if (!pairedIp.isNullOrBlank() && isValidServerIp(pairedIp)) pairedIp else defaultIp,
            serverPort = if (pairedPort > 0) pairedPort else defaultPort,
            reconnectInterval = prefs.getLong("reconnect_interval", 5000),
            heartbeatInterval = prefs.getLong("heartbeat_interval", 30000),
            sendTimeout = prefs.getInt("send_timeout", 10000),
            receiveTimeout = prefs.getInt("receive_timeout", 30000)
        )
        Log.d(TAG, "📋 加载本地配置完成: $syncConfig")
    }

    fun clearConfirmedItems() {
        confirmedItems.clear()
        Log.i(TAG, "🧹 已清理 confirmedItems，允许重发已确认单据")
    }

    private fun removeConfirmedItem(uniqueKey: String) {
        if (confirmedItems.remove(uniqueKey)) {
            Log.d(TAG, "🧹 已移除 confirmedItems：$uniqueKey")
        }
    }

    fun clearConfirmedItemsForPackagingBill(billId: Long) {
        val marker = "_PACKAGING_${billId}_"
        val toRemove = confirmedItems.filter { key ->
            key.startsWith("PACKAGING|") && key.contains(marker)
        }
        toRemove.forEach { confirmedItems.remove(it) }
        if (toRemove.isNotEmpty()) {
            Log.i(TAG, "🧹 已清除包装单 id=$billId 的 confirmedItems 缓存（${toRemove.size}项）")
        }
    }

    fun updateConfig(config: SyncConfig) {
        syncConfig = config
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_ip", config.serverIp)
            putInt("server_port", config.serverPort)
            putLong("reconnect_interval", config.reconnectInterval)
            putLong("heartbeat_interval", config.heartbeatInterval)
            putInt("send_timeout", config.sendTimeout)
            putInt("receive_timeout", config.receiveTimeout)
        }.apply()
        Log.i(TAG, "🔧 同步配置已更新并持久化: $config")

        if (isConnected.get()) {
            Log.d(TAG, "🔄 配置更新，触发重连应用新配置")
            disconnectInternal()
            connect()
        }
    }

    fun enableColdStartMode(durationMs: Long = 90_000L) {
        coldStartUntilMs = System.currentTimeMillis() + durationMs
        Log.i(TAG, "❄️ 冷启动快速重连已启用，持续 ${durationMs / 1000}s")
    }

    /** 外部发现（如 Service 启动扫描）成功后调用，避免紧接着 connect 再次全量扫网 */
    fun notifyDiscoveryApplied() {
        lastDiscoveryAppliedMs = System.currentTimeMillis()
    }

    private fun isColdStartMode(): Boolean = System.currentTimeMillis() < coldStartUntilMs

    fun connect() {
        scope.launch {
            if (isConnecting.get() || isConnected.get()) {
                Log.w(TAG, "🔧 检测到已有连接/连接中，主动断开旧连接以保证单一连接")
                disconnectInternal()
                delay(500)
            }

            shouldReconnect.set(true)
            isRegistered.set(false)
            connectionAttemptCount = 0
            reconnectDelay = syncConfig.reconnectInterval

            startBackgroundDiscovery()
            connectInternal()
        }
    }

    private fun connectInternal() {
        scope.launch {
            lastConnectionAttempt = System.currentTimeMillis()

            if (!shouldReconnect.get()) {
                Log.w(TAG, "🔴 重连开关已关闭，跳过连接")
                return@launch
            }

            if (isConnected.get() || isConnecting.get()) {
                Log.w(TAG, "⚠️ 已经连接或正在连接中，跳过重复连接")
                return@launch
            }

            if (!isValidServerIp(syncConfig.serverIp)) {
                Log.w(TAG, "⚠️ 服务器IP无效: ${syncConfig.serverIp}，尝试重新发现")
                isConnecting.set(false)
                _connectionState.value = ConnectionState.ERROR
                if (shouldReconnect.get()) {
                    val updated = awaitServerDiscovery(force = true)
                    if (updated) {
                        connectInternal()
                    } else {
                        scheduleSmartReconnect()
                    }
                }
                return@launch
            }

            if (shouldDiscoverBeforeConnect()) {
                Log.d(TAG, "🔍 连接前优先扫网（失败${consecutiveConnectFailures}次 / 不可信IP=${isUntrustedServerIp()})")
                _connectionState.value = ConnectionState.CONNECTING
                val updated = awaitServerDiscovery(force = true)
                if (updated) {
                    Log.i(TAG, "✅ 扫网发现新地址: ${syncConfig.serverIp}:${syncConfig.serverPort}")
                } else if (isUntrustedServerIp()) {
                    Log.w(TAG, "⚠️ 无可信缓存IP且扫网未发现，跳过TCP等待")
                    isConnecting.set(false)
                    _connectionState.value = ConnectionState.WAITING_RECONNECT
                    scheduleSmartReconnect()
                    return@launch
                }
            }

            isConnecting.set(true)
            _connectionState.value = ConnectionState.CONNECTING
            Log.d(TAG, "🔌 开始连接服务器: ${syncConfig.serverIp}:${syncConfig.serverPort}")

            try {
                socket = Socket().apply {
                    soTimeout = 5000
                    tcpNoDelay = true
                    connect(
                        InetSocketAddress(syncConfig.serverIp, syncConfig.serverPort),
                        5000
                    )
                    soTimeout = syncConfig.receiveTimeout
                    keepAlive = true
                }

                writer = BufferedWriter(
                    OutputStreamWriter(socket!!.getOutputStream(), StandardCharsets.UTF_8),
                    1024
                )
                reader = BufferedReader(
                    InputStreamReader(socket!!.getInputStream(), StandardCharsets.UTF_8),
                    1024
                )

                isConnected.set(true)
                isConnecting.set(false)
                clearConfirmedItems()
                Log.i(TAG, "✅ TCP连接成功！等待服务器注册确认: ${socket!!.inetAddress.hostAddress}")

                startConnectionMonitor()

                delay(500)
                sendRegistration()
                startRegistrationTimeout()
                startMessageListener()

            } catch (e: Exception) {
                Log.e(TAG, "❌ TCP连接失败: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
                isConnecting.set(false)
                isConnected.set(false)
                isRegistered.set(false)
                consecutiveConnectFailures++

                sendConnectionBroadcast(false, e.message ?: "连接失败")

                if (shouldReconnect.get()) {
                    scheduleSmartReconnect()
                }
            }
        }
    }

    private fun isUntrustedServerIp(): Boolean =
        isUntrustedCachedIp(context, syncConfig.serverIp)

    private fun shouldDiscoverBeforeConnect(): Boolean {
        if (System.currentTimeMillis() - lastDiscoveryAppliedMs < discoveryApplyGraceMs) {
            return false
        }
        return consecutiveConnectFailures >= 1 || isUntrustedServerIp()
    }

    private suspend fun awaitServerDiscovery(force: Boolean): Boolean {
        return discoveryScope.async {
            runServerDiscovery(force)
        }.await()
    }

    private suspend fun runServerDiscovery(force: Boolean): Boolean {
        return discoveryMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastRediscoverMs < minRediscoverIntervalMs) {
                return@withLock false
            }
            lastRediscoverMs = now

            try {
                withContext(NonCancellable) {
                    performServerDiscoveryScan()
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 重新发现服务器失败: ${e.message}")
                false
            }
        }
    }

    private suspend fun performServerDiscoveryScan(): Boolean {
        val pairingCode = getPairingCode()
        if (pairingCode.isBlank()) {
            return false
        }

        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        val discoveryMethod = prefs.getString(Constant.PREF_DISCOVERY_METHOD, "both") ?: "both"

        var discoveredIp: String? = null
        var discoveredPort: Int? = null

        if (discoveryMethod == "mdns" || discoveryMethod == "both") {
            val mdnsDevice = MdnsDeviceDiscovery.getInstance(context).autoConnect(pairingCode)
            if (mdnsDevice != null) {
                discoveredIp = mdnsDevice.ip
                discoveredPort = mdnsDevice.port
            }
        }

        if (discoveredIp == null && (discoveryMethod == "udp" || discoveryMethod == "both")) {
            val udp = UdpDeviceDiscovery.getInstance(context)
            val device = udp.autoConnect(
                pairingCode,
                announceTimeoutMs = 2000L,
                discoverTimeoutMs = 5000L,
            )
            if (device != null) {
                discoveredIp = device.ip
                discoveredPort = device.port
            }
        }

        return if (discoveredIp != null && discoveredPort != null) {
            applyDiscoveredServer(discoveredIp, discoveredPort)
        } else {
            false
        }
    }

    private fun applyDiscoveredServer(ip: String, port: Int): Boolean {
        val current = syncConfig
        if (ip == current.serverIp && port == current.serverPort) {
            return false
        }
        Log.i(TAG, "🔄 重新发现服务器: $ip:$port (原 ${current.serverIp}:${current.serverPort})")
        updateConfig(current.copy(serverIp = ip, serverPort = port))
        context.getSharedPreferences("sync_config", Context.MODE_PRIVATE).edit()
            .putString(Constant.PREF_PAIRED_SERVER_IP, ip)
            .putInt(Constant.PREF_PAIRED_SERVER_PORT, port)
            .apply()
        consecutiveConnectFailures = 0
        connectionAttemptCount = 0
        reconnectDelay = syncConfig.reconnectInterval
        lastDiscoveryAppliedMs = System.currentTimeMillis()
        return true
    }

    private fun markServerEverConnected() {
        context.getSharedPreferences("sync_config", Context.MODE_PRIVATE).edit()
            .putBoolean(Constant.PREF_SERVER_EVER_CONNECTED, true)
            .apply()
    }

    private fun startRegistrationTimeout() {
        registrationTimeoutJob?.cancel()
        registrationTimeoutJob = scope.launch {
            delay(10_000)
            if (isConnected.get() && !isRegistered.get()) {
                Log.w(TAG, "⏰ 注册超时，未收到 REGISTER_OK，断开并重连")
                handleConnectionLost()
            }
        }
    }

    private fun startBackgroundDiscovery() {
        backgroundDiscoveryJob?.cancel()
        backgroundDiscoveryJob = discoveryScope.launch {
            while (isActive && shouldReconnect.get()) {
                if (isConnected.get() && isRegistered.get()) break

                val updated = runServerDiscovery(force = true)
                if (updated && shouldReconnect.get() && !isConnected.get() && !isConnecting.get()) {
                    connectionAttemptCount = 0
                    consecutiveConnectFailures = 0
                    reconnectDelay = syncConfig.reconnectInterval
                    reconnectJob.get()?.cancel()
                    Log.i(TAG, "🔍 后台发现新服务器，立即尝试连接")
                    connectInternal()
                }

                delay(backgroundDiscoveryIntervalMs)
            }
        }
    }

    private fun stopBackgroundDiscovery() {
        backgroundDiscoveryJob?.cancel()
        backgroundDiscoveryJob = null
    }

    private suspend fun sendRegistration() {
        try {
            val deviceId = getDeviceId()
            val deviceName = getDeviceName()
            val pairingCode = getPairingCode()
            val deviceType = "手持端"
            val appVersion = getAppVersion()
            val androidApi = android.os.Build.VERSION.SDK_INT

            val registerMsg = buildString {
                append("REGISTER|")
                append(deviceId).append("|")
                append(deviceName).append("|")
                append(pairingCode).append("|")
                append(deviceType).append("|")
                append(appVersion).append("|")
                append(androidApi)
            }

            val sendResult = sendMessage(registerMsg)

            if (sendResult) {
                Log.i(TAG, "✅ 设备注册消息已发送")
            } else {
                Log.e(TAG, "❌ 设备注册消息发送失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送注册消息异常: ${e.message}", e)
        }
    }

    private fun getPairingCode(): String {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        return prefs.getString(Constant.PREF_PAIRING_CODE, "ABC-123") ?: "ABC-123"
    }

    private fun getDeviceName(): String {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL

            when {
                model.startsWith(manufacturer) -> model
                manufacturer.equals("samsung", ignoreCase = true) -> "Samsung $model"
                manufacturer.equals("xiaomi", ignoreCase = true) -> "Xiaomi $model"
                manufacturer.equals("huawei", ignoreCase = true) -> "Huawei $model"
                manufacturer.equals("oppo", ignoreCase = true) -> "OPPO $model"
                manufacturer.equals("vivo", ignoreCase = true) -> "vivo $model"
                manufacturer.equals("oneplus", ignoreCase = true) -> "OnePlus $model"
                manufacturer.equals("google", ignoreCase = true) -> "Pixel $model"
                else -> "$manufacturer $model"
            }.trim()
        } catch (e: Exception) {
            "Android设备"
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && connectionState.value == ConnectionState.CONNECTED) {
                delay(syncConfig.heartbeatInterval)
                try {
                    val heartbeatMsg = "PING|HEARTBEAT_${System.currentTimeMillis()}"
                    val sendResult = sendMessage(heartbeatMsg)
                    if (!sendResult) {
                        Log.w(TAG, "💔 心跳发送失败，连接异常")
                        handleConnectionLost()
                        break
                    }
                    Log.v(TAG, "💓 心跳已发送: $heartbeatMsg")
                } catch (e: Exception) {
                    Log.w(TAG, "💔 心跳发送异常: ${e.message}")
                    handleConnectionLost()
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "⏹️ 心跳任务已停止")
    }

    private fun scheduleSmartReconnect() {
        reconnectJob.get()?.cancel()

        scope.launch {
            connectionAttemptCount++

            val inColdStart = isColdStartMode()
            val untrustedIp = isUntrustedServerIp()
            val effectiveMaxAttempts = if (inColdStart) coldStartMaxAttempts else maxAttemptCount

            if (!inColdStart && !untrustedIp && connectionAttemptCount > 1) {
                reconnectDelay = min(
                    reconnectDelay * 2,
                    maxReconnectDelay
                )
            }

            if (connectionAttemptCount > effectiveMaxAttempts && !inColdStart) {
                Log.w(TAG, "⚠️  已达到最大重连尝试次数(${effectiveMaxAttempts}次)，暂停重连")
                _connectionState.value = ConnectionState.WAITING_RECONNECT

                delay(30 * 60 * 1000)
                connectionAttemptCount = 0
                reconnectDelay = syncConfig.reconnectInterval
                scheduleSmartReconnect()
                return@launch
            }

            val delayTime = when {
                inColdStart -> coldStartRetryIntervalMs
                untrustedIp || consecutiveConnectFailures >= 1 -> discoverFirstRetryIntervalMs
                connectionAttemptCount == 1 -> syncConfig.reconnectInterval
                else -> reconnectDelay
            }

            Log.i(
                TAG,
                "⏳ 第${connectionAttemptCount}次尝试重连，${delayTime}ms后重连..." +
                    if (untrustedIp) " (扫网优先)" else ""
            )
            _connectionState.value = ConnectionState.WAITING_RECONNECT
            updateNotification("等待重连 (${delayTime / 1000}秒后)")

            delay(delayTime)

            if (shouldReconnect.get() && !isConnected.get()) {
                Log.d(TAG, "🔄 执行智能重连，尝试次数: $connectionAttemptCount, 间隔: ${delayTime}ms")
                connectInternal()
            }
        }.also { reconnectJob.set(it) }
    }

    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch {
            while (shouldReconnect.get()) {
                delay(60000)

                if (shouldReconnect.get() && !isConnected.get() && !isConnecting.get()) {
                    Log.d(TAG, "⏰ 定时检测：连接已断开，触发重连")

                    val timeSinceLastAttempt = System.currentTimeMillis() - lastConnectionAttempt
                    if (timeSinceLastAttempt > 30000) {
                        scheduleSmartReconnect()
                    }
                }

                if (isConnected.get()) {
                    testConnectionAlive()
                }
            }
        }
    }

    private fun stopConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }

    private fun schedulePostConnectSync() {
        postConnectSyncJob?.cancel()
        postConnectSyncJob = scope.launch {
            delay(1000)
            if (!isConnected.get() || !isRegistered.get()) {
                Log.d(TAG, "连接后同步跳过：连接或注册状态已变化")
                return@launch
            }
            if (!postConnectSyncRunning.compareAndSet(false, true)) {
                Log.w(TAG, "连接后同步已在进行中，跳过重复触发")
                return@launch
            }
            try {
                if (!ensureSyncYearAligned()) {
                    return@launch
                }
                if (!isConnected.get() || !isRegistered.get()) {
                    return@launch
                }
                autoSyncPendingData()
                autoBidirectionalDeltaSyncOnConnect(skipHelloSync = true)
            } finally {
                postConnectSyncRunning.set(false)
            }
        }
    }

    private suspend fun testConnectionAlive() {
        try {
            val currentSocket = socket ?: run {
                handleConnectionLost()
                return
            }
            if (!currentSocket.isConnected || currentSocket.isClosed) {
                handleConnectionLost()
                return
            }

            val testMsg = "PING|CONNECTION_TEST"
            val sendResult = sendMessage(testMsg)
            if (!sendResult) {
                Log.w(TAG, "💔 连接测试PING命令发送失败")
                handleConnectionLost()
            }
            Log.v(TAG, "💓 连接测试包已发送: $testMsg")
        } catch (e: Exception) {
            Log.w(TAG, "💔 连接测试失败: ${e.message}")
            handleConnectionLost()
        }
    }

    private fun handleConnectionLost() {
        if (isConnected.get()) {
            Log.w(TAG, "🔌 检测到连接丢失")
            isConnected.set(false)
            isRegistered.set(false)
            _connectionState.value = ConnectionState.DISCONNECTED

            stopHeartbeat()
            stopConnectionMonitor()
            postConnectSyncJob?.cancel()
            registrationTimeoutJob?.cancel()
            postConnectSyncRunning.set(false)

            try {
                writer?.close()
                reader?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 关闭连接资源异常", e)
            }

            writer = null
            reader = null
            socket = null

            if (shouldReconnect.get()) {
                startBackgroundDiscovery()
                scheduleSmartReconnect()
            }
        }
    }

    private fun sendConnectionBroadcast(isConnected: Boolean, message: String) {
        val intent = Intent(SyncConnectionObserver.ACTION_TCP_CONNECTION_STATUS)
            .apply {
                putExtra(SyncConnectionObserver.EXTRA_IS_CONNECTED, isConnected)
                putExtra(SyncConnectionObserver.EXTRA_MESSAGE, message)
                putExtra("timestamp", System.currentTimeMillis())
            }
        context.sendBroadcast(intent)
        Log.d(TAG, "📢 发送连接状态广播: $message")
    }

    private suspend fun autoSyncPendingData() {
        try {
            Log.d(TAG, "🔍 开始检查未同步单据...")

            val pendingIn = database.inStockBillDao().getAllBills().first().filter { it.syncStatus == 0 }
            val pendingSale = database.saleBillDao().getAllBills().first().filter { it.syncStatus == 0 }
            val pendingPack = database.packagingBillDao().getAllBills().first().filter { !it.isSynced }
            val pendingAdvances = database.advanceDao().getUnsyncedAdvances()
            val pendingDeductions = database.deductionDao().getUnsyncedDeductions()
            val pendingPresales = database.preSaleBillDao().getUnsyncedBills()
            val pendingPresalePayments = database.paymentRecordDao().getUnsyncedPayments()
            val pendingLedger = database.ledgerEntryDao().getUnsyncedEntries()

            val totalPending = pendingIn.size + pendingSale.size + pendingPack.size +
                pendingAdvances.size + pendingDeductions.size +
                pendingPresales.size + pendingPresalePayments.size + pendingLedger.size

            if (totalPending > 0) {
                val detail = buildString {
                    append("入库${pendingIn.size}，销售${pendingSale.size}，包装${pendingPack.size}")
                    if (pendingAdvances.isNotEmpty()) append("，预支${pendingAdvances.size}")
                    if (pendingDeductions.isNotEmpty()) append("，扣款${pendingDeductions.size}")
                    if (pendingPresales.isNotEmpty()) append("，预售${pendingPresales.size}")
                    if (pendingPresalePayments.isNotEmpty()) append("，预售收款${pendingPresalePayments.size}")
                    if (pendingLedger.isNotEmpty()) append("，流水${pendingLedger.size}")
                }
                Log.i(TAG, "🔍 发现 $totalPending 条未同步数据（$detail），开始自动同步...")
                _messageChannel.send("发现 $totalPending 条未同步数据，开始自动同步...")
                val syncResult = syncPendingData()

                if (syncResult) {
                    Log.i(TAG, "✅ 自动同步完成，已触发反向同步")
                } else {
                    Log.e(TAG, "❌ 自动同步失败")
                }
            } else {
                Log.i(TAG, "✅ 所有数据已同步，无需同步")
                _messageChannel.send("✅ 所有数据已同步")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 自动同步检查失败: ${e.message}", e)
            _messageChannel.send("❌ 自动同步检查失败: ${e.message}")
        }
    }

    private suspend fun updateNotification(contentText: String) {
        Log.d(TAG, "📢 $contentText")
    }

    fun disconnect() {
        shouldReconnect.set(false)
        stopBackgroundDiscovery()
        reconnectJob.get()?.cancel()
        reconnectJob.set(null)
        disconnectInternal()
    }

    private fun disconnectInternal() {
        scope.launch {
            try {
                if (!isConnected.get()) {
                    Log.w(TAG, "🔴 未连接服务器，无需断开")
                    return@launch
                }

                isConnected.set(false)
                isRegistered.set(false)
                _connectionState.value = ConnectionState.DISCONNECTED
                _syncState.value = SyncState.Idle

                stopHeartbeat()
                stopConnectionMonitor()
                postConnectSyncJob?.cancel()
                registrationTimeoutJob?.cancel()
                postConnectSyncRunning.set(false)

                writer?.close()
                reader?.close()
                socket?.close()

                writer = null
                reader = null
                socket = null

                Log.i(TAG, "🔴 TCP连接已主动断开，所有Socket资源释放完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 断开连接失败: ${e.message}", e)
            }
        }
    }

    private fun startMessageListener() {
        scope.launch {
            try {
                while (isConnected.get()) {
                    socket?.soTimeout = if (fullSyncInProgress.get()) {
                        maxOf(syncConfig.receiveTimeout, 90000)
                    } else {
                        syncConfig.receiveTimeout
                    }
                    val line = try {
                        reader?.readLine() ?: break
                    } catch (e: java.net.SocketTimeoutException) {
                        if (fullSyncInProgress.get()) {
                            Log.w(TAG, "⏳ 全量同步期间读取超时，继续等待后续数据")
                            continue
                        }
                        throw e
                    }

                    if (line.isNotBlank()) {
                        Log.d(TAG, "📥 收到服务器消息: $line")
                        processServerMessage(line.removePrefix("\uFEFF"))
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "⏰ 读取超时，连接可能已中断")
                handleConnectionLost()
            } catch (e: CancellationException) {
                Log.i(TAG, "🛑 消息监听任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 监听服务器消息异常: ${e.message}")
                handleConnectionLost()
            } finally {
                if (isConnected.get()) {
                    Log.w(TAG, "📴 服务器消息流已关闭，触发断开连接")
                    handleConnectionLost()
                }
            }
        }
    }

    private suspend fun sendMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isConnected.get()) {
                Log.w(TAG, "🔴 发送失败：未连接服务器，消息：$message")
                return@withContext false
            }
            val safeWriter = writer ?: run {
                Log.e(TAG, "🔴 发送失败：写流为空，消息：$message")
                return@withContext false
            }

            try {
                Log.d(TAG, "📤 准备发送原始数据: $message")
                safeWriter.write(message)
                safeWriter.newLine()
                safeWriter.flush()
                Log.d(TAG, "✅ 消息发送成功: $message")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送消息失败，触发重连: ${e.message}，消息：$message", e)
                handleConnectionLost()
                false
            }
        }
    }

    private suspend fun sendMessageAndWaitForCommand(
        message: String,
        expectedCommand: String,
        timeoutMs: Long = 30000L,
    ): String? {
        return withContext(Dispatchers.IO) {
            if (!isRegistered.get()) {
                Log.w(TAG, "未注册，跳过等待命令 $expectedCommand")
                return@withContext null
            }
            val deferred = CompletableDeferred<String>()
            pendingCommandMap[expectedCommand] = deferred
            try {
                val sent = sendMessage(message)
                if (!sent) {
                    pendingCommandMap.remove(expectedCommand)
                    return@withContext null
                }
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } finally {
                pendingCommandMap.remove(expectedCommand)
            }
        }
    }

    private fun completeCommand(command: String, payload: String) {
        pendingCommandMap.remove(command)?.complete(payload)
    }

    private suspend fun sendMessageAndWaitForAck(dataType: String, jsonData: String, billNo: String, itemKey: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            val ackKey = "$dataType|$billNo"
            var ownsAckRegistration = false
            try {
                val uniqueKey = "$dataType|$billNo|$itemKey"

                val existingAck = pendingAckMap[ackKey]
                if (existingAck != null) {
                    Log.w(TAG, "🔁 检测到重复ACK等待，复用已有等待：$ackKey")
                    val reusedResult = withTimeoutOrNull(30000L) { existingAck.await() } ?: false
                    if (reusedResult) {
                        confirmedItems.add(uniqueKey)
                    }
                    return@withContext reusedResult
                }

                val pushMessage = UnifiedPushHelper.buildPushMessage(billNo, dataType, jsonData)
                val unifiedDeferred = CompletableDeferred<Boolean>()
                pendingUnifiedAckMap[billNo] = unifiedDeferred
                ownsAckRegistration = true

                val sendSuccess = sendMessage(pushMessage)
                if (!sendSuccess) {
                    pendingUnifiedAckMap.remove(billNo)
                    Log.e(TAG, "❌ 发送失败：$dataType $billNo")
                    return@withContext false
                }

                Log.d(TAG, "📤 已发送 PUSH $dataType $billNo，等待 ACK...")
                val result = withTimeoutOrNull(30000L) { unifiedDeferred.await() } ?: false

                if (result) {
                    confirmedItems.add(uniqueKey)
                    Log.i(TAG, "✅ 服务器 ACK 确认：$dataType $billNo")
                } else {
                    removeConfirmedItem(uniqueKey)
                    Log.e(TAG, "⏰ 等待 ACK 超时/失败：$dataType $billNo")
                }
                return@withContext result
            } catch (e: CancellationException) {
                Log.i(TAG, "🛑 发送等待确认任务取消：$dataType $billNo")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送并等待确认异常：$dataType $billNo，${e.message}", e)
                return@withContext false
            } finally {
                if (ownsAckRegistration) {
                    pendingUnifiedAckMap.remove(billNo)
                }
            }
        }
    }

    suspend fun syncPendingDataWithAck(): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("批量同步未完成单据")
                var successCount = 0
                var failCount = 0

                val pendingIn = database.inStockBillDao().getAllBills().first().filter { it.syncStatus == 0 }
                pendingIn.forEach {
                    val success = syncInStockBill(it.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                val pendingSale = database.saleBillDao().getAllBills().first().filter { it.syncStatus == 0 }
                pendingSale.forEach {
                    val success = syncSaleBill(it.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                val pendingPack = database.packagingBillDao().getAllBills().first().filter { !it.isSynced }
                pendingPack.forEach { bill ->
                    val success = syncPackagingBill(bill.id).success
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.advanceDao().getUnsyncedAdvances().forEach { advance ->
                    val success = syncAdvance(advance.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.deductionDao().getUnsyncedDeductions().forEach { deduction ->
                    val success = syncDeduction(deduction.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.outboundRecordDao().getUnsyncedRecords().forEach { record ->
                    val success = syncPreSaleOutbound(record.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.preSaleBillDao().getUnsyncedBills().forEach { bill ->
                    val success = syncPreSaleBill(bill.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.paymentRecordDao().getUnsyncedPayments().forEach { payment ->
                    val success = syncPreSalePayment(payment.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                database.ledgerEntryDao().getUnsyncedEntries().forEach { entry ->
                    val success = syncLedgerEntry(entry.id)
                    if (success) successCount++ else failCount++
                    delay(500)
                }

                val resultMsg = "批量同步完成：成功 $successCount 张，失败 $failCount 张"
                if (failCount > 0) {
                    _syncState.value = SyncState.Failed(resultMsg)
                    Log.e(TAG, "❌ $resultMsg")
                } else {
                    _syncState.value = SyncState.Success(resultMsg)
                    Log.i(TAG, "✅ $resultMsg")
                }
                _messageChannel.send(resultMsg)

                return@withContext Pair(successCount, failCount)

            } catch (e: Exception) {
                val errorMsg = "批量同步失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext Pair(0, 0)
            }
        }
    }

    // ========== 修改后的 processServerMessage 方法（支持消息转发） ==========
    private suspend fun processServerMessage(message: String) {
        try {
            val sanitized = message.removePrefix("\uFEFF")
            val separatorIndex = sanitized.indexOf('|')
            val command = if (separatorIndex >= 0) sanitized.substring(0, separatorIndex) else sanitized
            val data = if (separatorIndex >= 0) sanitized.substring(separatorIndex + 1) else ""
            if (command == "PUSH_CHANGES_ACK" || command == "PULL_DELTA_RESP" || command == "CONFIG_PULL_RESP" || command == "CONFIG_PUSH_ACK") {
                completeCommand(command, data)
                return
            }
            if (command == "ACK") {
                UnifiedPushHelper.parseAck(message)?.let { (opId, ok) ->
                    pendingUnifiedAckMap.remove(opId)?.complete(ok)
                    Log.d(TAG, if (ok) "✅ 收到 ACK ok: $opId" else "❌ 收到 ACK fail: $opId")
                }
                return
            }
            if (command == "SYNC_SERVER_READY") {
                Log.i(TAG, "✅ 服务器已就绪：$data")
                completeCommand("SYNC_SERVER_READY", data)
                _messageChannel.send("服务器已就绪：$data")
                return
            }

            // ✅ 如果有全量同步监听器，将 FULL_SYNC 相关消息转发给它
            when (command) {
                "FULL_SYNC_START", "FULL_SYNC_DATA", "FULL_SYNC_END", "FULL_SYNC_ERROR", "PREPARE_UPLOAD" -> {
                    val listener = fullSyncListener
                    if (listener != null) {
                        Log.d(TAG, "📤 转发 $command 消息给全量同步处理器")
                        listener(message)
                        return
                    } else {
                        Log.w(TAG, "⚠️ 收到 $command 但无全量同步监听器，继续走通用消息处理: ${message.take(120)}")
                    }
                }
            }

            when (command) {
                "REGISTER_OK" -> {
                    Log.i(TAG, "✅ 服务器注册确认成功：$data")
                    registrationTimeoutJob?.cancel()
                    connectionAttemptCount = 0
                    consecutiveConnectFailures = 0
                    reconnectDelay = syncConfig.reconnectInterval
                    markServerEverConnected()
                    stopBackgroundDiscovery()
                    isRegistered.set(true)
                    _connectionState.value = ConnectionState.CONNECTED
                    startHeartbeat()
                    sendConnectionBroadcast(true, "已连接到服务器")
                    _messageChannel.send("✅ 服务器连接成功：$data")
                    schedulePostConnectSync()
                }
                "REGISTER_FAIL" -> {
                    val sessionExpired = data.contains("会话无效") || data.contains("请先发送 REGISTER")
                    Log.e(TAG, "❌ 服务器拒绝注册：$data")
                    registrationTimeoutJob?.cancel()
                    if (sessionExpired) {
                        Log.w(TAG, "🔄 会话失效，断开并重连...")
                        isRegistered.set(false)
                        sendConnectionBroadcast(false, "会话失效，正在重连...")
                        handleConnectionLost()
                        return
                    }

                    shouldReconnect.set(false)
                    isRegistered.set(false)
                    isConnected.set(false)
                    isConnecting.set(false)
                    stopHeartbeat()
                    stopConnectionMonitor()
                    postConnectSyncJob?.cancel()
                    postConnectSyncRunning.set(false)
                    try {
                        writer?.close()
                        reader?.close()
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    writer = null
                    reader = null
                    socket = null
                    _connectionState.value = ConnectionState.ERROR
                    sendConnectionBroadcast(false, "配对码错误：$data")
                    _messageChannel.send("❌ 配对码错误：$data")
                }
                "PUSH_DATA" -> {
                    Log.d(TAG, "📥 收到服务器推送数据：${data.take(120)}")
                }
                "PUSH_COMPLETE" -> {
                    Log.i(TAG, "✅ 服务器推送完成：$data")
                    _messageChannel.send("服务器推送完成")
                }
                "HANDSHAKE_OK" -> {
                    Log.i(TAG, "✅ 握手成功：$data")
                    registrationTimeoutJob?.cancel()
                    isRegistered.set(true)
                    _connectionState.value = ConnectionState.CONNECTED
                    startHeartbeat()
                    _messageChannel.send("握手成功：$data")
                }
                "PONG" -> {
                    Log.v(TAG, "💓 收到心跳响应：$data")
                }
                "DELTA_AVAILABLE" -> {
                    Log.i(TAG, "📣 收到增量可用通知：$data")
                    scope.launch {
                        try {
                            val deviceId = getDeviceId()
                            val skipReason = getDownstreamDeltaSkipReason(deviceId)
                            if (skipReason != null) {
                                Log.i(TAG, "⏭️ 忽略 DELTA_AVAILABLE：$skipReason")
                                return@launch
                            }
                            var fromSeq = syncDao.getCursor(deviceId)?.lastAckedSeq ?: 0L
                            var latestAckedSeq = pullAndApplyDelta(deviceId, fromSeq)
                            if (latestAckedSeq == null) {
                                latestAckedSeq = recoverDownstreamConfigFromPc(deviceId)
                                fromSeq = syncDao.getCursor(deviceId)?.lastAckedSeq ?: fromSeq
                            }
                            if (latestAckedSeq != null) {
                                syncDao.updateLastAckedSeq(deviceId, latestAckedSeq, System.currentTimeMillis())
                                com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier.notifyChanged()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ DELTA_AVAILABLE 拉取失败: ${e.message}", e)
                        }
                    }
                }
                "SYNC_SUCCESS" -> {
                    val subParts = data.split("|", limit = 6)
                    if (subParts.size >= 2) {
                        val dataType = subParts[0]
                        val code = subParts[1]
                        val extraInfo = if (subParts.size >= 4) subParts[3] else ""
                        val isSourceRecordAck = code.startsWith("SRC_")
                        if (dataType == "PACKAGING" && subParts.size >= 5 && subParts[3] == "IDEMPOTENT") {
                            val idempotentReason = subParts.getOrElse(4) { "" }
                            if (idempotentReason.isNotBlank()) {
                                packagingIdempotentNotices["$dataType|$code"] = idempotentReason
                            }
                        }
                        completeAck(dataType, code, true)

                        when (dataType) {
                            "INBOUND", "SALES" -> {
                                Log.i(TAG, "✅ 同步确认成功：$dataType $code")
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, code, true) }
                                } else {
                                    scope.launch {
                                        updateSyncStatus(code, dataType, true)
                                    }
                                }
                            }
                            "PACKAGING" -> {
                                Log.i(TAG, "✅ 包装明细同步确认成功：$code（整单状态由 syncPackagingBill 统一更新）")
                            }
                            "ADVANCE", "DEDUCTION" -> {
                                Log.i(TAG, "✅ 预支扣款同步确认成功：$dataType $code")
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, code, true) }
                                } else {
                                    scope.launch {
                                        val dateInfo = if (extraInfo.isNotEmpty()) extraInfo else null
                                        updateAdvanceDeductionStatus(dataType, code, true, dateInfo)
                                    }
                                }
                            }
                            "PRESALE", "PRESALE_PAYMENT", "PRESALE_OUTBOUND", "LEDGER" -> {
                                Log.i(TAG, "✅ 预售/流水同步确认成功：$dataType $code")
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, code, true) }
                                }
                            }
                            "LOCATION", "OPERATOR", "CUSTOMER" -> {
                                Log.i(TAG, "✅ 基础数据同步确认成功：$dataType $code")
                                scope.launch {
                                    updateBaseSyncStatus(dataType, code, true)
                                }
                            }
                            else -> Log.w(TAG, "未知数据类型：$dataType")
                        }
                        _messageChannel.send("✅ $dataType $code 同步成功")
                    }
                }
                "SYNC_FAILED" -> {
                    val subParts = data.split("|", limit = 3)
                    if (subParts.size >= 2) {
                        val dataType = subParts[0]
                        val billNo = subParts[1]
                        val errorMsg = if (subParts.size >= 3) subParts[2] else "未知错误"
                        val isSourceRecordAck = billNo.startsWith("SRC_")
                        completeAck(dataType, billNo, false)
                        Log.e(TAG, "❌ 同步确认失败：$dataType $billNo - $errorMsg")
                        when (dataType) {
                            "INBOUND", "SALES" -> {
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, billNo, false) }
                                } else {
                                    scope.launch { updateSyncStatus(billNo, dataType, false) }
                                }
                            }
                            "PACKAGING" -> {
                                packagingAckFailureMessages["$dataType|$billNo"] = errorMsg
                                Log.w(TAG, "❌ 包装明细同步失败：$billNo - $errorMsg")
                            }
                            "ADVANCE", "DEDUCTION" -> {
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, billNo, false) }
                                }
                            }
                            "PRESALE", "PRESALE_PAYMENT", "PRESALE_OUTBOUND", "LEDGER" -> {
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, billNo, false) }
                                }
                            }
                        }
                        _syncState.value = SyncState.Failed("$dataType $billNo 同步失败：$errorMsg")
                        _messageChannel.send("❌ $dataType $billNo 同步失败：$errorMsg")
                    } else {
                        Log.e(TAG, "❌ 收到同步失败确认：$data")
                    }
                }
                "SYNC_ERROR" -> {
                    val subParts = data.split("|", limit = 2)
                    if (subParts.size >= 1) {
                        val dataType = subParts[0]
                        val errorMsg = if (subParts.size >= 2) subParts[1] else "服务器错误"
                        Log.e(TAG, "❌ 服务器同步错误：$dataType - $errorMsg")
                        _syncState.value = SyncState.Failed("服务器错误：$errorMsg")
                        _messageChannel.send("❌ 服务器错误：$errorMsg")
                    } else {
                        Log.e(TAG, "❌ 收到服务器错误：$data")
                    }
                }
                "SYNC_RESULT" -> handleSyncResult(data)
                "QUERY_RESULT" -> {
                    Log.d(TAG, "📊 收到查询结果：$data")
                    _messageChannel.send("查询结果：$data")
                }
                "ERROR" -> {
                    Log.e(TAG, "❌ 服务器返回错误：$data")
                    _syncState.value = SyncState.Failed(data)
                    _messageChannel.send("服务器错误：$data")
                }
                "RESYNC_REQUIRED" -> {
                    Log.w(TAG, "⚠️ 服务器要求全量重建：$data")
                    resetSyncCursorForFullResync(getDeviceId())
                    _messageChannel.send("服务器要求全量重建，下次连接执行首次全量：$data")
                }
                "CONFIG_NO_CHANGE" -> {
                    Log.i(TAG, "ℹ️ 基础配置无变化：$data")
                    _messageChannel.send("基础配置无变化")
                }
                else -> Log.w(TAG, "⚠️ 收到服务器未知指令：$command，数据：$data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理服务器消息失败: ${e.message}", e)
        }
    }

    private fun completeAck(dataType: String, billOrSourceId: String, success: Boolean) {
        val ackKey = "$dataType|$billOrSourceId"
        val deferred = pendingAckMap.remove(ackKey)
        if (deferred == null) {
            Log.w(TAG, "⚠️ ACK未命中待确认项：$ackKey（可能已超时，尝试迟到回写）")
            if (success) {
                scope.launch { handleLateAckSuccess(dataType, billOrSourceId) }
            }
            return
        }
        deferred.complete(success)
    }

    private suspend fun handleLateAckSuccess(dataType: String, sourceRecordId: String) {
        Log.i(TAG, "📥 处理迟到 ACK：$dataType $sourceRecordId")
        when (dataType) {
            "INBOUND", "SALES" -> updateSyncStatusBySourceRecordId(dataType, sourceRecordId, true)
            "PACKAGING" -> Log.i(TAG, "包装明细迟到 ACK，整单状态由 syncPackagingBill 统一更新")
            "ADVANCE", "DEDUCTION", "PRESALE", "PRESALE_PAYMENT", "PRESALE_OUTBOUND", "LEDGER" ->
                updateSyncStatusBySourceRecordId(dataType, sourceRecordId, true)
            else -> Log.d(TAG, "迟到 ACK 无需回写：$dataType")
        }
    }

    private suspend fun updateSyncStatusBySourceRecordId(dataType: String, sourceRecordId: String, success: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val parsed = parseSourceRecordId(sourceRecordId)
                if (parsed == null) {
                    Log.w(TAG, "⚠️ 无法解析 source_record_id：$sourceRecordId")
                    return@withContext
                }
                val localKey = parsed.third
                when (dataType) {
                    "INBOUND" -> {
                        val billNo = normalizeBillNoFromLocalKey(localKey)
                        database.inStockBillDao().getBillByNo(billNo)?.let {
                            database.inStockBillDao().updateSyncStatus(it.id, if (success) 1 else 0)
                        } ?: Log.w(TAG, "⚠️ INBOUND未找到本地单据：source=$sourceRecordId, key=$localKey, billNo=$billNo")
                    }
                    "SALES" -> {
                        val billNo = normalizeBillNoFromLocalKey(localKey)
                        database.saleBillDao().getBillByNo(billNo)?.let {
                            database.saleBillDao().updateSyncStatus(it.id, if (success) 1 else 0)
                        } ?: Log.w(TAG, "⚠️ SALES未找到本地单据：source=$sourceRecordId, key=$localKey, billNo=$billNo")
                    }
                    "PACKAGING" -> {
                        // 整单 isSynced 仅由 syncPackagingBill 全部明细成功后更新
                        Log.d(TAG, "PACKAGING 逐行 ACK 已收到，暂不更新整单状态：$sourceRecordId")
                    }
                    "ADVANCE" -> localKey.toLongOrNull()?.let { database.advanceDao().updateSyncStatus(it, if (success) 1 else 0) }
                    "DEDUCTION" -> localKey.toLongOrNull()?.let { database.deductionDao().updateSyncStatus(it, if (success) 1 else 0) }
                    "PRESALE" -> {
                        database.preSaleBillDao().getBillBySourceRecordId(sourceRecordId)?.let {
                            database.preSaleBillDao().updateSyncStatus(it.id, if (success) 1 else 0)
                        } ?: localKey.toLongOrNull()?.let {
                            database.preSaleBillDao().updateSyncStatus(it, if (success) 1 else 0)
                        }
                    }
                    "PRESALE_PAYMENT" -> {
                        database.paymentRecordDao().getBySourceRecordId(sourceRecordId)?.let {
                            database.paymentRecordDao().updateSyncStatus(it.id, if (success) 1 else 0)
                        } ?: localKey.toLongOrNull()?.let {
                            database.paymentRecordDao().updateSyncStatus(it, if (success) 1 else 0)
                        }
                    }
                    "PRESALE_OUTBOUND" -> {
                        database.outboundRecordDao().getBySourceRecordId(sourceRecordId)?.let {
                            database.outboundRecordDao().updateSyncStatus(it.id, if (success) 1 else 0)
                        } ?: localKey.toLongOrNull()?.let {
                            database.outboundRecordDao().updateSyncStatus(it, if (success) 1 else 0)
                        }
                    }
                    "LEDGER" -> localKey.toLongOrNull()?.let {
                        database.ledgerEntryDao().updateSyncStatus(
                            it,
                            if (success) 1 else 0,
                            if (success) System.currentTimeMillis() else null
                        )
                    }
                    else -> Log.w(TAG, "未知 source_record_id 数据类型：$dataType")
                }
                Log.d(TAG, "💾 已按 source_record_id 回写状态：$sourceRecordId -> ${if (success) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 按 source_record_id 回写失败：$sourceRecordId", e)
            }
        }
    }

    private fun parseSourceRecordId(sourceRecordId: String): Triple<String, String, String>? {
        if (!sourceRecordId.startsWith("SRC_")) return null
        val body = sourceRecordId.removePrefix("SRC_")
        val marker = "_PRESALE_OUTBOUND_"
            .takeIf { body.contains(it) }
            ?: "_PRESALE_PAYMENT_"
            .takeIf { body.contains(it) }
            ?: "_PRESALE_".takeIf { body.contains(it) }
            ?: "_LEDGER_".takeIf { body.contains(it) }
            ?: "_INBOUND_".takeIf { body.contains(it) }
            ?: "_SALES_".takeIf { body.contains(it) }
            ?: "_PACKAGING_".takeIf { body.contains(it) }
            ?: "_ADVANCE_".takeIf { body.contains(it) }
            ?: "_DEDUCTION_".takeIf { body.contains(it) }
            ?: return null
        val type = marker.trim('_')
        val splitIndex = body.indexOf(marker)
        if (splitIndex <= 0) return null
        val deviceId = body.substring(0, splitIndex)
        val localKey = body.substring(splitIndex + marker.length)
        return Triple(deviceId, type, localKey)
    }

    private fun normalizeBillNoFromLocalKey(localKey: String): String {
        val lastUnderscore = localKey.lastIndexOf('_')
        if (lastUnderscore <= 0 || lastUnderscore == localKey.length - 1) {
            return localKey
        }
        val tail = localKey.substring(lastUnderscore + 1)
        return if (tail.all { it.isDigit() }) localKey.substring(0, lastUnderscore) else localKey
    }

    /**
     * 处理接收到的全量同步数据
     */
    private suspend fun processFullSyncData(jsonData: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔄 开始解析并更新本地数据")
                Log.d(TAG, "JSON数据: ${jsonData.take(200)}")

                val jsonElement = JsonParser.parseString(jsonData)
                val jsonObject = jsonElement.asJsonObject

                val instruction = jsonObject.get("instruction")?.asString ?: ""
                Log.d(TAG, "instruction: $instruction")

                if (instruction == "REPLACE_ALL") {
                    val dataObject = jsonObject.getAsJsonObject("data")

                    val locationsArray = dataObject.getAsJsonArray("locations")
                    if (locationsArray != null) {
                        var locationCount = 0
                        for (i in 0 until locationsArray.size()) {
                            val locationElement = locationsArray.get(i)
                            val locationObj = locationElement.asJsonObject
                            val name = locationObj.get("name")?.asString ?: continue

                            val existing = database.locationDao().getByLocationName(name)
                            if (existing == null) {
                                val location = com.pingwei.lengkubao.data.db.entity.Location(
                                    locationName = name,
                                    enabled = true,
                                    syncStatus = 1,
                                )
                                database.locationDao().insert(location)
                                locationCount++
                                Log.i(TAG, "✅ 新增库位: $name")
                            } else if (!existing.enabled) {
                                database.locationDao().updateEnabledStatus(existing.id, true)
                                locationCount++
                                Log.i(TAG, "✅ 启用库位: $name")
                            }
                        }
                        Log.i(TAG, "✅ 库位数据同步完成: ${locationsArray.size()} 条，新增/启用 $locationCount 条")
                    }

                    val handlersArray = dataObject.getAsJsonArray("handlers")
                    if (handlersArray != null) {
                        var handlerCount = 0
                        for (i in 0 until handlersArray.size()) {
                            val handlerElement = handlersArray.get(i)
                            val handlerObj = handlerElement.asJsonObject
                            val name = handlerObj.get("name")?.asString ?: continue

                            val existing = database.operatorDao().getByOperatorName(name)
                            if (existing == null) {
                                val operator = com.pingwei.lengkubao.data.db.entity.Operator(
                                    name = name,
                                    enabled = true,
                                    syncStatus = 1,
                                )
                                database.operatorDao().insert(operator)
                                handlerCount++
                                Log.i(TAG, "✅ 新增经手人: $name")
                            } else if (!existing.enabled) {
                                database.operatorDao().updateEnabledStatus(existing.id, true)
                                handlerCount++
                                Log.i(TAG, "✅ 启用经手人: $name")
                            }
                        }
                        Log.i(TAG, "✅ 经手人数据同步完成: ${handlersArray.size()} 条，新增/启用 $handlerCount 条")
                    }

                    val clientsArray = dataObject.getAsJsonArray("clients")
                    if (clientsArray != null) {
                        val syncedCodes = mutableListOf<String>()
                        for (i in 0 until clientsArray.size()) {
                            val clientElement = clientsArray.get(i)
                            val clientObj = clientElement.asJsonObject
                            val code = clientObj.get("code")?.asString ?: continue
                            val name = clientObj.get("name")?.asString ?: continue
                            val phone = clientObj.get("phone")?.asString ?: ""
                            val enabled = resolveEnabledFromPayload(clientObj)
                            val customerType = resolveCustomerTypeFromPayload(code, clientObj)

                            upsertCustomerFromRemote(code, name, phone, customerType, enabled)
                            syncedCodes.add(code)
                            Log.d(TAG, "✅ 更新客户: $name ($customerType)")
                        }
                        purgeCustomersExceptSnapshot(syncedCodes)
                        Log.i(TAG, "✅ 客户数据同步完成: ${syncedCodes.size} 条（含卖家/买家，已清理快照外客户）")
                    }

                    val productsArray = dataObject.getAsJsonArray("product_types")
                    if (productsArray != null) {
                        replaceProductsFromSnapshot(productsArray)
                    }

                    val packTypesArray = dataObject.getAsJsonArray("pack_types")
                    if (packTypesArray != null) {
                        replacePackTypesFromSnapshot(packTypesArray)
                    }

                    // ✅ 同步PC库存快照（库位 + 型号）
                    val stocksArray = dataObject.getAsJsonArray("stocks")
                    if (stocksArray != null) {
                        val now = System.currentTimeMillis()
                        val products = database.productDao().getAll()
                        val locations = database.locationDao().getAllSimple()

                        val productByName = products.associateBy { it.productName }
                        val locationByName = locations.associateBy { it.locationName }

                        val snapshots = ArrayList<com.pingwei.lengkubao.data.db.entity.PcStockSnapshot>(stocksArray.size())
                        var appliedCount = 0
                        var skippedCount = 0

                        for (i in 0 until stocksArray.size()) {
                            val stockObj = stocksArray.get(i).asJsonObject
                            val locationName = stockObj.get("location_name")?.asString ?: ""
                            val spec = stockObj.get("spec")?.asString ?: ""
                            val currentQty = stockObj.get("current_quantity")?.asInt ?: 0

                            val product = productByName[spec]
                            val location = locationByName[locationName]

                            if (product == null || location == null) {
                                skippedCount++
                                continue
                            }

                            snapshots.add(
                                com.pingwei.lengkubao.data.db.entity.PcStockSnapshot(
                                    locationId = location.id,
                                    productId = product.id,
                                    spec = spec,
                                    locationName = locationName,
                                    currentQuantity = currentQty,
                                    snapshotTime = now
                                )
                            )

                            val existingStock = database.stockDao().getStock(product.id, location.id)
                            if (existingStock != null) {
                                database.stockDao().updateStockQuantity(
                                    productId = product.id,
                                    locationId = location.id,
                                    quantity = currentQty,
                                    timestamp = now,
                                    billNo = "PC_SNAPSHOT"
                                )
                            } else {
                                database.stockDao().insert(
                                    com.pingwei.lengkubao.data.db.entity.Stock(
                                        productId = product.id,
                                        productNo = product.productNo,
                                        productName = product.productName,
                                        locationId = location.id,
                                        currentQuantity = currentQty,
                                        reservedQuantity = 0,
                                        lastUpdated = now,
                                        lastBillNo = "PC_SNAPSHOT"
                                    )
                                )
                            }
                            appliedCount++
                        }

                        if (snapshots.isNotEmpty()) {
                            database.pcStockSnapshotDao().upsertAll(snapshots)
                        }

                        Log.i(TAG, "✅ PC库存快照同步完成: ${stocksArray.size()} 条，应用 $appliedCount 条，跳过 $skippedCount 条")
                    }

                    // ✅ 同步PC入库统计快照（按 date + 客户 + 库位 + 型号）
                    val inboundStatsObj = dataObject.getAsJsonObject("inbound_stats")
                    val inboundDailyArray = inboundStatsObj?.getAsJsonArray("daily")
                    if (inboundDailyArray != null) {
                        val now = System.currentTimeMillis()
                        val snapshots = ArrayList<com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot>(inboundDailyArray.size())
                        var appliedCount = 0
                        var skippedCount = 0

                        for (i in 0 until inboundDailyArray.size()) {
                            val obj = inboundDailyArray.get(i).asJsonObject
                            val date = obj.get("date")?.asString ?: ""
                            val customerNo = obj.get("customer_no")?.asString ?: ""
                            val customerName = obj.get("customer_name")?.asString ?: ""
                            val locationName = obj.get("location_name")?.asString ?: ""
                            val spec = obj.get("spec")?.asString ?: ""
                            val quantity = obj.get("quantity")?.asInt ?: 0
                            val amount = obj.get("amount")?.asDouble ?: 0.0
                            val orderCount = obj.get("order_count")?.asInt ?: 0

                            if (date.isBlank() || customerNo.isBlank() || locationName.isBlank() || spec.isBlank()) {
                                skippedCount++
                                continue
                            }

                            snapshots.add(
                                com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot(
                                    date = date,
                                    customerNo = customerNo,
                                    customerName = customerName,
                                    locationName = locationName,
                                    spec = spec,
                                    quantity = quantity,
                                    amount = amount,
                                    orderCount = orderCount,
                                    snapshotTime = now
                                )
                            )
                            appliedCount++
                        }

                        if (snapshots.isNotEmpty()) {
                            database.pcInboundDailySnapshotDao().upsertAll(snapshots)
                            com.pingwei.lengkubao.service.CustomerInboundStockBackfill(database)
                                .refreshFromPcSnapshots()
                        }

                        Log.i(TAG, "✅ PC入库统计快照同步完成: ${inboundDailyArray.size()} 条，应用 $appliedCount 条，跳过 $skippedCount 条")
                    }

                    val presaleBillsArray = dataObject.getAsJsonArray("presale_bills")
                    if (presaleBillsArray != null) {
                        var presaleApplied = 0
                        for (i in 0 until presaleBillsArray.size()) {
                            val billPayload = presaleBillsArray.get(i).asJsonObject
                            if (preSaleSyncApplier.applyBillPayload(billPayload, 0L)) {
                                presaleApplied++
                            }
                        }
                        Log.i(TAG, "✅ 预售单全量同步完成: ${presaleBillsArray.size()} 条，应用 $presaleApplied 条")
                    }

                    val presalePaymentsArray = dataObject.getAsJsonArray("presale_payments")
                    if (presalePaymentsArray != null) {
                        var paymentApplied = 0
                        for (i in 0 until presalePaymentsArray.size()) {
                            val paymentPayload = presalePaymentsArray.get(i).asJsonObject
                            if (preSaleSyncApplier.applyPaymentPayload(paymentPayload, 0L)) {
                                paymentApplied++
                            }
                        }
                        Log.i(TAG, "✅ 预售收款全量同步完成: ${presalePaymentsArray.size()} 条，应用 $paymentApplied 条")
                    }

                    val clientsCount = clientsArray?.size() ?: 0
                    val locationsCount = locationsArray?.size() ?: 0
                    val handlersCount = handlersArray?.size() ?: 0

                    val successMsg = "反向同步完成！客户:$clientsCount，库位:$locationsCount，经手人:$handlersCount"
                    Log.i(TAG, "✅ $successMsg")
                    _syncState.value = SyncState.Success(successMsg)
                    _messageChannel.send(successMsg)
                }

                fullSyncDataBuffer.clear()
                fullSyncTotalChunks = 0
                fullSyncReceivedChunks = 0

            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理全量同步数据失败: ${e.message}", e)
                _syncState.value = SyncState.Failed("反向同步失败: ${e.message}")
                _messageChannel.send("反向同步失败: ${e.message}")
                fullSyncDataBuffer.clear()
            }
        }
    }

    private suspend fun updateAdvanceDeductionStatus(dataType: String, customerNo: String, success: Boolean, dateInfo: String?) {
        withContext(Dispatchers.IO) {
            try {
                val status = if (success) 1 else 0
                when (dataType) {
                    "ADVANCE" -> {
                        if (dateInfo != null) {
                            val advances = database.advanceDao().getAdvancesByCustomerAndDate(customerNo, dateInfo)
                            for (advance in advances) {
                                database.advanceDao().updateSyncStatus(advance.id, status)
                                Log.d(TAG, "💾 更新预支款同步状态：$customerNo ${advance.advanceDate} -> ${if(success) "成功" else "失败"}")
                            }
                        } else {
                            val advance = database.advanceDao().getLatestUnsyncedByCustomer(customerNo)
                            if (advance != null) {
                                database.advanceDao().updateSyncStatus(advance.id, status)
                                Log.d(TAG, "💾 更新预支款同步状态：$customerNo -> ${if(success) "成功" else "失败"}")
                            }
                        }
                    }
                    "DEDUCTION" -> {
                        if (dateInfo != null) {
                            val deductions = database.deductionDao().getDeductionsByCustomerAndDate(customerNo, dateInfo)
                            for (deduction in deductions) {
                                database.deductionDao().updateSyncStatus(deduction.id, status)
                                Log.d(TAG, "💾 更新扣款同步状态：$customerNo ${deduction.deductDate} -> ${if(success) "成功" else "失败"}")
                            }
                        } else {
                            val deduction = database.deductionDao().getLatestUnsyncedByCustomer(customerNo)
                            if (deduction != null) {
                                database.deductionDao().updateSyncStatus(deduction.id, status)
                                Log.d(TAG, "💾 更新扣款同步状态：$customerNo -> ${if(success) "成功" else "失败"}")
                            }
                        }
                    }
                    else -> Log.w(TAG, "未知数据类型：$dataType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 更新预支扣款同步状态失败：$dataType $customerNo", e)
            }
        }
    }

    private suspend fun updateBaseSyncStatus(dataType: String, code: String, success: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val status = if (success) 1 else 0
                when (dataType) {
                    "LOCATION" -> {
                        val location = database.locationDao().getByLocationName(code)
                        Log.d(TAG, "🔍 查找库位: name=$code, result=${location != null}")
                        if (location != null) {
                            database.locationDao().updateSyncStatus(location.id, status)
                            Log.d(TAG, "💾 更新库位同步状态：${location.locationName} -> ${if(success) "成功" else "失败"}")
                        } else {
                            Log.w(TAG, "未找到库位名称：$code")
                            val allLocations = database.locationDao().getAllSimple()
                            Log.d(TAG, "当前所有库位: ${allLocations.map { it.locationName }}")
                        }
                    }
                    "OPERATOR" -> {
                        val operator = database.operatorDao().getByOperatorName(code)
                        Log.d(TAG, "🔍 查找经手人: name=$code, result=${operator != null}")
                        if (operator != null) {
                            database.operatorDao().updateSyncStatus(operator.id, status)
                            Log.d(TAG, "💾 更新经手人同步状态：${operator.name} -> ${if(success) "成功" else "失败"}")
                        } else {
                            Log.w(TAG, "未找到经手人名称：$code")
                            val allOperators = database.operatorDao().getAllSimple()
                            Log.d(TAG, "当前所有经手人: ${allOperators.map { it.name }}")
                        }
                    }
                    "CUSTOMER" -> {
                        val customer = database.customerDao().getByCustomerNo(code)
                        if (customer != null) {
                            database.customerDao().updateSyncStatus(customer.id, status)
                            Log.d(TAG, "💾 更新客户同步状态：$code -> ${if(success) "成功" else "失败"}")
                        } else {
                            Log.w(TAG, "未找到客户编号：$code")
                        }
                    }
                    else -> Log.w(TAG, "未知基础数据类型：$dataType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 更新基础数据同步状态失败：$dataType $code", e)
            }
        }
    }

    private suspend fun updateSyncStatus(billNo: String, dataType: String, success: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                when (dataType) {
                    "INBOUND" -> {
                        val bill = database.inStockBillDao().getBillByNo(billNo)
                        if (bill != null) {
                            database.inStockBillDao().updateSyncStatus(bill.id, if (success) 1 else 0)
                            Log.d(TAG, "💾 更新入库单同步状态：$billNo -> ${if(success) "成功" else "失败"}")
                        }
                    }
                    "SALES" -> {
                        val bill = database.saleBillDao().getBillByNo(billNo)
                        if (bill != null) {
                            database.saleBillDao().updateSyncStatus(bill.id, if (success) 1 else 0)
                            Log.d(TAG, "💾 更新销售单同步状态：$billNo -> ${if(success) "成功" else "失败"}")
                        }
                    }
                    "PACKAGING" -> {
                        val bill = database.packagingBillDao().getBillByNo(billNo)
                        if (bill != null) {
                            database.packagingBillDao().updateSyncStatus(bill.id, success)
                            Log.d(TAG, "💾 更新包装单同步状态：$billNo -> ${if(success) "成功" else "失败"}")
                        }
                    }
                    else -> Log.w(TAG, "未知数据类型：$dataType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 更新同步状态失败：$billNo", e)
            }
        }
    }

    private suspend fun handleSyncResult(data: String) {
        try {
            if (data.startsWith("同步成功")) {
                Log.i(TAG, "✅ 同步结果：$data")
                _syncState.value = SyncState.Success(data)
                _messageChannel.send(data)
            } else {
                Log.e(TAG, "❌ 同步结果：$data")
                _syncState.value = SyncState.Failed(data)
                _messageChannel.send("同步失败：$data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析同步结果失败: ${e.message}", e)
        }
    }

    suspend fun syncCustomer(customerId: Long) {
        try {
            val customer = database.customerDao().getCustomerById(customerId)
            if (customer != null) {
                val customerMap = mapOf(
                    "code" to customer.customerNo,
                    "name" to customer.customerName,
                    "phone" to (customer.phone ?: ""),
                    "customer_type" to customer.customerType,
                    "enabled" to customer.enabled
                )
                val json = gson.toJson(customerMap)
                sendMessage("CUSTOMER|$json")
                Log.i(TAG, "📤 客户数据已发送：${customer.customerName}，等待服务器确认")
            } else {
                Log.w(TAG, "⚠️  客户ID:$customerId 不存在，跳过同步")
                _syncState.value = SyncState.Failed("客户ID:$customerId 不存在")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 同步客户失败: ${e.message}", e)
            _syncState.value = SyncState.Failed("同步客户失败：${e.message}")
        }
    }

    suspend fun syncInStockBill(billId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("入库单")
                val bill = database.inStockBillDao().getBillById(billId)
                val items = database.inStockItemDao().getItemsByBillId(billId)

                if (bill != null && items.isNotEmpty()) {
                    var successCount = 0
                    var failCount = 0
                    val failedItems = mutableListOf<String>()

                    // ✅ 使用 forEachIndexed
                    items.forEachIndexed { index, item ->
                        val sourceRecordId = buildSourceRecordId("INBOUND", "${bill.billNo}_${index}")

                        val itemMap = mapOf(
                            "bill_no" to bill.billNo,
                            "client_code" to bill.customerNo,
                            "client_name" to (bill.customerName ?: ""),
                            "location" to bill.locationName,
                            "spec" to item.productName,
                            "quantity" to item.quantity.toString(),
                            "unit_price" to item.unitPrice.toString(),
                            "total_amount" to item.amount.toString(),
                            "handler" to bill.operatorName,
                            "creator" to "手持端",
                            "date" to dateFormat.format(Date(bill.createTime)),
                            "source_device_id" to getDeviceId(),
                            "source_record_id" to sourceRecordId
                        )
                        val json = gson.toJson(itemMap)

                        val success = sendMessageAndWaitForAck("INBOUND", json, sourceRecordId)

                        if (success) {
                            successCount++
                            Log.d(TAG, "📤 入库明细确认成功：${item.productName} x${item.quantity} (${index + 1}/${items.size})")
                        } else {
                            failCount++
                            failedItems.add(item.productName)
                            Log.e(TAG, "❌ 入库明细确认失败：${item.productName} x${item.quantity}")
                        }
                        delay(50)
                    }

                    if (failCount == 0) {
                        database.inStockBillDao().updateSyncStatus(billId, 1)
                        val successMsg = "入库单${bill.billNo}同步成功（${successCount}条明细）"
                        Log.i(TAG, "✅ $successMsg")
                        _syncState.value = SyncState.Success(successMsg)
                        _messageChannel.send(successMsg)
                        return@withContext true
                    } else {
                        val failMsg = "入库单${bill.billNo}同步部分失败：成功${successCount}条，失败${failCount}条（${failedItems.joinToString()}）"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "入库单ID:$billId 不存在或无明细，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步入库单失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }
    suspend fun syncSaleBill(billId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("销售单")
                val bill = database.saleBillDao().getBillById(billId)
                val items = database.saleItemDao().getItemsByBillId(billId)

                if (bill != null && items.isNotEmpty()) {
                    var successCount = 0
                    var failCount = 0
                    val failedItems = mutableListOf<String>()

                    items.forEachIndexed { index, item ->
                        val sourceRecordId = buildSourceRecordId("SALES", "${bill.billNo}_${index}")
                        val itemMap = mapOf(
                            "order_no" to bill.billNo,
                            "client_code" to bill.customerNo,
                            "client_name" to bill.customerName,
                            "spec" to item.productName,
                            "quantity" to item.quantity.toString(),
                            "unit_price" to item.salePrice.toString(),
                            "total_amount" to item.amount.toString(),
                            "date" to dateFormat.format(Date(bill.createTime)),
                            "handler" to bill.operatorName,
                            "creator" to "手持端",
                            "location" to bill.locationName,
                            "source_device_id" to getDeviceId(),
                            "source_record_id" to sourceRecordId
                        )
                        val json = gson.toJson(itemMap)

                        val success = sendMessageAndWaitForAck("SALES", json, sourceRecordId)

                        if (success) {
                            successCount++
                            Log.d(TAG, "📤 销售明细确认成功：${item.productName} x${item.quantity}")
                        } else {
                            failCount++
                            failedItems.add(item.productName)
                            Log.e(TAG, "❌ 销售明细确认失败：${item.productName} x${item.quantity}")
                        }
                        delay(50)
                    }

                    // ✅ 所有明细都成功后，才更新整单状态
                    if (failCount == 0) {
                        database.saleBillDao().updateSyncStatus(billId, 1)
                        val successMsg = "销售单${bill.billNo}同步成功（${successCount}条明细）"
                        Log.i(TAG, "✅ $successMsg")
                        _syncState.value = SyncState.Success(successMsg)
                        _messageChannel.send(successMsg)
                        return@withContext true
                    } else {
                        val failMsg = "销售单${bill.billNo}同步部分失败：成功${successCount}条，失败${failCount}条（${failedItems.joinToString()}）"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "销售单ID:$billId 不存在或无明细，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步销售单失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncPreSaleBill(billId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!canSyncPresaleData()) {
                    val warnMsg = "预售单同步跳过：年份未对齐"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
                _syncState.value = SyncState.Syncing("预售单")
                var bill = database.preSaleBillDao().getBillById(billId)
                var items = database.preSaleItemDao().getItemsByBillId(billId)

                if (bill != null && items.isNotEmpty()) {
                    var sourceRecordId = bill.sourceRecordId
                    if (sourceRecordId.isNullOrBlank()) {
                        sourceRecordId = SourceRecordIdUtils.buildPresaleBill(billId, context)
                        database.preSaleBillDao().updateSourceIdentity(
                            billId,
                            sourceRecordId,
                            SourceRecordIdUtils.getDeviceId(context),
                        )
                        bill = database.preSaleBillDao().getBillById(billId) ?: bill
                    }
                    val itemsJson = items.map { item ->
                        mapOf(
                            "spec" to item.productName,
                            "quantity" to item.quantity.toString(),
                            "shipped_quantity" to item.shippedQuantity.toString(),
                            "unit_price" to item.salePrice.toString(),
                            "total_amount" to item.amount.toString()
                        )
                    }
                    val billMap = mutableMapOf<String, Any?>(
                        "bill_no" to bill.billNo,
                        "buyer_code" to bill.buyerNo,
                        "buyer_name" to bill.buyerName,
                        "location" to bill.locationName,
                        "sale_mode" to bill.saleMode,
                        "bill_status" to bill.status,
                        "total_amount" to bill.totalAmount.toString(),
                        "paid_amount" to bill.paidAmount.toString(),
                        "handler" to bill.operatorName,
                        "remark" to bill.remark,
                        "date" to dateFormat.format(java.util.Date(bill.createTime)),
                        "items" to itemsJson,
                        "source_device_id" to (bill.sourceDeviceId ?: SourceRecordIdUtils.getDeviceId(context)),
                        "source_record_id" to sourceRecordId,
                    )
                    if (FiscalYearManager.isInitialized) {
                        billMap["fiscal_year"] = FiscalYearManager.activeYear
                    }
                    val json = gson.toJson(billMap)
                    Log.i(TAG, "📤 上传预售单 ${bill.billNo} mode=${bill.saleMode} status=${bill.status}")
                    val contentKey = "${bill.saleMode}|${bill.status}|${bill.paidAmount}"
                    val success = sendMessageAndWaitForAck("PRESALE", json, sourceRecordId, contentKey)

                    if (success) {
                        database.preSaleBillDao().updateSyncStatus(billId, 1)
                        val successMsg = "预售单[${bill.billNo}]同步成功 mode=${bill.saleMode}"
                        Log.i(TAG, "✅ $successMsg")
                        _syncState.value = SyncState.Success(successMsg)
                        _messageChannel.send(successMsg)
                        return@withContext true
                    } else {
                        database.preSaleBillDao().updateSyncStatus(billId, 0)
                        val failMsg = "预售单[${bill.billNo}]同步失败"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "预售单ID:$billId 不存在或无明细，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步预售单失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncPreSalePayment(paymentId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!canSyncPresaleData()) {
                    val warnMsg = "预售收款同步跳过：年份未对齐"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
                _syncState.value = SyncState.Syncing("预售收款")
                val payment = database.paymentRecordDao().getById(paymentId)
                if (payment == null) {
                    val warnMsg = "预售收款ID:$paymentId 不存在，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }

                val bill = database.preSaleBillDao().getBillById(payment.billId)
                if (bill == null) {
                    val warnMsg = "预售收款关联单据不存在：billId=${payment.billId}"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }

                val sourceRecordId = payment.sourceRecordId?.takeIf { it.isNotBlank() }
                    ?: SourceRecordIdUtils.buildPresalePayment(paymentId, context).also { built ->
                        database.paymentRecordDao().updateSourceIdentity(
                            paymentId,
                            built,
                            SourceRecordIdUtils.getDeviceId(context),
                        )
                    }
                val paymentMap = mutableMapOf<String, Any?>(
                    "bill_no" to bill.billNo,
                    "buyer_code" to bill.buyerNo,
                    "amount" to payment.amount.toString(),
                    "pay_method" to payment.payMethod,
                    "pay_time" to payment.payTime.toString(),
                    "remark" to payment.remark,
                    "source_device_id" to (payment.sourceDeviceId ?: SourceRecordIdUtils.getDeviceId(context)),
                    "source_record_id" to sourceRecordId,
                )
                if (FiscalYearManager.isInitialized) {
                    paymentMap["fiscal_year"] = FiscalYearManager.activeYear
                }
                val json = gson.toJson(paymentMap)
                val success = sendMessageAndWaitForAck("PRESALE_PAYMENT", json, sourceRecordId)

                if (success) {
                    database.paymentRecordDao().updateSyncStatus(paymentId, 1)
                    val successMsg = "预售收款[${bill.billNo} ${payment.amount}]同步成功"
                    Log.i(TAG, "✅ $successMsg")
                    _syncState.value = SyncState.Success(successMsg)
                    _messageChannel.send(successMsg)
                    return@withContext true
                } else {
                    val failMsg = "预售收款[${bill.billNo}]同步失败"
                    Log.e(TAG, "❌ $failMsg")
                    _syncState.value = SyncState.Failed(failMsg)
                    _messageChannel.send(failMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步预售收款失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncPreSaleOutbound(outboundId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!canSyncPresaleData()) {
                    val warnMsg = "预售出库同步跳过：年份未对齐"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
                _syncState.value = SyncState.Syncing("预售出库")
                val record = database.outboundRecordDao().getById(outboundId)
                if (record == null) {
                    val warnMsg = "预售出库ID:$outboundId 不存在，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }

                val bill = database.preSaleBillDao().getBillById(record.billId)
                if (bill == null) {
                    val warnMsg = "预售出库关联单据不存在：billId=${record.billId}"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }

                val recordItems = database.outboundRecordItemDao().getByRecordId(outboundId)
                val sourceRecordId = record.sourceRecordId?.takeIf { it.isNotBlank() }
                    ?: SourceRecordIdUtils.buildPresaleOutbound(outboundId, context).also { built ->
                        database.outboundRecordDao().updateSourceIdentity(
                            outboundId,
                            built,
                            SourceRecordIdUtils.getDeviceId(context),
                        )
                    }

                val itemsJson = recordItems.map { item ->
                    mapOf(
                        "spec" to item.productName,
                        "product_no" to item.productNo,
                        "quantity" to item.quantity.toString(),
                        "unit" to item.unit,
                    )
                }
                val outboundMap = mutableMapOf<String, Any?>(
                    "bill_no" to bill.billNo,
                    "buyer_code" to bill.buyerNo,
                    "ship_time" to record.shipTime.toString(),
                    "remark" to record.remark,
                    "bill_status" to bill.status,
                    "sale_mode" to bill.saleMode,
                    "items" to itemsJson,
                    "source_device_id" to (record.sourceDeviceId ?: SourceRecordIdUtils.getDeviceId(context)),
                    "source_record_id" to sourceRecordId,
                )
                if (FiscalYearManager.isInitialized) {
                    outboundMap["fiscal_year"] = FiscalYearManager.activeYear
                }
                val json = gson.toJson(outboundMap)
                val success = sendMessageAndWaitForAck("PRESALE_OUTBOUND", json, sourceRecordId)

                if (success) {
                    database.outboundRecordDao().updateSyncStatus(outboundId, 1)
                    val successMsg = "预售出库[${bill.billNo}]同步成功"
                    Log.i(TAG, "✅ $successMsg")
                    _syncState.value = SyncState.Success(successMsg)
                    _messageChannel.send(successMsg)
                    return@withContext true
                } else {
                    val failMsg = "预售出库[${bill.billNo}]同步失败"
                    Log.e(TAG, "❌ $failMsg")
                    _syncState.value = SyncState.Failed(failMsg)
                    _messageChannel.send(failMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步预售出库失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncLedgerEntry(entryId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("收支流水")
                val entry = database.ledgerEntryDao().getById(entryId)
                if (entry == null) {
                    val warnMsg = "收支流水ID:$entryId 不存在，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }

                val sourceRecordId = buildSourceRecordId("LEDGER", entryId.toString())
                val entryMap = mapOf(
                    "entry_no" to entry.entryNo,
                    "type" to entry.type,
                    "category_id" to entry.categoryId.toString(),
                    "category_name" to entry.categoryName,
                    "amount" to entry.amount.toString(),
                    "entry_date" to entry.entryDate,
                    "remark" to entry.remark,
                    "status" to entry.status.toString(),
                    "source_device_id" to getDeviceId(),
                    "source_record_id" to sourceRecordId
                )
                val json = gson.toJson(entryMap)
                val success = sendMessageAndWaitForAck("LEDGER", json, sourceRecordId)

                if (success) {
                    database.ledgerEntryDao().updateSyncStatus(entryId, 1, System.currentTimeMillis())
                    val successMsg = "收支流水[${entry.entryNo}]同步成功"
                    Log.i(TAG, "✅ $successMsg")
                    _syncState.value = SyncState.Success(successMsg)
                    _messageChannel.send(successMsg)
                    return@withContext true
                } else {
                    val failMsg = "收支流水[${entry.entryNo}]同步失败"
                    Log.e(TAG, "❌ $failMsg")
                    _syncState.value = SyncState.Failed(failMsg)
                    _messageChannel.send(failMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步收支流水失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncPackagingBill(billId: Long): PackagingSyncResult {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("包装单")
                val bill = database.packagingBillDao().getBillById(billId)
                val items = database.packagingItemDao().getItemsByBillId(billId)

                if (bill != null && items.isNotEmpty()) {
                    var successCount = 0
                    var failCount = 0
                    val failedItems = mutableListOf<String>()
                    val duplicateNotices = linkedSetOf<String>()
                    var lastFailureMessage: String? = null

                    items.forEach { item ->
                        val itemKey = if (item.itemId > 0L) item.itemId else item.packagingType
                        val sourceRecordId = buildSourceRecordId("PACKAGING", "${billId}_${itemKey}")
                        val ackKey = "PACKAGING|$sourceRecordId"
                        val itemMap = mutableMapOf(
                            "order_no" to bill.billNo,
                            "client_code" to bill.customerNo,
                            "client_name" to bill.customerName,
                            "pack_type" to item.packagingType,
                            "quantity" to item.quantity.toString(),
                            "unit_price" to item.unitPrice.toString(),
                            "total_amount" to item.amount.toString(),
                            "handler" to bill.operatorName,
                            "creator" to "手持端",
                            "date" to bill.billDate,
                            "pack_flag" to (bill.packagingTypeFlag ?: "TAKE"),
                            "source_device_id" to getDeviceId(),
                            "source_record_id" to sourceRecordId
                        )

                        if (bill.remark.isNotEmpty()) {
                            itemMap["remarks"] = bill.remark
                        }

                        val json = gson.toJson(itemMap)

                        val success = sendMessageAndWaitForAck("PACKAGING", json, sourceRecordId)

                        if (success) {
                            successCount++
                            packagingIdempotentNotices.remove(ackKey)?.let { duplicateNotices.add(it) }
                            Log.d(TAG, "📤 包装明细确认成功：${item.packagingType} x${item.quantity}")
                        } else {
                            failCount++
                            failedItems.add(item.packagingType)
                            packagingAckFailureMessages.remove(ackKey)?.let { lastFailureMessage = it }
                            Log.e(TAG, "❌ 包装明细确认失败：${item.packagingType} x${item.quantity}")
                        }
                        delay(50)
                    }

                    if (failCount == 0) {
                        database.packagingBillDao().updateSyncStatus(billId, true)
                        val duplicateSummary = duplicateNotices.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
                        val successMsg = "包装单${bill.billNo}同步成功，标记=${bill.packagingTypeFlag}（${successCount}条明细）"
                        Log.i(TAG, "✅ $successMsg")
                        _messageChannel.send(successMsg)
                        _syncState.value = SyncState.Success(successMsg)
                        return@withContext PackagingSyncResult(
                            success = true,
                            duplicateNotice = duplicateSummary,
                        )
                    } else {
                        clearConfirmedItemsForPackagingBill(billId)
                        database.packagingBillDao().updateSyncStatus(billId, false)
                        val failMsg = lastFailureMessage
                            ?: "包装单${bill.billNo}同步部分失败：成功${successCount}条，失败${failCount}条（${failedItems.joinToString()}）"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext PackagingSyncResult(
                            success = false,
                            errorMessage = failMsg,
                        )
                    }
                } else {
                    val warnMsg = "包装单ID:$billId 不存在或无明细，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext PackagingSyncResult(success = false, errorMessage = warnMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "同步包装单失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext PackagingSyncResult(success = false, errorMessage = errorMsg)
            }
        }
    }

    suspend fun syncLocation(locationId: Long) {
        try {
            val location = database.locationDao().getLocationById(locationId)
                ?: throw Exception("库位不存在，ID: $locationId")

            val locationMap = mapOf(
                "name" to location.locationName
            )
            val json = gson.toJson(locationMap)
            sendMessage("LOCATION|$json")

            Log.i(TAG, "📤 库位数据已发送：${location.locationName}")
            _syncState.value = SyncState.Success("库位同步成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 同步库位失败: ${e.message}")
            _syncState.value = SyncState.Failed("同步库位失败：${e.message}")
        }
    }

    suspend fun syncOperator(operatorId: Long) {
        try {
            val operator = database.operatorDao().getOperatorById(operatorId)
                ?: throw Exception("经手人不存在，ID: $operatorId")

            val operatorMap = mapOf(
                "name" to operator.name
            )
            val json = gson.toJson(operatorMap)
            sendMessage("OPERATOR|$json")

            Log.i(TAG, "📤 经手人数据已发送：${operator.name}")
            _syncState.value = SyncState.Success("经手人同步成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 同步经手人失败: ${e.message}")
            _syncState.value = SyncState.Failed("同步经手人失败：${e.message}")
        }
    }

    suspend fun syncAllConfigs(
        onProgress: ((current: Int, total: Int, type: String) -> Unit)? = null,
        onResult: ((success: Boolean, message: String) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("全量同步基础配置")

                val locations = database.locationDao().getAllSimple()
                val operators = database.operatorDao().getAllSimple()
                val customers = database.customerDao().getAllSimple()

                val total = locations.size + operators.size + customers.size
                var current = 0
                var successCount = 0
                var failCount = 0
                val failDetails = mutableListOf<String>()

                Log.i(TAG, "📊 开始全量同步基础配置（库位/经手人/客户），总计 $total 条数据")

                if (locations.isNotEmpty()) {
                    onProgress?.invoke(current, total, "库位")
                    locations.forEach { location ->
                        try {
                            syncLocation(location.id)
                            successCount++
                            Log.d(TAG, "✅ 库位同步成功：${location.locationName}")
                        } catch (e: Exception) {
                            failCount++
                            failDetails.add("库位[${location.locationName}]: ${e.message}")
                            Log.e(TAG, "❌ 库位同步失败：${location.locationName}", e)
                        }
                        current++
                        onProgress?.invoke(current, total, "库位")
                        delay(100)
                    }
                }

                if (operators.isNotEmpty()) {
                    onProgress?.invoke(current, total, "经手人")
                    operators.forEach { operator ->
                        try {
                            syncOperator(operator.id)
                            successCount++
                            Log.d(TAG, "✅ 经手人同步成功：${operator.name}")
                        } catch (e: Exception) {
                            failCount++
                            failDetails.add("经手人[${operator.name}]: ${e.message}")
                            Log.e(TAG, "❌ 经手人同步失败：${operator.name}", e)
                        }
                        current++
                        onProgress?.invoke(current, total, "经手人")
                        delay(100)
                    }
                }

                if (customers.isNotEmpty()) {
                    onProgress?.invoke(current, total, "客户")
                    customers.forEach { customer ->
                        try {
                            syncCustomer(customer.id)
                            successCount++
                            Log.d(TAG, "✅ 客户同步成功：${customer.customerName}")
                        } catch (e: Exception) {
                            failCount++
                            failDetails.add("客户[${customer.customerName}]: ${e.message}")
                            Log.e(TAG, "❌ 客户同步失败：${customer.customerName}", e)
                        }
                        current++
                        onProgress?.invoke(current, total, "客户")
                        delay(100)
                    }
                }

                val resultMsg = "基础配置全量同步完成：成功 $successCount 条，失败 $failCount 条"

                if (failCount > 0) {
                    val errorDetails = failDetails.joinToString("\n")
                    Log.e(TAG, "❌ $resultMsg\n失败详情：\n$errorDetails")
                    _syncState.value = SyncState.Failed(resultMsg)
                    onResult?.invoke(false, resultMsg)
                    _messageChannel.send("❌ $resultMsg")
                } else {
                    Log.i(TAG, "✅ $resultMsg")
                    _syncState.value = SyncState.Success(resultMsg)
                    _messageChannel.send("✅ $resultMsg")

                    Log.i(TAG, "🔄 正向同步完成，开始自动拉取服务器最新数据...")
                    _messageChannel.send("正向同步完成，正在拉取服务器最新数据...")

                    try {
                        val reverseResult = pullFullSyncFromServer(
                            skipUpload = true,  // ✅ 跳过上传，避免重复
                            onProgress = { revCurrent, revTotal, revType ->
                                Log.d(TAG, "反向同步进度: $revType $revCurrent/$revTotal")
                            },
                            onResult = { revSuccess, revMessage ->
                                if (revSuccess) {
                                    Log.i(TAG, "✅ 反向同步成功: $revMessage")
                                    _messageChannel.trySend("✅ 已更新服务器最新数据")
                                } else {
                                    Log.w(TAG, "⚠️ 反向同步失败: $revMessage")
                                    _messageChannel.trySend("⚠️ 拉取服务器数据失败: $revMessage")
                                }
                            }
                        )

                        if (reverseResult) {
                            val finalMsg = "$resultMsg，已更新服务器最新数据"
                            Log.i(TAG, "✅ $finalMsg")
                            onResult?.invoke(true, finalMsg)
                        } else {
                            val finalMsg = "$resultMsg，但拉取服务器数据失败"
                            Log.w(TAG, "⚠️ $finalMsg")
                            onResult?.invoke(true, finalMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "反向同步异常: ${e.message}"
                        Log.e(TAG, "❌ $errorMsg", e)
                        _messageChannel.send(errorMsg)
                        onResult?.invoke(true, "$resultMsg，但反向同步异常: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                val errorMsg = "全量同步失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                onResult?.invoke(false, errorMsg)
                _messageChannel.send(errorMsg)
            }
        }
    }

    suspend fun checkUnsyncedConfigs(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val unsyncedCustomers = pendingConfigCount("CUSTOMER") {
                    database.customerDao().getAllSimple().count { it.syncStatus == 0 }
                }
                val unsyncedLocations = pendingConfigCount("LOCATION") {
                    database.locationDao().getAllSimple().count { it.syncStatus == 0 }
                }
                val unsyncedOperators = pendingConfigCount("OPERATOR") {
                    database.operatorDao().getAllSimple().count { it.syncStatus == 0 }
                }
                val unsyncedProducts = syncDao.countPendingOpsByEntityType("PRODUCT")
                val unsyncedPackTypes = syncDao.countPendingOpsByEntityType("PACK_TYPE")

                val result = mapOf(
                    "库位" to unsyncedLocations,
                    "经手人" to unsyncedOperators,
                    "客户" to unsyncedCustomers,
                    "商品型号" to unsyncedProducts,
                    "包装类型" to unsyncedPackTypes,
                    "总计" to (
                        unsyncedLocations + unsyncedOperators + unsyncedCustomers +
                            unsyncedProducts + unsyncedPackTypes
                        ),
                )

                Log.i(TAG, "📊 未同步基础配置检查结果：$result")
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查未同步配置失败：${e.message}", e)
                return@withContext emptyMap()
            }
        }
    }

    private suspend fun pendingConfigCount(entityType: String, legacyCount: suspend () -> Int): Int {
        val oplogCount = syncDao.countPendingOpsByEntityType(entityType)
        return maxOf(oplogCount, legacyCount())
    }

    suspend fun autoBidirectionalDeltaSyncOnConnect(skipHelloSync: Boolean = false) {
        withContext(Dispatchers.IO) {
            if (!isConnected.get() || !isRegistered.get()) {
                Log.w(TAG, "增量同步跳过：未连接或未注册")
                return@withContext
            }
            try {
                val deviceId = getDeviceId()
                val now = System.currentTimeMillis()
                val cursor = syncDao.getCursor(deviceId) ?: SyncDeviceCursor(
                    deviceId = deviceId,
                    updatedAt = now,
                )
                syncDao.upsertCursor(cursor)

                if (!skipHelloSync) {
                    if (!ensureSyncYearAligned()) {
                        return@withContext
                    }
                    if (!isConnected.get() || !isRegistered.get()) {
                        return@withContext
                    }
                }

                val refreshedCursor = syncDao.getCursor(deviceId) ?: cursor
                val latestAckedSeq = ensureDownstreamConfigAligned(deviceId, refreshedCursor)

                val uploaded = uploadPendingConfigOps(deviceId)
                val successMsg = "双向增量同步完成：下行至$latestAckedSeq，上行${uploaded}条"
                _syncState.value = SyncState.Success(successMsg)
                _messageChannel.send(successMsg)
                Log.i(TAG, "✅ $successMsg")
            } catch (e: Exception) {
                val err = "双向增量同步失败：${e.message}"
                _syncState.value = SyncState.Failed(err)
                _messageChannel.send(err)
                Log.e(TAG, "❌ $err", e)
            }
        }
    }

    suspend fun pullFullConfigFromPc(
        onProgress: ((current: Int, total: Int, type: String) -> Unit)? = null,
        onResult: ((success: Boolean, message: String) -> Unit)? = null,
    ) {
        withContext(Dispatchers.IO) {
            if (!isConnected.get() || !isRegistered.get()) {
                val err = "未连接或未注册，请先连接电脑"
                _syncState.value = SyncState.Failed(err)
                onResult?.invoke(false, err)
                return@withContext
            }
            try {
                val deviceId = getDeviceId()
                _syncState.value = SyncState.Syncing("从电脑全量拉取基础配置")
                uploadPendingConfigOps(deviceId)
                val fullOk = pullFullSyncFromServer(skipUpload = true, onProgress = onProgress)
                if (!fullOk) {
                    val err = "全量拉取失败，请检查连接后重试"
                    _syncState.value = SyncState.Failed(err)
                    onResult?.invoke(false, err)
                    return@withContext
                }
                syncDao.updateFirstFullSyncDone(deviceId, true, System.currentTimeMillis())
                val baselineSeq = resolveFullSyncBaselineSeq()
                if (baselineSeq > 0L) {
                    syncDao.updateLastAckedSeq(deviceId, baselineSeq, System.currentTimeMillis())
                }
                val ack = pullAndApplyDelta(deviceId, baselineSeq.coerceAtLeast(0L))
                if (ack != null) {
                    syncDao.updateLastAckedSeq(deviceId, ack, System.currentTimeMillis())
                }
                val msg = "全量配置已与电脑对齐（基线序号=${ack ?: baselineSeq}）"
                _syncState.value = SyncState.Success(msg)
                com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier.notifyChanged()
                onResult?.invoke(true, msg)
            } catch (e: Exception) {
                val err = "全量拉取失败：${e.message}"
                _syncState.value = SyncState.Failed(err)
                onResult?.invoke(false, err)
            }
        }
    }

    suspend fun bidirectionalConfigSync(
        onResult: ((success: Boolean, message: String) -> Unit)? = null,
    ) {
        withContext(Dispatchers.IO) {
            if (!isConnected.get() || !isRegistered.get()) {
                val err = "未连接服务器，请先连接"
                _syncState.value = SyncState.Failed(err)
                onResult?.invoke(false, err)
                return@withContext
            }
            try {
                val deviceId = getDeviceId()
                val cursor = syncDao.getCursor(deviceId) ?: SyncDeviceCursor(deviceId = deviceId)
                syncDao.upsertCursor(cursor)

                _syncState.value = SyncState.Syncing("双向增量同步配置")
                var latestAckedSeq = ensureDownstreamConfigAligned(deviceId, cursor)
                val uploaded = uploadPendingConfigOps(deviceId)
                latestAckedSeq = pullAndApplyDelta(
                    deviceId,
                    syncDao.getCursor(deviceId)?.lastAckedSeq ?: latestAckedSeq,
                ) ?: recoverDownstreamConfigFromPc(deviceId) ?: latestAckedSeq
                if (latestAckedSeq != null) {
                    syncDao.updateLastAckedSeq(deviceId, latestAckedSeq, System.currentTimeMillis())
                }
                val msg = "配置同步完成：上行${uploaded}条，最新序号=${latestAckedSeq ?: cursor.lastAckedSeq}"
                _syncState.value = SyncState.Success(msg)
                _messageChannel.send(msg)
                onResult?.invoke(true, msg)
            } catch (e: Exception) {
                val err = "配置同步失败：${e.message}"
                _syncState.value = SyncState.Failed(err)
                onResult?.invoke(false, err)
            }
        }
    }

    private suspend fun tryPullConfigViaCrsql(deviceId: String): Boolean {
        if (!CrsqlHelper.isAvailable || !isConnected.get() || !isRegistered.get()) return false
        return try {
            val since = syncDao.getCursor(deviceId)?.lastAckedSeq ?: 0L
            val req = gson.toJson(mapOf("since_db_version" to since))
            val resp = sendMessageAndWaitForCommand("CONFIG_PULL|$req", "CONFIG_PULL_RESP", 30000L)
                ?: return false
            if (!looksLikeCompleteJson(resp)) return false
            val obj = JsonParser.parseString(resp).asJsonObject
            val available = obj.get("available")?.asBoolean ?: false
            if (!available) return false
            val changes = obj.getAsJsonArray("changes")
            Log.i(TAG, "📥 CONFIG_PULL cr-sqlite 收到 ${changes?.size() ?: 0} 条变更")
            true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ CONFIG_PULL cr-sqlite 失败: ${e.message}")
            false
        }
    }

    suspend fun pushPendingConfigOps(): Int {
        if (!isConnected.get() || !isRegistered.get()) return 0
        return uploadPendingConfigOps(getDeviceId())
    }

    private suspend fun markConfigSyncedAfterPush(op: SyncLocalOpLog) {
        when (op.entityType) {
            "CUSTOMER" -> database.customerDao().getByCustomerNo(op.entityKey)?.let {
                database.customerDao().updateSyncStatus(it.id, 1)
            }
            "LOCATION" -> database.locationDao().getByLocationName(op.entityKey)?.let {
                database.locationDao().updateSyncStatus(it.id, 1)
            }
            "OPERATOR" -> database.operatorDao().getByOperatorName(op.entityKey)?.let {
                database.operatorDao().updateSyncStatus(it.id, 1)
            }
            "PRODUCT" -> database.productDao().getByProductNo(op.entityKey)?.let {
                database.productDao().updateSyncStatus(it.id, 1)
            }
            "PACK_TYPE" -> { /* 无 syncStatus 字段，未同步由 oplog 统计 */ }
        }
    }

    private suspend fun uploadPendingConfigOps(deviceId: String): Int {
        val ops = syncDao.getPendingLocalOps(500)
        if (ops.isEmpty()) return 0
        val opByOriginId = ops.associateBy { it.originOpId }
        val payload = ops.map {
            mapOf(
                "entity_type" to it.entityType,
                "entity_key" to it.entityKey,
                "op_type" to it.opType,
                "payload" to JsonParser.parseString(it.payloadJson),
                "origin_device_id" to deviceId,
                "origin_op_id" to it.originOpId,
                "created_at" to it.createdAt,
            )
        }
        val req = gson.toJson(mapOf("ops" to payload))
        val response = sendMessageAndWaitForCommand("PUSH_CHANGES|$req", "PUSH_CHANGES_ACK", 45000L)
        if (response == null) {
            Log.w(TAG, "⏳ 等待 PUSH_CHANGES_ACK 超时，保留本地待上传变更")
            return 0
        }
        if (!looksLikeCompleteJson(response)) {
            Log.e(TAG, "❌ PUSH_CHANGES_ACK 不是完整JSON：${response.take(200)}")
            return 0
        }
        val ackObj = try {
            JsonParser.parseString(response).asJsonObject
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "❌ PUSH_CHANGES_ACK JSON解析失败：${response.take(200)}", e)
            return 0
        }
        val acked = ackObj.getAsJsonArray("acked_ops")
        var pushedCount = 0
        if (acked != null) {
            for (i in 0 until acked.size()) {
                val ack = acked.get(i).asJsonObject
                val originOpId = ack.get("origin_op_id")?.asString ?: continue
                val commitSeq = ack.get("commit_seq")?.asLong
                syncDao.markLocalOpPushed(originOpId, System.currentTimeMillis(), commitSeq)
                opByOriginId[originOpId]?.let { markConfigSyncedAfterPush(it) }
                pushedCount++
            }
        }
        if (pushedCount > 0) {
            val maxPushedId = syncDao.getPendingLocalOps(1).firstOrNull()?.id?.minus(1) ?: Long.MAX_VALUE
            syncDao.deletePushedOpsBefore(maxPushedId)
            com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier.notifyChanged()
        }
        return pushedCount
    }

    private suspend fun needsFullConfigResync(cursor: SyncDeviceCursor): Boolean {
        if (!cursor.firstFullSyncDone || cursor.lastAckedSeq <= 0L) {
            return false
        }
        val customers = database.customerDao().getAllSimple()
        val locations = database.locationDao().getAllSimple()
        val operators = database.operatorDao().getAllSimple()
        val products = database.productDao().getAll()
        val packTypes = database.packagingTypeDao().getAll()
        return (customers.isEmpty() && locations.isEmpty() && operators.isEmpty()) ||
            (products.isEmpty() && packTypes.isEmpty())
    }

    private suspend fun resetSyncCursorForFullResync(deviceId: String) {
        val now = System.currentTimeMillis()
        syncDao.upsertCursor(
            (syncDao.getCursor(deviceId) ?: SyncDeviceCursor(deviceId = deviceId)).copy(
                firstFullSyncDone = false,
                lastAckedSeq = 0L,
                updatedAt = now,
            )
        )
        Log.w(TAG, "🔄 已重置同步游标，deviceId=$deviceId")
    }

    /**
     * 全量拉取 PC 基础配置并补齐增量；仅在需要时调用。
     * @return 成功后的 lastAckedSeq，失败返回 null
     */
    private suspend fun recoverDownstreamConfigFromPc(deviceId: String): Long? {
        Log.w(TAG, "⚠️ 尝试全量恢复基础配置，deviceId=$deviceId")
        resetSyncCursorForFullResync(deviceId)
        _syncState.value = SyncState.Syncing("从电脑全量恢复基础配置")
        val fullOk = pullFullSyncFromServer(skipUpload = true)
        if (!fullOk) {
            Log.e(TAG, "❌ 全量恢复失败，保留游标以便下次重试")
            return null
        }
        syncDao.updateFirstFullSyncDone(deviceId, true, System.currentTimeMillis())
        val baselineSeq = resolveFullSyncBaselineSeq()
        if (baselineSeq > 0L) {
            syncDao.updateLastAckedSeq(deviceId, baselineSeq, System.currentTimeMillis())
            Log.i(TAG, "⏭️ 全量恢复基线 seq=$baselineSeq，跳过快照前历史增量")
        }
        val ack = pullAndApplyDelta(deviceId, baselineSeq.coerceAtLeast(0L))
        if (ack != null) {
            syncDao.updateLastAckedSeq(deviceId, ack, System.currentTimeMillis())
        }
        return ack
    }

    /**
     * 连接后对齐 PC 下行配置：检测游标异常、首次全量、增量补齐。
     */
    private suspend fun ensureDownstreamConfigAligned(
        deviceId: String,
        initialCursor: SyncDeviceCursor,
    ): Long {
        if (CrsqlHelper.isAvailable) {
            tryPullConfigViaCrsql(deviceId)
        }

        var cursor = initialCursor
        if (needsFullConfigResync(cursor)) {
            Log.w(TAG, "⚠️ 游标显示已同步但本地无基础数据，触发全量恢复")
            val recovered = recoverDownstreamConfigFromPc(deviceId)
            if (recovered != null) {
                return recovered
            }
            cursor = syncDao.getCursor(deviceId) ?: cursor
        }

        if (!cursor.firstFullSyncDone) {
            Log.i(TAG, "🔄 首次连接，从电脑拉取基础配置对齐")
            _syncState.value = SyncState.Syncing("首次从电脑拉取配置")
            val fullOk = pullFullSyncFromServer(
                skipUpload = true,
                onResult = { success, msg ->
                    Log.i(TAG, "首次拉取配置结果: success=$success, msg=$msg")
                },
            )
            if (fullOk) {
                syncDao.updateFirstFullSyncDone(deviceId, true, System.currentTimeMillis())
                val baselineSeq = resolveFullSyncBaselineSeq()
                if (baselineSeq > 0L) {
                    syncDao.updateLastAckedSeq(deviceId, baselineSeq, System.currentTimeMillis())
                    Log.i(TAG, "⏭️ 全量基线已建立，跳过快照前历史增量，baselineSeq=$baselineSeq")
                }
                cursor = syncDao.getCursor(deviceId) ?: cursor
                val ackSeq = pullAndApplyDelta(deviceId, cursor.lastAckedSeq)
                if (ackSeq != null) {
                    syncDao.updateLastAckedSeq(deviceId, ackSeq, System.currentTimeMillis())
                }
                return ackSeq ?: cursor.lastAckedSeq
            } else {
                Log.e(TAG, "❌ 首次全量拉取失败，下次连接将重试")
                return cursor.lastAckedSeq
            }
        }

        var latestAckedSeq = pullAndApplyDelta(deviceId, cursor.lastAckedSeq)
        if (latestAckedSeq == null) {
            Log.w(TAG, "⚠️ 增量未补齐（fromSeq=${cursor.lastAckedSeq}），降级全量恢复")
            latestAckedSeq = recoverDownstreamConfigFromPc(deviceId)
        }
        if (latestAckedSeq != null) {
            syncDao.updateLastAckedSeq(deviceId, latestAckedSeq, System.currentTimeMillis())
        }
        return latestAckedSeq ?: cursor.lastAckedSeq
    }

    private suspend fun getDownstreamDeltaSkipReason(deviceId: String): String? {
        if (fullSyncInProgress.get()) {
            return "全量同步进行中"
        }
        val cursor = syncDao.getCursor(deviceId)
        if (cursor != null && !cursor.firstFullSyncDone) {
            return "首次全量未完成"
        }
        return null
    }

    private suspend fun pullAndApplyDelta(deviceId: String, fromSeq: Long): Long? {
        getDownstreamDeltaSkipReason(deviceId)?.let { reason ->
            Log.i(TAG, "⏭️ 跳过增量拉取：$reason")
            return null
        }
        val req = gson.toJson(mapOf("device_id" to deviceId, "last_acked_seq" to fromSeq))
        val response = sendMessageAndWaitForCommand("PULL_DELTA_REQ|$req", "PULL_DELTA_RESP", 60000L) ?: return null
        if (!looksLikeCompleteJson(response)) {
            Log.e(TAG, "❌ PULL_DELTA_RESP 不是完整JSON：${response.take(300)}")
            return null
        }
        val respObj = try {
            JsonParser.parseString(response).asJsonObject
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "❌ PULL_DELTA_RESP JSON解析失败：${response.take(300)}", e)
            return null
        }
        val toSeq = respObj.get("to_seq")?.asLong ?: fromSeq
        val ops = respObj.getAsJsonArray("ops")
        if (ops == null || ops.size() == 0) {
            if (fromSeq >= toSeq) {
                sendMessage("DELTA_APPLY_ACK|${gson.toJson(mapOf("acked_seq" to toSeq))}")
                return toSeq
            }
            Log.w(
                TAG,
                "⚠️ 增量为空但游标落后（fromSeq=$fromSeq, toSeq=$toSeq），不提前 ACK，需全量或重试",
            )
            return null
        }
        database.withTransaction {
            syncDao.setSuppressLocalLog(true)
            try {
                for (i in 0 until ops.size()) {
                    val op = ops.get(i).asJsonObject
                    applyRemoteConfigOp(op)
                }
            } finally {
                syncDao.setSuppressLocalLog(false)
            }
        }
        sendMessage("DELTA_APPLY_ACK|${gson.toJson(mapOf("acked_seq" to toSeq))}")
        com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier.notifyChanged()
        return toSeq
    }

    private fun looksLikeCompleteJson(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        if (s == "{" || s == "[" || s == "}" || s == "]") return false
        val objOk = s.startsWith("{") && s.endsWith("}")
        val arrOk = s.startsWith("[") && s.endsWith("]")
        return objOk || arrOk
    }

    private suspend fun applyRemoteConfigOp(opObj: com.google.gson.JsonObject) {
        val entityType = opObj.get("entity_type")?.asString ?: return
        val opType = opObj.get("op_type")?.asString ?: "UPSERT"
        val payload = opObj.getAsJsonObject("payload") ?: return
        val originDeviceId = opObj.get("origin_device_id")?.asString ?: "PC"
        val originOpId = opObj.get("origin_op_id")?.asString ?: ""
        val commitSeq = opObj.get("commit_seq")?.asLong ?: 0L
        if (originOpId.isNotBlank() && syncDao.isApplied(originDeviceId, originOpId)) return

        when (entityType) {
            "CUSTOMER" -> applyCustomerDelta(opType, payload)
            "LOCATION" -> applyLocationDelta(opType, payload)
            "OPERATOR" -> applyOperatorDelta(opType, payload)
            "PRODUCT" -> applyProductDelta(opType, payload)
            "PACK_TYPE" -> applyPackTypeDelta(opType, payload)
            "PRESALE" -> preSaleSyncApplier.applyBillPayload(payload, commitSeq)
            "PRESALE_PAYMENT" -> preSaleSyncApplier.applyPaymentPayload(payload, commitSeq)
            "PRESALE_OUTBOUND" -> preSaleSyncApplier.applyOutboundPayload(payload, commitSeq)
        }
        if (originOpId.isNotBlank()) {
            syncDao.insertAppliedOp(
                SyncAppliedOp(
                    originDeviceId = originDeviceId,
                    originOpId = originOpId,
                    commitSeq = commitSeq,
                    appliedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private suspend fun applyCustomerDelta(opType: String, payload: com.google.gson.JsonObject) {
        val code = payload.get("code")?.asString ?: return
        if (opType == "DELETE") {
            database.customerDao().getByCustomerNo(code)?.let { existing ->
                database.customerDao().insertCustomer(
                    existing.copy(
                        enabled = false,
                        syncStatus = 1,
                        updateTime = System.currentTimeMillis(),
                    ),
                )
            }
            return
        }
        val name = payload.get("name")?.asString ?: code
        val phone = payload.get("phone")?.asString ?: ""
        val customerType = resolveCustomerTypeFromPayload(code, payload)
        val enabled = resolveEnabledFromPayload(payload)
        upsertCustomerFromRemote(code, name, phone, customerType, enabled)
    }

    private suspend fun upsertCustomerFromRemote(
        code: String,
        name: String,
        phone: String,
        customerType: String,
        enabled: Boolean,
    ) {
        val existing = database.customerDao().getByCustomerNo(code)
        if (existing == null) {
            database.customerDao().insertCustomer(
                Customer(
                    customerNo = code,
                    customerName = name,
                    phone = phone,
                    customerType = customerType,
                    enabled = enabled,
                    updateTime = System.currentTimeMillis(),
                    syncStatus = 1,
                )
            )
        } else {
            database.customerDao().insertCustomer(
                existing.copy(
                    customerName = name,
                    phone = phone,
                    customerType = customerType,
                    enabled = enabled,
                    updateTime = System.currentTimeMillis(),
                    syncStatus = 1,
                )
            )
        }
    }

    private fun resolveCustomerTypeFromPayload(code: String, payload: com.google.gson.JsonObject?): String {
        val raw = payload?.get("customer_type")?.asString
        if (!raw.isNullOrBlank()) {
            return raw.trim().uppercase(Locale.US)
        }
        return if (code.startsWith("MJ", ignoreCase = true)) {
            CustomerType.BUYER
        } else {
            CustomerType.SELLER
        }
    }

    private fun resolveEnabledFromPayload(payload: com.google.gson.JsonObject?, default: Boolean = true): Boolean {
        if (payload == null) return default
        if (payload.has("enabled") && !payload.get("enabled").isJsonNull) {
            return payload.get("enabled").asBoolean
        }
        if (payload.has("status") && !payload.get("status").isJsonNull) {
            return payload.get("status").asInt != 0
        }
        return default
    }

    private suspend fun applyLocationDelta(opType: String, payload: com.google.gson.JsonObject) {
        val name = payload.get("name")?.asString
            ?: payload.get("code")?.asString
            ?: return
        if (opType == "DELETE") {
            database.locationDao().getByLocationName(name)?.let { existing ->
                database.locationDao().updateEnabledStatus(existing.id, false)
            }
            return
        }
        val enabled = resolveEnabledFromPayload(payload)
        val description = payload.get("description")?.asString ?: ""
        val existing = database.locationDao().getByLocationName(name)
        if (existing == null) {
            database.locationDao().insert(
                Location(
                    locationName = name,
                    description = description,
                    enabled = enabled,
                    syncStatus = 1,
                ),
            )
        } else {
            database.locationDao().update(
                existing.copy(
                    locationName = name,
                    description = description,
                    enabled = enabled,
                    syncStatus = 1,
                ),
            )
        }
    }

    private suspend fun applyOperatorDelta(opType: String, payload: com.google.gson.JsonObject) {
        val name = payload.get("name")?.asString
            ?: payload.get("code")?.asString
            ?: return
        if (opType == "DELETE") {
            database.operatorDao().getByOperatorName(name)?.let { existing ->
                database.operatorDao().updateEnabledStatus(existing.id, false)
            }
            return
        }
        val enabled = resolveEnabledFromPayload(payload)
        val existing = database.operatorDao().getByOperatorName(name)
        if (existing == null) {
            database.operatorDao().insert(
                Operator(
                    name = name,
                    enabled = enabled,
                    syncStatus = 1,
                ),
            )
        } else {
            database.operatorDao().update(
                existing.copy(
                    name = name,
                    enabled = enabled,
                    syncStatus = 1,
                ),
            )
        }
    }

    private suspend fun applyProductDelta(opType: String, payload: com.google.gson.JsonObject) {
        val code = payload.get("code")?.asString?.trim().orEmpty()
        val name = payload.get("name")?.asString?.trim().orEmpty()
        val lookupKey = code.ifBlank { name }
        if (lookupKey.isBlank()) return
        if (opType == "DELETE") {
            findProductForDelta(code, name, payload.get("previous_name")?.asString)?.let { existing ->
                database.productDao().setEnabled(existing.id, false)
            }
            return
        }
        val enabled = resolveEnabledFromPayload(payload)
        val category = payload.get("category")?.asString ?: "梨"
        val previousName = payload.get("previous_name")?.asString?.trim().orEmpty()
        val existing = findProductForDelta(code, name, previousName)
        if (existing == null) {
            database.productDao().insert(
                Product(
                    productNo = code.ifBlank { name },
                    productName = name.ifBlank { code },
                    enabled = enabled,
                    category = category,
                    syncStatus = 1,
                )
            )
        } else {
            database.productDao().update(
                existing.copy(
                    productNo = code.ifBlank { existing.productNo },
                    productName = name.ifBlank { existing.productName },
                    enabled = enabled,
                    category = category,
                    syncStatus = 1,
                )
            )
        }
    }

    private suspend fun findProductForDelta(
        code: String,
        name: String,
        previousName: String?,
    ): Product? {
        if (code.isNotBlank()) {
            database.productDao().getByProductNo(code)?.let { return it }
        }
        if (!previousName.isNullOrBlank()) {
            database.productDao().getByProductName(previousName)?.let { return it }
            if (previousName != code) {
                database.productDao().getByProductNo(previousName)?.let { return it }
            }
        }
        if (name.isNotBlank()) {
            database.productDao().getByProductName(name)?.let { return it }
        }
        if (code.isNotBlank() && code != name) {
            database.productDao().getByProductName(code)?.let { return it }
        }
        return null
    }

    private suspend fun applyPackTypeDelta(opType: String, payload: com.google.gson.JsonObject) {
        val name = payload.get("name")?.asString?.trim().orEmpty()
        val codeKey = payload.get("code")?.asString?.trim().orEmpty()
        val lookupName = name.ifBlank { codeKey }
        if (lookupName.isBlank()) return
        val previousName = payload.get("previous_name")?.asString?.trim().orEmpty()
        if (opType == "DELETE") {
            findPackTypeForDelta(lookupName, previousName.ifBlank { lookupName })?.let { existing ->
                database.packagingTypeDao().setEnabled(existing.id, false)
            }
            return
        }
        val enabled = resolveEnabledFromPayload(payload)
        val unit = payload.get("unit")?.asString ?: "个"
        val unitPrice = payload.get("unit_price")?.asDouble ?: 0.0
        val remark = payload.get("remark")?.asString ?: ""
        val existing = findPackTypeForDelta(lookupName, previousName)
        if (existing == null) {
            database.packagingTypeDao().insert(
                com.pingwei.lengkubao.data.db.entity.PackagingType(
                    typeName = lookupName,
                    unit = unit,
                    unitPrice = unitPrice,
                    enabled = enabled,
                    remark = remark,
                ),
            )
        } else {
            database.packagingTypeDao().update(
                existing.copy(
                    typeName = lookupName,
                    unit = unit,
                    unitPrice = unitPrice,
                    enabled = enabled,
                    remark = remark,
                ),
            )
        }
    }

    private suspend fun findPackTypeForDelta(name: String, previousName: String?): PackagingType? {
        if (!previousName.isNullOrBlank()) {
            database.packagingTypeDao().getByTypeName(previousName)?.let { return it }
        }
        if (name.isNotBlank()) {
            database.packagingTypeDao().getByTypeName(name)?.let { return it }
        }
        return null
    }

    suspend fun autoSyncConfigsOnStartup() {
        try {
            Log.i(TAG, "🚀 应用启动，开始双向增量同步")

            if (!isConnected()) {
                Log.w(TAG, "⏳ 服务器未连接，等待连接后再同步")
                var waitTime = 0
                while (!isConnected() && waitTime < 10000) {
                    delay(500)
                    waitTime += 500
                }
            }

            if (isConnected()) {
                autoBidirectionalDeltaSyncOnConnect()
            } else {
                Log.w(TAG, "⚠️ 启动时双向增量同步跳过：服务器未连接")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动时自动双向增量同步失败：${e.message}", e)
        }
    }

    suspend fun syncAllCustomers() {
        try {
            _syncState.value = SyncState.Syncing("全量同步客户数据")
            val customers = database.customerDao().getAllCustomers().first()
            var count = 0

            customers.forEach {
                syncCustomer(it.id)
                count++
                delay(100)
            }

            val successMsg = "客户数据全量发送完成：共发送 $count 个客户，等待服务器逐个确认"
            _syncState.value = SyncState.Success(successMsg)
            _messageChannel.send(successMsg)
            Log.i(TAG, "✅ $successMsg")
        } catch (e: Exception) {
            val errorMsg = "客户数据同步失败：${e.message}"
            Log.e(TAG, "❌ $errorMsg", e)
            _syncState.value = SyncState.Failed(errorMsg)
            _messageChannel.send(errorMsg)
        }
    }

    suspend fun queryServerStatus(): String? {
        return withContext(Dispatchers.IO) {
            try {
                sendMessage("QUERY|STATUS")
                Log.d(TAG, "📤 已发送服务器状态查询请求")
                val tip = "服务器状态查询请求已发送，等待响应..."
                _messageChannel.send(tip)
                tip
            } catch (e: Exception) {
                Log.e(TAG, "❌ 查询服务器状态失败: ${e.message}", e)
                null
            }
        }
    }

    suspend fun syncAdvance(advanceId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("预支款")
                val advance = database.advanceDao().getAdvanceById(advanceId)

                if (advance != null) {
                    val sourceRecordId = buildSourceRecordId("ADVANCE", advance.id.toString())
                    val advanceMap = mapOf(
                        "client_code" to advance.customerNo,
                        "client_name" to advance.customerName,
                        "amount" to advance.amount.toString(),
                        "advance_date" to advance.advanceDate,
                        "reason" to (advance.reason ?: ""),
                        "handler" to (advance.handler ?: ""),
                        "creator" to (advance.creator ?: ""),
                        "status" to advance.status.toString(),
                        "created_time" to advance.createTime.toString(),
                        "source_device_id" to getDeviceId(),
                        "source_record_id" to sourceRecordId
                    )
                    val json = gson.toJson(advanceMap)

                    val success = sendMessageAndWaitForAck("ADVANCE", json, sourceRecordId)

                    if (success) {
                        database.advanceDao().updateSyncStatus(advanceId, 1)
                        val successMsg = "预支款[${advance.customerName} ${advance.amount}]同步成功"
                        Log.i(TAG, "✅ $successMsg")
                        _syncState.value = SyncState.Success(successMsg)
                        _messageChannel.send(successMsg)
                        return@withContext true
                    } else {
                        val failMsg = "预支款[${advance.customerName} ${advance.amount}]同步失败"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "预支款ID:$advanceId 不存在，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步预支款失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    suspend fun syncDeduction(deductionId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("扣款")
                val deduction = database.deductionDao().getDeductionById(deductionId)

                if (deduction != null) {
                    val sourceRecordId = buildSourceRecordId("DEDUCTION", deduction.id.toString())
                    val deductionMap = mapOf(
                        "client_code" to deduction.customerNo,
                        "client_name" to deduction.customerName,
                        "amount" to deduction.amount.toString(),
                        "quantity" to deduction.quantity.toString(),
                        "unit_price" to deduction.unitPrice.toString(),
                        "deduct_date" to deduction.deductDate,
                        "reason" to (deduction.reason ?: ""),
                        "handler" to (deduction.handler ?: ""),
                        "creator" to (deduction.creator ?: ""),
                        "status" to deduction.status.toString(),
                        "created_time" to deduction.createTime.toString(),
                        "source_device_id" to getDeviceId(),
                        "source_record_id" to sourceRecordId
                    )
                    val json = gson.toJson(deductionMap)

                    val success = sendMessageAndWaitForAck("DEDUCTION", json, sourceRecordId)

                    if (success) {
                        database.deductionDao().updateSyncStatus(deductionId, 1)
                        val successMsg = "扣款[${deduction.customerName} ${deduction.amount}]同步成功"
                        Log.i(TAG, "✅ $successMsg")
                        _syncState.value = SyncState.Success(successMsg)
                        _messageChannel.send(successMsg)
                        return@withContext true
                    } else {
                        val failMsg = "扣款[${deduction.customerName} ${deduction.amount}]同步失败"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "扣款ID:$deductionId 不存在，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步扣款失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            }
        }
    }

    // ========== 全量反向同步方法（使用消息转发机制） ==========

    /**
     * 发起全量反向同步（以电脑端为主）
     * @param skipUpload 是否跳过上传本地数据（避免递归调用）
     */
    suspend fun pullFullSyncFromServer(
        skipUpload: Boolean = false,
        onProgress: ((current: Int, total: Int, type: String) -> Unit)? = null,
        onResult: ((success: Boolean, message: String) -> Unit)? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!fullSyncInProgress.compareAndSet(false, true)) {
                    val busyMsg = "全量反向同步已在进行中，请稍后重试"
                    Log.w(TAG, "⚠️ $busyMsg")
                    onResult?.invoke(false, busyMsg)
                    _messageChannel.send(busyMsg)
                    return@withContext false
                }

                _syncState.value = SyncState.Syncing("全量反向同步")
                Log.i(TAG, "🔄 开始全量反向同步（以电脑端为主）")
                lastFullSyncBaselineSeq = null
                // 全量同步数据可能很大（分块数多），60s 很容易误判超时导致重连/连接切换。
                // 这里提高 socket 超时，并配合下方等待超时一起放宽。
                socket?.soTimeout = maxOf(syncConfig.receiveTimeout, 300000)

                if (!skipUpload) {
                    onProgress?.invoke(0, 3, "上传未同步数据")

                    val uploadResult = syncPendingData()
                    if (!uploadResult) {
                        Log.w(TAG, "⚠️ 部分未同步数据上传失败，但继续反向同步")
                    }

                    onProgress?.invoke(1, 3, "上传完成")
                } else {
                    Log.i(TAG, "⏭️ 跳过上传本地数据（避免递归）")
                    onProgress?.invoke(1, 3, "跳过上传")
                }

                // ✅ 创建 CompletableDeferred 用于等待接收完成
                val syncDeferred = CompletableDeferred<Boolean>()

                // ✅ 设置全量同步消息监听器（挂起函数类型）
                val chunkMap = linkedMapOf<Int, String>()
                var totalChunks = 0
                var assembledJsonData = ""

                fullSyncListener = { message ->
                    Log.d(TAG, "📥 全量同步处理器收到消息: ${message.take(100)}")

                    when {
                        message.startsWith("FULL_SYNC_START|") -> {
                            chunkMap.clear()
                            assembledJsonData = ""
                            val parts = message.split("|")
                            if (parts.size >= 3) {
                                totalChunks = parts[1].toIntOrNull() ?: 0
                                Log.i(TAG, "📦 开始接收全量数据，共 $totalChunks 块")
                                onProgress?.invoke(2, 3, "接收数据")
                            }
                        }

                        message.startsWith("FULL_SYNC_DATA|") -> {
                            val parts = message.split("|", limit = 4)
                            if (parts.size >= 4) {
                                val chunkIndex = parts[1].toIntOrNull() ?: 0
                                totalChunks = parts[2].toIntOrNull() ?: totalChunks
                                val chunkData = parts[3]

                                if (chunkIndex in 1..totalChunks) {
                                    chunkMap[chunkIndex] = chunkData
                                }

                                Log.d(
                                    TAG,
                                    "📦 接收数据块: $chunkIndex/$totalChunks, 已收 ${chunkMap.size} 块, 数据长度=${chunkData.length}",
                                )

                                if (chunkMap.size % 10 == 0 || chunkMap.size == totalChunks) {
                                    Log.d(TAG, "📦 接收进度: ${chunkMap.size}/$totalChunks")
                                    onProgress?.invoke(2, 3, "接收数据 ${chunkMap.size}/$totalChunks")
                                }
                            } else {
                                Log.w(TAG, "⚠️ FULL_SYNC_DATA 解析失败，原始消息: ${message.take(200)}")
                            }
                        }

                        message.startsWith("FULL_SYNC_END|") -> {
                            parseCommitSeqFromFullSyncEnd(message)?.let { seq ->
                                if (seq > 0L) {
                                    lastFullSyncBaselineSeq = seq
                                    Log.i(TAG, "📌 全量基线 commit_seq=$seq")
                                }
                            }
                            when {
                                totalChunks <= 0 || chunkMap.size < totalChunks -> {
                                    Log.e(TAG, "❌ 全量数据块不完整: ${chunkMap.size}/$totalChunks")
                                    syncDeferred.complete(false)
                                }
                                else -> {
                                    val missingIndex = (1..totalChunks).firstOrNull { !chunkMap.containsKey(it) }
                                    if (missingIndex != null) {
                                        Log.e(TAG, "❌ 全量数据缺少块: $missingIndex/$totalChunks")
                                        syncDeferred.complete(false)
                                    } else {
                                        assembledJsonData = (1..totalChunks).joinToString("") { chunkMap[it]!! }
                                        Log.i(
                                            TAG,
                                            "✅ 全量数据接收完成，共 ${chunkMap.size} 块，累计数据长度: ${assembledJsonData.length}",
                                        )
                                        syncDeferred.complete(true)
                                    }
                                }
                            }
                        }

                        message.startsWith("FULL_SYNC_ERROR|") -> {
                            val errorMsg = message.substringAfter("|")
                            Log.e(TAG, "❌ 服务器返回错误: $errorMsg")
                            syncDeferred.complete(false)
                        }

                        message.startsWith("PREPARE_UPLOAD|") -> {
                            Log.i(TAG, "📤 服务器要求上传未同步数据")
                            sendMessage("UPLOAD_COMPLETE")
                        }
                    }
                }

                // 发送请求
                sendMessage("PULL_FULL_SYNC")
                Log.i(TAG, "📥 已发送全量反向同步请求，等待服务器响应")

                // ✅ 等待接收完成（默认放宽到 5 分钟，避免大数据量误判超时）
                val completed = try {
                    withTimeout(300000L) {
                        syncDeferred.await()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "❌ 等待全量同步数据超时，已接收 ${chunkMap.size}/$totalChunks 块")
                    false
                } finally {
                    // ✅ 清除监听器
                    fullSyncListener = null
                }

                if (!completed) {
                    val errorMsg = "全量同步接收失败"
                    onResult?.invoke(false, errorMsg)
                    _syncState.value = SyncState.Failed(errorMsg)
                    return@withContext false
                }

                if (chunkMap.size < totalChunks || totalChunks <= 0) {
                    val errorMsg = "数据接收不完整：${chunkMap.size}/$totalChunks"
                    Log.e(TAG, "❌ $errorMsg")
                    onResult?.invoke(false, errorMsg)
                    _syncState.value = SyncState.Failed(errorMsg)
                    return@withContext false
                }

                onProgress?.invoke(3, 3, "更新本地数据")
                Log.i(TAG, "🔄 开始解析并替换本地数据，数据长度: ${assembledJsonData.length}")

                val jsonData = assembledJsonData
                if (jsonData.isEmpty()) {
                    val errorMsg = "接收到的数据为空"
                    Log.e(TAG, "❌ $errorMsg")
                    onResult?.invoke(false, errorMsg)
                    _syncState.value = SyncState.Failed(errorMsg)
                    return@withContext false
                }

                Log.i(TAG, "📄 完整 JSON 数据预览: ${jsonData.take(500)}")

                try {
                    val fullData = gson.fromJson(jsonData, FullSyncData::class.java)
                    Log.i(TAG, "解析结果: instruction = ${fullData.instruction}")

                    if (fullData.instruction == "REPLACE_ALL") {
                        database.withTransaction {
                            // 同步库位数据
                            if (fullData.data.locations != null) {
                                for (locationData in fullData.data.locations) {
                                    val existing = database.locationDao().getByLocationName(locationData.name)
                                    if (existing == null) {
                                        val location = com.pingwei.lengkubao.data.db.entity.Location(
                                            locationName = locationData.name,
                                            enabled = true,
                                            syncStatus = 1,
                                        )
                                        database.locationDao().insert(location)
                                        Log.i(TAG, "✅ 新增库位: ${locationData.name}")
                                    } else if (!existing.enabled) {
                                        database.locationDao().updateEnabledStatus(existing.id, true)
                                        Log.i(TAG, "✅ 启用库位: ${locationData.name}")
                                    }
                                }
                                Log.i(TAG, "✅ 库位数据同步完成: ${fullData.data.locations.size} 条")
                            }

                            // 同步经手人数据
                            if (fullData.data.handlers != null) {
                                for (handlerData in fullData.data.handlers) {
                                    val existing = database.operatorDao().getByOperatorName(handlerData.name)
                                    if (existing == null) {
                                        val operator = com.pingwei.lengkubao.data.db.entity.Operator(
                                            name = handlerData.name,
                                            enabled = true,
                                            syncStatus = 1,
                                        )
                                        database.operatorDao().insert(operator)
                                        Log.i(TAG, "✅ 新增经手人: ${handlerData.name}")
                                    } else if (!existing.enabled) {
                                        database.operatorDao().updateEnabledStatus(existing.id, true)
                                        Log.i(TAG, "✅ 启用经手人: ${handlerData.name}")
                                    }
                                }
                                Log.i(TAG, "✅ 经手人数据同步完成: ${fullData.data.handlers.size} 条")
                            }

                            // 同步客户数据（卖家 + 买家，真替换）
                            replaceCustomersFromSnapshot(fullData.data.clients)

                            fullData.data.product_types?.let { products ->
                                val arr = com.google.gson.JsonArray()
                                products.forEach { p ->
                                    val obj = com.google.gson.JsonObject()
                                    obj.addProperty("code", p.code)
                                    obj.addProperty("name", p.name)
                                    p.enabled?.let { obj.addProperty("enabled", it) }
                                    p.category?.let { obj.addProperty("category", it) }
                                    arr.add(obj)
                                }
                                replaceProductsFromSnapshot(arr)
                            }

                            fullData.data.pack_types?.let { packs ->
                                val arr = com.google.gson.JsonArray()
                                packs.forEach { p ->
                                    val obj = com.google.gson.JsonObject()
                                    obj.addProperty("name", p.name)
                                    p.enabled?.let { obj.addProperty("enabled", it) }
                                    p.unit?.let { obj.addProperty("unit", it) }
                                    p.unit_price?.let { obj.addProperty("unit_price", it) }
                                    arr.add(obj)
                                }
                                replacePackTypesFromSnapshot(arr)
                            }

                            // ✅ 同步PC库存快照（库位 + 型号），并刷新 stock.current_quantity（保留 reserved_quantity）
                            if (fullData.data.stocks != null) {
                                val now = System.currentTimeMillis()

                                val products = database.productDao().getAll()
                                val locations = database.locationDao().getAllSimple()

                                val snapshots =
                                    ArrayList<com.pingwei.lengkubao.data.db.entity.PcStockSnapshot>(fullData.data.stocks.size)
                                var appliedCount = 0
                                var skippedCount = 0

                                for (stockData in fullData.data.stocks) {
                                    val product = resolveProductForSnapshot(products, stockData.spec)
                                    val location = resolveLocationForSnapshot(locations, stockData.location_name)

                                    if (product == null || location == null) {
                                        skippedCount++
                                        continue
                                    }

                                    snapshots.add(
                                        com.pingwei.lengkubao.data.db.entity.PcStockSnapshot(
                                            locationId = location.id,
                                            productId = product.id,
                                            spec = stockData.spec.trim(),
                                            locationName = stockData.location_name.trim(),
                                            currentQuantity = stockData.current_quantity,
                                            snapshotTime = now
                                        )
                                    )

                                    val existingStock = database.stockDao().getStock(product.id, location.id)
                                    if (existingStock != null) {
                                        database.stockDao().updateStockQuantity(
                                            productId = product.id,
                                            locationId = location.id,
                                            quantity = stockData.current_quantity,
                                            timestamp = now,
                                            billNo = "PC_SNAPSHOT"
                                        )
                                    } else {
                                        database.stockDao().insert(
                                            com.pingwei.lengkubao.data.db.entity.Stock(
                                                productId = product.id,
                                                productNo = product.productNo,
                                                productName = product.productName,
                                                locationId = location.id,
                                                currentQuantity = stockData.current_quantity,
                                                reservedQuantity = 0,
                                                lastUpdated = now,
                                                lastBillNo = "PC_SNAPSHOT"
                                            )
                                        )
                                    }

                                    appliedCount++
                                }

                                if (snapshots.isNotEmpty()) {
                                    database.pcStockSnapshotDao().upsertAll(snapshots)
                                }

                                Log.i(
                                    TAG,
                                    "✅ PC库存快照同步完成: ${fullData.data.stocks.size} 条，应用 $appliedCount 条，跳过 $skippedCount 条"
                                )
                            }

                            // ✅ 同步PC入库统计快照（按 date + 客户 + 库位 + 型号）
                            val inboundDaily = fullData.data.inbound_stats?.daily
                            if (!inboundDaily.isNullOrEmpty()) {
                                val now = System.currentTimeMillis()
                                val snapshots =
                                    ArrayList<com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot>(inboundDaily.size)
                                var appliedCount = 0
                                var skippedCount = 0

                                for (row in inboundDaily) {
                                    val date = row.date.trim()
                                    val customerNo = row.customer_no.trim()
                                    val customerName = row.customer_name.trim()
                                    val locationName = row.location_name.trim()
                                    val spec = row.spec.trim()
                                    if (date.isBlank() || customerNo.isBlank() || locationName.isBlank() || spec.isBlank()) {
                                        skippedCount++
                                        continue
                                    }

                                    snapshots.add(
                                        com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot(
                                            date = date,
                                            customerNo = customerNo,
                                            customerName = customerName,
                                            locationName = locationName,
                                            spec = spec,
                                            quantity = row.quantity,
                                            amount = row.amount,
                                            orderCount = row.order_count,
                                            snapshotTime = now
                                        )
                                    )
                                    appliedCount++
                                }

                                if (snapshots.isNotEmpty()) {
                                    database.pcInboundDailySnapshotDao().upsertAll(snapshots)
                                    com.pingwei.lengkubao.service.CustomerInboundStockBackfill(database)
                                        .refreshFromPcSnapshots()
                                }

                                Log.i(
                                    TAG,
                                    "✅ PC入库统计快照同步完成: ${inboundDaily.size} 条，应用 $appliedCount 条，跳过 $skippedCount 条"
                                )
                            } else {
                                Log.w(TAG, "⚠️ PC入库统计快照为空（inbound_stats.daily 为空或缺失）")
                            }
                        }
                    }

                    val successMsg = "全量反向同步完成！客户:${fullData.data.clients?.size ?: 0}，" +
                            "库位:${fullData.data.locations?.size ?: 0}，" +
                            "经手人:${fullData.data.handlers?.size ?: 0}"

                    Log.i(TAG, "✅ $successMsg")
                    _syncState.value = SyncState.Success(successMsg)
                    onResult?.invoke(true, successMsg)
                    _messageChannel.send(successMsg)

                    return@withContext true

                } catch (e: Exception) {
                    val errorMsg = "解析全量同步数据失败: ${e.message}"
                    Log.e(TAG, "❌ $errorMsg", e)
                    onResult?.invoke(false, errorMsg)
                    _syncState.value = SyncState.Failed(errorMsg)
                    return@withContext false
                }

            } catch (e: Exception) {
                val errorMsg = "全量反向同步失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                onResult?.invoke(false, errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
            } finally {
                // 确保监听器被清除
                fullSyncListener = null
                fullSyncInProgress.set(false)
                socket?.soTimeout = syncConfig.receiveTimeout
            }
        }
    }

    suspend fun syncPendingData(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("批量同步未完成数据")
                var totalSync = 0
                var packagingWithFlag = 0
                var advanceCount = 0
                var deductionCount = 0

                var inventoryRelatedSyncFailed = false

                val pendingIn = database.inStockBillDao().getAllBills().first()
                    .filter { it.syncStatus == 0 }
                for (bill in pendingIn) {
                    if (!syncInStockBill(bill.id)) inventoryRelatedSyncFailed = true
                    totalSync++
                    delay(500)
                }

                val pendingSale = database.saleBillDao().getAllBills().first()
                    .filter { it.syncStatus == 0 }
                for (bill in pendingSale) {
                    if (!syncSaleBill(bill.id)) inventoryRelatedSyncFailed = true
                    totalSync++
                    delay(500)
                }

                val pendingPack = database.packagingBillDao().getAllBills().first()
                    .filter { !it.isSynced }
                for (bill in pendingPack) {
                    if (bill.packagingTypeFlag == "RETURN") {
                        packagingWithFlag++
                    }
                    if (!syncPackagingBill(bill.id).success) inventoryRelatedSyncFailed = true
                    totalSync++
                    delay(500)
                }

                val pendingAdvances = database.advanceDao().getUnsyncedAdvances()
                advanceCount = pendingAdvances.size
                for (advance in pendingAdvances) {
                    syncAdvance(advance.id)
                    totalSync++
                    delay(500)
                }

                val pendingDeductions = database.deductionDao().getUnsyncedDeductions()
                deductionCount = pendingDeductions.size
                for (deduction in pendingDeductions) {
                    syncDeduction(deduction.id)
                    totalSync++
                    delay(500)
                }

                val pendingPresaleOutbounds = database.outboundRecordDao().getUnsyncedRecords()
                val presaleOutboundCount = pendingPresaleOutbounds.size
                for (record in pendingPresaleOutbounds) {
                    syncPreSaleOutbound(record.id)
                    totalSync++
                    delay(500)
                }

                val pendingPresales = database.preSaleBillDao().getUnsyncedBills()
                val presaleCount = pendingPresales.size
                for (bill in pendingPresales) {
                    syncPreSaleBill(bill.id)
                    totalSync++
                    delay(500)
                }

                val pendingPresalePayments = database.paymentRecordDao().getUnsyncedPayments()
                val presalePaymentCount = pendingPresalePayments.size
                for (payment in pendingPresalePayments) {
                    syncPreSalePayment(payment.id)
                    totalSync++
                    delay(500)
                }

                val pendingLedgerEntries = database.ledgerEntryDao().getUnsyncedEntries()
                val ledgerCount = pendingLedgerEntries.size
                for (entry in pendingLedgerEntries) {
                    syncLedgerEntry(entry.id)
                    totalSync++
                    delay(500)
                }

                val flagInfo = if (packagingWithFlag > 0) "，其中进包装单 $packagingWithFlag 张" else ""
                val advanceInfo = if (advanceCount > 0) "，预支款 $advanceCount 条" else ""
                val deductionInfo = if (deductionCount > 0) "，扣款 $deductionCount 条" else ""
                val presaleInfo = if (presaleCount > 0) "，预售单 $presaleCount 张" else ""
                val presalePaymentInfo = if (presalePaymentCount > 0) "，预售收款 $presalePaymentCount 条" else ""
                val presaleOutboundInfo = if (presaleOutboundCount > 0) "，预售出库 $presaleOutboundCount 条" else ""
                val ledgerInfo = if (ledgerCount > 0) "，收支流水 $ledgerCount 条" else ""
                val successMsg = "批量同步数据发送完成：共发送 $totalSync 条数据$flagInfo$advanceInfo$deductionInfo$presaleInfo$presalePaymentInfo$presaleOutboundInfo$ledgerInfo，等待服务器逐个确认"
                _syncState.value = SyncState.Success(successMsg)
                _messageChannel.send(successMsg)
                Log.i(TAG, "✅ $successMsg")

                if (totalSync > 0 && inventoryRelatedSyncFailed) {
                    val skipMsg =
                        "入库/销售/包装存在同步失败，已跳过自动反向同步，避免用不完整电脑数据覆盖本地库存与统计"
                    Log.w(TAG, "⚠️ $skipMsg")
                    _messageChannel.send(skipMsg)
                }

                if (totalSync > 0 && !inventoryRelatedSyncFailed) {
                    Log.i(TAG, "🔄 正向同步完成，3秒后自动执行反向同步...")
                    delay(3000)

                    if (isConnected()) {
                        Log.i(TAG, "🚀 开始自动反向同步")
                        val reverseSyncResult = pullFullSyncFromServer(
                            skipUpload = true,  // ✅ 跳过上传，避免递归
                            onProgress = { current, total, type ->
                                Log.d(TAG, "反向同步进度: $type $current/$total")
                            },
                            onResult = { success, message ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    if (success) {
                                        Log.i(TAG, "✅ 自动反向同步成功: $message")
                                        _messageChannel.send("自动反向同步成功: $message")
                                    } else {
                                        Log.e(TAG, "❌ 自动反向同步失败: $message")
                                        _messageChannel.send("自动反向同步失败: $message")
                                    }
                                }
                            }
                        )

                        if (reverseSyncResult) {
                            Log.i(TAG, "✅ 自动反向同步完成")
                        } else {
                            Log.e(TAG, "❌ 自动反向同步失败")
                        }
                    } else {
                        Log.w(TAG, "⚠️ 连接已断开，跳过自动反向同步")
                    }
                } else if (totalSync == 0) {
                    Log.i(TAG, "ℹ️ 没有待同步数据，跳过反向同步")
                }

                true
            } catch (e: CancellationException) {
                val cancelMsg = "批量同步任务已取消"
                Log.i(TAG, "🛑 $cancelMsg")
                _messageChannel.send(cancelMsg)
                _syncState.value = SyncState.Idle
                throw e
            } catch (e: Exception) {
                val errorMsg = "批量同步失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                false
            }
        }
    }

    private fun resolveProductForSnapshot(products: List<Product>, specRaw: String): Product? {
        val t = specRaw.trim()
        if (t.isEmpty()) return null
        for (p in products) {
            if (p.productName.trim() == t) return p
        }
        for (p in products) {
            if (p.productName.trim().equals(t, ignoreCase = true)) return p
        }
        return null
    }

    private fun resolveLocationForSnapshot(locations: List<Location>, nameRaw: String): Location? {
        val t = nameRaw.trim()
        if (t.isEmpty()) return null
        for (loc in locations) {
            if (loc.locationName.trim() == t) return loc
        }
        for (loc in locations) {
            if (loc.locationName.trim().equals(t, ignoreCase = true)) return loc
        }
        return null
    }

    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "HANDHELD_${System.currentTimeMillis()}"
            prefs.edit().putString("device_id", deviceId).apply()
            Log.d(TAG, "🆔 生成新设备ID并持久化: $deviceId")
        }
        return deviceId
    }

    fun isConnected(): Boolean = isConnected.get()

    fun getCurrentConfig(): SyncConfig = syncConfig

    /** 扫码配对：写入 IP/端口/配对码并可选立即连接。 */
    fun applyQrPairing(payload: QrPairingHelper.PairingPayload, connectNow: Boolean = true) {
        val ip = payload.ip.trim()
        require(isValidServerIp(ip)) { "二维码中的 IP 无效: $ip" }
        require(payload.code.isNotBlank()) { "二维码缺少配对码" }

        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(Constant.PREF_PAIRING_CODE, payload.code)
            putString(Constant.PREF_PAIRED_SERVER_IP, ip)
            putInt(Constant.PREF_PAIRED_SERVER_PORT, payload.port)
            putString("server_ip", ip)
            putInt("server_port", payload.port)
        }.apply()

        updateConfig(
            syncConfig.copy(
                serverIp = ip,
                serverPort = payload.port
            )
        )
        notifyDiscoveryApplied()
        Log.i(TAG, "📱 二维码配对成功: ${payload.name} $ip:${payload.port}")
        if (connectNow) {
            connect()
        }
    }

    private fun parseKeyValueLong(data: String, key: String): Long? {
        return data.split('|')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.startsWith("$key=", ignoreCase = true)) {
                    trimmed.substringAfter("=").trim().toLongOrNull()
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private fun parseCommitSeqFromSyncReady(data: String): Long? = parseKeyValueLong(data, "seq")

    private fun parseCommitSeqFromFullSyncEnd(message: String): Long? = parseKeyValueLong(message, "commit_seq")

    private fun resolveFullSyncBaselineSeq(): Long {
        return lastFullSyncBaselineSeq ?: lastKnownServerCommitSeq ?: 0L
    }

    private suspend fun purgeCustomersExceptSnapshot(syncedCodes: List<String>) {
        if (syncedCodes.isEmpty()) {
            database.customerDao().deleteAllCustomers()
            Log.i(TAG, "🧹 已清空本地客户（PC 快照为空）")
        } else {
            database.customerDao().deleteExceptCodes(syncedCodes)
            Log.i(TAG, "🧹 已清理快照外客户，保留 ${syncedCodes.size} 条")
        }
    }

    private suspend fun replaceProductsFromSnapshot(productsArray: com.google.gson.JsonArray) {
        val syncedCodes = mutableListOf<String>()
        for (i in 0 until productsArray.size()) {
            val obj = productsArray.get(i).asJsonObject
            val code = obj.get("code")?.asString ?: continue
            val name = obj.get("name")?.asString ?: code
            val enabled = resolveEnabledFromPayload(obj)
            val category = obj.get("category")?.asString ?: "梨"
            val existing = database.productDao().getByProductNo(code)
                ?: database.productDao().getByProductName(name)
            if (existing == null) {
                database.productDao().insert(
                    Product(
                        productNo = code,
                        productName = name,
                        enabled = enabled,
                        category = category,
                        syncStatus = 1,
                    ),
                )
            } else {
                database.productDao().update(
                    existing.copy(
                        productNo = code,
                        productName = name,
                        enabled = enabled,
                        category = category,
                        syncStatus = 1,
                    ),
                )
            }
            syncedCodes.add(code)
        }
        if (syncedCodes.isEmpty()) {
            database.productDao().deleteAllProducts()
        } else {
            database.productDao().deleteExceptProductNos(syncedCodes)
        }
        Log.i(TAG, "✅ 型号数据同步完成: ${syncedCodes.size} 条（已清理快照外型号）")
    }

    private suspend fun replacePackTypesFromSnapshot(packTypesArray: com.google.gson.JsonArray) {
        val syncedNames = mutableListOf<String>()
        for (i in 0 until packTypesArray.size()) {
            val obj = packTypesArray.get(i).asJsonObject
            val name = obj.get("name")?.asString ?: continue
            val enabled = resolveEnabledFromPayload(obj)
            val unit = obj.get("unit")?.asString ?: "个"
            val unitPrice = obj.get("unit_price")?.asDouble ?: 0.0
            val existing = database.packagingTypeDao().getByTypeName(name)
            if (existing == null) {
                database.packagingTypeDao().insert(
                    com.pingwei.lengkubao.data.db.entity.PackagingType(
                        typeName = name,
                        unit = unit,
                        unitPrice = unitPrice,
                        enabled = enabled,
                    ),
                )
            } else {
                database.packagingTypeDao().update(
                    existing.copy(
                        typeName = name,
                        unit = unit,
                        unitPrice = unitPrice,
                        enabled = enabled,
                    ),
                )
            }
            syncedNames.add(name)
        }
        if (syncedNames.isEmpty()) {
            database.packagingTypeDao().deleteAllPackagingTypes()
        } else {
            database.packagingTypeDao().deleteExceptTypeNames(syncedNames)
        }
        Log.i(TAG, "✅ 包装类型同步完成: ${syncedNames.size} 条（已清理快照外包装）")
    }

    private suspend fun replaceCustomersFromSnapshot(clients: List<ClientSyncData>?) {
        if (clients == null) return
        val syncedCodes = mutableListOf<String>()
        for (clientData in clients) {
            if (clientData.code.isBlank()) continue
            val enabled = clientData.enabled ?: true
            val payload = com.google.gson.JsonObject().apply {
                clientData.customer_type?.let { addProperty("customer_type", it) }
            }
            val customerType = resolveCustomerTypeFromPayload(clientData.code, payload)
            upsertCustomerFromRemote(
                code = clientData.code,
                name = clientData.name,
                phone = clientData.phone ?: "",
                customerType = customerType,
                enabled = enabled,
            )
            syncedCodes.add(clientData.code)
        }
        purgeCustomersExceptSnapshot(syncedCodes)
        Log.i(TAG, "✅ 客户数据同步完成: ${syncedCodes.size} 条（含卖家/买家，已清理快照外客户）")
    }

    private fun parseActiveYearFromSyncReady(data: String): Int? {
        return data.split('|')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.startsWith("active_year=", ignoreCase = true)) {
                    trimmed.substringAfter("=").trim().toIntOrNull()
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private suspend fun ensureSyncYearAligned(): Boolean {
        if (!isConnected.get() || !isRegistered.get()) {
            return false
        }
        val deviceId = getDeviceId()
        val cursor = syncDao.getCursor(deviceId) ?: SyncDeviceCursor(
            deviceId = deviceId,
            updatedAt = System.currentTimeMillis(),
        )
        syncDao.upsertCursor(cursor)
        val helloPayload = gson.toJson(
            mapOf(
                "device_id" to deviceId,
                "first_full_sync_done" to cursor.firstFullSyncDone,
                "last_acked_seq" to cursor.lastAckedSeq,
                "supports_delta" to true,
                "mode" to "LWW_COMMIT_SEQ",
            )
        )
        val syncReadyData = sendMessageAndWaitForCommand(
            "HELLO_SYNC|$helloPayload",
            "SYNC_SERVER_READY",
            15000L,
        )
        if (syncReadyData == null) {
            val err = "等待服务器就绪超时"
            _syncState.value = SyncState.Failed(err)
            _messageChannel.send(err)
            return false
        }
        parseActiveYearFromSyncReady(syncReadyData)?.let { lastKnownPcActiveYear = it }
        parseCommitSeqFromSyncReady(syncReadyData)?.let { seq ->
            if (seq > 0L) {
                lastKnownServerCommitSeq = seq
                Log.d(TAG, "📌 PC commit_seq=$seq")
            }
        }
        checkSyncYearMismatch(syncReadyData)?.let { mismatchMsg ->
            Log.e(TAG, "❌ $mismatchMsg")
            sendConnectionBroadcast(false, mismatchMsg)
            disconnect()
            _syncState.value = SyncState.Failed(mismatchMsg)
            _messageChannel.send(mismatchMsg)
            return false
        }
        return true
    }

    private fun canSyncPresaleData(): Boolean {
        if (!FiscalYearManager.isInitialized) return true
        val pcYear = lastKnownPcActiveYear ?: return true
        val localYear = FiscalYearManager.activeYear
        if (pcYear != localYear) {
            Log.w(TAG, "预售同步跳过：PC年份=$pcYear，本地=$localYear")
            return false
        }
        return true
    }

    private fun checkSyncYearMismatch(syncReadyData: String): String? {
        val pcYear = parseActiveYearFromSyncReady(syncReadyData) ?: return null
        if (!FiscalYearManager.isInitialized) return null
        val localYear = FiscalYearManager.activeYear
        if (pcYear != localYear) {
            return "PC 活跃年份为 $pcYear，手持端为 $localYear，请先统一年份"
        }
        return null
    }
}

// 全量同步数据类
data class FullSyncData(
    val sync_type: String,
    val timestamp: String,
    val server_name: String,
    val data: FullSyncDataContent,
    val instruction: String
)

data class FullSyncDataContent(
    val clients: List<ClientSyncData>?,
    val locations: List<LocationSyncData>?,
    val handlers: List<HandlerSyncData>?,
    val product_types: List<ProductSyncData>? = null,
    val pack_types: List<PackTypeSyncData>? = null,
    val stocks: List<StockSyncData>?,
    val inbound_stats: InboundStatsSyncData?,
    val presale_bills: List<com.google.gson.JsonObject>? = null,
    val presale_payments: List<com.google.gson.JsonObject>? = null,
)

data class ProductSyncData(
    val code: String,
    val name: String,
    val enabled: Boolean? = null,
    val category: String? = null,
)

data class PackTypeSyncData(
    val name: String,
    val enabled: Boolean? = null,
    val unit: String? = null,
    val unit_price: Double? = null,
)

data class ClientSyncData(
    val code: String,
    val name: String,
    val phone: String? = null,
    val customer_type: String? = null,
    val enabled: Boolean? = null,
)

data class LocationSyncData(
    val name: String
)

data class HandlerSyncData(
    val name: String
)

data class StockSyncData(
    val location_name: String,
    val spec: String,
    val current_quantity: Int
)

data class InboundStatsSyncData(
    val daily: List<InboundDailySyncData>?
)

data class InboundDailySyncData(
    val date: String,
    val customer_no: String,
    val customer_name: String,
    val location_name: String,
    val spec: String,
    val quantity: Int,
    val amount: Double,
    val order_count: Int
)

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val dataType: String) : SyncState()
    data class Success(val message: String) : SyncState()
    data class Failed(val message: String) : SyncState()
}