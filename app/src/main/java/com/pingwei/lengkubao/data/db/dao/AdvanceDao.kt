package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Advance
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvanceDao {

    @Insert
    suspend fun insert(advance: Advance): Long

    @Query("SELECT * FROM advances WHERE id = :id")
    suspend fun getAdvanceById(id: Long): Advance?

    @Query("SELECT * FROM advances WHERE customer_no = :customerNo AND advance_date = :advanceDate")
    suspend fun getAdvancesByCustomerAndDate(customerNo: String, advanceDate: String): List<Advance>

    @Query("SELECT * FROM advances WHERE customer_no = :customerNo AND sync_status = 0 ORDER BY create_time DESC LIMIT 1")
    suspend fun getLatestUnsyncedByCustomer(customerNo: String): Advance?

    @Query("UPDATE advances SET sync_status = :status, sync_time = :syncTime WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int, syncTime: Long = System.currentTimeMillis())
    @Insert
    suspend fun insertAll(advances: List<Advance>)

    @Update
    suspend fun update(advance: Advance)

    @Delete
    suspend fun delete(advance: Advance)

    @Query("DELETE FROM advances WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM advances WHERE customer_no = :customerNo")
    suspend fun deleteByCustomerNo(customerNo: String)

    @Query("SELECT * FROM advances WHERE id = :id")
    suspend fun getById(id: Long): Advance?

    @Query("SELECT * FROM advances WHERE customer_no = :customerNo ORDER BY advance_date DESC, id DESC")
    fun getAdvancesByCustomer(customerNo: String): Flow<List<Advance>>

    @Query("SELECT * FROM advances ORDER BY advance_date DESC, id DESC")
    fun getAllAdvances(): Flow<List<Advance>>

    @Query("SELECT * FROM advances WHERE sync_status = 0 ORDER BY advance_date DESC")
    suspend fun getUnsyncedAdvances(): List<Advance>

    @Query("UPDATE advances SET sync_status = 1, sync_time = :syncTime WHERE id = :id")
    suspend fun markAsSynced(id: Long, syncTime: Long)

    @Query("UPDATE advances SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Long)

    @Query("SELECT COUNT(*) FROM advances WHERE customer_no = :customerNo AND status = 1")
    suspend fun getCountByCustomer(customerNo: String): Int

    @Query("SELECT SUM(amount) FROM advances WHERE customer_no = :customerNo AND status = 1")
    suspend fun getTotalAmountByCustomer(customerNo: String): Double?

    @Query("SELECT * FROM advances WHERE customer_no = :customerNo AND status = 1 AND advance_date BETWEEN :startDate AND :endDate ORDER BY advance_date DESC")
    suspend fun getAdvancesByCustomerAndDateRange(customerNo: String, startDate: String, endDate: String): List<Advance>

    @Query("SELECT * FROM advances WHERE advance_date = :date ORDER BY id DESC")
    suspend fun getAdvancesByDate(date: String): List<Advance>

    @Query("SELECT COUNT(*) FROM advances WHERE sync_status = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT SUM(amount) FROM advances WHERE status = 1")
    suspend fun getTotalAllAdvances(): Double?

    @Query("SELECT * FROM advances WHERE create_time BETWEEN :startTime AND :endTime ORDER BY create_time DESC, id DESC")
    suspend fun getByCreateTimeRange(startTime: Long, endTime: Long): List<Advance>

    @Query("UPDATE advances SET sync_status = 0, sync_time = NULL WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int
}