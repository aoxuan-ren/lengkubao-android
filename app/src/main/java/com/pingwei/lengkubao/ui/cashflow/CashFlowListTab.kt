package com.pingwei.lengkubao.ui.cashflow

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwei.lengkubao.data.db.entity.LedgerEntry
import com.pingwei.lengkubao.ui.cashflow.viewmodel.CashFlowViewModel
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.utils.PrintUtils
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun CashFlowListTab(
    viewModel: CashFlowViewModel,
    modifier: Modifier = Modifier,
    onEditEntry: (LedgerEntry) -> Unit
) {
    val context = LocalContext.current
    val filter by viewModel.filter.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val summary by viewModel.summary.collectAsState()

    var keyword by remember { mutableStateOf(filter.keyword.orEmpty()) }
    var typeFilter by remember { mutableStateOf(filter.type) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun applyKeywordSearch() {
        viewModel.updateFilter { it.copy(keyword = keyword.takeIf { k -> k.isNotBlank() }) }
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
        contentPadding = PaddingValues(vertical = AppDimens.pagePadding)
    ) {
        item {
            SummaryRow(summary = summary)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppDimens.pagePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactTypeFilter(
                        selected = typeFilter,
                        onSelected = { type ->
                            typeFilter = type
                            viewModel.updateFilter { it.copy(type = type) }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
                    ) {
                        CompactSelectField(
                            text = filter.startDate,
                            placeholder = "开始日期",
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactSelectField(
                            text = filter.endDate,
                            placeholder = "结束日期",
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CompactSearchField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        onSearch = { applyKeywordSearch() },
                        placeholder = "流水号/备注/类别"
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无流水记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                LedgerEntryRow(
                    entry = entry,
                    onClick = { onEditEntry(entry) }
                )
            }
        }
    }

    LaunchedEffect(showStartPicker) {
        if (!showStartPicker) return@LaunchedEffect
        showDatePicker(
            context = context,
            current = filter.startDate,
            onSelected = { date ->
                if (date > filter.endDate) {
                    Toast.makeText(context, "开始日期不能晚于结束日期", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateFilter { it.copy(startDate = date) }
                }
            },
            onDismiss = { showStartPicker = false }
        )
    }
    LaunchedEffect(showEndPicker) {
        if (!showEndPicker) return@LaunchedEffect
        showDatePicker(
            context = context,
            current = filter.endDate,
            onSelected = { date ->
                if (date < filter.startDate) {
                    Toast.makeText(context, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateFilter { it.copy(endDate = date) }
                }
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun CompactTypeFilter(
    selected: String?,
    onSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            null to "全部",
            "INCOME" to "收入",
            "EXPENSE" to "支出"
        ).forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
            val isSelected = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelected(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
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
        }
    )
}

@Composable
private fun SummaryRow(summary: com.pingwei.lengkubao.data.model.CashFlowSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryItem("收入", summary.totalIncome, Color(0xFF2E7D32))
        SummaryItem("支出", summary.totalExpense, Color(0xFFC62828))
        SummaryItem("利润", summary.profit, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SummaryItem(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            PrintUtils.formatAmount(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun LedgerEntryRow(entry: LedgerEntry, onClick: () -> Unit) {
    val isVoided = entry.status == 0
    val typeColor = when (entry.type) {
        "INCOME" -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.entryDate, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (entry.type == "INCOME") "收" else "支",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                if (isVoided) {
                    Text(
                        "作废",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                PrintUtils.formatAmount(entry.amount),
                fontWeight = FontWeight.Bold,
                color = if (isVoided) Color.Gray else typeColor
            )
        }
        Text(
            "${entry.categoryName} · ${entry.entryNo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.remark.isNotBlank()) {
            Text(
                entry.remark,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    current: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = current.split("-")
    val cal = Calendar.getInstance()
    if (parts.size == 3) {
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            onSelected(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d))
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).apply {
        setOnDismissListener { onDismiss() }
        show()
    }
}
