package com.pingwei.lengkubao.fiscal

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Calendar
import java.util.TreeSet
import kotlin.comparisons.compareByDescending

/**
 * 按年份隔离 Room 数据库文件与活跃年份配置（对齐桌面端 FiscalYearService）。
 */
object FiscalYearManager {
    private const val TAG = "FiscalYearManager"
    private const val MIN_YEAR = 2000
    private const val MAX_YEAR = 2100
    private const val DB_PREFIX = "lengkubao_"
    private const val LEGACY_DB_NAME = "lengkubao_v30.db"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var _activeYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    @Volatile
    private var initialized = false

    val isInitialized: Boolean get() = initialized

    val activeYear: Int
        get() {
            if (!initialized) throw IllegalStateException("FiscalYearManager 未初始化")
            return _activeYear
        }

    val currentActiveYear: Int get() = activeYear

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        ensureDirectoriesExist()
        migrateLegacyDatabaseIfNeeded()
        loadActiveYearFromConfig()
        initialized = true
        Log.i(TAG, "已初始化，活跃年份: $_activeYear, 库: ${getDbNameForYear(_activeYear)}")
    }

    fun getDbNameForYear(year: Int): String {
        validateYear(year)
        return "${DB_PREFIX}${year}.db"
    }

    fun getActiveDbName(): String {
        if (!initialized) throw IllegalStateException("FiscalYearManager 未初始化")
        return getDbNameForYear(_activeYear)
    }

    fun getDbFileForYear(context: Context, year: Int): File {
        return context.applicationContext.getDatabasePath(getDbNameForYear(year))
    }

    fun getActiveDbFile(context: Context): File {
        return getDbFileForYear(context, currentActiveYear)
    }

    private val configFile: File
        get() {
            val ctx = requireContext()
            return File(File(ctx.filesDir, "config"), "fiscal_year.ini")
        }

    fun listAvailableYears(context: Context): List<Int> {
        ensureDirectoriesExist()
        val years = TreeSet(compareByDescending<Int> { it })

        val dbDir = context.applicationContext.getDatabasePath(LEGACY_DB_NAME).parentFile
        if (dbDir != null && dbDir.exists()) {
            dbDir.listFiles()?.forEach { file ->
                if (!file.isFile || !file.name.startsWith(DB_PREFIX) || !file.name.endsWith(".db")) return@forEach
                val yearStr = file.name.removePrefix(DB_PREFIX).removeSuffix(".db")
                val year = yearStr.toIntOrNull() ?: return@forEach
                if (isValidSqliteFile(file) || !file.exists()) {
                    years.add(year)
                }
            }
        }

        if (initialized || configFile.exists()) {
            val activePath = getDbFileForYear(context, _activeYear)
            if (isValidSqliteFile(activePath) || !activePath.exists()) {
                years.add(_activeYear)
            }
        }

        return years.toList()
    }

    fun switchToYear(year: Int) {
        validateYear(year)
        if (year == _activeYear) return

        val ctx = requireContext()
        val dbPath = getDbFileForYear(ctx, year)
        if (dbPath.exists() && !isValidSqliteFile(dbPath)) {
            throw IllegalStateException("年份 $year 的数据库文件已损坏，无法切换。")
        }

        _activeYear = year
        saveActiveYearToConfig()
        Log.i(TAG, "已切换到 $year 年")
    }

    fun saveCurrentYear(context: Context, overwrite: Boolean = true) {
        val source = getActiveDbFile(context)
        if (!source.exists()) {
            throw IllegalStateException("当前年份数据库不存在，无法保存。")
        }
        if (!isValidSqliteFile(source)) {
            throw IllegalStateException("当前年份数据库文件无效或已损坏，无法保存。")
        }
        val target = getDbFileForYear(context, _activeYear)
        safeCopySqliteDatabase(context, source, target, overwrite)
    }

    fun createNewYear(context: Context, year: Int) {
        validateYear(year)
        val targetFile = getDbFileForYear(context, year)
        if (targetFile.exists()) {
            if (isValidSqliteFile(targetFile)) {
                throw IllegalStateException("年份 $year 的数据库已存在。")
            }
            deleteDatabaseSidecarFiles(targetFile)
        }

        val sourcePath = resolveSourceDatabasePath(context, _activeYear)
        saveCurrentYear(context, overwrite = true)

        try {
            YearDatabaseCreator.createFromMaster(context, sourcePath, targetFile)
            switchToYear(year)
        } catch (e: Exception) {
            deleteDatabaseSidecarFiles(targetFile)
            throw e
        }
    }

    fun isValidSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                if (input.read(header) < 16) return false
                String(header, 0, 15, Charsets.US_ASCII) == "SQLite format 3"
            }
        } catch (_: Exception) {
            false
        }
    }

    fun checkpointDatabase(context: Context, dbFile: File) {
        if (!dbFile.exists()) return
        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkpoint 失败: ${e.message}")
        }
    }

    fun safeCopySqliteDatabase(context: Context, source: File, target: File, overwrite: Boolean) {
        val src = source.absoluteFile
        val dst = target.absoluteFile
        if (src.absolutePath.equals(dst.absolutePath, ignoreCase = true)) {
            checkpointDatabase(context, src)
            return
        }
        if (dst.exists() && !overwrite) {
            throw java.io.IOException("目标数据库已存在: ${dst.absolutePath}")
        }
        checkpointDatabase(context, src)
        dst.parentFile?.mkdirs()
        deleteDatabaseSidecarFiles(dst)
        src.copyTo(dst, overwrite = true)
    }

    fun deleteDatabaseSidecarFiles(dbFile: File) {
        listOf(
            dbFile,
            File(dbFile.absolutePath + "-wal"),
            File(dbFile.absolutePath + "-shm"),
            File(dbFile.absolutePath + "-journal")
        ).forEach { file ->
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "删除文件失败 ${file.absolutePath}: ${e.message}")
            }
        }
    }

    private fun resolveSourceDatabasePath(context: Context, sourceYear: Int): File {
        val path = getDbFileForYear(context, sourceYear)
        if (isValidSqliteFile(path)) return path

        findLegacyDatabasePath(context)?.let { legacy ->
            if (isValidSqliteFile(legacy)) {
                Log.w(TAG, "源年份库无效，回退使用旧库: ${legacy.absolutePath}")
                return legacy
            }
        }

        throw IllegalStateException(
            "源年份 $sourceYear 的数据库无效或已损坏，无法新建年份。"
        )
    }

    private fun migrateLegacyDatabaseIfNeeded() {
        if (configFile.exists()) return

        val ctx = requireContext()
        val existingYears = listAvailableYears(ctx).filter { year ->
            isValidSqliteFile(getDbFileForYear(ctx, year))
        }
        if (existingYears.isNotEmpty()) {
            _activeYear = existingYears.first()
            saveActiveYearToConfig()
            return
        }

        val legacy = findLegacyDatabasePath(ctx) ?: run {
            _activeYear = Calendar.getInstance().get(Calendar.YEAR)
            saveActiveYearToConfig()
            return
        }
        if (!isValidSqliteFile(legacy)) {
            _activeYear = Calendar.getInstance().get(Calendar.YEAR)
            saveActiveYearToConfig()
            return
        }

        val defaultYear = Calendar.getInstance().get(Calendar.YEAR)
        val target = getDbFileForYear(ctx, defaultYear)
        target.parentFile?.mkdirs()
        safeCopySqliteDatabase(ctx, legacy, target, overwrite = true)
        _activeYear = defaultYear
        saveActiveYearToConfig()
        Log.i(TAG, "已将 $LEGACY_DB_NAME 迁移为 ${target.name}，原文件保留")
    }

    private fun findLegacyDatabasePath(context: Context): File? {
        val ctx = context.applicationContext
        val legacy = ctx.getDatabasePath(LEGACY_DB_NAME)
        return if (legacy.exists()) legacy else null
    }

    private fun ensureDirectoriesExist() {
        configFile.parentFile?.mkdirs()
    }

    private fun loadActiveYearFromConfig() {
        if (configFile.exists()) {
            configFile.readLines(Charsets.UTF_8).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("ActiveYear=", ignoreCase = true)) {
                    val value = trimmed.removePrefix("ActiveYear=").trim()
                    value.toIntOrNull()?.let { year ->
                        if (year in MIN_YEAR..MAX_YEAR) {
                            _activeYear = year
                            return
                        }
                    }
                }
            }
        }
        _activeYear = Calendar.getInstance().get(Calendar.YEAR)
        saveActiveYearToConfig()
    }

    private fun saveActiveYearToConfig() {
        ensureDirectoriesExist()
        configFile.writeText("ActiveYear=$_activeYear\n", Charsets.UTF_8)
    }

    private fun validateYear(year: Int) {
        require(year in MIN_YEAR..MAX_YEAR) { "年份须在 $MIN_YEAR–$MAX_YEAR 之间。" }
    }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException("FiscalYearManager 未初始化")
    }
}
