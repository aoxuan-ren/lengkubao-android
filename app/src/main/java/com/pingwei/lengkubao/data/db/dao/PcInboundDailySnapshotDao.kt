package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.PcInboundDailySnapshot

@Dao
interface PcInboundDailySnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PcInboundDailySnapshot>)

    @Query("DELETE FROM pc_inbound_daily_snapshot")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM pc_inbound_daily_snapshot
        WHERE date BETWEEN :startDate AND :endDate
          AND (:customerNo IS NULL OR customer_no = :customerNo)
        """
    )
    suspend fun queryRange(
        startDate: String,
        endDate: String,
        customerNo: String?
    ): List<PcInboundDailySnapshot>
}

