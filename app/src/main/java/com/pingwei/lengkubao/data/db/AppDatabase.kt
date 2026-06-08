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
    ],
    version = 23,
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

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "lengkubao_v23.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
            Log.d(TAG, "Database instance destroyed and closed")
        }

        @Suppress("DEPRECATION")
        private fun buildDatabase(context: Context): AppDatabase {
            Log.d(TAG, "🚀 Building NEW database - Version 23")

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Log.d(TAG, "✅ Database CREATED - 首次创建数据库")
                        Thread {
                            try {
                                insertInitialData(db)
                                Log.d(TAG, "✅ 初始数据插入完成")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 初始数据插入失败: ${e.message}")
                            }
                        }.start()
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

                        // 兜底：每次打开数据库都确保默认商品/包装类型存在（仅空表插入）
                        Thread {
                            try {
                                insertInitialData(db)
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ onOpen 补齐初始数据失败: ${e.message}")
                            }
                        }.start()
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
                            productNo, productName, unit, enabled, remark, category, create_time, standardPrice
                        ) VALUES 
                        ('SP01', '42型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP02', '45型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP03', '48型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP04', '60型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP05', '精品型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP06', '次型', '箱', 1, '', '梨', $now, 0.0),
                        ('SP07', '筐', '个', 1, '', '包装', $now, 0.0)
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
                        INSERT INTO packaging_type (type_no, type_name, unit, unit_price, enabled, remark, create_time) VALUES 
                        ('BZ01', '42箱', '个', 0.0, 1, '42型包装箱', $now),
                        ('BZ02', '45箱', '个', 0.0, 1, '45型包装箱', $now),
                        ('BZ03', '60箱', '个', 0.0, 1, '60型包装箱', $now),
                        ('BZ04', '42全套', '套', 0.0, 1, '42型全套包装', $now),
                        ('BZ05', '60全套', '套', 0.0, 1, '60型全套包装', $now),
                        ('BZ06', '45全套', '套', 0.0, 1, '45型全套包装', $now),
                        ('BZ07', '格垫', '个', 0.0, 1, '格垫包装', $now),
                        ('BZ08', '托盘', '个', 0.0, 1, '托盘包装', $now),
                        ('BZ09', '网垫', '个', 0.0, 1, '网垫包装', $now),
                        ('BZ10', '纸片', '张', 0.0, 1, '纸片包装', $now),
                        ('BZ11', '纸', '张', 0.0, 1, '纸包装', $now),
                        ('BZ12', '网套', '个', 0.0, 1, '网套包装', $now),
                        ('BZ13', '保鲜膜', '卷', 0.0, 1, '保鲜膜包装', $now),
                        ('BZ14', '48箱', '个', 0.0, 1, '48型包装箱', $now)
                        """.trimIndent()
                    )
                    Log.d(TAG, "✅ 已插入默认包装类型：14个")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 插入默认包装类型失败: ${e.message}")
                }
            } else {
                Log.d(TAG, "ℹ️ packaging_type表已有数据($packagingCount)，跳过默认包装类型插入")
            }
        }
    }
}

// 迁移类定义（需要放在companion object外部）
private open class Migration(startVersion: Int, endVersion: Int) : androidx.room.migration.Migration(startVersion, endVersion) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 迁移逻辑在companion object中已实现
    }
}