package com.pingwei.lengkubao.utils

import java.nio.charset.Charset

/**
 * 中文拼音首字母工具（GB2312 区间查表，离线可用）。
 */
object PinyinHelper {

    private val areaBounds = intArrayOf(
        45217, 45253, 45761, 46318, 46826, 47010, 47297, 47614,
        48119, 49062, 49324, 49896, 50371, 50614, 50622, 50906,
        51387, 51446, 52218, 52698, 52980, 53689, 54481, 55290
    )

    private val firstLetters = charArrayOf(
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'w', 'x', 'y', 'z'
    )

    private val gb2312Charset: Charset = Charset.forName("GB2312")

    /**
     * 获取文本的拼音首字母串（小写），英文数字保留。
     */
    fun getInitials(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
                c in 'A'..'Z' -> sb.append(c.lowercaseChar())
                c.code in 0x4E00..0x9FA5 -> {
                    val letter = getFirstLetter(c)
                    if (letter != '#') sb.append(letter)
                }
            }
        }
        return sb.toString()
    }

    private fun getFirstLetter(chinese: Char): Char {
        try {
            val bytes = chinese.toString().toByteArray(gb2312Charset)
            if (bytes.size < 2) return '#'

            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val code = b0 * 256 + b1

            for (i in firstLetters.indices) {
                if (code >= areaBounds[i] && code < areaBounds[i + 1]) {
                    return firstLetters[i]
                }
            }
        } catch (_: Exception) {
            // 编码失败时跳过
        }
        return '#'
    }
}
