package com.pingwei.lengkubao.ui.presale

import android.app.Activity
import android.content.BroadcastReceiver
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.PayMethod
import com.pingwei.lengkubao.data.db.entity.PreSaleMode
import com.pingwei.lengkubao.data.db.entity.PreSaleStatus
import com.pingwei.lengkubao.ui.presale.viewmodel.PreSaleDetailViewModel
import com.pingwei.lengkubao.ui.query.common.PreSaleQueryPrintDialog
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreSaleDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BILL_ID = "BILL_ID"
    }

    private var yearChangeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        yearChangeReceiver = registerFinishOnFiscalYearChanged()
        val billId = intent.getLongExtra(EXTRA_BILL_ID, 0L)
        setContent {
            LengkubaoTheme {
                PreSaleDetailScreen(
                    billId = billId,
                    onBack = { finish() },
                    onChanged = {
                        setResult(Activity.RESULT_OK)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        unregisterFinishOnFiscalYearChanged(yearChangeReceiver)
        yearChangeReceiver = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreSaleDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: PreSaleDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val bill by viewModel.bill.collectAsState()
    val items by viewModel.items.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val outboundRecords by viewModel.outboundRecords.collectAsState()
    val operationResult by viewModel.operationResult.collectAsState()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showOutboundDialog by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }

    LaunchedEffect(billId) { viewModel.loadBill(billId) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, billId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadBill()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(operationResult) {
        when (val r = operationResult) {
            is PreSaleDetailViewModel.OperationResult.Success -> {
                Toast.makeText(context, r.message, Toast.LENGTH_SHORT).show()
                onChanged()
                viewModel.clearOperationResult()
            }
            is PreSaleDetailViewModel.OperationResult.Error -> {
                Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                viewModel.clearOperationResult()
            }
            null -> {}
        }
    }

    if (showPaymentDialog) {
        var amount by remember { mutableStateOf("") }
        var method by remember { mutableStateOf(PayMethod.WECHAT) }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("登记收款") },
            text = {
                Column {
                    Text("欠款: ¥${"%.2f".format(viewModel.unpaidAmount())}")
                    OutlinedTextField(amount, { amount = it }, label = { Text("金额") })
                    Row {
                        listOf(PayMethod.WECHAT, PayMethod.TRANSFER, PayMethod.OTHER).forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.recordPayment(amount.toDoubleOrNull() ?: 0.0, method, "")
                    showPaymentDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPaymentDialog = false }) { Text("取消") } }
        )
    }

    if (showOutboundDialog) {
        OutboundDialog(
            items = items,
            remainingQuantity = viewModel::remainingQuantity,
            onDismiss = { showOutboundDialog = false },
            onConfirm = { quantities ->
                viewModel.recordOutbound(quantities)
                showOutboundDialog = false
            }
        )
    }

    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text("确认作废") },
            text = {
                val hasShipped = items.any { it.shippedQuantity > 0 }
                Text(
                    if (hasShipped) "作废后将释放未出库部分的预占库存，已出库部分不受影响"
                    else "作废后将释放预占库存"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.voidBill()
                    showVoidConfirm = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showVoidConfirm = false }) { Text("取消") } }
        )
    }

    PreSaleQueryPrintDialog(
        show = showPrintDialog,
        bill = bill,
        items = items,
        onDismiss = { showPrintDialog = false },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预售单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (bill != null && bill?.status != PreSaleStatus.CANCELLED && items.isNotEmpty()) {
                        IconButton(onClick = { showPrintDialog = true }) {
                            Icon(Icons.Default.Print, contentDescription = "打印")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val b = bill
        if (b == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.billNo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("买家: ${b.buyerName} (${b.buyerNo})")
                        Text("库位: ${b.locationName} | 经手: ${b.operatorName}")
                        Text("模式: ${if (b.saleMode == PreSaleMode.PRESALE) "预售" else "已售"} | 状态: ${statusLabel(b.status)}")
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("应收: ¥${"%.2f".format(b.totalAmount)}")
                        Text("已收: ¥${"%.2f".format(b.paidAmount)}")
                        Text("欠款: ¥${"%.2f".format(viewModel.unpaidAmount())}", color = MaterialTheme.colorScheme.error)
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("商品明细", fontWeight = FontWeight.Medium)
                        items.forEach { item ->
                            val remaining = viewModel.remainingQuantity(item)
                            val shippedText = if (item.shippedQuantity > 0) {
                                "（已出 ${item.shippedQuantity}${item.unit}，剩余 $remaining${item.unit}）"
                            } else {
                                ""
                            }
                            Text("${item.productName}  ${item.quantity}${item.unit} × ¥${item.salePrice} = ¥${"%.2f".format(item.amount)}$shippedText")
                        }
                    }
                }

                if (payments.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("收款记录", fontWeight = FontWeight.Medium)
                            payments.forEach { p ->
                                Text("¥${"%.2f".format(p.amount)} (${p.payMethod})")
                            }
                        }
                    }
                }

                if (outboundRecords.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("出库记录", fontWeight = FontWeight.Medium)
                            val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                            outboundRecords.forEach { recordWithItems ->
                                val timeStr = dateFormat.format(Date(recordWithItems.record.shipTime))
                                val itemDesc = recordWithItems.items.joinToString("、") {
                                    "${it.productName} ${it.quantity}${it.unit}"
                                }
                                Text("$timeStr  $itemDesc")
                            }
                        }
                    }
                }

                if (b.status != PreSaleStatus.CANCELLED) {
                    Button(onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("登记收款")
                    }
                }
                if (viewModel.canOutbound()) {
                    Button(onClick = { showOutboundDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("发货出库")
                    }
                }
                if (viewModel.canVoid()) {
                    OutlinedButton(
                        onClick = { showVoidConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("作废")
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboundDialog(
    items: List<com.pingwei.lengkubao.data.db.entity.PreSaleItem>,
    remainingQuantity: (com.pingwei.lengkubao.data.db.entity.PreSaleItem) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>) -> Unit,
) {
    val outboundableItems = items.filter { remainingQuantity(it) > 0 }
    val quantities = remember(outboundableItems.map { it.productNo }) {
        mutableStateMapOf<String, String>().apply {
            outboundableItems.forEach { put(it.productNo, "") }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发货出库") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("填写本次出库数量，可分多次出库直到全部发完", style = MaterialTheme.typography.bodySmall)
                outboundableItems.forEach { item ->
                    val remaining = remainingQuantity(item)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${item.productName}（剩余 $remaining${item.unit}）", fontWeight = FontWeight.Medium)
                        OutlinedTextField(
                            value = quantities[item.productNo] ?: "",
                            onValueChange = { quantities[item.productNo] = it.filter { c -> c.isDigit() } },
                            label = { Text("本次出库") },
                            suffix = { Text(item.unit) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = quantities.mapNotNull { (productNo, text) ->
                    val qty = text.toIntOrNull() ?: 0
                    if (qty > 0) productNo to qty else null
                }.toMap()
                onConfirm(parsed)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun statusLabel(status: String): String = when (status) {
    PreSaleStatus.PRESALE -> "预售中"
    PreSaleStatus.COMPLETED -> "已出库"
    PreSaleStatus.SHIPPED -> "部分出库"
    PreSaleStatus.CANCELLED -> "已作废"
    else -> status
}
