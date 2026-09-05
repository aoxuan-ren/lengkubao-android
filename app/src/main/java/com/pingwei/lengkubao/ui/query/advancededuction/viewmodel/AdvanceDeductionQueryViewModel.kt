package com.pingwei.lengkubao.ui.query.advancededuction.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.ui.query.advancededuction.AdvanceDeductionQueryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvanceDeductionQueryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val configManager = ConfigManager(application)

    val savedTimeRangeLabel: String
        get() = configManager.getQueryTimeRangeLabel(
            ConfigManager.QueryTimeRangeType.ADVANCE_DEDUCTION,
            "7天"
        )

    private val _records = MutableStateFlow<List<AdvanceDeductionQueryRecord>>(emptyList())
    val records: StateFlow<List<AdvanceDeductionQueryRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentKeyword: String? = null
    private var currentStartTime: Long = QueryTimeRangeUtils.getStartTime(savedTimeRangeLabel)
    private var currentEndTime: Long = QueryTimeRangeUtils.getEndTime(savedTimeRangeLabel)

    fun updateKeyword(keyword: String) {
        currentKeyword = keyword.takeIf { it.isNotBlank() }
    }

    fun refreshData() {
        search(currentKeyword, currentStartTime, currentEndTime)
    }

    fun search(
        keyword: String?,
        startTime: Long,
        endTime: Long
    ) {
        currentKeyword = keyword?.takeIf { it.isNotBlank() }
        currentStartTime = startTime
        currentEndTime = endTime

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val advances = database.advanceDao().getByCreateTimeRange(startTime, endTime)
                    .map { AdvanceDeductionQueryRecord.fromAdvance(it) }
                val deductions = database.deductionDao().getByCreateTimeRange(startTime, endTime)
                    .map { AdvanceDeductionQueryRecord.fromDeduction(it) }

                val merged = (advances + deductions)
                    .sortedWith(compareByDescending<AdvanceDeductionQueryRecord> { it.createTime }.thenByDescending { it.id })
                    .filter { matchesKeyword(it, currentKeyword) }

                _records.value = merged
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun matchesKeyword(record: AdvanceDeductionQueryRecord, keyword: String?): Boolean {
        if (keyword.isNullOrBlank()) return true
        val q = keyword.trim()
        return record.customerNo.contains(q, ignoreCase = true) ||
            record.customerName.contains(q, ignoreCase = true) ||
            (record.reason?.contains(q, ignoreCase = true) == true) ||
            record.typeLabel.contains(q, ignoreCase = true)
    }
}
