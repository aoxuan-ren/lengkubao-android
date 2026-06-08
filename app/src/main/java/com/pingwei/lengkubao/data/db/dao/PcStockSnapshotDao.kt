package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.PcStockSnapshot

@Dao
interface PcStockSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PcStockSnapshot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PcStockSnapshot)

    @Query("DELETE FROM pc_stock_snapshot")
    suspend fun deleteAll()

    @Query("SELECT * FROM pc_stock_snapshot WHERE location_id = :locationId ORDER BY product_id")
    suspend fun getByLocation(locationId: Long): List<PcStockSnapshot>
}

