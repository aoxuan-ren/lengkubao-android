package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packaging_bill")
data class PackagingBill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "bill_no", index = true)
    val billNo: String,

    @ColumnInfo(name = "customer_id")
    val customerId: Long,

    @ColumnInfo(name = "customer_no")
    val customerNo: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "bill_date")
    val billDate: String,

    // 新增：包装类型标记（取包装/退包装）
    @ColumnInfo(name = "packaging_type_flag")
    val packagingTypeFlag: String = "TAKE", // TAKE-取包装, RETURN-退包装

    @ColumnInfo(name = "operator_id")
    val operatorId: Long,

    @ColumnInfo(name = "operator_name")
    val operatorName: String = "",

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double = 0.0,

    @ColumnInfo(name = "status")
    val status: String = "1",

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "print_time")
    val printTime: Long? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false,

    @ColumnInfo(name = "is_printed")
    val isPrinted: Boolean = false,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false
)