package com.pingwei.lengkubao.ui.advancededuction

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.service.SunmiPrintService
import com.pingwei.lengkubao.ui.advancededuction.viewmodel.AdvanceDeductionViewModel
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.instock.components.OperatorSelectorDialog
import com.pingwei.lengkubao.ui.instock.components.SearchableCustomerField

private val CompactFieldHeight = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceDeductionScreen(
    viewModel: AdvanceDeductionViewModel
) {
    val context = LocalContext.current
    val allCustomers by viewModel.allCustomers.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val operators by viewModel.operators.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastSavedDeduction by viewModel.lastSavedDeduction.collectAsState()

    var advanceAmount by remember { mutableStateOf("") }
    var advanceReason by remember { mutableStateOf("") }
    var selectedAdvanceOperator by remember { mutableStateOf<Operator?>(null) }
    var showAdvanceOperatorDialog by remember { mutableStateOf(false) }

    var deductionQuantity by remember { mutableStateOf("") }
    var deductionUnitPrice by remember { mutableStateOf("") }
    var deductionReason by remember { mutableStateOf("") }
    var selectedDeductionOperator by remember { mutableStateOf<Operator?>(null) }
    var showDeductionOperatorDialog by remember { mutableStateOf(false) }

    val calculatedDeductionAmount = remember(deductionQuantity, deductionUnitPrice) {
        val quantity = deductionQuantity.toIntOrNull() ?: 0
        val unitPrice = deductionUnitPrice.toDoubleOrNull() ?: 0.0
        quantity * unitPrice
    }

    LaunchedEffect(lastSavedDeduction) {
        val deduction = lastSavedDeduction ?: return@LaunchedEffect
        try {
            val printService = SunmiPrintService.getInstance(context)
            val success = printService.printDeductionBill(
                customerName = deduction.customerName,
                customerNo = deduction.customerNo,
                quantity = deduction.quantity,
                unitPrice = deduction.unitPrice,
                amount = deduction.amount,
                reason = deduction.reason ?: "",
                handler = deduction.handler ?: "",
                deductDate = deduction.deductDate
            )
            if (success) {
                Toast.makeText(context, "扣款单打印成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "扣款已保存，但打印失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "扣款已保存，打印失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        viewModel.clearLastSavedDeduction()
    }

    if (showAdvanceOperatorDialog) {
        OperatorSelectorDialog(
            operators = operators,
            onDismiss = { showAdvanceOperatorDialog = false },
            onOperatorSelected = {
                selectedAdvanceOperator = it
                showAdvanceOperatorDialog = false
            }
        )
    }
    if (showDeductionOperatorDialog) {
        OperatorSelectorDialog(
            operators = operators,
            onDismiss = { showDeductionOperatorDialog = false },
            onOperatorSelected = {
                selectedDeductionOperator = it
                showDeductionOperatorDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预支与扣款") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (selectedCustomer != null) {
                            SelectedCustomerCard(
                                customer = selectedCustomer!!,
                                onClear = { viewModel.selectCustomer(null) }
                            )
                        } else {
                            SearchableCustomerField(
                                customers = allCustomers,
                                selectedCustomer = null,
                                onCustomerSelected = { viewModel.selectCustomer(it) },
                                modifier = Modifier.fillMaxWidth(),
                                fieldHeight = CompactFieldHeight,
                                fieldTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                showFloatingLabel = false
                            )
                        }
                    }
                }
            }

            if (selectedCustomer != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "新增预支",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactAmountField(
                                    value = advanceAmount,
                                    onValueChange = { advanceAmount = it },
                                    modifier = Modifier.weight(1f)
                                )
                                CompactSelectField(
                                    text = selectedAdvanceOperator?.name.orEmpty(),
                                    placeholder = "请选择",
                                    isError = selectedAdvanceOperator == null,
                                    onClick = { showAdvanceOperatorDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            CompactReasonField(
                                value = advanceReason,
                                onValueChange = { advanceReason = it },
                                placeholder = "预支事由（可选）"
                            )

                            Button(
                                onClick = {
                                    if (advanceAmount.isNotBlank() && selectedAdvanceOperator != null) {
                                        viewModel.addAdvance(
                                            customerNo = selectedCustomer!!.customerNo,
                                            customerName = selectedCustomer!!.customerName,
                                            amount = advanceAmount.toDoubleOrNull() ?: 0.0,
                                            reason = advanceReason,
                                            handler = selectedAdvanceOperator!!.name,
                                            creator = selectedAdvanceOperator!!.name,
                                            operatorId = selectedAdvanceOperator!!.id
                                        )
                                        advanceAmount = ""
                                        advanceReason = ""
                                        selectedAdvanceOperator = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = advanceAmount.isNotBlank() &&
                                    (advanceAmount.toDoubleOrNull() ?: 0.0) > 0 &&
                                    selectedAdvanceOperator != null
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text("保存预支记录")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "新增扣款",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactLabeledNumberField(
                                    value = deductionQuantity,
                                    onValueChange = { deductionQuantity = it },
                                    label = "数量",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                CompactLabeledNumberField(
                                    value = deductionUnitPrice,
                                    onValueChange = { deductionUnitPrice = it },
                                    label = "单价",
                                    keyboardType = KeyboardType.Decimal,
                                    showYen = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactSelectField(
                                    text = selectedDeductionOperator?.name.orEmpty(),
                                    placeholder = "请选择",
                                    isError = selectedDeductionOperator == null,
                                    onClick = { showDeductionOperatorDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (calculatedDeductionAmount > 0) {
                                        String.format("%.2f", calculatedDeductionAmount)
                                    } else {
                                        "0.00"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                )
                            }

                            CompactReasonField(
                                value = deductionReason,
                                onValueChange = { deductionReason = it },
                                placeholder = "扣款事由（可选）"
                            )

                            Button(
                                onClick = {
                                    val quantity = deductionQuantity.toIntOrNull() ?: 0
                                    val unitPrice = deductionUnitPrice.toDoubleOrNull() ?: 0.0
                                    if (quantity > 0 && unitPrice > 0 && selectedDeductionOperator != null) {
                                        viewModel.addDeduction(
                                            customerNo = selectedCustomer!!.customerNo,
                                            customerName = selectedCustomer!!.customerName,
                                            quantity = quantity,
                                            unitPrice = unitPrice,
                                            amount = calculatedDeductionAmount,
                                            reason = deductionReason,
                                            handler = selectedDeductionOperator!!.name,
                                            creator = selectedDeductionOperator!!.name,
                                            operatorId = selectedDeductionOperator!!.id
                                        )
                                        deductionQuantity = ""
                                        deductionUnitPrice = ""
                                        deductionReason = ""
                                        selectedDeductionOperator = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828)
                                ),
                                enabled = calculatedDeductionAmount > 0 &&
                                    selectedDeductionOperator != null
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text("保存扣款记录")
                                }
                            }
                        }
                    }
                }

                item {
                    RecentRecordsSection(
                        customerNo = selectedCustomer!!.customerNo,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(CompactFieldHeight),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("¥", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "金额",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun CompactLabeledNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    showYen: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(CompactFieldHeight),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (showYen) {
                    Text("¥", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun CompactReasonField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
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

@Composable
fun SelectedCustomerCard(
    customer: Customer,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = customer.customerName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "更换客户", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun RecentRecordsSection(
    customerNo: String,
    viewModel: AdvanceDeductionViewModel
) {
    val advances by viewModel.advances.collectAsState()
    val deductions by viewModel.deductions.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncStatusLegend()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (advances.isEmpty() && deductions.isEmpty()) {
                Text(
                    text = "暂无预支和扣款记录",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                advances.take(3).forEach { advance ->
                    RecentRecordItem(
                        type = "预支",
                        amount = advance.amount,
                        date = advance.advanceDate,
                        reason = advance.reason ?: "",
                        syncStatus = advance.syncStatus,
                        color = Color(0xFF1976D2)
                    )
                }

                deductions.take(3).forEach { deduction ->
                    RecentRecordItem(
                        type = "扣款",
                        amount = deduction.amount,
                        date = deduction.deductDate,
                        reason = deduction.reason ?: "",
                        syncStatus = deduction.syncStatus,
                        color = Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
fun SyncStatusLegend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "已同步",
                fontSize = 10.sp,
                color = Color(0xFF4CAF50)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFFF9800)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "未同步",
                fontSize = 10.sp,
                color = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
fun RecentRecordItem(
    type: String,
    amount: Double,
    date: String,
    reason: String,
    syncStatus: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$type: ¥${String.format("%.2f", amount)}",
                    fontWeight = FontWeight.Medium
                )

                when (syncStatus) {
                    0 -> {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "未同步",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFF9800)
                        )
                    }
                    1 -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已同步",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    2 -> {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "同步中",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF2196F3)
                        )
                    }
                    3 -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "同步失败",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }

            if (reason.isNotBlank()) {
                Text(
                    text = reason,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = date,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RecordDetailDialog(
    record: Any?,
    onDismiss: () -> Unit
) {
    if (record != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("记录详情") },
            text = {
                Column {
                    when (record) {
                        is com.pingwei.lengkubao.data.db.entity.Advance -> {
                            DetailRow("客户", "${record.customerName} (${record.customerNo})")
                            DetailRow("金额", "¥${String.format("%.2f", record.amount)}")
                            DetailRow("日期", record.advanceDate)
                            DetailRow("事由", record.reason ?: "-")
                            DetailRow("经手人", record.handler ?: "-")
                            DetailRow(
                                "同步状态",
                                when (record.syncStatus) {
                                    0 -> "未同步"
                                    1 -> "已同步"
                                    2 -> "同步中"
                                    3 -> "失败"
                                    else -> "未知"
                                }
                            )
                        }
                        is com.pingwei.lengkubao.data.db.entity.Deduction -> {
                            DetailRow("客户", "${record.customerName} (${record.customerNo})")
                            if (record.quantity > 0) {
                                DetailRow("数量", record.quantity.toString())
                                DetailRow("单价", "¥${String.format("%.2f", record.unitPrice)}")
                            }
                            DetailRow("金额", "¥${String.format("%.2f", record.amount)}")
                            DetailRow("日期", record.deductDate)
                            DetailRow("事由", record.reason ?: "-")
                            DetailRow("经手人", record.handler ?: "-")
                            DetailRow(
                                "同步状态",
                                when (record.syncStatus) {
                                    0 -> "未同步"
                                    1 -> "已同步"
                                    2 -> "同步中"
                                    3 -> "失败"
                                    else -> "未知"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f)
        )
    }
}
