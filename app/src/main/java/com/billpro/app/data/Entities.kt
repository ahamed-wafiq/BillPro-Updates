package com.billpro.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "businesses",
    indices = [Index("businessId", unique = true), Index("ownerUid"), Index("ownerEmail")]
)
data class BusinessEntity(
    @PrimaryKey val businessId: String,
    val ownerUid: String,
    val ownerEmail: String,
    val ownerName: String,
    val businessName: String,
    val phone: String = "",
    val gstin: String = "",
    val address: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "products", indices = [Index("name"), Index("sku"), Index("businessId")])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val name: String,
    val sku: String = "",
    val barcode: String = "",
    val unit: String = "pcs",
    val salePricePaise: Long,
    val purchasePricePaise: Long = 0,
    val gstRatePercent: Double = 0.0,
    val stockQuantity: Double = 0.0,
    val lowStockThreshold: Double = 0.0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers", indices = [Index("name"), Index("phone"), Index("businessId")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val name: String,
    val phone: String = "",
    val gstin: String = "",
    val address: String = "",
    val openingBalancePaise: Long = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "invoices", indices = [Index("invoiceNumber"), Index("customerId"), Index("businessId")])
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val invoiceNumber: String,
    val customerId: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val placeOfSupplyStateCode: String = "",
    val isInterState: Boolean = false,
    val subtotalPaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long,
    val discountPaise: Long = 0,
    val totalPaise: Long,
    val paidPaise: Long = 0,
    val status: String = "DRAFT",
    val notes: String = ""
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId"), Index("productId"), Index("businessId")]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val invoiceId: Long,
    val productId: Long? = null,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val gstRatePercent: Double,
    val taxableValuePaise: Long,
    val taxPaise: Long,
    val lineTotalPaise: Long
)

@Entity(tableName = "cashbook_entries", indices = [Index("createdAtEpochMs"), Index("category"), Index("businessId")])
data class CashbookEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val direction: String, // IN or OUT
    val category: String,
    val description: String,
    val amountPaise: Long,
    val linkedInvoiceId: Long? = null
)

@Entity(
    tableName = "inventory_ledger",
    foreignKeys = [ForeignKey(
        entity = ProductEntity::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("businessId"),
        Index("productId"),
        Index("createdAtEpochMs"),
        Index("movementType")
    ]
)
data class InventoryLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String = "default_biz",
    val productId: Long,
    val productName: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val movementType: String, // RESTOCK, WASTAGE, ADJUSTMENT, SALE, RETURN
    val quantityChange: Double, // Positive for incoming/restock/return, negative for outgoing/sale/damage
    val balanceAfter: Double,
    val unitCostPaise: Long = 0,
    val totalCostPaise: Long = 0,
    val reference: String = "", // Supplier name, PO#, Invoice#, Batch#
    val notes: String = ""
)
