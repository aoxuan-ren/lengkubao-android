package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PaymentRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PaymentRecord): Long

    @Query("SELECT * FROM payment_record WHERE bill_id = :billId ORDER BY pay_time DESC")
    fun getPaymentsByBillId(billId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_record WHERE bill_id = :billId ORDER BY pay_time DESC")
    suspend fun getPaymentsByBillIdSync(billId: Long): List<PaymentRecord>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE bill_id = :billId")
    suspend fun getTotalPaidByBillId(billId: Long): Double

    @Query("SELECT * FROM payment_record WHERE id = :id")
    suspend fun getById(id: Long): PaymentRecord?

    @Query("SELECT * FROM payment_record WHERE source_record_id = :sourceRecordId LIMIT 1")
    suspend fun getBySourceRecordId(sourceRecordId: String): PaymentRecord?

    @Query("UPDATE payment_record SET source_record_id = :sourceRecordId, source_device_id = :sourceDeviceId WHERE id = :id")
    suspend fun updateSourceIdentity(id: Long, sourceRecordId: String, sourceDeviceId: String)

    @Query("SELECT * FROM payment_record WHERE sync_status = 0 ORDER BY pay_time DESC")
    suspend fun getUnsyncedPayments(): List<PaymentRecord>

    @Query("UPDATE payment_record SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE payment_record SET sync_status = 0 WHERE pay_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByPayTimeRange(startTime: Long, endTime: Long): Int
}
