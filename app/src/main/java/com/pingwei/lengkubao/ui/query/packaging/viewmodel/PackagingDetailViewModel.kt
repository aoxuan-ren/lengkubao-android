package com.pingwei.lengkubao.ui.query.packaging.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.PackagingBill
import com.pingwei.lengkubao.data.db.entity.PackagingItem
import com.pingwei.lengkubao.service.PackagingVoidService
import com.pingwei.lengkubao.service.StockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PackagingDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PackagingDetailVM"

    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val stockService by lazy { StockService(database.stockDao(), database.stockChangeDao()) }
    private val voidService by lazy { // 新增：包装单删除服务
        PackagingVoidService(
            application.applicationContext,
            database,
            stockService
        )
    }

    private val _bill = MutableStateFlow<PackagingBill?>(null)
    val bill: StateFlow<PackagingBill?> = _bill.asStateFlow()

    private val _items = MutableStateFlow<List<PackagingItem>>(emptyList())
    val items: StateFlow<List<PackagingItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 新增：操作结果状态
    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    // 新增：删除成功回调
    private var onDeleteSuccessCallback: (() -> Unit)? = null

    private var currentBillId: Long = -1L

    // 新增：操作结果密封类
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
                Log.d(TAG, "🔍 开始加载包装单: $billId")

                val bill = database.packagingBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "❌ 单据不存在: $billId")
                    _bill.value = null
                    return@launch
                }

                Log.d(TAG, "📋 包装单信息: ${bill.billNo}, 总金额=${bill.totalAmount}")

                val items = database.packagingItemDao().getItemsByBillId(billId)
                Log.d(TAG, "📦 加载到明细数量: ${items.size}")

                // 自动修复数据不一致
                val calculatedTotal = items.sumOf { it.subtotal }
                var finalBill = bill

                if (bill.totalAmount == 0.0 && calculatedTotal > 0) {
                    Log.w(TAG, "⚠️ 数据不一致: 总金额为0，明细计算为 $calculatedTotal")
                    finalBill = bill.copy(totalAmount = calculatedTotal)
                    database.packagingBillDao().update(finalBill)
                    Log.d(TAG, "✅ 已自动修复总金额")
                }

                _bill.value = finalBill
                _items.value = items
                Log.d(TAG, "🎯 加载完成")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载包装单失败: ${e.message}", e)
                _operationResult.value = OperationResult.Error("加载失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 保留原有作废方法（可选）
    suspend fun voidBill(): Boolean {
        return try {
            val currentBill = _bill.value ?: return false
            database.packagingBillDao().updateVoidStatus(currentBill.id, true)
            loadBill(currentBill.id) // 重新加载更新状态
            _operationResult.value = OperationResult.Success("包装单已标记为作废", needRefresh = true)
            onDeleteSuccessCallback?.invoke() // 通知刷新
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 标记作废失败", e)
            _operationResult.value = OperationResult.Error("作废失败: ${e.message}")
            false
        }
    }

    /**
     * 新增：物理删除方法（真正删除数据）
     */
    suspend fun deleteBillPermanently(): Boolean {
        return try {
            _isLoading.value = true
            val currentBill = _bill.value ?: return false

            Log.d(TAG, "🗑️ 开始物理删除包装单: ${currentBill.billNo}")

            // 使用VoidService进行物理删除
            val result = voidService.voidBillWithConfirmation(currentBill.id)

            if (result.isSuccess && result.getOrNull() == true) {
                Log.d(TAG, "✅ 物理删除成功")
                clearData() // 清除当前数据
                _operationResult.value = OperationResult.Success("包装单已物理删除", needRefresh = true)
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
     * 新增：打印方法（如果原来没有的话）
     */
    suspend fun printBill(): Boolean {
        return try {
            val currentBill = _bill.value ?: return false
            database.packagingBillDao().updatePrintStatus(currentBill.id, true)
            loadBill(currentBill.id) // 重新加载更新状态
            _operationResult.value = OperationResult.Success("打印状态已更新", needRefresh = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 更新打印状态失败", e)
            _operationResult.value = OperationResult.Error("更新打印状态失败: ${e.message}")
            false
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