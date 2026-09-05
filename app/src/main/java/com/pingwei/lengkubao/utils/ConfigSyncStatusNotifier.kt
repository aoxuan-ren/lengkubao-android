package com.pingwei.lengkubao.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 基础配置待同步数量变化时通知 UI 刷新。 */
object ConfigSyncStatusNotifier {
    private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val refreshRequests = _refreshRequests.asSharedFlow()

    fun notifyChanged() {
        _refreshRequests.tryEmit(Unit)
    }
}
