package com.pingwei.lengkubao.utils

import android.content.Context
import com.google.gson.Gson
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.data.db.entity.PackagingType
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.data.db.entity.SyncLocalOpLog

object SyncOpLogHelper {
    private val gson = Gson()

    suspend fun enqueueCustomerUpsert(context: Context, customer: Customer) {
        enqueue(
            context = context,
            entityType = "CUSTOMER",
            entityKey = customer.customerNo,
            opType = "UPSERT",
            payload = mapOf(
                "code" to customer.customerNo,
                "name" to customer.customerName,
                "phone" to (customer.phone ?: ""),
                "customer_type" to customer.customerType,
                "enabled" to customer.enabled,
            ),
        )
    }

    suspend fun enqueueCustomerDelete(context: Context, customerNo: String) {
        enqueue(
            context = context,
            entityType = "CUSTOMER",
            entityKey = customerNo,
            opType = "DELETE",
            payload = mapOf("code" to customerNo),
        )
    }

    suspend fun enqueueLocationUpsert(context: Context, location: Location) {
        val key = location.locationName
        enqueue(
            context = context,
            entityType = "LOCATION",
            entityKey = key,
            opType = "UPSERT",
            payload = mapOf(
                "code" to key,
                "name" to location.locationName,
                "description" to location.description,
                "enabled" to location.enabled,
            ),
        )
    }

    suspend fun enqueueLocationDelete(context: Context, location: Location) {
        val key = location.locationName
        enqueue(
            context = context,
            entityType = "LOCATION",
            entityKey = key,
            opType = "DELETE",
            payload = mapOf(
                "code" to key,
                "name" to location.locationName,
            ),
        )
    }

    suspend fun enqueueOperatorUpsert(context: Context, operator: Operator) {
        enqueue(
            context = context,
            entityType = "OPERATOR",
            entityKey = operator.name,
            opType = "UPSERT",
            payload = mapOf(
                "code" to operator.name,
                "name" to operator.name,
                "enabled" to operator.enabled,
            ),
        )
    }

    suspend fun enqueueOperatorDelete(context: Context, operator: Operator) {
        enqueue(
            context = context,
            entityType = "OPERATOR",
            entityKey = operator.name,
            opType = "DELETE",
            payload = mapOf(
                "code" to operator.name,
                "name" to operator.name,
            ),
        )
    }

    suspend fun enqueueProductUpsert(context: Context, product: Product) {
        enqueue(
            context = context,
            entityType = "PRODUCT",
            entityKey = product.productNo,
            opType = "UPSERT",
            payload = mapOf(
                "code" to product.productNo,
                "name" to product.productName,
                "enabled" to product.enabled,
                "category" to product.category,
            ),
        )
    }

    suspend fun enqueueProductDelete(context: Context, product: Product) {
        enqueue(
            context = context,
            entityType = "PRODUCT",
            entityKey = product.productNo,
            opType = "DELETE",
            payload = mapOf(
                "code" to product.productNo,
                "name" to product.productName,
            ),
        )
    }

    suspend fun enqueuePackTypeUpsert(context: Context, packagingType: PackagingType) {
        enqueue(
            context = context,
            entityType = "PACK_TYPE",
            entityKey = packagingType.typeName,
            opType = "UPSERT",
            payload = mapOf(
                "code" to packagingType.typeName,
                "name" to packagingType.typeName,
                "enabled" to packagingType.enabled,
            ),
        )
    }

    suspend fun enqueuePackTypeDelete(context: Context, packagingType: PackagingType) {
        enqueue(
            context = context,
            entityType = "PACK_TYPE",
            entityKey = packagingType.typeName,
            opType = "DELETE",
            payload = mapOf(
                "code" to packagingType.typeName,
                "name" to packagingType.typeName,
            ),
        )
    }

    private suspend fun enqueue(
        context: Context,
        entityType: String,
        entityKey: String,
        opType: String,
        payload: Map<String, Any?>,
    ) {
        if (entityKey.isBlank()) return
        val syncDao = AppDatabase.getInstance(context).syncDao()
        if (syncDao.isSuppressLocalLog()) return

        val deviceId = resolveDeviceId(context)
        val now = System.currentTimeMillis()
        val originOpId = "${deviceId}_${entityType}_${entityKey}_${opType}_$now"
        syncDao.insertLocalOp(
            SyncLocalOpLog(
                entityType = entityType,
                entityKey = entityKey,
                opType = opType,
                payloadJson = gson.toJson(payload),
                originDeviceId = deviceId,
                originOpId = originOpId,
                createdAt = now,
            )
        )
        ConfigSyncStatusNotifier.notifyChanged()
    }

    private fun resolveDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("sync_config", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "HANDHELD_${System.currentTimeMillis()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }
}
