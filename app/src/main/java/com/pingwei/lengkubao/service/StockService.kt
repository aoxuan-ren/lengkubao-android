package com.pingwei.lengkubao.service

import android.util.Log
import com.pingwei.lengkubao.data.db.dao.StockChangeDao
import com.pingwei.lengkubao.data.db.dao.StockDao
import com.pingwei.lengkubao.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockService(
    private val stockDao: StockDao,
    private val stockChangeDao: StockChangeDao? = null // 设为可选参数
) {
    private val TAG = "StockService"

    // === 新增：单个商品库存增加方法（适配ViewModel调用）===
    suspend fun addStock(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        addQuantity: Int,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "📦 增加库存: 商品=$productName, 数量=$addQuantity, 库位=$locationName, 单据=$billNo")

            // 1. 更新库存
            val updatedRows = stockDao.addStockQuantity(
                productId = productId,
                locationId = locationId,
                addQuantity = addQuantity,
                timestamp = System.currentTimeMillis(),
                billNo = billNo
            )

            if (updatedRows <= 0) {
                // 如果没有更新到记录，创建新记录
                val newStock = Stock(
                    productId = productId,
                    productNo = "", // 需要从其他地方获取
                    productName = productName,
                    locationId = locationId,
                    currentQuantity = addQuantity,
                    reservedQuantity = 0,
                    lastUpdated = System.currentTimeMillis(),
                    lastBillNo = billNo
                )
                stockDao.insert(newStock)
                Log.d(TAG, "创建新库存记录: 商品=$productName, 初始数量=$addQuantity")
            } else {
                Log.d(TAG, "更新现有库存记录: 商品=$productName, 增加数量=$addQuantity")
            }

            // 2. 记录库存变动（如果stockChangeDao可用）
            stockChangeDao?.insert(
                StockChange(
                    productId = productId,
                    productName = productName,
                    locationId = locationId,
                    locationName = locationName,
                    changeType = ChangeType.INBOUND,
                    quantity = addQuantity,
                    relatedBillId = billId,
                    relatedBillType = BillType.INBOUND,
                    relatedBillNo = billNo,
                    remarks = "商品入库"
                )
            ) ?: run {
                Log.w(TAG, "stockChangeDao不可用，跳过库存变动记录")
            }

            Log.d(TAG, "✅ 库存增加成功: 商品=$productName, 数量=$addQuantity")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 库存增加失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // service/StockService.kt（添加VOID相关的记录）
    suspend fun recordVoidOperation(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        quantity: Int,
        billId: Long,
        billNo: String,
        reason: String = "单据作废"
    ): Boolean = withContext(Dispatchers.IO) { // 增加IO调度器，符合协程最佳实践
        return@withContext try {
            // 检查stockChangeDao是否可用
            if (stockChangeDao == null) {
                Log.w(TAG, "stockChangeDao不可用，无法记录作废操作")
                return@withContext false
            }

            val stockChange = StockChange(
                productId = productId,
                productName = productName,
                locationId = locationId,
                locationName = locationName,
                changeType = ChangeType.VOID,
                quantity = -quantity,
                relatedBillId = billId,
                relatedBillType = BillType.INBOUND,
                relatedBillNo = billNo,
                remarks = reason,
                timestamp = System.currentTimeMillis()
            )

            // 使用类中已声明的stockChangeDao，而不是未定义的database
            stockChangeDao.insert(stockChange)
            Log.d(TAG, "✅ 记录作废操作: ${productName} -${quantity}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 记录作废操作失败: ${e.message}", e)
            false
        }
    }

    // === 保留原有addStocks方法（批量增加）===
    suspend fun addStocks(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        addQuantity: Int,
        billId: Long,
        billNo: String,
        items: List<InStockItem>,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "📦 批量增加库存: 商品=$productName, 数量=$addQuantity, 库位=$locationName")

            // 调用单个增加方法
            val result = addStock(
                productId = productId,
                productName = productName,
                locationId = locationId,
                locationName = locationName,
                addQuantity = addQuantity,
                billId = billId,
                billNo = billNo
            )

            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "❌ 批量库存增加失败: ${e.message}", e)
            false
        }
    }

    // === 新增：批量增加多个商品库存 ===
    suspend fun addMultipleStocks(
        items: List<InStockItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "📦 开始批量增加多个商品库存: ${items.size} 个商品")

            var successCount = 0
            var failedCount = 0

            items.forEach { item ->
                val result = addStock(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    addQuantity = item.quantity,
                    billId = billId,
                    billNo = billNo
                )

                if (result.isSuccess) {
                    successCount++
                    Log.d(TAG, "✅ 商品库存增加成功: ${item.productName}, 数量=${item.quantity}")
                } else {
                    failedCount++
                    Log.e(TAG, "❌ 商品库存增加失败: ${item.productName}, 数量=${item.quantity}, 错误=${result.exceptionOrNull()?.message}")
                }
            }

            if (failedCount > 0) {
                Result.failure(Exception("批量增加库存失败: 成功 $successCount 个, 失败 $failedCount 个"))
            } else {
                Log.d(TAG, "✅ 所有商品库存增加成功: $successCount 个商品")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "批量增加库存异常", e)
            Result.failure(e)
        }
    }

    // === 销售预留（锁定库存）===
    suspend fun reserveForSale(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        quantity: Int,
        billId: Long,
        billNo: String,
        billType: BillType = BillType.SALE
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔒 销售预留: 商品=$productName, 数量=$quantity, 库位=$locationName")

            // 1. 检查可用库存
            val available = getAvailableStock(productId, locationId)
            if (available < quantity) {
                return@withContext Result.failure(
                    IllegalStateException("库存不足，可用: $available, 需要: $quantity")
                )
            }

            // 2. 锁定库存（增加预留数量）
            val reservedRows = stockDao.reserveStock(
                productId = productId,
                locationId = locationId,
                reserveQuantity = quantity,
                timestamp = System.currentTimeMillis()
            )

            if (reservedRows <= 0) {
                return@withContext Result.failure(
                    IllegalStateException("库存锁定失败")
                )
            }

            // 3. 记录库存变动（如果可用）
            stockChangeDao?.insert(
                StockChange(
                    productId = productId,
                    productName = productName,
                    locationId = locationId,
                    locationName = locationName,
                    changeType = ChangeType.RESERVE,
                    quantity = quantity,
                    relatedBillId = billId,
                    relatedBillType = billType,
                    relatedBillNo = billNo,
                    remarks = if (billType == BillType.PRESALE) "预售预留" else "销售预留"
                )
            ) ?: run {
                Log.w(TAG, "stockChangeDao不可用，跳过库存变动记录")
            }

            Log.d(TAG, "✅ 销售预留成功: 商品=$productName, 数量=$quantity")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销售预留失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // === 确认销售扣减（实际出库）===
    suspend fun confirmSaleDeduction(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        quantity: Int,
        billId: Long,
        billNo: String,
        billType: BillType = BillType.SALE
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "💰 确认销售扣减: 商品=$productName, 数量=$quantity, 单据=$billNo")

            // 1. 确认扣减（从预留转为实际扣减）
            val deductedRows = stockDao.confirmDeduction(
                productId = productId,
                locationId = locationId,
                deductQuantity = quantity,
                timestamp = System.currentTimeMillis(),
                billNo = billNo
            )

            if (deductedRows <= 0) {
                return@withContext Result.failure(
                    IllegalStateException("库存扣减失败，请检查预留库存")
                )
            }

            // 2. 记录库存变动（如果可用）
            stockChangeDao?.insert(
                StockChange(
                    productId = productId,
                    productName = productName,
                    locationId = locationId,
                    locationName = locationName,
                    changeType = ChangeType.OUTBOUND,
                    quantity = -quantity, // 负数表示减少
                    relatedBillId = billId,
                    relatedBillType = billType,
                    relatedBillNo = billNo,
                    remarks = if (billType == BillType.PRESALE) "预售出库" else "销售出库"
                )
            ) ?: run {
                Log.w(TAG, "stockChangeDao不可用，跳过库存变动记录")
            }

            Log.d(TAG, "✅ 销售扣减成功: 商品=$productName, 数量=$quantity")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销售扣减失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // === 取消销售预留（释放库存）===
    suspend fun cancelReservation(
        productId: Long,
        productName: String,
        locationId: Long,
        locationName: String,
        quantity: Int,
        billId: Long,
        billNo: String,
        billType: BillType = BillType.SALE
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔄 取消销售预留: 商品=$productName, 数量=$quantity")

            // 1. 释放预留库存
            val releasedRows = stockDao.releaseReservation(
                productId = productId,
                locationId = locationId,
                releaseQuantity = quantity,
                timestamp = System.currentTimeMillis()
            )

            if (releasedRows <= 0) {
                return@withContext Result.failure(
                    IllegalStateException("库存释放失败")
                )
            }

            // 2. 记录库存变动（如果可用）
            stockChangeDao?.insert(
                StockChange(
                    productId = productId,
                    productName = productName,
                    locationId = locationId,
                    locationName = locationName,
                    changeType = ChangeType.RELEASE,
                    quantity = quantity,
                    relatedBillId = billId,
                    relatedBillType = billType,
                    relatedBillNo = billNo,
                    remarks = if (billType == BillType.PRESALE) "取消预售预留" else "取消销售预留"
                )
            ) ?: run {
                Log.w(TAG, "stockChangeDao不可用，跳过库存变动记录")
            }

            Log.d(TAG, "✅ 销售预留取消成功: 商品=$productName, 数量=$quantity")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 销售预留取消失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // === 获取可用库存 ===
    suspend fun getAvailableStock(productId: Long, locationId: Long): Int {
        return withContext(Dispatchers.IO) {
            try {
                stockDao.getAvailableStock(productId, locationId) ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "获取可用库存失败: ${e.message}", e)
                0
            }
        }
    }

    suspend fun reserveMultipleForPreSale(
        items: List<PreSaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            items.forEachIndexed { index, item ->
                val result = reserveForSale(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    quantity = item.quantity,
                    billId = billId,
                    billNo = billNo,
                    billType = BillType.PRESALE
                )
                if (result.isFailure) {
                    rollbackPreSaleReservations(items.subList(0, index), locationId, locationName, billId, billNo)
                    return@withContext result
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "批量预售预留失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun confirmMultipleDeductionsForPreSale(
        items: List<PreSaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            items.forEach { item ->
                confirmSaleDeduction(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    quantity = item.quantity,
                    billId = billId,
                    billNo = billNo,
                    billType = BillType.PRESALE
                ).onFailure { error ->
                    Log.e(TAG, "预售扣减失败: ${item.productName}, ${error.message}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "批量预售扣减失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun releaseMultipleReservationsForPreSale(
        items: List<PreSaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            items.forEach { item ->
                cancelReservation(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    quantity = item.quantity,
                    billId = billId,
                    billNo = billNo,
                    billType = BillType.PRESALE
                ).onFailure { error ->
                    Log.e(TAG, "释放预售预留失败: ${item.productName}, ${error.message}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "批量释放预售预留失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun rollbackPreSaleReservations(
        items: List<PreSaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ) {
        items.forEach { item ->
            cancelReservation(
                productId = item.productId,
                productName = item.productName,
                locationId = locationId,
                locationName = locationName,
                quantity = item.quantity,
                billId = billId,
                billNo = billNo,
                billType = BillType.PRESALE
            )
        }
    }

    // === 批量预留库存 ===
    suspend fun reserveMultipleForSale(
        items: List<SaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 按顺序预留每个商品
            items.forEachIndexed { index, item ->
                val result = reserveForSale(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    quantity = item.quantity,
                    billId = billId,
                    billNo = billNo
                )

                if (result.isFailure) {
                    // 预留失败，回滚之前预留的库存
                    rollbackReservations(items.subList(0, index), locationId, locationName, billId, billNo)
                    return@withContext result
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "批量预留失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // === 批量确认扣减 ===
    suspend fun confirmMultipleDeductions(
        items: List<SaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            items.forEach { item ->
                confirmSaleDeduction(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = locationId,
                    locationName = locationName,
                    quantity = item.quantity,
                    billId = billId,
                    billNo = billNo
                ).onFailure { error ->
                    Log.e(TAG, "部分扣减失败: ${item.productName}, ${error.message}")
                    // 继续处理其他商品，不中断
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "批量扣减失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // === 回滚预留 ===
    private suspend fun rollbackReservations(
        items: List<SaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ) {
        items.forEach { item ->
            cancelReservation(
                productId = item.productId,
                productName = item.productName,
                locationId = locationId,
                locationName = locationName,
                quantity = item.quantity,
                billId = billId,
                billNo = billNo
            ).onFailure { error ->
                Log.e(TAG, "回滚失败: ${item.productName}, ${error.message}")
            }
        }
    }

    // === 新增：简单的构造函数（只有stockDao）===
    constructor(stockDao: StockDao) : this(stockDao, null)
}