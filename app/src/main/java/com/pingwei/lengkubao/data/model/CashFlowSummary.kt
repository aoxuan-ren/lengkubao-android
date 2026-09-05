package com.pingwei.lengkubao.data.model

data class CashFlowSummary(
    val totalIncome: Double,
    val totalExpense: Double
) {
    val profit: Double get() = totalIncome - totalExpense
}
