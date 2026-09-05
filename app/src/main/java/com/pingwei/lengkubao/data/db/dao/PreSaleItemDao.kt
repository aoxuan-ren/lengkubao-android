package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PreSaleItem

@Dao
interface PreSaleItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PreSaleItem>)

    @Query("SELECT * FROM presale_item WHERE bill_id = :billId")
    suspend fun getItemsByBillId(billId: Long): List<PreSaleItem>

    @Query("DELETE FROM presale_item WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)

    @Query("DELETE FROM presale_item WHERE itemId = :itemId")
    suspend fun deleteById(itemId: Long)

    @Query("UPDATE presale_item SET shipped_quantity = :shippedQuantity WHERE itemId = :itemId")
    suspend fun updateShippedQuantity(itemId: Long, shippedQuantity: Int)
}
