package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale_bill")
data class SaleBill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "bill_no", index = true)
    val billNo: String, // 格式：SALE-20251229-001

    @ColumnInfo(name = "customer_no")
    val customerNo: String,

    @ColumnInfo(name = "customer_name")
    val customerName: String, // 冗余存储，方便查询

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "location_id")
    val locationId: Long,

    // 新增：库位名称（打印需要）
    @ColumnInfo(name = "location_name")
    val locationName: String = "",

    @ColumnInfo(name = "operator_id")
    val operatorId: Long,

    // 新增：经手人姓名（打印需要）
    @ColumnInfo(name = "operator_name")
    val operatorName: String = "",

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double = 0.0, // 销售总款

    @ColumnInfo(name = "total_quantity")
    val totalQuantity: Int = 0, // 销售总数量

    @ColumnInfo(name = "status")
    val status: String = "1", // 1-正常，2-作废，3-已结算（电脑端使用）

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "print_time")
    val printTime: Long? = null, // 打印时间

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0, // 0-未同步，1-已同步

    @ColumnInfo(name = "customer_balance")
    val customerBalance: Double = 0.0 // 客户当前余额（从电脑端同步）
)