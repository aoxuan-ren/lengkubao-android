package com.pingwei.lengkubao.utils

import android.content.Context
import com.pingwei.lengkubao.fiscal.FiscalYearManager
import java.util.Calendar

object SourceRecordIdUtils {
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = "HANDHELD_${System.currentTimeMillis()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun build(type: String, localKey: String, context: Context): String {
        val safeDeviceId = getDeviceId(context).replace("|", "_")
        val safeLocalKey = localKey.replace("|", "_")
        return "SRC_${safeDeviceId}_${type}_$safeLocalKey"
    }

    fun buildPresaleBill(billId: Long, context: Context): String {
        val year = activeFiscalYear()
        return build("PRESALE", "${year}_$billId", context)
    }

    fun buildPresalePayment(paymentId: Long, context: Context): String {
        val year = activeFiscalYear()
        return build("PRESALE_PAYMENT", "${year}_$paymentId", context)
    }

    fun buildPresaleOutbound(outboundId: Long, context: Context): String {
        val year = activeFiscalYear()
        return build("PRESALE_OUTBOUND", "${year}_$outboundId", context)
    }

    private fun activeFiscalYear(): Int {
        return if (FiscalYearManager.isInitialized) {
            FiscalYearManager.activeYear
        } else {
            Calendar.getInstance().get(Calendar.YEAR)
        }
    }
}
