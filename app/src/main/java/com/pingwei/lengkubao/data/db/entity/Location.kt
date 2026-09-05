package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location")
data class Location(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "capacity")
    val capacity: Int = 0,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status", defaultValue = "0")
    val syncStatus: Int = 0,
)
