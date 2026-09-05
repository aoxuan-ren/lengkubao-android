package com.pingwei.lengkubao.ui.config

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Product
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.utils.ConfigNameSearchFilter
import com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier
import com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConfigScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val dismissKeyboard = rememberDismissKeyboard()

    val loadProducts: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val productList = AppDatabase.getInstance(context).productDao().getAll()
            withContext(Dispatchers.Main) {
                products = productList
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProducts()
    }

    LaunchedEffect(Unit) {
        ConfigSyncStatusNotifier.refreshRequests.collect {
            loadProducts()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadProducts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredProducts = remember(products, searchQuery) {
        ConfigNameSearchFilter.filter(products, searchQuery) { it.productName }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ConfigSearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
            )

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
                IconButton(onClick = { navController.navigate("product_add") }) {
                    Icon(Icons.Default.Add, contentDescription = "添加商品")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            text = if (searchQuery.isBlank()) "暂无商品型号" else "未找到相关商品",
                            color = Color.Gray
                        )
                        if (searchQuery.isBlank()) {
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
                                dismissKeyboard()
                                coroutineScope.launch(Dispatchers.IO) {
                                    val updatedProduct = product.copy(enabled = enabled, syncStatus = 0)
                                    AppDatabase.getInstance(context).productDao().update(updatedProduct)
                                    SyncTrigger.triggerProductSync(context, product.id)
                                    withContext(Dispatchers.Main) {
                                        loadProducts()
                                        snackbarHostState.showSnackbar(
                                            "${product.productName} ${if (enabled) "已启用" else "已禁用"}"
                                        )
                                    }
                                }
                            },
                            onEdit = {
                                dismissKeyboard()
                                navController.navigate("product_edit/${product.id}")
                            },
                            onDelete = {
                                dismissKeyboard()
                                productToDelete = product
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

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
                        coroutineScope.launch {
                            showDeleteConfirm = false
                            productToDelete = null
                            snackbarHostState.showSnackbar(PC_ONLY_CONFIG_DELETE_MESSAGE)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    productId: Long?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isEditMode = productId != null

    var productNo by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("箱") }
    var category by remember { mutableStateOf("梨") }
    var remark by remember { mutableStateOf("") }
    var standardPriceText by remember { mutableStateOf("0.0") }
    var isLoading by remember { mutableStateOf(isEditMode) }
    var isSaving by remember { mutableStateOf(false) }
    val hasGeneratedNo = remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        if (isEditMode && productId != null) {
            isLoading = true
            val product = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).productDao().getProductById(productId)
            }
            if (product != null) {
                productNo = product.productNo
                productName = product.productName
                unit = product.unit
                category = product.category
                remark = product.remark
                standardPriceText = product.standardPrice.toString()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (!isEditMode && !hasGeneratedNo.value) {
            val newNo = withContext(Dispatchers.IO) {
                try {
                    val allProducts = AppDatabase.getInstance(context).productDao().getAll()
                    var maxNumber = 0
                    val pattern = Regex("""SP(\d+)""")
                    for (p in allProducts) {
                        pattern.find(p.productNo)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
                            if (n > maxNumber) maxNumber = n
                        }
                    }
                    "SP${String.format("%02d", maxNumber + 1)}"
                } catch (_: Exception) {
                    "SP01"
                }
            }
            productNo = newNo
            hasGeneratedNo.value = true
        }
    }

    val isFormValid = if (isEditMode) {
        productNo.isNotBlank() && productName.isNotBlank()
    } else {
        productName.isNotBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isEditMode) "编辑商品型号" else "添加商品型号",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else if (isEditMode) {
            OutlinedTextField(
                value = productNo,
                onValueChange = { productNo = it },
                label = { Text("商品编号 *") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                isError = productNo.isBlank()
            )
            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("商品名称 *") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                isError = productName.isBlank()
            )
            Text("单位", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                listOf("箱", "件", "筐", "吨", "斤").forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(u) }
                    )
                }
            }
            Text("分类", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                listOf("梨", "苹果", "其他水果", "包装材料", "其他").forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) }
                    )
                }
            }
            OutlinedTextField(
                value = standardPriceText,
                onValueChange = {
                    if (it.matches(Regex("^\\d*\\.?\\d*$"))) standardPriceText = it
                },
                label = { Text("标准参考价 (元)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                maxLines = 2
            )
        } else {
            OutlinedTextField(
                value = productNo,
                onValueChange = {},
                label = { Text("商品编号") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                readOnly = true,
                enabled = false,
                colors = TextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    disabledLabelColor = MaterialTheme.colorScheme.primary
                )
            )
            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("商品名称 *") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                singleLine = true,
                placeholder = { Text("如：42型梨") },
                isError = productName.isBlank()
            )
        }

        Button(
            onClick = {
                if (!isFormValid || isSaving) return@Button
                isSaving = true
                val price = standardPriceText.toDoubleOrNull() ?: 0.0
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getInstance(context)
                        if (isEditMode && productId != null) {
                            val existing = db.productDao().getProductById(productId)
                            if (existing != null) {
                                val updated = existing.copy(
                                    productNo = productNo,
                                    productName = productName,
                                    unit = unit,
                                    remark = remark,
                                    category = category,
                                    standardPrice = price,
                                    syncStatus = 0
                                )
                                db.productDao().update(updated)
                                SyncTrigger.triggerProductSync(context, updated.id)
                            }
                        } else {
                            val product = Product(
                                productNo = productNo,
                                productName = productName,
                                unit = unit,
                                remark = remark,
                                category = category,
                                standardPrice = price
                            )
                            db.productDao().insert(product)
                            db.productDao().getByProductNo(productNo)
                                ?.let { SyncTrigger.triggerProductSync(context, it.id) }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (isEditMode) "更新成功: $productName" else "添加成功: $productName",
                                Toast.LENGTH_SHORT
                            ).show()
                            onSaved()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            isSaving = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isFormValid && !isLoading && !isSaving
        ) {
            Text(if (isSaving) "正在保存..." else if (isEditMode) "保存" else "添加")
        }
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
                Column(modifier = Modifier.weight(1f)) {
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
                Switch(
                    checked = product.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}
