package com.pingwei.lengkubao.ui.instock

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pingwei.lengkubao.data.db.entity.InStockItem
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.utils.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 入库开单打印扩展
 */
fun InStockActivity.setupPrinting() {
    // 修复1：使用自定义工厂类创建 SaleBillPrinter 实例（解决带 Context 构造参数问题）
    val printerFactory = SaleBillPrinterFactory(this)
    val printerViewModel = ViewModelProvider(this, printerFactory)[SaleBillPrinter::class.java]

    // 启动时自动检查打印机连接
    lifecycleScope.launch {
        delay(500) // 等待UI加载

        // 检查打印机状态，如果未连接则自动初始化
        val printService = SunmiPrintService.getInstance(this@setupPrinting)
        val connectionState = printService.getConnectionState()
        val canPrint = connectionState["can_print"] as? Boolean ?: false

        if (!canPrint) {
            // 尝试自动恢复
            Log.d("InStock", "检测到打印机未连接，尝试自动恢复")
            printerViewModel.initializePrinter(retryCount  = 1)
        }
    }

    // 使用 StateFlow 的 collect 来监听状态变化
    lifecycleScope.launch {
        printerViewModel.printStatus.collectLatest { status ->
            // 修复2：直接使用 PrintStatus（已导入），移除 SaleBillPrinter 限定，修正引用
            when (status) {
                is PrintStatus.Success -> {
                    ToastUtil.show(this@setupPrinting, "打印成功")
                }
                is PrintStatus.Error -> {
                    // 修复3：直接访问 error.message，解决 Unresolved reference 'message'
                    ToastUtil.show(this@setupPrinting, "打印失败: ${status.message}")
                }
                else -> {}
            }
        }
    }
}

/**
 * 打印入库单
 */
fun InStockActivity.printInStockBill(
    billNo: String,
    customerName: String,
    customerCode: String,
    location: String,
    operator: String,
    items: List<InStockItem>,
    remark: String = "无备注"
) {
    // 修复1：使用自定义工厂类创建 SaleBillPrinter 实例（解决带 Context 构造参数问题）
    val printerFactory = SaleBillPrinterFactory(this)
    val printerViewModel = ViewModelProvider(this, printerFactory)[SaleBillPrinter::class.java]

    val printItems = items.map { item ->
        InStockItemPrint(
            productName = item.productName,
            // 核心修复：Int 转 Double，匹配 InStockItemPrint.quantity 的类型要求
            quantity = item.quantity.toDouble()
        )
    }

    val totalItems = items.size
    // 修复4：类型不匹配问题，将 Int 转为 Double（两处参数统一处理）
    val totalQuantity = items.sumOf { it.quantity }.toDouble()

    printerViewModel.printInStockBill(
        billNo = billNo,
        customerName = customerName,
        customerCode = customerCode,
        location = location,
        operator = operator,
        items = printItems,
        // 修复5：totalItems 保持 Int（对应 SaleBillPrinter 方法定义），无需转换
        totalItems = totalItems,
        // 修复4：传入转换后的 Double 类型，匹配方法参数要求
        totalQuantity = totalQuantity,
        remark = remark
    )
}