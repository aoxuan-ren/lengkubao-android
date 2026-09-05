package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "customerNo", index = true)
    val customerNo: String, // 格式：C001, C002, ...

    @ColumnInfo(name = "customerName")
    val customerName: String,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    @ColumnInfo(name = "qr_code_path")
    val qrCodePath: String? = null, // 存储二维码图片路径

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis(),

    /** 同步状态：0-未同步，1-已同步，默认为0 */
    @ColumnInfo(name = "sync_status", defaultValue = "0")
    val syncStatus: Int = 0,

    /** 客户类型：SELLER=货主/卖家，BUYER=买家 */
    @ColumnInfo(name = "customer_type", defaultValue = "SELLER")
    val customerType: String = CustomerType.SELLER,

    /** 是否启用（与 PC clients.status 对应） */
    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true
)