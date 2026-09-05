package com.pingwei.lengkubao.service

import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.CustomerInboundStock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CustomerInboundStockBackfill(
    private val database: AppDatabase
) {
    private val TAG = "CustomerInboundBackfill"

    suspend fun runIfNeeded() = withContext(Dispatchers.IO) {
        val dao = database.customerInboundStockDao()
        if (dao.count() > 0) return@withContext
        Log.i(TAG, "开始从 PC 快照回填客户入库池...")
        refreshFromPcSnapshots()
        Log.i(TAG, "客户入库池回填完成，行数=${dao.count()}")
    }

    suspend fun refreshFromPcSnapshots() = withContext(Dispatchers.IO) {
        val snapshots = database.pcInboundDailySnapshotDao().getAll()
        if (snapshots.isEmpty()) return@withContext

        val products = database.productDao().getAll()
        val locations = database.locationDao().getAllSimple()
        val productByName = products.groupBy { it.productName.trim() }
        val locationByName = locations.associateBy { it.locationName.trim() }

        val grouped = snapshots.groupBy { "${it.customerNo}||${it.locationName.trim()}||${it.spec.trim()}" }
        val dao = database.customerInboundStockDao()
        val now = System.currentTimeMillis()

        grouped.forEach { (_, rows) ->
            val first = rows.first()
            val location = locationByName[first.locationName.trim()] ?: run {
                Log.w(TAG, "跳过未匹配库位: ${first.locationName}")
                return@forEach
            }
            val product = productByName[first.spec.trim()]?.firstOrNull() ?: run {
                Log.w(TAG, "跳过未匹配型号: ${first.spec}")
                return@forEach
            }
            val totalQty = rows.sumOf { it.quantity }
            val key = StockKey(first.customerNo, location.id, product.id)

            val existing = dao.getRow(key.customerNo, key.locationId, key.productId)
            if (existing != null) {
                dao.setInboundQuantity(key.customerNo, key.locationId, key.productId, totalQty, now)
            } else {
                dao.upsert(
                    CustomerInboundStock(
                        customerNo = first.customerNo,
                        customerName = first.customerName,
                        locationId = location.id,
                        locationName = location.locationName,
                        productId = product.id,
                        productNo = product.productNo,
                        productName = product.productName,
                        inboundQuantity = totalQty,
                        reservedQuantity = 0,
                        lastUpdated = now
                    )
                )
            }
        }
    }

    private data class StockKey(val customerNo: String, val locationId: Long, val productId: Long)
}
