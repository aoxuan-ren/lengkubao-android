package com.pingwei.lengkubao.data.model

/**
 * 默认配置的模型类，用于在应用层传递配置信息。
 */
data class DefaultConfig(
    val defaultLocationId: Long = -1L,
    val defaultHandlerId: Long = -1L
)