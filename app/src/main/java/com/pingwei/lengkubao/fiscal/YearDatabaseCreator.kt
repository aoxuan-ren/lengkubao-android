package com.pingwei.lengkubao.fiscal

import android.content.Context
import android.util.Log
import com.pingwei.lengkubao.data.db.AppDatabase
import java.io.File

/**
 * 从源库复制基础资料到目标年份空库（对齐桌面 CreateYearDatabaseFromMaster）。
 */
object YearDatabaseCreator {
    private const val TAG = "YearDatabaseCreator"

    private val MASTER_TABLES = listOf(
        "customer",
        "product",
        "packaging_type",
        "location",
        "operator"
    )

    fun createFromMaster(context: Context, sourceFile: File, targetFile: File) {
        val src = sourceFile.absoluteFile
        val dst = targetFile.absoluteFile
        if (!src.exists()) throw java.io.FileNotFoundException("源年份数据库不存在: ${src.absolutePath}")
        if (!FiscalYearManager.isValidSqliteFile(src)) {
            throw IllegalStateException("源数据库不是有效的 SQLite 文件: ${src.absolutePath}")
        }
        if (dst.exists()) {
            throw IllegalStateException("目标年份数据库已存在。")
        }

        FiscalYearManager.checkpointDatabase(context, src)
        FiscalYearManager.deleteDatabaseSidecarFiles(dst)

        // 创建空 schema（不插入默认种子数据）
        AppDatabase.createEmptyDatabase(context, dst.name)

        // #region agent log
        Log.i(
            "DBG256c22",
            """{"sessionId":"256c22","hypothesisId":"A","location":"YearDatabaseCreator.createFromMaster","message":"before ATTACH open","data":{"dst":"${dst.absolutePath}","exists":${dst.exists()},"length":${if (dst.exists()) dst.length() else -1}},"timestamp":${System.currentTimeMillis()}}"""
        )
        // #endregion

        val attachPath = src.absolutePath.replace("'", "''")
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dst.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            db.execSQL("ATTACH DATABASE '$attachPath' AS srcdb")
            try {
                db.beginTransaction()
                MASTER_TABLES.forEach { table ->
                    val countCursor = db.rawQuery(
                        "SELECT COUNT(*) FROM srcdb.sqlite_master WHERE type='table' AND name=?",
                        arrayOf(table)
                    )
                    countCursor.use { c ->
                        if (c.moveToFirst() && c.getInt(0) == 0) return@forEach
                    }
                    db.execSQL("INSERT INTO main.$table SELECT * FROM srcdb.$table")
                }
                insertDefaultLedgerCategoriesIfEmpty(db)
                db.setTransactionSuccessful()
            } finally {
                if (db.inTransaction()) db.endTransaction()
                db.execSQL("DETACH DATABASE srcdb")
            }
        }

        FiscalYearManager.checkpointDatabase(context, dst)
        Log.i(TAG, "新建年份库完成: ${dst.name}")
    }

    private fun insertDefaultLedgerCategoriesIfEmpty(db: android.database.sqlite.SQLiteDatabase) {
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM ledger_category", null)
        val count = countCursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count > 0) return

        db.execSQL(
            """
            INSERT INTO ledger_category (type, name, is_system, enabled, sort_order) VALUES
            ('INCOME', '包装费', 1, 1, 1),
            ('EXPENSE', '电费', 1, 1, 1),
            ('EXPENSE', '人工费', 1, 1, 2),
            ('EXPENSE', '设备维护', 1, 1, 3)
            """.trimIndent()
        )
    }
}
