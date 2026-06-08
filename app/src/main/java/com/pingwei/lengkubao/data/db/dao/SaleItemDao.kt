package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pingwei.lengkubao.data.db.entity.SaleItem
import kotlinx.coroutines.flow.Flow

// 必须定义在 Dao 接口同文件，确保 Room 能识别字段映射
data class CustomerSaleSummary(
    val product_id: Long,
    val product_name: String,
    val total_quantity: Int,
    val total_amount: Double
)

@Dao
interface SaleItemDao {
    // 插入单条
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SaleItem): Long

    // 批量插入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SaleItem>)

    // 更新
    @Update
    suspend fun update(item: SaleItem)

    // 删除单条
    @Delete
    suspend fun delete(item: SaleItem)

    // 根据账单ID删除所有明细
    @Query("DELETE FROM sale_item WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)

    // 根据账单ID查询明细（suspend + List，兼容 Room）
    @Query("SELECT * FROM sale_item WHERE bill_id = :billId")
    suspend fun getItemsByBillId(billId: Long): List<SaleItem>

    // 根据账单ID查询明细（Flow 类型，实时监听）
    @Query("SELECT * FROM sale_item WHERE bill_id = :billId")
    fun getItemsByBillIdFlow(billId: Long): Flow<List<SaleItem>>

    // 根据商品ID查询总销售数量
    @Query("""
        SELECT SUM(quantity) 
        FROM sale_item 
        WHERE product_id = :productId 
        AND bill_id IN (SELECT id FROM sale_bill WHERE status = 'COMPLETED')
    """)
    suspend fun getTotalSaleQuantityByProduct(productId: Long): Int?

    // 修复：ORDER BY itemId（替换原来的 id）
    @Query("SELECT * FROM sale_item WHERE bill_id = :billId ORDER BY itemId")
    suspend fun getByBillId(billId: Long): List<SaleItem>

    // 根据商品ID+库位ID查询销售数量
    @Query("""
        SELECT SUM(quantity) 
        FROM sale_item 
        WHERE product_id = :productId 
        AND bill_id IN (
            SELECT id FROM sale_bill 
            WHERE location_id = :locationId 
            AND status = 'COMPLETED'
        )
    """)
    suspend fun getSaleQuantityByProductAndLocation(productId: Long, locationId: Long): Int?

    // 根据账单ID查询总金额
    @Query("SELECT SUM(amount) FROM sale_item WHERE bill_id = :billId")
    suspend fun getTotalAmountByBillId(billId: Long): Double?

    // 修复：ORDER BY itemId DESC（替换原来的 id）
    @Query("""
        SELECT * FROM sale_item 
        WHERE bill_id IN (SELECT id FROM sale_bill ORDER BY create_time DESC LIMIT :limit)
        ORDER BY itemId DESC
    """)
    suspend fun getRecentSaleItems(limit: Int): List<SaleItem>

    // 客户销售汇总（关联 sale_bill，确保 sale_bill 有 customer_id 字段）
    @Query("""
        SELECT product_id, product_name, SUM(quantity) as total_quantity, SUM(amount) as total_amount
        FROM sale_item 
        WHERE bill_id IN (
            SELECT id FROM sale_bill 
            WHERE customer_id = :customerId 
            AND status = 'COMPLETED'
        )
        GROUP BY product_id
    """)
    suspend fun getCustomerSaleSummary(customerId: Long): List<CustomerSaleSummary>
}