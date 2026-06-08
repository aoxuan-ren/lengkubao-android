package com.pingwei.lengkubao.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.pingwei.lengkubao.ui.main.MainActivity
import java.io.File

object AppCacheCleaner {
    private const val TAG = "AppCacheCleaner"
    private const val MAINTENANCE_PREFS = "app_maintenance"
    private const val KEY_CLEAR_ON_NEXT_START = "clear_cache_on_next_start"

    data class ClearResult(val success: Boolean, val message: String)
    data class CleanupPreview(
        val cacheBytes: Long,
        val items: List<String>
    )

    fun clearCacheAndPrepareRestart(context: Context): ClearResult {
        return try {
            // 仅登记清理任务，避免在当前进程运行时直接删缓存导致不稳定
            context.getSharedPreferences(MAINTENANCE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CLEAR_ON_NEXT_START, true)
                .apply()
            ClearResult(true, "已标记清理任务，将在重启后执行")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败: ${e.message}", e)
            ClearResult(false, e.message ?: "未知错误")
        }
    }

    fun getCleanupPreview(context: Context): CleanupPreview {
        val internalCache = calcDirSize(context.cacheDir)
        val externalCache = calcDirSize(context.externalCacheDir)
        return CleanupPreview(
            cacheBytes = internalCache + externalCache,
            items = listOf(
                "配置缓存: sync_config",
                "打印状态缓存: printer_state",
                "应用临时缓存: cacheDir",
                "外部临时缓存: externalCacheDir",
                "不会删除本地单据数据库"
            )
        )
    }

    fun restartApp(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        context.startActivity(launchIntent)
        Log.i(TAG, "已执行软重启（清任务并返回主界面）")
    }

    fun performPendingCleanupOnStartup(context: Context) {
        val prefs = context.getSharedPreferences(MAINTENANCE_PREFS, Context.MODE_PRIVATE)
        val needCleanup = prefs.getBoolean(KEY_CLEAR_ON_NEXT_START, false)
        if (!needCleanup) return
        try {
            Log.i(TAG, "检测到待执行缓存清理任务，开始执行")
            clearSharedPrefs(context)
            clearCacheDir(context.cacheDir)
            clearCacheDir(context.externalCacheDir)
            prefs.edit().putBoolean(KEY_CLEAR_ON_NEXT_START, false).apply()
            Log.i(TAG, "启动阶段缓存清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "启动阶段缓存清理失败: ${e.message}", e)
        }
    }

    private fun clearSharedPrefs(context: Context) {
        // 仅清理业务配置缓存，避免删除系统/SDK保留项导致异常
        context.getSharedPreferences("sync_config", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("printer_state", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun clearCacheDir(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    private fun calcDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
