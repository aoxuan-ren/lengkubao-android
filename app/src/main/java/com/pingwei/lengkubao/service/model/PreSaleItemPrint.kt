package com.pingwei.lengkubao.service.model

/** 预售/出库销售单打印明细 */
data class PreSaleItemPrint(
    val productName: String,
    val quantity: Int,
    val unit: String = "箱",
    val salePrice: Double,
    val amount: Double,
)
