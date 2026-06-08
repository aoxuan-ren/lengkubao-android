package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_record",
    foreignKeys = [
        ForeignKey(
            entity = PreSaleBill::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bill_id"])]
)
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "bill_id")
    val billId: Long,

    @ColumnInfo(name = "amount")
    val amount: Double,

    /** 现金 / 转账 / 其他 */
    @ColumnInfo(name = "pay_method")
    val payMethod: String,

    @ColumnInfo(name = "pay_time")
    val payTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0
)

object PayMethod {
    const val CASH = "现金"
    const val TRANSFER = "转账"
    const val OTHER = "其他"
}
