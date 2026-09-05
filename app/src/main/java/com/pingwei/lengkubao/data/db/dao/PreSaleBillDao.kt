package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PreSaleBill
import kotlinx.coroutines.flow.Flow

@Dao
interface PreSaleBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: PreSaleBill): Long

    @Update
    suspend fun update(bill: PreSaleBill)

    @Query("SELECT * FROM presale_bill WHERE id = :id")
    suspend fun getBillById(id: Long): PreSaleBill?

    @Query("SELECT * FROM presale_bill ORDER BY create_time DESC")
    fun getAllBills(): Flow<List<PreSaleBill>>

    @Query("UPDATE presale_bill SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE presale_bill SET paid_amount = :paidAmount WHERE id = :id")
    suspend fun updatePaidAmount(id: Long, paidAmount: Double)

    @Query("UPDATE presale_bill SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("""
        SELECT * FROM presale_bill 
        WHERE (:billNo IS NULL OR bill_no LIKE '%' || :billNo || '%')
          AND (:buyerName IS NULL OR buyer_name LIKE '%' || :buyerName || '%')
          AND (:startTime IS NULL OR create_time >= :startTime)
          AND (:endTime IS NULL OR create_time <= :endTime)
        ORDER BY create_time DESC
    """)
    fun getBillsByFilter(
        billNo: String?,
        buyerName: String?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<PreSaleBill>>

    @Query("SELECT * FROM presale_bill WHERE source_record_id = :sourceRecordId LIMIT 1")
    suspend fun getBillBySourceRecordId(sourceRecordId: String): PreSaleBill?

    @Query("SELECT * FROM presale_bill WHERE bill_no = :billNo LIMIT 1")
    suspend fun getBillByBillNo(billNo: String): PreSaleBill?

    @Query("UPDATE presale_bill SET source_record_id = :sourceRecordId, source_device_id = :sourceDeviceId WHERE id = :id")
    suspend fun updateSourceIdentity(id: Long, sourceRecordId: String, sourceDeviceId: String)

    @Query("UPDATE presale_bill SET remote_updated_at = :commitSeq WHERE id = :id")
    suspend fun updateRemoteUpdatedAt(id: Long, commitSeq: Long)

    @Query("UPDATE presale_bill SET sale_mode = :mode, status = :status WHERE id = :id")
    suspend fun updateSaleModeAndStatus(id: Long, mode: String, status: String)

    @Query("SELECT * FROM presale_bill WHERE sync_status = 0 ORDER BY create_time DESC")
    suspend fun getUnsyncedBills(): List<PreSaleBill>

    @Query("UPDATE presale_bill SET sync_status = 0 WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int
}
