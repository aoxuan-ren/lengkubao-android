package com.pingwei.lengkubao.ui.cashflow

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.data.db.entity.LedgerCategory
import com.pingwei.lengkubao.service.CashFlowService
import com.pingwei.lengkubao.ui.cashflow.viewmodel.CashFlowViewModel
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageDialog(
    categories: List<LedgerCategory>,
    onDismiss: () -> Unit,
    onAddCategory: (type: String, name: String) -> Unit,
    onToggleEnabled: (LedgerCategory) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var newCategoryName by remember { mutableStateOf("") }
    val type = if (selectedTab == 0) "INCOME" else "EXPENSE"
    val filtered = categories
        .filter { CashFlowService.isSelectableHandheldCategory(it) }
        .filter { it.type == type }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理类别") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0 to "收入", 1 to "支出").forEachIndexed { index, (tab, label) ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (selectedTab == tab) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { selectedTab = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == tab) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = "新类别名称",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onAddCategory(type, newCategoryName)
                                newCategoryName = ""
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
                filtered.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Medium)
                            if (category.isSystem) {
                                Text("系统内置", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = category.enabled,
                            onCheckedChange = { onToggleEnabled(category) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun CashFlowAddTab(
    viewModel: CashFlowViewModel,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()
    val selectableCategories = categories.filter { CashFlowService.isSelectableHandheldCategory(it) }
    val editingEntry by viewModel.editingEntry.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    var entryType by remember { mutableStateOf("INCOME") }
    var selectedCategory by remember { mutableStateOf<LedgerCategory?>(null) }
    var amountText by remember { mutableStateOf("") }
    var entryDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var remark by remember { mutableStateOf("") }
    var showCategoryManage by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(editingEntry) {
        editingEntry?.let { entry ->
            entryType = entry.type
            amountText = entry.amount.toString()
            entryDate = entry.entryDate
            remark = entry.remark
            selectedCategory = selectableCategories.find { it.id == entry.categoryId }
        }
    }

    LaunchedEffect(entryType, selectableCategories) {
        if (selectedCategory?.type != entryType) {
            selectedCategory = selectableCategories.firstOrNull { it.type == entryType && it.enabled }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            if (it.contains("已保存") || it.contains("已更新")) {
                if (editingEntry == null) {
                    amountText = ""
                    remark = ""
                }
                onSaved()
            }
            viewModel.clearMessage()
        }
    }

    if (showCategoryManage) {
        CategoryManageDialog(
            categories = categories,
            onDismiss = { showCategoryManage = false },
            onAddCategory = viewModel::addCategory,
            onToggleEnabled = viewModel::toggleCategoryEnabled
        )
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text(if (entryType == "INCOME") "选择收入类别" else "选择支出类别") },
            text = {
                Column {
                    selectableCategories.filter { it.type == entryType && it.enabled }.forEach { category ->
                        Text(
                            text = category.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategory = category
                                    showCategoryPicker = false
                                }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
    ) {
        if (editingEntry != null) {
            TextButton(
                onClick = { viewModel.clearEditingEntry() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("取消编辑")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppDimens.pagePadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("INCOME" to "收入", "EXPENSE" to "支出").forEachIndexed { index, (type, label) ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                            }
                            val selected = entryType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { entryType = type },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showCategoryManage = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "管理类别",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                CompactSelectField(
                    text = selectedCategory?.name.orEmpty(),
                    placeholder = "请选择类别",
                    isError = selectedCategory == null,
                    onClick = { showCategoryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                ) {
                    CompactTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = "金额",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    CompactSelectField(
                        text = entryDate,
                        placeholder = "日期",
                        onClick = {
                            val parts = entryDate.split("-")
                            val cal = Calendar.getInstance()
                            if (parts.size == 3) {
                                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    entryDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                CompactTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    placeholder = "备注（可选）",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                val category = selectedCategory
                if (category == null) {
                    Toast.makeText(context, "请选择类别", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(context, "请输入有效金额", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.saveEntry(
                    type = entryType,
                    category = category,
                    amount = amount,
                    entryDate = entryDate,
                    remark = remark
                )
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.buttonHeight)
        ) {
            Text(if (editingEntry != null) "保存修改" else "保存")
        }

        if (editingEntry != null && editingEntry!!.status == 1) {
            OutlinedButton(
                onClick = { viewModel.voidEntry(editingEntry!!.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("作废此流水")
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.height(40.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}
