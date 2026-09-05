package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packaging_type")
data class PackagingType(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "type_name")
    val typeName: String,

    @ColumnInfo(name = "unit")
    val unit: String = "个",

    @ColumnInfo(name = "unit_price")
    val unitPrice: Double = 0.0,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),
)
