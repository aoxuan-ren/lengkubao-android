package com.pingwei.lengkubao.ui.presale.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.PreSaleBill
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreSaleQueryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val _bills = MutableStateFlow<List<PreSaleBill>>(emptyList())
    val bills: StateFlow<List<PreSaleBill>> = _bills.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var savedTimeRangeLabel = "7天"
    private var searchText = ""
    private var allBills = emptyList<PreSaleBill>()

    init {
        viewModelScope.launch {
            database.preSaleBillDao().getAllBills().collect { list ->
                allBills = list
                _bills.value = filterList(list)
            }
        }
    }

    fun refreshData(search: String = searchText, timeRangeLabel: String = savedTimeRangeLabel) {
        searchText = search
        savedTimeRangeLabel = normalizeLabel(timeRangeLabel)
        _bills.value = filterList(allBills)
    }

    private fun filterList(list: List<PreSaleBill>): List<PreSaleBill> {
        val label = normalizeLabel(savedTimeRangeLabel)
        val start = QueryTimeRangeUtils.getStartTime(label)
        val end = QueryTimeRangeUtils.getEndTime(label)
        return list.filter { bill ->
            bill.createTime in start..end &&
                (searchText.isBlank() ||
                    bill.billNo.contains(searchText, ignoreCase = true) ||
                    bill.buyerName.contains(searchText, ignoreCase = true))
        }.sortedByDescending { it.createTime }
    }

    private fun normalizeLabel(label: String): String = when (label) {
        "近7天" -> "7天"
        else -> label
    }
}
