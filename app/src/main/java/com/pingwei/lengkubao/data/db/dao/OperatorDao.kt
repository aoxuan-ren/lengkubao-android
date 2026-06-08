// data/db/dao/OperatorDao.kt (完整版)
package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Operator
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operator: Operator): Long
    @Query("SELECT * FROM operator WHERE operatorNo = :operatorNo LIMIT 1")
    suspend fun getByOperatorNo(operatorNo: String): Operator?
    @Query("UPDATE operator SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)
    @Update
    suspend fun update(operator: Operator)

    @Delete
    suspend fun delete(operator: Operator)

    @Query("UPDATE operator SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, enabled: Boolean)

    @Query("SELECT * FROM operator ORDER BY name")
    fun getAllOperators(): Flow<List<Operator>>
    // 在 OperatorDao 接口中添加
    @Query("SELECT * FROM operator WHERE name = :name LIMIT 1")
    suspend fun getByOperatorName(name: String): Operator?
    @Query("SELECT * FROM operator WHERE enabled = 1 ORDER BY name")
    fun getEnabledOperators(): Flow<List<Operator>>

    @Query("SELECT * FROM operator WHERE id = :id")
    suspend fun getOperatorById(id: Long): Operator?

    @Query("SELECT COUNT(*) FROM operator WHERE operatorNo = :operatorNo")
    suspend fun countByOperatorNo(operatorNo: String): Int

    @Query("SELECT * FROM operator WHERE name LIKE '%' || :keyword || '%' OR operatorNo LIKE '%' || :keyword || '%'")
    fun searchOperators(keyword: String): Flow<List<Operator>>

    // 添加这个方法用于DatabaseInitializer
    @Query("SELECT * FROM operator ORDER BY name")
    suspend fun getAllSimple(): List<Operator>
}