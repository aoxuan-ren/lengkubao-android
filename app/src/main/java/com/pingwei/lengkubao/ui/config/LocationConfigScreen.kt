package com.pingwei.lengkubao.ui.config

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.utils.ConfigDeleteResult
import com.pingwei.lengkubao.utils.ConfigNameSearchFilter
import com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationConfigScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val viewModel: LocationConfigViewModel = viewModel(
        factory = LocationConfigViewModelFactory(context.applicationContext as Application)
    )
    val coroutineScope = rememberCoroutineScope()

    val locations by viewModel.locations.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<Location?>(null) }
    val dismissKeyboard = rememberDismissKeyboard()

    val displayList = remember(locations, searchQuery) {
        ConfigNameSearchFilter.filter(locations, searchQuery) { it.locationName }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("库位管理") },
                actions = {
                    IconButton(onClick = { navController.navigate("location_add") }) {
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
                ConfigSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
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
                                if (searchQuery.isNotEmpty()) {
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
                                        dismissKeyboard()
                                        navController.navigate("location_edit/${location.id}")
                                    },
                                    onDelete = {
                                        dismissKeyboard()
                                        selectedLocation = location
                                        showDeleteDialog = true
                                    },
                                    onToggleEnabled = {
                                        dismissKeyboard()
                                        coroutineScope.launch {
                                            viewModel.toggleLocationEnabled(location)
                                            val status = if (location.enabled) "禁用" else "启用"
                                            snackbarHostState.showSnackbar("已${status} ${location.locationName}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    if (showDeleteDialog && selectedLocation != null) {
            var refCount by remember(selectedLocation!!.id) { mutableIntStateOf(-1) }
            LaunchedEffect(selectedLocation!!.id) {
                refCount = viewModel.countLocationBillRefs(selectedLocation!!.id)
            }
            val location = selectedLocation!!
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    selectedLocation = null
                },
                title = { Text("确认删除") },
                text = {
                    when {
                        refCount < 0 -> Text("正在检查单据引用…")
                        refCount > 0 -> Text(
                            "库位 ${location.locationName} 已被 $refCount 条单据引用，无法物理删除。确认后将停用。"
                        )
                        else -> Text("确定永久删除库位 ${location.locationName}？此操作不可恢复。")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (refCount < 0) return@TextButton
                            coroutineScope.launch {
                                val result = viewModel.deleteLocation(location)
                                showDeleteDialog = false
                                selectedLocation = null
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
                    TextButton(onClick = {
                        showDeleteDialog = false
                        selectedLocation = null
                    }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationFormScreen(
    locationId: Long?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: LocationConfigViewModel = viewModel(
        factory = LocationConfigViewModelFactory(context.applicationContext as Application)
    )
    val isEditMode = locationId != null

    var locationName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("0") }
    var isNameError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(isEditMode) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(locationId) {
        if (isEditMode && locationId != null) {
            isLoading = true
            val location = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).locationDao().getLocationById(locationId)
            }
            if (location != null) {
                locationName = location.locationName
                description = location.description
                capacity = location.capacity.toString()
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isEditMode) "编辑库位" else "添加库位",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = locationName,
                onValueChange = {
                    locationName = it
                    isNameError = false
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                label = { Text("库位名称 *") },
                placeholder = { Text("如: 东1库") },
                singleLine = true,
                isError = isNameError,
                supportingText = {
                    if (isNameError) Text("库位名称不能为空")
                }
            )
            if (isEditMode) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    label = { Text("描述") },
                    maxLines = 3
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) capacity = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    label = { Text("容量限制") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Button(
            onClick = {
                if (isSaving) return@Button
                val nameValid = locationName.isNotBlank()
                isNameError = !nameValid
                if (!nameValid) return@Button
                isSaving = true
                coroutineScope.launch {
                    if (isEditMode) {
                        val existing = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).locationDao().getLocationById(locationId!!)
                        }
                        if (existing != null) {
                            viewModel.updateLocation(
                                existing.copy(
                                    locationName = locationName.trim(),
                                    description = description.trim(),
                                    capacity = capacity.toIntOrNull() ?: 0
                                )
                            )
                            Toast.makeText(context, "更新库位成功", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            isSaving = false
                        }
                    } else {
                        val success = viewModel.addLocation(
                            locationName = locationName.trim(),
                            description = "",
                            capacity = 0
                        )
                        if (success) {
                            Toast.makeText(context, "添加库位成功", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            Toast.makeText(context, "库位名称已存在", Toast.LENGTH_SHORT).show()
                            isSaving = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isSaving
        ) {
            Text(if (isSaving) "正在保存..." else if (isEditMode) "保存" else "添加")
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
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        location.locationName,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleEnabled, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (location.enabled) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                        contentDescription = if (location.enabled) "禁用" else "启用",
                        tint = if (location.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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
