package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Location
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: Location): Long

    @Query("SELECT * FROM location ORDER BY location_name")
    fun getAllLocationsSync(): List<Location>

    @Query("UPDATE location SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Update
    suspend fun update(location: Location)

    @Query("SELECT * FROM location WHERE location_name = :locationName LIMIT 1")
    suspend fun getByLocationName(locationName: String): Location?

    @Delete
    suspend fun delete(location: Location)

    @Query("UPDATE location SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, enabled: Boolean)

    @Query("SELECT * FROM location ORDER BY location_name")
    fun getAllLocations(): Flow<List<Location>>

    @Query("SELECT * FROM location WHERE enabled = 1 ORDER BY location_name")
    fun getEnabledLocations(): Flow<List<Location>>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun getLocationById(id: Long): Location?

    @Query("SELECT COUNT(*) FROM location WHERE location_name = :locationName")
    suspend fun countByLocationName(locationName: String): Int

    @Query("SELECT * FROM location WHERE location_name LIKE '%' || :keyword || '%'")
    fun searchLocations(keyword: String): Flow<List<Location>>

    @Query("SELECT * FROM location ORDER BY location_name")
    suspend fun getAllSimple(): List<Location>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT location_id FROM in_stock_bill WHERE location_id = :locationId
            UNION ALL
            SELECT location_id FROM sale_bill WHERE location_id = :locationId
            UNION ALL
            SELECT location_id FROM presale_bill WHERE location_id = :locationId
        )
    """)
    suspend fun countLocationBillRefs(locationId: Long): Int
}
