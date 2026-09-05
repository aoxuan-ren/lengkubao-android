//com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter.kt
package com.pingwei.lengkubao.ui.common.printer

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// 正确导入路径（统一使用该路径，删除原有其他路径的导入）
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.service.model.PreSaleItemPrint
import com.pingwei.lengkubao.service.model.SaleItemPrint
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.model.BillType
import com.pingwei.lengkubao.service.model.PrintResult
import com.pingwei.lengkubao.service.model.PrinterStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 打印状态（提取为顶层密封类，方便外部访问）
 */
sealed class PrintStatus {
    object Idle : PrintStatus()
    object Initializing : PrintStatus()
    object Ready : PrintStatus()
    object Printing : PrintStatus()
    object Success : PrintStatus()
    data class Error(val message: String) : PrintStatus()
}

/**
 * 打印管理器（已删除销售单打印功能）
 * 作为ViewModel，管理打印状态和打印操作
 */
class SaleBillPrinter(context: Context) : ViewModel() {

    private val printService = SunmiPrintService.getInstance(context)

    // 状态流（使用顶层 PrintStatus）
    private val _printStatus = MutableStateFlow<PrintStatus>(PrintStatus.Idle)
    val printStatus: StateFlow<PrintStatus> = _printStatus.asStateFlow()

    private val _printerInfo = MutableStateFlow<PrinterStatus?>(null)
    val printerInfo: StateFlow<PrinterStatus?> = _printerInfo.asStateFlow()

    private val _printHistory = MutableStateFlow<List<PrintResult>>(emptyList())
    val printHistory: StateFlow<List<PrintResult>> = _printHistory.asStateFlow()

    /**
     * 增强版初始化打印机（支持状态恢复）
     */
    fun initializePrinterWithRecovery(maxRetries: Int = 2) {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Initializing

            try {
                // 首先尝试检查现有连接
                val currentState = printService.getConnectionState()
                val canPrint = currentState["can_print"] as? Boolean ?: false

                if (canPrint) {
                    Log.d("SaleBillPrinter", "打印机已连接，跳过初始化")
                    updatePrinterInfo()
                    _printStatus.value = PrintStatus.Ready
                    return@launch
                }

                // 如果没有可用连接，尝试自动恢复
                Log.d("SaleBillPrinter", "尝试自动恢复打印机连接")
                val recovered = printService.checkAndAutoRecover()

                if (recovered) {
                    updatePrinterInfo()
                    _printStatus.value = PrintStatus.Ready
                } else {
                    // 自动恢复失败，进行完整初始化
                    Log.d("SaleBillPrinter", "自动恢复失败，进行完整初始化")

                    var success = false
                    var retryCount = 0
                    var lastError: String? = null

                    while (!success && retryCount < maxRetries) {
                        retryCount++
                        Log.d("SaleBillPrinter", "初始化尝试 $retryCount/$maxRetries")

                        try {
                            success = printService.initialize()
                            if (success) {
                                // 等待一小段时间让连接稳定
                                delay(300)
                                // 验证打印机状态
                                val status = printService.getConnectionStatus()
                                val printerStatusCode = status["printerStatusCode"] as? Int ?: -1

                                if (printerStatusCode != 505) { // 505表示未检测到打印机
                                    updatePrinterInfo()
                                    _printStatus.value = PrintStatus.Ready
                                    Log.d("SaleBillPrinter", "打印机初始化成功")
                                    return@launch
                                } else {
                                    lastError = "打印机未检测到 (505)"
                                    success = false
                                }
                            }
                        } catch (e: Exception) {
                            lastError = "初始化异常: ${e.message}"
                            Log.e("SaleBillPrinter", "初始化失败: $lastError", e)
                        }

                        // 等待后重试
                        if (!success && retryCount < maxRetries) {
                            delay(1000L * retryCount)
                        }
                    }

                    // 所有尝试都失败
                    _printStatus.value = PrintStatus.Error(
                        lastError ?: "打印机初始化失败，请检查连接"
                    )
                }

            } catch (e: Exception) {
                _printStatus.value = PrintStatus.Error("初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 初始化打印机
     * 为了保持兼容性，同时支持 retryCount 和 maxRetries 参数名
     */
    fun initializePrinter(retryCount: Int = 3) {
        initializePrinterWithRecovery(maxRetries = retryCount)
    }

    /**
     * 添加一个别名方法，支持 maxRetries 参数名
     */
    fun initializePrinterWithMaxRetries(maxRetries: Int = 3) {
        initializePrinterWithRecovery(maxRetries = maxRetries)
    }

    /**
     * 更新打印机信息
     */
    private suspend fun updatePrinterInfo() {
        try {
            val status = printService.checkPrinterStatus()
            val info = printService.getPrinterInfo()

            _printerInfo.value = PrinterStatus(
                isConnected = true,
                statusMessage = status,
                lastChecked = Date()
            )
        } catch (e: Exception) {
            _printerInfo.value = PrinterStatus(
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
                _printerInfo.value = PrinterStatus(
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
        totalItems: Int,  // 保持为 Int
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

                // 添加到历史记录
                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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

                // 添加到历史记录
                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

                _printStatus.value = PrintStatus.Error("打印异常: ${e.message}")
            }
        }
    }

    /**
     * 打印预售/出库销售单
     */
    fun printPreSaleBill(
        billNo: String,
        buyerName: String,
        locationName: String,
        operatorName: String,
        saleMode: String,
        items: List<PreSaleItemPrint>,
        totalAmount: Double,
        paidAmount: Double,
        remark: String = "",
    ) {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Printing
            try {
                val success = printService.printPreSaleBill(
                    billNo = billNo,
                    buyerName = buyerName,
                    locationName = locationName,
                    operatorName = operatorName,
                    saleMode = saleMode,
                    items = items,
                    totalAmount = totalAmount,
                    paidAmount = paidAmount,
                    remark = remark,
                )

                val result = PrintResult(
                    success = success,
                    message = if (success) "打印成功" else "打印失败",
                    billNo = billNo,
                    billType = BillType.PRESALE,
                )

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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
                    billType = BillType.PRESALE,
                )

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

                _printStatus.value = PrintStatus.Error("打印异常: ${e.message}")
            }
        }
    }

    /**
     * 打印报账单（销售出库单）
     */
    fun printSaleBill(
        billNo: String,
        customerName: String,
        locationName: String,
        operatorName: String,
        items: List<SaleItemPrint>,
        totalAmount: Double,
        totalQuantity: Int,
        remark: String = "",
    ) {
        viewModelScope.launch {
            _printStatus.value = PrintStatus.Printing
            try {
                val success = printService.printSaleBill(
                    billNo = billNo,
                    customerName = customerName,
                    locationName = locationName,
                    operatorName = operatorName,
                    items = items,
                    totalAmount = totalAmount,
                    totalQuantity = totalQuantity,
                    remark = remark,
                )

                val result = PrintResult(
                    success = success,
                    message = if (success) "打印成功" else "打印失败",
                    billNo = billNo,
                    billType = BillType.SALE,
                )

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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
                    billType = BillType.SALE,
                )

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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

                // 添加到历史记录
                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

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

                val history = _printHistory.value.toMutableList()
                history.add(0, result)
                _printHistory.value = history

                _printStatus.value = PrintStatus.Error("测试异常: ${e.message}")
            }
        }
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
     * 清理资源（优化版）
     */
    override fun onCleared() {
        super.onCleared()
        // 注意：这里不再强制断开连接，因为打印服务是单例
        // 应该由应用生命周期管理连接状态
        Log.d("SaleBillPrinter", "ViewModel 被清理")
    }
}