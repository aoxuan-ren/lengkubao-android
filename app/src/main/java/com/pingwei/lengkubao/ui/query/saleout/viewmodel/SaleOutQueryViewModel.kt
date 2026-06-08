package com.pingwei.lengkubao.ui.query.saleout.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.SaleBill
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.utils.BillQuerySearchFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SaleOutQueryViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SaleOutQueryVM"
    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val configManager = ConfigManager(application.applicationContext)
    private val defaultTimeRangeLabel = configManager.getQueryTimeRangeLabel(
        ConfigManager.QueryTimeRangeType.SALE_OUT,
        "今天"
    )

    private val _bills = MutableStateFlow<List<SaleBill>>(emptyList())
    val bills: StateFlow<List<SaleBill>> = _bills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSearchParams = SearchParams(
        keyword = null,
        startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel),
        endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel),
        operatorId = null,
        locationId = null
    )

    val savedTimeRangeLabel: String = defaultTimeRangeLabel

    init {
        loadInitialData()
        observeDatabaseChanges()
    }

    private fun observeDatabaseChanges() {
        viewModelScope.launch {
            database.saleBillDao().getAllBills().collect { billList ->
                Log.d(TAG, "📊 数据库数据变化，收到 ${billList.size} 条销售单记录")
                _bills.value = filterBillsByParams(billList, currentSearchParams)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "🔄 强制刷新销售单数据")
                performSearchInternal(
                    keyword = currentSearchParams.keyword,
                    startTime = currentSearchParams.startTime,
                    endTime = currentSearchParams.endTime,
                    operatorId = currentSearchParams.operatorId,
                    locationId = currentSearchParams.locationId
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 刷新数据失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reloadData() {
        loadInitialData()
    }

    suspend fun search(
        keyword: String?,
        startTime: Long?,
        endTime: Long?,
        operatorId: Long?,
        locationId: Long?
    ) {
        _isLoading.value = true
        try {
            Log.d(TAG, "🔍 开始搜索销售单")
            performSearchInternal(
                keyword = keyword,
                startTime = startTime ?: QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel),
                endTime = endTime ?: QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel),
                operatorId = operatorId,
                locationId = locationId
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 搜索失败", e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun performSearchInternal(
        keyword: String?,
        startTime: Long,
        endTime: Long,
        operatorId: Long?,
        locationId: Long?
    ) {
        currentSearchParams = SearchParams(
            keyword = keyword?.takeIf { it.isNotBlank() },
            startTime = startTime,
            endTime = endTime,
            operatorId = operatorId,
            locationId = locationId
        )

        Log.d(TAG, "搜索条件：keyword=$keyword, startTime=$startTime, endTime=$endTime")

        val billList = database.saleBillDao()
            .getSaleBillsByFilter(
                billNo = null,
                customerName = null,
                startTime = startTime.takeIf { it != 0L },
                endTime = endTime.takeIf { it != Long.MAX_VALUE },
                operatorId = operatorId,
                locationId = locationId
            )
            .first()

        val filteredBills = filterBillsByParams(billList, currentSearchParams)
        Log.d(TAG, "🎯 搜索到 ${filteredBills.size} 张销售单")
        _bills.value = filteredBills
    }

    private fun filterBillsByParams(
        billList: List<SaleBill>,
        params: SearchParams
    ): List<SaleBill> {
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
                Log.d(TAG, "🔍 开始加载销售单列表数据")

                val startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel)
                val endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel)
                performSearchInternal(
                    keyword = currentSearchParams.keyword,
                    startTime = startTime,
                    endTime = endTime,
                    operatorId = currentSearchParams.operatorId,
                    locationId = currentSearchParams.locationId
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载销售单失败", e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    data class SearchParams(
        val keyword: String?,
        val startTime: Long,
        val endTime: Long,
        val operatorId: Long?,
        val locationId: Long?
    )
}
