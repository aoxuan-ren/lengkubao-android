// utils/SunmiScannerReceiver.kt 修改
package com.pingwei.lengkubao.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

class SunmiScannerReceiver(
    private val context: Context,
    private val onScanResult: (String) -> Unit
) {
    private val TAG = "SunmiScannerReceiver"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "📱 接收到广播: action=${intent.action}")

            if (intent.action == "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED") {
                val code = intent.getStringExtra("data")
                val sourceByte = intent.getByteArrayExtra("source_byte")

                Log.d(TAG, "📱 扫码结果: code=$code, sourceByte=${sourceByte?.size} bytes")

                if (!code.isNullOrBlank()) {
                    // 去除可能的空白字符和换行符
                    val cleanCode = code.trim()
                    Log.d(TAG, "📱 清理后扫码结果: $cleanCode")
                    onScanResult(cleanCode)
                } else {
                    Log.w(TAG, "⚠️ 扫码结果为空")
                }
            } else {
                Log.d(TAG, "ℹ️ 收到其他广播: ${intent.action}")
            }
        }
    }

    /**
     * 注册广播接收器
     */
    fun register() {
        try {
            val filter = IntentFilter().apply {
                addAction("com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED")
                // 添加可能的其他相关广播
                addAction("android.intent.action.ACTION_POWER_CONNECTED")
                addAction("android.intent.action.ACTION_POWER_DISCONNECTED")
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "✅ 扫码头广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册扫码头广播接收器失败", e)
        }
    }

    /**
     * 注销广播接收器
     */
    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "✅ 扫码头广播接收器已注销")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注销扫码头广播接收器失败", e)
        }
    }
}