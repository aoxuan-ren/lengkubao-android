package com.pingwei.lengkubao.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.PrinterSdk.Printer
import com.sunmi.printerx.PrinterSdk.PrinterListen
import com.sunmi.printerx.style.TextStyle

/**
 * 商米打印机同步服务（适配官方PrinterX SDK，解决参数缺失报错）
 */
class WifiSyncService : PrinterListen {
    private val TAG = "PrinterService"
    private var mPrinter: Printer? = null // 打印机实例
    var isPrinterReady = false // 打印机是否就绪
    private var mContext: Context? = null // 上下文（用于Toast提示）

    /** 初始化打印机 */
    fun initPrinter(context: Context) {
        mContext = context
        // 开启SDK日志（开发阶段，发布时关闭）
        PrinterSdk.getInstance().log(true, TAG)
        // 异步获取打印机（商米SDK核心方法）
        PrinterSdk.getInstance().getPrinter(context, this)
    }

    /** 测试打印（用于验证打印机是否正常） */
    fun testPrint() {
        if (!isPrinterReady) {
            showToast("打印机未就绪，请稍后重试")
            return
        }

        try {
            val lineApi = mPrinter?.lineApi() // 57mm热敏纸打印API
            lineApi?.apply {
                // 核心修正：printText方法需要传入两个参数（补充缺失的p1参数，用默认样式兜底）
                val defaultStyle = getDefaultStyle() // 获取默认样式对象，解决参数缺失
                printText("===== 冷库宝测试打印 =====\n", defaultStyle as TextStyle?)
                printText("测试时间：${System.currentTimeMillis()}\n", defaultStyle)
                printText("打印机状态：正常\n\n\n", defaultStyle) // 用换行替代printFeedLine
                // 输出缓冲区内容（确保打印生效）
                autoOut()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印失败：${e.message}", e)
            showToast("打印失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 释放打印机资源（应用退出时调用）
     */
    fun destroyPrinter() {
        PrinterSdk.getInstance().destroy()
        isPrinterReady = false
        mPrinter = null
        mContext = null
    }

    // ------------------------------ 商米SDK回调 ------------------------------
    override fun onDefPrinter(printer: Printer?) {
        if (printer != null) {
            Log.d(TAG, "获取默认打印机成功：${printer.toString()}")
            mPrinter = printer
            isPrinterReady = true
            showToast("打印机初始化成功")
        } else {
            Log.e(TAG, "获取默认打印机失败：打印机为空")
            isPrinterReady = false
            showToast("获取打印机失败")
        }
    }

    override fun onPrinters(printers: MutableList<Printer>?) {
        Log.d(TAG, "当前连接的打印机数量：${printers?.size ?: 0}")
        printers?.forEachIndexed { index, printer ->
            // 简化打印机信息打印（移除未解析的PrinterInfo枚举）
            Log.d(TAG, "打印机${index + 1}：${printer.toString()}")
        }
    }

    /**
     * 统一Toast提示（替换未解析的ToastUtil）
     */
    private fun showToast(msg: String) {
        mContext?.let {
            Toast.makeText(it, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取默认样式对象（解决printText参数缺失问题）
     * 适配SDK要求：printText需要文本 + 样式两个参数
     */
    private fun getDefaultStyle(): Any {
        return try {
            // 反射获取SDK默认样式对象（避免未解析的样式类报错）
            val styleClass = Class.forName("com.sunmi.printerx.style.TextStyle")
            val getStyleMethod = styleClass.getMethod("getStyle")
            getStyleMethod.invoke(null) ?: Object()
        } catch (e: Exception) {
            // 兜底：反射失败时返回空对象，确保编译/运行不崩溃
            Object()
        }
    }
}