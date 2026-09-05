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
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.ui.common.rememberDismissKeyboard
import com.pingwei.lengkubao.utils.ConfigDeleteResult
import com.pingwei.lengkubao.utils.ConfigNameSearchFilter
import com.pingwei.lengkubao.utils.PC_ONLY_CONFIG_DELETE_MESSAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorConfigScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val viewModel: OperatorConfigViewModel = viewModel(
        factory = OperatorConfigViewModelFactory(context.applicationContext as Application)
    )
    val coroutineScope = rememberCoroutineScope()

    val operators by viewModel.operators.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedOperator by remember { mutableStateOf<Operator?>(null) }
    val dismissKeyboard = rememberDismissKeyboard()

    val displayList = remember(operators, searchQuery) {
        ConfigNameSearchFilter.filter(operators, searchQuery) { it.name }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("经手人管理") },
                actions = {
                    IconButton(onClick = { navController.navigate("operator_add") }) {
                        Icon(Icons.Filled.AddCircle, "添加经手人")
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
                                    Icons.Filled.PersonOff,
                                    contentDescription = "空列表",
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("暂无经手人数据")
                                if (searchQuery.isNotEmpty()) {
                                    Text("尝试其他关键词或清空搜索", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("点击右上角 + 按钮添加经手人", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(displayList) { operator ->
                                OperatorItem(
                                    operator = operator,
                                    onEdit = {
                                        dismissKeyboard()
                                        navController.navigate("operator_edit/${operator.id}")
                                    },
                                    onDelete = {
                                        dismissKeyboard()
                                        selectedOperator = operator
                                        showDeleteDialog = true
                                    },
                                    onToggleEnabled = {
                                        dismissKeyboard()
                                        coroutineScope.launch {
                                            viewModel.toggleOperatorEnabled(operator)
                                            val status = if (operator.enabled) "禁用" else "启用"
                                            snackbarHostState.showSnackbar("已${status} ${operator.name}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog && selectedOperator != null) {
            var refCount by remember(selectedOperator!!.id) { mutableIntStateOf(-1) }
            LaunchedEffect(selectedOperator!!.id) {
                refCount = viewModel.countOperatorBillRefs(selectedOperator!!.id)
            }
            val operator = selectedOperator!!
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    selectedOperator = null
                },
                title = { Text("确认删除") },
                text = {
                    when {
                        refCount < 0 -> Text("正在检查单据引用…")
                        refCount > 0 -> Text(
                            "经手人 ${operator.name} 已被 $refCount 条单据引用，无法物理删除。确认后将停用。"
                        )
                        else -> Text("确定永久删除经手人 ${operator.name}？此操作不可恢复。")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (refCount < 0) return@TextButton
                            coroutineScope.launch {
                                val result = viewModel.deleteOperator(operator)
                                showDeleteDialog = false
                                selectedOperator = null
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
                        selectedOperator = null
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
fun OperatorFormScreen(
    operatorId: Long?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: OperatorConfigViewModel = viewModel(
        factory = OperatorConfigViewModelFactory(context.applicationContext as Application)
    )
    val isEditMode = operatorId != null

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("操作员") }
    var remark by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(isEditMode) }
    var isSaving by remember { mutableStateOf(false) }
    val roles = listOf("操作员", "管理员", "财务", "仓管员", "销售员")

    LaunchedEffect(operatorId) {
        if (isEditMode && operatorId != null) {
            isLoading = true
            val operator = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).operatorDao().getOperatorById(operatorId)
            }
            if (operator != null) {
                name = operator.name
                phone = operator.phone
                role = operator.role
                remark = operator.remark
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
            text = if (isEditMode) "编辑经手人" else "添加经手人",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isNameError = false
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                label = { Text("姓名 *") },
                placeholder = { Text("如: 张三") },
                singleLine = true,
                isError = isNameError,
                supportingText = {
                    if (isNameError) Text("姓名不能为空")
                },
            )
            if (isEditMode) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    label = { Text("联系电话") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("角色") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "选择角色")
                            }
                        },
                        readOnly = true,
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        roles.forEach { roleItem ->
                            DropdownMenuItem(
                                text = { Text(roleItem) },
                                onClick = {
                                    role = roleItem
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    label = { Text("备注") },
                    maxLines = 3,
                )
            }
        }

        Button(
            onClick = {
                if (isSaving) return@Button
                val nameValid = name.isNotBlank()
                isNameError = !nameValid
                if (!nameValid) return@Button
                isSaving = true
                coroutineScope.launch {
                    if (isEditMode) {
                        val existing = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).operatorDao().getOperatorById(operatorId!!)
                        }
                        if (existing != null) {
                            viewModel.updateOperator(
                                existing.copy(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    role = role.trim(),
                                    remark = remark.trim(),
                                ),
                            )
                            Toast.makeText(context, "更新经手人成功", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            isSaving = false
                        }
                    } else {
                        val success = viewModel.addOperator(
                            name = name.trim(),
                            phone = "",
                            role = "操作员",
                            remark = "",
                        )
                        if (success) {
                            Toast.makeText(context, "添加经手人成功", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            Toast.makeText(context, "经手人姓名已存在", Toast.LENGTH_SHORT).show()
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
fun OperatorItem(
    operator: Operator,
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
            containerColor = if (operator.enabled) MaterialTheme.colorScheme.surface
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
                        operator.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            operator.role,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (!operator.enabled) {
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
                if (operator.phone.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = "电话",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            operator.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (operator.remark.isNotEmpty()) {
                    Text(
                        operator.remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleEnabled, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (operator.enabled) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                        contentDescription = if (operator.enabled) "禁用" else "启用",
                        tint = if (operator.enabled) MaterialTheme.colorScheme.primary
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
