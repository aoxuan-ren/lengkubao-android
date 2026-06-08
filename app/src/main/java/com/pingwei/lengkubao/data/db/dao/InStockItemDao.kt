// data/db/dao/InStockItemDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.InStockItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InStockItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InStockItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InStockItem>)

    @Update
    suspend fun update(item: InStockItem)

    @Delete
    suspend fun delete(item: InStockItem)

    @Query("DELETE FROM in_stock_item WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)

    @Query("SELECT * FROM in_stock_item WHERE bill_id = :billId")
    suspend fun getItemsByBillId(billId: Long): List<InStockItem>

    @Query("SELECT * FROM in_stock_item WHERE bill_id = :billId")
    fun getItemsByBillIdFlow(billId: Long): Flow<List<InStockItem>>

    @Query("SELECT SUM(quantity) FROM in_stock_item WHERE product_id = :productId AND bill_id IN (SELECT id FROM in_stock_bill WHERE status = 1)")
    suspend fun getTotalQuantityByProduct(productId: Long): Int?
}