package com.pingwei.lengkubao.ui.presale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.pingwei.lengkubao.fiscal.FiscalYearEvents

/**
 * 年份切换后自动关闭页面，避免 ViewModel 持有旧年份数据库。
 */
fun ComponentActivity.registerFinishOnFiscalYearChanged(): BroadcastReceiver {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(
        receiver,
        IntentFilter(FiscalYearEvents.ACTION_FISCAL_YEAR_CHANGED),
    )
    return receiver
}

fun ComponentActivity.unregisterFinishOnFiscalYearChanged(receiver: BroadcastReceiver?) {
    receiver ?: return
    try {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    } catch (_: IllegalArgumentException) {
    }
}
