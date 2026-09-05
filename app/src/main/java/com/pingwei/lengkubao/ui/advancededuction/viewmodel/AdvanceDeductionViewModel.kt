package com.pingwei.lengkubao.ui.advancededuction.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Advance
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.CustomerType
import com.pingwei.lengkubao.data.db.entity.Deduction
import com.pingwei.lengkubao.data.db.entity.Operator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdvanceDeductionViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getInstance(application.applicationContext)
    private val _operators = MutableStateFlow<List<Operator>>(emptyList())
    val operators: StateFlow<List<Operator>> = _operators
    // 客户列表（全量，供搜索组件过滤）
    private val _allCustomers = MutableStateFlow<List<Customer>>(emptyList())
    val allCustomers: StateFlow<List<Customer>> = _allCustomers

    // 预支列表
    private val _advances = MutableStateFlow<List<Advance>>(emptyList())
    val advances: StateFlow<List<Advance>> = _advances

    // 扣款列表
    private val _deductions = MutableStateFlow<List<Deduction>>(emptyList())
    val deductions: StateFlow<List<Deduction>> = _deductions

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 最近保存的扣款记录（用于打印）
    private val _lastSavedDeduction = MutableStateFlow<Deduction?>(null)
    val lastSavedDeduction: StateFlow<Deduction?> = _lastSavedDeduction

    // 选中的客户
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer

    init {
        loadCustomers()
        loadRecentAdvances()
        loadRecentDeductions()
        loadOperators()  // 新增
    }
    // 新增方法
    private fun loadOperators() {
        viewModelScope.launch {
            database.operatorDao().getAllOperators().collect { list ->
                _operators.value = list
            }
        }
    }
    private fun loadCustomers() {
        viewModelScope.launch {
            database.customerDao().getCustomersByType(CustomerType.SELLER).collect { list ->
                _allCustomers.value = list
            }
        }
    }

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        if (customer != null) {
            loadCustomerAdvances(customer.customerNo)
            loadCustomerDeductions(customer.customerNo)
        } else {
            _advances.value = emptyList()
            _deductions.value = emptyList()
        }
    }

    private fun loadCustomerAdvances(customerNo: String) {
        viewModelScope.launch {
            database.advanceDao().getAdvancesByCustomer(customerNo).collect { list ->
                _advances.value = list
            }
        }
    }

    private fun loadCustomerDeductions(customerNo: String) {
        viewModelScope.launch {
            database.deductionDao().getDeductionsByCustomer(customerNo).collect { list ->
                _deductions.value = list
            }
        }
    }

    private fun loadRecentAdvances() {
        viewModelScope.launch {
            database.advanceDao().getAllAdvances().collect { list ->
                _advances.value = list.take(10)
            }
        }
    }

    private fun loadRecentDeductions() {
        viewModelScope.launch {
            database.deductionDao().getAllDeductions().collect { list ->
                _deductions.value = list.take(10)
            }
        }
    }

    // 修改 addAdvance 方法
    fun addAdvance(
        customerNo: String,
        customerName: String,
        amount: Double,
        reason: String,
        handler: String,
        creator: String,
        operatorId: Long  // 新增参数
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val advance = Advance(
                    customerNo = customerNo,
                    customerName = customerName,
                    amount = amount,
                    advanceDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    reason = reason.ifEmpty { null },
                    handler = handler,
                    creator = creator,
                    status = 1,
                    createTime = System.currentTimeMillis(),
                    syncStatus = 0,
                    operatorId = operatorId  // 新增
                )
                database.advanceDao().insert(advance)
                loadCustomerAdvances(customerNo)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addDeduction(
        customerNo: String,
        customerName: String,
        quantity: Int,
        unitPrice: Double,
        amount: Double,
        reason: String,
        handler: String,
        creator: String,
        operatorId: Long
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deduction = Deduction(
                    customerNo = customerNo,
                    customerName = customerName,
                    amount = amount,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    deductDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    reason = reason.ifEmpty { null },
                    handler = handler,
                    creator = creator,
                    status = 1,
                    createTime = System.currentTimeMillis(),
                    syncStatus = 0,
                    operatorId = operatorId
                )
                val id = database.deductionDao().insert(deduction)
                deduction.id = id
                _lastSavedDeduction.value = deduction
                loadCustomerDeductions(customerNo)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearLastSavedDeduction() {
        _lastSavedDeduction.value = null
    }

    fun deleteAdvance(advance: Advance) {
        viewModelScope.launch {
            database.advanceDao().delete(advance)
            if (_selectedCustomer.value != null) {
                loadCustomerAdvances(_selectedCustomer.value!!.customerNo)
            }
        }
    }

    fun deleteDeduction(deduction: Deduction) {
        viewModelScope.launch {
            database.deductionDao().delete(deduction)
            if (_selectedCustomer.value != null) {
                loadCustomerDeductions(_selectedCustomer.value!!.customerNo)
            }
        }
    }

    fun getCustomerTotalAdvance(customerNo: String): Double? {
        var result: Double? = null
        viewModelScope.launch {
            result = database.advanceDao().getTotalAmountByCustomer(customerNo)
        }
        return result
    }

    fun getCustomerTotalDeduction(customerNo: String): Double? {
        var result: Double? = null
        viewModelScope.launch {
            result = database.deductionDao().getTotalAmountByCustomer(customerNo)
        }
        return result
    }
}