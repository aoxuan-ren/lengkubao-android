package com.pingwei.lengkubao.data.db.entity

data class StockWithProduct(
    val stockId: Long,
    val productId: Long,
    val productNo: String,
    val productName: String,
    val locationId: Long,
    val locationName: String,
    val currentQuantity: Int,
    val reservedQuantity: Int,
    val lastUpdated: Long,
    val lastBillNo: String,
) {
    val availableQuantity: Int
        get() = currentQuantity - reservedQuantity

    val stockStatus: String
        get() = when {
            availableQuantity <= 0 -> "无库存"
            availableQuantity <= 10 -> "库存紧张"
            else -> "库存充足"
        }
}
