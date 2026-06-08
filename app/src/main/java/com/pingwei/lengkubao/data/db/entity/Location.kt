package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location")
data class Location(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "location_no")
    val locationNo: String, // 库位编号，如 "A1"

    @ColumnInfo(name = "location_name")
    val locationName: String, // 库位名称，如 "东1库"

    @ColumnInfo(name = "description")
    val description: String = "", // 描述

    @ColumnInfo(name = "capacity")
    val capacity: Int = 0, // 容量限制

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true, // 是否启用

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    /** 同步状态：0-未同步，1-已同步，默认为0 */
    @ColumnInfo(name = "sync_status", defaultValue = "0")
    val syncStatus: Int = 0
)