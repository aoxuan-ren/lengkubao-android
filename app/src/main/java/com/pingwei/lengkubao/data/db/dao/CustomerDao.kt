// data/db/dao/CustomerDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long
    // 在CustomerDao中添加这个方法（如果还没有）
    @Query("SELECT * FROM customer WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?
    // 在 CustomerDao 接口中添加
    @Query("UPDATE customer SET customerName = :customerName, phone = :phone WHERE id = :id")
    suspend fun updateCustomer(id: Long, customerName: String, phone: String)
    @Query("UPDATE customer SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE customer SET enabled = :enabled, sync_status = 0, update_time = :updateTime WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, enabled: Boolean, updateTime: Long = System.currentTimeMillis())
    @Query("SELECT * FROM customer ORDER BY customerNo ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    // 同步查询方法
    @Query("SELECT * FROM customer")
    suspend fun getAllCustomersSync(): List<Customer>

    // 通过客户号查询
    @Query("SELECT * FROM customer WHERE customerNo = :customerNo LIMIT 1")
    suspend fun getByCustomerNo(customerNo: String): Customer?

    // 添加 delete 方法
    @Delete
    suspend fun delete(customer: Customer)

    // 按客户号删除
    @Query("DELETE FROM customer WHERE customerNo = :customerNo")
    suspend fun deleteByCustomerNo(customerNo: String)

    @Query("DELETE FROM customer WHERE customer_type = :type")
    suspend fun deleteAllByType(type: String)

    @Query("DELETE FROM customer WHERE customer_type = :type AND customerNo NOT IN (:codes)")
    suspend fun deleteByTypeExceptCodes(type: String, codes: List<String>)

    @Query("DELETE FROM customer WHERE customerNo NOT IN (:codes)")
    suspend fun deleteExceptCodes(codes: List<String>)

    @Query("DELETE FROM customer")
    suspend fun deleteAllCustomers()

    // 搜索客户（按名称或编号）
    @Query("SELECT * FROM customer WHERE customerName LIKE '%' || :keyword || '%' OR customerNo LIKE '%' || :keyword || '%'")
    fun searchCustomers(keyword: String): Flow<List<Customer>>

    // 简单获取所有客户（用于下拉选择等场景）
    @Query("SELECT * FROM customer ORDER BY customerNo")
    suspend fun getAllSimple(): List<Customer>

    // 获取客户数量
    @Query("SELECT COUNT(*) FROM customer")
    suspend fun getCustomerCount(): Int

    // 通过名称查询
    @Query("SELECT * FROM customer WHERE customerName = :customerName LIMIT 1")
    suspend fun getByCustomerName(customerName: String): Customer?

    // 新增：获取当前最大客户编号（用于自动生成）
    @Query("SELECT MAX(customerNo) FROM customer")
    suspend fun getMaxCustomerNo(): String?

    // 新增：更新客户二维码路径
    @Query("UPDATE customer SET qr_code_path = :qrCodePath, update_time = :updateTime WHERE id = :id")
    suspend fun updateQrCodePath(id: Long, qrCodePath: String, updateTime: Long): Int

    @Query("SELECT * FROM customer WHERE customer_type = :type ORDER BY customerNo ASC")
    fun getCustomersByType(type: String): Flow<List<Customer>>

    @Query("SELECT * FROM customer WHERE customer_type = :type ORDER BY customerNo ASC")
    suspend fun getCustomersByTypeSync(type: String): List<Customer>

    @Query("SELECT MAX(customerNo) FROM customer WHERE customerNo LIKE :prefix || '%'")
    suspend fun getMaxCustomerNoByPrefix(prefix: String): String?

    @Query("SELECT * FROM customer WHERE customer_type = :type AND (customerName LIKE '%' || :keyword || '%' OR customerNo LIKE '%' || :keyword || '%') ORDER BY customerNo ASC")
    fun searchCustomersByType(keyword: String, type: String): Flow<List<Customer>>
}