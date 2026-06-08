// ui/query/viewmodel/QueryViewModel.kt
package com.pingwei.lengkubao.ui.query.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Customer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 查询功能通用ViewModel
 */
class QueryViewModel(application: Application) : AndroidViewModel(application) {

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }

    // 搜索条件
    var searchBillNo: String? = null
    var selectedCustomerNo: String? = null
    var startTime: Long = 0L
    var endTime: Long = System.currentTimeMillis()

    // 客户列表
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    init {
        loadCustomers()
    }

    private fun loadCustomers() {
        viewModelScope.launch {
            val customerList = database.customerDao().getAllSimple()
            _customers.value = customerList
        }
    }
}