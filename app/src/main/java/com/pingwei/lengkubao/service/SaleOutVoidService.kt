package com.pingwei.lengkubao.service

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.SaleBill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

/**
 * 销售单删除服务：删除后已售量随 sale_item 移除自动恢复，不操作总库存 stock。
 */
class SaleOutVoidService(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "SaleOutVoidService"

    suspend fun voidBillWithConfirmation(billId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ 开始物理删除销售单: $billId")

                val bill = database.saleBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "❌ 销售单不存在: $billId")
                    return@withContext Result.failure(IllegalArgumentException("报账单不存在"))
                }

                Log.d(TAG, "📋 销售单信息: ${bill.billNo}, 客户=${bill.customerName}")

                val result = deleteSaleBill(billId)

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

    private suspend fun deleteSaleBill(billId: Long): Boolean {
        return try {
            val saleItems = database.saleItemDao().getItemsByBillId(billId)
            Log.d(TAG, "📦 找到 ${saleItems.size} 条销售明细，删除后客户可报账库存自动恢复")

            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val itemsDeleted = database.saleItemDao().deleteByBillId(billId)
                    Log.d(TAG, "🗑️ 删除明细数量: $itemsDeleted")

                    val billDeleted = database.saleBillDao().deleteSaleBillById(billId)
                    Log.d(TAG, "🗑️ 删除主表结果: $billDeleted")

                    billDeleted > 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除销售单数据库异常", e)
            false
        }
    }

    suspend fun safeDeleteBill(billId: Long): Boolean {
        val result = voidBillWithConfirmation(billId)
        return result.getOrNull() == true
    }
}
