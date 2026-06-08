// File: utils/SyncTrigger.kt
package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 同步触发器
 */
object SyncTrigger {
    private const val TAG = "SyncTrigger"

    /**
     * 单据保存后触发同步
     */
    fun triggerBillSync(context: Context, billId: Long, billType: String) {
        if (shouldAutoSync(context)) {
            // 立即通过服务同步
            Log.i(TAG, "📡 触发实时同步: $billType, ID=$billId")
            TcpSyncService.syncBillNow(context, billId, billType)
        } else {
            // 标记为未同步状态
            Log.d(TAG, "自动同步未开启，标记单据为未同步: $billType, ID=$billId")
            markBillAsUnsynced(context, billId, billType)
        }
    }

    /**
     * 入库单保存
     */
    fun triggerInStockSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "IN_STOCK")
    }

    /**
     * 销售单保存
     */
    fun triggerSaleSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "SALE")
    }

    /**
     * 包装单保存
     */
    fun triggerPackagingSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "PACKAGING")
    }

    /**
     * 触发基础配置同步
     */
    fun triggerConfigSync(context: Context, configType: String, configId: Long) {
        if (shouldAutoSync(context)) {
            Log.i(TAG, "📡 触发基础配置实时同步: $configType, ID=$configId")
            // 需要扩展 TcpSyncService 支持基础配置
            TcpSyncService.syncConfigNow(context, configId, configType)
        } else {
            Log.d(TAG, "自动同步未开启，标记配置为未同步: $configType, ID=$configId")
            // 为基础配置表添加同步状态标记
            markConfigAsUnsynced(context, configId, configType)
        }
    }

    // 便捷方法
    fun triggerProductSync(context: Context, productId: Long) {
        triggerConfigSync(context, "PRODUCT", productId)
    }

    fun triggerLocationSync(context: Context, locationId: Long) {
        triggerConfigSync(context, "LOCATION", locationId)
    }

    fun triggerOperatorSync(context: Context, operatorId: Long) {
        triggerConfigSync(context, "OPERATOR", operatorId)
    }

    fun triggerCustomerSync(context: Context, customerId: Long) {
        triggerConfigSync(context, "CUSTOMER", customerId)
    }

    /**
     * 检查是否启用自动同步
     */
    private fun shouldAutoSync(context: Context): Boolean {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_sync", false)
    }

    /**
     * 标记单据为未同步状态
     */
    private fun markBillAsUnsynced(context: Context, billId: Long, billType: String) {
        GlobalScope.launch {
            try {
                val database = AppDatabase.getInstance(context)
                when (billType) {
                    "IN_STOCK" -> {
                        database.inStockBillDao().updateSyncStatus(billId, 0)
                        Log.d(TAG, "入库单标记为未同步: $billId")
                    }
                    "SALE" -> {
                        database.saleBillDao().updateSyncStatus(billId, 0)
                        Log.d(TAG, "销售单标记为未同步: $billId")
                    }
                    "PACKAGING" -> {
                        database.packagingBillDao().updateSyncStatus(billId, false)
                        Log.d(TAG, "包装单标记为未同步: $billId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记未同步状态失败: ${e.message}", e)
            }
        }
    }

    /**
     * 标记基础配置为未同步状态
     */
    private fun markConfigAsUnsynced(context: Context, configId: Long, configType: String) {
        GlobalScope.launch {
            try {
                val database = AppDatabase.getInstance(context)
                when (configType) {
                    "PRODUCT" -> {
                        // 需要先在 ProductDao 中添加 updateSyncStatus 方法
                        // database.productDao().updateSyncStatus(configId, 0)
                        Log.d(TAG, "商品标记为未同步: $configId (需要实现DAO方法)")
                    }
                    "LOCATION" -> {
                        // 需要先在 LocationDao 中添加 updateSyncStatus 方法
                        // database.locationDao().updateSyncStatus(configId, 0)
                        Log.d(TAG, "库位标记为未同步: $configId (需要实现DAO方法)")
                    }
                    "OPERATOR" -> {
                        // 需要先在 OperatorDao 中添加 updateSyncStatus 方法
                        // database.operatorDao().updateSyncStatus(configId, 0)
                        Log.d(TAG, "经手人标记为未同步: $configId (需要实现DAO方法)")
                    }
                    "CUSTOMER" -> {
                        // 需要先在 CustomerDao 中添加 updateSyncStatus 方法
                        // database.customerDao().updateSyncStatus(configId, 0)
                        Log.d(TAG, "客户标记为未同步: $configId (需要实现DAO方法)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记配置未同步状态失败: ${e.message}", e)
            }
        }
    }
}