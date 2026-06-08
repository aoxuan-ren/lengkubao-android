package com.pingwei.lengkubao.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operator")
data class Operator(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "operatorNo")
    val operatorNo: String, // 经手人编号

    @ColumnInfo(name = "name")
    val name: String, // 经手人姓名

    @ColumnInfo(name = "phone")
    val phone: String = "", // 联系电话

    @ColumnInfo(name = "role")
    val role: String = "操作员", // 角色

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true, // 是否启用

    @ColumnInfo(name = "remark")
    val remark: String = "", // 备注

    @ColumnInfo(name = "create_time")
    val createTime: Long = System.currentTimeMillis(),

    /** 同步状态：0-未同步，1-已同步，默认为0 */
    @ColumnInfo(name = "sync_status", defaultValue = "0")
    val syncStatus: Int = 0
)