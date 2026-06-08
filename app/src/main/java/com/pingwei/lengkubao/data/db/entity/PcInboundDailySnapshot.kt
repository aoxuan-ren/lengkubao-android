package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "pc_inbound_daily_snapshot",
    primaryKeys = ["date", "customer_no", "location_name", "spec"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["customer_no"]),
        Index(value = ["location_name"]),
        Index(value = ["spec"]),
        Index(value = ["snapshot_time"])
    ]
)
data class PcInboundDailySnapshot(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "customer_no")
    val customerNo: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "spec")
    val spec: String,

    @ColumnInfo(name = "quantity")
    val quantity: Int,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "order_count")
    val orderCount: Int,

    @ColumnInfo(name = "snapshot_time")
    val snapshotTime: Long
)

