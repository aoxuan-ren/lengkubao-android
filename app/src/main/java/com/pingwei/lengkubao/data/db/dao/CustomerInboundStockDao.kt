package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.CustomerInboundStock

@Dao
interface CustomerInboundStockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CustomerInboundStock)

    @Query("SELECT COUNT(*) FROM customer_inbound_stock")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM customer_inbound_stock
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
        LIMIT 1
        """
    )
    suspend fun getRow(customerNo: String, locationId: Long, productId: Long): CustomerInboundStock?

    @Query(
        """
        SELECT COALESCE(
            (SELECT inbound_quantity FROM customer_inbound_stock
             WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId), 0
        ) - COALESCE(
            (SELECT reserved_quantity FROM customer_inbound_stock
             WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId), 0
        ) - COALESCE(
            (SELECT SUM(si.quantity) FROM sale_item si
             INNER JOIN sale_bill sb ON si.bill_id = sb.id
             WHERE sb.customer_no = :customerNo AND sb.location_id = :locationId
               AND si.product_id = :productId AND sb.status = 'COMPLETED'), 0
        )
        """
    )
    suspend fun getAvailableQuantity(customerNo: String, locationId: Long, productId: Long): Int?

    @Query(
        """
        SELECT COALESCE(SUM(si.quantity), 0) FROM sale_item si
        INNER JOIN sale_bill sb ON si.bill_id = sb.id
        WHERE sb.customer_no = :customerNo AND sb.location_id = :locationId
          AND si.product_id = :productId AND sb.status = 'COMPLETED'
        """
    )
    suspend fun getSoldQuantity(customerNo: String, locationId: Long, productId: Long): Int

    @Query(
        """
        UPDATE customer_inbound_stock
        SET inbound_quantity = inbound_quantity + :quantity, last_updated = :timestamp
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
        """
    )
    suspend fun addInboundQuantity(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE customer_inbound_stock
        SET inbound_quantity = :inboundQuantity, last_updated = :timestamp
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
        """
    )
    suspend fun setInboundQuantity(
        customerNo: String,
        locationId: Long,
        productId: Long,
        inboundQuantity: Int,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE customer_inbound_stock
        SET inbound_quantity = inbound_quantity - :quantity, last_updated = :timestamp
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
          AND inbound_quantity >= :quantity
        """
    )
    suspend fun subtractInboundQuantity(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE customer_inbound_stock
        SET reserved_quantity = reserved_quantity + :quantity, last_updated = :timestamp
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
        """
    )
    suspend fun addReservedQuantity(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int,
        timestamp: Long
    ): Int

    @Query(
        """
        UPDATE customer_inbound_stock
        SET reserved_quantity = reserved_quantity - :quantity, last_updated = :timestamp
        WHERE customer_no = :customerNo AND location_id = :locationId AND product_id = :productId
          AND reserved_quantity >= :quantity
        """
    )
    suspend fun releaseReservedQuantity(
        customerNo: String,
        locationId: Long,
        productId: Long,
        quantity: Int,
        timestamp: Long
    ): Int
}
