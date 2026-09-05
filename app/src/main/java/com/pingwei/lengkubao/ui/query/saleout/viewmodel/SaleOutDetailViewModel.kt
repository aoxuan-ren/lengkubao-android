// ui/query/saleout/viewmodel/SaleOutDetailViewModel.kt
package com.pingwei.lengkubao.ui.query.saleout.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.SaleBill
import com.pingwei.lengkubao.data.db.entity.SaleItem
import com.pingwei.lengkubao.service.SaleOutVoidService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SaleOutDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SaleOutDetailVM"
    private val database by lazy { AppDatabase.getInstance(application.applicationContext) }
    private val voidService by lazy {
        SaleOutVoidService(
            application.applicationContext,
            database
        )
    }

    // 销售单主信息数据流
    private val _bill = MutableStateFlow<SaleBill?>(null)
    val bill: StateFlow<SaleBill?> = _bill.asStateFlow()

    // 销售单明细数据流
    private val _items = MutableStateFlow<List<SaleItem>>(emptyList())
    val items: StateFlow<List<SaleItem>> = _items.asStateFlow()

    // 加载状态数据流
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

    // 加载销售单及明细数据（复用入库详情ViewModel逻辑，含数据修复）
    fun loadBill(billId: Long) {
        if (billId == currentBillId && _bill.value != null) return
        currentBillId = billId

        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "开始加载销售单: $billId")

                // 1. 加载销售单主信息
                val bill = database.saleBillDao().getBillById(billId)
                if (bill == null) {
                    Log.e(TAG, "销售单不存在: $billId")
                    _bill.value = null
                    return@launch
                }
                Log.d(TAG, "销售单信息: ${bill.billNo}, 总金额=${bill.totalAmount}")

                // 2. 加载销售单明细
                val items = database.saleItemDao().getItemsByBillId(billId)
                Log.d(TAG, "加载到明细数量: ${items.size}")

                // 3. 从明细重新计算总金额（修复数据不一致问题）
                val calculatedTotal = items.sumOf { it.amount }
                Log.d(TAG, "从明细计算的总金额: $calculatedTotal")

                // 4. 自动修复总金额不一致（如果数据库总金额为0但明细有值）
                var finalBill = bill
                if (bill.totalAmount == 0.0 && calculatedTotal > 0) {
                    Log.w(TAG, "数据不一致：数据库总金额为0，明细计算为 $calculatedTotal")
                    finalBill = bill.copy(totalAmount = calculatedTotal)
                    database.saleBillDao().update(finalBill)
                    Log.d(TAG, "已自动修复总金额")
                }

                // 5. 更新数据流
                _bill.value = finalBill
                _items.value = items
                Log.d(TAG, "最终显示总金额: ${finalBill.totalAmount}")

            } catch (e: Exception) {
                Log.e(TAG, "加载销售单失败", e)
                _operationResult.value = OperationResult.Error("加载失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 作废销售单（调用DAO的voidBill方法，含状态和同步状态更新）
    suspend fun voidBill(): Boolean {
        return try {
            val currentBill = _bill.value ?: return false
            database.saleBillDao().voidBill(currentBill.id) // 已在DAO中实现事务
            loadBill(currentBill.id) // 重新加载更新状态
            _operationResult.value = OperationResult.Success("报账单已标记为作废", needRefresh = true)
            onDeleteSuccessCallback?.invoke() // 通知刷新
            true
        } catch (e: Exception) {
            Log.e(TAG, "作废销售单失败", e)
            _operationResult.value = OperationResult.Error("作废失败: ${e.message}")
            false
        }
    }

    // 新增：物理删除方法（真正删除数据，恢复库存）
    suspend fun deleteBillPermanently(): Boolean {
        return try {
            _isLoading.value = true
            val currentBill = _bill.value ?: return false

            Log.d(TAG, "🗑️ 开始物理删除销售单: ${currentBill.billNo}")

            // 使用VoidService进行物理删除（包含库存恢复）
            val result = voidService.voidBillWithConfirmation(currentBill.id)

            if (result.isSuccess && result.getOrNull() == true) {
                Log.d(TAG, "✅ 物理删除成功，库存已恢复")
                clearData() // 清除当前数据
                _operationResult.value = OperationResult.Success("报账单已物理删除，库存已恢复", needRefresh = true)
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

    // 打印成功后更新打印时间
    suspend fun markPrinted(): Boolean {
        return try {
            val currentBill = _bill.value ?: return false
            val updatedBill = currentBill.copy(printTime = System.currentTimeMillis())
            database.saleBillDao().update(updatedBill)
            loadBill(currentBill.id)
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新打印时间失败", e)
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