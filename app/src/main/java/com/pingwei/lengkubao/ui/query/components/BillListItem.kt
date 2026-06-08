package com.pingwei.lengkubao.ui.query.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.*

/**
 * 通用单据列表项（新增同步状态展示）
 */
@Composable
fun BillListItem(
    billNo: String,
    customerName: String,
    createTime: Long,
    totalAmount: Double,
    status: String,
    locationName: String? = null,
    packagingType: String? = null,
    // 新增同步状态参数，兼容两种传参方式
    syncStatus: Int = 0, // 0-未同步，1-已同步
    isSynced: Boolean = false, // 包装单专用同步状态标记
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 初始化 SimpleDateFormat
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    val timeText = sdf.format(Date(createTime))

    // 调试日志
    val TAG = "BillListItem"
    LaunchedEffect(billNo) {
        Log.d(TAG, "🔹 开始渲染列表项")
        Log.d(TAG, "   单据号: $billNo")
        Log.d(TAG, "   客户名称: $customerName")
        Log.d(TAG, "   创建时间: $createTime (格式化后: $timeText)")
        Log.d(TAG, "   合计金额: ¥${String.format("%.2f", totalAmount)}")
        Log.d(TAG, "   单据状态: $status (转换后: ${getStatusText(status)})")
        Log.d(TAG, "   库位信息: ${locationName ?: "无"}")
        Log.d(TAG, "   包装类型: ${packagingType ?: "无"}")
        Log.d(TAG, "   同步状态-syncStatus: $syncStatus")
        Log.d(TAG, "   同步状态-isSynced: $isSynced")
    }

    // 单据状态颜色/文本
    val statusColor = getStatusColor(status)
    val statusText = getStatusText(status)

    // 解析同步状态：优先判断isSynced，其次判断syncStatus，兼容两种业务场景
    val (syncBadgeText, syncBadgeColor) = when {
        isSynced || syncStatus == 1 -> Pair("已同步", Color.Green)
        else -> Pair("未同步", Color(0xFFFF9800)) // 橙色
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
        ) {
            // 第一行：单据号 + 单据状态 + 同步状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = billNo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 状态徽章组：单据状态 + 同步状态
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 原单据状态徽章
                    Badge(
                        containerColor = statusColor.copy(alpha = 0.2f),
                        contentColor = statusColor,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // 新增：同步状态徽章
                    Badge(
                        containerColor = syncBadgeColor.copy(alpha = 0.2f),
                        contentColor = syncBadgeColor
                    ) {
                        Text(
                            text = syncBadgeText,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            // 第二行：客户 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = customerName.ifBlank { "未知客户" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 第三行：详细信息（库位/包装类型 + 金额）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    locationName?.takeIf { it.isNotBlank() }?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "库位",
                                modifier = Modifier.size(AppDimens.iconMedium),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    packagingType?.takeIf { it.isNotBlank() }?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = "包装类型",
                                modifier = Modifier.size(AppDimens.iconMedium),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 金额展示
                val displayAmount = if (totalAmount.isNaN() || totalAmount < 0) 0.0 else totalAmount
                Text(
                    text = "¥${String.format("%.2f", displayAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 提取状态颜色获取逻辑，提升代码可维护性
 */
private fun getStatusColor(status: String): Color {
    return when (status.uppercase(Locale.ROOT)) {
        "1", "COMPLETED" -> Color.Green
        "2", "VOIDED" -> Color.Red
        else -> Color.Gray
    }
}

/**
 * 提取状态文本获取逻辑，提升代码可维护性
 */
private fun getStatusText(status: String): String {
    return when (status.uppercase(Locale.ROOT)) {
        "1", "COMPLETED" -> "正常"
        "2", "VOIDED" -> "作废"
        else -> status.ifBlank { "未知状态" }
    }
}