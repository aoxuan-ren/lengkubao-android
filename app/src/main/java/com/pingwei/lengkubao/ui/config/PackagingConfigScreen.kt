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
import com.pingwei.lengkubao.data.db.entity.PackagingType
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.utils.ConfigDeleteResult
import com.pingwei.lengkubao.utils.ConfigDeleteService
import com.pingwei.lengkubao.utils.ConfigNameSearchFilter
import com.pingwei.lengkubao.utils.ConfigSyncStatusNotifier
import com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingConfigScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var packagingTypes by remember { mutableStateOf<List<PackagingType>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var typeToDelete by remember { mutableStateOf<PackagingType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val dismissKeyboard = rememberDismissKeyboard()

    val loadPackagingTypes: () -> Unit = {
        coroutineScope.launch {
            val typeList = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).packagingTypeDao().getAll()
            }
            packagingTypes = typeList
        }
    }

    LaunchedEffect(Unit) {
        loadPackagingTypes()
    }

    LaunchedEffect(Unit) {
        ConfigSyncStatusNotifier.refreshRequests.collect {
            loadPackagingTypes()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadPackagingTypes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredTypes = remember(packagingTypes, searchQuery) {
        ConfigNameSearchFilter.filter(packagingTypes, searchQuery) { it.typeName }
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
                    text = "包装类型管理",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { navController.navigate("packaging_add") }) {
                    Icon(Icons.Default.Add, contentDescription = "添加包装类型")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            text = if (searchQuery.isBlank()) "暂无包装类型" else "未找到相关包装类型",
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
                    items(filteredTypes) { packagingType ->
                        PackagingTypeItem(
                            packagingType = packagingType,
                            onToggleEnabled = { enabled ->
                                dismissKeyboard()
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            AppDatabase.getInstance(context)
                                                .packagingTypeDao()
                                                .update(packagingType.copy(enabled = enabled))
                                            SyncTrigger.triggerPackTypeSync(context, packagingType.id)
                                        }
                                        loadPackagingTypes()
                                        snackbarHostState.showSnackbar(
                                            "${packagingType.typeName} ${if (enabled) "已启用" else "已禁用"}"
                                        )
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("保存失败: ${e.message ?: "未知错误"}")
                                    }
                                }
                            },
                            onEdit = {
                                dismissKeyboard()
                                navController.navigate("packaging_edit/${packagingType.id}")
                            },
                            onDelete = {
                                dismissKeyboard()
                                typeToDelete = packagingType
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && typeToDelete != null) {
        var refCount by remember(typeToDelete!!.id) { mutableIntStateOf(-1) }
        LaunchedEffect(typeToDelete!!.id) {
            refCount = ConfigDeleteService.countPackTypeBillRefs(context, typeToDelete!!.id)
        }
        val packagingType = typeToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                typeToDelete = null
            },
            title = { Text("确认删除") },
            text = {
                Column {
                    when {
                        refCount < 0 -> Text("正在检查单据引用…")
                        refCount > 0 -> Text(
                            "包装类型 ${packagingType.typeName} 已被 $refCount 条单据引用，无法物理删除。确认后将停用。"
                        )
                        else -> Text("确定永久删除包装类型 ${packagingType.typeName}？此操作不可恢复。")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "单价: ¥${String.format("%.2f", packagingType.unitPrice)}/${packagingType.unit}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (packagingType.enabled && refCount >= 0) {
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
                        if (refCount < 0) return@TextButton
                        coroutineScope.launch {
                            val result = ConfigDeleteService.deletePackType(context, packagingType)
                            showDeleteConfirm = false
                            typeToDelete = null
                            packagingTypes = withContext(Dispatchers.IO) {
                                AppDatabase.getInstance(context).packagingTypeDao().getAll()
                            }
                            when (result) {
                                is ConfigDeleteResult.PcOnly ->
                                    snackbarHostState.showSnackbar(PC_ONLY_CONFIG_DELETE_MESSAGE)
                                is ConfigDeleteResult.PhysicallyDeleted ->
                                    snackbarHostState.showSnackbar("已永久删除")
                                is ConfigDeleteResult.DisabledDueToReferences ->
                                    snackbarHostState.showSnackbar(
                                        "已被单据引用，无法物理删除，已停用"
                                    )
                                is ConfigDeleteResult.Failed ->
                                    snackbarHostState.showSnackbar(result.message)
                            }
                        }
                    },
                    enabled = refCount >= 0,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingTypeFormScreen(
    packagingTypeId: Long?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isEditMode = packagingTypeId != null

    var typeName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("个") }
    var unitPriceText by remember { mutableStateOf("0.0") }
    var remark by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(isEditMode) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(packagingTypeId) {
        if (isEditMode && packagingTypeId != null) {
            isLoading = true
            val type = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).packagingTypeDao().getById(packagingTypeId)
            }
            if (type != null) {
                typeName = type.typeName
                unit = type.unit
                unitPriceText = type.unitPrice.toString()
                remark = type.remark
            }
            isLoading = false
        }
    }

    val isFormValid = typeName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isEditMode) "编辑包装类型" else "添加包装类型",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = typeName,
                onValueChange = { typeName = it },
                label = { Text("包装类型名称 *") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                placeholder = { Text("如：纸箱") },
                isError = typeName.isBlank()
            )
            if (isEditMode) {
                Text("单位", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    listOf("个", "卷", "只", "包", "箱").forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) }
                        )
                    }
                }
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d*$"))) unitPriceText = it
                    },
                    label = { Text("默认单价 (元) *") },
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
            }
        }

        Button(
            onClick = {
                if (!isFormValid || isSaving) return@Button
                isSaving = true
                val price = unitPriceText.toDoubleOrNull() ?: 0.0
                val trimmedName = typeName.trim()
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(context)
                            if (isEditMode && packagingTypeId != null) {
                                val existing = db.packagingTypeDao().getById(packagingTypeId)
                                if (existing != null) {
                                    db.packagingTypeDao().update(
                                        existing.copy(
                                            typeName = trimmedName,
                                            unit = unit,
                                            unitPrice = price,
                                            remark = remark
                                        )
                                    )
                                    SyncTrigger.triggerPackTypeSync(context, existing.id)
                                }
                            } else {
                                if (db.packagingTypeDao().countByTypeName(trimmedName) > 0) {
                                    throw IllegalStateException("包装类型名称已存在")
                                }
                                db.packagingTypeDao().insert(
                                    PackagingType(
                                        typeName = trimmedName,
                                        unit = unit,
                                        unitPrice = price,
                                        remark = remark
                                    )
                                )
                                db.packagingTypeDao().getByTypeName(trimmedName)
                                    ?.let { SyncTrigger.triggerPackTypeSync(context, it.id) }
                            }
                        }
                        Toast.makeText(
                            context,
                            if (isEditMode) "更新成功: $trimmedName" else "添加成功: $trimmedName",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSaved()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.message ?: "保存失败",
                            Toast.LENGTH_SHORT
                        ).show()
                        isSaving = false
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = packagingType.typeName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (packagingType.enabled) MaterialTheme.colorScheme.primary else Color.Gray
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
                Switch(
                    checked = packagingType.enabled,
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
