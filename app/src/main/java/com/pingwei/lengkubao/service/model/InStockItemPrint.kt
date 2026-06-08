package com.pingwei.lengkubao.service.model

/**
 * 入库单打印项数据类
 */
data class InStockItemPrint(
    val productName: String,
    val quantity: Double,
    val unit: String = "件"
)