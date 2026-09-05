package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.Operator
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operator: Operator): Long

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

    @Query("SELECT * FROM operator WHERE name = :name LIMIT 1")
    suspend fun getByOperatorName(name: String): Operator?

    @Query("SELECT * FROM operator WHERE enabled = 1 ORDER BY name")
    fun getEnabledOperators(): Flow<List<Operator>>

    @Query("SELECT * FROM operator WHERE id = :id")
    suspend fun getOperatorById(id: Long): Operator?

    @Query("SELECT COUNT(*) FROM operator WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("SELECT * FROM operator WHERE name LIKE '%' || :keyword || '%'")
    fun searchOperators(keyword: String): Flow<List<Operator>>

    @Query("SELECT * FROM operator ORDER BY name")
    suspend fun getAllSimple(): List<Operator>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT operator_id FROM in_stock_bill WHERE operator_id = :operatorId
            UNION ALL
            SELECT operator_id FROM sale_bill WHERE operator_id = :operatorId
            UNION ALL
            SELECT operator_id FROM presale_bill WHERE operator_id = :operatorId
            UNION ALL
            SELECT operator_id FROM packaging_bill WHERE operator_id = :operatorId
            UNION ALL
            SELECT operator_id FROM advances WHERE operator_id = :operatorId
            UNION ALL
            SELECT operator_id FROM deductions WHERE operator_id = :operatorId
        )
    """)
    suspend fun countOperatorBillRefs(operatorId: Long): Int
}
