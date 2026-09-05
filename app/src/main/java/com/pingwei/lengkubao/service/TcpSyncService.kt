package com.pingwei.lengkubao.service

import android.app.*
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pingwei.lengkubao.R
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.sync.SyncState
import com.pingwei.lengkubao.sync.mdns.MdnsDeviceDiscovery
import com.pingwei.lengkubao.sync.udp.UdpDeviceDiscovery
import com.pingwei.lengkubao.ui.main.MainActivity
import com.pingwei.lengkubao.utils.Constant
import com.pingwei.lengkubao.utils.SyncStatusUtils
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

class TcpSyncService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var syncManager: TcpSyncManager
    private var isServiceRunning = false

    companion object {
        const val CHANNEL_ID = "tcp_sync_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START_SYNC = "com.pingwei.lengkubao.TCP_START_SYNC"
        const val ACTION_STOP_SYNC = "com.pingwei.lengkubao.TCP_STOP_SYNC"
        const val ACTION_SYNC_NOW = "com.pingwei.lengkubao.TCP_SYNC_NOW"
        const val ACTION_SYNC_PENDING_NOW = "com.pingwei.lengkubao.TCP_SYNC_PENDING_NOW"
        const val ACTION_SYNC_COMPLETE = "com.pingwei.lengkubao.TCP_SYNC_COMPLETE"

        const val ACTION_SYNC_CONFIG = "com.pingwei.lengkubao.TCP_SYNC_CONFIG"
        const val EXTRA_BILL_ID = "bill_id"
        const val EXTRA_BILL_TYPE = "bill_type"
        const val EXTRA_RESULT = "sync_result"
        // 新增：广播透传单据ID和类型
        const val EXTRA_BILL_ID_BROADCAST = "broadcast_bill_id"
        const val EXTRA_BILL_TYPE_BROADCAST = "broadcast_bill_type"
        const val EXTRA_ERROR_MSG = "error_message"
        const val EXTRA_DUPLICATE_NOTICE = "duplicate_notice"
        const val EXTRA_FORCE_RESET_SYNC = "force_reset_sync"
        const val EXTRA_CONFIG_ID = "config_id"
        const val EXTRA_CONFIG_TYPE = "config_type"
        // 启动同步服务
        fun startService(context: Context) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_START_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // 新增：应用启动时自动启动
        fun startOnAppLaunch(context: Context) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_START_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun pushPendingConfigOps(context: Context) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_SYNC_CONFIG
                putExtra(EXTRA_CONFIG_ID, -1L)
                putExtra(EXTRA_CONFIG_TYPE, "PENDING_OPS")
            }
            context.startService(intent)
        }

        fun syncConfigNow(context: Context, configId: Long, configType: String) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_SYNC_CONFIG
                putExtra(EXTRA_CONFIG_ID, configId)
                putExtra(EXTRA_CONFIG_TYPE, configType)
            }
            context.startService(intent)
        }

        // 停止同步服务
        fun stopService(context: Context) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
            context.startService(intent)
        }

        // 立即同步某张单据
        fun syncBillNow(context: Context, billId: Long, billType: String, forceResetSync: Boolean = false) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_SYNC_NOW
                putExtra(EXTRA_BILL_ID, billId)
                putExtra(EXTRA_BILL_TYPE, billType)
                putExtra(EXTRA_FORCE_RESET_SYNC, forceResetSync)
            }
            context.startService(intent)
        }

        fun syncPendingNow(context: Context) {
            val intent = Intent(context, TcpSyncService::class.java).apply {
                action = ACTION_SYNC_PENDING_NOW
            }
            context.startService(intent)
        }
    }

    private val inFlightTasks = ConcurrentHashMap.newKeySet<String>()
    private val presaleResyncPending = ConcurrentHashMap.newKeySet<Long>()
    private val packagingResyncPending = ConcurrentHashMap.newKeySet<Long>()

    override fun onCreate() {
        super.onCreate()
        refreshSyncManager()
        // 创建通知通道（8.0+必须）
        createNotificationChannel()
        // 启动前台服务，避免被系统杀死
        startForeground(NOTIFICATION_ID, createNotification("TCP同步服务启动中..."))
    }

    private fun refreshSyncManager(): TcpSyncManager {
        val database = AppDatabase.getInstance(applicationContext)
        syncManager = TcpSyncManager.getInstance(applicationContext, database)
        return syncManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 🔧 关键修改：如果没有intent或intent为null，也自动启动同步服务
        val action = intent?.action ?: ACTION_START_SYNC
        refreshSyncManager()

        when (action) {
            ACTION_START_SYNC -> {
                if (!isServiceRunning) {
                    startSyncService()
                } else {
                    Log.d("TcpSyncService", "🔧 服务已在运行，检查连接状态...")
                    // 检查连接状态，如果断开则重连
                    scope.launch {
                        val currSyncManager = refreshSyncManager()
                        if (!currSyncManager.isConnected()) {
                            Log.i("TcpSyncService", "🔄 检测到连接断开，触发重连")
                            currSyncManager.enableColdStartMode()
                            currSyncManager.connect()
                        }
                    }
                }
            }
            ACTION_STOP_SYNC -> {
                stopSyncService()
                stopSelf()
            }
            ACTION_SYNC_NOW -> {
                val billId = intent?.getLongExtra(EXTRA_BILL_ID, 0)
                val billType = intent?.getStringExtra(EXTRA_BILL_TYPE) ?: ""
                val forceResetSync = intent?.getBooleanExtra(EXTRA_FORCE_RESET_SYNC, false) ?: false
                if (billId != null) {
                    if (billId > 0 && billType.isNotBlank()) {
                        syncBillImmediately(billId, billType, forceResetSync)
                    } else {
                        Log.e("TcpSyncService", "实时同步参数异常：billId=$billId, billType=$billType")
                    }
                }
            }
            ACTION_SYNC_PENDING_NOW -> {
                syncPendingDataImmediately()
            }
            ACTION_SYNC_CONFIG -> {
                val configId = intent?.getLongExtra(EXTRA_CONFIG_ID, 0) ?: 0L
                val configType = intent?.getStringExtra(EXTRA_CONFIG_TYPE) ?: ""
                if (configType == "PENDING_OPS") {
                    pushPendingConfigOpsImmediately()
                } else if (configId > 0 && configType.isNotBlank()) {
                    syncConfigImmediately(configId, configType)
                }
            }
            else -> {
                // 默认情况：自动启动同步
                if (!isServiceRunning) {
                    startSyncService()
                }
            }
        }
        // 服务被杀死后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // 取消协程作用域，释放资源
        job.cancel()
        // 停止前台服务
        stopForeground(true)
        Log.i("TcpSyncService", "🔴 TCP同步服务已销毁")
    }

    // 添加同步方法
    private fun syncConfigImmediately(configId: Long, configType: String) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!isServiceRunning) {
                    Log.e(TAG, "同步失败：服务未启动")
                    return@launch
                }
                SyncTrigger.triggerConfigSync(applicationContext, configType, configId)
                syncManager.pushPendingConfigOps()
            } catch (e: Exception) {
                Log.e(TAG, "同步基础配置失败", e)
            }
        }
    }

    private fun pushPendingConfigOpsImmediately() {
        scope.launch(Dispatchers.IO) {
            try {
                if (!isServiceRunning) {
                    Log.e(TAG, "同步失败：服务未启动")
                    return@launch
                }
                syncManager.pushPendingConfigOps()
            } catch (e: Exception) {
                Log.e(TAG, "推送待上传配置失败", e)
            }
        }
    }
    // 启动核心同步逻辑
    private fun startSyncService() {
        scope.launch {
            isServiceRunning = true
            refreshSyncManager()
            updateNotification("正在初始化...")

            // 检查是否启用自动配对
            val prefs = getSharedPreferences("sync_config", Context.MODE_PRIVATE)
            val autoConnect = prefs.getBoolean(Constant.PREF_AUTO_CONNECT, true)
            val savedPairingCode = prefs.getString(Constant.PREF_PAIRING_CODE, "")

            if (autoConnect && !savedPairingCode.isNullOrBlank()) {
                updateNotification("🔍 正在自动扫描配对设备...")
                Log.i(TAG, "🔍 启动自动配对扫描，配对码: $savedPairingCode")

                val discoveryMethod = prefs.getString(Constant.PREF_DISCOVERY_METHOD, "both") ?: "both"
                var discoveredIp: String? = null
                var discoveredPort: Int? = null

                if (discoveryMethod == "udp" || discoveryMethod == "both") {
                    val udpDevice = UdpDeviceDiscovery.getInstance(applicationContext).autoConnect(savedPairingCode)
                    if (udpDevice != null) {
                        discoveredIp = udpDevice.ip
                        discoveredPort = udpDevice.port
                        Log.i(TAG, "✅ UDP自动发现成功: ${udpDevice.deviceName} (${udpDevice.ip}:${udpDevice.port})")
                    }
                }

                if (discoveredIp == null && (discoveryMethod == "mdns" || discoveryMethod == "both")) {
                    val mdnsDevice = MdnsDeviceDiscovery.getInstance(applicationContext).autoConnect(savedPairingCode)
                    if (mdnsDevice != null) {
                        discoveredIp = mdnsDevice.ip
                        discoveredPort = mdnsDevice.port
                        Log.i(TAG, "✅ mDNS自动发现成功: ${mdnsDevice.deviceName} (${mdnsDevice.ip}:${mdnsDevice.port})")
                    }
                }

                if (discoveredIp != null && discoveredPort != null) {
                    updateNotification("✅ 找到配对设备: $discoveredIp")
                    syncManager.updateConfig(
                        TcpSyncManager.SyncConfig(
                            serverIp = discoveredIp,
                            serverPort = discoveredPort
                        )
                    )
                    prefs.edit().apply {
                        putString(Constant.PREF_PAIRED_SERVER_IP, discoveredIp)
                        putInt(Constant.PREF_PAIRED_SERVER_PORT, discoveredPort)
                    }.apply()
                    syncManager.notifyDiscoveryApplied()
                    delay(500)
                } else {
                    Log.w(TAG, "⚠️ 自动发现未找到设备，使用保存的IP")
                    val savedIp = prefs.getString(Constant.PREF_PAIRED_SERVER_IP, null)
                    if (!savedIp.isNullOrBlank() &&
                        TcpSyncManager.isValidServerIp(savedIp) &&
                        !TcpSyncManager.isUntrustedCachedIp(applicationContext, savedIp)
                    ) {
                        val savedPort = prefs.getInt(Constant.PREF_PAIRED_SERVER_PORT, 8080)
                        syncManager.updateConfig(
                            TcpSyncManager.SyncConfig(
                                serverIp = savedIp,
                                serverPort = savedPort
                            )
                        )
                    } else if (!savedIp.isNullOrBlank()) {
                        Log.w(TAG, "⚠️ 保存的IP不可用($savedIp)，将依赖后台自动发现")
                    }
                }
            }

            syncManager.enableColdStartMode()
            // 连接服务器
            updateNotification("正在连接服务器...")
            syncManager.connect()

            // 监听连接状态
            syncManager.connectionState.collect { state ->
                val notifyMsg = when (state) {
                    TcpSyncManager.ConnectionState.CONNECTED -> {
                        "✅ 已连接到同步服务器"
                    }
                    TcpSyncManager.ConnectionState.CONNECTING -> "🔌 正在连接/注册..."
                    TcpSyncManager.ConnectionState.ERROR -> "❌ 连接失败"
                    TcpSyncManager.ConnectionState.DISCONNECTED -> "📴 已断开"
                    TcpSyncManager.ConnectionState.SYNCING -> "🔄 正在同步数据..."
                    TcpSyncManager.ConnectionState.WAITING_RECONNECT -> "⏳ 等待重连..."
                }
                updateNotification(notifyMsg)
            }
        }
    }

    // 停止同步服务
    private fun stopSyncService() {
        scope.launch {
            isServiceRunning = false
            // 断开TCP连接
            syncManager.disconnect()
            updateNotification("TCP同步服务已停止")
        }
    }

    // 核心优化：实时同步单张单据方法（带确认机制）
    private fun syncBillImmediately(billId: Long, billType: String, forceResetSync: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val taskKey = "BILL|$billType|$billId"
            if (!inFlightTasks.add(taskKey)) {
                if (billType == "PRESALE") {
                    presaleResyncPending.add(billId)
                    Log.w(TAG, "[$billType-$billId] 同步进行中，已排队等待再次上传")
                } else if (billType == "PACKAGING") {
                    packagingResyncPending.add(billId)
                    Log.w(TAG, "[$billType-$billId] 同步进行中，已排队等待再次上传")
                } else {
                    Log.w(TAG, "[$billType-$billId] 同步任务已在进行中，忽略重复触发")
                }
                return@launch
            }
            val billTypeName = when (billType) {
                "IN_STOCK" -> "入库单"
                "SALE" -> "销售单"
                "PACKAGING" -> "包装单"
                "ADVANCE" -> "预支款"
                "DEDUCTION" -> "扣款"
                "PRESALE" -> "预售单"
                "PRESALE_PAYMENT" -> "预售收款"
                "PRESALE_OUTBOUND" -> "预售出库"
                else -> "未知单据"
            }
            val tag = "[$billType-$billId]"
            val notifyPrefix = "实时同步$billTypeName($billId)"

            try {
                // 前置校验
                if (!isServiceRunning) {
                    val errorMsg = "同步服务未启动"
                    Log.e(TAG, "$tag ${notifyPrefix}失败：$errorMsg")
                    updateNotification("❌ ${notifyPrefix}失败：$errorMsg")
                    sendSyncCompleteBroadcast(billId, billType, false, errorMsg)
                    return@launch
                }

                val currentConnState = syncManager.connectionState.value
                if (currentConnState != TcpSyncManager.ConnectionState.CONNECTED) {
                    val errorMsg = "TCP未连接（当前状态：${currentConnState.name}）"
                    Log.e(TAG, "$tag ${notifyPrefix}失败：$errorMsg")
                    updateNotification("❌ ${notifyPrefix}失败：$errorMsg")
                    sendSyncCompleteBroadcast(billId, billType, false, errorMsg)
                    return@launch
                }

                // 开始同步
                updateNotification("🔄 $notifyPrefix...")
                Log.i(TAG, "$tag 开始执行$notifyPrefix")

                if (billType == "PACKAGING" && forceResetSync) {
                    SyncStatusUtils.resetPackagingBillSyncStatus(this@TcpSyncService, billId)
                    syncManager.clearConfirmedItemsForPackagingBill(billId)
                }

                // 执行同步并等待结果
                if (billType == "PACKAGING") {
                    val packagingResult = syncManager.syncPackagingBill(billId)
                    if (packagingResult.success) {
                        val successMsg = "$billTypeName $billId 同步成功"
                        Log.i(TAG, "$tag ✅ $successMsg")
                        updateNotification("✅ $successMsg")
                        sendSyncCompleteBroadcast(
                            billId = billId,
                            billType = billType,
                            isSuccess = true,
                            errorMsg = "",
                            duplicateNotice = packagingResult.duplicateNotice,
                        )
                    } else {
                        val failMsg = packagingResult.errorMessage ?: "$billTypeName $billId 同步失败"
                        Log.e(TAG, "$tag ❌ $failMsg")
                        updateNotification("❌ $failMsg")
                        sendSyncCompleteBroadcast(
                            billId = billId,
                            billType = billType,
                            isSuccess = false,
                            errorMsg = failMsg,
                        )
                    }
                } else {
                    val syncResult = when (billType) {
                        "IN_STOCK" -> syncManager.syncInStockBill(billId)
                        "SALE" -> syncManager.syncSaleBill(billId)
                        "ADVANCE" -> syncManager.syncAdvance(billId)
                        "DEDUCTION" -> syncManager.syncDeduction(billId)
                        "PRESALE" -> syncManager.syncPreSaleBill(billId)
                        "PRESALE_PAYMENT" -> syncManager.syncPreSalePayment(billId)
                        "PRESALE_OUTBOUND" -> syncManager.syncPreSaleOutbound(billId)
                        else -> false
                    }

                    if (syncResult) {
                        val successMsg = "$billTypeName $billId 同步成功"
                        Log.i(TAG, "$tag ✅ $successMsg")
                        updateNotification("✅ $successMsg")
                        sendSyncCompleteBroadcast(billId, billType, true, "")
                    } else {
                        val failMsg = "$billTypeName $billId 同步失败"
                        Log.e(TAG, "$tag ❌ $failMsg")
                        updateNotification("❌ $failMsg")
                        sendSyncCompleteBroadcast(billId, billType, false, "服务器保存失败或超时")
                    }
                }

            } catch (e: CancellationException) {
                Log.w(TAG, "$tag ${notifyPrefix}被取消", e)
                updateNotification("⚠️ ${notifyPrefix}被取消")
                sendSyncCompleteBroadcast(billId, billType, false, "同步被取消")
            } catch (e: Exception) {
                val errorMsg = e.message ?: "同步执行异常"
                Log.e(TAG, "$tag ${notifyPrefix}失败，执行异常", e)
                updateNotification("❌ ${notifyPrefix}失败：$errorMsg")
                sendSyncCompleteBroadcast(billId, billType, false, errorMsg)
            } finally {
                inFlightTasks.remove(taskKey)
                if (billType == "PRESALE" && presaleResyncPending.remove(billId)) {
                    Log.i(TAG, "$tag 执行排队中的预售单再次上传")
                    syncBillImmediately(billId, billType)
                } else if (billType == "PACKAGING" && packagingResyncPending.remove(billId)) {
                    Log.i(TAG, "$tag 执行排队中的包装单再次上传")
                    syncBillImmediately(billId, billType)
                }
            }
        }
    }

    private fun syncPendingDataImmediately() {
        scope.launch(Dispatchers.IO) {
            val taskKey = "BATCH|PENDING"
            if (!inFlightTasks.add(taskKey)) {
                Log.w(TAG, "批量同步任务已在进行中，忽略重复触发")
                sendSyncCompleteBroadcast(-1L, "BATCH", false, "批量同步任务已在进行中")
                return@launch
            }
            try {
                if (!isServiceRunning) {
                    val errorMsg = "同步服务未启动，请检查同步配置"
                    updateNotification("❌ 批量同步失败：$errorMsg")
                    sendSyncCompleteBroadcast(-1L, "BATCH", false, errorMsg)
                    return@launch
                }
                val currentConnState = syncManager.connectionState.value
                if (currentConnState != TcpSyncManager.ConnectionState.CONNECTED) {
                    val errorMsg = "TCP未连接（${currentConnState.name}），请确认电脑端已启动同步服务"
                    updateNotification("❌ 批量同步失败：$errorMsg")
                    sendSyncCompleteBroadcast(-1L, "BATCH", false, errorMsg)
                    return@launch
                }
                updateNotification("🔄 开始批量同步未完成单据...")
                val success = syncManager.syncPendingData()
                if (success) {
                    updateNotification("✅ 批量同步任务已触发，等待服务器确认")
                    sendSyncCompleteBroadcast(-1L, "BATCH", true, "")
                } else {
                    val errorMsg = "批量同步执行失败，请查看日志"
                    updateNotification("❌ $errorMsg")
                    sendSyncCompleteBroadcast(-1L, "BATCH", false, errorMsg)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "批量同步被取消", e)
                updateNotification("⚠️ 批量同步被取消")
                sendSyncCompleteBroadcast(-1L, "BATCH", false, "批量同步被取消")
            } catch (e: Exception) {
                Log.e(TAG, "批量同步执行异常", e)
                updateNotification("❌ 批量同步异常：${e.message}")
                sendSyncCompleteBroadcast(-1L, "BATCH", false, e.message ?: "批量同步异常")
            } finally {
                inFlightTasks.remove(taskKey)
            }
        }
    }


    /**
     * 检查未同步的基础配置
     */
    suspend fun checkUnsyncedConfigs(): Map<String, Int> {
        return syncManager.checkUnsyncedConfigs()
    }

    /**
     * 应用启动时自动同步基础配置
     */
    fun autoSyncConfigsOnStartup() {
        scope.launch {
            delay(2000) // 延迟2秒，等待服务完全启动
            if (isServiceRunning) {
                Log.i(TAG, "🚀 应用启动，自动同步基础配置")
                syncManager.autoSyncConfigsOnStartup()
            }
        }
    }
    // 封装同步完成广播发送方法，统一透传参数，避免代码冗余
    private fun sendSyncCompleteBroadcast(
        billId: Long,
        billType: String,
        isSuccess: Boolean,
        errorMsg: String,
        duplicateNotice: String? = null,
    ) {
        Intent(ACTION_SYNC_COMPLETE).apply {
            putExtra(EXTRA_BILL_ID_BROADCAST, billId)
            putExtra(EXTRA_BILL_TYPE_BROADCAST, billType)
            putExtra(EXTRA_RESULT, isSuccess)
            putExtra(EXTRA_ERROR_MSG, errorMsg)
            if (!duplicateNotice.isNullOrBlank()) {
                putExtra(EXTRA_DUPLICATE_NOTICE, duplicateNotice)
            }
            sendBroadcast(this)
        }
    }

    // 创建通知通道（Android 8.0+ 必须）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TCP数据同步",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TCP长连接数据同步服务，保持手持端与服务器数据一致"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 创建前台服务通知
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // 挂起意图（点击通知跳转到主页面）
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // 构建通知 → 修正此处图标为你项目实际的同步图标
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("冷库宝TCP同步")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sync) // 替换为项目中实际的图标ID，如R.drawable.ic_launcher
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 不可手动清除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true) // 仅首次显示通知声音/震动
            .build()
    }

    // 更新通知内容
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}