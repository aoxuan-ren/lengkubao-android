package com.pingwei.lengkubao.ui.presale

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.PayMethod
import com.pingwei.lengkubao.data.db.entity.PreSaleMode
import com.pingwei.lengkubao.data.db.entity.PreSaleStatus
import com.pingwei.lengkubao.ui.presale.viewmodel.PreSaleDetailViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme

class PreSaleDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BILL_ID = "BILL_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val operationResult by viewModel.operationResult.collectAsState()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showShipConfirm by remember { mutableStateOf(false) }
    var showVoidConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(billId) { viewModel.loadBill(billId) }

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
        var method by remember { mutableStateOf(PayMethod.CASH) }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("登记收款") },
            text = {
                Column {
                    Text("欠款: ¥${"%.2f".format(viewModel.unpaidAmount())}")
                    OutlinedTextField(amount, { amount = it }, label = { Text("金额") })
                    Row {
                        listOf(PayMethod.CASH, PayMethod.TRANSFER, PayMethod.OTHER).forEach { m ->
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

    if (showShipConfirm) {
        AlertDialog(
            onDismissRequest = { showShipConfirm = false },
            title = { Text("确认发货") },
            text = { Text("发货后将扣减库存，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.shipBill()
                    showShipConfirm = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showShipConfirm = false }) { Text("取消") } }
        )
    }

    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text("确认作废") },
            text = { Text("作废后将释放预占库存") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.voidBill()
                    showVoidConfirm = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showVoidConfirm = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预售单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                        Text("模式: ${if (b.saleMode == PreSaleMode.PRESALE) "预售" else "出库销售"} | 状态: ${b.status}")
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("应收: ¥${"%.2f".format(b.totalAmount)}")
                        Text("已收: ¥${"%.2f".format(b.paidAmount)}")
                        Text("欠款: ¥${"%.2f".format(viewModel.unpaidAmount())}", color = MaterialTheme.colorScheme.error)
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("商品明细", fontWeight = FontWeight.Medium)
                        items.forEach { item ->
                            Text("${item.productName}  ${item.quantity}${item.unit} × ¥${item.salePrice} = ¥${"%.2f".format(item.amount)}")
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

                if (b.status != PreSaleStatus.CANCELLED) {
                    Button(onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("登记收款")
                    }
                }
                if (b.status == PreSaleStatus.PRESALE) {
                    Button(onClick = { showShipConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("发货出库")
                    }
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
