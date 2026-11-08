package com.billpro.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        CustomerEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        CashbookEntryEntity::class,
        BusinessEntity::class,
        InventoryLedgerEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BillProDatabase : RoomDatabase() {
    abstract fun dao(): BillProDao

    companion object {
        @Volatile private var INSTANCE: BillProDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add businessId column to products
                db.execSQL("ALTER TABLE products ADD COLUMN businessId TEXT NOT NULL DEFAULT 'default_biz'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_businessId ON products(businessId)")

                // 2. Add businessId column to customers
                db.execSQL("ALTER TABLE customers ADD COLUMN businessId TEXT NOT NULL DEFAULT 'default_biz'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_businessId ON customers(businessId)")

                // 3. Add businessId column to invoices
                db.execSQL("ALTER TABLE invoices ADD COLUMN businessId TEXT NOT NULL DEFAULT 'default_biz'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_businessId ON invoices(businessId)")

                // 4. Add businessId column to invoice_lines
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN businessId TEXT NOT NULL DEFAULT 'default_biz'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_businessId ON invoice_lines(businessId)")

                // 5. Add businessId column to cashbook_entries
                db.execSQL("ALTER TABLE cashbook_entries ADD COLUMN businessId TEXT NOT NULL DEFAULT 'default_biz'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cashbook_entries_businessId ON cashbook_entries(businessId)")

                // 6. Create businesses table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS businesses (
                        businessId TEXT NOT NULL PRIMARY KEY,
                        ownerUid TEXT NOT NULL,
                        ownerEmail TEXT NOT NULL,
                        ownerName TEXT NOT NULL,
                        businessName TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        gstin TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        createdAtEpochMs INTEGER NOT NULL DEFAULT 0,
                        updatedAtEpochMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_businesses_businessId ON businesses(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_businesses_ownerUid ON businesses(ownerUid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_businesses_ownerEmail ON businesses(ownerEmail)")

                // 7. Seed initial business record for 'default_biz' so legacy single-business records are safely preserved
                db.execSQL("""
                    INSERT OR IGNORE INTO businesses (businessId, ownerUid, ownerEmail, ownerName, businessName, phone, gstin, address, createdAtEpochMs, updatedAtEpochMs)
                    VALUES ('default_biz', 'legacy_owner', 'owner@billpro.local', 'Default Owner', 'BillPro Store', '+91 98765 43210', '33ABCDE1234F1Z5', '123 Business Avenue', 0, 0)
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        businessId TEXT NOT NULL DEFAULT 'default_biz',
                        productId INTEGER NOT NULL,
                        productName TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL DEFAULT 0,
                        movementType TEXT NOT NULL,
                        quantityChange REAL NOT NULL,
                        balanceAfter REAL NOT NULL,
                        unitCostPaise INTEGER NOT NULL DEFAULT 0,
                        totalCostPaise INTEGER NOT NULL DEFAULT 0,
                        reference TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (productId) REFERENCES products(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_ledger_businessId ON inventory_ledger(businessId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_ledger_productId ON inventory_ledger(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_ledger_createdAtEpochMs ON inventory_ledger(createdAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_ledger_movementType ON inventory_ledger(movementType)")
            }
        }

        fun getInstance(context: Context): BillProDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BillProDatabase::class.java,
                    "billpro.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
    }
}
