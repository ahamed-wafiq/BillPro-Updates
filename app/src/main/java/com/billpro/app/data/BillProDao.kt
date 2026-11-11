package com.billpro.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BillProDao {
    // ----------------------------------------------------
    // BUSINESS MANAGEMENT
    // ----------------------------------------------------
    @Query("SELECT * FROM businesses WHERE businessId = :businessId LIMIT 1")
    suspend fun getBusinessById(businessId: String): BusinessEntity?

    @Query("SELECT * FROM businesses WHERE businessId = :businessId LIMIT 1")
    fun observeBusiness(businessId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE ownerEmail = :email LIMIT 1")
    suspend fun getBusinessByOwnerEmail(email: String): BusinessEntity?

    @Query("SELECT * FROM businesses WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun getBusinessByOwnerUid(ownerUid: String): BusinessEntity?

    @Query("SELECT * FROM businesses ORDER BY businessName COLLATE NOCASE")
    suspend fun getAllBusinesses(): List<BusinessEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: BusinessEntity)

    // ----------------------------------------------------
    // PRODUCTS (ISOLATED BY BUSINESS)
    // ----------------------------------------------------
    @Query("SELECT * FROM products WHERE businessId = :businessId ORDER BY name COLLATE NOCASE")
    fun observeProducts(businessId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE businessId = :businessId AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name COLLATE NOCASE")
    fun searchProducts(businessId: String, query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND businessId = :businessId LIMIT 1")
    suspend fun getProductById(id: Long, businessId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity): Long

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id AND businessId = :businessId")
    suspend fun deleteProductById(id: Long, businessId: String)

    @Query("UPDATE products SET stockQuantity = stockQuantity + :delta, updatedAtEpochMs = :now WHERE id = :id AND businessId = :businessId")
    suspend fun incrementProductStock(id: Long, businessId: String, delta: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stockQuantity = stockQuantity - :delta, updatedAtEpochMs = :now WHERE id = :id AND businessId = :businessId")
    suspend fun decrementProductStock(id: Long, businessId: String, delta: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stockQuantity = :newStock, updatedAtEpochMs = :now WHERE id = :id AND businessId = :businessId")
    suspend fun setProductStock(id: Long, businessId: String, newStock: Double, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM products WHERE businessId = :businessId AND stockQuantity <= lowStockThreshold")
    fun observeLowStockCount(businessId: String): Flow<Int>

    @Query("SELECT * FROM products WHERE businessId = :businessId AND stockQuantity <= lowStockThreshold ORDER BY stockQuantity ASC")
    fun observeLowStockProducts(businessId: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE businessId = :businessId")
    fun observeProductCount(businessId: String): Flow<Int>

    // ----------------------------------------------------
    // INVENTORY LEDGER (STOCK MOVEMENT TRACKING)
    // ----------------------------------------------------
    @Query("SELECT * FROM inventory_ledger WHERE businessId = :businessId ORDER BY createdAtEpochMs DESC")
    fun observeInventoryLedger(businessId: String): Flow<List<InventoryLedgerEntity>>

    @Query("SELECT * FROM inventory_ledger WHERE productId = :productId AND businessId = :businessId ORDER BY createdAtEpochMs DESC")
    fun observeProductInventoryLedger(productId: Long, businessId: String): Flow<List<InventoryLedgerEntity>>

    @Query("SELECT * FROM inventory_ledger WHERE businessId = :businessId AND (productName LIKE '%' || :query || '%' OR reference LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY createdAtEpochMs DESC")
    fun searchInventoryLedger(businessId: String, query: String): Flow<List<InventoryLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryLedger(entry: InventoryLedgerEntity): Long

    @Delete
    suspend fun deleteInventoryLedgerEntry(entry: InventoryLedgerEntity)

    @Transaction
    suspend fun recordStockMovement(
        productId: Long,
        businessId: String,
        movementType: String,
        quantityChange: Double,
        unitCostPaise: Long = 0,
        reference: String = "",
        notes: String = ""
    ): Long {
        val product = getProductById(productId, businessId) ?: throw IllegalArgumentException("Product not found")
        val newStock = product.stockQuantity + quantityChange
        setProductStock(productId, businessId, newStock)
        val totalCostPaise = (unitCostPaise * Math.abs(quantityChange)).toLong()
        return insertInventoryLedger(
            InventoryLedgerEntity(
                businessId = businessId,
                productId = productId,
                productName = product.name,
                createdAtEpochMs = System.currentTimeMillis(),
                movementType = movementType,
                quantityChange = quantityChange,
                balanceAfter = newStock,
                unitCostPaise = unitCostPaise,
                totalCostPaise = totalCostPaise,
                reference = reference,
                notes = notes
            )
        )
    }

    // ----------------------------------------------------
    // CUSTOMERS (ISOLATED BY BUSINESS)
    // ----------------------------------------------------
    @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name COLLATE NOCASE")
    fun observeCustomers(businessId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE businessId = :businessId AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR gstin LIKE '%' || :query || '%') ORDER BY name COLLATE NOCASE")
    fun searchCustomers(businessId: String, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id AND businessId = :businessId LIMIT 1")
    suspend fun getCustomerById(id: Long, businessId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity): Long

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id AND businessId = :businessId")
    suspend fun deleteCustomerById(id: Long, businessId: String)

    @Query("SELECT COUNT(*) FROM customers WHERE businessId = :businessId")
    fun observeCustomerCount(businessId: String): Flow<Int>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND businessId = :businessId ORDER BY createdAtEpochMs DESC")
    fun observeCustomerInvoices(customerId: Long, businessId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT COALESCE(SUM(totalPaise - paidPaise), 0) FROM invoices WHERE customerId = :customerId AND businessId = :businessId AND status != 'CANCELLED'")
    suspend fun getCustomerUnpaidInvoiceBalance(customerId: Long, businessId: String): Long

    // ----------------------------------------------------
    // INVOICES & LINES (ISOLATED BY BUSINESS)
    // ----------------------------------------------------
    @Query("SELECT * FROM invoices WHERE businessId = :businessId ORDER BY createdAtEpochMs DESC")
    fun observeInvoices(businessId: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE businessId = :businessId AND status = :status ORDER BY createdAtEpochMs DESC")
    fun observeInvoicesByStatus(businessId: String, status: String): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT DISTINCT i.* FROM invoices i
        LEFT JOIN customers c ON i.customerId = c.id
        WHERE i.businessId = :businessId 
          AND (
              i.invoiceNumber LIKE '%' || :query || '%' 
              OR i.notes LIKE '%' || :query || '%'
              OR (c.name IS NOT NULL AND c.name LIKE '%' || :query || '%')
              OR (c.phone IS NOT NULL AND c.phone LIKE '%' || :query || '%')
              OR (i.customerId IS NULL AND ('walk-in' LIKE '%' || :query || '%' OR 'cash' LIKE '%' || :query || '%'))
          )
        ORDER BY i.createdAtEpochMs DESC
    """)
    fun searchInvoices(businessId: String, query: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id AND businessId = :businessId LIMIT 1")
    suspend fun getInvoiceById(id: Long, businessId: String): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: InvoiceEntity): Long

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id AND businessId = :businessId")
    suspend fun deleteInvoiceById(id: Long, businessId: String)

    @Query("UPDATE invoices SET status = :status WHERE id = :id AND businessId = :businessId")
    suspend fun updateInvoiceStatus(id: Long, businessId: String, status: String)

    @Query("UPDATE invoices SET paidPaise = :paidPaise, status = :status WHERE id = :id AND businessId = :businessId")
    suspend fun updateInvoicePaidAmount(id: Long, businessId: String, paidPaise: Long, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceLines(lines: List<InvoiceLineEntity>)

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId AND businessId = :businessId ORDER BY id ASC")
    suspend fun getInvoiceLines(invoiceId: Long, businessId: String): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId AND businessId = :businessId ORDER BY id ASC")
    fun observeInvoiceLines(invoiceId: Long, businessId: String): Flow<List<InvoiceLineEntity>>

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId AND businessId = :businessId")
    suspend fun deleteInvoiceLines(invoiceId: Long, businessId: String)

    // ----------------------------------------------------
    // CASHBOOK ENTRIES (ISOLATED BY BUSINESS)
    // ----------------------------------------------------
    @Query("SELECT * FROM cashbook_entries WHERE businessId = :businessId ORDER BY createdAtEpochMs DESC")
    fun observeCashbook(businessId: String): Flow<List<CashbookEntryEntity>>

    @Query("SELECT * FROM cashbook_entries WHERE businessId = :businessId AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs ORDER BY createdAtEpochMs DESC")
    fun observeCashbookForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<List<CashbookEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashbookEntry(entry: CashbookEntryEntity): Long

    @Query("DELETE FROM cashbook_entries WHERE id = :id AND businessId = :businessId")
    suspend fun deleteCashbookEntryById(id: Long, businessId: String)

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cashbook_entries WHERE businessId = :businessId AND direction = 'IN'")
    fun observeTotalCashIn(businessId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cashbook_entries WHERE businessId = :businessId AND direction = 'OUT'")
    fun observeTotalCashOut(businessId: String): Flow<Long>

    // ----------------------------------------------------
    // AGGREGATIONS & REPORTS (ISOLATED BY BUSINESS)
    // ----------------------------------------------------
    @Query("SELECT COALESCE(SUM(totalPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeInvoiceTotalForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeInvoiceCountForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalPaise - paidPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND totalPaise > paidPaise AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observePendingPaymentsForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cashbook_entries WHERE businessId = :businessId AND direction = 'IN' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeCashInForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cashbook_entries WHERE businessId = :businessId AND direction = 'OUT' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeCashOutForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(cgstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeCgstForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(sgstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeSgstForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(igstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeIgstForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(subtotalPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND createdAtEpochMs >= :startMs AND createdAtEpochMs < :endMs")
    fun observeTaxableValueForPeriod(businessId: String, startMs: Long, endMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalSales(businessId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalInvoicesCount(businessId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalPaise - paidPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED' AND totalPaise > paidPaise")
    fun observePendingPayments(businessId: String): Flow<Long>

    @Query("SELECT * FROM invoices WHERE businessId = :businessId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecentInvoices(businessId: String, limit: Int = 10): Flow<List<InvoiceEntity>>

    @Query("SELECT COALESCE(SUM(cgstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalCgst(businessId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(sgstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalSgst(businessId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(igstPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalIgst(businessId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(subtotalPaise), 0) FROM invoices WHERE businessId = :businessId AND status != 'CANCELLED'")
    fun observeTotalTaxableValue(businessId: String): Flow<Long>

    // ----------------------------------------------------
    // BACKWARD COMPATIBLE DEFAULTS (TARGETS 'default_biz')
    // ----------------------------------------------------
    fun observeProducts(): Flow<List<ProductEntity>> = observeProducts("default_biz")
    fun searchProducts(query: String): Flow<List<ProductEntity>> = searchProducts("default_biz", query)
    suspend fun getProductById(id: Long): ProductEntity? = getProductById(id, "default_biz")
    suspend fun deleteProductById(id: Long) = deleteProductById(id, "default_biz")
    suspend fun incrementProductStock(id: Long, delta: Double, now: Long = System.currentTimeMillis()) = incrementProductStock(id, "default_biz", delta, now)
    suspend fun decrementProductStock(id: Long, delta: Double, now: Long = System.currentTimeMillis()) = decrementProductStock(id, "default_biz", delta, now)
    suspend fun setProductStock(id: Long, newStock: Double, now: Long = System.currentTimeMillis()) = setProductStock(id, "default_biz", newStock, now)
    fun observeLowStockCount(): Flow<Int> = observeLowStockCount("default_biz")
    fun observeLowStockProducts(): Flow<List<ProductEntity>> = observeLowStockProducts("default_biz")
    fun observeProductCount(): Flow<Int> = observeProductCount("default_biz")

    fun observeCustomers(): Flow<List<CustomerEntity>> = observeCustomers("default_biz")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = searchCustomers("default_biz", query)
    suspend fun getCustomerById(id: Long): CustomerEntity? = getCustomerById(id, "default_biz")
    suspend fun deleteCustomerById(id: Long) = deleteCustomerById(id, "default_biz")
    fun observeCustomerCount(): Flow<Int> = observeCustomerCount("default_biz")
    fun observeCustomerInvoices(customerId: Long): Flow<List<InvoiceEntity>> = observeCustomerInvoices(customerId, "default_biz")

    fun observeInvoices(): Flow<List<InvoiceEntity>> = observeInvoices("default_biz")
    fun observeInvoicesByStatus(status: String): Flow<List<InvoiceEntity>> = observeInvoicesByStatus("default_biz", status)
    fun searchInvoices(query: String): Flow<List<InvoiceEntity>> = searchInvoices("default_biz", query)
    suspend fun getInvoiceById(id: Long): InvoiceEntity? = getInvoiceById(id, "default_biz")
    suspend fun deleteInvoiceById(id: Long) = deleteInvoiceById(id, "default_biz")
    suspend fun updateInvoiceStatus(id: Long, status: String) = updateInvoiceStatus(id, "default_biz", status)
    suspend fun updateInvoicePaidAmount(id: Long, paidPaise: Long, status: String) = updateInvoicePaidAmount(id, "default_biz", paidPaise, status)
    suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity> = getInvoiceLines(invoiceId, "default_biz")
    fun observeInvoiceLines(invoiceId: Long): Flow<List<InvoiceLineEntity>> = observeInvoiceLines(invoiceId, "default_biz")
    suspend fun deleteInvoiceLines(invoiceId: Long) = deleteInvoiceLines(invoiceId, "default_biz")

    fun observeCashbook(): Flow<List<CashbookEntryEntity>> = observeCashbook("default_biz")
    fun observeCashbookForPeriod(startMs: Long, endMs: Long): Flow<List<CashbookEntryEntity>> = observeCashbookForPeriod("default_biz", startMs, endMs)
    suspend fun deleteCashbookEntryById(id: Long) = deleteCashbookEntryById(id, "default_biz")
    fun observeTotalCashIn(): Flow<Long> = observeTotalCashIn("default_biz")
    fun observeTotalCashOut(): Flow<Long> = observeTotalCashOut("default_biz")

    fun observeInvoiceTotalForPeriod(startMs: Long, endMs: Long): Flow<Long> = observeInvoiceTotalForPeriod("default_biz", startMs, endMs)
    fun observeTotalSales(): Flow<Long> = observeTotalSales("default_biz")
    fun observeTotalInvoicesCount(): Flow<Int> = observeTotalInvoicesCount("default_biz")
    fun observePendingPayments(): Flow<Long> = observePendingPayments("default_biz")
    fun observeRecentInvoices(limit: Int = 10): Flow<List<InvoiceEntity>> = observeRecentInvoices("default_biz", limit)
    fun observeTotalCgst(): Flow<Long> = observeTotalCgst("default_biz")
    fun observeTotalSgst(): Flow<Long> = observeTotalSgst("default_biz")
    fun observeTotalIgst(): Flow<Long> = observeTotalIgst("default_biz")
    fun observeTotalTaxableValue(): Flow<Long> = observeTotalTaxableValue("default_biz")

    // ----------------------------------------------------
    // COMPOSITE ATOMIC BUSINESS TRANSACTIONS
    // ----------------------------------------------------
    @Transaction
    suspend fun createInvoiceWithLines(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        decrementStock: Boolean = true,
        recordCashbook: Boolean = true
    ): Long {
        val invoiceId = upsertInvoice(invoice)
        val bizId = invoice.businessId
        val linesWithInvoiceId = lines.map { it.copy(invoiceId = invoiceId, businessId = bizId) }
        insertInvoiceLines(linesWithInvoiceId)

        if (decrementStock) {
            for (line in linesWithInvoiceId) {
                val prodId = line.productId
                if (prodId != null) {
                    decrementProductStock(prodId, bizId, line.quantity)
                    val prod = getProductById(prodId, bizId)
                    val balanceAfter = prod?.stockQuantity ?: 0.0
                    insertInventoryLedger(
                        InventoryLedgerEntity(
                            businessId = bizId,
                            productId = prodId,
                            productName = line.description,
                            createdAtEpochMs = invoice.createdAtEpochMs,
                            movementType = "SALE",
                            quantityChange = -line.quantity,
                            balanceAfter = balanceAfter,
                            unitCostPaise = line.unitPricePaise,
                            totalCostPaise = line.lineTotalPaise,
                            reference = invoice.invoiceNumber,
                            notes = "Sale billed on ${invoice.invoiceNumber}"
                        )
                    )
                }
            }
        }

        if (recordCashbook && invoice.paidPaise > 0) {
            insertCashbookEntry(
                CashbookEntryEntity(
                    businessId = bizId,
                    createdAtEpochMs = invoice.createdAtEpochMs,
                    direction = "IN",
                    category = "Sales",
                    description = "Payment for ${invoice.invoiceNumber}",
                    amountPaise = invoice.paidPaise,
                    linkedInvoiceId = invoiceId
                )
            )
        }
        return invoiceId
    }

    @Transaction
    suspend fun recordPayment(
        invoiceId: Long,
        amountPaise: Long,
        paymentMethod: String = "Cash"
    ) {
        // default fallback
        recordPaymentForBusiness(invoiceId, "default_biz", amountPaise, paymentMethod)
    }

    @Transaction
    suspend fun recordPaymentForBusiness(
        invoiceId: Long,
        businessId: String,
        amountPaise: Long,
        paymentMethod: String = "Cash"
    ) {
        val invoice = getInvoiceById(invoiceId, businessId) ?: return
        val updatedPaid = (invoice.paidPaise + amountPaise).coerceAtMost(invoice.totalPaise)
        val newStatus = if (updatedPaid >= invoice.totalPaise) "PAID" else "PARTIAL"
        updateInvoicePaidAmount(invoiceId, businessId, updatedPaid, newStatus)

        insertCashbookEntry(
            CashbookEntryEntity(
                businessId = businessId,
                createdAtEpochMs = System.currentTimeMillis(),
                direction = "IN",
                category = "Invoice Payment",
                description = "Collected for ${invoice.invoiceNumber} via $paymentMethod",
                amountPaise = amountPaise,
                linkedInvoiceId = invoiceId
            )
        )
    }

    @Transaction
    suspend fun cancelInvoiceWithStockRestoration(invoiceId: Long) {
        cancelInvoiceWithStockRestorationForBusiness(invoiceId, "default_biz")
    }

    @Transaction
    suspend fun cancelInvoiceWithStockRestorationForBusiness(invoiceId: Long, businessId: String) {
        val invoice = getInvoiceById(invoiceId, businessId) ?: return
        if (invoice.status == "CANCELLED") return

        val lines = getInvoiceLines(invoiceId, businessId)
        for (line in lines) {
            val prodId = line.productId
            if (prodId != null) {
                incrementProductStock(prodId, businessId, line.quantity)
                val prod = getProductById(prodId, businessId)
                val balanceAfter = prod?.stockQuantity ?: 0.0
                insertInventoryLedger(
                    InventoryLedgerEntity(
                        businessId = businessId,
                        productId = prodId,
                        productName = line.description,
                        createdAtEpochMs = System.currentTimeMillis(),
                        movementType = "RETURN",
                        quantityChange = line.quantity,
                        balanceAfter = balanceAfter,
                        unitCostPaise = line.unitPricePaise,
                        totalCostPaise = line.lineTotalPaise,
                        reference = invoice.invoiceNumber,
                        notes = "Restored stock from cancelled invoice ${invoice.invoiceNumber}"
                    )
                )
            }
        }

        updateInvoiceStatus(invoiceId, businessId, "CANCELLED")

        if (invoice.paidPaise > 0) {
            insertCashbookEntry(
                CashbookEntryEntity(
                    businessId = businessId,
                    createdAtEpochMs = System.currentTimeMillis(),
                    direction = "OUT",
                    category = "Refund",
                    description = "Cancelled invoice refund for ${invoice.invoiceNumber}",
                    amountPaise = invoice.paidPaise,
                    linkedInvoiceId = invoiceId
                )
            )
        }
    }
}
