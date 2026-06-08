package com.pingwei.lengkubao.service

import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.utils.PrintUtils.generateBillNo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreSaleService(
    private val database: AppDatabase,
    private val stockService: StockService
) {
    private val TAG = "PreSaleService"

    suspend fun saveBill(
        buyer: Customer,
        location: Location,
        operator: Operator,
        items: List<PreSaleItem>,
        saleMode: String,
        remark: String,
        initialPayment: Double = 0.0,
        payMethod: String = PayMethod.CASH
    ): Result<Pair<Long, String>> = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) return@withContext Result.failure(IllegalStateException("请添加商品"))
            for (item in items) {
                if (item.quantity <= 0) {
                    return@withContext Result.failure(IllegalStateException("商品数量必须大于0"))
                }
                val available = stockService.getAvailableStock(item.productId, location.id)
                if (item.quantity > available) {
                    return@withContext Result.failure(
                        IllegalStateException("${item.productName} 库存不足，可用: $available")
                    )
                }
            }

            val billNo = generateBillNo("YS")
            val totalAmount = items.sumOf { it.amount }
            val totalQuantity = items.sumOf { it.quantity }

            val reserveResult = stockService.reserveMultipleForPreSale(
                items = items,
                locationId = location.id,
                locationName = location.locationName,
                billId = 0,
                billNo = billNo
            )
            if (reserveResult.isFailure) {
                return@withContext Result.failure(
                    reserveResult.exceptionOrNull() ?: IllegalStateException("库存预留失败")
                )
            }

            val initialStatus = when (saleMode) {
                PreSaleMode.DIRECT_OUT -> PreSaleStatus.COMPLETED
                else -> PreSaleStatus.PRESALE
            }

            val bill = PreSaleBill(
                billNo = billNo,
                buyerNo = buyer.customerNo,
                buyerName = buyer.customerName,
                buyerId = buyer.id,
                locationId = location.id,
                locationName = location.locationName,
                operatorId = operator.id,
                operatorName = operator.name,
                saleMode = saleMode,
                totalAmount = totalAmount,
                paidAmount = 0.0,
                status = initialStatus,
                remark = remark
            )

            val billId = database.preSaleBillDao().insert(bill)
            if (billId <= 0) {
                stockService.releaseMultipleReservationsForPreSale(items, location.id, location.locationName, 0, billNo)
                return@withContext Result.failure(IllegalStateException("单据保存失败"))
            }

            database.preSaleItemDao().insertAll(items.map { it.copy(billId = billId) })

            if (saleMode == PreSaleMode.DIRECT_OUT) {
                val confirmResult = stockService.confirmMultipleDeductionsForPreSale(
                    items = items,
                    locationId = location.id,
                    locationName = location.locationName,
                    billId = billId,
                    billNo = billNo
                )
                if (confirmResult.isFailure) {
                    Log.e(TAG, "即时出库扣减失败: ${confirmResult.exceptionOrNull()?.message}")
                }
            }

            if (initialPayment > 0) {
                recordPaymentInternal(billId, initialPayment, payMethod, "开单收款").getOrElse { error ->
                    Log.w(TAG, "开单收款失败: ${error.message}")
                }
            }

            Log.d(TAG, "预售单保存成功: $billNo")
            Result.success(billId to billNo)
        } catch (e: Exception) {
            Log.e(TAG, "保存预售单失败", e)
            Result.failure(e)
        }
    }

    suspend fun shipBill(billId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bill = database.preSaleBillDao().getBillById(billId)
                ?: return@withContext Result.failure(IllegalStateException("单据不存在"))
            if (bill.status != PreSaleStatus.PRESALE) {
                return@withContext Result.failure(IllegalStateException("仅预售状态可发货"))
            }
            val items = database.preSaleItemDao().getItemsByBillId(billId)
            val confirmResult = stockService.confirmMultipleDeductionsForPreSale(
                items = items,
                locationId = bill.locationId,
                locationName = bill.locationName,
                billId = billId,
                billNo = bill.billNo
            )
            if (confirmResult.isFailure) {
                return@withContext confirmResult
            }
            database.preSaleBillDao().updateStatus(billId, PreSaleStatus.SHIPPED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voidBill(billId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bill = database.preSaleBillDao().getBillById(billId)
                ?: return@withContext Result.failure(IllegalStateException("单据不存在"))
            if (bill.status == PreSaleStatus.SHIPPED || bill.status == PreSaleStatus.CANCELLED) {
                return@withContext Result.failure(IllegalStateException("当前状态不可作废"))
            }
            val items = database.preSaleItemDao().getItemsByBillId(billId)
            if (bill.status == PreSaleStatus.PRESALE) {
                stockService.releaseMultipleReservationsForPreSale(
                    items = items,
                    locationId = bill.locationId,
                    locationName = bill.locationName,
                    billId = billId,
                    billNo = bill.billNo
                )
            }
            database.preSaleBillDao().updateStatus(billId, PreSaleStatus.CANCELLED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        billId: Long,
        amount: Double,
        payMethod: String,
        remark: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        recordPaymentInternal(billId, amount, payMethod, remark)
    }

    private suspend fun recordPaymentInternal(
        billId: Long,
        amount: Double,
        payMethod: String,
        remark: String
    ): Result<Unit> {
        if (amount <= 0) return Result.failure(IllegalStateException("收款金额必须大于0"))
        val bill = database.preSaleBillDao().getBillById(billId)
            ?: return Result.failure(IllegalStateException("单据不存在"))
        if (bill.status == PreSaleStatus.CANCELLED) {
            return Result.failure(IllegalStateException("已作废单据不可收款"))
        }
        val newPaid = bill.paidAmount + amount
        if (newPaid > bill.totalAmount + 0.001) {
            return Result.failure(IllegalStateException("收款总额不能超过应收金额"))
        }
        database.paymentRecordDao().insert(
            PaymentRecord(
                billId = billId,
                amount = amount,
                payMethod = payMethod,
                remark = remark
            )
        )
        database.preSaleBillDao().updatePaidAmount(billId, newPaid)
        return Result.success(Unit)
    }
}
