package com.pingwei.lengkubao.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.fiscal.FiscalYearEvents

object SyncConnectionObserver {
    const val ACTION_TCP_CONNECTION_STATUS = "TCP_CONNECTION_STATUS"
    const val EXTRA_IS_CONNECTED = "is_connected"
    const val EXTRA_MESSAGE = "message"

    fun formatConnectionLabel(state: TcpSyncManager.ConnectionState): String {
        return when (state) {
            TcpSyncManager.ConnectionState.CONNECTED -> "✅ 已连接"
            TcpSyncManager.ConnectionState.CONNECTING -> "🔌 连接中..."
            TcpSyncManager.ConnectionState.ERROR -> "❌ 连接失败"
            TcpSyncManager.ConnectionState.DISCONNECTED -> "📴 已断开"
            TcpSyncManager.ConnectionState.SYNCING -> "🔄 同步中..."
            TcpSyncManager.ConnectionState.WAITING_RECONNECT -> "⏳ 等待重连..."
        }
    }

    fun isConnected(state: TcpSyncManager.ConnectionState): Boolean {
        return state == TcpSyncManager.ConnectionState.CONNECTED
    }
}

/**
 * 在 resume、年份切换、TCP 状态广播时递增，用于强制重新订阅 TcpSyncManager。
 */
@Composable
fun rememberSyncCollectorKey(): Int {
    var collectorKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                collectorKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    SyncConnectionObserver.ACTION_TCP_CONNECTION_STATUS,
                    FiscalYearEvents.ACTION_FISCAL_YEAR_CHANGED -> collectorKey++
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(SyncConnectionObserver.ACTION_TCP_CONNECTION_STATUS)
            addAction(FiscalYearEvents.ACTION_FISCAL_YEAR_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(FiscalYearEvents.ACTION_FISCAL_YEAR_CHANGED),
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    return collectorKey
}

@Composable
fun rememberTcpConnectionStatus(): Pair<Boolean, String> {
    var connectionState by remember {
        mutableStateOf(TcpSyncManager.ConnectionState.DISCONNECTED)
    }
    val collectorKey = rememberSyncCollectorKey()

    LaunchedEffect(collectorKey) {
        val syncManager = LengKuBaoApplication.getSyncManager()
        connectionState = syncManager.connectionState.value
        syncManager.connectionState.collect { state ->
            connectionState = state
        }
    }

    val label = SyncConnectionObserver.formatConnectionLabel(connectionState)
    val connected = SyncConnectionObserver.isConnected(connectionState)
    return connected to label
}

@Composable
fun rememberTcpConnectionState(): TcpSyncManager.ConnectionState {
    var connectionState by remember {
        mutableStateOf(TcpSyncManager.ConnectionState.DISCONNECTED)
    }
    val collectorKey = rememberSyncCollectorKey()

    LaunchedEffect(collectorKey) {
        val syncManager = LengKuBaoApplication.getSyncManager()
        connectionState = syncManager.connectionState.value
        syncManager.connectionState.collect { state ->
            connectionState = state
        }
    }

    return connectionState
}

@Composable
fun rememberTcpSyncState(): SyncState {
    var syncState by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    val collectorKey = rememberSyncCollectorKey()

    LaunchedEffect(collectorKey) {
        val syncManager = LengKuBaoApplication.getSyncManager()
        syncState = syncManager.syncState.value
        syncManager.syncState.collect { state ->
            syncState = state
        }
    }

    return syncState
}
