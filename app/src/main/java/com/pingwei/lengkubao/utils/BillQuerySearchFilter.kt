package com.pingwei.lengkubao.utils

import com.pingwei.lengkubao.data.db.entity.Customer

/**
 * 单据查询页搜索：单据号、客户编号、客户名称、拼音首字母。
 */
object BillQuerySearchFilter {

    const val PLACEHOLDER = "输入单据号/客户名称/首字母"

    fun matches(
        keyword: String?,
        billNo: String,
        customerNo: String,
        customerName: String?
    ): Boolean {
        if (keyword.isNullOrBlank()) return true

        val key = keyword.trim()
        if (billNo.contains(key, ignoreCase = true)) {
            return true
        }

        return CustomerSearchFilter.matches(
            Customer(
                customerNo = customerNo,
                customerName = customerName.orEmpty()
            ),
            key
        )
    }
}
