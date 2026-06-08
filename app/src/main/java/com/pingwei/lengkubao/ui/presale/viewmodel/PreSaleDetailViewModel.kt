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
import kotlinx.coroutines.launch

class PreSaleDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val preSaleService = PreSaleService(
        database,
        StockService(database.stockDao(), database.stockChangeDao())
    )

    private val _bill = MutableStateFlow<PreSaleBill?>(null)
    val bill: StateFlow<PreSaleBill?> = _bill.asStateFlow()

    private val _items = MutableStateFlow<List<PreSaleItem>>(emptyList())
    val items: StateFlow<List<PreSaleItem>> = _items.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentRecord>>(emptyList())
    val payments: StateFlow<List<PaymentRecord>> = _payments.asStateFlow()

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
                onSuccess = {
                    reloadBill()
                    _operationResult.value = OperationResult.Success("收款登记成功")
                },
                onFailure = { e ->
                    _operationResult.value = OperationResult.Error(e.message ?: "收款失败")
                }
            )
        }
    }

    fun shipBill() {
        viewModelScope.launch {
            val billId = _bill.value?.id ?: return@launch
            preSaleService.shipBill(billId).fold(
                onSuccess = {
                    _bill.value = database.preSaleBillDao().getBillById(billId)
                    _operationResult.value = OperationResult.Success("发货成功")
                },
                onFailure = { e ->
                    _operationResult.value = OperationResult.Error(e.message ?: "发货失败")
                }
            )
        }
    }

    fun voidBill() {
        viewModelScope.launch {
            val billId = _bill.value?.id ?: return@launch
            preSaleService.voidBill(billId).fold(
                onSuccess = {
                    _bill.value = database.preSaleBillDao().getBillById(billId)
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
}
