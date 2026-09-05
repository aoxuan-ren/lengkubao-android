package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.OutboundRecord

@Dao
interface OutboundRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: OutboundRecord): Long

    @Query("SELECT * FROM outbound_record WHERE bill_id = :billId ORDER BY ship_time DESC")
    suspend fun getByBillId(billId: Long): List<OutboundRecord>

    @Query("SELECT * FROM outbound_record WHERE id = :id")
    suspend fun getById(id: Long): OutboundRecord?

    @Query("SELECT * FROM outbound_record WHERE source_record_id = :sourceRecordId LIMIT 1")
    suspend fun getBySourceRecordId(sourceRecordId: String): OutboundRecord?

    @Query("UPDATE outbound_record SET source_record_id = :sourceRecordId, source_device_id = :sourceDeviceId WHERE id = :id")
    suspend fun updateSourceIdentity(id: Long, sourceRecordId: String, sourceDeviceId: String)

    @Query("SELECT * FROM outbound_record WHERE sync_status = 0 ORDER BY ship_time DESC")
    suspend fun getUnsyncedRecords(): List<OutboundRecord>

    @Query("UPDATE outbound_record SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)
}
