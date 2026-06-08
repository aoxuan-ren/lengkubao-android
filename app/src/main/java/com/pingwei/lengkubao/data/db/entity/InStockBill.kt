// data/db/entity/InStockBill.kt（建议修改版）
package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "in_stock_bill")
data class InStockBill(
    @ColumnInfo(name = "bill_no")
    val billNo: String,

    @ColumnInfo(name = "customer_no")
    val customerNo: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String? = "",  // 新增字段，便于显示

    @ColumnInfo(name = "location_id")
    val locationId: Long,

    @ColumnInfo(name = "location_name")
    val locationName: String = "",  // 新增字段，便于显示

    @ColumnInfo(name = "operator_id")
    val operatorId: Long,

    @ColumnInfo(name = "operator_name")
    val operatorName: String = "",  // 新增字段，便于显示

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double = 0.0,

    @ColumnInfo(name = "total_quantity")
    val totalQuantity: Int = 0,

    @ColumnInfo(name = "status")
    val status: String = "1",

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "print_time")
    val printTime: Long? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}