package com.pingwei.lengkubao.ui.common

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit // 导入KTX扩展函数（关键）

/**
 * 配置管理器，用于管理应用的默认值配置（如默认库位、默认经手人）、企业信息及打印配置。
 * 使用 SharedPreferences 进行持久化存储，采用 KTX 扩展函数优化存储操作。
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "lengkubao_config",
        Context.MODE_PRIVATE
    )

    companion object {
        // 企业信息配置键
        const val KEY_COMPANY_NAME = "company_name"
        const val KEY_COMPANY_ADDRESS = "company_address"
        const val KEY_COMPANY_PHONE = "company_phone"

        // 打印配置键
        const val KEY_PRINTER_AUTO_CUT = "printer_auto_cut"
        const val KEY_PRINTER_PAPER_WIDTH = "printer_paper_width"
        const val KEY_PRINTER_FONT_SIZE = "printer_font_size"

        // 默认值
        private const val DEFAULT_COMPANY_NAME = "平伟冷藏库（天马果业）"
        private const val DEFAULT_COMPANY_ADDRESS = "地址：藁城区贾市庄镇马邱村"
        private const val DEFAULT_COMPANY_PHONE = "电话：13315168281"

        // 纸张宽度常量
        const val PAPER_WIDTH_58 = 58
        const val PAPER_WIDTH_80 = 80

        // 入库统计默认开始日期（结束日期每次进入页面默认为今天，不持久化）
        private const val KEY_INSTOCK_STATS_START_DATE = "instock_stats_start_date"

        // 单据查询默认时间段
        private const val KEY_QUERY_INSTOCK_TIME_RANGE = "query_instock_time_range"
        private const val KEY_QUERY_SALEOUT_TIME_RANGE = "query_saleout_time_range"
        private const val KEY_QUERY_PACKAGING_TIME_RANGE = "query_packaging_time_range"
    }

    enum class QueryTimeRangeType {
        IN_STOCK,
        SALE_OUT,
        PACKAGING
    }

    // ==================== 原有核心功能（KTX 优化） ====================
    /**
     * 默认库位ID
     */
    var defaultLocationId: Long
        get() = prefs.getLong("default_location_id", -1L) // -1 表示未设置
        set(value) = prefs.edit { // KTX 扩展函数写法
            putLong("default_location_id", value)
        }

    /**
     * 默认经手人ID
     */
    var defaultHandlerId: Long
        get() = prefs.getLong("default_handler_id", -1L) // -1 表示未设置
        set(value) = prefs.edit { // KTX 扩展函数写法
            putLong("default_handler_id", value)
        }

    /**
     * 清除所有默认值配置
     */
    fun clearAllDefaults() {
        prefs.edit { clear() } // KTX 扩展函数写法
    }

    // ==================== 日期范围默认值 ====================

    fun getInStockStatsStartDate(defaultValue: Long): Long {
        return if (prefs.contains(KEY_INSTOCK_STATS_START_DATE)) {
            prefs.getLong(KEY_INSTOCK_STATS_START_DATE, defaultValue)
        } else {
            defaultValue
        }
    }

    fun saveInStockStatsStartDate(startDate: Long) {
        prefs.edit {
            putLong(KEY_INSTOCK_STATS_START_DATE, startDate)
        }
    }

    fun getQueryTimeRangeLabel(type: QueryTimeRangeType, defaultLabel: String): String {
        val key = when (type) {
            QueryTimeRangeType.IN_STOCK -> KEY_QUERY_INSTOCK_TIME_RANGE
            QueryTimeRangeType.SALE_OUT -> KEY_QUERY_SALEOUT_TIME_RANGE
            QueryTimeRangeType.PACKAGING -> KEY_QUERY_PACKAGING_TIME_RANGE
        }
        return prefs.getString(key, defaultLabel) ?: defaultLabel
    }

    fun saveQueryTimeRangeLabel(type: QueryTimeRangeType, label: String) {
        val key = when (type) {
            QueryTimeRangeType.IN_STOCK -> KEY_QUERY_INSTOCK_TIME_RANGE
            QueryTimeRangeType.SALE_OUT -> KEY_QUERY_SALEOUT_TIME_RANGE
            QueryTimeRangeType.PACKAGING -> KEY_QUERY_PACKAGING_TIME_RANGE
        }
        prefs.edit { putString(key, label) }
    }

    // ==================== 扩展：企业信息配置功能（KTX 优化） ====================
    /**
     * 获取企业名称
     */
    fun getCompanyName(): String {
        return prefs.getString(KEY_COMPANY_NAME, DEFAULT_COMPANY_NAME) ?: DEFAULT_COMPANY_NAME
    }

    /**
     * 设置企业名称
     */
    fun setCompanyName(name: String) {
        prefs.edit { // KTX 扩展函数写法
            putString(KEY_COMPANY_NAME, name)
        }
    }

    /**
     * 获取企业地址
     */
    fun getCompanyAddress(): String {
        return prefs.getString(KEY_COMPANY_ADDRESS, DEFAULT_COMPANY_ADDRESS) ?: DEFAULT_COMPANY_ADDRESS
    }

    /**
     * 设置企业地址
     */
    fun setCompanyAddress(address: String) {
        prefs.edit { // KTX 扩展函数写法
            putString(KEY_COMPANY_ADDRESS, address)
        }
    }

    /**
     * 获取企业电话
     */
    fun getCompanyPhone(): String {
        return prefs.getString(KEY_COMPANY_PHONE, DEFAULT_COMPANY_PHONE) ?: DEFAULT_COMPANY_PHONE
    }

    /**
     * 设置企业电话
     */
    fun setCompanyPhone(phone: String) {
        prefs.edit { // KTX 扩展函数写法
            putString(KEY_COMPANY_PHONE, phone)
        }
    }

    /**
     * 保存所有企业信息（批量操作，提高效率）
     */
    fun saveCompanyInfo(name: String, address: String, phone: String) {
        prefs.edit { // KTX 扩展函数写法（批量操作更简洁）
            putString(KEY_COMPANY_NAME, name)
            putString(KEY_COMPANY_ADDRESS, address)
            putString(KEY_COMPANY_PHONE, phone)
        }
    }

    /**
     * 获取所有企业信息（返回结构化Map，方便外部使用）
     */
    fun getCompanyInfo(): Map<String, String> {
        return mapOf(
            "name" to getCompanyName(),
            "address" to getCompanyAddress(),
            "phone" to getCompanyPhone()
        )
    }

    // ==================== 扩展：打印配置功能（KTX 优化） ====================
    /**
     * 获取是否自动切纸
     */
    fun getAutoCut(): Boolean {
        return prefs.getBoolean(KEY_PRINTER_AUTO_CUT, true)
    }

    /**
     * 设置自动切纸
     */
    fun setAutoCut(enabled: Boolean) {
        prefs.edit { // KTX 扩展函数写法
            putBoolean(KEY_PRINTER_AUTO_CUT, enabled)
        }
    }

    /**
     * 获取纸张宽度
     */
    fun getPaperWidth(): Int {
        return prefs.getInt(KEY_PRINTER_PAPER_WIDTH, PAPER_WIDTH_58)
    }

    /**
     * 设置纸张宽度
     */
    fun setPaperWidth(width: Int) {
        prefs.edit { // KTX 扩展函数写法
            putInt(KEY_PRINTER_PAPER_WIDTH, width)
        }
    }

    /**
     * 获取打印字体大小
     */
    fun getFontSize(): Int {
        return prefs.getInt(KEY_PRINTER_FONT_SIZE, 1)
    }

    /**
     * 设置打印字体大小
     */
    fun setFontSize(size: Int) {
        prefs.edit { // KTX 扩展函数写法
            putInt(KEY_PRINTER_FONT_SIZE, size)
        }
    }

    // ==================== 扩展：原有补充的默认配置（字符串类型，KTX 优化） ====================
    /**
     * 获取默认操作员（字符串类型）
     */
    fun getDefaultOperator(): String? {
        return prefs.getString("default_operator", null)
    }

    /**
     * 设置默认操作员（字符串类型）
     */
    fun setDefaultOperator(operator: String) {
        prefs.edit { // KTX 扩展函数写法
            putString("default_operator", operator)
        }
    }

    /**
     * 获取默认库位（字符串类型）
     */
    fun getDefaultLocation(): String? {
        return prefs.getString("default_location", null)
    }

    /**
     * 设置默认库位（字符串类型）
     */
    fun setDefaultLocation(location: String) {
        prefs.edit { // KTX 扩展函数写法
            putString("default_location", location)
        }
    }
}