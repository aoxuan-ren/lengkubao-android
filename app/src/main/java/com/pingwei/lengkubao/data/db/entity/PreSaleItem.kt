package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "presale_item",
    foreignKeys = [
        ForeignKey(
            entity = PreSaleBill::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["bill_id"]),
        Index(value = ["product_id"])
    ]
)
data class PreSaleItem(
    @ColumnInfo(name = "bill_id")
    val billId: Long = 0,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_no")
    val productNo: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "quantity")
    val quantity: Int,

    @ColumnInfo(name = "shipped_quantity")
    val shippedQuantity: Int = 0,

    @ColumnInfo(name = "sale_price")
    val salePrice: Double,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "unit")
    val unit: String = "箱",

    @ColumnInfo(name = "remark")
    val remark: String = ""
) {
    @PrimaryKey(autoGenerate = true)
    var itemId: Long = 0
}
