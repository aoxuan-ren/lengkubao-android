package com.pingwei.lengkubao.ui.packaging

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pingwei.lengkubao.data.db.entity.PackagingItem
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.utils.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 包装记账打印扩展
 */
fun PackagingActivity.setupPrinting() {
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
            Log.d("Packaging", "检测到打印机未连接，尝试自动恢复")
            printerViewModel.initializePrinter(retryCount  = 1)
        }
    }

    lifecycleScope.launch {
        printerViewModel.printStatus.collectLatest { status ->
            // 修复2：直接使用顶层密封类 PrintStatus，移除 SaleBillPrinter 限定
            when (status) {
                is PrintStatus.Success -> {
                    ToastUtil.show(this@setupPrinting, "打印成功")
                }
                is PrintStatus.Error -> {
                    // 修复3：正常访问 Error 子类的 message 属性，解决未解析问题
                    ToastUtil.show(this@setupPrinting, "打印失败: ${status.message}")
                }
                else -> {}
            }
        }
    }
}

/**
 * 打印包装单
 */
fun PackagingActivity.printPackagingBill(
    billNo: String,
    customerName: String,
    customerCode: String,
    operator: String,
    items: List<PackagingItem>,
    totalAmount: Double,
    remark: String = "无备注"
) {
    // 修复1：使用自定义工厂类创建 SaleBillPrinter 实例（解决带 Context 构造参数问题）
    val printerFactory = SaleBillPrinterFactory(this)
    val printerViewModel = ViewModelProvider(this, printerFactory)[SaleBillPrinter::class.java]

    val printItems = items.map { item ->
        PackagingItemPrint(
            // 修复4：修正 PackagingItem 实体类的属性名（适配实际字段，两种可选方案，优先方案1）
            // 方案1：若实体类属性名是 packagingName/type 等，替换为实际存在的属性（推荐，此处以 packagingType 为例，若实际是其他名称可修改）
            packagingType = item.packagingType, // 确保 PackagingItem 中存在该属性，若不存在替换为实际字段（如 item.type / item.packagingName）
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            amount = item.quantity * item.unitPrice
        )
    }

    printerViewModel.printPackagingBill(
        billNo = billNo,
        customerName = customerName,
        customerCode = customerCode,
        operator = operator,
        items = printItems,
        totalAmount = totalAmount,
        remark = remark
    )
}