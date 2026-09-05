package com.pingwei.lengkubao.ui.cashflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.LedgerCategory
import com.pingwei.lengkubao.data.db.entity.LedgerEntry
import com.pingwei.lengkubao.data.model.CashFlowFilter
import com.pingwei.lengkubao.data.model.CashFlowSummary
import com.pingwei.lengkubao.data.model.StatRow
import com.pingwei.lengkubao.service.CashFlowService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val service = CashFlowService(
        database.ledgerEntryDao(),
        database.ledgerCategoryDao()
    )

    private val _filter = MutableStateFlow(defaultFilter())
    val filter: StateFlow<CashFlowFilter> = _filter.asStateFlow()

    val categories: StateFlow<List<LedgerCategory>> = service.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entries: StateFlow<List<LedgerEntry>> = _filter
        .flatMapLatest { service.observeEntries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summary = MutableStateFlow(CashFlowSummary(0.0, 0.0))
    val summary: StateFlow<CashFlowSummary> = _summary.asStateFlow()

    private val _statsByDate = MutableStateFlow<List<StatRow>>(emptyList())
    val statsByDate: StateFlow<List<StatRow>> = _statsByDate.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _editingEntry = MutableStateFlow<LedgerEntry?>(null)
    val editingEntry: StateFlow<LedgerEntry?> = _editingEntry.asStateFlow()

    init {
        refreshSummary()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun updateFilter(transform: (CashFlowFilter) -> CashFlowFilter) {
        _filter.value = transform(_filter.value)
        refreshSummary()
    }

    fun refreshSummary() {
        viewModelScope.launch {
            _summary.value = service.getSummary(_filter.value)
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _statsByDate.value = service.getStatsByDate(_filter.value)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startEditEntry(entry: LedgerEntry) {
        _editingEntry.value = entry
    }

    fun clearEditingEntry() {
        _editingEntry.value = null
    }

    fun saveEntry(
        type: String,
        category: LedgerCategory,
        amount: Double,
        entryDate: String,
        remark: String
    ) {
        if (amount <= 0) {
            _message.value = "金额必须大于 0"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existing = _editingEntry.value
                val now = System.currentTimeMillis()
                if (existing != null) {
                    service.updateEntry(
                        existing.copy(
                            type = type,
                            categoryId = category.id,
                            categoryName = category.name,
                            amount = amount,
                            entryDate = entryDate,
                            remark = remark.trim(),
                            updateTime = now
                        )
                    )
                    _message.value = "流水已更新"
                    _editingEntry.value = null
                } else {
                    service.addEntry(
                        LedgerEntry(
                            entryNo = "",
                            type = type,
                            categoryId = category.id,
                            categoryName = category.name,
                            amount = amount,
                            entryDate = entryDate,
                            remark = remark.trim(),
                            createTime = now
                        )
                    )
                    _message.value = "流水已保存"
                }
                refreshSummary()
            } catch (e: Exception) {
                _message.value = "保存失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun voidEntry(id: Long) {
        viewModelScope.launch {
            try {
                service.voidEntry(id)
                _message.value = "流水已作废"
                if (_editingEntry.value?.id == id) {
                    _editingEntry.value = null
                }
                refreshSummary()
            } catch (e: Exception) {
                _message.value = "作废失败: ${e.message}"
            }
        }
    }

    fun addCategory(type: String, name: String) {
        viewModelScope.launch {
            service.addCategory(type, name)
                .onSuccess { _message.value = "类别已添加" }
                .onFailure { _message.value = it.message ?: "添加失败" }
        }
    }

    fun toggleCategoryEnabled(category: LedgerCategory) {
        viewModelScope.launch {
            service.setCategoryEnabled(category.id, !category.enabled)
        }
    }

    companion object {
        fun defaultFilter(): CashFlowFilter {
            val cal = Calendar.getInstance()
            val end = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, -30)
            val start = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            return CashFlowFilter(startDate = start, endDate = end)
        }
    }
}

class CashFlowViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CashFlowViewModel::class.java)) {
            return CashFlowViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
