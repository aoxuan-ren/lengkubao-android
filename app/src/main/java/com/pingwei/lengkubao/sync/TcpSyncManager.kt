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
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.data.db.entity.SyncAppliedOp
import com.pingwei.lengkubao.data.db.entity.SyncDeviceCursor
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
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

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
                disconnect()
                scope.cancel()
                _messageChannel.close()
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
    private val syncDao = database.syncDao()

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

    private var heartbeatJob: Job? = null
    private val confirmedItems = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingAckMap = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val pendingCommandMap = ConcurrentHashMap<String, CompletableDeferred<String>>()

    init {
        loadConfig()
        Log.i(TAG, "🔄 TcpSyncManager 初始化完成，已加载本地配置：${syncConfig.serverIp}:${syncConfig.serverPort}")
    }

    private fun loadConfig() {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        syncConfig = SyncConfig(
            serverIp = prefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100",
            serverPort = prefs.getInt("server_port", 8080),
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
                _connectionState.value = ConnectionState.CONNECTED
                connectionAttemptCount = 0
                reconnectDelay = syncConfig.reconnectInterval
                Log.i(TAG, "✅ TCP连接成功！服务器地址: ${socket!!.inetAddress.hostAddress}")

                startConnectionMonitor()

                delay(500)
                sendRegistration()
                startHeartbeat()
                startMessageListener()
                delay(1000)

                sendConnectionBroadcast(true, "已连接到服务器")

                if (isConnected.get() && isRegistered.get()) {
                    autoSyncPendingData()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ TCP连接失败: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
                isConnecting.set(false)
                isConnected.set(false)
                isRegistered.set(false)

                sendConnectionBroadcast(false, e.message ?: "连接失败")

                if (shouldReconnect.get()) {
                    scheduleSmartReconnect()
                }
            }
        }
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

            if (connectionAttemptCount > 1) {
                reconnectDelay = min(
                    reconnectDelay * 2,
                    maxReconnectDelay
                )
            }

            if (connectionAttemptCount > maxAttemptCount) {
                Log.w(TAG, "⚠️  已达到最大重连尝试次数(${maxAttemptCount}次)，暂停重连")
                _connectionState.value = ConnectionState.WAITING_RECONNECT

                delay(30 * 60 * 1000)
                connectionAttemptCount = 0
                reconnectDelay = syncConfig.reconnectInterval
                scheduleSmartReconnect()
                return@launch
            }

            val delayTime = if (connectionAttemptCount == 1) {
                syncConfig.reconnectInterval
            } else {
                reconnectDelay
            }

            Log.i(TAG, "⏳ 第${connectionAttemptCount}次尝试重连，${delayTime}ms后重连...")
            _connectionState.value = ConnectionState.WAITING_RECONNECT
            updateNotification("等待重连 (${delayTime/1000}秒后)")

            delay(delayTime)

            if (shouldReconnect.get() && !isConnected.get()) {
                Log.d(TAG, "🔄 执行智能重连，尝试次数: $connectionAttemptCount, 间隔: ${delayTime}ms")
                connectInternal()
            }
        }.also { reconnectJob.set(it) }
    }

    private fun startConnectionMonitor() {
        scope.launch {
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
                scheduleSmartReconnect()
            }
        }
    }

    private fun sendConnectionBroadcast(isConnected: Boolean, message: String) {
        val intent = Intent("TCP_CONNECTION_STATUS")
            .apply {
                putExtra("is_connected", isConnected)
                putExtra("message", message)
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

            val totalPending = pendingIn.size + pendingSale.size + pendingPack.size

            if (totalPending > 0) {
                Log.i(TAG, "🔍 发现 $totalPending 张未同步单据，开始自动同步...")
                _messageChannel.send("发现 $totalPending 张未同步单据，开始自动同步...")
                val syncResult = syncPendingData()

                if (syncResult) {
                    Log.i(TAG, "✅ 自动同步完成，已触发反向同步")
                } else {
                    Log.e(TAG, "❌ 自动同步失败")
                }
            } else {
                Log.i(TAG, "✅ 所有单据已同步，无需同步")
                _messageChannel.send("✅ 所有单据已同步")
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

                if (confirmedItems.contains(uniqueKey)) {
                    Log.d(TAG, "⏭️ 跳过已确认的项：$uniqueKey")
                    return@withContext true
                }

                val existingAck = pendingAckMap[ackKey]
                if (existingAck != null) {
                    Log.w(TAG, "🔁 检测到重复ACK等待，复用已有等待：$ackKey")
                    val reusedResult = withTimeoutOrNull(30000L) { existingAck.await() } ?: false
                    if (reusedResult) {
                        confirmedItems.add(uniqueKey)
                    }
                    return@withContext reusedResult
                }

                val message = "$dataType|$jsonData"
                val ackDeferred = CompletableDeferred<Boolean>()
                pendingAckMap[ackKey] = ackDeferred
                ownsAckRegistration = true

                val sendSuccess = sendMessage(message)
                if (!sendSuccess) {
                    pendingAckMap.remove(ackKey)
                    ackDeferred.complete(false)
                    Log.e(TAG, "❌ 发送失败：$dataType $billNo")
                    return@withContext false
                }

                Log.d(TAG, "📤 已发送 $dataType $billNo，等待服务器确认...")
                val result = withTimeoutOrNull(30000L) { ackDeferred.await() } ?: false

                if (result) {
                    confirmedItems.add(uniqueKey)
                    Log.i(TAG, "✅ 服务器确认成功：$dataType $billNo")
                } else {
                    Log.e(TAG, "⏰ 等待服务器确认超时/失败：$dataType $billNo")
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
                    pendingAckMap.remove(ackKey)
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
                    val success = syncPackagingBill(bill.id)
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
            if (command == "PUSH_CHANGES_ACK" || command == "PULL_DELTA_RESP") {
                completeCommand(command, data)
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
                    isRegistered.set(true)
                    _messageChannel.send("✅ 服务器连接成功：$data")

                    scope.launch {
                        delay(1000)
                        if (isConnected.get() && isRegistered.get()) {
                            autoSyncPendingData()
                        }
                    }
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
                    isRegistered.set(true)
                    _messageChannel.send("握手成功：$data")
                }
                "PONG" -> {
                    Log.v(TAG, "💓 收到心跳响应：$data")
                }
                "SYNC_SERVER_READY" -> {
                    Log.i(TAG, "✅ 服务器已就绪：$data")
                    _messageChannel.send("服务器已就绪：$data")
                }
                "SYNC_SUCCESS" -> {
                    val subParts = data.split("|", limit = 4)
                    if (subParts.size >= 2) {
                        val dataType = subParts[0]
                        val code = subParts[1]
                        val extraInfo = if (subParts.size >= 4) subParts[3] else ""
                        val isSourceRecordAck = code.startsWith("SRC_")
                        completeAck(dataType, code, true)

                        when (dataType) {
                            "INBOUND", "SALES", "PACKAGING" -> {
                                Log.i(TAG, "✅ 同步确认成功：$dataType $code")
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, code, true) }
                                } else {
                                    scope.launch {
                                        updateSyncStatus(code, dataType, true)
                                    }
                                }
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
                            "INBOUND", "SALES", "PACKAGING" -> {
                                if (isSourceRecordAck) {
                                    scope.launch { updateSyncStatusBySourceRecordId(dataType, billNo, false) }
                                } else {
                                    scope.launch { updateSyncStatus(billNo, dataType, false) }
                                }
                            }
                            "ADVANCE", "DEDUCTION" -> {
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
                    val deviceId = getDeviceId()
                    syncDao.upsertCursor(
                        (syncDao.getCursor(deviceId) ?: SyncDeviceCursor(deviceId = deviceId)).copy(
                            firstFullSyncDone = false,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
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
            Log.w(TAG, "⚠️ ACK未命中待确认项：$ackKey（可能已超时或注册竞态）")
            return
        }
        deferred.complete(success)
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
                        val billNo = normalizeBillNoFromLocalKey(localKey)
                        database.packagingBillDao().getBillByNo(billNo)?.let {
                            database.packagingBillDao().updateSyncStatus(it.id, success)
                        } ?: Log.w(TAG, "⚠️ PACKAGING未找到本地单据：source=$sourceRecordId, key=$localKey, billNo=$billNo")
                    }
                    "ADVANCE" -> localKey.toLongOrNull()?.let { database.advanceDao().updateSyncStatus(it, if (success) 1 else 0) }
                    "DEDUCTION" -> localKey.toLongOrNull()?.let { database.deductionDao().updateSyncStatus(it, if (success) 1 else 0) }
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
        val marker = "_INBOUND_"
            .takeIf { body.contains(it) }
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
                                    locationNo = "",
                                    enabled = true,
                                    syncStatus = 1
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
                                    operatorNo = "",
                                    enabled = true,
                                    syncStatus = 1
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
                        var clientCount = 0
                        for (i in 0 until clientsArray.size()) {
                            val clientElement = clientsArray.get(i)
                            val clientObj = clientElement.asJsonObject
                            val code = clientObj.get("code")?.asString ?: continue
                            val name = clientObj.get("name")?.asString ?: continue
                            val phone = clientObj.get("phone")?.asString ?: ""

                            val existing = database.customerDao().getByCustomerNo(code)
                            if (existing != null) {
                                database.customerDao().updateCustomer(
                                    existing.id,
                                    name,
                                    phone
                                )
                                clientCount++
                                Log.d(TAG, "✅ 更新客户: $name")
                            } else {
                                val customer = com.pingwei.lengkubao.data.db.entity.Customer(
                                    customerNo = code,
                                    customerName = name,
                                    phone = phone
                                )
                                database.customerDao().insertCustomer(customer)
                                clientCount++
                                Log.d(TAG, "✅ 新增客户: $name")
                            }
                        }
                        Log.i(TAG, "✅ 客户数据同步完成: ${clientsArray.size()} 条")
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
                                        locationNo = location.locationNo,
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
                        }

                        Log.i(TAG, "✅ PC入库统计快照同步完成: ${inboundDailyArray.size()} 条，应用 $appliedCount 条，跳过 $skippedCount 条")
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
                    "phone" to (customer.phone ?: "")
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

    /**
     * 预售单同步桩（待 PC 端对接，暂不发送）
     */
    suspend fun syncPreSaleBill(billId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "ℹ️ 预售单同步待 PC 端对接，billId=$billId（本地保留 syncStatus=0）")
            false
        }
    }

    /**
     * 预售收款同步桩（待 PC 端对接，暂不发送）
     */
    suspend fun syncPreSalePayment(paymentId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "ℹ️ 预售收款同步待 PC 端对接，paymentId=$paymentId")
            false
        }
    }

    suspend fun syncPackagingBill(billId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing("包装单")
                val bill = database.packagingBillDao().getBillById(billId)
                val items = database.packagingItemDao().getItemsByBillId(billId)

                if (bill != null && items.isNotEmpty()) {
                    var successCount = 0
                    var failCount = 0
                    val failedItems = mutableListOf<String>()

                    items.forEachIndexed { index, item ->
                        val sourceRecordId = buildSourceRecordId("PACKAGING", "${bill.billNo}_${index}")
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
                            Log.d(TAG, "📤 包装明细确认成功：${item.packagingType} x${item.quantity}")
                        } else {
                            failCount++
                            failedItems.add(item.packagingType)
                            Log.e(TAG, "❌ 包装明细确认失败：${item.packagingType} x${item.quantity}")
                        }
                        delay(50)
                    }

                    // ✅ 所有明细都成功后，才更新整单状态
                    if (failCount == 0) {
                        database.packagingBillDao().updateSyncStatus(billId, true)
                        val successMsg = "包装单${bill.billNo}同步成功，标记=${bill.packagingTypeFlag}（${successCount}条明细）"
                        Log.i(TAG, "✅ $successMsg")
                        _messageChannel.send(successMsg)
                        _syncState.value = SyncState.Success(successMsg)
                        return@withContext true
                    } else {
                        val failMsg = "包装单${bill.billNo}同步部分失败：成功${successCount}条，失败${failCount}条（${failedItems.joinToString()}）"
                        Log.e(TAG, "❌ $failMsg")
                        _syncState.value = SyncState.Failed(failMsg)
                        _messageChannel.send(failMsg)
                        return@withContext false
                    }
                } else {
                    val warnMsg = "包装单ID:$billId 不存在或无明细，跳过同步"
                    Log.w(TAG, "⚠️ $warnMsg")
                    _syncState.value = SyncState.Failed(warnMsg)
                    _messageChannel.send(warnMsg)
                    return@withContext false
                }
            } catch (e: Exception) {
                val errorMsg = "同步包装单失败：${e.message}"
                Log.e(TAG, "❌ $errorMsg", e)
                _syncState.value = SyncState.Failed(errorMsg)
                _messageChannel.send(errorMsg)
                return@withContext false
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
                val unsyncedLocations = database.locationDao().getAllSimple().count { it.syncStatus == 0 }
                val unsyncedOperators = database.operatorDao().getAllSimple().count { it.syncStatus == 0 }
                val unsyncedCustomers = database.customerDao().getAllSimple().count { it.syncStatus == 0 }

                val result = mapOf(
                    "库位" to unsyncedLocations,
                    "经手人" to unsyncedOperators,
                    "客户" to unsyncedCustomers,
                    "总计" to (unsyncedLocations + unsyncedOperators + unsyncedCustomers)
                )

                Log.i(TAG, "📊 未同步基础配置检查结果：$result")
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查未同步配置失败：${e.message}", e)
                return@withContext emptyMap()
            }
        }
    }

    suspend fun autoBidirectionalDeltaSyncOnConnect() {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                val now = System.currentTimeMillis()
                val cursor = syncDao.getCursor(deviceId) ?: SyncDeviceCursor(
                    deviceId = deviceId,
                    updatedAt = now,
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
                sendMessage("HELLO_SYNC|$helloPayload")

                if (!cursor.firstFullSyncDone) {
                    Log.i(TAG, "🔄 首次连接，执行全量双向同步")
                    _syncState.value = SyncState.Syncing("首次双向全量同步")
                    syncAllConfigs(onResult = { success, msg ->
                        Log.i(TAG, "首次全量基础配置结果: success=$success, msg=$msg")
                    })
                    syncDao.updateFirstFullSyncDone(deviceId, true, now)
                }

                val uploaded = uploadPendingConfigOps(deviceId)
                val latestAckedSeq = pullAndApplyDelta(deviceId, cursor.lastAckedSeq)
                if (latestAckedSeq != null) {
                    syncDao.updateLastAckedSeq(deviceId, latestAckedSeq, System.currentTimeMillis())
                }
                val successMsg = "双向增量同步完成：上行${uploaded}条，最新序号=${latestAckedSeq ?: cursor.lastAckedSeq}"
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

    private suspend fun uploadPendingConfigOps(deviceId: String): Int {
        val ops = syncDao.getPendingLocalOps(500)
        if (ops.isEmpty()) return 0
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
                pushedCount++
            }
        }
        if (pushedCount > 0) {
            val maxPushedId = syncDao.getPendingLocalOps(1).firstOrNull()?.id?.minus(1) ?: Long.MAX_VALUE
            syncDao.deletePushedOpsBefore(maxPushedId)
        }
        return pushedCount
    }

    private suspend fun pullAndApplyDelta(deviceId: String, fromSeq: Long): Long? {
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
            sendMessage("DELTA_APPLY_ACK|${gson.toJson(mapOf("acked_seq" to toSeq))}")
            return toSeq
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
            database.customerDao().getByCustomerNo(code)?.let { database.customerDao().delete(it) }
            return
        }
        val name = payload.get("name")?.asString ?: code
        val phone = payload.get("phone")?.asString ?: ""
        val existing = database.customerDao().getByCustomerNo(code)
        if (existing == null) {
            database.customerDao().insertCustomer(
                Customer(
                    customerNo = code,
                    customerName = name,
                    phone = phone,
                    updateTime = System.currentTimeMillis(),
                    syncStatus = 1,
                )
            )
        } else {
            database.customerDao().insertCustomer(
                existing.copy(
                    customerName = name,
                    phone = phone,
                    updateTime = System.currentTimeMillis(),
                    syncStatus = 1,
                )
            )
        }
    }

    private suspend fun applyLocationDelta(opType: String, payload: com.google.gson.JsonObject) {
        val code = payload.get("code")?.asString ?: return
        if (opType == "DELETE") {
            database.locationDao().getByLocationNo(code)?.let { database.locationDao().delete(it) }
            return
        }
        val name = payload.get("name")?.asString ?: code
        val enabled = payload.get("enabled")?.asBoolean ?: true
        val existing = database.locationDao().getByLocationNo(code)
        if (existing == null) {
            database.locationDao().insert(
                Location(
                    locationNo = code,
                    locationName = name,
                    enabled = enabled,
                    syncStatus = 1,
                )
            )
        } else {
            database.locationDao().update(
                existing.copy(
                    locationName = name,
                    enabled = enabled,
                    syncStatus = 1,
                )
            )
        }
    }

    private suspend fun applyOperatorDelta(opType: String, payload: com.google.gson.JsonObject) {
        val code = payload.get("code")?.asString ?: return
        if (opType == "DELETE") {
            database.operatorDao().getByOperatorNo(code)?.let { database.operatorDao().delete(it) }
            return
        }
        val name = payload.get("name")?.asString ?: code
        val enabled = payload.get("enabled")?.asBoolean ?: true
        val existing = database.operatorDao().getByOperatorNo(code)
        if (existing == null) {
            database.operatorDao().insert(
                Operator(
                    operatorNo = code,
                    name = name,
                    enabled = enabled,
                    syncStatus = 1,
                )
            )
        } else {
            database.operatorDao().update(
                existing.copy(
                    name = name,
                    enabled = enabled,
                    syncStatus = 1,
                )
            )
        }
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
                var receivedChunks = 0
                var totalChunks = 0
                val stringBuilder = StringBuilder()

                fullSyncListener = { message ->
                    Log.d(TAG, "📥 全量同步处理器收到消息: ${message.take(100)}")

                    when {
                        message.startsWith("FULL_SYNC_START|") -> {
                            val parts = message.split("|")
                            if (parts.size >= 3) {
                                totalChunks = parts[1].toIntOrNull() ?: 0
                                Log.i(TAG, "📦 开始接收全量数据，共 $totalChunks 块")
                                onProgress?.invoke(2, 3, "接收数据")
                            }
                        }

                        message.startsWith("FULL_SYNC_DATA|") -> {
                            // ✅ 修复：直接按 | 分割，格式: FULL_SYNC_DATA|块索引|总块数|JSON数据
                            val parts = message.split("|", limit = 4)
                            if (parts.size >= 4) {
                                val chunkIndex = parts[1].toIntOrNull() ?: 0
                                totalChunks = parts[2].toIntOrNull() ?: totalChunks
                                val chunkData = parts[3]

                                stringBuilder.append(chunkData)
                                receivedChunks++

                                Log.d(TAG, "📦 接收数据块: $chunkIndex/$totalChunks, receivedChunks=$receivedChunks, 数据长度=${chunkData.length}")

                                if (receivedChunks % 10 == 0 || receivedChunks == totalChunks) {
                                    Log.d(TAG, "📦 接收进度: $receivedChunks/$totalChunks")
                                    onProgress?.invoke(2, 3, "接收数据 $receivedChunks/$totalChunks")
                                }
                            } else {
                                Log.w(TAG, "⚠️ FULL_SYNC_DATA 解析失败，原始消息: ${message.take(200)}")
                            }
                        }

                        message.startsWith("FULL_SYNC_END|") -> {
                            Log.i(TAG, "✅ 全量数据接收完成，共 $receivedChunks 块，累计数据长度: ${stringBuilder.length}")
                            syncDeferred.complete(true)
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
                    Log.e(TAG, "❌ 等待全量同步数据超时，已接收 $receivedChunks 块")
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

                if (receivedChunks < totalChunks) {
                    val errorMsg = "数据接收不完整：$receivedChunks/$totalChunks"
                    Log.e(TAG, "❌ $errorMsg")
                    onResult?.invoke(false, errorMsg)
                    _syncState.value = SyncState.Failed(errorMsg)
                    return@withContext false
                }

                onProgress?.invoke(3, 3, "更新本地数据")
                Log.i(TAG, "🔄 开始解析并替换本地数据，数据长度: ${stringBuilder.length}")

                val jsonData = stringBuilder.toString()
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
                                            locationNo = "",
                                            enabled = true,
                                            syncStatus = 1
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
                                            operatorNo = "",
                                            enabled = true,
                                            syncStatus = 1
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

                            // 同步客户数据
                            if (fullData.data.clients != null) {
                                for (clientData in fullData.data.clients) {
                                    val existing = database.customerDao().getByCustomerNo(clientData.code)
                                    if (existing != null) {
                                        database.customerDao().updateCustomer(
                                            existing.id,
                                            clientData.name,
                                            clientData.phone ?: ""
                                        )
                                    } else {
                                        val customer = com.pingwei.lengkubao.data.db.entity.Customer(
                                            customerNo = clientData.code,
                                            customerName = clientData.name,
                                            phone = clientData.phone ?: ""
                                        )
                                        database.customerDao().insertCustomer(customer)
                                    }
                                }
                                Log.i(TAG, "✅ 客户数据同步完成: ${fullData.data.clients.size} 条")
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
                                                locationNo = location.locationNo,
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
                    if (!syncPackagingBill(bill.id)) inventoryRelatedSyncFailed = true
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

                val flagInfo = if (packagingWithFlag > 0) "，其中退包装单 $packagingWithFlag 张" else ""
                val advanceInfo = if (advanceCount > 0) "，预支款 $advanceCount 条" else ""
                val deductionInfo = if (deductionCount > 0) "，扣款 $deductionCount 条" else ""
                val successMsg = "批量同步数据发送完成：共发送 $totalSync 条数据$flagInfo$advanceInfo$deductionInfo，等待服务器逐个确认"
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
    val stocks: List<StockSyncData>?,
    val inbound_stats: InboundStatsSyncData?
)

data class ClientSyncData(
    val code: String,
    val name: String,
    val phone: String?
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