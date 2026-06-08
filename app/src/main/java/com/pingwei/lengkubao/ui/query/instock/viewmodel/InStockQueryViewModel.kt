package com.pingwei.lengkubao.ui.query.instock.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.InStockBill
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.utils.BillQuerySearchFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class InStockQueryViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "InStockQueryVM"

    // 数据库实例懒加载
    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val configManager = ConfigManager(application.applicationContext)
    private val defaultTimeRangeLabel = configManager.getQueryTimeRangeLabel(
        ConfigManager.QueryTimeRangeType.IN_STOCK,
        "7天"
    )

    // 单据列表状态流，对外只读
    private val _bills = MutableStateFlow<List<InStockBill>>(emptyList())
    val bills: StateFlow<List<InStockBill>> = _bills.asStateFlow()

    // 加载状态流，对外只读
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 当前搜索条件，用于实时过滤和刷新复用
    private var currentSearchParams = SearchParams(
        keyword = null,
        startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel),
        endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel)
    )

    val savedTimeRangeLabel: String = defaultTimeRangeLabel

    init {
        loadInitialData()
        // 初始化数据库实时监听，数据变更自动触发过滤更新
        observeDatabaseChanges()
    }

    // ============ 数据刷新相关方法 ============
    /**
     * 强制刷新数据（单据删除/修改后调用）
     */
    fun refreshData() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 强制刷新数据")
            _isLoading.value = true
            try {
                // 重新应用当前搜索条件执行搜索
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

    /**
     * 重新加载初始默认数据（7天数据）
     */
    fun reloadData() {
        loadInitialData()
    }

    /**
     * 加载初始默认数据（最近7天入库单）
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "🔍 开始加载入库单列表初始数据")
                val startTime = QueryTimeRangeUtils.getStartTime(defaultTimeRangeLabel)
                val endTime = QueryTimeRangeUtils.getEndTime(defaultTimeRangeLabel)
                // 更新默认搜索条件
                currentSearchParams = currentSearchParams.copy(
                    startTime = startTime,
                    endTime = endTime
                )
                // 触发一次初始数据加载
                val billList = database.inStockBillDao().getAllBills().first()
                Log.d(TAG, "📋 从数据库加载到 ${billList.size} 张单据")
                // 应用默认条件过滤
                val filteredBills = filterBillsByParams(billList, currentSearchParams)
                Log.d(TAG, "🎯 初始过滤后剩余 ${filteredBills.size} 张单据")
                _bills.value = filteredBills
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载初始数据失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============ 数据库实时监听核心方法 ============
    /**
     * 监听数据库单据表的实时变化，自动根据当前搜索条件过滤数据
     */
    private fun observeDatabaseChanges() {
        viewModelScope.launch {
            database.inStockBillDao().getAllBills().collect { billList ->
                Log.d(TAG, "📊 数据库数据发生变化，共 ${billList.size} 条记录")
                // 复用过滤逻辑，自动应用当前搜索条件
                val filteredBills = filterBillsByParams(billList, currentSearchParams)
                _bills.value = filteredBills
            }
        }
    }

    // ============ 搜索相关方法 ============
    /**
     * 外部调用的搜索方法
     */
    suspend fun search(keyword: String?, startTime: Long, endTime: Long) {
        _isLoading.value = true
        try {
            Log.d(TAG, "🔍 执行入库单搜索")
            performSearchInternal(keyword, startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 搜索失败", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 内部搜索执行方法：更新搜索条件+触发即时数据过滤
     */
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

        val billList = database.inStockBillDao().getAllBills().first()
        Log.d(TAG, "📋 待筛选单据总数：${billList.size}")
        val filteredBills = filterBillsByParams(billList, currentSearchParams)
        Log.d(TAG, "🎯 搜索匹配结果数：${filteredBills.size}")
        _bills.value = filteredBills
    }

    // ============ 通用过滤工具方法 ============
    /**
     * 根据搜索参数统一过滤单据列表，复用逻辑避免重复代码
     */
    private fun filterBillsByParams(
        billList: List<InStockBill>,
        params: SearchParams
    ): List<InStockBill> {
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

    // ============ 搜索参数数据类 ============
    /**
     * 封装搜索条件，统一管理查询参数
     */
    data class SearchParams(
        val keyword: String?,
        val startTime: Long,
        val endTime: Long
    )
}