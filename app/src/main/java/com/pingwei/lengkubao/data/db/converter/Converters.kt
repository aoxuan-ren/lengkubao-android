// data/db/converter/Converters.kt
package com.pingwei.lengkubao.data.db.converter

import androidx.room.TypeConverter
import java.util.Date
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // Date ↔ Long
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // Boolean ↔ Int（SQLite 没有 Boolean 类型）
    @TypeConverter
    fun fromIntToBoolean(value: Int?): Boolean? {
        return value?.let { it == 1 }
    }

    @TypeConverter
    fun fromBooleanToInt(bool: Boolean?): Int? {
        return bool?.let { if (it) 1 else 0 }
    }

    // List<String> ↔ JSON String（可选，用于未来扩展）
    @TypeConverter
    fun fromStringToList(value: String?): List<String>? {
        return value?.let {
            gson.fromJson(it, object : TypeToken<List<String>>() {}.type)
        }
    }

    @TypeConverter
    fun fromListToString(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }
}