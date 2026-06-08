// utils/CustomerCodeGenerator.kt
package com.pingwei.lengkubao.utils

import android.content.Context
import com.pingwei.lengkubao.LengKuBaoApplication
import com.pingwei.lengkubao.data.db.entity.CustomerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CustomerCodeGenerator {
    private const val SELLER_PREFIX = "KH"
    private const val BUYER_PREFIX = "MJ"
    private const val DIGITS = 3

    suspend fun generateNextCustomerNo(context: Context): String {
        return generateNextCustomerNo(context, CustomerType.SELLER)
    }

    suspend fun generateNextCustomerNo(context: Context, customerType: String): String {
        val prefix = if (customerType == CustomerType.BUYER) BUYER_PREFIX else SELLER_PREFIX
        return withContext(Dispatchers.IO) {
            val database = LengKuBaoApplication.getDatabase()
            val customerDao = database.customerDao()
            try {
                val maxNo = customerDao.getMaxCustomerNoByPrefix(prefix)
                if (maxNo.isNullOrBlank() || !maxNo.startsWith(prefix)) {
                    return@withContext "${prefix}001"
                }
                try {
                    val numberStr = maxNo.substring(prefix.length)
                    var nextNumber = numberStr.toInt() + 1
                    if (nextNumber > 999) nextNumber = 1
                    val formattedNumber = String.format("%0${DIGITS}d", nextNumber)
                    return@withContext "$prefix$formattedNumber"
                } catch (e: Exception) {
                    return@withContext "${prefix}001"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext "${prefix}001"
            }
        }
    }

    fun isValidCustomerNo(customerNo: String): Boolean {
        return isValidCustomerNo(customerNo, CustomerType.SELLER) ||
            isValidCustomerNo(customerNo, CustomerType.BUYER)
    }

    fun isValidCustomerNo(customerNo: String, customerType: String): Boolean {
        val prefix = if (customerType == CustomerType.BUYER) BUYER_PREFIX else SELLER_PREFIX
        if (!customerNo.startsWith(prefix)) return false
        if (customerNo.length != prefix.length + DIGITS) return false
        val numberPart = customerNo.substring(prefix.length)
        return try {
            numberPart.toInt() in 1..999
        } catch (e: NumberFormatException) {
            false
        }
    }

    fun generateNextCustomerNoFast(lastNumber: Int? = null): String {
        return generateNextCustomerNoFast(CustomerType.SELLER, lastNumber)
    }

    fun generateNextCustomerNoFast(customerType: String, lastNumber: Int? = null): String {
        val prefix = if (customerType == CustomerType.BUYER) BUYER_PREFIX else SELLER_PREFIX
        val nextNumber = (lastNumber ?: 0) + 1
        val formattedNumber = String.format("%0${DIGITS}d", nextNumber)
        return "$prefix$formattedNumber"
    }
}
