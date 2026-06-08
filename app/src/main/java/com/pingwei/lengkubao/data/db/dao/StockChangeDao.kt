// data/db/dao/StockChangeDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.BillType
import com.pingwei.lengkubao.data.db.entity.ChangeType
import com.pingwei.lengkubao.data.db.entity.StockChange
import kotlinx.coroutines.flow.Flow

@Dao
interface StockChangeDao {

    @Insert
    suspend fun insert(change: StockChange): Long

    @Query("SELECT * FROM stock_change WHERE product_id = :productId ORDER BY timestamp DESC")
    fun getChangesByProduct(productId: Long): Flow<List<StockChange>>

    @Query("SELECT * FROM stock_change WHERE location_id = :locationId ORDER BY timestamp DESC")
    fun getChangesByLocation(locationId: Long): Flow<List<StockChange>>

    @Query("SELECT * FROM stock_change WHERE related_bill_id = :billId AND related_bill_type = :billType")
    suspend fun getChangesByBill(billId: Long, billType: BillType): List<StockChange>

    @Query("SELECT * FROM stock_change ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChanges(limit: Int = 100): List<StockChange>
}