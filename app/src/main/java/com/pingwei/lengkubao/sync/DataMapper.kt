// File: sync/DataMapper.kt
package com.pingwei.lengkubao.sync

import com.pingwei.lengkubao.data.db.entity.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据映射器（适配电脑端JSON格式）
 * 负责将手持端实体映射为电脑端可识别的JSON同步数据格式
 */
class DataMapper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 映射客户数据 -> CUSTOMER|JSON
     */
    fun mapCustomerToJson(customer: Customer): String {
        val jsonData = mapOf(
            "code" to customer.customerNo,          // 客户编号
            "name" to customer.customerName,       // 客户名称
            "phone" to (customer.phone ?: ""),     // 联系电话
            "contact" to "",                       // 联系人（手持端无此字段）
            "address" to ""                        // 地址（手持端无此字段）
        )
        return convertMapToJson(jsonData)
    }

    /**
     * 映射入库单数据 -> INBOUND|JSON
     * 注意：手持端的入库单有明细，电脑端是单条记录格式
     * 需要将多条明细合并或分别发送
     */
    fun mapInStockBillToJsonList(bill: InStockBill, items: List<InStockItem>): List<String> {
        return items.map { item ->
            val jsonData = mapOf(
                "bill_no" to bill.billNo,                   // 入库单号
                "client_code" to bill.customerNo,          // 客户编号
                "client_name" to (bill.customerName ?: ""), // 客户名称
                "location_code" to bill.locationName,      // 库位编码/名称
                "product_spec" to item.productName,        // 商品型号/名称
                "quantity" to item.quantity.toString(),    // 数量（字符串格式）
                "unit_price" to item.unitPrice.toString(), // 单价
                "total_amount" to item.amount.toString(),  // 金额
                "handler" to bill.operatorName,            // 经手人
                "creator" to "手持端",                      // 创建人
                "date" to dateFormat.format(Date(bill.createTime)) // 日期
            )
            convertMapToJson(jsonData)
        }
    }

    /**
     * 映射销售单数据 -> SALES|JSON
     */
    fun mapSaleBillToJsonList(bill: SaleBill, items: List<SaleItem>): List<String> {
        return items.map { item ->
            val jsonData = mapOf(
                "order_no" to bill.billNo,                 // 销售单号
                "client_code" to bill.customerNo,          // 客户编号
                "client_name" to bill.customerName,        // 客户名称
                "spec" to item.productName,                // 商品型号/名称
                "quantity" to item.quantity.toString(),    // 数量
                "unit_price" to item.salePrice.toString(), // 销售单价
                "total_amount" to item.amount.toString(),  // 金额
                "date" to dateFormat.format(Date(bill.createTime)), // 日期
                "handler" to bill.operatorName,            // 经手人
                "creator" to "手持端",                      // 创建人
                "location" to bill.locationName,           // 库位
                "customer_balance" to bill.customerBalance.toString() // 客户余额
            )
            convertMapToJson(jsonData)
        }
    }

    /**
     * 映射包装单数据 -> PACKAGING|JSON
     */
    fun mapPackagingBillToJsonList(bill: PackagingBill, items: List<PackagingItem>): List<String> {
        return items.map { item ->
            val jsonData = mapOf(
                "order_no" to bill.billNo,                 // 包装单号
                "client_code" to bill.customerNo,          // 客户编号
                "client_name" to bill.customerName,        // 客户名称
                "pack_type" to item.packagingType,         // 包装类型
                "quantity" to item.quantity.toString(),    // 数量
                "unit_price" to item.unitPrice.toString(), // 单价
                "total_amount" to item.amount.toString(),  // 金额
                "handler" to bill.operatorName,            // 经手人
                "creator" to "手持端",                      // 创建人
                "date" to bill.billDate                    // 单据日期
            )
            convertMapToJson(jsonData)
        }
    }

    /**
     * 映射商品数据（如果需要）
     */
    fun mapProductToJson(product: Product): String {
        val jsonData = mapOf(
            "name" to product.productName,                // 商品名称
            "code" to product.productNo,                  // 商品编号
            "category" to product.category,               // 分类
            "unit" to product.unit,                       // 单位
            "standard_price" to product.standardPrice.toString(), // 标准单价
            "is_active" to product.enabled                // 是否启用
        )
        return convertMapToJson(jsonData)
    }

    /**
     * 映射库位数据（如果需要）
     */
    fun mapLocationToJson(location: Location): String {
        val jsonData = mapOf(
            "name" to location.locationName,
            "code" to location.locationName,
            "description" to location.description,
            "capacity" to location.capacity.toString(),
            "is_active" to location.enabled,
        )
        return convertMapToJson(jsonData)
    }

    fun mapOperatorToJson(operator: Operator): String {
        val jsonData = mapOf(
            "name" to operator.name,
            "code" to operator.name,
            "phone" to operator.phone,
            "role" to operator.role,
            "is_active" to operator.enabled,
        )
        return convertMapToJson(jsonData)
    }

    /**
     * 映射预售单数据 -> PRESALE|JSON（预留，待 PC 端对接）
     */
    fun mapPreSaleBillToJson(bill: PreSaleBill, items: List<PreSaleItem>): String {
        val jsonData = mapOf(
            "order_no" to bill.billNo,
            "buyer_code" to bill.buyerNo,
            "buyer_name" to bill.buyerName,
            "sale_mode" to bill.saleMode,
            "total_amount" to bill.totalAmount.toString(),
            "paid_amount" to bill.paidAmount.toString(),
            "status" to bill.status,
            "date" to dateFormat.format(Date(bill.createTime)),
            "handler" to bill.operatorName,
            "location" to bill.locationName,
            "item_count" to items.size.toString()
        )
        return convertMapToJson(jsonData)
    }

    /**
     * 映射收款记录 -> PAYMENT|JSON（预留，待 PC 端对接）
     */
    fun mapPaymentToJson(payment: PaymentRecord, bill: PreSaleBill): String {
        val jsonData = mapOf(
            "order_no" to bill.billNo,
            "buyer_code" to bill.buyerNo,
            "amount" to payment.amount.toString(),
            "pay_method" to payment.payMethod,
            "pay_time" to payment.payTime.toString(),
            "remark" to payment.remark
        )
        return convertMapToJson(jsonData)
    }

    /**
     * 将Map转换为JSON字符串
     */
    private fun convertMapToJson(data: Map<String, Any>): String {
        // 简化版JSON转换，实际项目建议使用Gson或Moshi
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")

        val entries = data.entries.toList()
        for ((index, entry) in entries.withIndex()) {
            val key = entry.key
            val value = entry.value

            jsonBuilder.append("\"$key\":")

            when (value) {
                is String -> jsonBuilder.append("\"$value\"")
                is Boolean -> jsonBuilder.append(if (value) "true" else "false")
                is Number -> jsonBuilder.append(value)
                else -> jsonBuilder.append("\"${value.toString()}\"")
            }

            if (index < entries.size - 1) {
                jsonBuilder.append(",")
            }
        }

        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }
}

/**
 * JSON工具类
 */
object JsonSyncHelper {

    /**
     * 构建同步消息
     */
    fun buildSyncMessage(dataType: String, jsonData: String): String {
        return "$dataType|$jsonData"
    }

    /**
     * 构建批量同步消息
     */
    fun buildBatchSyncMessage(messages: List<String>): String {
        return messages.joinToString("\n")
    }
}