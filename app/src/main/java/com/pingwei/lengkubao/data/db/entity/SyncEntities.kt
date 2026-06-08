package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_local_oplog",
    indices = [
        Index(value = ["origin_op_id"], unique = true),
        Index(value = ["entity_type", "entity_key"]),
        Index(value = ["pushed_at"]),
    ],
)
data class SyncLocalOpLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_key")
    val entityKey: String,
    @ColumnInfo(name = "op_type")
    val opType: String, // UPSERT / DELETE
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "origin_device_id")
    val originDeviceId: String,
    @ColumnInfo(name = "origin_op_id")
    val originOpId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pushed_at")
    val pushedAt: Long? = null,
    @ColumnInfo(name = "commit_seq")
    val commitSeq: Long? = null,
)

@Entity(tableName = "sync_device_cursor")
data class SyncDeviceCursor(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "last_acked_seq")
    val lastAckedSeq: Long = 0L,
    @ColumnInfo(name = "last_uploaded_local_id")
    val lastUploadedLocalId: Long = 0L,
    @ColumnInfo(name = "first_full_sync_done")
    val firstFullSyncDone: Boolean = false,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sync_applied_ops",
    primaryKeys = ["origin_device_id", "origin_op_id"],
)
data class SyncAppliedOp(
    @ColumnInfo(name = "origin_device_id")
    val originDeviceId: String,
    @ColumnInfo(name = "origin_op_id")
    val originOpId: String,
    @ColumnInfo(name = "commit_seq")
    val commitSeq: Long = 0L,
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sync_runtime_flags")
data class SyncRuntimeFlag(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,
    @ColumnInfo(name = "suppress_local_log")
    val suppressLocalLog: Boolean = false,
)
