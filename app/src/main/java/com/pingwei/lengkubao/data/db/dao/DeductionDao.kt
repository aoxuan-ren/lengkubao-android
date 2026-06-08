package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Deduction
import kotlinx.coroutines.flow.Flow

@Dao
interface DeductionDao {

    @Insert
    suspend fun insert(deduction: Deduction): Long


    @Query("SELECT * FROM deductions WHERE id = :id")
    suspend fun getDeductionById(id: Long): Deduction?

    @Query("SELECT * FROM deductions WHERE customer_no = :customerNo AND deduct_date = :deductDate")
    suspend fun getDeductionsByCustomerAndDate(customerNo: String, deductDate: String): List<Deduction>

    @Query("SELECT * FROM deductions WHERE customer_no = :customerNo AND sync_status = 0 ORDER BY create_time DESC LIMIT 1")
    suspend fun getLatestUnsyncedByCustomer(customerNo: String): Deduction?

    @Query("UPDATE deductions SET sync_status = :status, sync_time = :syncTime WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int, syncTime: Long = System.currentTimeMillis())
    @Insert
    suspend fun insertAll(deductions: List<Deduction>)

    @Update
    suspend fun update(deduction: Deduction)

    @Delete
    suspend fun delete(deduction: Deduction)

    @Query("DELETE FROM deductions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM deductions WHERE customer_no = :customerNo")
    suspend fun deleteByCustomerNo(customerNo: String)

    @Query("SELECT * FROM deductions WHERE id = :id")
    suspend fun getById(id: Long): Deduction?

    @Query("SELECT * FROM deductions WHERE customer_no = :customerNo ORDER BY deduct_date DESC, id DESC")
    fun getDeductionsByCustomer(customerNo: String): Flow<List<Deduction>>

    @Query("SELECT * FROM deductions ORDER BY deduct_date DESC, id DESC")
    fun getAllDeductions(): Flow<List<Deduction>>

    @Query("SELECT * FROM deductions WHERE sync_status = 0 ORDER BY deduct_date DESC")
    suspend fun getUnsyncedDeductions(): List<Deduction>

    @Query("UPDATE deductions SET sync_status = 1, sync_time = :syncTime WHERE id = :id")
    suspend fun markAsSynced(id: Long, syncTime: Long)

    @Query("UPDATE deductions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Long)

    @Query("SELECT COUNT(*) FROM deductions WHERE customer_no = :customerNo AND status = 1")
    suspend fun getCountByCustomer(customerNo: String): Int

    @Query("SELECT SUM(amount) FROM deductions WHERE customer_no = :customerNo AND status = 1")
    suspend fun getTotalAmountByCustomer(customerNo: String): Double?

    @Query("SELECT * FROM deductions WHERE customer_no = :customerNo AND status = 1 AND deduct_date BETWEEN :startDate AND :endDate ORDER BY deduct_date DESC")
    suspend fun getDeductionsByCustomerAndDateRange(customerNo: String, startDate: String, endDate: String): List<Deduction>

    @Query("SELECT * FROM deductions WHERE deduct_date = :date ORDER BY id DESC")
    suspend fun getDeductionsByDate(date: String): List<Deduction>

    @Query("SELECT COUNT(*) FROM deductions WHERE sync_status = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT SUM(amount) FROM deductions WHERE status = 1")
    suspend fun getTotalAllDeductions(): Double?

    @Query("UPDATE deductions SET sync_status = 0, sync_time = NULL WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int
}