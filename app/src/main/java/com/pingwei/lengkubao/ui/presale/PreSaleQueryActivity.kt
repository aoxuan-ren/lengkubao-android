package com.pingwei.lengkubao.ui.presale

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.PreSaleBill
import com.pingwei.lengkubao.data.db.entity.PreSaleMode
import com.pingwei.lengkubao.data.db.entity.PreSaleStatus
import com.pingwei.lengkubao.ui.presale.viewmodel.PreSaleQueryViewModel
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.ui.theme.LengkubaoTheme
import java.text.SimpleDateFormat
import java.util.*

class PreSaleQueryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LengkubaoTheme {
                PreSaleQueryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreSaleQueryScreen(
    onBack: () -> Unit,
    viewModel: PreSaleQueryViewModel = viewModel()
) {
    val context = LocalContext.current
    val bills by viewModel.bills.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var timeRangeLabel by remember { mutableStateOf("7天") }

    val detailLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshData(searchText, timeRangeLabel)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshData("", "7天")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预售单查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData(searchText, timeRangeLabel) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(AppDimens.pagePadding)
        ) {
            Text(
                "预售单/收款数据尚未同步电脑端",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    viewModel.refreshData(it, timeRangeLabel)
                },
                label = { Text("单号/买家") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QueryTimeRangeUtils.PRESET_LABELS.forEach { label ->
                    FilterChip(
                        selected = timeRangeLabel == label,
                        onClick = {
                            timeRangeLabel = label
                            viewModel.refreshData(searchText, label)
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bills, key = { it.id }) { bill ->
                    PreSaleBillListItem(bill) {
                        detailLauncher.launch(
                            Intent(context, PreSaleDetailActivity::class.java).apply {
                                putExtra(PreSaleDetailActivity.EXTRA_BILL_ID, bill.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreSaleBillListItem(bill: PreSaleBill, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val unpaid = (bill.totalAmount - bill.paidAmount).coerceAtLeast(0.0)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bill.billNo, fontWeight = FontWeight.Bold)
                Text(statusLabel(bill.status))
            }
            Text("买家: ${bill.buyerName}")
            Text("模式: ${if (bill.saleMode == PreSaleMode.PRESALE) "预售" else "出库销售"}")
            Text("总额 ¥${"%.2f".format(bill.totalAmount)} | 已收 ¥${"%.2f".format(bill.paidAmount)} | 欠款 ¥${"%.2f".format(unpaid)}")
            Text(dateFmt.format(Date(bill.createTime)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    PreSaleStatus.PRESALE -> "预售中"
    PreSaleStatus.COMPLETED -> "已出库"
    PreSaleStatus.SHIPPED -> "已发货"
    PreSaleStatus.CANCELLED -> "已作废"
    else -> status
}
