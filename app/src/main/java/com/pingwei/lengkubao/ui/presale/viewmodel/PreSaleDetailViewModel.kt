package com.pingwei.lengkubao.ui.presale.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.service.PreSaleService
import com.pingwei.lengkubao.service.StockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pingwei.lengkubao.utils.PreSaleSyncHelper
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.launch

data class OutboundRecordWithItems(
    val record: OutboundRecord,
    val items: List<OutboundRecordItem>
)

class PreSaleDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val preSaleService by lazy {
        PreSaleService(
            database,
            StockService(database.stockDao(), database.stockChangeDao())
        )
    }

    private val _bill = MutableStateFlow<PreSaleBill?>(null)
    val bill: StateFlow<PreSaleBill?> = _bill.asStateFlow()

    private val _items = MutableStateFlow<List<PreSaleItem>>(emptyList())
    val items: StateFlow<List<PreSaleItem>> = _items.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentRecord>>(emptyList())
    val payments: StateFlow<List<PaymentRecord>> = _payments.asStateFlow()

    private val _outboundRecords = MutableStateFlow<List<OutboundRecordWithItems>>(emptyList())
    val outboundRecords: StateFlow<List<OutboundRecordWithItems>> = _outboundRecords.asStateFlow()

    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    sealed class OperationResult {
        data class Success(val message: String, val needRefresh: Boolean = true) : OperationResult()
        data class Error(val message: String) : OperationResult()
    }

    fun loadBill(billId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _bill.value = database.preSaleBillDao().getBillById(billId)
            _items.value = database.preSaleItemDao().getItemsByBillId(billId)
            _payments.value = database.paymentRecordDao().getPaymentsByBillIdSync(billId)
            val records = database.outboundRecordDao().getByBillId(billId)
            _outboundRecords.value = records.map { record ->
                OutboundRecordWithItems(
                    record = record,
                    items = database.outboundRecordItemDao().getByRecordId(record.id)
                )
            }
            _isLoading.value = false
        }
    }

    fun reloadBill() {
        _bill.value?.id?.let { loadBill(it) }
    }

    fun recordPayment(amount: Double, payMethod: String, remark: String) {
        viewModelScope.launch {
            val billId = _bill.value?.id ?: return@launch
            preSaleService.recordPayment(billId, amount, payMethod, remark).fold(
                onSuccess = { paymentId ->
                    PreSaleSyncHelper.ensurePaymentSourceIdentity(context, paymentId)
                    SyncTrigger.triggerPreSalePaymentSync(context, paymentId)
                    SyncTrigger.triggerPreSaleSync(context, billId)
                    reloadBill()
                    _operationResult.value = OperationResult.Success("收款登记成功")
                },
                onFailure = { e ->
                    _operationResult.value = OperationResult.Error(e.message ?: "收款失败")
                }
            )
        }
    }

    fun recordOutbound(productQuantities: Map<String, Int>, remark: String = "") {
        viewModelScope.launch {
            val billId = _bill.value?.id ?: return@launch
            val outboundProducts = productQuantities.filterValues { it > 0 }
            if (outboundProducts.isEmpty()) return@launch

            val freshItems = database.preSaleItemDao().getItemsByBillId(billId)
            val remapped = mutableMapOf<String, Int>()
            for ((productKey, qty) in outboundProducts) {
                val matched = freshItems.any {
                    it.productNo == productKey || it.productName == productKey
                }
                if (!matched) {
                    _operationResult.value = OperationResult.Error("商品明细已更新，请重试")
                    reloadBill()
                    return@launch
                }
                remapped[productKey] = (remapped[productKey] ?: 0) + qty
            }

            preSaleService.recordOutbound(billId, remapped, remark).fold(
                onSuccess = { recordId ->
                    PreSaleSyncHelper.ensureOutboundSourceIdentity(context, recordId)
                    SyncTrigger.triggerPreSaleOutboundSync(context, recordId)
                    reloadBill()
                    _operationResult.value = OperationResult.Success("发货出库成功")
                },
                onFailure = { e ->
                    _operationResult.value = OperationResult.Error(e.message ?: "发货出库失败")
                }
            )
        }
    }

    fun voidBill() {
        viewModelScope.launch {
            val billId = _bill.value?.id ?: return@launch
            preSaleService.voidBill(billId).fold(
                onSuccess = {
                    PreSaleSyncHelper.syncBillToServer(context, billId)
                    reloadBill()
                    _operationResult.value = OperationResult.Success("作废成功")
                },
                onFailure = { e ->
                    _operationResult.value = OperationResult.Error(e.message ?: "作废失败")
                }
            )
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    fun unpaidAmount(): Double {
        val b = _bill.value ?: return 0.0
        return (b.totalAmount - b.paidAmount).coerceAtLeast(0.0)
    }

    fun remainingQuantity(item: PreSaleItem): Int {
        return (item.quantity - item.shippedQuantity).coerceAtLeast(0)
    }

    fun totalRemainingQuantity(): Int {
        return _items.value.sumOf { remainingQuantity(it) }
    }

    fun canOutbound(): Boolean {
        val b = _bill.value ?: return false
        return b.saleMode == PreSaleMode.PRESALE &&
            (b.status == PreSaleStatus.PRESALE || b.status == PreSaleStatus.SHIPPED) &&
            totalRemainingQuantity() > 0
    }

    fun canVoid(): Boolean {
        val b = _bill.value ?: return false
        return b.saleMode == PreSaleMode.PRESALE &&
            (b.status == PreSaleStatus.PRESALE || b.status == PreSaleStatus.SHIPPED)
    }
}
