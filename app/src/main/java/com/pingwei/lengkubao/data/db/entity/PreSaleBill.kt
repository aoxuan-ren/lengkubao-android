package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "presale_bill",
    indices = [Index(value = ["source_record_id"], unique = true)]
)
data class PreSaleBill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "bill_no", index = true)
    val billNo: String,

    @ColumnInfo(name = "buyer_no")
    val buyerNo: String,

    @ColumnInfo(name = "buyer_name")
    val buyerName: String,

    @ColumnInfo(name = "buyer_id")
    val buyerId: Long = 0,

    @ColumnInfo(name = "location_id")
    val locationId: Long,

    @ColumnInfo(name = "location_name")
    val locationName: String = "",

    @ColumnInfo(name = "operator_id")
    val operatorId: Long,

    @ColumnInfo(name = "operator_name")
    val operatorName: String = "",

    /** PRESALE=预售模式, DIRECT_OUT=出库销售模式 */
    @ColumnInfo(name = "sale_mode")
    val saleMode: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Double = 0.0,

    @ColumnInfo(name = "paid_amount")
    val paidAmount: Double = 0.0,

    /** PRESALE / COMPLETED / SHIPPED / CANCELLED */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = 0,

    @ColumnInfo(name = "source_record_id")
    val sourceRecordId: String? = null,

    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String? = null,

    /** PC 增量 commit_seq，用于 LWW */
    @ColumnInfo(name = "remote_updated_at")
    val remoteUpdatedAt: Long = 0L
)

object PreSaleMode {
    const val PRESALE = "PRESALE"
    const val DIRECT_OUT = "DIRECT_OUT"
}

object PreSaleStatus {
    const val PRESALE = "PRESALE"
    const val COMPLETED = "COMPLETED"
    const val SHIPPED = "SHIPPED"
    const val CANCELLED = "CANCELLED"
}
