package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_item",
    foreignKeys = [
        ForeignKey(
            entity = SaleBill::class,
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
data class SaleItem(
    @ColumnInfo(name = "bill_id")
    val billId: Long,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_no")
    val productNo: String, // 冗余存储

    @ColumnInfo(name = "product_name")
    val productName: String, // 冗余存储

    @ColumnInfo(name = "quantity")
    val quantity: Int, // 销售数量

    @ColumnInfo(name = "sale_price")
    val salePrice: Double, // 销售单价（出库时输入）

    @ColumnInfo(name = "amount")
    val amount: Double, // 金额 = quantity × salePrice

    @ColumnInfo(name = "unit")
    val unit: String = "箱",

    @ColumnInfo(name = "remark")
    val remark: String = ""
) {
    @PrimaryKey(autoGenerate = true)
    var itemId: Long = 0 // 主键保持原有定义，Room 可正常识别
}