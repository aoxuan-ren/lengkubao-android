// data/db/dao/LocationDao.kt (完整版)
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Location
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: Location): Long
    // 在 LocationDao 中添加
    @Query("SELECT * FROM location ORDER BY location_no")
    fun getAllLocationsSync(): List<Location>
    @Query("SELECT * FROM location WHERE location_no = :locationNo LIMIT 1")
    suspend fun getByLocationNo(locationNo: String): Location?
    @Query("UPDATE location SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)
    @Update
    suspend fun update(location: Location)
    // 在 LocationDao 接口中添加
    @Query("SELECT * FROM location WHERE location_name = :locationName LIMIT 1")
    suspend fun getByLocationName(locationName: String): Location?
    @Delete
    suspend fun delete(location: Location)

    @Query("UPDATE location SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, enabled: Boolean)

    @Query("SELECT * FROM location ORDER BY location_no")
    fun getAllLocations(): Flow<List<Location>>

    @Query("SELECT * FROM location WHERE enabled = 1 ORDER BY location_no")
    fun getEnabledLocations(): Flow<List<Location>>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun getLocationById(id: Long): Location?

    @Query("SELECT COUNT(*) FROM location WHERE location_no = :locationNo")
    suspend fun countByLocationNo(locationNo: String): Int

    @Query("SELECT * FROM location WHERE location_no LIKE '%' || :keyword || '%' OR location_name LIKE '%' || :keyword || '%'")
    fun searchLocations(keyword: String): Flow<List<Location>>

    // 添加简单查询方法（非Flow版本，用于初始化等场景）
    @Query("SELECT * FROM location ORDER BY location_no")
    suspend fun getAllSimple(): List<Location>
}