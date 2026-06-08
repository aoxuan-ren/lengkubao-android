// data/db/entity/StockWithProduct.kt
package com.pingwei.lengkubao.data.db.entity

/**
 * 用于查询结果的映射类（不是实体类）
 * Room会自动根据查询结果映射字段
 */
data class StockWithProduct(
    val stockId: Long,
    val productId: Long,
    val productNo: String,
    val productName: String,
    val locationId: Long,
    val locationNo: String,
    val currentQuantity: Int,
    val reservedQuantity: Int,
    val lastUpdated: Long,
    val lastBillNo: String
) {
    // 计算属性
    val availableQuantity: Int
        get() = currentQuantity - reservedQuantity

    val stockStatus: String
        get() = when {
            availableQuantity <= 0 -> "无库存"
            availableQuantity <= 10 -> "库存紧张"
            else -> "库存充足"
        }
}