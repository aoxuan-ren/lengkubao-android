package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.sync.TcpSyncManager

object PreSaleSyncHelper {
    private const val TAG = "PreSaleSyncHelper"

    suspend fun ensureBillSourceIdentity(context: Context, billId: Long) {
        val database = AppDatabase.getInstance(context)
        val bill = database.preSaleBillDao().getBillById(billId) ?: return
        if (!bill.sourceRecordId.isNullOrBlank()) return
        val deviceId = SourceRecordIdUtils.getDeviceId(context)
        val sourceRecordId = SourceRecordIdUtils.buildPresaleBill(billId, context)
        database.preSaleBillDao().updateSourceIdentity(billId, sourceRecordId, deviceId)
    }

    suspend fun ensurePaymentSourceIdentity(context: Context, paymentId: Long) {
        val database = AppDatabase.getInstance(context)
        val payment = database.paymentRecordDao().getById(paymentId) ?: return
        if (!payment.sourceRecordId.isNullOrBlank()) return
        val deviceId = SourceRecordIdUtils.getDeviceId(context)
        val sourceRecordId = SourceRecordIdUtils.buildPresalePayment(paymentId, context)
        database.paymentRecordDao().updateSourceIdentity(paymentId, sourceRecordId, deviceId)
    }

    suspend fun ensureOutboundSourceIdentity(context: Context, outboundId: Long) {
        val database = AppDatabase.getInstance(context)
        val record = database.outboundRecordDao().getById(outboundId) ?: return
        if (!record.sourceRecordId.isNullOrBlank()) return
        val deviceId = SourceRecordIdUtils.getDeviceId(context)
        val sourceRecordId = SourceRecordIdUtils.buildPresaleOutbound(outboundId, context)
        database.outboundRecordDao().updateSourceIdentity(outboundId, sourceRecordId, deviceId)
    }

    /** 发货/作废后立即上传，不依赖 auto_sync 开关。 */
    suspend fun syncBillToServer(context: Context, billId: Long): Boolean {
        val database = AppDatabase.getInstance(context)
        ensureBillSourceIdentity(context, billId)
        val bill = database.preSaleBillDao().getBillById(billId)
        if (bill == null) {
            Log.w(TAG, "syncBillToServer: 单据不存在 id=$billId")
            return false
        }
        val syncManager = LengKuBaoApplication.getSyncManager()
        if (syncManager.connectionState.value != TcpSyncManager.ConnectionState.CONNECTED) {
            Log.w(TAG, "syncBillToServer: TCP 未连接，保留待同步 bill=${bill.billNo} mode=${bill.saleMode}")
            database.preSaleBillDao().updateSyncStatus(billId, 0)
            return false
        }
        Log.i(TAG, "syncBillToServer: bill=${bill.billNo} mode=${bill.saleMode} status=${bill.status}")
        return syncManager.syncPreSaleBill(billId)
    }
}
