package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_entry")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "entry_no", index = true)
    val entryNo: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "category_id", index = true)
    val categoryId: Long,

    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "entry_date", index = true)
    val entryDate: String,

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "status")
    val status: Int = 1,

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "update_time")
    val updateTime: Long? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    @ColumnInfo(name = "sync_time")
    val syncTime: Long? = null
)
