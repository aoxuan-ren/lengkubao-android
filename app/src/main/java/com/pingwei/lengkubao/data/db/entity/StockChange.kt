// data/db/entity/StockChange.kt
package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_change")
data class StockChange(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "product_id", index = true)
    val productId: Long,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "location_id", index = true)
    val locationId: Long,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "change_type")
    val changeType: ChangeType, // 变动类型

    @ColumnInfo(name = "quantity")
    val quantity: Int, // 变动数量（正数表示增加，负数表示减少）

    @ColumnInfo(name = "related_bill_id", index = true)
    val relatedBillId: Long?, // 关联单据ID

    @ColumnInfo(name = "related_bill_type")
    val relatedBillType: BillType?, // 关联单据类型

    @ColumnInfo(name = "related_bill_no")
    val relatedBillNo: String, // 关联单据号

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "remarks")
    val remarks: String = "" // 备注信息
)

