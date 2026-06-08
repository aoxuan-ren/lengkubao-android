// data/db/dao/PackagingTypeDao.kt
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.PackagingType
import kotlinx.coroutines.flow.Flow

@Dao
interface PackagingTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packagingType: PackagingType)

    @Update
    suspend fun update(packagingType: PackagingType)

    @Delete
    suspend fun delete(packagingType: PackagingType)

    @Query("SELECT * FROM packaging_type ORDER BY type_no")
    suspend fun getAll(): List<PackagingType>

    @Query("SELECT * FROM packaging_type WHERE enabled = 1 ORDER BY type_no")
    suspend fun getAllEnabled(): List<PackagingType>

    @Query("SELECT * FROM packaging_type WHERE type_no = :typeNo LIMIT 1")
    suspend fun getByNo(typeNo: String): PackagingType?

    @Query("SELECT * FROM packaging_type WHERE id = :id")
    suspend fun getById(id: Long): PackagingType?

    @Query("UPDATE packaging_type SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM packaging_type")
    suspend fun getCount(): Int

    // Flow版本
    @Query("SELECT * FROM packaging_type ORDER BY type_no")
    fun getAllFlow(): Flow<List<PackagingType>>
}