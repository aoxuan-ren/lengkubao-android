package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbound_record",
    foreignKeys = [
        ForeignKey(
            entity = PreSaleBill::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bill_id"]),
        Index(value = ["source_record_id"], unique = true)
    ]
)
data class OutboundRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "bill_id")
    val billId: Long,

    @ColumnInfo(name = "ship_time")
    val shipTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    @ColumnInfo(name = "source_record_id")
    val sourceRecordId: String? = null,

    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String? = null
)
