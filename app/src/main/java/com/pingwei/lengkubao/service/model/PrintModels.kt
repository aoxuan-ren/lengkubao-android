//com.pingwei.lengkubao.service.model.PrintModels.kt
package com.pingwei.lengkubao.service.model

import java.util.Date

/**
 * 打印相关的数据模型
 */

/**
 * 打印配置数据类
 */
data class PrintConfig(
    val companyName: String,
    val companyAddress: String,
    val companyPhone: String,
    val autoCut: Boolean = true,
    val paperWidth: Int = 58,
    val fontSize: Int = 1
)

/**
 * 打印结果
 */
data class PrintResult(
    val success: Boolean,
    val message: String? = null,
    val timestamp: Date = Date(),
    val billNo: String? = null,
    val billType: BillType? = null
)

/**
 * 单据类型
 */
enum class BillType {
    SALE, IN_STOCK, PACKAGING, PRESALE
}

/**
 * 打印机状态
 */
data class PrinterStatus(
    val isConnected: Boolean = false,
    val statusCode: Int = -1,
    val statusMessage: String = "未连接",
    val printerModel: String? = null,
    val serialNo: String? = null,
    val firmwareVersion: String? = null,
    val lastChecked: Date = Date()
)