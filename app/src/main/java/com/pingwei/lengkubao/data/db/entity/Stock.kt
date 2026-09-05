package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock")
data class Stock(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_no")
    val productNo: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "location_id")
    val locationId: Long,

    @ColumnInfo(name = "current_quantity")
    var currentQuantity: Int = 0,

    @ColumnInfo(name = "reserved_quantity")
    var reservedQuantity: Int = 0,

    @ColumnInfo(name = "last_updated")
    var lastUpdated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_bill_no")
    var lastBillNo: String = "",
) {
    val availableQuantity: Int
        get() = currentQuantity - reservedQuantity
}
