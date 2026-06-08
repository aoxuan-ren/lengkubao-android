// data/db/entity/InStockItem.kt
package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "in_stock_item",
    foreignKeys = [
        ForeignKey(
            entity = InStockBill::class,
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
data class InStockItem(
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "bill_id")
    val billId: Long, // 关联入库单ID

    @ColumnInfo(name = "product_id")
    val productId: Long, // 商品ID

    @ColumnInfo(name = "product_no")
    val productNo: String, // 商品编号（冗余存储，方便查询）

    @ColumnInfo(name = "product_name")
    val productName: String, // 商品名称

    @ColumnInfo(name = "quantity")
    val quantity: Int, // 数量

    @ColumnInfo(name = "unit_price")
    val unitPrice: Double = 0.0, // 单价（入库单价）

    @ColumnInfo(name = "amount")
    val amount: Double = 0.0, // 金额

    @ColumnInfo(name = "unit")
    val unit: String = "箱", // 单位

    @ColumnInfo(name = "remark")
    val remark: String = ""
) {
    @androidx.room.PrimaryKey(autoGenerate = true)
    var itemId: Long = 0
}