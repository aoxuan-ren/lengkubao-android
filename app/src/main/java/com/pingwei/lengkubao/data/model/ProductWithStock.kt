// data/model/ProductWithStock.kt
package com.pingwei.lengkubao.data.model

import com.pingwei.lengkubao.data.db.entity.Product

/**
 * 商品带库存信息模型（用于销售出库界面显示）
 */
data class ProductWithStock(
    val product: Product,
    val availableStock: Int,  // 可用库存
    val locationId: Long,
    val locationName: String? = null
) {
    val isStockAvailable: Boolean
        get() = availableStock > 0

    val stockWarningLevel: StockWarningLevel
        get() = when {
            availableStock <= 0 -> StockWarningLevel.OUT_OF_STOCK
            availableStock <= 10 -> StockWarningLevel.LOW_STOCK
            else -> StockWarningLevel.NORMAL
        }
}

enum class StockWarningLevel {
    NORMAL,      // 库存充足
    LOW_STOCK,   // 库存偏低
    OUT_OF_STOCK // 无库存
}