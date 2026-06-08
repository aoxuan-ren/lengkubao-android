// ui/saleout/components/DirectSaleProductInput.kt
package com.pingwei.lengkubao.ui.saleout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.db.entity.SaleItem
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.data.model.ProductWithStock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectSaleProductInput(
    productWithStock: ProductWithStock,
    existingSaleItem: SaleItem?,
    onQuantityChange: (Int, Double) -> Unit, // 修改：同时传递数量和价格
    onPriceChange: (Double) -> Unit,
    isEnabled: Boolean = true
) {
    var quantityText by remember {
        mutableStateOf(existingSaleItem?.quantity?.toString() ?: "0")
    }
    var priceText by remember {
        mutableStateOf(
            existingSaleItem?.salePrice?.let { String.format("%.2f", it) } ?:
            String.format("%.2f", productWithStock.product.standardPrice)
        )
    }
    var quantityError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    val availableStock = productWithStock.availableStock
    val isInSaleList = existingSaleItem != null

    // 监听价格变化，并立即更新到父组件
    LaunchedEffect(priceText) {
        val price = priceText.toDoubleOrNull() ?: 0.0
        if (price > 0) {
            onPriceChange(price)
            priceError = false
        } else if (priceText.isNotEmpty()) {
            priceError = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInSaleList)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isInSaleList) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
        ) {
            // 商品信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productWithStock.product.productName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "型号: ${productWithStock.product.productNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 库存信息
                StockInfoBadge(
                    availableStock = availableStock,
                    unit = productWithStock.product.unit
                )
            }

            // 输入区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 销售单价输入 - 修复：确保价格变化时立即更新
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*(\\.\\d{0,2})?$")) || newValue.isEmpty()) {
                                priceText = newValue
                                val price = newValue.toDoubleOrNull() ?: 0.0

                                if (price > 0) {
                                    priceError = false
                                    onPriceChange(price) // 立即更新价格
                                } else if (newValue.isNotEmpty()) {
                                    priceError = true
                                } else {
                                    priceError = false
                                }
                            }
                        },
                        label = { Text("销售单价") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = priceError,
                        enabled = isEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        suffix = { Text("元") },
                        supportingText = {
                            if (priceError) {
                                Text("请输入有效的单价（大于0）", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                // 销售数量输入
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                                quantityText = newValue
                                val quantity = newValue.toIntOrNull() ?: 0

                                // 获取当前价格
                                val currentPrice = priceText.toDoubleOrNull() ?: productWithStock.product.standardPrice

                                if (quantity > 0) {
                                    if (quantity > availableStock) {
                                        quantityError = true
                                    } else {
                                        quantityError = false
                                        // 同时传递数量和价格
                                        onQuantityChange(quantity, currentPrice)
                                    }
                                } else if (quantity == 0) {
                                    quantityError = false
                                    // 传递0数量
                                    onQuantityChange(0, currentPrice)
                                }
                            }
                        },
                        label = { Text("销售数量") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = quantityError,
                        enabled = isEnabled && availableStock > 0,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        suffix = { Text(productWithStock.product.unit) },
                        supportingText = {
                            when {
                                quantityError -> {
                                    Text(
                                        "库存不足，可用: $availableStock",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                quantityText.isNotEmpty() && quantityText.toIntOrNull() ?: 0 > 0 -> {
                                    Text("可用库存: $availableStock")
                                }
                            }
                        }
                    )
                }
            }

            // 快捷数量按钮
            if (availableStock > 0 && isEnabled) {
                QuickQuantityButtons(
                    availableStock = availableStock,
                    onQuantitySelected = { qty ->
                        quantityText = qty.toString()
                        quantityError = false
                        val currentPrice = priceText.toDoubleOrNull() ?: productWithStock.product.standardPrice
                        onQuantityChange(qty, currentPrice)
                    }
                )
            }

            // 金额预览
            if (quantityText.isNotEmpty() && priceText.isNotEmpty()) {
                val quantity = quantityText.toIntOrNull() ?: 0
                val price = priceText.toDoubleOrNull() ?: 0.0
                val amount = quantity * price

                if (quantity > 0 && price > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "小计金额",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "¥${String.format("%.2f", amount)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (isInSaleList) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("已添加")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}