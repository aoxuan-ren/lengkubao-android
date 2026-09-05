// service/InStockVoidService.kt
package com.pingwei.lengkubao.service

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class InStockVoidService(
    private val context: Context,
    private val database: AppDatabase,
    private val stockService: StockService
) {
    private val TAG = "InStockVoidService"

    /**
     * 物理删除入库单（包括库存还原）
     */
    suspend fun voidInStockBill(billId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚨 开始物理删除入库单: $billId")

        return@withContext try {
            // 1. 获取完整的入库单数据（包括明细）
            val bill = database.inStockBillDao().getBillById(billId)
            if (bill == null) {
                Log.e(TAG, "❌ 入库单不存在: $billId")
                return@withContext false
            }

            val items = database.inStockItemDao().getItemsByBillId(billId)
            if (items.isEmpty()) {
                Log.w(TAG, "⚠️ 入库单无明细: $billId")
            }

            Log.d(TAG, "📋 获取到入库单: ${bill.billNo}, 包含 ${items.size} 个明细项")

            // 2. 验证状态（避免重复操作）
            if (bill.status == "VOIDED" || bill.status == "2") {
                Log.w(TAG, "⚠️ 单据已作废，无需重复操作: ${bill.billNo}")
                return@withContext false
            }

            // 3. 开启数据库事务
            database.runInTransaction {
                // 使用 runBlocking 在事务中执行挂起函数
                runBlocking {
                    // 4. 还原库存（入库单删除需要减少库存）
                    if (!restoreStockInTransaction(items, bill, bill.locationId, bill.locationName, bill.id, bill.billNo)) {
                        Log.e(TAG, "❌ 库存还原失败，事务回滚")
                        throw Exception("库存还原失败")
                    }
                }

                Log.d(TAG, "✅ 库存还原成功")

                // 5. 先删除明细（因为有外键约束）
                runBlocking {
                    val itemsDeleted = database.inStockItemDao().deleteByBillId(billId)
                    Log.d(TAG, "🗑️ 删除明细记录: $itemsDeleted 条")

                    // 6. 删除主表记录
                    database.inStockBillDao().delete(bill)
                    Log.d(TAG, "🗑️ 删除主表记录")
                }
            }

            Log.d(TAG, "🎉 入库单物理删除成功: ${bill.billNo}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 入库单物理删除失败", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 还原库存（在事务中执行） - 现在是挂起函数
     */
    private suspend fun restoreStockInTransaction(
        items: List<InStockItem>,
        bill: InStockBill,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String
    ): Boolean {
        Log.d(TAG, "🔄 开始还原库存，单据: $billNo")

        try {
            items.forEach { item ->
                Log.d(TAG, "🔄 处理商品: ${item.productName}, 数量: -${item.quantity}")

                // 1. 获取当前库存
                val currentStock = database.stockDao().getStock(item.productId, locationId)
                if (currentStock == null) {
                    Log.w(TAG, "⚠️ 库存记录不存在: 商品ID=${item.productId}, 库位ID=$locationId")
                    // 即使库存不存在，也继续处理其他商品
                    return@forEach
                }

                // 2. 验证库存是否足够扣减
                if (currentStock.currentQuantity < item.quantity) {
                    Log.e(TAG, "❌ 库存不足无法还原: 商品=${item.productName}, " +
                            "当前库存=${currentStock.currentQuantity}, 需要扣减=${item.quantity}")
                    throw IllegalStateException(
                        "库存不足: ${item.productName} 当前库存${currentStock.currentQuantity}，需要扣减${item.quantity}"
                    )
                }

                // 3. 更新库存（减少）- 使用addStockQuantity方法（传入负数）
                val updatedRows = try {
                    database.stockDao().addStockQuantity(
                        productId = item.productId,
                        locationId = locationId,
                        addQuantity = -item.quantity,
                        timestamp = System.currentTimeMillis(),
                        billNo = "VOID_$billNo"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 库存更新失败", e)
                    throw Exception("库存更新失败: ${item.productName}")
                }

                if (updatedRows <= 0) {
                    Log.e(TAG, "❌ 库存更新失败: ${item.productName}")
                    throw Exception("库存更新失败: ${item.productName}")
                }

                Log.d(TAG, "✅ 库存还原成功: ${item.productName} -${item.quantity}箱")

                // 4. 记录库存变更（使用VOID类型）
                try {
                    val stockChange = StockChange(
                        productId = item.productId,
                        productName = item.productName,
                        locationId = locationId,
                        locationName = locationName,
                        changeType = ChangeType.VOID, // 使用VOID类型表示作废还原
                        quantity = -item.quantity,
                        relatedBillId = billId,
                        relatedBillType = BillType.INBOUND,
                        relatedBillNo = billNo,
                        remarks = "入库单作废，库存还原",
                        timestamp = System.currentTimeMillis()
                    )

                    database.stockChangeDao().insert(stockChange)
                    Log.d(TAG, "📝 记录库存变更: ${item.productName} 减少${item.quantity}箱")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 库存变更记录失败，不影响主流程: ${e.message}")
                }
            }

            Log.d(TAG, "✅ 所有库存还原完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 库存还原失败", e)
            throw e
        }
    }

    /**
     * 安全删除（包含确认对话框逻辑）
     */
    suspend fun voidBillWithConfirmation(billId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 这里可以添加额外的验证逻辑
            val bill = database.inStockBillDao().getBillById(billId)
            if (bill == null) {
                return@withContext Result.failure(Exception("单据不存在"))
            }

            // 验证是否已经删除
            if (bill.status == "VOIDED" || bill.status == "2") {
                return@withContext Result.success(false)
            }

            // 检查单据类型（只允许入库单）
            if (bill.status == "SALE" || bill.status == "PACKAGING") {
                return@withContext Result.failure(Exception("只能删除入库单"))
            }

            // 执行删除
            val success = voidInStockBill(billId)

            if (success) {
                Log.d(TAG, "✅ 单据删除成功: $billId")
                Result.success(true)
            } else {
                Log.e(TAG, "❌ 单据删除失败: $billId")
                Result.failure(Exception("删除操作失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除过程中出错", e)
            Result.failure(e)
        }
    }

    /**
     * 获取单据详情（用于确认对话框显示）
     */
    suspend fun getBillDetails(billId: Long): BillDetails? = withContext(Dispatchers.IO) {
        return@withContext try {
            val bill = database.inStockBillDao().getBillById(billId)
            val items = database.inStockItemDao().getItemsByBillId(billId)

            if (bill != null) {
                BillDetails(
                    billNo = bill.billNo,
                    customerName = bill.customerName ?: "",
                    locationName = bill.locationName,
                    operatorName = bill.operatorName ?: "",
                    totalQuantity = bill.totalQuantity,
                    totalAmount = bill.totalAmount,
                    itemCount = items.size,
                    createTime = bill.createTime
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取单据详情失败", e)
            null
        }
    }

    /**
     * 验证库存是否充足（在删除前检查）
     */
    suspend fun validateStockBeforeVoid(billId: Long): ValidationResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val bill = database.inStockBillDao().getBillById(billId) ?:
            return@withContext ValidationResult.Error("单据不存在")

            val items = database.inStockItemDao().getItemsByBillId(billId)

            val insufficientItems = mutableListOf<String>()

            items.forEach { item ->
                val currentStock = database.stockDao().getStock(item.productId, bill.locationId)
                if (currentStock == null || currentStock.currentQuantity < item.quantity) {
                    insufficientItems.add("${item.productName} (需要${item.quantity}箱, 库存${currentStock?.currentQuantity ?: 0}箱)")
                }
            }

            if (insufficientItems.isNotEmpty()) {
                ValidationResult.InsufficientStock(
                    message = "库存不足，无法作废单据",
                    items = insufficientItems
                )
            } else {
                ValidationResult.Valid
            }
        } catch (e: Exception) {
            Log.e(TAG, "验证库存失败", e)
            ValidationResult.Error("验证失败: ${e.message}")
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class InsufficientStock(val message: String, val items: List<String>) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    data class BillDetails(
        val billNo: String,
        val customerName: String,
        val locationName: String,
        val operatorName: String,
        val totalQuantity: Int,
        val totalAmount: Double,
        val itemCount: Int,
        val createTime: Long
    )
}