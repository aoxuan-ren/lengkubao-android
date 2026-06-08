package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deductions")
data class Deduction(
    @ColumnInfo(name = "customer_no", index = true)
    val customerNo: String,              // 对应电脑端 client_code

    @ColumnInfo(name = "customer_name")
    val customerName: String,             // 对应电脑端 client_name

    @ColumnInfo(name = "amount")
    val amount: Double,                    // 对应电脑端 amount

    @ColumnInfo(name = "deduct_date", index = true)
    val deductDate: String,                // 对应电脑端 deduct_date (yyyy-MM-dd)

    @ColumnInfo(name = "reason")
    val reason: String? = null,             // 对应电脑端 reason

    @ColumnInfo(name = "handler")
    val handler: String? = null,            // 对应电脑端 handler

    @ColumnInfo(name = "creator")
    val creator: String? = null,            // 对应电脑端 creator

    @ColumnInfo(name = "status")
    val status: Long = 1,                   // 对应电脑端 status (1-正常, 0-作废)

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),  // 对应电脑端 created_time

    // 手持端特有字段
    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,                 // 0-未同步, 1-已同步

    @ColumnInfo(name = "sync_time")
    val syncTime: Long? = null,               // 同步时间戳

    @ColumnInfo(name = "operator_id")
    val operatorId: Long? = null,              // 操作员ID

    @ColumnInfo(name = "operator_name")
    val operatorName: String? = null           // 操作员姓名（冗余）
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}