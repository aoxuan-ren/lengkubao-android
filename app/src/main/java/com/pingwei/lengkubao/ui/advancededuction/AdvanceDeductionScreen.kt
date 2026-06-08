package com.pingwei.lengkubao.ui.advancededuction

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.data.db.entity.Customer
import com.pingwei.lengkubao.data.db.entity.Operator
import com.pingwei.lengkubao.ui.advancededuction.viewmodel.AdvanceDeductionViewModel
import com.pingwei.lengkubao.ui.instock.components.SearchableCustomerField
import com.pingwei.lengkubao.ui.instock.components.SearchableOperatorField

private val AdvanceCompactFieldHeight = 64.dp
private val AdvanceCompactMultiLineFieldHeight = 88.dp
private val AdvanceSectionTitleSize = 16.sp
private val AdvanceInputFontSize = 15.sp
private val AdvanceLabelFontSize = 13.sp

@Composable
private fun advanceFieldTextStyle() =
    MaterialTheme.typography.bodyMedium.copy(
        fontSize = AdvanceInputFontSize,
        lineHeight = 22.sp
    )

@Composable
private fun advanceFieldLabelStyle() =
    MaterialTheme.typography.labelMedium.copy(fontSize = AdvanceLabelFontSize)

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

    var advanceAmount by remember { mutableStateOf("") }
    var advanceReason by remember { mutableStateOf("") }
    var selectedAdvanceOperator by remember { mutableStateOf<Operator?>(null) }

    var deductionAmount by remember { mutableStateOf("") }
    var deductionReason by remember { mutableStateOf("") }
    var selectedDeductionOperator by remember { mutableStateOf<Operator?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u9884\u652f\u4e0e\u6263\u6b3e") },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "\u8fd4\u56de")
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text(
                            text = "1. \u9009\u62e9\u5ba2\u6237",
                            fontSize = AdvanceSectionTitleSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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
                                label = "\u641c\u7d22\u5ba2\u6237",
                                modifier = Modifier.fillMaxWidth(),
                                fieldHeight = AdvanceCompactFieldHeight,
                                fieldTextStyle = advanceFieldTextStyle(),
                                fieldLabelStyle = advanceFieldLabelStyle()
                            )
                        }
                    }
                }
            }

            if (selectedCustomer != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "2. \u65b0\u589e\u9884\u652f",
                                    fontSize = AdvanceSectionTitleSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            AdvanceCompactOutlinedTextField(
                                value = advanceAmount,
                                onValueChange = { advanceAmount = it },
                                label = "\u9884\u652f\u91d1\u989d",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                leadingIcon = {
                                    Text("\u00a5", style = advanceFieldTextStyle())
                                }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            AdvanceCompactOutlinedTextField(
                                value = advanceReason,
                                onValueChange = { advanceReason = it },
                                label = "\u9884\u652f\u4e8b\u7531",
                                singleLine = false,
                                autoHeight = true
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            SearchableOperatorField(
                                operators = operators,
                                selectedOperator = selectedAdvanceOperator,
                                onOperatorSelected = { selectedAdvanceOperator = it },
                                label = "\u9009\u62e9\u7ecf\u624b\u4eba",
                                modifier = Modifier.fillMaxWidth(),
                                fieldHeight = AdvanceCompactFieldHeight,
                                fieldTextStyle = advanceFieldTextStyle(),
                                fieldLabelStyle = advanceFieldLabelStyle()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

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
                                        advanceAmount.toDoubleOrNull() ?: 0.0 > 0 &&
                                        selectedAdvanceOperator != null
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text("\u4fdd\u5b58\u9884\u652f\u8bb0\u5f55")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "3. \u65b0\u589e\u6263\u6b3e",
                                    fontSize = AdvanceSectionTitleSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            AdvanceCompactOutlinedTextField(
                                value = deductionAmount,
                                onValueChange = { deductionAmount = it },
                                label = "\u6263\u6b3e\u91d1\u989d",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                leadingIcon = {
                                    Text("\u00a5", style = advanceFieldTextStyle())
                                }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            AdvanceCompactOutlinedTextField(
                                value = deductionReason,
                                onValueChange = { deductionReason = it },
                                label = "\u6263\u6b3e\u4e8b\u7531",
                                singleLine = false,
                                autoHeight = true
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            SearchableOperatorField(
                                operators = operators,
                                selectedOperator = selectedDeductionOperator,
                                onOperatorSelected = { selectedDeductionOperator = it },
                                label = "\u9009\u62e9\u7ecf\u624b\u4eba",
                                modifier = Modifier.fillMaxWidth(),
                                fieldHeight = AdvanceCompactFieldHeight,
                                fieldTextStyle = advanceFieldTextStyle(),
                                fieldLabelStyle = advanceFieldLabelStyle()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (deductionAmount.isNotBlank() && selectedDeductionOperator != null) {
                                        viewModel.addDeduction(
                                            customerNo = selectedCustomer!!.customerNo,
                                            customerName = selectedCustomer!!.customerName,
                                            amount = deductionAmount.toDoubleOrNull() ?: 0.0,
                                            reason = deductionReason,
                                            handler = selectedDeductionOperator!!.name,
                                            creator = selectedDeductionOperator!!.name,
                                            operatorId = selectedDeductionOperator!!.id
                                        )
                                        deductionAmount = ""
                                        deductionReason = ""
                                        selectedDeductionOperator = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828)
                                ),
                                enabled = deductionAmount.isNotBlank() &&
                                        deductionAmount.toDoubleOrNull() ?: 0.0 > 0 &&
                                        selectedDeductionOperator != null
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Text("\u4fdd\u5b58\u6263\u6b3e\u8bb0\u5f55")
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
fun SelectedCustomerCard(
    customer: Customer,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = customer.customerName,
                    fontSize = AdvanceSectionTitleSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\u7f16\u53f7: ${customer.customerNo}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "\u66f4\u6362\u5ba2\u6237")
            }
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
                    text = "\u6700\u8fd1\u8bb0\u5f55",
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
                    text = "\u6682\u65e0\u9884\u652f\u548c\u6263\u6b3e\u8bb0\u5f55",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                advances.take(3).forEach { advance ->
                    RecentRecordItem(
                        type = "\u9884\u652f",
                        amount = advance.amount,
                        date = advance.advanceDate,
                        reason = advance.reason ?: "",
                        syncStatus = advance.syncStatus,
                        color = Color(0xFF1976D2)
                    )
                }

                deductions.take(3).forEach { deduction ->
                    RecentRecordItem(
                        type = "\u6263\u6b3e",
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
                text = "\u5df2\u540c\u6b65",
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
                text = "\u672a\u540c\u6b65",
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
                    text = "$type: \u00a5${String.format("%.2f", amount)}",
                    fontWeight = FontWeight.Medium
                )

                when (syncStatus) {
                    0 -> {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "\u672a\u540c\u6b65",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFFF9800)
                        )
                    }
                    1 -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "\u5df2\u540c\u6b65",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    2 -> {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "\u540c\u6b65\u4e2d",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF2196F3)
                        )
                    }
                    3 -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "\u540c\u6b65\u5931\u8d25",
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
private fun AdvanceCompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    autoHeight: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = when {
        singleLine -> 1
        autoHeight -> 5
        else -> 2
    },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val inputStyle = advanceFieldTextStyle()
    val labelStyle = advanceFieldLabelStyle()

    val heightModifier = when {
        singleLine -> Modifier.height(AdvanceCompactFieldHeight)
        autoHeight -> Modifier.heightIn(min = AdvanceCompactFieldHeight)
        else -> Modifier.height(AdvanceCompactMultiLineFieldHeight)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        textStyle = inputStyle,
        modifier = modifier
            .fillMaxWidth()
            .then(heightModifier),
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = if (!singleLine) minLines else 1,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

@Composable
fun RecordDetailDialog(
    record: Any?,
    onDismiss: () -> Unit
) {
    if (record != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("\u8bb0\u5f55\u8be6\u60c5") },
            text = {
                Column {
                    when (record) {
                        is com.pingwei.lengkubao.data.db.entity.Advance -> {
                            DetailRow("\u5ba2\u6237", "${record.customerName} (${record.customerNo})")
                            DetailRow("\u91d1\u989d", "\u00a5${String.format("%.2f", record.amount)}")
                            DetailRow("\u65e5\u671f", record.advanceDate)
                            DetailRow("\u4e8b\u7531", record.reason ?: "-")
                            DetailRow("\u7ecf\u624b\u4eba", record.handler ?: "-")
                            DetailRow("\u540c\u6b65\u72b6\u6001", when(record.syncStatus) {
                                0 -> "\u672a\u540c\u6b65"
                                1 -> "\u5df2\u540c\u6b65"
                                2 -> "\u540c\u6b65\u4e2d"
                                3 -> "\u5931\u8d25"
                                else -> "\u672a\u77e5"
                            })
                        }
                        is com.pingwei.lengkubao.data.db.entity.Deduction -> {
                            DetailRow("\u5ba2\u6237", "${record.customerName} (${record.customerNo})")
                            DetailRow("\u91d1\u989d", "\u00a5${String.format("%.2f", record.amount)}")
                            DetailRow("\u65e5\u671f", record.deductDate)
                            DetailRow("\u4e8b\u7531", record.reason ?: "-")
                            DetailRow("\u7ecf\u624b\u4eba", record.handler ?: "-")
                            DetailRow("\u540c\u6b65\u72b6\u6001", when(record.syncStatus) {
                                0 -> "\u672a\u540c\u6b65"
                                1 -> "\u5df2\u540c\u6b65"
                                2 -> "\u540c\u6b65\u4e2d"
                                3 -> "\u5931\u8d25"
                                else -> "\u672a\u77e5"
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("\u5173\u95ed")
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
