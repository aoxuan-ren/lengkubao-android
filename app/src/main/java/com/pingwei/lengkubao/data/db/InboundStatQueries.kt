// data/db/InboundStatQueries.kt
package com.pingwei.lengkubao.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery

object InboundStatQueries {

    /**
     * 构建按库位和商品分组统计的查询
     */
    fun buildLocationStatsQuery(
        customerNo: String?,
        startTime: Long,
        endTime: Long
    ): SimpleSQLiteQuery {
        val sql = """
        SELECT 
            b.location_id as locationId,
            l.location_no as locationNo,
            l.location_name as locationName,
            i.product_id as productId,
            p.productNo as productNo,
            p.productName as productName,
            SUM(i.quantity) as quantity,
            COUNT(DISTINCT b.id) as orderCount,
            SUM(i.amount) as totalAmount
        FROM in_stock_item i
        INNER JOIN in_stock_bill b ON i.bill_id = b.id
        INNER JOIN location l ON b.location_id = l.id
        INNER JOIN product p ON i.product_id = p.id
        WHERE (${if (customerNo == null) "?" else "b.customer_no = ?"})
            AND b.create_time BETWEEN ? AND ?
            AND b.status = 'COMPLETED'  -- 修改这里！
        GROUP BY b.location_id, l.location_no, l.location_name, i.product_id, p.productNo, p.productName
        ORDER BY l.location_no, p.productNo
    """.trimIndent()

        val args = mutableListOf<Any>()
        if (customerNo == null) {
            args.add(1)
        } else {
            args.add(customerNo)
        }
        args.add(startTime)
        args.add(endTime)

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /**
     * 构建入库总计统计查询
     */
    /**
     * 构建入库总计统计查询
     */
    fun buildTotalStatsQuery(
        customerNo: String?,
        startTime: Long,
        endTime: Long
    ): SimpleSQLiteQuery {
        val sql = """
        SELECT 
            COALESCE(SUM(i.quantity), 0) as totalQuantity,
            COALESCE(SUM(i.amount), 0.0) as totalAmount,
            COUNT(DISTINCT i.product_id) as productCount,
            COUNT(DISTINCT b.id) as orderCount
        FROM in_stock_item i
        INNER JOIN in_stock_bill b ON i.bill_id = b.id
        WHERE (${if (customerNo == null) "?" else "b.customer_no = ?"})
            AND b.create_time BETWEEN ? AND ?
            AND b.status = 'COMPLETED'  -- 改为 COMPLETED
    """.trimIndent()

        val args = mutableListOf<Any>()
        if (customerNo == null) {
            args.add(1)
        } else {
            args.add(customerNo)
        }
        args.add(startTime)
        args.add(endTime)

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
    /**
     * 构建每日入库统计查询
     */
    fun buildDailyStatsQuery(
        customerNo: String?,
        startTime: Long,
        endTime: Long
    ): SimpleSQLiteQuery {
        val sql = """
            SELECT 
                strftime('%Y-%m-%d', datetime(b.create_time / 1000, 'unixepoch', 'localtime')) as date,
                SUM(i.quantity) as dailyQuantity,
                SUM(i.amount) as dailyAmount,
                COUNT(DISTINCT b.id) as dailyOrderCount
            FROM in_stock_item i
            INNER JOIN in_stock_bill b ON i.bill_id = b.id
            WHERE (${if (customerNo == null) "?" else "b.customer_no = ?"})
                AND b.create_time BETWEEN ? AND ?
                AND b.status = '1'
            GROUP BY date
            ORDER BY date
        """.trimIndent()

        val args = mutableListOf<Any>()
        if (customerNo == null) {
            args.add(1)
        } else {
            args.add(customerNo)
        }
        args.add(startTime)
        args.add(endTime)

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    // 可以继续添加其他查询构建方法...
}