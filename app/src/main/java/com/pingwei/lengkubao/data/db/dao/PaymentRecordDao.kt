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
}
