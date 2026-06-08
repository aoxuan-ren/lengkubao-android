package com.pingwei.lengkubao.service.model

/**
 * 销售单打印项数据类
 */
data class SaleItemPrint(
    val productName: String,
    val quantity: Int,
    val unit: String = "件",
    val unitPrice: Double,
    val amount: Double
)