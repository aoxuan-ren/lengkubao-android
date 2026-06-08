// data/db/dao/ProductDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)
    @Query("UPDATE product SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)
    @Update
    suspend fun update(product: Product)
    @Query("SELECT * FROM product WHERE productNo = :productNo LIMIT 1")
    suspend fun getByProductNo(productNo: String): Product?


    @Delete
    suspend fun delete(product: Product)
    // 添加：查询所有启用商品
    @Query("SELECT * FROM product WHERE enabled = 1 ORDER BY productNo")
    suspend fun getAllEnabled(): List<Product>

    // 添加：查询所有启用商品（Flow版本）
    @Query("SELECT * FROM product WHERE enabled = 1 ORDER BY productNo")
    fun getAllEnabledFlow(): Flow<List<Product>?>

    // 添加：根据ID获取启用商品（如果禁用返回null）
    @Query("SELECT * FROM product WHERE id = :productId AND enabled = 1")
    suspend fun getEnabledById(productId: Long): Product?

    // 添加：根据编号获取启用商品
    @Query("SELECT * FROM product WHERE productNo = :productNo AND enabled = 1 LIMIT 1")
    suspend fun getEnabledByNo(productNo: String): Product?
    @Query("SELECT * FROM product ORDER BY productNo")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM product WHERE productNo = :productNo LIMIT 1")
    suspend fun getByNo(productNo: String): Product?

    @Query("UPDATE product SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("SELECT * FROM product WHERE id = :productId")
    suspend fun getProductById(productId: Long): Product?
    // 添加这个关键方法
    @Query("SELECT COUNT(*) FROM product")
    suspend fun getCount(): Int

    // 如果需要 Flow 版本
    @Query("SELECT * FROM product ORDER BY productNo")
    fun getAllFlow(): Flow<List<Product>?>

}