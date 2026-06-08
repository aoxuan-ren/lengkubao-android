package com.pingwei.lengkubao.utils



import com.pingwei.lengkubao.data.db.entity.Customer



/**

 * 客户搜索过滤（编号、名称、拼音首字母），与桌面端 ClientSearchHelper 逻辑一致。

 * 纯字母输入时优先按拼音首字母匹配并排序。

 */

object CustomerSearchFilter {



    const val PLACEHOLDER = "输入编号/名称/首字母"



    fun displayName(customer: Customer): String {

        return "${customer.customerName ?: "未知客户"} (${customer.customerNo})"

    }



    fun filter(customers: List<Customer>, keyword: String): List<Customer> {

        if (keyword.isBlank()) return customers

        val key = keyword.trim()

        val keyLower = key.lowercase()

        val letterKeyword = isInitialSearchKeyword(keyLower)



        return customers.filter { matches(it, key) }

            .sortedWith(

                compareBy(

                    { customer -> matchPriority(customer, key, keyLower, letterKeyword) },

                    { it.customerNo }

                )

            )

    }



    fun matches(customer: Customer, keyword: String): Boolean {

        if (keyword.isBlank()) return true

        val key = keyword.trim()

        val keyLower = key.lowercase()

        val name = customer.customerName.orEmpty()

        val initials = PinyinHelper.getInitials(name)



        if (isInitialSearchKeyword(keyLower)) {

            if (initials.isNotEmpty() &&

                (initials.startsWith(keyLower) || initials.contains(keyLower))

            ) {

                return true

            }

        }



        if (customer.customerNo.contains(key, ignoreCase = true)) {

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

        customer: Customer,

        key: String,

        keyLower: String,

        letterKeyword: Boolean

    ): Int {

        val initials = PinyinHelper.getInitials(customer.customerName.orEmpty())

        return when {

            letterKeyword && initials.startsWith(keyLower) -> 0

            letterKeyword && initials.contains(keyLower) -> 1

            customer.customerNo.contains(key, ignoreCase = true) -> 2

            customer.customerName.orEmpty().contains(key, ignoreCase = true) -> 3

            initials.startsWith(keyLower) -> 4

            initials.contains(keyLower) -> 5

            else -> 6

        }

    }

}


