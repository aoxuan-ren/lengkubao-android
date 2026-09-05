// ui/query/saleout/SaleOutDetailScreen.kt
package com.pingwei.lengkubao.ui.query.saleout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.entity.SaleItem
import kotlinx.coroutines.launch
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import com.pingwei.lengkubao.ui.query.common.SaleOutQueryPrintDialog
import com.pingwei.lengkubao.ui.query.saleout.viewmodel.SaleOutDetailViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleOutDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleteSuccess: () -> Unit, // 新增：删除成功回调参数
    viewModel: SaleOutDetailViewModel = viewModel()
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
        if (operationResult is SaleOutDetailViewModel.OperationResult.Success) {
            val successResult = operationResult as SaleOutDetailViewModel.OperationResult.Success
            if (successResult.needRefresh) {
                // 延迟一下，确保用户看到成功消息
                kotlinx.coroutines.delay(500)
                // 执行删除成功回调，这会触发列表页刷新并关闭详情页
                onDeleteSuccess()
            }
        }
    }

    // 操作结果对话框（仅用于错误情况）
    if (operationResult is SaleOutDetailViewModel.OperationResult.Error) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearOperationResult()
            },
            title = {
                Text("操作失败")
            },
            text = {
                Text((operationResult as SaleOutDetailViewModel.OperationResult.Error).message)
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

    // 加载销售单数据
    LaunchedEffect(billId) {
        viewModel.loadBill(billId)
    }

    // 收集数据
    val bill by viewModel.bill.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showPrintDialog by remember { mutableStateOf(false) }

    SaleOutQueryPrintDialog(
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
                title = { Text("报账单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (bill != null && bill?.status != "2") {
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
                                // 物理删除菜单项（危险操作）
                                DropdownMenuItem(
                                    text = {
                                        Text("物理删除", color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        expanded = false
                                        showPhysicalDeleteConfirmation(
                                            context,
                                            "物理删除报账单",
                                            "彻底删除报账单和明细数据，同时恢复库存。此操作不可恢复！"
                                        ) {
                                            coroutineScope.launch {
                                                viewModel.deleteBillPermanently()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "物理删除",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )

                                // 原有作废功能
                                DropdownMenuItem(
                                    text = { Text("标记作废") },
                                    onClick = {
                                        expanded = false
                                        showVoidConfirmation(context) {
                                            coroutineScope.launch {
                                                val result = viewModel.voidBill()
                                                if (result) {
                                                    Toast.makeText(context, "报账单已作废", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "作废失败", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cancel, contentDescription = "作废")
                                    }
                                )

                                // 编辑功能
                                DropdownMenuItem(
                                    text = { Text("修改") },
                                    onClick = {
                                        expanded = false
                                        onEdit()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = "修改")
                                    }
                                )

                                // 打印功能
                                DropdownMenuItem(
                                    text = { Text("打印") },
                                    onClick = {
                                        expanded = false
                                        if (items.isNotEmpty()) {
                                            showPrintDialog = true
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Print, contentDescription = "打印")
                                    }
                                )
                            }
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
                    } else if (bill != null) {
                        // 已作废单据只显示物理删除选项
                        IconButton(
                            onClick = {
                                showPhysicalDeleteConfirmation(
                                    context,
                                    "物理删除已作废报账单",
                                    "此报账单已作废，确定要彻底删除吗？删除后库存将恢复。此操作不可恢复！"
                                ) {
                                    coroutineScope.launch {
                                        viewModel.deleteBillPermanently()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "物理删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (bill == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("报账单不存在或已删除")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // 销售单基本信息卡片
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
                        BillInfoRow(label = "客户", value = bill!!.customerName ?: "未知客户")
                        BillInfoRow(label = "库位", value = bill!!.locationName ?: "无")
                        BillInfoRow(label = "经手人", value = bill!!.operatorName ?: "未知")
                        BillInfoRow(label = "开单时间", value = formatTime(bill!!.createTime))
                        BillInfoRow(label = "报账总量", value = "${bill!!.totalQuantity}${items.firstOrNull()?.unit ?: "件"}")
                        BillInfoRow(label = "状态", value = getStatusText(bill!!.status))
                        BillInfoRow(label = "同步状态", value = getSyncStatusText(bill!!.syncStatus))
                        if (bill!!.remark.isNotBlank()) {
                            Divider()
                            BillInfoRow(label = "备注", value = bill!!.remark)
                        }
                    }
                }

                // 商品明细卡片
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
                                text = "无商品明细数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            // 明细表格头部
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("商品名称", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(2f))
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
                                        "${item.quantity}${item.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "¥${String.format("%.2f", item.salePrice)}",
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
                                Divider()
                            }
                            // 合计行
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
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        "${bill!!.totalQuantity}${items.firstOrNull()?.unit ?: "件"}",
                                        style = MaterialTheme.typography.bodyMedium
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
                }

                // 库存变动提示（销售单为出库，显示减少量）
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
                                        "-${item.quantity}${item.unit}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Red // 出库用红色标识减少
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

// 通用单据信息行（复用入库/包装单逻辑）
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
// 新增：物理删除确认对话框
private fun showPhysicalDeleteConfirmation(
    context: Context,
    title: String,
    message: String,
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
// 时间格式化（复用通用逻辑）
private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
    return sdf.format(java.util.Date(timestamp))
}

// 销售单状态文本转换（适配SaleBill.status字段）
private fun getStatusText(status: String): String {
    return when (status.uppercase(java.util.Locale.ROOT)) {
        "1", "COMPLETED" -> "正常"
        "2", "VOIDED" -> "作废"
        "3", "SETTLED" -> "已结算"
        else -> status
    }
}

// 同步状态文本转换（适配SaleBill.syncStatus字段）
private fun getSyncStatusText(syncStatus: Int): String {
    return when (syncStatus) {
        0 -> "未同步"
        1 -> "已同步"
        else -> "同步失败"
    }
}

// 作废确认对话框（复用入库/包装单逻辑）
private fun showVoidConfirmation(context: Context, onConfirm: () -> Unit) {
    android.app.AlertDialog.Builder(context)
        .setTitle("确认作废")
        .setMessage("确定要作废此报账单吗？此操作不可撤销。")
        .setPositiveButton("确定作废") { _, _ -> onConfirm() }
        .setNegativeButton("取消", null)
        .show()
}