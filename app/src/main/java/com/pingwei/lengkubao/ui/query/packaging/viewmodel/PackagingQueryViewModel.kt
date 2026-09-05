package com.pingwei.lengkubao.ui.query.packaging.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.PackagingBill
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.utils.BillQuerySearchFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PackagingQueryViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PackagingQueryVM"

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val configManager = ConfigManager(application.applicationContext)
    private val defaultTimeRangeLabel = configManager.getQueryTimeRangeLabel(
        ConfigManager.QueryTimeRangeType.PACKAGING,
        "今天"
    )

    private val _bills = MutableStateFlow<List<PackagingBill>>(emptyList())
    val bills: StateFlow<List<PackagingBill>> = _bills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSearchParams = SearchParams(
        keyword = null,
        startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel),
        endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel)
    )
    private var allBills = emptyList<PackagingBill>()

    val savedTimeRangeLabel: String = defaultTimeRangeLabel

    init {
        loadInitialData()
        observeDatabaseChanges()
    }

    /** 输入时即时按单据号/客户/首字母过滤，不触发加载态 */
    fun updateKeyword(keyword: String?) {
        currentSearchParams = currentSearchParams.copy(
            keyword = keyword?.takeIf { it.isNotBlank() }
        )
        _bills.value = filterBillsByParams(allBills, currentSearchParams)
    }

    private fun observeDatabaseChanges() {
        viewModelScope.launch {
            database.packagingBillDao().getAllBills().collect { billList ->
                Log.d(TAG, "📊 数据库数据变化，收到 ${billList.size} 条记录")
                allBills = billList
                _bills.value = filterBillsByParams(billList, currentSearchParams)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "🔄 强制刷新包装单数据")
                performSearchInternal(
                    keyword = currentSearchParams.keyword,
                    startTime = currentSearchParams.startTime,
                    endTime = currentSearchParams.endTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 刷新数据失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun search(keyword: String?, startTime: Long, endTime: Long) {
        _isLoading.value = true
        try {
            Log.d(TAG, "🔍 开始搜索包装单")
            performSearchInternal(keyword, startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 搜索失败", e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun performSearchInternal(
        keyword: String?,
        startTime: Long,
        endTime: Long
    ) {
        currentSearchParams = SearchParams(
            keyword = keyword?.takeIf { it.isNotBlank() },
            startTime = startTime,
            endTime = endTime
        )

        val billList = database.packagingBillDao().getAllBills().first()
        allBills = billList
        Log.d(TAG, "📋 共有 ${billList.size} 张单据可供筛选")
        val filtered = filterBillsByParams(billList, currentSearchParams)
        Log.d(TAG, "🎯 搜索到 ${filtered.size} 张单据")
        _bills.value = filtered
    }

    private fun filterBillsByParams(
        billList: List<PackagingBill>,
        params: SearchParams
    ): List<PackagingBill> {
        return billList.filter { bill ->
            val timeMatch = bill.createTime in params.startTime..params.endTime
            val keywordMatch = BillQuerySearchFilter.matches(
                keyword = params.keyword,
                billNo = bill.billNo,
                customerNo = bill.customerNo,
                customerName = bill.customerName
            )
            timeMatch && keywordMatch
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "🔍 开始加载包装单列表数据")

                val startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel)
                val endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel)
                performSearchInternal(
                    keyword = currentSearchParams.keyword,
                    startTime = startTime,
                    endTime = endTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载数据失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    data class SearchParams(
        val keyword: String?,
        val startTime: Long,
        val endTime: Long
    )
}
