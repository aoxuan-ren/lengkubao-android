// ui/config/ProductConfigScreen.kt (修改后的完整版)
package com.pingwei.lengkubao.ui.config

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConfigScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var searchText by remember { mutableStateOf("") }

    // 创建加载商品的函数
    val loadProducts: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val productList = AppDatabase.getInstance(context)
                .productDao()
                .getAll() // 注意：这个需要根据你的实际方法名调整
            products = productList
        }
    }

    // 加载商品数据
    LaunchedEffect(Unit) {
        loadProducts()
    }

    // 搜索功能
    val filteredProducts = if (searchText.isBlank()) {
        products
    } else {
        products.filter { product ->
            product.productNo.contains(searchText, ignoreCase = true) ||
                    product.productName.contains(searchText, ignoreCase = true) ||
                    product.remark.contains(searchText, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("搜索商品") },
                        leadingIcon = { Icon(Icons.Filled.Search, "搜索") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    if (searchText.isNotBlank()) {
                        IconButton(
                            onClick = { searchText = "" }
                        ) {
                            Icon(Icons.Filled.Clear, "清除搜索")
                        }
                    }
                }
            }

            // 标题和添加按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "商品型号管理",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加商品"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 商品列表
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "无商品",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = if (searchText.isBlank()) "暂无商品型号" else "未找到相关商品",
                            color = Color.Gray
                        )
                        if (searchText.isBlank()) {
                            Text(
                                text = "点击右上角 + 按钮添加",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(filteredProducts) { product ->
                        ProductItem(
                            product = product,
                            onToggleEnabled = { enabled ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    // 先更新数据库，然后刷新列表
                                    val updatedProduct = product.copy(enabled = enabled)
                                    AppDatabase.getInstance(context)
                                        .productDao()
                                        .update(updatedProduct) // 需要确保有update方法
                                    withContext(Dispatchers.Main) {
                                        loadProducts()
                                        snackbarHostState.showSnackbar(
                                            "${product.productName} ${if (enabled) "已启用" else "已禁用"}"
                                        )
                                    }
                                }
                            },
                            onEdit = {
                                productToEdit = product
                            },
                            onDelete = {
                                productToDelete = product
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加商品对话框
    if (showAddDialog) {
        ProductEditDialog(
            product = null,
            onDismiss = { showAddDialog = false },
            onSave = { productNo, productName, unit, remark, category, standardPrice ->
                coroutineScope.launch(Dispatchers.IO) {
                    val product = Product(
                        productNo = productNo,
                        productName = productName,
                        unit = unit,
                        remark = remark,
                        category = category,
                        standardPrice = standardPrice
                    )
                    AppDatabase.getInstance(context)
                        .productDao()
                        .insert(product)
                    withContext(Dispatchers.Main) {
                        loadProducts()
                        snackbarHostState.showSnackbar("添加成功: $productName")
                    }
                }
                showAddDialog = false
            }
        )
    }

    // 编辑商品对话框
    if (productToEdit != null) {
        ProductEditDialog(
            product = productToEdit,
            onDismiss = { productToEdit = null },
            onSave = { productNo, productName, unit, remark, category, standardPrice ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updatedProduct = productToEdit!!.copy(
                        productNo = productNo,
                        productName = productName,
                        unit = unit,
                        remark = remark,
                        category = category,
                        standardPrice = standardPrice
                    )
                    AppDatabase.getInstance(context)
                        .productDao()
                        .update(updatedProduct)
                    withContext(Dispatchers.Main) {
                        loadProducts()
                        productToEdit = null
                        snackbarHostState.showSnackbar("更新成功: $productName")
                    }
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && productToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                productToDelete = null
            },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除商品型号吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${productToDelete!!.productName} (${productToDelete!!.productNo})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (productToDelete!!.enabled) {
                        Text(
                            "⚠️ 此商品已启用，删除后将无法在开单界面使用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val productName = productToDelete!!.productName
                            AppDatabase.getInstance(context)
                                .productDao()
                                .delete(productToDelete!!)
                            withContext(Dispatchers.Main) {
                                loadProducts()
                                showDeleteConfirm = false
                                productToDelete = null
                                snackbarHostState.showSnackbar("删除成功: $productName")
                            }
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        productToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ProductItem(
    product: Product,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (product.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.productNo,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (product.enabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = product.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (product.enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                        if (!product.enabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text("已禁用", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "单位：${product.unit} | 分类：${product.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (product.remark.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "备注：${product.remark}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    if (product.standardPrice > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "参考价：¥${"%.2f".format(product.standardPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 启用/禁用开关
                Switch(
                    checked = product.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 如果是编辑模式，使用原有的商品编号；如果是添加模式，初始为空（稍后会自动生成）
    var productNo by remember { mutableStateOf(product?.productNo ?: "") }
    var productName by remember { mutableStateOf(product?.productName ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "箱") }
    var category by remember { mutableStateOf(product?.category ?: "梨") }
    var remark by remember { mutableStateOf(product?.remark ?: "") }
    var standardPriceText by remember { mutableStateOf(product?.standardPrice?.toString() ?: "0.0") }

    // 标记是否已生成过编号，避免重复生成
    val hasGeneratedNo = remember { mutableStateOf(false) }

    val isEditMode = product != null
    // 添加模式时，只需要商品名称不为空即可，商品编号会自动生成
    val isFormValid = if (isEditMode) {
        productNo.isNotBlank() && productName.isNotBlank()
    } else {
        productName.isNotBlank()
    }

    // 自动生成商品编号（仅在添加模式且尚未生成时执行）
    LaunchedEffect(Unit) {
        if (!isEditMode && !hasGeneratedNo.value) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 获取当前最大的商品编号
                    val allProducts = AppDatabase.getInstance(context)
                        .productDao()
                        .getAll()

                    // 找出最大的编号数字
                    var maxNumber = 0
                    val pattern = Regex("""SP(\d+)""")

                    for (p in allProducts) {
                        val matchResult = pattern.find(p.productNo)
                        if (matchResult != null) {
                            val number = matchResult.groupValues[1].toIntOrNull() ?: 0
                            if (number > maxNumber) {
                                maxNumber = number
                            }
                        }
                    }

                    // 生成新的编号（SP + 两位数字，从01开始）
                    val newNumber = maxNumber + 1
                    val newProductNo = "SP${String.format("%02d", newNumber)}"

                    withContext(Dispatchers.Main) {
                        productNo = newProductNo
                        hasGeneratedNo.value = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 如果出错，默认从SP01开始
                    withContext(Dispatchers.Main) {
                        productNo = "SP01"
                        hasGeneratedNo.value = true
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val price = try {
                        standardPriceText.toDouble()
                    } catch (e: Exception) {
                        0.0
                    }
                    onSave(productNo, productName, unit, remark, category, price)
                },
                enabled = isFormValid
            ) {
                Text(if (isEditMode) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = {
            Text(if (isEditMode) "编辑商品型号" else "添加商品型号")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 商品编号字段
                if (isEditMode) {
                    // 编辑模式下：可编辑的普通输入框
                    OutlinedTextField(
                        value = productNo,
                        onValueChange = { productNo = it },
                        label = { Text("商品编号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：42型") },
                        isError = productNo.isBlank(),
                        supportingText = {
                            if (productNo.isBlank()) {
                                Text("商品编号不能为空")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("商品名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：42型梨") },
                        isError = productName.isBlank(),
                        supportingText = {
                            if (productName.isBlank()) {
                                Text("商品名称不能为空")
                            }
                        }
                    )

                    // 单位选择
                    Text("单位", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("箱", "件", "筐", "吨", "斤").forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // 分类选择
                    Text("分类", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("梨", "苹果", "其他水果", "包装材料", "其他").forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = standardPriceText,
                        onValueChange = {
                            // 只允许数字和小数点
                            if (it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                standardPriceText = it
                            }
                        },
                        label = { Text("标准参考价 (元)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                } else {
                    // 添加模式下：只显示商品编号和商品名称
                    // 商品编号字段 - 只读显示
                    OutlinedTextField(
                        value = productNo,
                        onValueChange = {},
                        label = { Text("商品编号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false,
                        colors = TextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )

                    // 商品名称字段 - 唯一需要填写的
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("商品名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：42型梨") },
                        isError = productName.isBlank(),

                    )
                }
            }
        }
    )
}