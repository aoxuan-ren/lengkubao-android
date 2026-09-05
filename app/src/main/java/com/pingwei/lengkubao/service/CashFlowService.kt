package com.pingwei.lengkubao.service

import com.pingwei.lengkubao.data.db.dao.LedgerCategoryDao
import com.pingwei.lengkubao.data.db.dao.LedgerEntryDao
import com.pingwei.lengkubao.data.db.entity.LedgerCategory
import com.pingwei.lengkubao.data.db.entity.LedgerEntry
import com.pingwei.lengkubao.data.model.CashFlowFilter
import com.pingwei.lengkubao.data.model.CashFlowSummary
import com.pingwei.lengkubao.data.model.StatRow
import com.pingwei.lengkubao.utils.LedgerNoGenerator
import kotlinx.coroutines.flow.Flow

class CashFlowService(
    private val entryDao: LedgerEntryDao,
    private val categoryDao: LedgerCategoryDao
) {

    companion object {
        val BLOCKED_HANDHELD_INCOME_CATEGORIES = setOf("仓储费", "制冷费")

        fun isSelectableHandheldCategory(category: LedgerCategory): Boolean {
            return !(category.type == "INCOME" && category.name in BLOCKED_HANDHELD_INCOME_CATEGORIES)
        }
    }

    fun observeEntries(filter: CashFlowFilter): Flow<List<LedgerEntry>> {
        return entryDao.queryEntriesFlow(
            startDate = filter.startDate,
            endDate = filter.endDate,
            statusFilter = filter.statusFilter,
            typeFilter = filter.type?.takeIf { it.isNotBlank() },
            categoryId = filter.categoryId,
            keyword = filter.keyword?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun getFlowLines(filter: CashFlowFilter): List<LedgerEntry> {
        return entryDao.queryEntries(
            startDate = filter.startDate,
            endDate = filter.endDate,
            statusFilter = filter.statusFilter,
            typeFilter = filter.type?.takeIf { it.isNotBlank() },
            categoryId = filter.categoryId,
            keyword = filter.keyword?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun getSummary(filter: CashFlowFilter): CashFlowSummary {
        val amounts = entryDao.querySummaryAmounts(
            startDate = filter.startDate,
            endDate = filter.endDate,
            typeFilter = filter.type?.takeIf { it.isNotBlank() },
            categoryId = filter.categoryId,
            keyword = filter.keyword?.takeIf { it.isNotBlank() }
        )
        return CashFlowSummary(
            totalIncome = amounts.totalIncome,
            totalExpense = amounts.totalExpense
        )
    }

    suspend fun getStatsByDate(filter: CashFlowFilter): List<StatRow> {
        return entryDao.statsByDate(filter.startDate, filter.endDate)
    }

    suspend fun addEntry(entry: LedgerEntry): Long {
        val entryNo = if (entry.entryNo.isBlank()) {
            LedgerNoGenerator.generate(entryDao)
        } else {
            entry.entryNo
        }
        return entryDao.insert(entry.copy(entryNo = entryNo))
    }

    suspend fun updateEntry(entry: LedgerEntry) {
        entryDao.update(entry.copy(updateTime = System.currentTimeMillis()))
    }

    suspend fun voidEntry(id: Long) {
        entryDao.voidEntry(id, System.currentTimeMillis())
    }

    suspend fun getEntryById(id: Long): LedgerEntry? = entryDao.getById(id)

    fun observeCategories() = categoryDao.getAllFlow()

    suspend fun getEnabledCategories(type: String): List<LedgerCategory> {
        return categoryDao.getEnabledByType(type).filter { isSelectableHandheldCategory(it) }
    }

    suspend fun getAllCategories(): List<LedgerCategory> = categoryDao.getAll()

    suspend fun addCategory(type: String, name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("类别名称不能为空"))
        if (type == "INCOME" && trimmed in BLOCKED_HANDHELD_INCOME_CATEGORIES) {
            return Result.failure(IllegalArgumentException("手持端不支持添加制冷费/仓储费收入类目"))
        }
        if (categoryDao.countByTypeAndName(type, trimmed) > 0) {
            return Result.failure(IllegalArgumentException("该类别已存在"))
        }
        val maxOrder = categoryDao.getAll()
            .filter { it.type == type }
            .maxOfOrNull { it.sortOrder } ?: 0
        val id = categoryDao.insert(
            LedgerCategory(
                type = type,
                name = trimmed,
                isSystem = false,
                enabled = true,
                sortOrder = maxOrder + 1
            )
        )
        return Result.success(id)
    }

    suspend fun setCategoryEnabled(id: Long, enabled: Boolean) {
        categoryDao.updateEnabled(id, enabled)
    }
}
