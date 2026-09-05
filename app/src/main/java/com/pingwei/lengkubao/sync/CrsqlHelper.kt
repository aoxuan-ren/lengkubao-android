package com.pingwei.lengkubao.sync

import android.content.Context
import android.util.Log
import java.io.File

/**
 * cr-sqlite POC：检测 native 扩展是否可用；不可用时配置同步走 Outbox fallback。
 */
object CrsqlHelper {
    private const val TAG = "CrsqlHelper"

    val configTables = arrayOf(
        "customer",
        "location",
        "operator",
        "product",
        "pack_type",
    )

    @Volatile
    private var checked = false

    @Volatile
    var isAvailable: Boolean = false
        private set

    fun tryLoad(context: Context): Boolean {
        if (checked) return isAvailable
        synchronized(this) {
            if (checked) return isAvailable
            checked = true
            val libDir = context.applicationInfo.nativeLibraryDir
            val candidates = listOf(
                File(libDir, "libcrsqlite.so"),
                File(context.filesDir, "libcrsqlite.so"),
            )
            val found = candidates.any { it.exists() }
            isAvailable = found
            if (found) {
                Log.i(TAG, "✅ cr-sqlite 扩展已就绪，配置表可走 CRR 通道")
            } else {
                Log.i(TAG, "ℹ️ cr-sqlite 扩展未打包，配置同步使用 Outbox fallback")
            }
            return isAvailable
        }
    }
}
