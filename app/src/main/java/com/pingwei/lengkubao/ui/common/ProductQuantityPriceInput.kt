package com.pingwei.lengkubao.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pingwei.lengkubao.data.model.ProductWithStock
import com.pingwei.lengkubao.ui.theme.AppDimens

enum class ProductInputField {
    QUANTITY, PRICE
}

/**
 * 商品行数量/单价输入（点击后清空并弹出自定义数字键盘，与客户报账一致）
 */
@Composable
fun ProductQuantityPriceInput(
    productWithStock: ProductWithStock,
    existingQuantity: Int,
    existingPrice: Double,
    onQuantityChange: (Int, Double) -> Unit,
    isEnabled: Boolean = true,
    stockLabel: String = "",
    modifier: Modifier = Modifier
) {
    val quantityText = if (existingQuantity > 0) existingQuantity.toString() else ""
    val priceText = if (existingPrice > 0) existingPrice.toString() else ""

    var localQuantityText by remember { mutableStateOf(quantityText) }
    var localPriceText by remember { mutableStateOf(priceText) }
    var showCustomKeyboard by remember { mutableStateOf(false) }
    var activeInputField by remember { mutableStateOf<ProductInputField?>(null) }

    LaunchedEffect(quantityText, priceText) {
        if (!showCustomKeyboard) {
            localQuantityText = quantityText
            localPriceText = priceText
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = productWithStock.product.productName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 72.dp)
                )

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (stockLabel.isNotEmpty()) {
                            Text(
                                text = stockLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = productWithStock.availableStock.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFA000)
                        )
                    }
                }

                ProductInputBox(
                    modifier = Modifier.weight(0.9f),
                    placeholder = "数量",
                    displayText = localQuantityText,
                    enabled = isEnabled,
                    onClick = {
                        activeInputField = ProductInputField.QUANTITY
                        localQuantityText = ""
                        showCustomKeyboard = true
                    }
                )

                ProductInputBox(
                    modifier = Modifier.weight(0.9f),
                    placeholder = "单价",
                    displayText = localPriceText,
                    enabled = isEnabled,
                    onClick = {
                        activeInputField = ProductInputField.PRICE
                        localPriceText = ""
                        showCustomKeyboard = true
                    }
                )
            }

            if (showCustomKeyboard) {
                CustomNumberKeyboardDialog(
                    title = when (activeInputField) {
                        ProductInputField.QUANTITY -> "输入数量"
                        ProductInputField.PRICE -> "输入单价"
                        else -> "输入"
                    },
                    currentValue = when (activeInputField) {
                        ProductInputField.QUANTITY -> localQuantityText
                        ProductInputField.PRICE -> localPriceText
                        else -> ""
                    },
                    onValueChanged = { newValue ->
                        when (activeInputField) {
                            ProductInputField.QUANTITY -> localQuantityText = newValue
                            ProductInputField.PRICE -> localPriceText = newValue
                            else -> {}
                        }
                    },
                    onConfirm = {
                        val quantity = localQuantityText.toIntOrNull() ?: 0
                        val price = localPriceText.toDoubleOrNull() ?: 0.0
                        onQuantityChange(quantity, price)
                        showCustomKeyboard = false
                        activeInputField = null
                    },
                    onDismiss = {
                        localQuantityText = quantityText
                        localPriceText = priceText
                        showCustomKeyboard = false
                        activeInputField = null
                    }
                )
            }

            if (existingQuantity > 0) {
                Text(
                    text = "金额: ¥${String.format("%.2f", existingQuantity * existingPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductInputBox(
    placeholder: String,
    displayText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .clickable(enabled = enabled) { onClick() }
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = if (displayText.isEmpty()) placeholder else displayText,
                fontSize = 14.sp,
                color = if (displayText.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomNumberKeyboardDialog(
    title: String,
    currentValue: String,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var tempValue by remember { mutableStateOf(currentValue) }

    LaunchedEffect(currentValue) {
        tempValue = currentValue
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tempValue.isEmpty()) "0" else tempValue,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "←")
                )

                keys.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        rowKeys.forEach { key ->
                            val isSpecialKey = key == "C" || key == "←"
                            val backgroundColor = when {
                                isSpecialKey -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                            val textColor = when {
                                isSpecialKey -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(backgroundColor)
                                    .clickable {
                                        when (key) {
                                            "C" -> {
                                                tempValue = ""
                                                onValueChanged("")
                                            }
                                            "←" -> {
                                                if (tempValue.isNotEmpty()) {
                                                    tempValue = tempValue.dropLast(1)
                                                    onValueChanged(tempValue)
                                                }
                                            }
                                            else -> {
                                                if (tempValue.length < 6) {
                                                    tempValue += key
                                                    onValueChanged(tempValue)
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    fontSize = if (isSpecialKey) 16.sp else 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
