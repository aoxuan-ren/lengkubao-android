package com.pingwei.lengkubao.ui.query.packaging

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.query.packaging.viewmodel.PackagingDetailViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens
import kotlinx.coroutines.launch
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleteSuccess: () -> Unit, // 删除成功回调参数
    viewModel: PackagingDetailViewModel = viewModel()
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
        if (operationResult is PackagingDetailViewModel.OperationResult.Success) {
            val successResult = operationResult as PackagingDetailViewModel.OperationResult.Success
            if (successResult.needRefresh) {
                // 延迟一下，确保用户看到成功消息
                kotlinx.coroutines.delay(500)
                // 执行删除成功回调，这会触发列表页刷新并关闭详情页
                onDeleteSuccess()
            }
        }
    }

    // 操作结果对话框（仅用于错误情况）
    if (operationResult is PackagingDetailViewModel.OperationResult.Error) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearOperationResult()
            },
            title = {
                Text("操作失败")
            },
            text = {
                Text((operationResult as PackagingDetailViewModel.OperationResult.Error).message)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("包装单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (bill != null && !bill!!.isVoided) {
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
                                            "物理删除包装单",
                                            "彻底删除包装单和明细数据。此操作不可恢复！"
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

                                // 原有功能保持不变
                                DropdownMenuItem(
                                    text = { Text("标记作废") },
                                    onClick = {
                                        expanded = false
                                        showVoidConfirmation(context) {
                                            coroutineScope.launch {
                                                viewModel.voidBill()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cancel, contentDescription = "作废")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("修改") },
                                    onClick = {
                                        expanded = false
                                        bill?.let { onEdit(it.id) }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = "修改")
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("打印") },
                                    onClick = {
                                        expanded = false
                                        coroutineScope.launch {
                                            val result = viewModel.printBill()
                                            if (result) {
                                                Toast.makeText(context, "打印状态已更新", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "打印状态更新失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Print, contentDescription = "打印")
                                    }
                                )
                            }
                        }
                    } else if (bill != null) {
                        // 已作废单据只显示物理删除选项
                        IconButton(
                            onClick = {
                                showPhysicalDeleteConfirmation(
                                    context,
                                    "物理删除已作废包装单",
                                    "此包装单已作废，确定要彻底删除吗？此操作不可恢复！"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    Text("包装单不存在或已删除")
                }
            } else {
                // 整体滑动布局 - 所有内容作为一个整体滑动
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ========== 包装单基本信息卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 标题行：包含包装类型标记
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "包装单信息",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // 【新增】显示取包装/退包装标记
                                val flag = bill!!.packagingTypeFlag
                                val (flagText, flagIcon, flagColor) = if (flag == "TAKE") {
                                    Triple("取包装", Icons.Default.ExitToApp, MaterialTheme.colorScheme.primary)
                                } else {
                                    Triple("退包装", Icons.Default.ArrowBack, MaterialTheme.colorScheme.error)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        flagIcon,
                                        contentDescription = flagText,
                                        tint = flagColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        flagText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = flagColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Divider()

                            BillInfoRow(label = "单据号", value = bill!!.billNo)
                            BillInfoRow(label = "客户", value = bill!!.customerName ?: "")
                            BillInfoRow(label = "经手人", value = bill!!.operatorName ?: "")
                            BillInfoRow(label = "开单时间", value = formatTime(bill!!.createTime))
                            BillInfoRow(label = "单据日期", value = bill!!.billDate)

                            // 状态行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "状态",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                                ) {
                                    // 作废状态
                                    if (bill!!.isVoided) {
                                        Badge(
                                            containerColor = Color.Red.copy(alpha = 0.2f),
                                            contentColor = Color.Red
                                        ) {
                                            Text("作废")
                                        }
                                    } else {
                                        Badge(
                                            containerColor = Color.Green.copy(alpha = 0.2f),
                                            contentColor = Color.Green
                                        ) {
                                            Text("正常")
                                        }
                                    }

                                    // 打印状态
                                    if (bill!!.isPrinted) {
                                        Badge(
                                            containerColor = Color.Blue.copy(alpha = 0.2f),
                                            contentColor = Color.Blue
                                        ) {
                                            Text("已打印")
                                        }
                                    }

                                    // 同步状态
                                    if (bill!!.isSynced) {
                                        Badge(
                                            containerColor = Color.Green.copy(alpha = 0.2f),
                                            contentColor = Color.Green
                                        ) {
                                            Text("已同步")
                                        }
                                    } else {
                                        Badge(
                                            containerColor = Color(0xFFFF9800).copy(alpha = 0.2f),
                                            contentColor = Color(0xFFFF9800)
                                        ) {
                                            Text("未同步")
                                        }
                                    }
                                }
                            }

                            if (bill!!.remark.isNotBlank()) {
                                Divider()
                                BillInfoRow(label = "备注", value = bill!!.remark)
                            }
                        }
                    }

                    // ========== 包装明细卡片 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding)
                        ) {
                            Text(
                                text = "包装明细",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (items.isEmpty()) {
                                Text(
                                    text = "无包装明细数据",
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
                                    Text("包装类型", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(2f))
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
                                            item.packagingTypeName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(2f)
                                        )
                                        Text(
                                            "${item.quantity}${item.unit}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "¥${String.format("%.2f", item.unitPrice)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "¥${String.format("%.2f", item.subtotal)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (bill!!.packagingTypeFlag == "RETURN" && item.subtotal > 0)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1.5f)
                                        )
                                    }
                                    Divider()
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
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "${items.sumOf { it.quantity }}${items.firstOrNull()?.unit ?: "件"}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "¥${String.format("%.2f", bill!!.totalAmount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (bill!!.packagingTypeFlag == "RETURN" && bill!!.totalAmount > 0)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ========== 包装统计卡片 ==========
                    if (items.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(AppDimens.pagePadding)
                            ) {
                                Text(
                                    text = "包装统计",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // 按包装类型分组统计
                                val groupedItems = items.groupBy { it.packagingTypeId }

                                groupedItems.forEach { (typeId, itemList) ->
                                    val typeName = itemList.firstOrNull()?.packagingTypeName ?: "未知包装类型"
                                    val totalQuantity = itemList.sumOf { it.quantity }.toLong()
                                    val totalAmount = itemList.sumOf { it.subtotal.toDouble() }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            typeName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                                        ) {
                                            Text(
                                                "${totalQuantity}${itemList.firstOrNull()?.unit ?: "件"}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "¥${String.format("%.2f", totalAmount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (bill!!.packagingTypeFlag == "RETURN" && totalAmount > 0)
                                                    MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                // 总计
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "总计",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "${items.sumOf { it.quantity }}${items.firstOrNull()?.unit ?: "件"}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "¥${String.format("%.2f", bill!!.totalAmount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (bill!!.packagingTypeFlag == "RETURN" && bill!!.totalAmount > 0)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 底部留白
                    Spacer(modifier = Modifier.height(16.dp))
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

// 物理删除确认对话框
private fun showPhysicalDeleteConfirmation(
    context: Context,
    title: String,
    message: String,
    onConfirm: () -> Unit
) {
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage("⚠️ 警告：此操作不可撤销！\n\n$message\n\n确定要继续吗？")
        .setPositiveButton("确定删除") { _, _ -> onConfirm() }
        .setNegativeButton("取消", null)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show()
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    return sdf.format(Date(timestamp))
}

private fun showVoidConfirmation(context: Context, onConfirm: () -> Unit) {
    android.app.AlertDialog.Builder(context)
        .setTitle("确认作废")
        .setMessage("确定要作废此包装单吗？此操作不可撤销。")
        .setPositiveButton("确定作废") { _, _ -> onConfirm() }
        .setNegativeButton("取消", null)
        .show()
}