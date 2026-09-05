package com.pingwei.lengkubao.service

import android.util.Log
import com.google.gson.JsonObject
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.data.db.dao.PreSaleBillDao
import com.pingwei.lengkubao.data.db.dao.PreSaleItemDao
import com.pingwei.lengkubao.data.db.dao.OutboundRecordDao
import com.pingwei.lengkubao.data.db.dao.OutboundRecordItemDao
import com.pingwei.lengkubao.data.db.dao.PaymentRecordDao
import com.pingwei.lengkubao.fiscal.FiscalYearManager
import java.text.SimpleDateFormat
import java.util.Locale

class PreSaleSyncApplier(
    private val database: AppDatabase,
    private val stockService: StockService,
) {
    private val TAG = "PreSaleSyncApplier"
    private val billDao: PreSaleBillDao = database.preSaleBillDao()
    private val itemDao: PreSaleItemDao = database.preSaleItemDao()
    private val paymentDao: PaymentRecordDao = database.paymentRecordDao()
    private val outboundDao: OutboundRecordDao = database.outboundRecordDao()
    private val outboundItemDao: OutboundRecordItemDao = database.outboundRecordItemDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun applyBillPayload(payload: JsonObject, commitSeq: Long): Boolean {
        if (shouldRejectFiscalYear(payload)) return false

        val sourceRecordId = payload.get("source_record_id")?.asString?.takeIf { it.isNotBlank() }
        val billNo = payload.get("bill_no")?.asString?.takeIf { it.isNotBlank() } ?: return false

        val existing = when {
            !sourceRecordId.isNullOrBlank() -> billDao.getBillBySourceRecordId(sourceRecordId)
            else -> billDao.getBillByBillNo(billNo)
        }

        if (existing != null && commitSeq > 0 && existing.remoteUpdatedAt >= commitSeq) {
            Log.d(TAG, "跳过旧预售单增量: $billNo seq=$commitSeq local=${existing.remoteUpdatedAt}")
            return false
        }

        val remoteSaleMode = payload.get("sale_mode")?.asString ?: existing?.saleMode ?: PreSaleMode.PRESALE
        if (existing != null &&
            existing.saleMode == PreSaleMode.DIRECT_OUT &&
            remoteSaleMode == PreSaleMode.PRESALE
        ) {
            Log.w(TAG, "忽略已售→预售回退: $billNo")
            return false
        }

        var remoteStatus = payload.get("bill_status")?.asString
            ?: payload.get("status")?.asString
            ?: existing?.status
            ?: PreSaleStatus.PRESALE

        if (remoteSaleMode == PreSaleMode.DIRECT_OUT && remoteStatus == PreSaleStatus.PRESALE) {
            remoteStatus = PreSaleStatus.COMPLETED
        }

        val oldStatus = existing?.status
        val oldSaleMode = existing?.saleMode
        val existingItems = existing?.let { itemDao.getItemsByBillId(it.id) } ?: emptyList()
        val outboundShippedByProduct = existing?.let { buildOutboundShippedMap(it.id) } ?: emptyMap()
        val items = parseItems(
            payload = payload,
            billId = existing?.id ?: 0L,
            existingItems = existingItems,
            outboundShippedByProduct = outboundShippedByProduct,
        )
        val bill = buildBill(
            payload = payload,
            existing = existing,
            items = items,
            remoteStatus = remoteStatus,
            remoteSaleMode = remoteSaleMode,
            sourceRecordId = sourceRecordId,
            commitSeq = commitSeq,
        )
        val billId = if (existing == null) {
            billDao.insert(bill)
        } else {
            billDao.update(bill.copy(id = existing.id))
            existing.id
        }

        val itemsWithBillId = items.map { it.copy(billId = billId) }
        if (existing == null) {
            if (itemsWithBillId.isNotEmpty()) {
                itemDao.insertAll(itemsWithBillId)
            }
        } else {
            upsertItemsForBill(billId, itemsWithBillId, existingItems)
        }

        applyStockTransition(
            oldStatus = oldStatus,
            newStatus = bill.status,
            oldSaleMode = oldSaleMode,
            saleMode = bill.saleMode,
            items = itemsWithBillId,
            locationId = bill.locationId,
            locationName = bill.locationName,
            billId = billId,
            billNo = bill.billNo,
        )
        Log.i(TAG, "已应用远程预售单: ${bill.billNo} mode=${bill.saleMode} status=${bill.status} seq=$commitSeq")
        return true
    }

    suspend fun applyPaymentPayload(payload: JsonObject, commitSeq: Long): Boolean {
        if (shouldRejectFiscalYear(payload)) return false

        val sourceRecordId = payload.get("source_record_id")?.asString?.takeIf { it.isNotBlank() }
        if (!sourceRecordId.isNullOrBlank() && paymentDao.getBySourceRecordId(sourceRecordId) != null) {
            Log.d(TAG, "跳过重复预售收款: $sourceRecordId")
            return false
        }

        val billNo = payload.get("bill_no")?.asString?.takeIf { it.isNotBlank() } ?: return false
        val bill = billDao.getBillByBillNo(billNo) ?: run {
            Log.w(TAG, "预售收款找不到关联单据: $billNo")
            return false
        }

        if (commitSeq > 0 && bill.remoteUpdatedAt >= commitSeq) {
            return false
        }

        val amount = payload.get("amount")?.asString?.toDoubleOrNull()
            ?: payload.get("amount")?.asDouble
            ?: return false
        val payMethod = payload.get("pay_method")?.asString ?: PayMethod.OTHER
        val payTimeRaw = payload.get("pay_time")?.asString
        val payTime = parsePayTime(payTimeRaw)
        val remark = payload.get("remark")?.asString ?: ""
        val sourceDeviceId = payload.get("source_device_id")?.asString

        val paymentId = paymentDao.insert(
            PaymentRecord(
                billId = bill.id,
                amount = amount,
                payMethod = payMethod,
                payTime = payTime,
                remark = remark,
                syncStatus = 1,
                sourceRecordId = sourceRecordId,
                sourceDeviceId = sourceDeviceId,
            )
        )
        if (!sourceRecordId.isNullOrBlank() && sourceDeviceId != null) {
            paymentDao.updateSourceIdentity(paymentId, sourceRecordId, sourceDeviceId)
        }

        val totalPaid = paymentDao.getTotalPaidByBillId(bill.id)
        billDao.updatePaidAmount(bill.id, totalPaid)
        if (commitSeq > 0) {
            billDao.updateRemoteUpdatedAt(bill.id, commitSeq)
        }
        Log.i(TAG, "已应用远程预售收款: $billNo amount=$amount")
        return true
    }

    suspend fun applyOutboundPayload(payload: JsonObject, commitSeq: Long): Boolean {
        if (shouldRejectFiscalYear(payload)) return false

        val sourceRecordId = payload.get("source_record_id")?.asString?.takeIf { it.isNotBlank() }
        if (!sourceRecordId.isNullOrBlank() && outboundDao.getBySourceRecordId(sourceRecordId) != null) {
            Log.d(TAG, "跳过重复预售出库: $sourceRecordId")
            return false
        }

        val billNo = payload.get("bill_no")?.asString?.takeIf { it.isNotBlank() } ?: return false
        val bill = billDao.getBillByBillNo(billNo) ?: run {
            Log.w(TAG, "预售出库找不到关联单据: $billNo")
            return false
        }

        if (commitSeq > 0 && bill.remoteUpdatedAt >= commitSeq) {
            return false
        }

        val shipTimeRaw = payload.get("ship_time")?.asString
        val shipTime = parsePayTime(shipTimeRaw)
        val remark = payload.get("remark")?.asString ?: ""
        val sourceDeviceId = payload.get("source_device_id")?.asString
        val remoteStatus = payload.get("bill_status")?.asString
        val remoteSaleMode = payload.get("sale_mode")?.asString

        val billItems = itemDao.getItemsByBillId(bill.id)
        val recordItems = parseOutboundItems(payload, billItems)
        if (recordItems.isEmpty()) return false

        val recordId = outboundDao.insert(
            OutboundRecord(
                billId = bill.id,
                shipTime = shipTime,
                remark = remark,
                syncStatus = 1,
                sourceRecordId = sourceRecordId,
                sourceDeviceId = sourceDeviceId,
            )
        )
        outboundItemDao.insertAll(recordItems.map { it.copy(outboundRecordId = recordId) })

        for (recordItem in recordItems) {
            val billItem = billItems.find { it.itemId == recordItem.billItemId }
                ?: billItems.find { it.productNo == recordItem.productNo || it.productName == recordItem.productName }
            if (billItem != null) {
                itemDao.updateShippedQuantity(
                    billItem.itemId,
                    billItem.shippedQuantity + recordItem.quantity
                )
                stockService.confirmSaleDeduction(
                    productId = recordItem.productId,
                    productName = recordItem.productName,
                    locationId = bill.locationId,
                    locationName = bill.locationName,
                    quantity = recordItem.quantity,
                    billId = bill.id,
                    billNo = bill.billNo,
                    billType = BillType.PRESALE
                )
            }
        }

        if (!remoteSaleMode.isNullOrBlank() && !remoteStatus.isNullOrBlank()) {
            billDao.updateSaleModeAndStatus(bill.id, remoteSaleMode, remoteStatus)
        } else {
            val updatedItems = itemDao.getItemsByBillId(bill.id)
            val fullyShipped = updatedItems.all { it.shippedQuantity >= it.quantity }
            if (fullyShipped) {
                billDao.updateSaleModeAndStatus(bill.id, PreSaleMode.DIRECT_OUT, PreSaleStatus.COMPLETED)
            } else {
                billDao.updateStatus(bill.id, PreSaleStatus.SHIPPED)
            }
        }

        if (commitSeq > 0) {
            billDao.updateRemoteUpdatedAt(bill.id, commitSeq)
        }
        Log.i(TAG, "已应用远程预售出库: $billNo recordId=$recordId")
        return true
    }

    private fun parseOutboundItems(
        payload: JsonObject,
        billItems: List<PreSaleItem>
    ): List<OutboundRecordItem> {
        val itemsArray = payload.getAsJsonArray("items") ?: return emptyList()
        val result = mutableListOf<OutboundRecordItem>()
        for (element in itemsArray) {
            val obj = element.asJsonObject
            val spec = obj.get("spec")?.asString ?: continue
            val productNo = obj.get("product_no")?.asString ?: ""
            val quantity = obj.get("quantity")?.asString?.toIntOrNull()
                ?: obj.get("quantity")?.asInt
                ?: continue
            if (quantity <= 0) continue
            val unit = obj.get("unit")?.asString ?: "箱"
            val billItem = billItems.find { it.productName == spec || it.productNo == productNo }
                ?: continue
            result.add(
                OutboundRecordItem(
                    billItemId = billItem.itemId,
                    productId = billItem.productId,
                    productNo = billItem.productNo,
                    productName = billItem.productName,
                    quantity = quantity,
                    unit = unit,
                )
            )
        }
        return result
    }

    private suspend fun buildBill(
        payload: JsonObject,
        existing: PreSaleBill?,
        items: List<PreSaleItem>,
        remoteStatus: String,
        remoteSaleMode: String,
        sourceRecordId: String?,
        commitSeq: Long,
    ): PreSaleBill {
        val buyerNo = payload.get("buyer_code")?.asString ?: existing?.buyerNo ?: ""
        val buyerName = payload.get("buyer_name")?.asString ?: existing?.buyerName ?: ""
        val locationName = payload.get("location")?.asString ?: existing?.locationName ?: ""
        val totalAmount = payload.get("total_amount")?.asString?.toDoubleOrNull()
            ?: payload.get("total_amount")?.asDouble
            ?: items.sumOf { it.amount }
        val paidAmount = payload.get("paid_amount")?.asString?.toDoubleOrNull()
            ?: payload.get("paid_amount")?.asDouble
            ?: existing?.paidAmount
            ?: 0.0
        val operatorName = payload.get("handler")?.asString ?: existing?.operatorName ?: ""
        val remark = payload.get("remark")?.asString ?: existing?.remark ?: ""
        val dateStr = payload.get("date")?.asString
        val createTime = existing?.createTime ?: parseBillDate(dateStr)
        val sourceDeviceId = payload.get("source_device_id")?.asString ?: existing?.sourceDeviceId

        val buyer = database.customerDao().getByCustomerNo(buyerNo)
        val location = database.locationDao().getByLocationName(locationName)
            ?: existing?.let { database.locationDao().getLocationById(it.locationId) }
        val operator = database.operatorDao().getByOperatorName(operatorName)
            ?: existing?.let { database.operatorDao().getOperatorById(it.operatorId) }

        return PreSaleBill(
            id = existing?.id ?: 0,
            billNo = payload.get("bill_no")?.asString ?: existing?.billNo ?: "",
            buyerNo = buyerNo,
            buyerName = buyerName,
            buyerId = buyer?.id ?: existing?.buyerId ?: 0,
            locationId = location?.id ?: existing?.locationId ?: 0,
            locationName = locationName,
            operatorId = operator?.id ?: existing?.operatorId ?: 0,
            operatorName = operatorName,
            saleMode = remoteSaleMode,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            status = remoteStatus,
            remark = remark,
            createTime = createTime,
            syncStatus = 1,
            sourceRecordId = sourceRecordId ?: existing?.sourceRecordId,
            sourceDeviceId = sourceDeviceId,
            remoteUpdatedAt = if (commitSeq > 0) commitSeq else existing?.remoteUpdatedAt ?: 0L,
        )
    }

    private suspend fun parseItems(
        payload: JsonObject,
        billId: Long,
        existingItems: List<PreSaleItem> = emptyList(),
        outboundShippedByProduct: Map<String, Int> = emptyMap(),
    ): List<PreSaleItem> {
        val itemsArray = payload.getAsJsonArray("items") ?: return emptyList()
        val products = database.productDao().getAll()
        val result = mutableListOf<PreSaleItem>()
        for (element in itemsArray) {
            val itemObj = element.asJsonObject
            val spec = itemObj.get("spec")?.asString ?: continue
            val quantity = itemObj.get("quantity")?.asString?.toIntOrNull()
                ?: itemObj.get("quantity")?.asInt
                ?: 0
            if (quantity <= 0) continue
            val unitPrice = itemObj.get("unit_price")?.asString?.toDoubleOrNull()
                ?: itemObj.get("unit_price")?.asDouble
                ?: 0.0
            val amount = itemObj.get("total_amount")?.asString?.toDoubleOrNull()
                ?: itemObj.get("total_amount")?.asDouble
                ?: quantity * unitPrice
            val product = products.find { it.productName == spec }
                ?: products.find { it.productName.equals(spec, ignoreCase = true) }
            if (product == null) {
                Log.w(TAG, "远程预售明细未匹配商品: $spec")
                continue
            }
            val hasShippedInPayload = itemObj.has("shipped_quantity") &&
                !itemObj.get("shipped_quantity").isJsonNull
            val parsedShipped = if (hasShippedInPayload) {
                itemObj.get("shipped_quantity")?.asString?.toIntOrNull()
                    ?: itemObj.get("shipped_quantity")?.asInt
            } else {
                null
            }
            val localShipped = existingItems
                .find { it.productNo == product.productNo || it.productName == product.productName }
                ?.shippedQuantity ?: 0
            val outboundShipped = outboundShippedByProduct[product.productNo]
                ?: outboundShippedByProduct[product.productName]
                ?: outboundShippedByProduct[spec]
                ?: 0
            val shippedQuantity = if (hasShippedInPayload) {
                maxOf(parsedShipped ?: 0, localShipped, outboundShipped)
            } else {
                maxOf(localShipped, outboundShipped)
            }.coerceIn(0, quantity)
            result.add(
                PreSaleItem(
                    billId = billId,
                    productId = product.id,
                    productNo = product.productNo,
                    productName = product.productName,
                    quantity = quantity,
                    shippedQuantity = shippedQuantity,
                    salePrice = unitPrice,
                    amount = amount,
                    unit = product.unit,
                )
            )
        }
        return result
    }

    private suspend fun upsertItemsForBill(
        billId: Long,
        newItems: List<PreSaleItem>,
        existingItems: List<PreSaleItem>,
    ) {
        if (newItems.isEmpty()) {
            if (existingItems.isNotEmpty()) {
                itemDao.deleteByBillId(billId)
            }
            return
        }

        val merged = newItems.map { newItem ->
            val existing = findMatchingItem(existingItems, newItem)
            PreSaleItem(
                billId = billId,
                productId = newItem.productId,
                productNo = newItem.productNo,
                productName = newItem.productName,
                quantity = newItem.quantity,
                shippedQuantity = newItem.shippedQuantity,
                salePrice = newItem.salePrice,
                amount = newItem.amount,
                unit = newItem.unit,
                remark = newItem.remark,
            ).apply {
                itemId = existing?.itemId ?: 0
            }
        }

        itemDao.insertAll(merged)

        val keptProductNos = merged.map { it.productNo }.toSet()
        existingItems
            .filter { it.productNo !in keptProductNos }
            .forEach { itemDao.deleteById(it.itemId) }
    }

    private fun findMatchingItem(existingItems: List<PreSaleItem>, newItem: PreSaleItem): PreSaleItem? {
        return existingItems.find { it.productNo == newItem.productNo }
            ?: existingItems.find { it.productName == newItem.productName }
    }

    private suspend fun buildOutboundShippedMap(billId: Long): Map<String, Int> {
        val totals = mutableMapOf<String, Int>()
        outboundItemDao.getAllItemsByBillId(billId).forEach { item ->
            listOf(item.productNo, item.productName).forEach { key ->
                if (key.isNotBlank()) {
                    totals[key] = (totals[key] ?: 0) + item.quantity
                }
            }
        }
        return totals
    }

    private suspend fun applyStockTransition(
        oldStatus: String?,
        newStatus: String,
        oldSaleMode: String?,
        saleMode: String,
        items: List<PreSaleItem>,
        locationId: Long,
        locationName: String,
        billId: Long,
        billNo: String,
    ) {
        if (items.isEmpty() || locationId <= 0) return

        val wasReserved = oldStatus == PreSaleStatus.PRESALE || oldStatus == PreSaleStatus.SHIPPED
        val modeChangedToDirectOut = oldSaleMode == PreSaleMode.PRESALE &&
            saleMode == PreSaleMode.DIRECT_OUT &&
            oldSaleMode != saleMode

        fun remainingItems(): List<PreSaleItem> {
            return items.mapNotNull { item ->
                val remaining = item.quantity - item.shippedQuantity
                if (remaining > 0) item.copy(quantity = remaining) else null
            }
        }

        when {
            oldStatus == null && newStatus == PreSaleStatus.PRESALE && saleMode == PreSaleMode.PRESALE -> {
                stockService.reserveMultipleForPreSale(items, locationId, locationName, billId, billNo)
            }
            oldStatus == null && (newStatus == PreSaleStatus.COMPLETED || saleMode == PreSaleMode.DIRECT_OUT) -> {
                stockService.reserveMultipleForPreSale(items, locationId, locationName, billId, billNo)
                val toDeduct = remainingItems().ifEmpty { items }
                stockService.confirmMultipleDeductionsForPreSale(toDeduct, locationId, locationName, billId, billNo)
            }
            wasReserved && modeChangedToDirectOut -> {
                val toDeduct = remainingItems()
                if (toDeduct.isNotEmpty()) {
                    stockService.confirmMultipleDeductionsForPreSale(toDeduct, locationId, locationName, billId, billNo)
                }
            }
            wasReserved && newStatus == PreSaleStatus.COMPLETED -> {
                val toDeduct = remainingItems()
                if (toDeduct.isNotEmpty()) {
                    stockService.confirmMultipleDeductionsForPreSale(toDeduct, locationId, locationName, billId, billNo)
                }
            }
            wasReserved && newStatus == PreSaleStatus.CANCELLED -> {
                val toRelease = remainingItems().ifEmpty { items }
                stockService.releaseMultipleReservationsForPreSale(toRelease, locationId, locationName, billId, billNo)
            }
        }
    }

    private fun shouldRejectFiscalYear(payload: JsonObject): Boolean {
        if (!FiscalYearManager.isInitialized) return false
        val fiscalYear = payload.get("fiscal_year")?.let { element ->
            when {
                element.isJsonNull -> null
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asInt
                element.isJsonPrimitive -> element.asString.toIntOrNull()
                else -> null
            }
        } ?: return false
        if (fiscalYear != FiscalYearManager.activeYear) {
            Log.w(
                TAG,
                "跳过跨年预售下行: fiscal_year=$fiscalYear local=${FiscalYearManager.activeYear}",
            )
            return true
        }
        return false
    }

    private fun parseBillDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis() }
            .getOrDefault(System.currentTimeMillis())
    }

    private fun parsePayTime(payTimeRaw: String?): Long {
        if (payTimeRaw.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(payTimeRaw)?.time
                ?: dateFormat.parse(payTimeRaw)?.time
                ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }
}
