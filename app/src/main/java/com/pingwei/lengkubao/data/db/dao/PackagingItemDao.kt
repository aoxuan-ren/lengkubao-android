// data/db/dao/PackagingItemDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PackagingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PackagingItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PackagingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PackagingItem>)

    @Update
    suspend fun update(item: PackagingItem)

    @Delete
    suspend fun delete(item: PackagingItem)

    @Query("SELECT * FROM packaging_item WHERE bill_id = :billId")
    suspend fun getItemsByBillId(billId: Long): List<PackagingItem>

    @Query("SELECT * FROM packaging_item WHERE packaging_type_id = :typeId")
    suspend fun getItemsByTypeId(typeId: Long): List<PackagingItem>

    @Query("DELETE FROM packaging_item WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)

    // 用于统计查询的汇总数据类
    data class PackagingSummary(
        @ColumnInfo(name = "packaging_type_id")
        val packagingTypeId: Long,

        @ColumnInfo(name = "packaging_type_name")
        val packagingTypeName: String,

        @ColumnInfo(name = "total_quantity")
        val totalQuantity: Int,

        @ColumnInfo(name = "total_amount")
        val totalAmount: Double
    )

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("""
        SELECT 
            packaging_type_id,
            packaging_type AS packaging_type_name,  -- 👈 关键：用别名匹配 PackagingSummary
            SUM(quantity) AS total_quantity,
            SUM(amount) AS total_amount              -- 👈 关键：用 amount，不是 subtotal
        FROM packaging_item 
        WHERE bill_id IN (
            SELECT id FROM packaging_bill 
            WHERE customer_no = :customerNo 
              AND create_time BETWEEN :startTime AND :endTime
        )
        GROUP BY packaging_type_id, packaging_type   -- 👈 补全 GROUP BY
    """)
    suspend fun getPackagingSummaryByCustomer(
        customerNo: String,
        startTime: Long,
        endTime: Long
    ): List<PackagingSummary>
}