package com.pingwei.lengkubao.data.db.dao

import androidx.room.*
import com.pingwei.lengkubao.data.db.entity.LedgerEntry
import com.pingwei.lengkubao.data.model.StatRow
import com.pingwei.lengkubao.data.model.SummaryAmounts
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerEntryDao {

    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Update
    suspend fun update(entry: LedgerEntry)

    @Query("SELECT * FROM ledger_entry WHERE id = :id")
    suspend fun getById(id: Long): LedgerEntry?

    @Query("SELECT COUNT(*) FROM ledger_entry WHERE entry_no LIKE :datePrefix || '%'")
    suspend fun countByDatePrefix(datePrefix: String): Int

    @Query("UPDATE ledger_entry SET status = 0, update_time = :updateTime WHERE id = :id")
    suspend fun voidEntry(id: Long, updateTime: Long)

    @Query(
        """
        SELECT * FROM ledger_entry
        WHERE entry_date >= :startDate AND entry_date <= :endDate
        AND (:statusFilter = -1 OR status = :statusFilter)
        AND (:typeFilter IS NULL OR type = :typeFilter)
        AND (:categoryId = 0 OR category_id = :categoryId)
        AND (
            :keyword IS NULL OR :keyword = ''
            OR entry_no LIKE '%' || :keyword || '%'
            OR remark LIKE '%' || :keyword || '%'
            OR category_name LIKE '%' || :keyword || '%'
        )
        ORDER BY entry_date DESC, create_time DESC, id DESC
        """
    )
    suspend fun queryEntries(
        startDate: String,
        endDate: String,
        statusFilter: Int,
        typeFilter: String?,
        categoryId: Long,
        keyword: String?
    ): List<LedgerEntry>

    @Query(
        """
        SELECT * FROM ledger_entry
        WHERE entry_date >= :startDate AND entry_date <= :endDate
        AND (:statusFilter = -1 OR status = :statusFilter)
        AND (:typeFilter IS NULL OR type = :typeFilter)
        AND (:categoryId = 0 OR category_id = :categoryId)
        AND (
            :keyword IS NULL OR :keyword = ''
            OR entry_no LIKE '%' || :keyword || '%'
            OR remark LIKE '%' || :keyword || '%'
            OR category_name LIKE '%' || :keyword || '%'
        )
        ORDER BY entry_date DESC, create_time DESC, id DESC
        """
    )
    fun queryEntriesFlow(
        startDate: String,
        endDate: String,
        statusFilter: Int,
        typeFilter: String?,
        categoryId: Long,
        keyword: String?
    ): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense
        FROM ledger_entry
        WHERE status = 1
        AND entry_date >= :startDate AND entry_date <= :endDate
        AND (:typeFilter IS NULL OR type = :typeFilter)
        AND (:categoryId = 0 OR category_id = :categoryId)
        AND (
            :keyword IS NULL OR :keyword = ''
            OR entry_no LIKE '%' || :keyword || '%'
            OR remark LIKE '%' || :keyword || '%'
            OR category_name LIKE '%' || :keyword || '%'
        )
        """
    )
    suspend fun querySummaryAmounts(
        startDate: String,
        endDate: String,
        typeFilter: String?,
        categoryId: Long,
        keyword: String?
    ): SummaryAmounts

    @Query(
        """
        SELECT entry_date AS groupKey,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense
        FROM ledger_entry
        WHERE status = 1
        AND entry_date >= :startDate AND entry_date <= :endDate
        GROUP BY entry_date
        ORDER BY entry_date DESC
        """
    )
    suspend fun statsByDate(startDate: String, endDate: String): List<StatRow>

    @Query("UPDATE ledger_entry SET sync_status = :status, sync_time = :syncTime WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int, syncTime: Long?)

    @Query("SELECT * FROM ledger_entry WHERE sync_status = 0 ORDER BY create_time DESC")
    suspend fun getUnsyncedEntries(): List<LedgerEntry>

    @Query("UPDATE ledger_entry SET sync_status = 0, sync_time = NULL WHERE create_time BETWEEN :startTime AND :endTime")
    suspend fun resetSyncStatusByCreateTimeRange(startTime: Long, endTime: Long): Int
}
