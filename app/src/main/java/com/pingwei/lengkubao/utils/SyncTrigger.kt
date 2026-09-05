// File: utils/SyncTrigger.kt
package com.pingwei.lengkubao.utils

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.service.TcpSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            Log.i(TAG, "📡 触发实时同步: $billType, ID=$billId")
            TcpSyncService.syncBillNow(context, billId, billType)
        } else {
            Log.d(TAG, "自动同步未开启，标记单据为未同步: $billType, ID=$billId")
            markBillAsUnsynced(context, billId, billType)
        }
    }

    fun triggerInStockSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "IN_STOCK")
    }

    fun triggerSaleSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "SALE")
    }

    fun triggerPackagingSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "PACKAGING")
    }

    fun triggerPreSaleSync(context: Context, billId: Long) {
        triggerBillSync(context, billId, "PRESALE")
    }

    fun triggerPreSalePaymentSync(context: Context, paymentId: Long) {
        triggerBillSync(context, paymentId, "PRESALE_PAYMENT")
    }

    fun triggerPreSaleOutboundSync(context: Context, outboundId: Long) {
        triggerBillSync(context, outboundId, "PRESALE_OUTBOUND")
    }

    /**
     * 触发基础配置同步：写入待上传 oplog，并在已连接时推送
     */
    fun triggerConfigSync(context: Context, configType: String, configId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                when (configType) {
                    "CUSTOMER" -> {
                        database.customerDao().getCustomerById(configId)?.let {
                            SyncOpLogHelper.enqueueCustomerUpsert(context, it)
                        }
                    }
                    "LOCATION" -> {
                        database.locationDao().getLocationById(configId)?.let {
                            SyncOpLogHelper.enqueueLocationUpsert(context, it)
                        }
                    }
                    "OPERATOR" -> {
                        database.operatorDao().getOperatorById(configId)?.let {
                            SyncOpLogHelper.enqueueOperatorUpsert(context, it)
                        }
                    }
                    "PRODUCT" -> {
                        database.productDao().getProductById(configId)?.let {
                            SyncOpLogHelper.enqueueProductUpsert(context, it)
                        }
                    }
                    "PACK_TYPE", "PACKAGING" -> {
                        database.packagingTypeDao().getById(configId)?.let {
                            SyncOpLogHelper.enqueuePackTypeUpsert(context, it)
                        }
                    }
                    else -> Log.e(TAG, "未知配置类型: $configType")
                }
                if (shouldAutoSync(context)) {
                    TcpSyncService.pushPendingConfigOps(context)
                } else {
                    markConfigAsUnsynced(context, configId, configType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "触发配置同步失败: ${e.message}", e)
            }
        }
    }

    fun triggerCustomerDelete(context: Context, customerNo: String) {
        Log.i(TAG, "忽略手持端配置删除: CUSTOMER/$customerNo（删除以 PC 为准）")
    }

    fun triggerLocationDelete(context: Context, location: com.pingwei.lengkubao.data.db.entity.Location) {
        Log.i(TAG, "忽略手持端配置删除: LOCATION/${location.locationName}（删除以 PC 为准）")
    }

    fun triggerOperatorDelete(context: Context, operator: com.pingwei.lengkubao.data.db.entity.Operator) {
        Log.i(TAG, "忽略手持端配置删除: OPERATOR/${operator.name}（删除以 PC 为准）")
    }

    fun triggerProductSync(context: Context, productId: Long) {
        triggerConfigSync(context, "PRODUCT", productId)
    }

    fun triggerPackTypeSync(context: Context, packTypeId: Long) {
        triggerConfigSync(context, "PACK_TYPE", packTypeId)
    }

    fun triggerProductDelete(context: Context, product: com.pingwei.lengkubao.data.db.entity.Product) {
        Log.i(TAG, "忽略手持端配置删除: PRODUCT/${product.productNo}（删除以 PC 为准）")
    }

    fun triggerPackTypeDelete(context: Context, packagingType: com.pingwei.lengkubao.data.db.entity.PackagingType) {
        Log.i(TAG, "忽略手持端配置删除: PACK_TYPE/${packagingType.typeName}（删除以 PC 为准）")
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

    private fun shouldAutoSync(context: Context): Boolean {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        return prefs.getBoolean(Constant.PREF_AUTO_SYNC, Constant.PREF_AUTO_SYNC_DEFAULT)
    }

    private fun markBillAsUnsynced(context: Context, billId: Long, billType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                when (billType) {
                    "IN_STOCK" -> database.inStockBillDao().updateSyncStatus(billId, 0)
                    "SALE" -> database.saleBillDao().updateSyncStatus(billId, 0)
                    "PACKAGING" -> database.packagingBillDao().updateSyncStatus(billId, false)
                    "PRESALE" -> database.preSaleBillDao().updateSyncStatus(billId, 0)
                    "PRESALE_PAYMENT" -> database.paymentRecordDao().updateSyncStatus(billId, 0)
                    "PRESALE_OUTBOUND" -> database.outboundRecordDao().updateSyncStatus(billId, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记未同步状态失败: ${e.message}", e)
            }
        }
    }

    private fun markConfigAsUnsynced(context: Context, configId: Long, configType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                when (configType) {
                    "PRODUCT" -> database.productDao().updateSyncStatus(configId, 0)
                    "LOCATION" -> database.locationDao().updateSyncStatus(configId, 0)
                    "OPERATOR" -> database.operatorDao().updateSyncStatus(configId, 0)
                    "CUSTOMER" -> database.customerDao().updateSyncStatus(configId, 0)
                    "PACK_TYPE" -> { /* 包装类型无 syncStatus，未同步由 oplog 统计 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记配置未同步状态失败: ${e.message}", e)
            }
        }
    }
}
