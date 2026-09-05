package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.sync.PackagingSyncResult
import com.pingwei.lengkubao.sync.TcpSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PackagingSyncHelper {
    private const val TAG = "PackagingSyncHelper"

    /** 保存后立即上传，不依赖 auto_sync 开关。 */
    fun syncBillToServerAsync(context: Context, billId: Long) {
        TcpSyncService.startService(context)
        TcpSyncService.syncBillNow(context, billId, "PACKAGING")
    }

    /** 重置同步状态、清除 ACK 缓存并重新上传。 */
    fun resetAndSyncBillAsync(context: Context, billId: Long) {
        TcpSyncService.startService(context)
        TcpSyncService.syncBillNow(context, billId, "PACKAGING", forceResetSync = true)
    }

    suspend fun resetAndSyncBill(context: Context, billId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!resetSyncStatus(context, billId)) {
            return@withContext false
        }
        LengKuBaoApplication.getSyncManager().clearConfirmedItemsForPackagingBill(billId)
        syncBillToServer(context, billId).success
    }

    suspend fun resetSyncStatus(context: Context, billId: Long): Boolean {
        return SyncStatusUtils.resetPackagingBillSyncStatus(context, billId)
    }

    suspend fun syncBillToServer(context: Context, billId: Long): PackagingSyncResult {
        val database = AppDatabase.getInstance(context)
        val bill = database.packagingBillDao().getBillById(billId)
        if (bill == null) {
            Log.w(TAG, "syncBillToServer: 单据不存在 id=$billId")
            return PackagingSyncResult(success = false, errorMessage = "单据不存在")
        }
        val syncManager = LengKuBaoApplication.getSyncManager()
        if (syncManager.connectionState.value != TcpSyncManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "syncBillToServer: TCP 未连接，保留待同步 bill=${bill.billNo}")
            database.packagingBillDao().updateSyncStatus(billId, false)
            return PackagingSyncResult(success = false, errorMessage = "TCP未连接，请确认电脑端同步服务已启动")
        }
        Log.i(TAG, "syncBillToServer: bill=${bill.billNo} flag=${bill.packagingTypeFlag}")
        return syncManager.syncPackagingBill(billId)
    }
}
