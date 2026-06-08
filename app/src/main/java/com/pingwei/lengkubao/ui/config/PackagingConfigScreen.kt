// ui/config/PackagingConfigScreen.kt
package com.pingwei.lengkubao.ui.config

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
import com.pingwei.lengkubao.data.db.entity.PackagingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingConfigScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var packagingTypes by remember { mutableStateOf<List<PackagingType>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var typeToEdit by remember { mutableStateOf<PackagingType?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var typeToDelete by remember { mutableStateOf<PackagingType?>(null) }
    var searchText by remember { mutableStateOf("") }

    // 加载包装类型的函数
    val loadPackagingTypes: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val typeList = AppDatabase.getInstance(context)
                .packagingTypeDao()
                .getAll()
            packagingTypes = typeList
        }
    }

    // 加载包装类型数据
    LaunchedEffect(Unit) {
        loadPackagingTypes()
    }

    // 搜索功能
    val filteredTypes = if (searchText.isBlank()) {
        packagingTypes
    } else {
        packagingTypes.filter { type ->
            type.typeNo.contains(searchText, ignoreCase = true) ||
                    type.typeName.contains(searchText, ignoreCase = true) ||
                    type.remark.contains(searchText, ignoreCase = true)
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
                        label = { Text("搜索包装类型") },
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
                    text = "包装类型管理",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加包装类型"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 包装类型列表
            if (filteredTypes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = "无包装类型",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = if (searchText.isBlank()) "暂无包装类型" else "未找到相关包装类型",
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
                    items(filteredTypes) { packagingType ->
                        PackagingTypeItem(
                            packagingType = packagingType,
                            onToggleEnabled = { enabled ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val updatedType = packagingType.copy(enabled = enabled)
                                    AppDatabase.getInstance(context)
                                        .packagingTypeDao()
                                        .update(updatedType)
                                    withContext(Dispatchers.Main) {
                                        loadPackagingTypes()
                                        snackbarHostState.showSnackbar(
                                            "${packagingType.typeName} ${if (enabled) "已启用" else "已禁用"}"
                                        )
                                    }
                                }
                            },
                            onEdit = {
                                typeToEdit = packagingType
                            },
                            onDelete = {
                                typeToDelete = packagingType
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加包装类型对话框
    if (showAddDialog) {
        PackagingTypeEditDialog(
            packagingType = null,
            onDismiss = { showAddDialog = false },
            onSave = { typeNo, typeName, unit, unitPrice, remark ->
                coroutineScope.launch(Dispatchers.IO) {
                    val packagingType = PackagingType(
                        typeNo = typeNo,
                        typeName = typeName,
                        unit = unit,
                        unitPrice = unitPrice,
                        remark = remark
                    )
                    AppDatabase.getInstance(context)
                        .packagingTypeDao()
                        .insert(packagingType)
                    withContext(Dispatchers.Main) {
                        loadPackagingTypes()
                        snackbarHostState.showSnackbar("添加成功: $typeName")
                    }
                }
                showAddDialog = false
            }
        )
    }

    // 编辑包装类型对话框
    if (typeToEdit != null) {
        PackagingTypeEditDialog(
            packagingType = typeToEdit,
            onDismiss = { typeToEdit = null },
            onSave = { typeNo, typeName, unit, unitPrice, remark ->
                coroutineScope.launch(Dispatchers.IO) {
                    val updatedType = typeToEdit!!.copy(
                        typeNo = typeNo,
                        typeName = typeName,
                        unit = unit,
                        unitPrice = unitPrice,
                        remark = remark
                    )
                    AppDatabase.getInstance(context)
                        .packagingTypeDao()
                        .update(updatedType)
                    withContext(Dispatchers.Main) {
                        loadPackagingTypes()
                        typeToEdit = null
                        snackbarHostState.showSnackbar("更新成功: $typeName")
                    }
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && typeToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                typeToDelete = null
            },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除包装类型吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${typeToDelete!!.typeName} (${typeToDelete!!.typeNo})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "单价: ¥${String.format("%.2f", typeToDelete!!.unitPrice)}/${typeToDelete!!.unit}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (typeToDelete!!.enabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "⚠️ 此包装类型已启用，删除后将无法在包装记账界面使用",
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
                            val typeName = typeToDelete!!.typeName
                            AppDatabase.getInstance(context)
                                .packagingTypeDao()
                                .delete(typeToDelete!!)
                            withContext(Dispatchers.Main) {
                                loadPackagingTypes()
                                showDeleteConfirm = false
                                typeToDelete = null
                                snackbarHostState.showSnackbar("删除成功: $typeName")
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
                        typeToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun PackagingTypeItem(
    packagingType: PackagingType,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (packagingType.enabled) MaterialTheme.colorScheme.surface
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
                            text = packagingType.typeNo,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (packagingType.enabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = packagingType.typeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (packagingType.enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                        if (!packagingType.enabled) {
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
                        text = "单价: ¥${String.format("%.2f", packagingType.unitPrice)}/${packagingType.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (packagingType.remark.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "备注: ${packagingType.remark}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }

                // 启用/禁用开关
                Switch(
                    checked = packagingType.enabled,
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
fun PackagingTypeEditDialog(
    packagingType: PackagingType?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var typeNo by remember { mutableStateOf(packagingType?.typeNo ?: "") }
    var typeName by remember { mutableStateOf(packagingType?.typeName ?: "") }
    var unit by remember { mutableStateOf(packagingType?.unit ?: "个") }
    var unitPriceText by remember { mutableStateOf(packagingType?.unitPrice?.toString() ?: "0.0") }
    var remark by remember { mutableStateOf(packagingType?.remark ?: "") }

    val hasGeneratedNo = remember { mutableStateOf(false) }

    val isEditMode = packagingType != null
    val isFormValid = if (isEditMode) {
        typeNo.isNotBlank() && typeName.isNotBlank()
    } else {
        typeName.isNotBlank()
    }

    // 自动生成包装类型编号
    LaunchedEffect(Unit) {
        if (!isEditMode && !hasGeneratedNo.value) {
            val newNo = withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(context)
                    val allTypes = db.packagingTypeDao().getAll()

                    var maxNumber = 0
                    val pattern = Regex("""BZ(\d+)""")

                    if (allTypes.isNotEmpty()) {
                        allTypes.forEach { type ->
                            val matchResult = pattern.find(type.typeNo)
                            if (matchResult != null) {
                                val number = matchResult.groupValues[1].toIntOrNull() ?: 0
                                if (number > maxNumber) {
                                    maxNumber = number
                                }
                            }
                        }
                    }

                    val newNumber = maxNumber + 1
                    "BZ${String.format("%02d", newNumber)}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "BZ01"
                }
            }

            typeNo = newNo
            hasGeneratedNo.value = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val price = try {
                        unitPriceText.toDouble()
                    } catch (e: Exception) {
                        0.0
                    }
                    onSave(typeNo, typeName, unit, price, remark)
                    onDismiss()
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
            Text(if (isEditMode) "编辑包装类型" else "添加包装类型")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditMode) {
                    // 编辑模式：显示可编辑的编号
                    OutlinedTextField(
                        value = typeNo,
                        onValueChange = { typeNo = it },
                        label = { Text("包装类型编号 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：BZ01") },
                        isError = typeNo.isBlank(),
                        supportingText = {
                            if (typeNo.isBlank()) {
                                Text("包装类型编号不能为空")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = typeName,
                        onValueChange = { typeName = it },
                        label = { Text("包装类型名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：纸箱") },
                        isError = typeName.isBlank(),
                        supportingText = {
                            if (typeName.isBlank()) {
                                Text("包装类型名称不能为空")
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
                        listOf("个", "卷", "只", "包", "箱").forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = unitPriceText,
                        onValueChange = {
                            if (it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                unitPriceText = it
                            }
                        },
                        label = { Text("默认单价 (元) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = unitPriceText.isBlank(),
                        supportingText = {
                            if (unitPriceText.isBlank()) {
                                Text("默认单价不能为空")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                } else {
                    // 添加模式：只显示自动生成的编号和名称
                    OutlinedTextField(
                        value = typeNo,
                        onValueChange = {},
                        label = { Text("包装类型编号（自动生成）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false,
                        colors = TextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    OutlinedTextField(
                        value = typeName,
                        onValueChange = { typeName = it },
                        label = { Text("包装类型名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("如：纸箱") },
                        isError = typeName.isBlank(),
                        supportingText = {
                            if (typeName.isBlank()) {
                                Text("包装类型名称不能为空")
                            }
                        }
                    )
                }
            }
        }
    )
}