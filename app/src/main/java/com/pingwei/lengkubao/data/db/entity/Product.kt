package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "productNo")
    val productNo: String,

    @ColumnInfo(name = "productName")
    val productName: String,

    @ColumnInfo(name = "unit")
    val unit: String = "箱",

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "category")
    val category: String = "梨",

    @ColumnInfo(name = "standardPrice")
    val standardPrice: Double = 10.0,

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    /** 同步状态：0-未同步，1-已同步，默认为0 */
    @ColumnInfo(name = "sync_status", defaultValue = "0")
    val syncStatus: Int = 0
)