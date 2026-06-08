package com.pingwei.lengkubao.service.model

/**
 * 包装单打印项数据类
 */
data class PackagingItemPrint(
    val packagingTypeFlag: String = "TAKE",
    val packagingType: String,
    val quantity: Int,
    val unitPrice: Double,
    val amount: Double
)