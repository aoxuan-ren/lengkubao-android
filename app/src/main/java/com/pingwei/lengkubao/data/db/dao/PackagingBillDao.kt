// data/db/dao/PackagingBillDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PackagingBill
import com.pingwei.lengkubao.data.db.entity.PackagingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PackagingBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: PackagingBill): Long

    @Query("SELECT * FROM packaging_bill WHERE id = :id")
    suspend fun getById(id: Long): PackagingBill?

    @Query("SELECT * FROM packaging_item WHERE bill_id = :billId ORDER BY itemId  ASC")
    suspend fun getItemsByBillId(billId: Long): List<PackagingItem>

    @Update
    suspend fun update(bill: PackagingBill)

    // 原有删除方法（无返回值），保留并补充返回值以匹配要求
    @Delete
    suspend fun delete(bill: PackagingBill): Int

    // 新增：根据billId删除包装单
    @Query("DELETE FROM packaging_bill WHERE id = :billId")
    suspend fun deleteBill(billId: Long): Int

    // 新增：根据billNo删除包装单
    @Query("DELETE FROM packaging_bill WHERE bill_no = :billNo")
    suspend fun deleteByBillNo(billNo: String): Int

    @Query("SELECT * FROM packaging_bill ORDER BY create_time DESC")
    fun getAllBills(): Flow<List<PackagingBill>>

    @Query("SELECT * FROM packaging_bill WHERE id = :id")
    suspend fun getBillById(id: Long): PackagingBill?

    @Query("SELECT * FROM packaging_bill WHERE bill_no = :billNo")
    suspend fun getBillByNo(billNo: String): PackagingBill?

    @Query("SELECT * FROM packaging_bill WHERE customer_no = :customerNo ORDER BY create_time DESC")
    fun getBillsByCustomer(customerNo: String): Flow<List<PackagingBill>>

    @Query("UPDATE packaging_bill SET is_voided = :voided WHERE id = :id")
    suspend fun updateVoidStatus(id: Long, voided: Boolean)

    @Query("UPDATE packaging_bill SET is_printed = :printed WHERE id = :id")
    suspend fun updatePrintStatus(id: Long, printed: Boolean)

    @Query("UPDATE packaging_bill SET is_synced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean)

    @Query("UPDATE packaging_bill SET is_synced = 0, sync_status = 0 WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM packaging_bill WHERE DATE(create_time/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')")
    suspend fun getTodayBillCount(date: Long): Int
}