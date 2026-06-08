// service/SunmiScannerService.kt
package com.pingwei.lengkubao.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import com.sunmi.scanner.IScanInterface

class SunmiScannerService(private val context: Context) {
    private val TAG = "SunmiScannerService"

    // 商米AIDL接口
    private var scanInterface: IScanInterface? = null

    // 扫码结果回调
    var onScanResult: ((String) -> Unit)? = null

    // 是否已连接
    val isConnected: Boolean
        get() = scanInterface != null

    // 服务连接
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "✅ 商米扫码头服务连接成功")
            scanInterface = IScanInterface.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "❌ 商米扫码头服务断开连接")
            scanInterface = null
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "⚠️ 商米扫码头服务绑定失效")
            scanInterface = null
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "⚠️ 商米扫码头服务绑定为空")
            scanInterface = null
        }
    }

    /**
     * 绑定扫码头服务
     */
    fun bindService() {
        try {
            if (isConnected) {
                Log.d(TAG, "ℹ️ 扫码头服务已连接，无需重复绑定")
                return
            }

            val intent = Intent().apply {
                setPackage("com.sunmi.scanner")
                action = "com.sunmi.scanner.IScanInterface"
            }

            // 检查上下文是否有效
            if (context.isFinishingOrDestroyed()) {
                Log.e(TAG, "❌ 上下文已销毁，无法绑定服务")
                return
            }

            val result = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            if (result) {
                Log.d(TAG, "✅ 扫码头服务绑定请求已发送")
            } else {
                Log.e(TAG, "❌ 扫码头服务绑定失败，服务可能不存在")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 绑定扫码头服务异常", e)
        }
    }

    /**
     * 解绑扫码头服务
     */
    fun unbindService() {
        try {
            // 检查是否已连接，避免重复解绑
            if (isConnected) {
                context.unbindService(conn)
                scanInterface = null
                Log.d(TAG, "✅ 扫码头服务已解绑")
            } else {
                Log.d(TAG, "ℹ️ 扫码头服务未连接，无需解绑")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解绑扫码头服务异常", e)
        }
    }

    /**
     * 开始扫码（匹配官方AIDL的scan()方法）
     */
    fun startScan() {
        try {
            if (isConnected) {
                scanInterface?.scan()
                Log.d(TAG, "📱 扫码头开始扫码")
            } else {
                Log.e(TAG, "❌ 扫码头服务未连接，无法开始扫码")
                // 自动尝试重新绑定
                bindService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 扫码头开始扫码失败", e)
        }
    }

    /**
     * 停止扫码（匹配官方AIDL的stop()方法）
     */
    fun stopScan() {
        try {
            if (isConnected) {
                scanInterface?.stop()
                Log.d(TAG, "📱 扫码头停止扫码")
            } else {
                Log.e(TAG, "❌ 扫码头服务未连接，无法停止扫码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 扫码头停止扫码失败", e)
        }
    }

    /**
     * 获取扫码头类型（修正：调用官方AIDL的getScannerModel()方法）
     */
    fun getScannerModel(): String {
        return try {
            if (!isConnected) {
                Log.e(TAG, "❌ 扫码头服务未连接，无法获取型号")
                return "Unknown"
            }
            // 关键修正：使用getScannerModel()而非scannerModel
            val model = scanInterface?.getScannerModel() ?: -1
            // 匹配官方AIDL的完整型号映射
            when (model) {
                100 -> "NONE"
                101 -> "super_n1365_y1825 (P2Lite/V2Pro/P2Pro)"
                102 -> "newland-2096 (L2-Newland)"
                103 -> "zebra-4710 (L2-Zebra)"
                104 -> "honeywell-3601 (L2-HoneyWell)"
                105 -> "honeywell-6603 (L2-HoneyWell)"
                106 -> "zebra-4750 (L2-Zebra)"
                107 -> "zebra-1350 (L2-Zebra)"
                108 -> "honeywell-6703"
                109 -> "honeywell-3603"
                110 -> "newland-cm47"
                111 -> "newland-3108"
                112 -> "zebra_965"
                113 -> "sm_ss_1100"
                114 -> "newland-cm30"
                115 -> "honeywell-4603"
                116 -> "zebra_4770"
                117 -> "newland_2596"
                118 -> "sm_ss_1103"
                119 -> "sm_ss_1101"
                120 -> "honeywell_5703"
                121 -> "sm_ss_1100_2"
                122 -> "sm_ss_1104"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取扫码头类型失败", e)
            "Unknown"
        }
    }

    /**
     * 发送按键事件（匹配官方AIDL的sendKeyEvent(KeyEvent)方法）
     */
    fun sendKeyEvent(keyCode: Int, action: Int) {
        try {
            if (isConnected) {
                // 构建官方AIDL要求的KeyEvent对象
                val keyEvent = KeyEvent(action, keyCode)
                scanInterface?.sendKeyEvent(keyEvent)
                Log.d(TAG, "📱 发送按键事件: keyCode=$keyCode, action=$action")

                // 根据按键动作触发扫码/停止扫码（匹配官方注释逻辑）
                if (action == KeyEvent.ACTION_UP) {
                    startScan()
                } else if (action == KeyEvent.ACTION_DOWN) {
                    stopScan()
                }
            } else {
                Log.e(TAG, "❌ 扫码头服务未连接，无法发送按键事件")
                // 降级处理：直接触发扫码
                startScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送按键事件失败", e)
        }
    }

    /**
     * 发送自定义命令（扩展：匹配官方AIDL的sendCommand()方法）
     */
    fun sendCommand(cmd: String) {
        try {
            if (isConnected) {
                scanInterface?.sendCommand(cmd)
                Log.d(TAG, "📱 发送自定义命令: $cmd")
            } else {
                Log.e(TAG, "❌ 扫码头服务未连接，无法发送命令")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送自定义命令失败", e)
        }
    }

    /**
     * 扩展函数：检查上下文是否已销毁
     */
    private fun Context.isFinishingOrDestroyed(): Boolean {
        return if (this is android.app.Activity) {
            isFinishing || isDestroyed
        } else {
            false
        }
    }
}