package com.pingwei.lengkubao.utils

/**
 * 配置项列表搜索（仅名称 + 拼音首字母），不含系统自动编号。
 */
object ConfigNameSearchFilter {

    const val PLACEHOLDER = "输入名称/首字母"

    fun <T> filter(items: List<T>, keyword: String, nameSelector: (T) -> String): List<T> {
        if (keyword.isBlank()) return items

        val key = keyword.trim()
        val keyLower = key.lowercase()
        val letterKeyword = isInitialSearchKeyword(keyLower)

        return items.filter { matches(nameSelector(it), key, keyLower, letterKeyword) }
            .sortedWith(compareBy { matchPriority(nameSelector(it), key, keyLower, letterKeyword) })
    }

    private fun matches(name: String, key: String, keyLower: String, letterKeyword: Boolean): Boolean {
        val initials = PinyinHelper.getInitials(name)

        if (letterKeyword) {
            if (initials.isNotEmpty() &&
                (initials.startsWith(keyLower) || initials.contains(keyLower))
            ) {
                return true
            }
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

    private fun matchPriority(name: String, key: String, keyLower: String, letterKeyword: Boolean): Int {
        val initials = PinyinHelper.getInitials(name)
        return when {
            letterKeyword && initials.startsWith(keyLower) -> 0
            letterKeyword && initials.contains(keyLower) -> 1
            name.contains(key, ignoreCase = true) -> 2
            initials.startsWith(keyLower) -> 3
            initials.contains(keyLower) -> 4
            else -> 5
        }
    }
}
