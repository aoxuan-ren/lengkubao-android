// ui/query/instock/viewmodel/InStockDetailViewModel.kt（修改版）
package com.pingwei.lengkubao.ui.query.instock.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.InStockBill
import com.pingwei.lengkubao.data.db.entity.InStockItem
import com.pingwei.lengkubao.service.InStockVoidService
import com.pingwei.lengkubao.service.StockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InStockDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "InStockDetailVM"

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val stockService by lazy { StockService(database.stockDao(), database.stockChangeDao()) }
    private val voidService by lazy {
        InStockVoidService(
            application.applicationContext,
            database,
            stockService
        )
    }

    private val _bill = MutableStateFlow<InStockBill?>(null)
    val bill: StateFlow<InStockBill?> = _bill.asStateFlow()

    private val _items = MutableStateFlow<List<InStockItem>>(emptyList())
    val items: StateFlow<List<InStockItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    // 新增：删除成功回调
    private var onDeleteSuccessCallback: (() -> Unit)? = null

    private var currentBillId: Long = -1L

    sealed class OperationResult {
        data class Success(val message: String, val needRefresh: Boolean = false) : OperationResult()
        data class Error(val message: String) : OperationResult()
    }

    // 新增：设置删除成功回调
    fun setOnDeleteSuccessCallback(callback: () -> Unit) {
        this.onDeleteSuccessCallback = callback
    }

    fun loadBill(billId: Long) {
        if (billId == currentBillId && _bill.value != null) return

        currentBillId = billId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "🔍 开始加载入库单: $billId")

                val bill = database.inStockBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "❌ 单据不存在: $billId")
                    _bill.value = null
                    return@launch
                }

                Log.d(TAG, "📋 单据信息: ${bill.billNo}, 状态=${bill.status}")

                val items = database.inStockItemDao().getItemsByBillId(billId)
                Log.d(TAG, "📦 明细数量: ${items.size}")

                // 自动修复数据不一致
                val calculatedTotal = items.sumOf { it.amount }
                var finalBill = bill

                if (bill.totalAmount == 0.0 && calculatedTotal > 0) {
                    Log.w(TAG, "⚠️ 数据不一致: 总金额为0，明细计算为 $calculatedTotal")
                    finalBill = bill.copy(totalAmount = calculatedTotal)
                    database.inStockBillDao().update(finalBill)
                    Log.d(TAG, "✅ 已自动修复总金额")
                }

                _bill.value = finalBill
                _items.value = items
                Log.d(TAG, "🎯 加载完成，状态=${finalBill.status}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载入库单失败", e)
                _operationResult.value = OperationResult.Error("加载失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 旧作废方法（仅更新状态）
     */
    suspend fun voidBill(): Boolean {
        return try {
            val currentBill = _bill.value ?: return false
            database.inStockBillDao().updateStatus(currentBill.id, 2)
            loadBill(currentBill.id) // 重新加载更新状态
            _operationResult.value = OperationResult.Success("单据已标记为作废", needRefresh = true)
            onDeleteSuccessCallback?.invoke() // 通知刷新
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 标记作废失败", e)
            _operationResult.value = OperationResult.Error("作废失败: ${e.message}")
            false
        }
    }

    /**
     * 物理删除方法（真正删除数据）
     */
    suspend fun deleteBillPermanently(): Boolean {
        return try {
            _isLoading.value = true
            val currentBill = _bill.value ?: return false

            Log.d(TAG, "🗑️ 开始物理删除单据: ${currentBill.billNo}")

            // 使用VoidService进行物理删除
            val result = voidService.voidBillWithConfirmation(currentBill.id)

            if (result.isSuccess && result.getOrNull() == true) {
                Log.d(TAG, "✅ 物理删除成功")
                clearData() // 清除当前数据
                _operationResult.value = OperationResult.Success("单据已物理删除", needRefresh = true)
                onDeleteSuccessCallback?.invoke() // 通知列表刷新
                true
            } else {
                val error = result.exceptionOrNull()?.message ?: "删除失败"
                Log.e(TAG, "❌ 物理删除失败: $error")
                _operationResult.value = OperationResult.Error("删除失败: $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 物理删除异常", e)
            _operationResult.value = OperationResult.Error("删除异常: ${e.message}")
            false
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 清除当前数据
     */
    private fun clearData() {
        _bill.value = null
        _items.value = emptyList()
        currentBillId = -1L
    }

    /**
     * 清除操作结果
     */
    fun clearOperationResult() {
        _operationResult.value = null
    }
}