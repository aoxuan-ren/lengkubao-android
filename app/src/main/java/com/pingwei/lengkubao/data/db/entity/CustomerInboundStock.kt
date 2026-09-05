package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "customer_inbound_stock",
    primaryKeys = ["customer_no", "location_id", "product_id"]
)
data class CustomerInboundStock(
    @ColumnInfo(name = "customer_no")
    val customerNo: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "location_id")
    val locationId: Long,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_no")
    val productNo: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "inbound_quantity")
    val inboundQuantity: Int,

    @ColumnInfo(name = "reserved_quantity")
    val reservedQuantity: Int = 0,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
