package com.pingwei.lengkubao.ui.cashflow

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pingwei.lengkubao.data.model.StatRow
import com.pingwei.lengkubao.ui.cashflow.viewmodel.CashFlowViewModel
import com.pingwei.lengkubao.ui.instock.components.CompactSelectField
import com.pingwei.lengkubao.ui.theme.AppDimens
import com.pingwei.lengkubao.utils.PrintUtils
import java.util.*

@Composable
fun CashFlowStatsTab(
    viewModel: CashFlowViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val filter by viewModel.filter.collectAsState()
    val statsByDate by viewModel.statsByDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(filter.startDate, filter.endDate) {
        viewModel.loadStats()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing)
        ) {
            CompactSelectField(
                text = filter.startDate,
                placeholder = "开始日期",
                onClick = {
                    showDatePicker(context, filter.startDate) { date ->
                        viewModel.updateFilter { it.copy(startDate = date) }
                        viewModel.loadStats()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            CompactSelectField(
                text = filter.endDate,
                placeholder = "结束日期",
                onClick = {
                    showDatePicker(context, filter.endDate) { date ->
                        viewModel.updateFilter { it.copy(endDate = date) }
                        viewModel.loadStats()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            statsByDate.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("该时段暂无统计数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                StatsTable(
                    modifier = Modifier.weight(1f),
                    rows = statsByDate
                )
            }
        }
    }
}

@Composable
private fun StatsTable(rows: List<StatRow>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("日期", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("收入", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("支出", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("利润", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
        }
        items(rows, key = { it.groupKey }) { row ->
            StatRowItem(row)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            val totalIncome = rows.sumOf { it.income }
            val totalExpense = rows.sumOf { it.expense }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("合计", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text(
                    PrintUtils.formatAmount(totalIncome),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    PrintUtils.formatAmount(totalExpense),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    PrintUtils.formatAmount(totalIncome - totalExpense),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatRowItem(row: StatRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.groupKey, modifier = Modifier.weight(1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            PrintUtils.formatAmount(row.income),
            color = Color(0xFF2E7D32),
            modifier = Modifier.weight(1f)
        )
        Text(
            PrintUtils.formatAmount(row.expense),
            color = Color(0xFFC62828),
            modifier = Modifier.weight(1f)
        )
        Text(
            PrintUtils.formatAmount(row.profit),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun showDatePicker(context: android.content.Context, current: String, onSelected: (String) -> Unit) {
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
    ).show()
}
