// data/db/dao/StockDao.kt（修正版本）
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.pingwei.lengkubao.data.db.entity.Stock
import com.pingwei.lengkubao.data.db.entity.StockWithProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock: Stock): Long

    @Update
    suspend fun update(stock: Stock): Int

    @Delete
    suspend fun delete(stock: Stock)

    // ============ 修正的新增方法 ============

    /**
     * 查询指定库位下所有有库存的商品（带商品信息）
     * 注意：已修正字段名匹配问题
     */
    @Query("""
        SELECT 
            s.id as stockId,
            s.product_id as productId,
            s.product_no as productNo,
            s.product_name as productName,
            s.location_id as locationId,
            s.location_no as locationNo,
            s.current_quantity as currentQuantity,
            s.reserved_quantity as reservedQuantity,
            s.last_updated as lastUpdated,
            s.last_bill_no as lastBillNo
        FROM stock s
        WHERE s.location_id = :locationId
        AND s.current_quantity > 0
        ORDER BY s.product_no
    """)
    suspend fun getStocksWithProductByLocation(locationId: Long): List<StockWithProduct>

    /**
     * 查询指定库位和商品的库存（包含预留库存）
     */
    @Query("""
        SELECT 
            s.id as stockId,
            s.product_id as productId,
            s.product_no as productNo,
            s.product_name as productName,
            s.location_id as locationId,
            s.location_no as locationNo,
            s.current_quantity as currentQuantity,
            s.reserved_quantity as reservedQuantity,
            s.last_updated as lastUpdated,
            s.last_bill_no as lastBillNo
        FROM stock s
        WHERE s.location_id = :locationId
        AND s.product_id = :productId
    """)
    suspend fun getStockWithProduct(productId: Long, locationId: Long): StockWithProduct?

    /**
     * 获取指定商品的实时可用库存
     */
    @Query("""
        SELECT (s.current_quantity - s.reserved_quantity) 
        FROM stock s
        WHERE s.product_id = :productId 
        AND s.location_id = :locationId
    """)
    suspend fun getRealTimeAvailableStock(productId: Long, locationId: Long): Int?

    /**
     * 批量获取多个商品的可用库存
     */
    @Transaction
    suspend fun getBatchAvailableStocks(
        productIds: List<Long>,
        locationId: Long
    ): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        productIds.forEach { productId ->
            val available = getRealTimeAvailableStock(productId, locationId) ?: 0
            result[productId] = available
        }
        return result
    }

    // ============ 以下是原有方法 ============

    // 按商品和库位更新库存
    @Query("""
        UPDATE stock 
        SET current_quantity = :quantity, 
            last_updated = :timestamp, 
            last_bill_no = :billNo 
        WHERE product_id = :productId AND location_id = :locationId
    """)
    suspend fun updateStockQuantity(
        productId: Long,
        locationId: Long,
        quantity: Int,
        timestamp: Long,
        billNo: String
    )

    // 增加库存（入库用）
    @Query("""
        UPDATE stock 
        SET current_quantity = current_quantity + :addQuantity, 
            last_updated = :timestamp, 
            last_bill_no = :billNo 
        WHERE product_id = :productId AND location_id = :locationId
    """)
    suspend fun addStockQuantity(
        productId: Long,
        locationId: Long,
        addQuantity: Int,
        timestamp: Long,
        billNo: String
    ):Int

    // 减少库存（出库用）- 带预留库存检查
    @Query("""
        UPDATE stock 
        SET current_quantity = current_quantity - :subQuantity,
            reserved_quantity = reserved_quantity - :subQuantity,
            last_updated = :timestamp,
            last_bill_no = :billNo 
        WHERE product_id = :productId 
          AND location_id = :locationId 
          AND current_quantity >= :subQuantity
          AND reserved_quantity >= :subQuantity
    """)
    suspend fun subtractStockQuantity(
        productId: Long,
        locationId: Long,
        subQuantity: Int,
        timestamp: Long,
        billNo: String
    ): Int

    // 锁定库存（预留库存）
    @Query("""
        UPDATE stock 
        SET current_quantity = current_quantity - :lockQuantity,
            reserved_quantity = reserved_quantity + :lockQuantity,
            last_updated = :timestamp
        WHERE product_id = :productId 
          AND location_id = :locationId 
          AND current_quantity >= :lockQuantity
    """)
    suspend fun lockStock(
        productId: Long,
        locationId: Long,
        lockQuantity: Int,
        timestamp: Long
    ): Int

    // 释放预留库存（取消锁定）
    @Query("""
        UPDATE stock 
        SET current_quantity = current_quantity + :releaseQuantity,
            reserved_quantity = reserved_quantity - :releaseQuantity,
            last_updated = :timestamp
        WHERE product_id = :productId 
          AND location_id = :locationId 
          AND reserved_quantity >= :releaseQuantity
    """)
    suspend fun releaseStock(
        productId: Long,
        locationId: Long,
        releaseQuantity: Int,
        timestamp: Long
    ): Int

    // 获取指定商品在指定库位的库存
    @Query("SELECT * FROM stock WHERE product_id = :productId AND location_id = :locationId")
    suspend fun getStock(productId: Long, locationId: Long): Stock?

    // 获取指定库位的所有商品库存
    @Query("SELECT * FROM stock WHERE location_id = :locationId")
    fun getStocksByLocation(locationId: Long): Flow<List<Stock>>

    // 获取指定商品在所有库位的库存
    @Query("SELECT * FROM stock WHERE product_id = :productId")
    fun getStocksByProduct(productId: Long): Flow<List<Stock>>

    // 获取所有库存
    @Query("SELECT * FROM stock ORDER BY location_no, product_no")
    fun getAllStocks(): Flow<List<Stock>>

    // 检查可用库存数量
    @Query("SELECT (current_quantity - reserved_quantity) FROM stock WHERE product_id = :productId AND location_id = :locationId")
    suspend fun checkAvailableQuantity(productId: Long, locationId: Long): Int?

    // 原子操作：检查并锁定库存
    @Transaction
    suspend fun checkAndLockStockAtomic(productId: Long, locationId: Long, quantity: Int): Boolean {
        val available = checkAvailableQuantity(productId, locationId) ?: 0
        if (available >= quantity) {
            return lockStock(productId, locationId, quantity, System.currentTimeMillis()) > 0
        }
        return false
    }

    // 原生查询支持
    @RawQuery
    suspend fun getStockByQuery(query: SupportSQLiteQuery): Stock?

    // 原子更新（版本控制）
    @Query("""
        UPDATE stock 
        SET current_quantity = :newCurrentQty, 
            reserved_quantity = :newReservedQty,
            last_updated = :timestamp
        WHERE id = :id 
        AND current_quantity = :oldCurrentQty
        AND reserved_quantity = :oldReservedQty
    """)
    suspend fun updateStockAtomic(
        id: Long,
        oldCurrentQty: Int,
        oldReservedQty: Int,
        newCurrentQty: Int,
        newReservedQty: Int,
        timestamp: Long
    ): Int
    // === 新增：库存预留和锁定方法 ===

    /**
     * 锁定库存（增加预留数量）
     */
    @Query("""
        UPDATE stock 
        SET reserved_quantity = reserved_quantity + :reserveQuantity,
            last_updated = :timestamp
        WHERE product_id = :productId 
        AND location_id = :locationId
        AND (current_quantity - reserved_quantity) >= :reserveQuantity
    """)
    suspend fun reserveStock(
        productId: Long,
        locationId: Long,
        reserveQuantity: Int,
        timestamp: Long
    ): Int

    /**
     * 确认扣减（从预留转为实际扣减）
     */
    @Query("""
        UPDATE stock 
        SET current_quantity = current_quantity - :deductQuantity,
            reserved_quantity = reserved_quantity - :deductQuantity,
            last_updated = :timestamp,
            last_bill_no = :billNo
        WHERE product_id = :productId 
        AND location_id = :locationId
        AND reserved_quantity >= :deductQuantity
    """)
    suspend fun confirmDeduction(
        productId: Long,
        locationId: Long,
        deductQuantity: Int,
        timestamp: Long,
        billNo: String
    ): Int

    /**
     * 释放预留库存
     */
    @Query("""
        UPDATE stock 
        SET reserved_quantity = reserved_quantity - :releaseQuantity,
            last_updated = :timestamp
        WHERE product_id = :productId 
        AND location_id = :locationId
        AND reserved_quantity >= :releaseQuantity
    """)
    suspend fun releaseReservation(
        productId: Long,
        locationId: Long,
        releaseQuantity: Int,
        timestamp: Long
    ): Int

    /**
     * 获取实时可用库存
     */
    @Query("""
        SELECT (current_quantity - reserved_quantity) as available 
        FROM stock 
        WHERE product_id = :productId AND location_id = :locationId
    """)
    suspend fun getAvailableStock(productId: Long, locationId: Long): Int?

}