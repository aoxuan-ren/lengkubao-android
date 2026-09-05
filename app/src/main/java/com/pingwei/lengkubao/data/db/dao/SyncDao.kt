package com.pingwei.lengkubao.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pingwei.lengkubao.data.db.entity.SyncAppliedOp
import com.pingwei.lengkubao.data.db.entity.SyncDeviceCursor
import com.pingwei.lengkubao.data.db.entity.SyncLocalOpLog

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalOp(op: SyncLocalOpLog)

    @Query("SELECT * FROM sync_local_oplog WHERE pushed_at IS NULL ORDER BY id ASC LIMIT :limit")
    suspend fun getPendingLocalOps(limit: Int = 500): List<SyncLocalOpLog>

    @Query("UPDATE sync_local_oplog SET pushed_at = :pushedAt, commit_seq = :commitSeq WHERE origin_op_id = :originOpId")
    suspend fun markLocalOpPushed(originOpId: String, pushedAt: Long, commitSeq: Long?)

    @Query("DELETE FROM sync_local_oplog WHERE pushed_at IS NOT NULL AND id <= :maxId")
    suspend fun deletePushedOpsBefore(maxId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncDeviceCursor)

    @Query("SELECT * FROM sync_device_cursor WHERE device_id = :deviceId LIMIT 1")
    suspend fun getCursor(deviceId: String): SyncDeviceCursor?

    @Query(
        """
        UPDATE sync_device_cursor 
        SET last_acked_seq = :lastAckedSeq, updated_at = :updatedAt
        WHERE device_id = :deviceId
        """
    )
    suspend fun updateLastAckedSeq(deviceId: String, lastAckedSeq: Long, updatedAt: Long)

    @Query(
        """
        UPDATE sync_device_cursor 
        SET first_full_sync_done = :firstDone, updated_at = :updatedAt
        WHERE device_id = :deviceId
        """
    )
    suspend fun updateFirstFullSyncDone(deviceId: String, firstDone: Boolean, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppliedOp(op: SyncAppliedOp): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sync_applied_ops 
            WHERE origin_device_id = :originDeviceId AND origin_op_id = :originOpId
        )
        """
    )
    suspend fun isApplied(originDeviceId: String, originOpId: String): Boolean

    @Query("SELECT COUNT(*) FROM sync_local_oplog WHERE pushed_at IS NULL AND entity_type = :entityType")
    suspend fun countPendingOpsByEntityType(entityType: String): Int

    @Query("UPDATE sync_runtime_flags SET suppress_local_log = :suppress WHERE id = 1")
    suspend fun setSuppressLocalLog(suppress: Boolean)

    @Query("SELECT suppress_local_log FROM sync_runtime_flags WHERE id = 1")
    suspend fun isSuppressLocalLog(): Boolean
}
