package com.pingwei.lengkubao.data.model

/**
 * 入库统计结果模型
 * 按库位和型号分组的入库统计数据
 */
data class InboundStatItem(
    val locationId: Long,           // 库位ID
    val locationNo: String,         // 库位编号
    val locationName: String,       // 库位名称
    val productId: Long,            // 商品ID
    val productNo: String,          // 商品编号
    val productName: String,        // 商品名称
    val quantity: Int,              // 入库数量
    val orderCount: Int,            // 入库单数
    val totalAmount: Double         // 入库总金额
)

/**
 * 入库总计统计
 */
data class InboundTotalStats(
    val totalQuantity: Int = 0,     // 总数量
    val totalAmount: Double = 0.0,  // 总金额
    val productCount: Int = 0,      // 商品种数
    val orderCount: Int = 0         // 总单数
)

/**
 * 客户信息简表（用于下拉选择）
 */
data class ClientInfo(
    val code: String,
    val name: String
) {
    val displayName: String get() = "$name ($code)"
}
/**
 * 每日入库统计
 */
data class DailyInboundStat(
    val date: String,
    val dailyQuantity: Int,
    val dailyAmount: Double,
    val dailyOrderCount: Int
)

/**
 * 客户入库统计
 */
data class CustomerInboundStat(
    val customerNo: String,
    val customerName: String,
    val totalQuantity: Int,
    val totalAmount: Double,
    val orderCount: Int,
    val productCount: Int
)