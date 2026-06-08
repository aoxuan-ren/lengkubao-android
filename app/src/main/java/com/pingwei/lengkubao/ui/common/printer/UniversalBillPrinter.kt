// ui/common/printer/UniversalBillPrinter.kt
package com.pingwei.lengkubao.ui.common.printer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.model.BillType
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.service.model.PrintResult
import com.pingwei.lengkubao.service.model.PrinterStatus as ServicePrinterStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 通用单据打印机管理器
 * 支持入库单、包装单两种单据类型（已移除销售单）
 */
class UniversalBillPrinter(context: Context) : ViewModel() {

    private val printService = SunmiPrintService.getInstance(context)

    // 状态流
    private val _printStatus = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val printStatus: StateFlow<PrintStatus> = _printStatus.asStateFlow()

    private val _printerInfo = MutableStateFlow<ServicePrinterStatus?>(null)
    val printerInfo: StateFlow<ServicePrinterStatus?> = _printerInfo.asStateFlow()

    private val _printHistory = MutableStateFlow<List<PrintResult>>(emptyList())
    val printHistory: StateFlow<List<PrintResult>> = _printHistory.asStateFlow()

    /**
     * 初始化打印机
     */
    fun initializePrinter() {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Initializing
            try {
                val success = printService.initialize()
                if (success) {
                    updatePrinterInfo()
                    _printStatus.value = PrintStatus.Ready
                } else {
                    _printStatus.value = PrintStatus.Error("打印机初始化失败")
                }
            } catch (e: Exception) {
                _printStatus.value = PrintStatus.Error("初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 更新打印机信息
     */
    private suspend fun updatePrinterInfo() {
        try {
            val status = printService.checkPrinterStatus()
            val info = printService.getPrinterInfo()

            _printerInfo.value = ServicePrinterStatus(
                isConnected = true,
                statusMessage = status,
                lastChecked = Date()
            )
        } catch (e: Exception) {
            _printerInfo.value = ServicePrinterStatus(
                isConnected = false,
                statusMessage = "获取信息失败: ${e.message}",
                lastChecked = Date()
            )
        }
    }

    /**
     * 检查打印机状态
     */
    fun checkStatus() {
        viewModelScope.launch {
            try {
                updatePrinterInfo()
            } catch (e: Exception) {
                _printerInfo.value = ServicePrinterStatus(
                    isConnected = false,
                    statusMessage = "检查状态失败: ${e.message}",
                    lastChecked = Date()
                )
            }
        }
    }

    /**
     * 打印入库单
     */
    fun printInStockBill(
        billNo: String,
        customerName: String,
        customerCode: String,
        location: String,
        operator: String,
        items: List<InStockItemPrint>,
        totalItems: Int,
        totalQuantity: Double,
        remark: String = "无备注",
        creator: String = "系统"
    ) {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Printing
            try {
                val success = printService.printInStockBill(
                    billNo = billNo,
                    customerName = customerName,
                    location = location,
                    operator = operator,
                    items = items,
                    totalItems = totalItems,
                    totalQuantity = totalQuantity
                )

                val result = PrintResult(
                    success = success,
                    message = if (success) "打印成功" else "打印失败",
                    billNo = billNo,
                    billType = BillType.IN_STOCK
                )

                addToHistory(result)

                if (success) {
                    _printStatus.value = PrintStatus.Success
                } else {
                    _printStatus.value = PrintStatus.Error("打印失败，请检查打印机状态")
                }
            } catch (e: Exception) {
                val result = PrintResult(
                    success = false,
                    message = "打印异常: ${e.message}",
                    billNo = billNo,
                    billType = BillType.IN_STOCK
                )

                addToHistory(result)
                _printStatus.value = PrintStatus.Error("打印异常: ${e.message}")
            }
        }
    }

    /**
     * 打印包装单
     */
    fun printPackagingBill(
        billNo: String,
        customerName: String,
        customerCode: String,
        operator: String,
        items: List<PackagingItemPrint>,
        totalAmount: Double,
        remark: String = "无备注",
        creator: String = "系统"
    ) {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Printing
            try {
                val success = printService.printPackagingBill(
                    billNo = billNo,
                    customerName = customerName,
                    operator = operator,
                    items = items,
                    totalAmount = totalAmount
                )

                val result = PrintResult(
                    success = success,
                    message = if (success) "打印成功" else "打印失败",
                    billNo = billNo,
                    billType = BillType.PACKAGING
                )

                addToHistory(result)

                if (success) {
                    _printStatus.value = PrintStatus.Success
                } else {
                    _printStatus.value = PrintStatus.Error("打印失败，请检查打印机状态")
                }
            } catch (e: Exception) {
                val result = PrintResult(
                    success = false,
                    message = "打印异常: ${e.message}",
                    billNo = billNo,
                    billType = BillType.PACKAGING
                )

                addToHistory(result)
                _printStatus.value = PrintStatus.Error("打印异常: ${e.message}")
            }
        }
    }

    /**
     * 测试打印
     */
    fun testPrint() {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Printing
            try {
                val success = printService.testPrint()
                val result = PrintResult(
                    success = success,
                    message = if (success) "测试打印成功" else "测试打印失败"
                )

                addToHistory(result)

                if (success) {
                    _printStatus.value = PrintStatus.Success
                } else {
                    _printStatus.value = PrintStatus.Error("测试打印失败")
                }
            } catch (e: Exception) {
                val result = PrintResult(
                    success = false,
                    message = "测试异常: ${e.message}"
                )

                addToHistory(result)
                _printStatus.value = PrintStatus.Error("测试异常: ${e.message}")
            }
        }
    }

    /**
     * 添加到历史记录
     */
    private fun addToHistory(result: PrintResult) {
        val history = _printHistory.value.toMutableList()
        history.add(0, result)
        _printHistory.value = history
    }

    /**
     * 清除打印历史
     */
    fun clearPrintHistory() {
        _printHistory.value = emptyList()
    }

    /**
     * 重置打印状态
     */
    fun resetStatus() {
        _printStatus.value = PrintStatus.Idle
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        printService.disconnect()
    }
}

/**
 * 自定义 ViewModel 工厂
 */
class UniversalBillPrinterFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UniversalBillPrinter::class.java)) {
            return UniversalBillPrinter(context) as T
        }
        throw IllegalArgumentException("未知的 ViewModel 类: ${modelClass.name}")
    }
}
