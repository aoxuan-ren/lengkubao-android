package com.pingwei.lengkubao.service

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.SaleBill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction // 关键：导入Room的挂起版事务扩展函数

/**
 * 销售单删除服务
 * 处理销售单的物理删除逻辑，包括库存恢复
 */
class SaleOutVoidService(
    private val context: Context,
    private val database: AppDatabase,
    private val stockService: StockService
) {
    private val TAG = "SaleOutVoidService"

    /**
     * 物理删除销售单（带确认逻辑）
     */
    suspend fun voidBillWithConfirmation(billId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ 开始物理删除销售单: $billId")

                // 1. 检查单据是否存在
                val bill = database.saleBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "❌ 销售单不存在: $billId")
                    return@withContext Result.failure(IllegalArgumentException("销售单不存在"))
                }

                Log.d(TAG, "📋 销售单信息: ${bill.billNo}, 客户=${bill.customerName}")

                // 2. 删除销售单（同时恢复库存）
                val result = deleteSaleBillWithStockRecovery(billId, bill)

                if (result) {
                    Log.d(TAG, "✅ 销售单物理删除成功: ${bill.billNo}")
                    Result.success(true)
                } else {
                    Log.e(TAG, "❌ 销售单物理删除失败: ${bill.billNo}")
                    Result.failure(RuntimeException("删除失败"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 删除销售单异常", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 执行销售单删除（包含库存恢复）
     * @param billId 销售单ID
     * @param bill 提前查询的销售单对象，避免重复查询
     */
    private suspend fun deleteSaleBillWithStockRecovery(billId: Long, bill: SaleBill): Boolean {
        return try {
            // 1. 获取销售单明细（用于库存恢复）
            val saleItems = database.saleItemDao().getItemsByBillId(billId)
            Log.d(TAG, "📦 找到 ${saleItems.size} 条销售明细")

            // 2. 恢复库存（销售单删除需要增加库存）
            saleItems.forEach { item ->
                Log.d(TAG, "🔄 恢复库存: ${item.productName} x${item.quantity}")

                // 调用StockService的addStock方法恢复库存
                val stockResult = stockService.addStock(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = bill.locationId,
                    locationName = "", // 可根据实际情况补充库位名称
                    addQuantity = item.quantity,
                    billId = billId,
                    billNo = bill.billNo
                )

                // 检查库存恢复是否成功
                if (stockResult.isFailure) {
                    Log.e(TAG, "❌ 库存恢复失败: ${item.productName}, 错误=${stockResult.exceptionOrNull()?.message}")
                    throw RuntimeException("库存恢复失败：${item.productName}")
                }

                // 可选：记录作废操作
                stockService.recordVoidOperation(
                    productId = item.productId,
                    productName = item.productName,
                    locationId = bill.locationId,
                    locationName = "",
                    quantity = item.quantity,
                    billId = billId,
                    billNo = bill.billNo,
                    reason = "销售单删除恢复库存"
                )
            }

            // 3. 关键修复：使用Room的挂起版事务API（withTransaction）
            val deleteResult = withContext(Dispatchers.IO) {
                // withTransaction 是挂起函数，支持在内部调用其他挂起的DAO方法
                database.withTransaction {
                    // 删除销售单明细（调用suspend的DAO方法）
                    val itemsDeleted = database.saleItemDao().deleteByBillId(billId)
                    Log.d(TAG, "🗑️ 删除明细数量: $itemsDeleted")

                    // 删除销售单主表（调用suspend的DAO方法）
                    val billDeleted = database.saleBillDao().deleteSaleBillById(billId)
                    Log.d(TAG, "🗑️ 删除主表结果: $billDeleted")

                    // 返回是否删除成功
                    billDeleted > 0
                }
            }

            deleteResult
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除销售单数据库异常", e)
            false
        }
    }

    /**
     * 安全删除销售单（提供简单调用接口）
     */
    suspend fun safeDeleteBill(billId: Long): Boolean {
        val result = voidBillWithConfirmation(billId)
        return if (result.isSuccess) {
            result.getOrNull() ?: false
        } else {
            false
        }
    }
}