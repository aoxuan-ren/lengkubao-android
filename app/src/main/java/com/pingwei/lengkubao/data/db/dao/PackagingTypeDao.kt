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

    @Query("SELECT * FROM packaging_type ORDER BY type_name")
    suspend fun getAll(): List<PackagingType>

    @Query("SELECT * FROM packaging_type WHERE enabled = 1 ORDER BY type_name")
    suspend fun getAllEnabled(): List<PackagingType>

    @Query("SELECT * FROM packaging_type WHERE type_name = :typeName LIMIT 1")
    suspend fun getByTypeName(typeName: String): PackagingType?

    @Query("SELECT * FROM packaging_type WHERE id = :id")
    suspend fun getById(id: Long): PackagingType?

    @Query("UPDATE packaging_type SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM packaging_type WHERE type_name NOT IN (:names)")
    suspend fun deleteExceptTypeNames(names: List<String>)

    @Query("DELETE FROM packaging_type")
    suspend fun deleteAllPackagingTypes()

    @Query("SELECT COUNT(*) FROM packaging_type")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM packaging_type WHERE type_name = :typeName")
    suspend fun countByTypeName(typeName: String): Int

    @Query("SELECT * FROM packaging_type ORDER BY type_name")
    fun getAllFlow(): Flow<List<PackagingType>>

    @Query("SELECT COUNT(*) FROM packaging_item WHERE packaging_type_id = :typeId")
    suspend fun countPackTypeBillRefs(typeId: Long): Int
}
