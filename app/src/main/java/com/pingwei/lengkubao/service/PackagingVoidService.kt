package com.pingwei.lengkubao.service

import android.content.Context
import com.pingwei.lengkubao.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.room.withTransaction

/**
 * 包装单删除服务
 * 处理包装单的物理删除逻辑
 */
class PackagingVoidService(
    private val context: Context,
    private val database: AppDatabase,
    private val stockService: StockService? = null // 包装单通常不涉及库存，但保留参数
) {
    private val TAG = "PackagingVoidService"

    /**
     * 物理删除包装单（带确认逻辑）
     */
    suspend fun voidBillWithConfirmation(billId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ 开始物理删除包装单: $billId")

                // 1. 检查单据是否存在
                val bill = database.packagingBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "❌ 包装单不存在: $billId")
                    return@withContext Result.failure(IllegalArgumentException("包装单不存在"))
                }

                // 2. 检查单据状态（如果是已作废的单据，可以提示用户）
                if (bill.isVoided) {
                    Log.w(TAG, "⚠️ 包装单 ${bill.billNo} 已作废")
                    // 继续删除，因为物理删除应该可以删除已作废的单据
                }

                // 3. 删除包装单（Room的外键约束会自动级联删除明细）
                val result = deletePackagingBill(billId)

                if (result) {
                    Log.d(TAG, "✅ 包装单物理删除成功: ${bill.billNo}")
                    Result.success(true)
                } else {
                    Log.e(TAG, "❌ 包装单物理删除失败: ${bill.billNo}")
                    Result.failure(RuntimeException("删除失败"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 删除包装单异常", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 执行包装单删除
     */
    private suspend fun deletePackagingBill(billId: Long): Boolean {
        return try {
            // 使用 Room 的 suspend 版本事务方法：withTransaction
            // 注意：需要确保 database 是 RoomDatabase 的子类实例，且导入 androidx.room.withTransaction
            database.withTransaction {
                // 1. 先删除包装单明细（显式删除，避免外键约束问题）
                database.packagingItemDao().deleteByBillId(billId)

                // 2. 再删除包装单主表
                val rowsDeleted = database.packagingBillDao().deleteBill(billId)

                // 返回是否删除成功（影响行数 > 0 表示删除成功）
                rowsDeleted > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除包装单数据库异常", e)
            false
        }
    }

    /**
     * 安全删除包装单（提供简单调用接口）
     */
    suspend fun safeDeleteBill(billId: Long): Boolean {
        return voidBillWithConfirmation(billId).getOrNull() ?: false
    }
}