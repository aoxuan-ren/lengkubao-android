// data/db/entity/PackagingType.kt
package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packaging_type")
data class PackagingType(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "type_no")
    val typeNo: String, // 包装类型编号，如："BZ01"

    @ColumnInfo(name = "type_name")
    val typeName: String, // 包装类型名称，如："纸箱"

    @ColumnInfo(name = "unit")
    val unit: String = "个", // 单位：个、卷、只等

    @ColumnInfo(name = "unit_price")
    val unitPrice: Double = 0.0, // 默认单价

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true, // 是否启用

    @ColumnInfo(name = "remark")
    val remark: String = "", // 备注

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis()
)