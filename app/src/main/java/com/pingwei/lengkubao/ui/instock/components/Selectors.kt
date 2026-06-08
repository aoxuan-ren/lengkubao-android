// ui/instock/components/Selectors.kt
package com.pingwei.lengkubao.ui.instock.components

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
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.utils.CustomerSearchFilter
import com.pingwei.lengkubao.utils.OperatorSearchFilter

// ==================== 客户选择对话框 ====================
@Composable
fun CustomerSelectorDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onCustomerSelected: (Customer) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filteredCustomers = remember(searchText, customers) {
        CustomerSearchFilter.filter(customers, searchText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择客户") },
        text = {
            Column {
                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(CustomerSearchFilter.PLACEHOLDER) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimens.buttonHeight),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                // 客户列表
                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = "暂无客户",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.itemSpacing))
                            Text(
                                text = if (searchText.isBlank()) "暂无客户数据" else "未找到相关客户",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                    ) {
                        items(filteredCustomers) { customer ->
                            ListItem(
                                headlineContent = {
                                    // 修复第86行：给可空的 customerName 设置默认值，确保 Text 接收非空 String
                                    Text(customer.customerName ?: "未知客户")
                                },
                                supportingContent = {
                                    Text("编号: ${customer.customerNo}")
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCustomerSelected(customer)
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

// ==================== 扫码选择客户对话框 ====================
@Composable
fun ScanCustomerDialog(
    onDismiss: () -> Unit,
    onScanRequest: () -> Unit,
    onManualSelect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择客户方式") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
            ) {
                // 扫码选择
                Button(
                    onClick = {
                        onScanRequest()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码")
                        Text("扫码选择客户")
                    }
                }

                // 手动选择
                Button(
                    onClick = {
                        onManualSelect()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "列表")
                        Text("从列表选择")
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

// ==================== 库位选择对话框 ====================
@Composable
fun LocationSelectorDialog(
    locations: List<Location>,
    onDismiss: () -> Unit,
    onLocationSelected: (Location) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择库位") },
        text = {
            if (locations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无库位数据")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(locations) { location ->
                        ListItem(
                            headlineContent = {
                                Text("${location.locationName} (${location.locationNo})")
                            },
                            supportingContent = {
                                if (location.description.isNotEmpty()) {
                                    Text(location.description)
                                }
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (location.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = location.enabled,
                                    onClick = {
                                        onLocationSelected(location)
                                        onDismiss()
                                    }
                                ),
                            colors = ListItemDefaults.colors(
                                containerColor = if (location.enabled)
                                    MaterialTheme.colorScheme.surface
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Divider()
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

// ==================== 经手人选择对话框 ====================
@Composable
fun OperatorSelectorDialog(
    operators: List<Operator>,
    onDismiss: () -> Unit,
    onOperatorSelected: (Operator) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filteredOperators = remember(searchText, operators) {
        OperatorSearchFilter.filter(operators, searchText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择经手人") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(OperatorSearchFilter.PLACEHOLDER) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimens.buttonHeight),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AppDimens.sectionSpacing))

                if (operators.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无经手人数据")
                    }
                } else if (filteredOperators.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonOff,
                                contentDescription = "未找到经手人",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.itemSpacing))
                            Text(
                                text = "未找到相关经手人",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = AppDimens.dialogListMaxHeight)
                    ) {
                        items(filteredOperators) { operator ->
                            ListItem(
                                headlineContent = { Text(operator.name) },
                                supportingContent = {
                                    Text("编号: ${operator.operatorNo}")
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (operator.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = operator.enabled,
                                        onClick = {
                                            onOperatorSelected(operator)
                                            onDismiss()
                                        }
                                    )
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

// ==================== 商品选择对话框（简化版，避免编译错误） ====================
@Composable
fun ProductSelectorDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onProductSelected: (Product, quantity: Int) -> Unit
) {
    var quantity by remember { mutableStateOf("1") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择商品") },
        text = {
            Column {
                // 商品列表
                if (products.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无商品数据")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(products.filter { it.enabled }) { product ->
                            ListItem(
                                headlineContent = {
                                    Text("${product.productName} (${product.productNo})")
                                },
                                supportingContent = {
                                    Text("单位: ${product.unit}")
                                },
                                leadingContent = {
                                    RadioButton(
                                        selected = selectedProduct?.id == product.id,
                                        onClick = { selectedProduct = product }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProduct = product }
                            )
                            Divider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 数量输入（简化版，避免KeyboardOptions错误）
                if (selectedProduct != null) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { newValue ->
                            // 只允许输入数字
                            if (newValue.all { it.isDigit() } && newValue.isNotEmpty()) {
                                quantity = newValue
                            } else if (newValue.isEmpty()) {
                                quantity = ""
                            }
                        },
                        label = { Text("数量") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Text(selectedProduct?.unit ?: "箱")
                        }
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedProduct?.let { product ->
                            val qty = quantity.toIntOrNull() ?: 1
                            if (qty > 0) {
                                onProductSelected(product, qty)
                                onDismiss()
                            }
                        }
                    },
                    enabled = selectedProduct != null && quantity.isNotEmpty() && quantity.toIntOrNull() ?: 0 > 0
                ) {
                    Text("确定")
                }
            }
        }
    )
}

// ==================== 商品项目显示组件 ====================
@Composable
fun ProductItemDisplay(
    item: com.pingwei.lengkubao.data.db.entity.InStockItem,
    index: Int,
    onRemove: (Int) -> Unit
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
                .padding(AppDimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "编号: ${item.productNo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "数量: ${item.quantity}${item.unit} 单价: ¥${String.format("%.2f", item.unitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "¥${String.format("%.2f", item.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}