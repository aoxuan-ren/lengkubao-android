// ui/saleout/components/SaleItemDisplay.kt
package com.pingwei.lengkubao.ui.saleout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.ui.theme.AppDimens

// ui/saleout/components/SaleItemDisplay.kt
@Composable
fun SaleItemDisplay(
    item: com.pingwei.lengkubao.data.db.entity.SaleItem,
    index: Int,
    onRemove: (Int) -> Unit,
    onEdit: ((Int) -> Unit)? = null,
    isEnabled: Boolean = true // 新增参数
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = { if (isEnabled) onRemove(index) },
                modifier = Modifier.size(32.dp),
                enabled = isEnabled
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = if (isEnabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

// 空的销售商品列表提示
@Composable
fun EmptySaleItemsPlaceholder(
    onAddProduct: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddProduct() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = "暂无商品",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先选择库位查看可销售商品",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "选择库位后，商品列表将自动显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
// 销售汇总信息
@Composable
fun SaleSummaryInfo(
    totalQuantity: Double,
    totalAmount: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("销售种类：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$totalQuantity 箱",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("销售总金额：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "¥${String.format("%.2f", totalAmount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}