package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.SaleBill
import com.pingwei.lengkubao.data.db.entity.SaleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: SaleBill): Long
    // 在 SaleBillDao 中添加
// 在SaleBillDao.kt中

    // 新增：根据ID删除方法
    @Query("DELETE FROM sale_bill WHERE id = :billId")
    suspend fun deleteSaleBillById(billId: Long): Int
    @Query("SELECT * FROM sale_item WHERE bill_id = :billId")
    suspend fun getItemsByBillId(billId: Long): List<SaleItem>
    @Update
    suspend fun update(bill: SaleBill)

    @Delete
    suspend fun delete(bill: SaleBill)

    @Query("UPDATE sale_bill SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("SELECT * FROM sale_bill ORDER BY create_time DESC")
    fun getAllBills(): Flow<List<SaleBill>>

    @Query("SELECT * FROM sale_bill WHERE id = :id")
    suspend fun getBillById(id: Long): SaleBill?

    @Query("SELECT * FROM sale_bill WHERE bill_no = :billNo")
    suspend fun getBillByNo(billNo: String): SaleBill?

    @Query("SELECT * FROM sale_bill WHERE customer_no = :customerNo ORDER BY create_time DESC")
    fun getBillsByCustomer(customerNo: String): Flow<List<SaleBill>>

    // 获取今日销售单数量（用于生成流水号）
    @Query("SELECT COUNT(*) FROM sale_bill WHERE DATE(create_time/1000, 'unixepoch') = DATE(:date/1000, 'unixepoch')")
    suspend fun getTodayBillCount(date: Long): Int

    // 更新同步状态
    @Query("UPDATE sale_bill SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE sale_bill SET sync_status = 0 WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int

    // 作废单据（同时更新状态和同步状态）
    @Transaction
    suspend fun voidBill(billId: Long) {
        updateStatus(billId, 2) // 2=作废
        updateSyncStatus(billId, 0) // 需要重新同步
    }

    @Query("SELECT * FROM sale_bill WHERE id = :id")
    suspend fun getById(id: Long): SaleBill?

    @Query("UPDATE sale_bill SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    // 按条件查询销售单（适配筛选功能：客户、日期范围、经手人、库位）
    @Query("""
        SELECT * FROM sale_bill 
        WHERE (:billNo IS NULL OR bill_no LIKE '%' || :billNo || '%')
          AND (:customerName IS NULL OR customer_name LIKE '%' || :customerName || '%')
          AND (:startTime IS NULL OR create_time >= :startTime)
          AND (:endTime IS NULL OR create_time <= :endTime)
          AND (:operatorId IS NULL OR operator_id = :operatorId)
          AND (:locationId IS NULL OR location_id = :locationId)
        ORDER BY create_time DESC
    """)
    fun getSaleBillsByFilter(
        billNo: String?,        // 单据号模糊查询
        customerName: String?,  // 客户名称模糊查询
        startTime: Long?,       // 开始时间（时间戳）
        endTime: Long?,         // 结束时间（时间戳）
        operatorId: Long?,      // 经手人ID筛选
        locationId: Long?       // 库位ID筛选
    ): Flow<List<SaleBill>>

}