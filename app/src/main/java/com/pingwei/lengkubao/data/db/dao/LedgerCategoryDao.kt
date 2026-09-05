package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.LedgerCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerCategoryDao {

    @Insert
    suspend fun insert(category: LedgerCategory): Long

    @Update
    suspend fun update(category: LedgerCategory)

    @Delete
    suspend fun delete(category: LedgerCategory)

    @Query("SELECT * FROM ledger_category WHERE id = :id")
    suspend fun getById(id: Long): LedgerCategory?

    @Query("SELECT * FROM ledger_category ORDER BY type, sort_order, id")
    fun getAllFlow(): Flow<List<LedgerCategory>>

    @Query("SELECT * FROM ledger_category ORDER BY type, sort_order, id")
    suspend fun getAll(): List<LedgerCategory>

    @Query("SELECT * FROM ledger_category WHERE type = :type AND enabled = 1 ORDER BY sort_order, id")
    suspend fun getEnabledByType(type: String): List<LedgerCategory>

    @Query("SELECT COUNT(*) FROM ledger_category WHERE type = :type AND name = :name")
    suspend fun countByTypeAndName(type: String, name: String): Int

    @Query("UPDATE ledger_category SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}
