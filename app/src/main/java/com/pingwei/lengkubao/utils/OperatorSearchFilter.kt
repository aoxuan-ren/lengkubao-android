package com.pingwei.lengkubao.utils

import com.pingwei.lengkubao.data.db.entity.Operator

/**
 * 经手人搜索过滤（编号、姓名、拼音首字母），与客户搜索逻辑一致。
 * 纯字母输入时优先按拼音首字母匹配并排序。
 */
object OperatorSearchFilter {

    const val PLACEHOLDER = "输入编号/名称/首字母"

    fun displayName(operator: Operator): String = operator.name

    fun filter(operators: List<Operator>, keyword: String): List<Operator> {
        if (keyword.isBlank()) return operators
        val key = keyword.trim()
        val keyLower = key.lowercase()
        val letterKeyword = isInitialSearchKeyword(keyLower)

        return operators.filter { matches(it, key) }
            .sortedWith(
                compareBy(
                    { operator -> matchPriority(operator, key, keyLower, letterKeyword) },
                    { it.operatorNo }
                )
            )
    }

    fun matches(operator: Operator, keyword: String): Boolean {
        if (keyword.isBlank()) return true
        val key = keyword.trim()
        val keyLower = key.lowercase()
        val name = operator.name
        val initials = PinyinHelper.getInitials(name)

        if (isInitialSearchKeyword(keyLower)) {
            if (initials.isNotEmpty() &&
                (initials.startsWith(keyLower) || initials.contains(keyLower))
            ) {
                return true
            }
        }

        if (operator.operatorNo.contains(key, ignoreCase = true)) {
            return true
        }

        if (name.contains(key, ignoreCase = true)) {
            return true
        }

        if (initials.isNotEmpty()) {
            if (initials.startsWith(keyLower) || initials.contains(keyLower)) {
                return true
            }
        }

        return false
    }

    private fun isInitialSearchKeyword(keyword: String): Boolean {
        return keyword.isNotEmpty() && keyword.all { it.isLetter() && it.code < 128 }
    }

    private fun matchPriority(
        operator: Operator,
        key: String,
        keyLower: String,
        letterKeyword: Boolean
    ): Int {
        val initials = PinyinHelper.getInitials(operator.name)
        return when {
            letterKeyword && initials.startsWith(keyLower) -> 0
            letterKeyword && initials.contains(keyLower) -> 1
            operator.operatorNo.contains(key, ignoreCase = true) -> 2
            operator.name.contains(key, ignoreCase = true) -> 3
            initials.startsWith(keyLower) -> 4
            initials.contains(keyLower) -> 5
            else -> 6
        }
    }
}
