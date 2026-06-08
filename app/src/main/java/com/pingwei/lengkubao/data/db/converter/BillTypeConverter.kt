// data/db/converter/BillTypeConverter.kt
package com.pingwei.lengkubao.data.db.converter

import androidx.room.TypeConverter
import com.pingwei.lengkubao.data.db.entity.BillType

class BillTypeConverter {
    @TypeConverter
    fun fromBillType(type: BillType?): String? = type?.name

    @TypeConverter
    fun toBillType(value: String?): BillType? =
        value?.let {
            try {
                BillType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
}