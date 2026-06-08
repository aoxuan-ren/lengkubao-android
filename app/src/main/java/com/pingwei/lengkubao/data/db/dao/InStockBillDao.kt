// data/db/dao/InStockBillDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.pingwei.lengkubao.data.db.entity.InStockBill
import com.pingwei.lengkubao.data.db.entity.InStockItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InStockBillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: InStockBill): Long

    @Update
    suspend fun update(bill: InStockBill)

    @Delete
    suspend fun delete(bill: InStockBill)

    @Query("UPDATE in_stock_bill SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("SELECT * FROM in_stock_bill ORDER BY create_time DESC")
    fun getAllBills(): Flow<List<InStockBill>>

    @Query("SELECT * FROM in_stock_bill WHERE id = :id")
    suspend fun getBillById(id: Long): InStockBill?

    @Query("SELECT * FROM in_stock_bill WHERE bill_no = :billNo")
    suspend fun getBillByNo(billNo: String): InStockBill?

    @Query("SELECT * FROM in_stock_bill WHERE customer_no = :customerNo ORDER BY create_time DESC")
    fun getBillsByCustomer(customerNo: String): Flow<List<InStockBill>>

    @Query("SELECT COUNT(*) FROM in_stock_bill WHERE DATE(create_time/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')")
    suspend fun getTodayBillCount(date: Long): Int

    @Query("UPDATE in_stock_bill SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE in_stock_bill SET sync_status = 0 WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM in_stock_bill WHERE id = :id")
    suspend fun getById(id: Long): InStockBill?

    @Query("SELECT * FROM in_stock_item WHERE bill_id = :billId ORDER BY id ASC")
    suspend fun getItemsByBillId(billId: Long): List<InStockItem>

    // ==================== 入库统计相关方法（使用 @RawQuery） ====================

    /**
     * 按库位和商品分组统计入库数量
     */
    @RawQuery
    suspend fun getInboundStatisticsByLocationRaw(query: SupportSQLiteQuery): List<InboundStatRow>

    /**
     * 获取入库总计统计
     */
    @RawQuery
    suspend fun getInboundTotalStatsRaw(query: SupportSQLiteQuery): InboundTotalRow?

    /**
     * 按日期分组统计入库数量
     */
    @RawQuery
    suspend fun getInboundDailyStatsRaw(query: SupportSQLiteQuery): List<DailyInboundRow>

    /**
     * 按客户统计入库数据
     */
    @RawQuery
    suspend fun getInboundStatsByCustomerRaw(query: SupportSQLiteQuery): List<CustomerInboundRow>

    /**
     * 获取指定库位的入库统计
     */
    @RawQuery
    suspend fun getInboundStatsByLocationRaw(query: SupportSQLiteQuery): List<LocationInboundRow>

    /**
     * 获取指定商品的入库统计
     */
    @RawQuery
    suspend fun getTopInboundProductsRaw(query: SupportSQLiteQuery): List<ProductInboundRow>

    /**
     * 获取客户月度入库统计
     */
    @RawQuery
    suspend fun getCustomerMonthlyInboundStatsRaw(query: SupportSQLiteQuery): List<MonthlyInboundRow>
}

// ==================== 定义返回的数据类 ====================

/**
 * 入库统计行数据
 */
data class InboundStatRow(
    val locationId: Long,
    val locationNo: String,
    val locationName: String,
    val productId: Long,
    val productNo: String,
    val productName: String,
    val quantity: Int,
    val orderCount: Int,
    val totalAmount: Double
)

/**
 * 入库总计数据
 */
data class InboundTotalRow(
    val totalQuantity: Int,
    val totalAmount: Double,
    val productCount: Int,
    val orderCount: Int
)

/**
 * 每日入库统计
 */
data class DailyInboundRow(
    val date: String,
    val dailyQuantity: Int,
    val dailyAmount: Double,
    val dailyOrderCount: Int
)

/**
 * 客户入库统计
 */
data class CustomerInboundRow(
    val customerNo: String,
    val customerName: String,
    val totalQuantity: Int,
    val totalAmount: Double,
    val orderCount: Int,
    val productCount: Int
)

/**
 * 库位入库统计
 */
data class LocationInboundRow(
    val locationName: String,
    val totalQuantity: Int,
    val billCount: Int
)

/**
 * 商品入库统计
 */
data class ProductInboundRow(
    val productName: String,
    val totalQuantity: Int,
    val billCount: Int
)

/**
 * 月度入库统计
 */
data class MonthlyInboundRow(
    val month: String,
    val monthlyQuantity: Int,
    val monthlyAmount: Double,
    val monthlyOrderCount: Int
)