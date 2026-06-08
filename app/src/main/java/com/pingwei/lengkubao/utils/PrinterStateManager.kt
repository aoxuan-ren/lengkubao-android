package com.pingwei.lengkubao.utils

import android.content.Context
import android.content.SharedPreferences
import com.pingwei.lengkubao.service.SunmiPrintService

/**
 * 打印机状态管理器
 * 负责持久化和恢复打印机连接状态
 */
object PrinterStateManager {

    private const val PREFS_NAME = "printer_state_manager"
    private const val KEY_LAST_CONNECTED = "last_connected"
    private const val KEY_LAST_CONNECT_TIME = "last_connect_time"
    private const val KEY_AUTO_RECOVER = "auto_recover"
    private const val KEY_MAX_RETRY = "max_retry"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConnectionState(isConnected: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LAST_CONNECTED, isConnected)
            .putLong(KEY_LAST_CONNECT_TIME, System.currentTimeMillis())
            .apply()
    }

    fun shouldAutoRecover(): Boolean {
        val lastConnected = prefs.getBoolean(KEY_LAST_CONNECTED, false)
        val lastConnectTime = prefs.getLong(KEY_LAST_CONNECT_TIME, 0)
        val autoRecover = prefs.getBoolean(KEY_AUTO_RECOVER, true)

        // 检查是否应该自动恢复：
        // 1. 上次是连接状态
        // 2. 开启了自动恢复
        // 3. 上次连接在10分钟内（避免太久远的状态）
        return lastConnected && autoRecover &&
                (System.currentTimeMillis() - lastConnectTime < 10 * 60 * 1000)
    }

    fun getConnectionState(): Map<String, Any> {
        return mapOf(
            "last_connected" to prefs.getBoolean(KEY_LAST_CONNECTED, false),
            "last_connect_time" to prefs.getLong(KEY_LAST_CONNECT_TIME, 0),
            "auto_recover_enabled" to prefs.getBoolean(KEY_AUTO_RECOVER, true),
            "max_retry_count" to prefs.getInt(KEY_MAX_RETRY, 3)
        )
    }

    fun setAutoRecover(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECOVER, enabled).apply()
    }

    fun setMaxRetryCount(count: Int) {
        prefs.edit().putInt(KEY_MAX_RETRY, count).apply()
    }

    /**
     * 清理所有状态
     */
    fun clearAllStates() {
        prefs.edit().clear().apply()
    }
}