// ui/packaging/components/PackagingTypeSelectorDialog.kt
package com.pingwei.lengkubao.ui.packaging.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.db.entity.PackagingType
import com.pingwei.lengkubao.ui.packaging.viewmodel.PackagingInputItem
import com.pingwei.lengkubao.ui.theme.AppDimens

@Composable
fun PackagingTypeSelectorDialog(
    packagingInputs: List<PackagingInputItem>,
    onDismiss: () -> Unit,
    onPackagingTypeSelected: (PackagingType) -> Unit
) {
    // 获取所有包装类型（从输入项中提取）
    val allPackagingTypes = packagingInputs.map { it.packagingType }
    val enabledPackagingTypes = allPackagingTypes.filter { it.enabled }

    // 过滤掉已经添加过的包装类型（数量大于0）
    val availablePackagingTypes = remember(packagingInputs) {
        enabledPackagingTypes.filter { packagingType ->
            packagingInputs.find { it.packagingType.id == packagingType.id }?.quantity == 0
        }
    }

    var searchText by remember { mutableStateOf("") }
    val filteredTypes = remember(searchText, availablePackagingTypes) {
        if (searchText.isBlank()) {
            availablePackagingTypes
        } else {
            availablePackagingTypes.filter { type ->
                type.typeName.contains(searchText, ignoreCase = true) ||
                        type.typeNo.contains(searchText, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择包装类型") },
        text = {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("搜索包装类型") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 已添加的包装类型提示
                val addedCount = packagingInputs.count { it.quantity > 0 }
                if (addedCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已添加 $addedCount 种包装类型",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已添加",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.itemSpacing))
                }

                // 包装类型列表
                if (filteredTypes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = "暂无包装类型",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.itemSpacing))
                            Text(
                                text = if (searchText.isBlank()) {
                                    if (availablePackagingTypes.isEmpty()) {
                                        "所有包装类型已添加或已禁用"
                                    } else {
                                        "暂无可用包装类型"
                                    }
                                } else {
                                    "未找到相关包装类型"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(filteredTypes) { packagingType ->
                            PackagingTypeItem(
                                packagingType = packagingType,
                                onClick = {
                                    onPackagingTypeSelected(packagingType)
                                    onDismiss()
                                }
                            )
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun PackagingTypeItem(
    packagingType: PackagingType,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(packagingType.typeName)
        },
        supportingContent = {
            Column {
                Text("编号: ${packagingType.typeNo}")
                Text("单价: ¥${String.format("%.2f", packagingType.unitPrice)}/${packagingType.unit}")
                if (packagingType.remark.isNotEmpty()) {
                    Text(
                        text = "备注: ${packagingType.remark}",
                        maxLines = 1
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                Icons.Default.Inventory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}