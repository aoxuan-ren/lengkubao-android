package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "packaging_item",
    foreignKeys = [
        ForeignKey(
            entity = PackagingBill::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bill_id"])
    ]
)
data class PackagingItem(
    @ColumnInfo(name = "bill_id")
    val billId: Long,

    // 新增：明细级别的包装类型标记（可选，如果需要在明细级别区分）
    @ColumnInfo(name = "packaging_type_flag")
    val packagingTypeFlag: String = "TAKE", // TAKE-取包装, RETURN-退包装

    @ColumnInfo(name = "packaging_type")
    val packagingType: String,

    @ColumnInfo(name = "packaging_type_id")
    val packagingTypeId: Long = 0,

    @ColumnInfo(name = "packaging_type_no")
    val packagingTypeNo: String = "",

    @ColumnInfo(name = "packaging_type_name")
    val packagingTypeName: String = "",

    @ColumnInfo(name = "unit")
    val unit: String = "",

    @ColumnInfo(name = "quantity")
    val quantity: Int,

    @ColumnInfo(name = "unit_price")
    val unitPrice: Double,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "subtotal")
    val subtotal: Double = 0.0,

    @ColumnInfo(name = "remark")
    val remark: String = "",

    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false,

    @ColumnInfo(name = "is_printed")
    val isPrinted: Boolean = false,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false

) {
    @PrimaryKey(autoGenerate = true)
    var itemId: Long = 0
}