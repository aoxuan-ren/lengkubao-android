package com.pingwei.lengkubao.ui.query.common

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.InStockBill
import com.pingwei.lengkubao.data.db.entity.InStockItem
import com.pingwei.lengkubao.data.db.entity.PackagingBill
import com.pingwei.lengkubao.data.db.entity.PackagingItem
import com.pingwei.lengkubao.data.db.entity.PreSaleBill
import com.pingwei.lengkubao.data.db.entity.PreSaleItem
import com.pingwei.lengkubao.data.db.entity.SaleBill
import com.pingwei.lengkubao.data.db.entity.SaleItem
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.service.model.InStockItemPrint
import com.pingwei.lengkubao.service.model.PackagingItemPrint
import com.pingwei.lengkubao.service.model.PreSaleItemPrint
import com.pingwei.lengkubao.service.model.SaleItemPrint
import com.pingwei.lengkubao.ui.common.printer.PrintStatus
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinter
import com.pingwei.lengkubao.ui.common.printer.SaleBillPrinterFactory
import com.pingwei.lengkubao.ui.theme.AppDimens
import kotlinx.coroutines.delay

private const val TAG = "QueryBillPrint"

@Composable
fun InStockQueryPrintDialog(
    show: Boolean,
    bill: InStockBill?,
    items: List<InStockItem>,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit
) {
    if (!show || bill == null) return

    val context = LocalContext.current
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsState()

    var printMessage by remember(show) { mutableStateOf("正在准备打印...") }
    var isPrinting by remember(show) { mutableStateOf(false) }
    var hasStarted by remember(show) { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            printerViewModel.initializePrinter(retryCount = 1)
        }
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "打印成功！"
                isPrinting = false
                Toast.makeText(context, "打印成功", Toast.LENGTH_SHORT).show()
                onPrintSuccess()
                delay(600)
                onDismiss()
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                Toast.makeText(context, "打印失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            else -> {}
        }
    }

    LaunchedEffect(show, bill.id) {
        if (!show || hasStarted || items.isEmpty()) return@LaunchedEffect
        hasStarted = true
        isPrinting = true
        printMessage = "正在获取单据信息..."

        try {
            val printItems = items.map { item ->
                InStockItemPrint(
                    productName = item.productName,
                    quantity = item.quantity.toDouble(),
                    unit = item.unit
                )
            }

            printMessage = "正在连接打印机..."
            val printService = SunmiPrintService.getInstance(context)
            var connected = false
            var attempts = 0
            while (!connected && attempts < 3) {
                attempts++
                printMessage = "连接打印机尝试 $attempts/3..."
                connected = printService.ensureConnectedForPrint()
                if (!connected) delay(1000)
            }

            if (!connected) {
                printMessage = "❌ 打印机连接失败，请检查设备"
                isPrinting = false
                return@LaunchedEffect
            }

            printMessage = "正在打印..."
            printerViewModel.printInStockBill(
                billNo = bill.billNo,
                customerName = bill.customerName ?: "未知客户",
                customerCode = bill.customerNo,
                location = bill.locationName,
                operator = bill.operatorName,
                items = printItems,
                totalItems = items.size,
                totalQuantity = bill.totalQuantity.toDouble(),
                remark = bill.remark.ifBlank { "无备注" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "入库单打印异常", e)
            printMessage = "❌ 打印异常: ${e.message}"
            isPrinting = false
        }
    }

    BillPrintProgressDialog(
        title = "📄 打印入库单",
        message = printMessage,
        isPrinting = isPrinting,
        onDismiss = onDismiss
    )
}

@Composable
fun PackagingQueryPrintDialog(
    show: Boolean,
    bill: PackagingBill?,
    items: List<PackagingItem>,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit
) {
    if (!show || bill == null) return

    val context = LocalContext.current
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsState()

    var printMessage by remember(show) { mutableStateOf("正在准备打印...") }
    var isPrinting by remember(show) { mutableStateOf(false) }
    var hasStarted by remember(show) { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            printerViewModel.initializePrinter(retryCount = 1)
        }
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "打印成功！"
                isPrinting = false
                Toast.makeText(context, "打印成功", Toast.LENGTH_SHORT).show()
                onPrintSuccess()
                delay(600)
                onDismiss()
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                Toast.makeText(context, "打印失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            else -> {}
        }
    }

    LaunchedEffect(show, bill.id) {
        if (!show || hasStarted || items.isEmpty()) return@LaunchedEffect
        hasStarted = true
        isPrinting = true
        printMessage = "正在获取单据信息..."

        try {
            val printItems = items.map { item ->
                PackagingItemPrint(
                    packagingTypeFlag = item.packagingTypeFlag,
                    packagingType = item.packagingTypeName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    amount = if (item.amount > 0) item.amount else item.subtotal
                )
            }

            printMessage = "正在连接打印机..."
            val printService = SunmiPrintService.getInstance(context)
            var connected = false
            var attempts = 0
            while (!connected && attempts < 3) {
                attempts++
                printMessage = "连接打印机尝试 $attempts/3..."
                connected = printService.ensureConnectedForPrint()
                if (!connected) delay(1000)
            }

            if (!connected) {
                printMessage = "❌ 打印机连接失败，请检查设备"
                isPrinting = false
                return@LaunchedEffect
            }

            printMessage = "正在打印..."
            printerViewModel.printPackagingBill(
                billNo = bill.billNo,
                customerName = bill.customerName,
                customerCode = bill.customerNo,
                operator = bill.operatorName,
                items = printItems,
                totalAmount = bill.totalAmount,
                remark = bill.remark.ifBlank { "无备注" },
                creator = bill.operatorName
            )
        } catch (e: Exception) {
            Log.e(TAG, "包装单打印异常", e)
            printMessage = "❌ 打印异常: ${e.message}"
            isPrinting = false
        }
    }

    BillPrintProgressDialog(
        title = "📄 打印包装单",
        message = printMessage,
        isPrinting = isPrinting,
        onDismiss = onDismiss
    )
}

@Composable
fun PreSaleQueryPrintDialog(
    show: Boolean,
    bill: PreSaleBill?,
    items: List<PreSaleItem>,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit = {},
) {
    if (!show || bill == null) return

    val context = LocalContext.current
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsState()

    var printMessage by remember(show) { mutableStateOf("正在准备打印...") }
    var isPrinting by remember(show) { mutableStateOf(false) }
    var hasStarted by remember(show) { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            printerViewModel.initializePrinter(retryCount = 1)
        }
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "打印成功！"
                isPrinting = false
                Toast.makeText(context, "打印成功", Toast.LENGTH_SHORT).show()
                onPrintSuccess()
                delay(600)
                onDismiss()
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                Toast.makeText(context, "打印失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            else -> {}
        }
    }

    LaunchedEffect(show, bill.id) {
        if (!show || hasStarted || items.isEmpty()) return@LaunchedEffect
        hasStarted = true
        isPrinting = true
        printMessage = "正在获取单据信息..."

        try {
            val printItems = items.map { item ->
                PreSaleItemPrint(
                    productName = item.productName,
                    quantity = item.quantity,
                    unit = item.unit,
                    salePrice = item.salePrice,
                    amount = item.amount,
                )
            }

            printMessage = "正在连接打印机..."
            val printService = SunmiPrintService.getInstance(context)
            var connected = false
            var attempts = 0
            while (!connected && attempts < 3) {
                attempts++
                printMessage = "连接打印机尝试 $attempts/3..."
                connected = printService.ensureConnectedForPrint()
                if (!connected) delay(1000)
            }

            if (!connected) {
                printMessage = "❌ 打印机连接失败，请检查设备"
                isPrinting = false
                return@LaunchedEffect
            }

            printMessage = "正在打印..."
            printerViewModel.printPreSaleBill(
                billNo = bill.billNo,
                buyerName = bill.buyerName,
                locationName = bill.locationName,
                operatorName = bill.operatorName,
                saleMode = bill.saleMode,
                items = printItems,
                totalAmount = bill.totalAmount,
                paidAmount = bill.paidAmount,
                remark = bill.remark,
            )
        } catch (e: Exception) {
            Log.e(TAG, "预售单打印异常", e)
            printMessage = "❌ 打印异常: ${e.message}"
            isPrinting = false
        }
    }

    BillPrintProgressDialog(
        title = "📄 打印预售单",
        message = printMessage,
        isPrinting = isPrinting,
        onDismiss = onDismiss
    )
}

@Composable
fun SaleOutQueryPrintDialog(
    show: Boolean,
    bill: SaleBill?,
    items: List<SaleItem>,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit = {},
) {
    if (!show || bill == null) return

    val context = LocalContext.current
    val printerFactory = SaleBillPrinterFactory(context)
    val printerViewModel: SaleBillPrinter = viewModel(factory = printerFactory)
    val printStatus by printerViewModel.printStatus.collectAsState()

    var printMessage by remember(show) { mutableStateOf("正在准备打印...") }
    var isPrinting by remember(show) { mutableStateOf(false) }
    var hasStarted by remember(show) { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            printerViewModel.initializePrinter(retryCount = 1)
        }
    }

    LaunchedEffect(printStatus) {
        when (printStatus) {
            is PrintStatus.Success -> {
                printMessage = "打印成功！"
                isPrinting = false
                Toast.makeText(context, "打印成功", Toast.LENGTH_SHORT).show()
                onPrintSuccess()
                delay(600)
                onDismiss()
            }
            is PrintStatus.Error -> {
                val error = printStatus as PrintStatus.Error
                printMessage = "❌ ${error.message}"
                isPrinting = false
                Toast.makeText(context, "打印失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            is PrintStatus.Printing -> {
                printMessage = "正在打印中..."
                isPrinting = true
            }
            else -> {}
        }
    }

    LaunchedEffect(show, bill.id) {
        if (!show || hasStarted || items.isEmpty()) return@LaunchedEffect
        hasStarted = true
        isPrinting = true
        printMessage = "正在获取单据信息..."

        try {
            val printItems = items.map { item ->
                SaleItemPrint(
                    productName = item.productName,
                    quantity = item.quantity,
                    unit = item.unit,
                    unitPrice = item.salePrice,
                    amount = item.amount,
                )
            }

            printMessage = "正在连接打印机..."
            val printService = SunmiPrintService.getInstance(context)
            var connected = false
            var attempts = 0
            while (!connected && attempts < 3) {
                attempts++
                printMessage = "连接打印机尝试 $attempts/3..."
                connected = printService.ensureConnectedForPrint()
                if (!connected) delay(1000)
            }

            if (!connected) {
                printMessage = "❌ 打印机连接失败，请检查设备"
                isPrinting = false
                return@LaunchedEffect
            }

            printMessage = "正在打印..."
            printerViewModel.printSaleBill(
                billNo = bill.billNo,
                customerName = bill.customerName,
                locationName = bill.locationName,
                operatorName = bill.operatorName,
                items = printItems,
                totalAmount = bill.totalAmount,
                totalQuantity = bill.totalQuantity,
                remark = bill.remark,
            )
        } catch (e: Exception) {
            Log.e(TAG, "报账单打印异常", e)
            printMessage = "❌ 打印异常: ${e.message}"
            isPrinting = false
        }
    }

    BillPrintProgressDialog(
        title = "📄 打印报账单",
        message = printMessage,
        isPrinting = isPrinting,
        onDismiss = onDismiss
    )
}

@Composable
private fun BillPrintProgressDialog(
    title: String,
    message: String,
    isPrinting: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isPrinting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
            ) {
                Text(message)
                if (isPrinting) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPrinting
            ) {
                Text(if (isPrinting) "打印中..." else "关闭")
            }
        }
    )
}
