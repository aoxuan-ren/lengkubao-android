package com.pingwei.lengkubao.service

import android.util.Log
import com.pingwei.lengkubao.data.db.dao.CustomerInboundStockDao
import com.pingwei.lengkubao.data.db.entity.CustomerInboundStock
import com.pingwei.lengkubao.data.db.entity.SaleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustomerInboundStockService(
    private val dao: CustomerInboundStockDao,
    private val backfill: CustomerInboundStockBackfill
) {
    private val TAG = "CustomerInboundStockService"

    suspend fun ensureInitialized() {
        backfill.runIfNeeded()
    }

    suspend fun getAvailable(customerNo: String, locationId: Long, productId: Long): Int =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            dao.getAvailableQuantity(customerNo, locationId, productId) ?: 0
        }

    suspend fun addInbound(
        customerNo: String,
        customerName: String,
        locationId: Long,
        locationName: String,
        productId: Long,
        productNo: String,
        productName: String,
        quantity: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext Result.success(Unit)
        return@withContext try {
            ensureInitialized()
            val now = System.currentTimeMillis()
            val updated = dao.addInboundQuantity(customerNo, locationId, productId, quantity, now)
            if (updated <= 0) {
                dao.upsert(
                    CustomerInboundStock(
                        customerNo = customerNo,
                        customerName = customerName,
                        locationId = locationId,
                        locationName = locationName,
                        productId = productId,
                        productNo = productNo,
                        productName = productName,
                        inboundQuantity = quantity,
                        reservedQuantity = 0,
                        lastUpdated = now
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addInbound failed", e)
            Result.failure(e)
        }
    }

    suspend fun subtractInbound(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext Result.success(Unit)
        return@withContext try {
            val available = dao.getAvailableQuantity(customerNo, locationId, productId) ?: 0
            val sold = dao.getSoldQuantity(customerNo, locationId, productId)
            val row = dao.getRow(customerNo, locationId, productId)
            val maxSubtract = (row?.inboundQuantity ?: 0) - (row?.reservedQuantity ?: 0) - sold
            if (quantity > maxSubtract) {
                return@withContext Result.failure(
                    IllegalStateException("客户入库库存不足，最多可扣减: $maxSubtract")
                )
            }
            val updated = dao.subtractInboundQuantity(
                customerNo, locationId, productId, quantity, System.currentTimeMillis()
            )
            if (updated <= 0) {
                return@withContext Result.failure(IllegalStateException("客户入库库存扣减失败"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reserveForSale(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val available = dao.getAvailableQuantity(customerNo, locationId, productId) ?: 0
            if (available < quantity) {
                return@withContext Result.failure(
                    IllegalStateException("可报账库存不足，可用: $available, 需要: $quantity")
                )
            }
            val updated = dao.addReservedQuantity(
                customerNo, locationId, productId, quantity, System.currentTimeMillis()
            )
            if (updated <= 0) {
                return@withContext Result.failure(IllegalStateException("可报账库存预留失败"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmSaleDeduction(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int
    ): Result<Unit> = releaseReservation(customerNo, locationId, productId, quantity)

    suspend fun releaseReservation(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updated = dao.releaseReservedQuantity(
                customerNo, locationId, productId, quantity, System.currentTimeMillis()
            )
            if (updated <= 0) {
                return@withContext Result.failure(IllegalStateException("释放可报账预留失败"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reserveMultipleForSale(
        customerNo: String,
        items: List<SaleItem>,
        locationId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEachIndexed { index, item ->
                val result = reserveForSale(customerNo, locationId, item.productId, item.quantity)
                if (result.isFailure) {
                    rollbackReservations(customerNo, items.subList(0, index), locationId)
                    return@withContext result
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmMultipleDeductions(
        customerNo: String,
        items: List<SaleItem>,
        locationId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { item ->
                confirmSaleDeduction(customerNo, locationId, item.productId, item.quantity)
                    .onFailure { return@withContext Result.failure(it) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun releaseMultipleReservations(
        customerNo: String,
        items: List<SaleItem>,
        locationId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        items.forEach { item ->
            releaseReservation(customerNo, locationId, item.productId, item.quantity)
        }
        Result.success(Unit)
    }

    /** 作废/删除销售单：已售量由 sale_item 删除自动减少，无需恢复 stock */
    suspend fun restoreOnVoid(): Result<Unit> = Result.success(Unit)

    private suspend fun rollbackReservations(
        customerNo: String,
        items: List<SaleItem>,
        locationId: Long
    ) {
        items.forEach { item ->
            releaseReservation(customerNo, locationId, item.productId, item.quantity)
        }
    }
}
