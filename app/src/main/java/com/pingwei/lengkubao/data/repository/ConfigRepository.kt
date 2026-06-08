package com.pingwei.lengkubao.data.repository

import android.content.Context
import com.pingwei.lengkubao.data.model.DefaultConfig
import com.pingwei.lengkubao.ui.common.ConfigManager

/**
 * 配置数据仓库，负责管理默认值等配置信息的存取。
 */
// 移除 @Singleton 和 @Inject 注解
class ConfigRepository(
    private val context: Context
) {
    private val configManager: ConfigManager by lazy { ConfigManager(context) }

    /**
     * 获取当前的默认配置
     */
    fun getDefaultConfig(): DefaultConfig {
        return DefaultConfig(
            defaultLocationId = configManager.defaultLocationId,
            defaultHandlerId = configManager.defaultHandlerId
        )
    }

    /**
     * 保存默认库位ID
     */
    fun saveDefaultLocationId(locationId: Long) {
        configManager.defaultLocationId = locationId
    }

    /**
     * 保存默认经手人ID
     */
    fun saveDefaultHandlerId(handlerId: Long) {
        configManager.defaultHandlerId = handlerId
    }

    /**
     * 清除所有默认配置
     */
    fun clearAllDefaults() {
        configManager.clearAllDefaults()
    }
}