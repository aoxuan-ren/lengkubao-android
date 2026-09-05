package com.pingwei.lengkubao.fiscal

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.TcpSyncManager
import com.pingwei.lengkubao.utils.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 切换/保存/新建年份时的统一协调（停止同步 → 关闭库 → 操作 → 重开库 → 重启同步）。
 */
object YearSwitchCoordinator {
    private const val TAG = "YearSwitchCoordinator"

    suspend fun switchToYear(context: Context, year: Int): Result<Unit> {
        return executeWithDbReload(context) {
            FiscalYearManager.switchToYear(year)
        }
    }

    suspend fun saveCurrentYear(context: Context): Result<Unit> {
        return executeWithDbReload(context) {
            FiscalYearManager.saveCurrentYear(context, overwrite = true)
        }
    }

    suspend fun createNewYear(context: Context, year: Int): Result<Unit> {
        return executeWithDbReload(context) {
            FiscalYearManager.createNewYear(context, year)
        }
    }

    private suspend fun executeWithDbReload(
        context: Context,
        operation: () -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        val wasAutoSync = prefs.getBoolean(Constant.PREF_AUTO_SYNC, Constant.PREF_AUTO_SYNC_DEFAULT)

        try {
            Log.i(TAG, "开始年份操作，停止同步服务…")
            TcpSyncService.stopService(appContext)
            appContext.stopService(Intent(appContext, TcpSyncService::class.java))
            delay(300)
            TcpSyncManager.destroyInstance()
            AppDatabase.destroyInstance()

            operation()

            LengKuBaoApplication.reloadDatabaseInstance()

            if (wasAutoSync) {
                TcpSyncService.startService(appContext)
            }

            LocalBroadcastManager.getInstance(appContext)
                .sendBroadcast(Intent(FiscalYearEvents.ACTION_FISCAL_YEAR_CHANGED))

            Log.i(TAG, "年份操作完成")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "年份操作失败: ${e.message}", e)
            try {
                LengKuBaoApplication.reloadDatabaseInstance()
            } catch (_: Exception) {
            }
            Result.failure(e)
        }
    }
}
