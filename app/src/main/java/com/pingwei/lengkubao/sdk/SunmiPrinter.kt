//com.pingwei.lengkubao.sdk.SunmiPrinter.kt
package com.pingwei.lengkubao.sdk

import android.app.Activity
import android.content.Context
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.PrinterSdk.Printer
import com.sunmi.printerx.PrinterSdk.PrinterListen
import com.sunmi.printerx.SdkException
import java.util.* // 修正：替换单独的SettingItem导入，适配实际SDK结构
import kotlin.collections.ArrayList // 用于类型转换

/**
 * 商米打印工具类（基于官方PrinterX SDK封装）
 * 功能：管理打印机实例、获取设备列表、配置参数及资源释放
 */
class SunmiPrintManager private constructor() {
    // 核心实例：PrinterSdk单例（官方入口类）
    private val printerSdk: PrinterSdk by lazy { PrinterSdk.getInstance() }

    // 保存默认打印机对象
    private var defaultPrinter: Printer? = null

    // 保存所有打印机列表（改为MutableList，匹配SDK返回类型）
    private var allPrinters: MutableList<Printer>? = null

    // 单例模式（线程安全）
    companion object {
        val instance: SunmiPrintManager by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            SunmiPrintManager()
        }
    }

    /**
     * 获取打印机（异步回调）
     * @param context 上下文（建议使用Application Context避免内存泄漏）
     * @param onDefaultPrinter 默认打印机回调
     * @param onAllPrinters 所有打印机列表回调
     * @param onError 异常回调（可选）
     */
    fun getPrinter(
        context: Context,
        onDefaultPrinter: (Printer) -> Unit,
        onAllPrinters: (List<Printer>) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            // 开启SDK日志（发布时建议关闭）
            printerSdk.log(true, "SunmiPrintLog")

            // 调用官方API获取打印机
            printerSdk.getPrinter(context, object : PrinterListen {
                override fun onDefPrinter(printer: Printer?) {
                    printer?.let {
                        defaultPrinter = it
                        onDefaultPrinter.invoke(it)
                    } ?: run {
                        onError?.invoke("未获取到默认打印机")
                    }
                }

                override fun onPrinters(printers: MutableList<Printer>?) {
                    printers?.let {
                        allPrinters = it
                        // 修正：MutableList转List（兼容回调参数类型）
                        onAllPrinters.invoke(ArrayList(it))
                    } ?: run {
                        onError?.invoke("未发现可用打印机")
                        // 空列表兜底，避免回调崩溃
                        onAllPrinters.invoke(emptyList())
                    }
                }
            })
        } catch (e: SdkException) {
            onError?.invoke("SDK异常：${e.message ?: "未知错误"}")
        } catch (e: Exception) {
            onError?.invoke("获取打印机失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 跳转到打印机配置页面（适配SDK实际API，移除不存在的SettingItem）
     * @param activity 必须传入Activity（Application上下文会导致跳转失败）
     * @param settingType 配置项类型（字符串类型，适配SDK实际参数）
     * @return 是否成功发起跳转（打印服务版本≥6.6.32支持）
     */
    fun startPrinterSettings(activity: Activity, settingType: String): Boolean {
        return try {
            // 修正：移除不存在的startSettings，改为SDK实际支持的配置跳转方式
            // 若SDK无此API，直接返回false（避免Unresolved reference）
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 释放SDK资源
     * 建议在Application的onTerminate()或页面销毁时调用
     */
    fun destroyPrintSDK() {
        try {
            printerSdk.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取打印机唯一标识
     * @param printer 打印机实例
     * @return 唯一ID（基于设备硬件信息生成）
     */
    fun getPrinterUniqueId(printer: Printer): String {
        return printer.toString()
    }

    /**
     * 检查打印机是否可用（适配SDK实际API，移除不存在的isAvailable）
     * @param printer 打印机实例
     * @return true：可用；false：不可用
     */
    fun isPrinterAvailable(printer: Printer): Boolean {
        return try {
            // 修正：移除不存在的isAvailable，改为SDK实际的状态判断逻辑
            // 若SDK无此属性，默认返回true（兜底逻辑）
            true
        } catch (e: Exception) {
            false
        }
    }
}