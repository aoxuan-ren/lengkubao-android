// File: LengKuBaoApplication.kt
package com.pingwei.lengkubao

import android.app.Application
import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.fiscal.FiscalYearManager
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.utils.AppCacheCleaner
import com.pingwei.lengkubao.utils.Constant
import com.pingwei.lengkubao.utils.PrinterStateManager
import com.sunmi.tms.api.TMSApi
import com.sunmi.tms.exception.TmsServiceDisconnectedException

class LengKuBaoApplication : Application() {

    private val TAG = "LengKuBaoApp"
    private lateinit var tmsApi: TMSApi

    companion object {
        private lateinit var instance: LengKuBaoApplication
        // 全局数据库单例，仅初始化一次
        private lateinit var appDatabase: AppDatabase

        /**
         * 获取Application全局单例（供全应用调用）
         */
        fun getInstance(): LengKuBaoApplication = instance

        /**
         * 获取TcpSyncManager全局单例
         */
        fun getSyncManager(): TcpSyncManager {
            val syncManager = TcpSyncManager.getInstance(instance, appDatabase)
            Log.d("LengKuBaoApp", "📡 获取TcpSyncManager实例: $syncManager")
            return syncManager
        }

        /**
         * 获取数据库实例
         */
        fun getDatabase(): AppDatabase = appDatabase

        /**
         * 年份切换后重新加载数据库单例。
         */
        fun reloadDatabaseInstance() {
            AppDatabase.destroyInstance()
            appDatabase = AppDatabase.getInstance(instance)
            Log.d("LengKuBaoApp", "Database reloaded: ${FiscalYearManager.getActiveDbName()}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 先执行待处理缓存清理，避免运行中清理带来的不稳定
        AppCacheCleaner.performPendingCleanupOnStartup(this)

        // 1. 初始化打印机状态管理器
        PrinterStateManager.init(this)
        Log.d(TAG, "🔄 Application starting...")

        // 2. 初始化数据库（合并原有逻辑）
        initDatabase()

        // 3. 初始化商米SDK
        initTMSSdk()

        // 4. 初始化TCP同步（新增功能）
        initTcpSync()

        Log.i(TAG, "✅ 应用初始化完成，所有组件已加载")
    }

    private fun initDatabase() {
        FiscalYearManager.initialize(this)
        try {
            AppDatabase.destroyInstance()
            Log.d(TAG, "✅ Old database instance cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear old database: ${e.message}")
        }

        appDatabase = AppDatabase.getInstance(this)
        Log.d(TAG, "✅ Database initialized: ${FiscalYearManager.getActiveDbName()}")
    }

    private fun initTMSSdk() {
        try {
            tmsApi = TMSApi()
            tmsApi.setLoggable(true)
            Log.d(TAG, "✅ TMS SDK initialized")
        } catch (e: TmsServiceDisconnectedException) {
            Log.e(TAG, "❌ TMS service disconnected: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ TMS SDK init error: ${e.message}", e)
        }
    }

    private fun initTcpSync() {
        // 初始化时自动启动同步服务（如果配置了自动同步）
        val prefs = getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constant.PREF_AUTO_SYNC, Constant.PREF_AUTO_SYNC_DEFAULT)) {
            Log.i(TAG, "📡 应用启动，自动开启后台同步服务")
            TcpSyncService.startService(this)
        }
    }

    fun getTmsApi(): TMSApi {
        if (!::tmsApi.isInitialized) {
            throw TmsServiceDisconnectedException("TMS SDK not initialized")
        }
        return tmsApi
    }

    override fun onTerminate() {
        super.onTerminate()
        // 销毁TcpSyncManager单例，释放Socket/协程/通道资源
        TcpSyncManager.destroyInstance()
        Log.i(TAG, "🔴 应用退出，TcpSyncManager资源已释放")
    }
}