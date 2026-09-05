package com.pingwei.lengkubao.ui.query.advancededuction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.service.TcpSyncService
import com.pingwei.lengkubao.ui.common.ConfigManager
import com.pingwei.lengkubao.ui.query.QueryTimeRangeUtils
import com.pingwei.lengkubao.ui.query.advancededuction.viewmodel.AdvanceDeductionQueryViewModel
import com.pingwei.lengkubao.ui.query.components.QueryFilterBar
import com.pingwei.lengkubao.ui.theme.AppDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceDeductionQueryScreen(
    onBack: () -> Unit,
    viewModel: AdvanceDeductionQueryViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configManager = remember { ConfigManager(context) }

    fun launchUiTask(taskName: String, block: suspend () -> Unit) {
        runCatching {
            coroutineScope.launch { block() }
        }.onFailure { e ->
            val isDisposedScope = e.message?.contains("rememberCoroutineScope left the composition") == true
            if (isDisposedScope) {
                Log.w(TAG, "页面已销毁，忽略任务：$taskName")
            } else {
                Log.e(TAG, "$taskName 失败", e)
            }
        }
    }

    var searchText by remember { mutableStateOf("") }
    var timeRangeLabel by remember { mutableStateOf(viewModel.savedTimeRangeLabel) }
    var selectedRecord by remember { mutableStateOf<AdvanceDeductionQueryRecord?>(null) }

    val records by viewModel.records.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != TcpSyncService.ACTION_SYNC_COMPLETE) return
                val isSuccess = intent.getBooleanExtra(TcpSyncService.EXTRA_RESULT, false)
                val billId = intent.getLongExtra(TcpSyncService.EXTRA_BILL_ID_BROADCAST, 0)
                val billType = intent.getStringExtra(TcpSyncService.EXTRA_BILL_TYPE_BROADCAST)
                if (billId > 0 && (billType == "ADVANCE" || billType == "DEDUCTION")) {
                    launchUiTask("同步完成后刷新预支扣款列表") {
                        delay(300)
                        viewModel.refreshData()
                        val typeLabel = if (billType == "ADVANCE") "预支" else "扣款"
                        val message = if (isSuccess) "同步成功" else "同步失败"
                        Toast.makeText(context, "$typeLabel($billId) $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TcpSyncService.ACTION_SYNC_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.search(
            keyword = null,
            startTime = QueryTimeRangeUtils.getStartTime(timeRangeLabel),
            endTime = QueryTimeRangeUtils.getEndTime(timeRangeLabel)
        )
    }

    val onTimeRangeSelected: (String) -> Unit = { label ->
        timeRangeLabel = label
        configManager.saveQueryTimeRangeLabel(
            ConfigManager.QueryTimeRangeType.ADVANCE_DEDUCTION,
            label
        )
        launchUiTask("时间筛选") {
            viewModel.search(
                keyword = searchText.takeIf { it.isNotBlank() },
                startTime = QueryTimeRangeUtils.getStartTime(label),
                endTime = QueryTimeRangeUtils.getEndTime(label)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预支扣款查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            TcpSyncService.syncPendingNow(context)
                            Toast.makeText(context, "已提交后台同步请求", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "同步未完成单据",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            launchUiTask("手动刷新") { viewModel.refreshData() }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            QueryFilterBar(
                searchText = searchText,
                onSearchTextChange = {
                    searchText = it
                    viewModel.updateKeyword(it)
                },
                onSearch = {
                    launchUiTask("条件搜索") {
                        viewModel.search(
                            keyword = searchText.takeIf { it.isNotBlank() },
                            startTime = QueryTimeRangeUtils.getStartTime(timeRangeLabel),
                            endTime = QueryTimeRangeUtils.getEndTime(timeRangeLabel)
                        )
                    }
                },
                timeRangeLabel = timeRangeLabel,
                onTimeRangeClick = { onTimeRangeSelected("今天") },
                onFilterChipClick = { label -> onTimeRangeSelected(label) },
                modifier = Modifier.padding(AppDimens.itemSpacing)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.itemSpacing)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    records.isEmpty() -> {
                        Text(
                            text = "暂无预支/扣款记录",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                        ) {
                            items(records, key = { "${it.billType}-${it.id}" }) { record ->
                                AdvanceDeductionListItem(
                                    record = record,
                                    onClick = { selectedRecord = record },
                                    onSyncClick = {
                                        if (record.syncStatus != 1) {
                                            TcpSyncService.syncBillNow(
                                                context,
                                                record.id,
                                                record.billType
                                            )
                                            Toast.makeText(
                                                context,
                                                "正在同步${record.typeLabel}...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRecord?.let { record ->
        AdvanceDeductionDetailDialog(
            record = record,
            onDismiss = { selectedRecord = null },
            onSyncClick = {
                if (record.syncStatus != 1) {
                    TcpSyncService.syncBillNow(context, record.id, record.billType)
                    Toast.makeText(context, "正在同步${record.typeLabel}...", Toast.LENGTH_SHORT).show()
                }
                selectedRecord = null
            }
        )
    }
}

@Composable
private fun AdvanceDeductionListItem(
    record: AdvanceDeductionQueryRecord,
    onClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    val timeText = remember(record.createTime) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(record.createTime))
    }
    val typeColor = if (record.type == AdvanceDeductionRecordType.ADVANCE) {
        Color(0xFF2196F3)
    } else {
        Color(0xFFFF9800)
    }
    val syncText = when (record.syncStatus) {
        1 -> "已同步"
        else -> "未同步"
    }
    val syncColor = if (record.syncStatus == 1) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = typeColor
                    )
                    Text(
                        text = record.typeLabel,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                TextButton(
                    onClick = onSyncClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(text = syncText, color = syncColor, fontSize = 11.sp)
                }
            }

            Text(
                text = "${record.customerName} (${record.customerNo})",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "¥${String.format("%.2f", record.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp
                )
                Text(
                    text = "$timeText · ${record.recordDate}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!record.reason.isNullOrBlank()) {
                Text(
                    text = record.reason,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AdvanceDeductionDetailDialog(
    record: AdvanceDeductionQueryRecord,
    onDismiss: () -> Unit,
    onSyncClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${record.typeLabel}详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("客户", "${record.customerName} (${record.customerNo})")
                DetailRow("金额", "¥${String.format("%.2f", record.amount)}")
                DetailRow("业务日期", record.recordDate)
                DetailRow(
                    "创建时间",
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                        .format(Date(record.createTime))
                )
                if (record.type == AdvanceDeductionRecordType.DEDUCTION) {
                    DetailRow("数量", record.quantity.toString())
                    DetailRow("单价", "¥${String.format("%.2f", record.unitPrice)}")
                }
                DetailRow("事由", record.reason ?: "-")
                DetailRow("经手人", record.handler ?: "-")
                DetailRow(
                    "同步状态",
                    when (record.syncStatus) {
                        0 -> "未同步"
                        1 -> "已同步"
                        2 -> "同步中"
                        3 -> "失败"
                        else -> "未知"
                    }
                )
            }
        },
        confirmButton = {
            Row {
                if (record.syncStatus != 1) {
                    TextButton(onClick = onSyncClick) {
                        Text("重新同步")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label：",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp)
        )
        Text(text = value)
    }
}

private const val TAG = "AdvanceDeductionQuery"
