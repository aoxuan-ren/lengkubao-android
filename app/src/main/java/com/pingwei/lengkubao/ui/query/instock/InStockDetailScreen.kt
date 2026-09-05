// com.pingwei.lengkubao.ui.query.instock.InStockDetailScreen.kt
package com.pingwei.lengkubao.ui.query.instock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.query.common.InStockQueryPrintDialog
import com.pingwei.lengkubao.ui.query.instock.viewmodel.InStockDetailViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InStockDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteSuccess: () -> Unit, // 新增：删除成功回调
    viewModel: InStockDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 设置删除成功回调
    LaunchedEffect(Unit) {
        viewModel.setOnDeleteSuccessCallback {
            onDeleteSuccess() // 调用父组件传递的刷新回调
        }
    }

    // 收集操作结果
    val operationResult by viewModel.operationResult.collectAsState()

    // 监听操作结果，成功后自动执行回调
    LaunchedEffect(operationResult) {
        if (operationResult is InStockDetailViewModel.OperationResult.Success) {
            val successResult = operationResult as InStockDetailViewModel.OperationResult.Success
            if (successResult.needRefresh) {
                // 延迟一下，确保用户看到成功消息
                kotlinx.coroutines.delay(500)
                // 执行删除成功回调，这会触发列表页刷新并关闭详情页
                onDeleteSuccess()
            }
        }
    }

    // 操作结果对话框（仅用于错误情况）
    if (operationResult is InStockDetailViewModel.OperationResult.Error) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearOperationResult()
            },
            title = {
                Text("操作失败")
            },
            text = {
                Text((operationResult as InStockDetailViewModel.OperationResult.Error).message)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearOperationResult()
                }) {
                    Text("确定")
                }
            }
        )
    }

    // 加载数据
    LaunchedEffect(billId) {
        viewModel.loadBill(billId)
    }

    // 收集数据
    val bill by viewModel.bill.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showPrintDialog by remember { mutableStateOf(false) }

    InStockQueryPrintDialog(
        show = showPrintDialog,
        bill = bill,
        items = items,
        onDismiss = { showPrintDialog = false },
        onPrintSuccess = {
            coroutineScope.launch {
                viewModel.markPrinted()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("入库单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (bill != null && bill?.status != "2" && bill?.status != "VOIDED") {
                        // 作废/删除菜单
                        var expanded by remember { mutableStateOf(false) }

                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("标记作废") },
                                    onClick = {
                                        expanded = false
                                        showVoidConfirmation(
                                            context,
                                            "标记作废",
                                            "仅标记单据状态为作废，保留数据记录",
                                            isPhysicalDelete = false
                                        ) {
                                            coroutineScope.launch {
                                                viewModel.voidBill()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cancel, contentDescription = "标记作废")
                                    }
                                )

                                Divider()

                                DropdownMenuItem(
                                    text = { Text("物理删除") },
                                    onClick = {
                                        expanded = false
                                        showPhysicalDeleteConfirmation(
                                            context,
                                            "物理删除",
                                            "彻底删除单据和明细数据，同时还原库存。此操作不可恢复！",
                                            isPhysicalDelete = true
                                        ) {
                                            coroutineScope.launch {
                                                viewModel.deleteBillPermanently()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = "物理删除")
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }

                        IconButton(onClick = {
                            bill?.let { onEdit(it.id) }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "修改")
                        }

                        IconButton(
                            onClick = {
                                if (items.isNotEmpty()) {
                                    showPrintDialog = true
                                }
                            },
                            enabled = items.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "打印")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (bill == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "单据不存在",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("单据不存在或已被删除", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) {
                        Text("返回列表")
                    }
                }
            }
        } else  {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // 单据基本信息
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.pagePadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BillInfoRow(label = "单据号", value = bill!!.billNo)
                        BillInfoRow(label = "客户", value = bill!!.customerName ?: "")
                        BillInfoRow(label = "库位", value = bill!!.locationName)
                        BillInfoRow(label = "经手人", value = bill!!.operatorName ?: "")
                        BillInfoRow(label = "开单时间", value = formatTime(bill!!.createTime))
                        BillInfoRow(label = "状态", value = getStatusText(bill!!.status))
                        BillInfoRow(label = "同步状态", value = getSyncStatusText(bill!!.syncStatus))

                        if (bill!!.remark.isNotBlank()) {
                            Divider()
                            BillInfoRow(label = "备注", value = bill!!.remark)
                        }
                    }
                }

                // 商品明细
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.pagePadding)
                    ) {
                        Text(
                            text = "商品明细",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (items.isEmpty()) {
                            Text(
                                text = "无明细数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            // 表格头部
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("商品型号", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(2f))
                                Text("数量", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text("单价", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text("金额", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.5f))
                            }

                            Divider()

                            // 明细列表
                            items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        item.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(2f)
                                    )
                                    Text(
                                        "${item.quantity}箱",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "¥${String.format("%.2f", item.unitPrice)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "¥${String.format("%.2f", item.amount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }
                                HorizontalDivider(
                                    Modifier,
                                    DividerDefaults.Thickness,
                                    DividerDefaults.color
                                )
                            }

                            // 合计
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "合计",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "¥${String.format("%.2f", bill!!.totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 库存变动信息（如果需要）
                if (items.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding)
                        ) {
                            Text(
                                text = "库存变动",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${item.productName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "+${item.quantity}箱",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Green
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
    return sdf.format(java.util.Date(timestamp))
}

private fun getStatusText(status: String): String {
    return when (status) {
        "1", "COMPLETED" -> "正常"
        "2", "VOIDED" -> "作废"
        else -> status
    }
}

private fun getSyncStatusText(status: Int): String {
    return when (status) {
        0 -> "未同步"
        1 -> "已同步"
        else -> "同步失败"
    }
}

// 标记作废确认对话框
private fun showVoidConfirmation(
    context: Context,
    title: String,
    message: String,
    isPhysicalDelete: Boolean = false,
    onConfirm: () -> Unit
) {
    val builder = android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("确定") { _, _ -> onConfirm() }
        .setNegativeButton("取消", null)

    if (isPhysicalDelete) {
        builder.setIcon(android.R.drawable.ic_dialog_alert)
    }

    builder.show()
}

// 物理删除确认对话框（更严厉的警告）
private fun showPhysicalDeleteConfirmation(
    context: Context,
    title: String,
    message: String,
    isPhysicalDelete: Boolean = true,
    onConfirm: () -> Unit
) {
    val builder = android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage("⚠️ 警告：此操作不可撤销！\n\n$message\n\n确定要继续吗？")
        .setPositiveButton("确定删除") { _, _ -> onConfirm() }
        .setNegativeButton("取消", null)
        .setIcon(android.R.drawable.ic_dialog_alert)

    builder.show()
}