// 新建文件：utils/SyncStatusUtils.kt
package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object SyncStatusUtils {
    private const val TAG = "SyncStatusUtils"

    data class ResetResult(
        val inStockCount: Int,
        val saleCount: Int,
        val packagingCount: Int,
        val advanceCount: Int,
        val deductionCount: Int
    ) {
        val totalCount: Int
            get() = inStockCount + saleCount + packagingCount + advanceCount + deductionCount
    }

    /**
     * 获取未同步单据数量
     */
    suspend fun getPendingSyncCount(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(context)

            val pendingIn = database.inStockBillDao().getAllBills().first().count { it.syncStatus == 0 }
            val pendingSale = database.saleBillDao().getAllBills().first().count { it.syncStatus == 0 }
            val pendingPack = database.packagingBillDao().getAllBills().first().count { !it.isSynced }

            val total = pendingIn + pendingSale + pendingPack
            Log.d(TAG, "📊 未同步单据统计：入库单=$pendingIn，销售单=$pendingSale，包装单=$pendingPack，总计=$total")

            return@withContext total
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取未同步单据数量失败", e)
            return@withContext 0
        }
    }

    /**
     * 检查是否有未同步单据
     */
    suspend fun hasPendingSync(context: Context): Boolean {
        return getPendingSyncCount(context) > 0
    }

    /**
     * 获取未同步单据详细统计
     */
    suspend fun getPendingSyncDetails(context: Context): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(context)

            val pendingIn = database.inStockBillDao().getAllBills().first().count { it.syncStatus == 0 }
            val pendingSale = database.saleBillDao().getAllBills().first().count { it.syncStatus == 0 }
            val pendingPack = database.packagingBillDao().getAllBills().first().count { !it.isSynced }

            return@withContext mapOf(
                "入库单" to pendingIn,
                "销售单" to pendingSale,
                "包装单" to pendingPack,
                "总计" to (pendingIn + pendingSale + pendingPack)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取未同步单据详情失败", e)
            return@withContext emptyMap()
        }
    }

    /**
     * 按创建时间范围重置入库/销售/包装单及预支、扣款同步状态。
     */
    suspend fun resetBillSyncStatusByTimeRange(
        context: Context,
        startTime: Long,
        endTime: Long
    ): ResetResult = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(context)
            val inStockCount = database.inStockBillDao()
                .resetSyncStatusByCreateTimeRange(startTime, endTime)
            val saleCount = database.saleBillDao()
                .resetSyncStatusByCreateTimeRange(startTime, endTime)
            val packagingCount = database.packagingBillDao()
                .resetSyncStatusByCreateTimeRange(startTime, endTime)
            val advanceCount = database.advanceDao()
                .resetSyncStatusByCreateTimeRange(startTime, endTime)
            val deductionCount = database.deductionDao()
                .resetSyncStatusByCreateTimeRange(startTime, endTime)

            val result = ResetResult(
                inStockCount = inStockCount,
                saleCount = saleCount,
                packagingCount = packagingCount,
                advanceCount = advanceCount,
                deductionCount = deductionCount
            )
            Log.i(
                TAG,
                "✅ 同步状态重置完成: 入库=${result.inStockCount}, 销售=${result.saleCount}, 包装=${result.packagingCount}, 预支=${result.advanceCount}, 扣款=${result.deductionCount}, 总计=${result.totalCount}"
            )
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置同步状态失败", e)
            return@withContext ResetResult(0, 0, 0, 0, 0)
        }
    }
}