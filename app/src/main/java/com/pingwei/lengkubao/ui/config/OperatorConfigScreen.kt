// ui/config/OperatorConfigScreen.kt (完整版)
package com.pingwei.lengkubao.ui.config

import android.app.Application
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
import com.pingwei.lengkubao.data.db.entity.Operator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun OperatorConfigScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: OperatorConfigViewModel = viewModel(
        factory = OperatorConfigViewModelFactory(context.applicationContext as Application)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 监听视图模型状态
    val operators by viewModel.operators.collectAsState(initial = emptyList())
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val searchText by viewModel.searchText.collectAsState()

    // 控制对话框显示
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedOperator by remember { mutableStateOf<Operator?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("经手人管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 只保留添加经手人按钮，删除了初始化默认经手人按钮
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
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
                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { viewModel.searchOperators(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索经手人姓名或编号...") },
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

                // 经手人列表
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val displayList = if (searchText.isNotEmpty()) searchResults else operators

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
                                if (searchText.isNotEmpty()) {
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
                                        selectedOperator = operator
                                        showEditDialog = true
                                    },
                                    onDelete = {
                                        selectedOperator = operator
                                        showDeleteDialog = true
                                    },
                                    onToggleEnabled = {
                                        coroutineScope.launch {
                                            viewModel.toggleOperatorEnabled(operator)
                                            val status = if (operator.enabled) "禁用" else "启用"
                                            snackbarHostState.showSnackbar(
                                                "已${status} ${operator.name}"
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

        // 添加经手人对话框
        if (showAddDialog) {
            AddOperatorDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { operatorNo, name, phone, role, remark ->
                    coroutineScope.launch {
                        val success = viewModel.addOperator(
                            operatorNo = operatorNo,
                            name = name,
                            phone = phone,
                            role = role,
                            remark = remark
                        )
                        if (success) {
                            snackbarHostState.showSnackbar(
                                "添加经手人成功"
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                "经手人编号已存在"
                            )
                        }
                        showAddDialog = false
                    }
                }
            )
        }

        // 编辑经手人对话框
        if (showEditDialog && selectedOperator != null) {
            EditOperatorDialog(
                operator = selectedOperator!!,
                onDismiss = { showEditDialog = false },
                onConfirm = { operatorNo, name, phone, role, remark ->
                    coroutineScope.launch {
                        viewModel.updateOperator(
                            operator = selectedOperator!!.copy(
                                operatorNo = operatorNo,
                                name = name,
                                phone = phone,
                                role = role,
                                remark = remark
                            )
                        )
                        snackbarHostState.showSnackbar(
                            "更新经手人成功"
                        )
                        showEditDialog = false
                    }
                }
            )
        }

        // 删除确认对话框
        if (showDeleteDialog && selectedOperator != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = {
                    Text("确定要删除经手人 ${selectedOperator!!.name} (${selectedOperator!!.operatorNo}) 吗？此操作不可恢复。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.deleteOperator(selectedOperator!!)
                                snackbarHostState.showSnackbar(
                                    "删除经手人成功"
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
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        operator.operatorNo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
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
                Text(
                    operator.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleEnabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (operator.enabled) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                        contentDescription = if (operator.enabled) "禁用" else "启用",
                        tint = if (operator.enabled) MaterialTheme.colorScheme.primary
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
fun AddOperatorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var operatorNo by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    val hasGeneratedNo = remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 自动生成经手人编号
    LaunchedEffect(Unit) {
        if (!hasGeneratedNo.value) {
            val newNo = withContext(Dispatchers.IO) {
                try {
                    val db = com.pingwei.lengkubao.data.db.AppDatabase.getInstance(context)
                    // 使用 getAllSimple() 方法获取所有经手人列表
                    val allOperators = db.operatorDao().getAllSimple()

                    var maxNumber = 0
                    val pattern = Regex("""OP(\d+)""")

                    if (allOperators.isNotEmpty()) {
                        allOperators.forEach { op ->
                            val matchResult = pattern.find(op.operatorNo)
                            if (matchResult != null) {
                                val number = matchResult.groupValues[1].toIntOrNull() ?: 0
                                if (number > maxNumber) {
                                    maxNumber = number
                                }
                            }
                        }
                    }

                    val newNumber = maxNumber + 1
                    "OP${String.format("%02d", newNumber)}"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "OP01"
                }
            }

            operatorNo = newNo
            hasGeneratedNo.value = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加经手人") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 经手人编号 - 自动生成，只读显示
                OutlinedTextField(
                    value = operatorNo,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("经手人编号（自动生成）") },
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                // 姓名 - 唯一需要填写的
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("姓名 *") },
                    placeholder = { Text("如: 张三") },
                    singleLine = true,
                    isError = isNameError,
                    supportingText = {
                        if (isNameError) {
                            Text("姓名不能为空")
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nameValid = name.isNotBlank()
                    isNameError = !nameValid

                    if (nameValid) {
                        keyboardController?.hide()
                        onConfirm(
                            operatorNo,  // 使用自动生成的编号
                            name.trim(),
                            "",  // 电话传空
                            "操作员",  // 角色使用默认值
                            ""   // 备注传空
                        )
                        onDismiss()
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
fun EditOperatorDialog(
    operator: Operator,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var operatorNo by remember { mutableStateOf(operator.operatorNo) }
    var name by remember { mutableStateOf(operator.name) }
    var phone by remember { mutableStateOf(operator.phone) }
    var role by remember { mutableStateOf(operator.role) }
    var remark by remember { mutableStateOf(operator.remark) }

    var isNoError by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val roles = listOf("操作员", "管理员", "财务", "仓管员", "销售员")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑经手人") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = operatorNo,
                    onValueChange = {
                        operatorNo = it
                        isNoError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("经手人编号 *") },
                    singleLine = true,
                    isError = isNoError,
                    supportingText = {
                        if (isNoError) {
                            Text("经手人编号不能为空")
                        }
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("姓名 *") },
                    singleLine = true,
                    isError = isNameError,
                    supportingText = {
                        if (isNameError) {
                            Text("姓名不能为空")
                        }
                    }
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("联系电话") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                // 角色选择
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        readOnly = true
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        roles.forEach { roleItem ->
                            DropdownMenuItem(
                                text = { Text(roleItem) },
                                onClick = {
                                    role = roleItem
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val noValid = operatorNo.isNotBlank()
                    val nameValid = name.isNotBlank()

                    isNoError = !noValid
                    isNameError = !nameValid

                    if (noValid && nameValid) {
                        keyboardController?.hide()
                        onConfirm(
                            operatorNo.trim(),
                            name.trim(),
                            phone.trim(),
                            role.trim(),
                            remark.trim()
                        )
                        onDismiss()
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