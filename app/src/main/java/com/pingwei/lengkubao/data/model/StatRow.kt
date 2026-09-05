package com.pingwei.lengkubao.data.model

import androidx.room.ColumnInfo

data class StatRow(
    @ColumnInfo(name = "groupKey")
    val groupKey: String,
    val income: Double,
    val expense: Double
) {
    val profit: Double get() = income - expense
}

data class SummaryAmounts(
    val totalIncome: Double,
    val totalExpense: Double
)
