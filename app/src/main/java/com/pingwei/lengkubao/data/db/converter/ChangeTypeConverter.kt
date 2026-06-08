// data/db/converter/ChangeTypeConverter.kt
package com.pingwei.lengkubao.data.db.converter

import androidx.room.TypeConverter
import com.pingwei.lengkubao.data.db.entity.ChangeType

class ChangeTypeConverter {
    @TypeConverter
    fun fromChangeType(type: ChangeType): String = type.name

    @TypeConverter
    fun toChangeType(value: String): ChangeType =
        try {
            ChangeType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // 兼容旧数据或未知类型
            ChangeType.ADJUST
        }
}