package com.pingwei.lengkubao.data.db.converter

import androidx.room.TypeConverter
import com.pingwei.lengkubao.data.db.entity.BillType
import com.pingwei.lengkubao.data.db.entity.ChangeType

/**
 * Room枚举类型转换器：实现枚举与String的相互转换
 */
class StockEnumConverter {

    // ========== ChangeType 转换 ==========
    @TypeConverter
    fun convertChangeTypeToString(changeType: ChangeType?): String? {
        // 枚举转String：返回枚举名称
        return changeType?.name
    }

    @TypeConverter
    fun convertStringToChangeType(typeStr: String?): ChangeType? {
        // String转枚举：通过枚举名称匹配
        if (typeStr.isNullOrEmpty()) return null
        return ChangeType.values().firstOrNull { it.name == typeStr }
    }

    // ========== BillType 转换 ==========
    @TypeConverter
    fun convertBillTypeToString(billType: BillType?): String? {
        // 枚举转String：返回枚举名称
        return billType?.name
    }

    @TypeConverter
    fun convertStringToBillType(typeStr: String?): BillType? {
        // String转枚举：通过枚举名称匹配
        if (typeStr.isNullOrEmpty()) return null
        return BillType.values().firstOrNull { it.name == typeStr }
    }
}