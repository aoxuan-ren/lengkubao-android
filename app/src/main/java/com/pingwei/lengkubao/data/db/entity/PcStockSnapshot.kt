package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "pc_stock_snapshot",
    primaryKeys = ["location_id", "product_id"],
    indices = [
        Index(value = ["location_id"]),
        Index(value = ["product_id"]),
        Index(value = ["snapshot_time"])
    ]
)
data class PcStockSnapshot(
    @ColumnInfo(name = "location_id")
    val locationId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "spec")
    val spec: String,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "current_quantity")
    val currentQuantity: Int,

    @ColumnInfo(name = "snapshot_time")
    val snapshotTime: Long
)

