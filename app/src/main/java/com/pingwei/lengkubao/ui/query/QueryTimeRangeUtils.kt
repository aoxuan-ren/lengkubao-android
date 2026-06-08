package com.pingwei.lengkubao.ui.query

import java.util.Calendar

/**
 * 单据查询时间范围工具（今天/7天/本月/全部）。
 */
object QueryTimeRangeUtils {

    val PRESET_LABELS = listOf("今天", "7天", "本月", "全部")

    fun isValidLabel(label: String): Boolean {
        return label in PRESET_LABELS || label == "自定义"
    }

    fun getStartTime(label: String): Long {
        val calendar = Calendar.getInstance()
        when (label) {
            "今天" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "7天" -> {
                calendar.add(Calendar.DAY_OF_MONTH, -7)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "本月" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            "全部", "自定义" -> return 0L
            else -> {
                calendar.add(Calendar.DAY_OF_MONTH, -7)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }
        return calendar.timeInMillis
    }

    fun getEndTime(label: String): Long {
        return if (label == "全部") Long.MAX_VALUE else System.currentTimeMillis()
    }
}
