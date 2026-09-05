package com.pingwei.lengkubao.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pingwei.lengkubao.data.db.converter.StockEnumConverter
import com.pingwei.lengkubao.data.db.dao.*
import com.pingwei.lengkubao.data.db.entity.*
import com.pingwei.lengkubao.fiscal.FiscalYearManager

@TypeConverters(StockEnumConverter::class)
@Database(
    entities = [
        Customer::class,
        Product::class,
        Location::class,
        Operator::class,
        InStockBill::class,
        InStockItem::class,
        SaleBill::class,
        SaleItem::class,
        Stock::class,
        StockChange::class,
        PackagingType::class,
        PackagingBill::class,
        PackagingItem::class,
        PcStockSnapshot::class,
        PcInboundDailySnapshot::class,
        // 新增预支和扣款实体
        Advance::class,
        Deduction::class,
        SyncLocalOpLog::class,
        SyncDeviceCursor::class,
        SyncAppliedOp::class,
        SyncRuntimeFlag::class,
        PreSaleBill::class,
        PreSaleItem::class,
        PaymentRecord::class,
        OutboundRecord::class,
        OutboundRecordItem::class,
        CustomerInboundStock::class,
        LedgerCategory::class,
        LedgerEntry::class,
    ],
    version = 35,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun locationDao(): LocationDao
    abstract fun operatorDao(): OperatorDao
    abstract fun inStockBillDao(): InStockBillDao
    abstract fun inStockItemDao(): InStockItemDao
    abstract fun saleBillDao(): SaleBillDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun stockDao(): StockDao
    abstract fun stockChangeDao(): StockChangeDao
    abstract fun packagingTypeDao(): PackagingTypeDao
    abstract fun packagingBillDao(): PackagingBillDao
    abstract fun packagingItemDao(): PackagingItemDao
    abstract fun pcStockSnapshotDao(): PcStockSnapshotDao
    abstract fun pcInboundDailySnapshotDao(): PcInboundDailySnapshotDao
    // 新增DAO
    abstract fun advanceDao(): AdvanceDao
    abstract fun deductionDao(): DeductionDao
    abstract fun syncDao(): SyncDao
    abstract fun preSaleBillDao(): PreSaleBillDao
    abstract fun preSaleItemDao(): PreSaleItemDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun outboundRecordDao(): OutboundRecordDao
    abstract fun outboundRecordItemDao(): OutboundRecordItemDao
    abstract fun customerInboundStockDao(): CustomerInboundStockDao
    abstract fun ledgerCategoryDao(): LedgerCategoryDao
    abstract fun ledgerEntryDao(): LedgerEntryDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = if (FiscalYearManager.isInitialized) {
                    FiscalYearManager.getActiveDbName()
                } else {
                    "lengkubao_v30.db"
                }
                val instance = buildDatabase(context, dbName, skipInitialData = false)
                INSTANCE = instance
                instance
            }
        }

        /** 创建仅含 schema 的空库（新建年份时使用，不插入默认种子数据）。 */
        fun createEmptyDatabase(context: Context, dbName: String) {
            val appContext = context.applicationContext
            val db = buildDatabase(appContext, dbName, skipInitialData = true)
            try {
                // Room 懒加载：须先打开 writableDatabase 才会在磁盘创建 .db 文件
                db.openHelper.writableDatabase.close()
            } finally {
                db.close()
            }
            val dbFile = appContext.getDatabasePath(dbName)
            // #region agent log
            Log.i(
                "DBG256c22",
                """{"sessionId":"256c22","hypothesisId":"A","location":"AppDatabase.createEmptyDatabase","message":"after force open","data":{"dbName":"$dbName","exists":${dbFile.exists()},"length":${if (dbFile.exists()) dbFile.length() else -1}},"timestamp":${System.currentTimeMillis()}}"""
            )
            // #endregion
            if (!dbFile.exists()) {
                throw IllegalStateException("空库创建失败：文件未生成 $dbName")
            }
            Log.d(TAG, "空库已创建: $dbName (${dbFile.length()} bytes)")
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
            Log.d(TAG, "Database instance destroyed and closed")
        }

        @Suppress("DEPRECATION")
        private fun buildDatabase(
            context: Context,
            dbName: String,
            skipInitialData: Boolean,
        ): AppDatabase {
            Log.d(TAG, "🚀 Building database - Version 33, name=$dbName, skipSeed=$skipInitialData")

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_30_31,
                    MIGRATION_31_32,
                    MIGRATION_32_33,
                    MIGRATION_33_34,
                    MIGRATION_34_35
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Log.d(TAG, "✅ Database CREATED - 首次创建数据库")
                        if (!skipInitialData) {
                            Thread {
                                try {
                                    insertInitialData(db)
                                    Log.d(TAG, "✅ 初始数据插入完成")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ 初始数据插入失败: ${e.message}")
                                }
                            }.start()
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Log.d(TAG, "✅ Database OPENED - Version: ${db.version}")

                        // 创建索引
                        try {
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_enabled ON product(enabled)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_product_no ON product(productNo)")

                            // 为新表创建索引
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_advances_customer_no ON advances(customer_no)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_advances_advance_date ON advances(advance_date)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deductions_customer_no ON deductions(customer_no)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deductions_deduct_date ON deductions(deduct_date)")

                            // 为包装单表创建索引
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packaging_bill_customer_no ON packaging_bill(customer_no)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packaging_bill_bill_no ON packaging_bill(bill_no)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packaging_bill_flag ON packaging_bill(packaging_type_flag)")

                            // 为包装明细表创建索引
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packaging_item_bill_id ON packaging_item(bill_id)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_packaging_item_flag ON packaging_item(packaging_type_flag)")

                            // PC库存快照表索引
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_location_id ON pc_stock_snapshot(location_id)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_product_id ON pc_stock_snapshot(product_id)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_snapshot_time ON pc_stock_snapshot(snapshot_time)")

                            // PC入库统计快照表索引
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_date ON pc_inbound_daily_snapshot(date)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_customer_no ON pc_inbound_daily_snapshot(customer_no)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_location_name ON pc_inbound_daily_snapshot(location_name)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_spec ON pc_inbound_daily_snapshot(spec)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_snapshot_time ON pc_inbound_daily_snapshot(snapshot_time)")
                            db.execSQL("INSERT OR IGNORE INTO sync_runtime_flags(id, suppress_local_log) VALUES (1, 0)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS sync_local_oplog (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entity_type TEXT NOT NULL, entity_key TEXT NOT NULL, op_type TEXT NOT NULL, payload_json TEXT NOT NULL, origin_device_id TEXT NOT NULL, origin_op_id TEXT NOT NULL, created_at INTEGER NOT NULL, pushed_at INTEGER, commit_seq INTEGER)")
                            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_local_oplog_origin_op ON sync_local_oplog(origin_op_id)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS sync_device_cursor (device_id TEXT NOT NULL PRIMARY KEY, last_acked_seq INTEGER NOT NULL DEFAULT 0, last_uploaded_local_id INTEGER NOT NULL DEFAULT 0, first_full_sync_done INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE IF NOT EXISTS sync_applied_ops (origin_device_id TEXT NOT NULL, origin_op_id TEXT NOT NULL, commit_seq INTEGER NOT NULL DEFAULT 0, applied_at INTEGER NOT NULL, PRIMARY KEY(origin_device_id, origin_op_id))")

                            // 旧触发器方案（依赖 json_object）已下线，避免 SQLite 兼容性问题。
                            db.execSQL("DROP TRIGGER IF EXISTS trg_sync_customer_update")
                            db.execSQL("DROP TRIGGER IF EXISTS trg_sync_location_update")
                            db.execSQL("DROP TRIGGER IF EXISTS trg_sync_operator_update")

                            Log.d(TAG, "✅ 索引创建完成")
                        } catch (e: Exception) {
                            Log.e(TAG, "创建索引失败: ${e.message}")
                        }

                        // 检查数据
                        try {
                            val cursor = db.query("SELECT COUNT(*) FROM product")
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            cursor.close()
                            Log.d(TAG, "📊 当前商品数量: $count")
                        } catch (e: Exception) {
                            Log.e(TAG, "检查数据失败: ${e.message}")
                        }

                        if (!skipInitialData) {
                            Thread {
                                try {
                                    insertInitialData(db)
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ onOpen 补齐初始数据失败: ${e.message}")
                                }
                            }.start()
                        }
                    }
                })
                .build()
        }

        // 从版本18升级到19的迁移策略（添加packaging_type_flag字段）
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本18到19")

                // 1. 备份原表（可选）
                db.execSQL("CREATE TABLE IF NOT EXISTS packaging_bill_backup AS SELECT * FROM packaging_bill")

                // 2. 创建新表（包含packaging_type_flag字段）
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS packaging_bill_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_no TEXT NOT NULL,
                        customer_id INTEGER NOT NULL,
                        customer_no TEXT NOT NULL,
                        customer_name TEXT NOT NULL,
                        bill_date TEXT NOT NULL,
                        packaging_type_flag TEXT NOT NULL DEFAULT 'TAKE',
                        operator_id INTEGER NOT NULL,
                        operator_name TEXT NOT NULL DEFAULT '',
                        total_amount REAL NOT NULL DEFAULT 0.0,
                        status TEXT NOT NULL DEFAULT '1',
                        remark TEXT NOT NULL DEFAULT '',
                        create_time INTEGER NOT NULL,
                        print_time INTEGER,
                        sync_status INTEGER NOT NULL DEFAULT 0,
                        is_voided INTEGER NOT NULL DEFAULT 0,
                        is_printed INTEGER NOT NULL DEFAULT 0,
                        is_synced INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 3. 复制旧数据，新字段使用默认值'TAKE'
                db.execSQL("""
                    INSERT INTO packaging_bill_new (
                        id, bill_no, customer_id, customer_no, customer_name, bill_date,
                        operator_id, operator_name, total_amount, status, remark,
                        create_time, print_time, sync_status, is_voided, is_printed, is_synced
                    )
                    SELECT 
                        id, bill_no, customer_id, customer_no, customer_name, bill_date,
                        operator_id, operator_name, total_amount, status, remark,
                        create_time, print_time, sync_status, is_voided, is_printed, is_synced
                    FROM packaging_bill
                """.trimIndent())

                // 4. 删除旧表
                db.execSQL("DROP TABLE packaging_bill")

                // 5. 重命名新表
                db.execSQL("ALTER TABLE packaging_bill_new RENAME TO packaging_bill")

                // 6. 同样处理packaging_item表（可选）
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS packaging_item_new (
                            itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            bill_id INTEGER NOT NULL,
                            packaging_type_flag TEXT NOT NULL DEFAULT 'TAKE',
                            packaging_type TEXT NOT NULL,
                            packaging_type_id INTEGER NOT NULL DEFAULT 0,
                            packaging_type_no TEXT NOT NULL DEFAULT '',
                            packaging_type_name TEXT NOT NULL DEFAULT '',
                            unit TEXT NOT NULL DEFAULT '',
                            quantity INTEGER NOT NULL,
                            unit_price REAL NOT NULL,
                            amount REAL NOT NULL,
                            subtotal REAL NOT NULL DEFAULT 0.0,
                            remark TEXT NOT NULL DEFAULT '',
                            is_voided INTEGER NOT NULL DEFAULT 0,
                            is_printed INTEGER NOT NULL DEFAULT 0,
                            is_synced INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(bill_id) REFERENCES packaging_bill(id) ON DELETE CASCADE
                        )
                    """.trimIndent())

                    // 复制数据
                    db.execSQL("""
                        INSERT INTO packaging_item_new (
                            itemId, bill_id, packaging_type, packaging_type_id, packaging_type_no,
                            packaging_type_name, unit, quantity, unit_price, amount, subtotal,
                            remark, is_voided, is_printed, is_synced
                        )
                        SELECT 
                            itemId, bill_id, packaging_type, packaging_type_id, packaging_type_no,
                            packaging_type_name, unit, quantity, unit_price, amount, subtotal,
                            remark, is_voided, is_printed, is_synced
                        FROM packaging_item
                    """.trimIndent())

                    db.execSQL("DROP TABLE packaging_item")
                    db.execSQL("ALTER TABLE packaging_item_new RENAME TO packaging_item")

                    Log.d(TAG, "✅ packaging_item表迁移完成")
                } catch (e: Exception) {
                    Log.e(TAG, "packaging_item表迁移失败: ${e.message}")
                }

                Log.d(TAG, "✅ 数据库迁移到版本19完成（添加了包装类型标记字段）")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本19到20（新增PC库存快照表）")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pc_stock_snapshot (
                        location_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        spec TEXT NOT NULL,
                        location_name TEXT NOT NULL,
                        current_quantity INTEGER NOT NULL,
                        snapshot_time INTEGER NOT NULL,
                        PRIMARY KEY(location_id, product_id)
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_location_id ON pc_stock_snapshot(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_product_id ON pc_stock_snapshot(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_stock_snapshot_snapshot_time ON pc_stock_snapshot(snapshot_time)")

                Log.d(TAG, "✅ 数据库迁移到版本20完成（PC库存快照表已创建）")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本20到21（新增PC入库统计快照表）")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pc_inbound_daily_snapshot (
                        date TEXT NOT NULL,
                        customer_no TEXT NOT NULL,
                        customer_name TEXT NOT NULL,
                        location_name TEXT NOT NULL,
                        spec TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        order_count INTEGER NOT NULL,
                        snapshot_time INTEGER NOT NULL,
                        PRIMARY KEY(date, customer_no, location_name, spec)
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_date ON pc_inbound_daily_snapshot(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_customer_no ON pc_inbound_daily_snapshot(customer_no)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_location_name ON pc_inbound_daily_snapshot(location_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_spec ON pc_inbound_daily_snapshot(spec)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_inbound_daily_snapshot_snapshot_time ON pc_inbound_daily_snapshot(snapshot_time)")

                Log.d(TAG, "✅ 数据库迁移到版本21完成（PC入库统计快照表已创建）")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本21到22（新增双向增量同步元数据）")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_local_oplog (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entity_type TEXT NOT NULL,
                        entity_key TEXT NOT NULL,
                        op_type TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        origin_device_id TEXT NOT NULL,
                        origin_op_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        pushed_at INTEGER,
                        commit_seq INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_local_oplog_origin_op ON sync_local_oplog(origin_op_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_local_oplog_entity ON sync_local_oplog(entity_type, entity_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_local_oplog_pushed_at ON sync_local_oplog(pushed_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_device_cursor (
                        device_id TEXT NOT NULL PRIMARY KEY,
                        last_acked_seq INTEGER NOT NULL DEFAULT 0,
                        last_uploaded_local_id INTEGER NOT NULL DEFAULT 0,
                        first_full_sync_done INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_applied_ops (
                        origin_device_id TEXT NOT NULL,
                        origin_op_id TEXT NOT NULL,
                        commit_seq INTEGER NOT NULL DEFAULT 0,
                        applied_at INTEGER NOT NULL,
                        PRIMARY KEY(origin_device_id, origin_op_id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_runtime_flags (
                        id INTEGER NOT NULL PRIMARY KEY,
                        suppress_local_log INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO sync_runtime_flags(id, suppress_local_log) VALUES (1, 0)")

                // 旧触发器方案（依赖 json_object）已下线，避免低版本 SQLite 环境报错。
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_customer_insert")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_customer_update")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_customer_delete")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_location_insert")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_location_update")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_location_delete")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_operator_insert")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_operator_update")
                db.execSQL("DROP TRIGGER IF EXISTS trg_sync_operator_delete")

                Log.d(TAG, "✅ 数据库迁移到版本22完成（双向增量元数据表已创建，旧触发器已停用）")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本22到23（预售出库 + 买家类型）")

                db.execSQL("ALTER TABLE customer ADD COLUMN customer_type TEXT NOT NULL DEFAULT 'SELLER'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS presale_bill (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_no TEXT NOT NULL,
                        buyer_no TEXT NOT NULL,
                        buyer_name TEXT NOT NULL,
                        buyer_id INTEGER NOT NULL DEFAULT 0,
                        location_id INTEGER NOT NULL,
                        location_name TEXT NOT NULL DEFAULT '',
                        operator_id INTEGER NOT NULL,
                        operator_name TEXT NOT NULL DEFAULT '',
                        sale_mode TEXT NOT NULL,
                        total_amount REAL NOT NULL DEFAULT 0.0,
                        paid_amount REAL NOT NULL DEFAULT 0.0,
                        status TEXT NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        create_time INTEGER NOT NULL,
                        sync_status INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_presale_bill_no ON presale_bill(bill_no)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_presale_bill_buyer_no ON presale_bill(buyer_no)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS presale_item (
                        itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        product_no TEXT NOT NULL,
                        product_name TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        sale_price REAL NOT NULL,
                        amount REAL NOT NULL,
                        unit TEXT NOT NULL DEFAULT '箱',
                        remark TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(bill_id) REFERENCES presale_bill(id) ON DELETE CASCADE,
                        FOREIGN KEY(product_id) REFERENCES product(id) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_presale_item_bill_id ON presale_item(bill_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_id INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        pay_method TEXT NOT NULL,
                        pay_time INTEGER NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        sync_status INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bill_id) REFERENCES presale_bill(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_payment_record_bill_id ON payment_record(bill_id)")

                Log.d(TAG, "✅ 数据库迁移到版本23完成")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本23到24（客户入库库存池）")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS customer_inbound_stock (
                        customer_no TEXT NOT NULL,
                        customer_name TEXT NOT NULL DEFAULT '',
                        location_id INTEGER NOT NULL,
                        location_name TEXT NOT NULL DEFAULT '',
                        product_id INTEGER NOT NULL,
                        product_no TEXT NOT NULL DEFAULT '',
                        product_name TEXT NOT NULL DEFAULT '',
                        inbound_quantity INTEGER NOT NULL DEFAULT 0,
                        reserved_quantity INTEGER NOT NULL DEFAULT 0,
                        last_updated INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(customer_no, location_id, product_id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_customer_inbound_stock_customer ON customer_inbound_stock(customer_no)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_customer_inbound_stock_location ON customer_inbound_stock(location_id)"
                )
                Log.d(TAG, "✅ 数据库迁移到版本24完成")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本24到25（收支流水账）")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ledger_category (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        is_system INTEGER NOT NULL DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ledger_entry (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entry_no TEXT NOT NULL,
                        type TEXT NOT NULL,
                        category_id INTEGER NOT NULL,
                        category_name TEXT NOT NULL,
                        amount REAL NOT NULL,
                        entry_date TEXT NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        status INTEGER NOT NULL DEFAULT 1,
                        create_time INTEGER NOT NULL,
                        update_time INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_date_type_status ON ledger_entry(entry_date, type, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_category_id ON ledger_entry(category_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_entry_no ON ledger_entry(entry_no)"
                )

                insertDefaultLedgerCategories(db)

                Log.d(TAG, "✅ 数据库迁移到版本25完成")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本25到26（移除客户/经手人字段）")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ledger_entry_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entry_no TEXT NOT NULL,
                        type TEXT NOT NULL,
                        category_id INTEGER NOT NULL,
                        category_name TEXT NOT NULL,
                        amount REAL NOT NULL,
                        entry_date TEXT NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        status INTEGER NOT NULL DEFAULT 1,
                        create_time INTEGER NOT NULL,
                        update_time INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO ledger_entry_new (
                        id, entry_no, type, category_id, category_name, amount,
                        entry_date, remark, status, create_time, update_time
                    )
                    SELECT
                        id, entry_no, type, category_id, category_name, amount,
                        entry_date, remark, status, create_time, update_time
                    FROM ledger_entry
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE ledger_entry")
                db.execSQL("ALTER TABLE ledger_entry_new RENAME TO ledger_entry")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_date_type_status ON ledger_entry(entry_date, type, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_category_id ON ledger_entry(category_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_ledger_entry_entry_no ON ledger_entry(entry_no)"
                )

                Log.d(TAG, "✅ 数据库迁移到版本26完成")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本26到27（扣款数量单价）")
                db.execSQL(
                    "ALTER TABLE deductions ADD COLUMN quantity INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE deductions ADD COLUMN unit_price REAL NOT NULL DEFAULT 0"
                )
                Log.d(TAG, "✅ 数据库迁移到版本27完成")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本27到28（收支流水同步字段）")
                db.execSQL(
                    "ALTER TABLE ledger_entry ADD COLUMN sync_status INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE ledger_entry ADD COLUMN sync_time INTEGER"
                )
                Log.d(TAG, "✅ 数据库迁移到版本28完成")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本28到29（客户启用状态）")
                db.execSQL(
                    "ALTER TABLE customer ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
                )
                Log.d(TAG, "✅ 数据库迁移到版本29完成")
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本29到30（预售单跨设备同步字段）")
                db.execSQL("ALTER TABLE presale_bill ADD COLUMN source_record_id TEXT")
                db.execSQL("ALTER TABLE presale_bill ADD COLUMN source_device_id TEXT")
                db.execSQL("ALTER TABLE presale_bill ADD COLUMN remote_updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_presale_bill_source_record_id " +
                        "ON presale_bill(source_record_id) WHERE source_record_id IS NOT NULL"
                )
                db.execSQL("ALTER TABLE payment_record ADD COLUMN source_record_id TEXT")
                db.execSQL("ALTER TABLE payment_record ADD COLUMN source_device_id TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payment_record_source_record_id " +
                        "ON payment_record(source_record_id) WHERE source_record_id IS NOT NULL"
                )
                Log.d(TAG, "✅ 数据库迁移到版本30完成")
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本30到31（预装商品 sync_status 修正）")
                db.execSQL(
                    """
                    UPDATE product SET sync_status = 1
                    WHERE productNo NOT IN (
                        SELECT entity_key FROM sync_local_oplog
                        WHERE entity_type = 'PRODUCT' AND pushed_at IS NULL
                    )
                    """.trimIndent()
                )
                Log.d(TAG, "✅ 数据库迁移到版本31完成")
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本31到32（移除手持端仓储费/制冷费收入类目）")
                db.execSQL(
                    """
                    DELETE FROM ledger_category
                    WHERE type = 'INCOME' AND name IN ('仓储费', '制冷费')
                    """.trimIndent()
                )
                Log.d(TAG, "✅ 数据库迁移到版本32完成")
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本32到33（基础配置去编号、对齐电脑端）")
                migrateOperatorRemoveNo(db)
                migrateLocationRemoveNo(db)
                migratePackagingTypeRemoveNo(db)
                db.execSQL(
                    """
                    DELETE FROM sync_local_oplog
                    WHERE pushed_at IS NULL
                      AND entity_type IN ('OPERATOR', 'LOCATION', 'PACK_TYPE')
                    """.trimIndent()
                )
                Log.d(TAG, "✅ 数据库迁移到版本33完成")
            }

            private fun migrateOperatorRemoveNo(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TEMP TABLE _op_map AS
                    SELECT id AS old_id,
                           (SELECT MIN(o2.id) FROM operator o2 WHERE o2.name = operator.name) AS new_id
                    FROM operator
                    """.trimIndent()
                )
                listOf(
                    "in_stock_bill",
                    "sale_bill",
                    "presale_bill",
                    "packaging_bill",
                    "advances",
                    "deductions",
                ).forEach { table ->
                    db.execSQL(
                        """
                        UPDATE $table
                        SET operator_id = (
                            SELECT new_id FROM _op_map WHERE old_id = $table.operator_id
                        )
                        WHERE operator_id IN (SELECT old_id FROM _op_map WHERE old_id != new_id)
                        """.trimIndent()
                    )
                }
                db.execSQL("DELETE FROM operator WHERE id NOT IN (SELECT DISTINCT new_id FROM _op_map)")
                db.execSQL("DROP TABLE _op_map")
                db.execSQL(
                    """
                    CREATE TABLE operator_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL UNIQUE,
                        phone TEXT NOT NULL DEFAULT '',
                        role TEXT NOT NULL DEFAULT '操作员',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        remark TEXT NOT NULL DEFAULT '',
                        create_time INTEGER NOT NULL,
                        sync_status INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO operator_new (id, name, phone, role, enabled, remark, create_time, sync_status)
                    SELECT id, name, phone, role, enabled, remark, create_time, sync_status FROM operator
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE operator")
                db.execSQL("ALTER TABLE operator_new RENAME TO operator")
            }

            private fun migrateLocationRemoveNo(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TEMP TABLE _loc_map AS
                    SELECT id AS old_id,
                           (SELECT MIN(l2.id) FROM location l2
                            WHERE l2.location_name = location.location_name) AS new_id
                    FROM location
                    """.trimIndent()
                )
                listOf(
                    "in_stock_bill",
                    "sale_bill",
                    "presale_bill",
                    "stock",
                    "customer_inbound_stock",
                    "pc_stock_snapshot",
                    "stock_change",
                ).forEach { table ->
                    db.execSQL(
                        """
                        UPDATE $table
                        SET location_id = (
                            SELECT new_id FROM _loc_map WHERE old_id = $table.location_id
                        )
                        WHERE location_id IN (SELECT old_id FROM _loc_map WHERE old_id != new_id)
                        """.trimIndent()
                    )
                }
                db.execSQL("DELETE FROM location WHERE id NOT IN (SELECT DISTINCT new_id FROM _loc_map)")
                db.execSQL("DROP TABLE _loc_map")
                db.execSQL(
                    """
                    CREATE TABLE stock_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        product_id INTEGER NOT NULL,
                        product_no TEXT NOT NULL,
                        product_name TEXT NOT NULL,
                        location_id INTEGER NOT NULL,
                        current_quantity INTEGER NOT NULL DEFAULT 0,
                        reserved_quantity INTEGER NOT NULL DEFAULT 0,
                        last_updated INTEGER NOT NULL,
                        last_bill_no TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO stock_new (
                        product_id, product_no, product_name, location_id,
                        current_quantity, reserved_quantity, last_updated, last_bill_no
                    )
                    SELECT product_id, product_no, product_name, location_id,
                           SUM(current_quantity), SUM(reserved_quantity),
                           MAX(last_updated), MAX(last_bill_no)
                    FROM stock
                    GROUP BY product_id, location_id
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE stock")
                db.execSQL("ALTER TABLE stock_new RENAME TO stock")
                db.execSQL(
                    """
                    CREATE TABLE location_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        location_name TEXT NOT NULL UNIQUE,
                        description TEXT NOT NULL DEFAULT '',
                        capacity INTEGER NOT NULL DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        create_time INTEGER NOT NULL,
                        sync_status INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO location_new (
                        id, location_name, description, capacity, enabled, create_time, sync_status
                    )
                    SELECT id, location_name, description, capacity, enabled, create_time, sync_status
                    FROM location
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE location")
                db.execSQL("ALTER TABLE location_new RENAME TO location")
            }

            private fun migratePackagingTypeRemoveNo(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TEMP TABLE _pt_map AS
                    SELECT id AS old_id,
                           (SELECT MIN(p2.id) FROM packaging_type p2
                            WHERE p2.type_name = packaging_type.type_name) AS new_id
                    FROM packaging_type
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE packaging_item
                    SET packaging_type_id = (
                        SELECT new_id FROM _pt_map WHERE old_id = packaging_item.packaging_type_id
                    )
                    WHERE packaging_type_id IN (SELECT old_id FROM _pt_map WHERE old_id != new_id)
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM packaging_type WHERE id NOT IN (SELECT DISTINCT new_id FROM _pt_map)")
                db.execSQL("DROP TABLE _pt_map")
                db.execSQL(
                    """
                    CREATE TABLE packaging_type_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type_name TEXT NOT NULL UNIQUE,
                        unit TEXT NOT NULL DEFAULT '个',
                        unit_price REAL NOT NULL DEFAULT 0.0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        remark TEXT NOT NULL DEFAULT '',
                        create_time INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO packaging_type_new (
                        id, type_name, unit, unit_price, enabled, remark, create_time
                    )
                    SELECT id, type_name, unit, unit_price, enabled, remark, create_time
                    FROM packaging_type
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE packaging_type")
                db.execSQL("ALTER TABLE packaging_type_new RENAME TO packaging_type")
                db.execSQL(
                    """
                    CREATE TABLE packaging_item_new (
                        itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_id INTEGER NOT NULL,
                        packaging_type_flag TEXT NOT NULL DEFAULT 'TAKE',
                        packaging_type TEXT NOT NULL,
                        packaging_type_id INTEGER NOT NULL DEFAULT 0,
                        packaging_type_name TEXT NOT NULL DEFAULT '',
                        unit TEXT NOT NULL DEFAULT '',
                        quantity INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        amount REAL NOT NULL,
                        subtotal REAL NOT NULL DEFAULT 0.0,
                        remark TEXT NOT NULL DEFAULT '',
                        is_voided INTEGER NOT NULL DEFAULT 0,
                        is_printed INTEGER NOT NULL DEFAULT 0,
                        is_synced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bill_id) REFERENCES packaging_bill(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO packaging_item_new (
                        itemId, bill_id, packaging_type_flag, packaging_type, packaging_type_id,
                        packaging_type_name, unit, quantity, unit_price, amount, subtotal,
                        remark, is_voided, is_printed, is_synced
                    )
                    SELECT itemId, bill_id, packaging_type_flag, packaging_type, packaging_type_id,
                           packaging_type_name, unit, quantity, unit_price, amount, subtotal,
                           remark, is_voided, is_printed, is_synced
                    FROM packaging_item
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE packaging_item")
                db.execSQL("ALTER TABLE packaging_item_new RENAME TO packaging_item")
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本34到35（预售分次出库）")
                db.execSQL(
                    "ALTER TABLE presale_item ADD COLUMN shipped_quantity INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outbound_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bill_id INTEGER NOT NULL,
                        ship_time INTEGER NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        sync_status INTEGER NOT NULL DEFAULT 0,
                        source_record_id TEXT,
                        source_device_id TEXT,
                        FOREIGN KEY(bill_id) REFERENCES presale_bill(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbound_record_bill_id ON outbound_record(bill_id)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_outbound_record_source_record_id " +
                        "ON outbound_record(source_record_id) WHERE source_record_id IS NOT NULL"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS outbound_record_item (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        outbound_record_id INTEGER NOT NULL,
                        bill_item_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        product_no TEXT NOT NULL,
                        product_name TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unit TEXT NOT NULL DEFAULT '箱',
                        FOREIGN KEY(outbound_record_id) REFERENCES outbound_record(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_outbound_record_item_record_id ON outbound_record_item(outbound_record_id)"
                )
                Log.d(TAG, "✅ 数据库迁移到版本35完成")
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "🔄 开始迁移数据库从版本33到34（packaging_bill.bill_no 唯一约束）")
                // 为历史重复单号追加 id 后缀，避免唯一索引创建失败
                db.execSQL(
                    """
                    UPDATE packaging_bill
                    SET bill_no = bill_no || '_' || id
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM packaging_bill GROUP BY bill_no
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS index_packaging_bill_bill_no")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_packaging_bill_bill_no ON packaging_bill(bill_no)"
                )
                Log.d(TAG, "✅ 数据库迁移到版本34完成")
            }
        }

        private fun insertDefaultLedgerCategories(db: SupportSQLiteDatabase) {
            val count = try {
                db.query("SELECT COUNT(*) FROM ledger_category").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            } catch (_: Exception) {
                0
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

        private fun insertInitialData(db: SupportSQLiteDatabase) {
            Log.d(TAG, "📊 检查并补齐初始数据（仅空表插入）...")
            val now = System.currentTimeMillis()

            val productCount = try {
                db.query("SELECT COUNT(*) FROM product").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "查询product数量失败（可能不存在）: ${e.message}")
                0
            }

            if (productCount <= 0) {
                try {
                    db.execSQL(
                        """
                        INSERT INTO product (
                            productNo, productName, unit, enabled, remark, category, create_time, standardPrice, sync_status
                        ) VALUES 
                        ('SP01', '42型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP02', '45型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP03', '48型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP04', '60型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP05', '精品型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP06', '次型', '箱', 1, '', '梨', $now, 0.0, 1),
                        ('SP07', '筐', '个', 1, '', '包装', $now, 0.0, 1)
                        """.trimIndent()
                    )
                    Log.d(TAG, "✅ 已插入默认商品：7个")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 插入默认商品失败: ${e.message}")
                }
            } else {
                Log.d(TAG, "ℹ️ product表已有数据($productCount)，跳过默认商品插入")
            }

            val packagingCount = try {
                db.query("SELECT COUNT(*) FROM packaging_type").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "查询packaging_type数量失败（可能不存在）: ${e.message}")
                0
            }

            if (packagingCount <= 0) {
                try {
                    db.execSQL(
                        """
                        INSERT INTO packaging_type (type_name, unit, unit_price, enabled, remark, create_time) VALUES 
                        ('42箱', '个', 0.0, 1, '42型包装箱', $now),
                        ('45箱', '个', 0.0, 1, '45型包装箱', $now),
                        ('60箱', '个', 0.0, 1, '60型包装箱', $now),
                        ('42全套', '套', 0.0, 1, '42型全套包装', $now),
                        ('60全套', '套', 0.0, 1, '60型全套包装', $now),
                        ('45全套', '套', 0.0, 1, '45型全套包装', $now),
                        ('格垫', '个', 0.0, 1, '格垫包装', $now),
                        ('托盘', '个', 0.0, 1, '托盘包装', $now),
                        ('网垫', '个', 0.0, 1, '网垫包装', $now),
                        ('纸片', '张', 0.0, 1, '纸片包装', $now),
                        ('纸', '张', 0.0, 1, '纸包装', $now),
                        ('网套', '个', 0.0, 1, '网套包装', $now),
                        ('保鲜膜', '卷', 0.0, 1, '保鲜膜包装', $now),
                        ('48箱', '个', 0.0, 1, '48型包装箱', $now)
                        """.trimIndent()
                    )
                    Log.d(TAG, "✅ 已插入默认包装类型：14个")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 插入默认包装类型失败: ${e.message}")
                }
            } else {
                Log.d(TAG, "ℹ️ packaging_type表已有数据($packagingCount)，跳过默认包装类型插入")
            }

            insertDefaultLedgerCategories(db)
        }
    }
}

// 迁移类定义（需要放在companion object外部）
private open class Migration(startVersion: Int, endVersion: Int) : androidx.room.migration.Migration(startVersion, endVersion) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 迁移逻辑在companion object中已实现
    }
}