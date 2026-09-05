package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbound_record_item",
    foreignKeys = [
        ForeignKey(
            entity = OutboundRecord::class,
            parentColumns = ["id"],
            childColumns = ["outbound_record_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["outbound_record_id"])]
)
data class OutboundRecordItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "outbound_record_id")
    val outboundRecordId: Long = 0,

    @ColumnInfo(name = "bill_item_id")
    val billItemId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_no")
    val productNo: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Int,

    @ColumnInfo(name = "unit")
    val unit: String = "箱"
)
