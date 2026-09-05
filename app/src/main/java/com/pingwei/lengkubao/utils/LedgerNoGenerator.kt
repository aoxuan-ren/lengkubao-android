package com.pingwei.lengkubao.utils

import com.pingwei.lengkubao.data.db.dao.LedgerEntryDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LedgerNoGenerator {

    suspend fun generate(dao: LedgerEntryDao): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
        val prefix = "LS-$datePart-"
        val count = dao.countByDatePrefix(prefix)
        val seq = (count + 1).toString().padStart(3, '0')
        return "$prefix$seq"
    }
}
