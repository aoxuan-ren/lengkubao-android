package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.OutboundRecordItem

@Dao
interface OutboundRecordItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OutboundRecordItem>)

    @Query("SELECT * FROM outbound_record_item WHERE outbound_record_id = :recordId")
    suspend fun getByRecordId(recordId: Long): List<OutboundRecordItem>

    @Query("""
        SELECT oi.* FROM outbound_record_item oi
        INNER JOIN outbound_record o ON oi.outbound_record_id = o.id
        WHERE o.bill_id = :billId
    """)
    suspend fun getAllItemsByBillId(billId: Long): List<OutboundRecordItem>
}
