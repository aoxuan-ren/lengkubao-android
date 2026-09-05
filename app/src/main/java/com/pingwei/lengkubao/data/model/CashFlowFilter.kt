package com.pingwei.lengkubao.data.model

data class CashFlowFilter(
    val startDate: String,
    val endDate: String,
    val type: String? = null,
    val categoryId: Long = 0,
    val keyword: String? = null,
    val includeVoided: Boolean = false
) {
    val statusFilter: Int get() = if (includeVoided) -1 else 1
}
