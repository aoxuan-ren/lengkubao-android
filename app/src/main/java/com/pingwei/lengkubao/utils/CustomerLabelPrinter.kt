// utils/CustomerLabelPrinter.kt
package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.service.SunmiPrintService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 客户标签打印工具（高层封装）
 */
object CustomerLabelPrinter {

    /**
     * 增强版打印单个客户二维码标签
     */
    suspend fun printCustomerLabel(
        context: Context,
        customer: Customer,
        printQrCodeImage: Boolean = true
    ): PrintResult {
        return withContext(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)

                // 1. 检查打印机连接
                val connected = printService.ensureConnectedForPrint()
                if (!connected) {
                    Log.e("CustomerLabelPrinter", "打印机未连接")
                    return@withContext PrintResult(
                        success = false,
                        message = "打印机未连接，请检查打印机电源和连接",
                        customerNo = customer.customerNo,
                        errorType = "NOT_CONNECTED"
                    )
                }

                // 2. 检查打印机状态
                val status = printService.checkPrinterStatus()
                Log.d("CustomerLabelPrinter", "打印机状态: $status")

                if (status.contains("缺纸")) {
                    return@withContext PrintResult(
                        success = false,
                        message = "打印机缺纸，请添加纸张",
                        customerNo = customer.customerNo,
                        errorType = "NO_PAPER"
                    )
                }

                if (status.contains("过热")) {
                    return@withContext PrintResult(
                        success = false,
                        message = "打印头过热，请等待冷却",
                        customerNo = customer.customerNo,
                        errorType = "OVERHEAT"
                    )
                }

                // 3. 检查二维码图片是否存在
                val qrCodePath = if (printQrCodeImage && !customer.qrCodePath.isNullOrBlank()) {
                    val file = File(customer.qrCodePath!!)
                    if (file.exists()) {
                        customer.qrCodePath
                    } else {
                        null
                    }
                } else {
                    null
                }

                // 4. 打印标签
                // 修改为：
                val success = printService.printSimpleCustomerLabel(
                    customerName = customer.customerName,
                    customerNo = customer.customerNo,
                    phone = customer.phone
                )

                return@withContext PrintResult(
                    success = success,
                    message = if (success) "标签打印成功" else "打印失败，请重试",
                    customerNo = customer.customerNo,
                    errorType = if (success) null else "PRINT_FAILED"
                )

            } catch (e: Exception) {
                Log.e("CustomerLabelPrinter", "打印失败", e)
                return@withContext PrintResult(
                    success = false,
                    message = "打印失败: ${e.message ?: "未知错误"}",
                    customerNo = customer.customerNo,
                    errorType = "EXCEPTION"
                )
            }
        }
    }

    // 扩展 PrintResult 类
    data class PrintResult(
        val success: Boolean,
        val message: String,
        val customerNo: String,
        val errorType: String? = null // 新增：错误类型标识
    )

    /**
     * 检查打印机状态
     */
    suspend fun checkPrinterStatus(context: Context): PrinterStatus {
        return withContext(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)

                // 检查连接
                val connected = printService.ensureConnectedForPrint()
                if (!connected) {
                    return@withContext PrinterStatus.NOT_CONNECTED
                }

                // 获取状态信息
                val statusText = printService.checkPrinterStatus()

                return@withContext when {
                    statusText.contains("正常") -> PrinterStatus.READY
                    statusText.contains("缺纸") -> PrinterStatus.NO_PAPER
                    statusText.contains("过热") -> PrinterStatus.OVERHEAT
                    statusText.contains("开盖") -> PrinterStatus.COVER_OPEN
                    else -> PrinterStatus.UNKNOWN
                }

            } catch (e: Exception) {
                return@withContext PrinterStatus.NOT_CONNECTED
            }
        }
    }

    /**
     * 调试方法：打印测试页
     */
    suspend fun printTestPage(context: Context): PrintResult {
        return withContext(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)

                // 尝试打印测试页
                val success = printService.testPrint()

                return@withContext PrintResult(
                    success = success,
                    message = if (success) "测试页打印成功" else "测试页打印失败",
                    customerNo = "TEST"
                )
            } catch (e: Exception) {
                return@withContext PrintResult(
                    success = false,
                    message = "测试打印失败: ${e.message}",
                    customerNo = "TEST"
                )
            }
        }
    }

    /**
     * 获取详细的打印机信息
     */
    suspend fun getPrinterInfo(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                val printService = SunmiPrintService.getInstance(context)
                val connected = printService.ensureConnectedForPrint()

                if (!connected) {
                    return@withContext "打印机未连接"
                }

                val status = printService.checkPrinterStatus()
                val info = printService.getPrinterInfo()

                "状态: $status\n$info"
            } catch (e: Exception) {
                "获取打印机信息失败: ${e.message}"
            }
        }
    }



    enum class PrinterStatus {
        READY,          // 打印机就绪
        NO_PAPER,       // 缺纸
        OVERHEAT,       // 过热
        COVER_OPEN,     // 舱门打开
        NOT_CONNECTED,  // 未连接
        UNKNOWN         // 未知状态
    }
}