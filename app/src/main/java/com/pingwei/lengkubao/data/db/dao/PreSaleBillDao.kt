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
}
