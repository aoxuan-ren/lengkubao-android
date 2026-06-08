package com.pingwei.lengkubao.ui.saleout.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.data.model.ProductWithStock
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.ui.saleout.viewmodel.SaleOutViewModel
import com.pingwei.lengkubao.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleProductSelectorDialog(
    onDismiss: () -> Unit,
    onProductSelected: (Product, salePrice: Double, quantity: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: SaleOutViewModel = viewModel()

    // 收集状态
    val productsWithStock by viewModel.productsWithStock.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 本地状态 - 修复默认数量为1，支持正常输入
    var selectedProduct by remember { mutableStateOf<ProductWithStock?>(null) }
    var salePrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }  // 修复：默认数量改为1
    var priceError by remember { mutableStateOf(false) }
    var quantityError by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 过滤后的商品列表
    val filteredProducts = remember(productsWithStock, searchQuery) {
        if (searchQuery.isEmpty()) {
            productsWithStock
        } else {
            productsWithStock.filter { productWithStock ->
                productWithStock.product.productName.contains(searchQuery, ignoreCase = true) ||
                        productWithStock.product.productNo.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 修复按钮启用条件计算逻辑
    val isButtonEnabled by remember(selectedProduct, salePrice, quantity, priceError, quantityError) {
        derivedStateOf {
            selectedProduct != null &&
                    salePrice.isNotEmpty() &&
                    quantity.isNotEmpty() &&
                    (salePrice.toDoubleOrNull() ?: 0.0) > 0 &&
                    (quantity.toIntOrNull() ?: 0) > 0 &&
                    !priceError &&
                    !quantityError  // 恢复数量错误校验，确保库存充足时才启用按钮
        }
    }

    // 选中商品时自动填充默认价格 - 修复：使用Product实体类中实际存在的价格字段
    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null && salePrice.isEmpty()) {
            // 使用Product实体类中实际存在的standardPrice字段作为默认售价
            salePrice = selectedProduct!!.product.standardPrice.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "选择销售商品",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请选择商品并输入销售信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索商品...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true
                )

                // 商品列表区域
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "加载商品库存...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (filteredProducts.isEmpty()) {
                        EmptyProductState(
                            searchQuery = searchQuery,
                            onDismiss = onDismiss
                        )
                    } else {
                        // 商品列表标题
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "商品列表 (${filteredProducts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "可用库存",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(filteredProducts, key = { it.product.id }) { productWithStock ->
                                ProductStockListItem(
                                    productWithStock = productWithStock,
                                    selected = selectedProduct?.product?.id == productWithStock.product.id,
                                    onClick = {
                                        selectedProduct = productWithStock
                                        // 选中商品时自动填充默认价格（使用standardPrice字段）
                                        if (salePrice.isEmpty()) {
                                            salePrice = productWithStock.product.standardPrice.toString()
                                        }
                                        // 重置数量为1
                                        quantity = "1"
                                        quantityError = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 商品详情和输入区域 - 确保选中商品后才显示
                selectedProduct?.let { productWithStock ->
                    val availableStock = productWithStock.availableStock

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(AppDimens.pagePadding),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
                        ) {
                            // 商品信息头
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = productWithStock.product.productName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "型号: ${productWithStock.product.productNo}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 库存标签
                                StockStatusBadge(availableStock = availableStock)
                            }

                            Divider(modifier = Modifier.fillMaxWidth())

                            // 输入区域
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 销售单价输入 - 修复输入逻辑和KeyboardOptions导入
                                OutlinedTextField(
                                    value = salePrice,
                                    onValueChange = { newValue ->
                                        // 允许数字和小数点，最多两位小数
                                        if (newValue.matches(Regex("^\\d*(\\.\\d{0,2})?$")) || newValue.isEmpty()) {
                                            salePrice = newValue
                                            priceError = false
                                        }
                                    },
                                    label = { Text("销售单价") },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = priceError,
                                    trailingIcon = {
                                        Text("元/${productWithStock.product.unit}")
                                    },
                                    supportingText = {
                                        if (priceError) {
                                            Text("请输入有效的销售单价（大于0）")
                                        } else {
                                            Text("标准价格: ${productWithStock.product.standardPrice} 元")
                                        }
                                    },
                                    placeholder = {
                                        Text("例如: 12.50")
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    )
                                )

                                // 修复数量输入逻辑 - 支持正常输入，库存不足时标记错误
                                OutlinedTextField(
                                    value = quantity,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() }) {
                                            quantity = newValue
                                            val qty = newValue.toIntOrNull() ?: 0

                                            // 库存校验
                                            if (qty <= 0) {
                                                quantityError = true
                                            } else if (qty > availableStock) {
                                                quantityError = true
                                                // 库存不足时弹出提示
                                                Toast.makeText(
                                                    context,
                                                    "库存不足，可用: $availableStock",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                quantityError = false
                                            }
                                        } else if (newValue.isEmpty()) {
                                            quantity = newValue
                                            quantityError = true
                                        }
                                    },
                                    label = { Text("销售数量") },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = quantityError,
                                    trailingIcon = {
                                        Text(productWithStock.product.unit)
                                    },
                                    supportingText = {
                                        if (quantityError) {
                                            when {
                                                quantity.isEmpty() || quantity.toIntOrNull() ?: 0 <= 0 -> {
                                                    Text("请输入大于0的数量", color = MaterialTheme.colorScheme.error)
                                                }
                                                quantity.toIntOrNull() ?: 0 > availableStock -> {
                                                    Text(
                                                        "⚠️ 数量超过可用库存: $availableStock",
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                else -> {
                                                    Text("输入无效", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        } else {
                                            Text("可用库存: $availableStock ${productWithStock.product.unit}")
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    )
                                )

                                // 快捷数量按钮 - 修复逻辑
                                if (availableStock > 0) {
                                    QuickQuantityButtons(
                                        availableStock = availableStock,
                                        onQuantitySelected = { qty ->
                                            quantity = qty.toString()
                                            quantityError = false
                                        }
                                    )
                                }

                                // 金额预览
                                if (salePrice.isNotEmpty() && quantity.isNotEmpty() && !priceError && !quantityError) {
                                    val price = salePrice.toDoubleOrNull() ?: 0.0
                                    val qty = quantity.toIntOrNull() ?: 0
                                    val amount = price * qty

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(AppDimens.pagePadding),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "小计金额",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "¥${String.format("%.2f", amount)}",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "销售数量",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "$qty ${productWithStock.product.unit}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
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
                        Log.d("SaleProductSelector", "🔄 点击'添加到销售单'按钮")

                        // 快速校验核心参数
                        val product = selectedProduct?.product ?: run {
                            Log.e("SaleProductSelector", "❌ 未选择商品")
                            Toast.makeText(context, "请选择商品", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val price = salePrice.toDoubleOrNull() ?: run {
                            priceError = true
                            Log.e("SaleProductSelector", "❌ 单价无效: $salePrice")
                            Toast.makeText(context, "请输入有效的销售单价", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val qty = quantity.toIntOrNull() ?: run {
                            Log.e("SaleProductSelector", "❌ 数量无效: $quantity")
                            Toast.makeText(context, "请输入有效的数量", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val availableStock = selectedProduct!!.availableStock

                        // 最终库存校验
                        when {
                            price <= 0 -> {
                                priceError = true
                                Toast.makeText(context, "请输入有效的销售单价（大于0）", Toast.LENGTH_SHORT).show()
                            }
                            qty <= 0 -> {
                                Toast.makeText(context, "请输入大于0的销售数量", Toast.LENGTH_SHORT).show()
                            }
                            qty > availableStock -> {
                                // 库存不足时弹出明确提示（修复Composable调用错误）
                                coroutineScope.launch {
                                    // 使用Toast替代AlertDialog避免Composable调用错误
                                    Toast.makeText(
                                        context,
                                        "库存不足，当前商品可用库存为 $availableStock，无法销售 $qty 件",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            else -> {
                                // 实时检查库存
                                coroutineScope.launch {
                                    val realTimeStock = viewModel.getRealTimeStock(product.id)
                                    Log.d("SaleProductSelector", "📊 实时库存检查: $realTimeStock")

                                    if (qty > realTimeStock) {
                                        Toast.makeText(
                                            context,
                                            "库存已变化，当前可用: $realTimeStock",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Log.d("SaleProductSelector", "✅ 验证通过，调用 onProductSelected")
                                        Log.d("SaleProductSelector", "📦 商品: ${product.productName}")
                                        Log.d("SaleProductSelector", "💰 单价: $price, 数量: $qty")

                                        // 调用回调添加商品，不关闭对话框（支持多选）
                                        onProductSelected(product, price, qty)

                                        // 重置选择状态，方便继续添加其他商品
                                        selectedProduct = null
                                        salePrice = ""
                                        quantity = "1"
                                        priceError = false
                                        quantityError = false

                                        Toast.makeText(context, "已添加 ${product.productName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = isButtonEnabled
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加到销售单")
                }
            }
        }
    )
}

@Composable
private fun ProductStockListItem(
    productWithStock: ProductWithStock,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cardColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = if (selected) CardDefaults.cardElevation(defaultElevation = 4.dp)
        else CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 商品名称和型号
                    Column {
                        Text(
                            text = productWithStock.product.productName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = "型号: ${productWithStock.product.productNo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 选中指示器
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 库存信息
            StockInfoBadge(
                availableStock = productWithStock.availableStock,
                unit = productWithStock.product.unit
            )
        }
    }
}

@Composable
fun StockInfoBadge(availableStock: Int, unit: String) {
    val (text, color, bgColor) = when {
        availableStock <= 0 -> Triple(
            "无库存",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        availableStock <= 10 -> Triple(
            "$availableStock$unit",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        else -> Triple(
            "$availableStock$unit",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.clip(MaterialTheme.shapes.small)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StockStatusBadge(availableStock: Int) {
    val (text, color) = when {
        availableStock <= 0 -> Pair("无库存", MaterialTheme.colorScheme.error)
        availableStock <= 5 -> Pair("库存紧张", MaterialTheme.colorScheme.error)
        availableStock <= 10 -> Pair("库存偏少", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Pair("库存充足", MaterialTheme.colorScheme.primary)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun QuickQuantityButtons(
    availableStock: Int,
    onQuantitySelected: (Int) -> Unit
) {
    val quickQuantities = remember(availableStock) {
        listOf(1, 5, 10, availableStock.coerceAtMost(20))
            .filter { it <= availableStock && it > 0 }
            .distinct()
    }

    if (quickQuantities.isNotEmpty()) {
        Column {
            Text(
                text = "快捷数量:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
            ) {
                quickQuantities.forEach { qty ->
                    OutlinedButton(
                        onClick = { onQuantitySelected(qty) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(qty.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProductState(
    searchQuery: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = "无商品",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "未找到相关商品",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "请尝试其他搜索词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "暂无可用商品库存",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "请先入库或选择其他库位",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = onDismiss) {
                Text("返回")
            }
        }
    }
}