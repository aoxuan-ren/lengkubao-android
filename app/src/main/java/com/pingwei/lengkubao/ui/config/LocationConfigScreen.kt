// ui/config/LocationConfigScreen.kt (修复Material3版本)
package com.pingwei.lengkubao.ui.config

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LocationConfigScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: LocationConfigViewModel = viewModel(
        factory = LocationConfigViewModelFactory(context.applicationContext as Application)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 监听视图模型状态
    val locations by viewModel.locations.collectAsState(initial = emptyList())
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val searchText by viewModel.searchText.collectAsState()

    // 控制对话框显示
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<Location?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("库位管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 只保留添加库位按钮，删除了初始化默认库位按钮
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(Icons.Filled.AddCircle, "添加库位")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { viewModel.searchLocations(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索库位编号或名称...") },
                    leadingIcon = { Icon(Icons.Filled.Search, "搜索") },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearSearch() }
                            ) {
                                Icon(Icons.Filled.Clear, "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            // 搜索逻辑已经在onValueChange中处理
                        }
                    )
                )

                // 库位列表
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val displayList = if (searchText.isNotEmpty()) searchResults else locations

                    if (displayList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocationOff,
                                    contentDescription = "空列表",
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("暂无库位数据")
                                if (searchText.isNotEmpty()) {
                                    Text("尝试其他关键词或清空搜索", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("点击右上角 + 按钮添加库位", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(displayList) { location ->
                                LocationItem(
                                    location = location,
                                    onEdit = {
                                        selectedLocation = location
                                        showEditDialog = true
                                    },
                                    onDelete = {
                                        selectedLocation = location
                                        showDeleteDialog = true
                                    },
                                    onToggleEnabled = {
                                        coroutineScope.launch {
                                            viewModel.toggleLocationEnabled(location)
                                            val status = if (location.enabled) "禁用" else "启用"
                                            snackbarHostState.showSnackbar(
                                                "已${status} ${location.locationName}"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 添加库位对话框
        if (showAddDialog) {
            AddLocationDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { locationNo, locationName, description, capacity ->
                    coroutineScope.launch {
                        val success = viewModel.addLocation(
                            locationNo = locationNo,
                            locationName = locationName,
                            description = description,
                            capacity = capacity
                        )
                        if (success) {
                            snackbarHostState.showSnackbar(
                                "添加库位成功"
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                "库位编号已存在"
                            )
                        }
                        showAddDialog = false
                    }
                }
            )
        }

        // 编辑库位对话框
        if (showEditDialog && selectedLocation != null) {
            EditLocationDialog(
                location = selectedLocation!!,
                onDismiss = { showEditDialog = false },
                onConfirm = { locationNo, locationName, description, capacity ->
                    coroutineScope.launch {
                        viewModel.updateLocation(
                            location = selectedLocation!!.copy(
                                locationNo = locationNo,
                                locationName = locationName,
                                description = description,
                                capacity = capacity
                            )
                        )
                        snackbarHostState.showSnackbar(
                            "更新库位成功"
                        )
                        showEditDialog = false
                    }
                }
            )
        }

        // 删除确认对话框
        if (showDeleteDialog && selectedLocation != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = {
                    Text("确定要删除库位 ${selectedLocation!!.locationName} (${selectedLocation!!.locationNo}) 吗？此操作不可恢复。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.deleteLocation(selectedLocation!!)
                                snackbarHostState.showSnackbar(
                                    "删除库位成功"
                                )
                                showDeleteDialog = false
                            }
                        }
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun LocationItem(
    location: Location,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (location.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        location.locationNo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!location.enabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "已禁用",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Text(
                    location.locationName,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (location.description.isNotEmpty()) {
                    Text(
                        location.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (location.capacity > 0) {
                    Text(
                        "容量: ${location.capacity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleEnabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (location.enabled) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                        contentDescription = if (location.enabled) "禁用" else "启用",
                        tint = if (location.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddLocationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var locationNo by remember { mutableStateOf("") }
    var locationName by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    val hasGeneratedNo = remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 自动生成库位编号
    LaunchedEffect(Unit) {
        if (!hasGeneratedNo.value) {
            // 在后台线程执行数据库操作
            val newNo = withContext(Dispatchers.IO) {
                try {
                    val db = com.pingwei.lengkubao.data.db.AppDatabase.getInstance(context)
                    // 使用同步方法获取库位列表
                    val allLocations = db.locationDao().getAllSimple()

                    // 找出最大的编号数字
                    var maxNumber = 0
                    val pattern = Regex("""LC(\d+)""")

                    if (allLocations.isNotEmpty()) {
                        allLocations.forEach { loc ->
                            val matchResult = pattern.find(loc.locationNo)
                            if (matchResult != null) {
                                val number = matchResult.groupValues[1].toIntOrNull() ?: 0
                                if (number > maxNumber) {
                                    maxNumber = number
                                }
                            }
                        }
                    }

                    // 生成新的编号（LC + 两位数字，从01开始）
                    val newNumber = maxNumber + 1
                    "LC${String.format("%02d", newNumber)}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "LC01"
                }
            }

            locationNo = newNo
            hasGeneratedNo.value = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加库位") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 库位编号 - 自动生成，只读显示
                OutlinedTextField(
                    value = locationNo,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("库位编号（自动生成）") },
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                // 库位名称 - 唯一需要填写的
                OutlinedTextField(
                    value = locationName,
                    onValueChange = {
                        locationName = it
                        isNameError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("库位名称 *") },
                    placeholder = { Text("如: 东1库") },
                    singleLine = true,
                    isError = isNameError,
                    supportingText = {
                        if (isNameError) {
                            Text("库位名称不能为空")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nameValid = locationName.isNotBlank()
                    isNameError = !nameValid

                    if (nameValid) {
                        keyboardController?.hide()
                        onConfirm(
                            locationNo,  // 使用自动生成的编号
                            locationName.trim(),
                            "",  // 描述传空
                            0    // 容量传空
                        )
                        onDismiss() // 添加这一行，关闭对话框
                    }
                }
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun EditLocationDialog(
    location: Location,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit
) {
    var locationNo by remember { mutableStateOf(location.locationNo) }
    var locationName by remember { mutableStateOf(location.locationName) }
    var description by remember { mutableStateOf(location.description) }
    var capacity by remember { mutableStateOf(location.capacity.toString()) }
    var isNoError by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑库位") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = locationNo,
                    onValueChange = {
                        locationNo = it
                        isNoError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("库位编号 *") },
                    singleLine = true,
                    isError = isNoError,
                    supportingText = {
                        if (isNoError) {
                            Text("库位编号不能为空")
                        }
                    }
                )

                OutlinedTextField(
                    value = locationName,
                    onValueChange = {
                        locationName = it
                        isNameError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("库位名称 *") },
                    singleLine = true,
                    isError = isNameError,
                    supportingText = {
                        if (isNameError) {
                            Text("库位名称不能为空")
                        }
                    }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述") },
                    singleLine = false,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = capacity,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) {
                            capacity = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("容量限制") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 验证输入
                    val noValid = locationNo.isNotBlank()
                    val nameValid = locationName.isNotBlank()

                    isNoError = !noValid
                    isNameError = !nameValid

                    if (noValid && nameValid) {
                        keyboardController?.hide()
                        onConfirm(
                            locationNo.trim(),
                            locationName.trim(),
                            description.trim(),
                            capacity.toIntOrNull() ?: 0
                        )
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}