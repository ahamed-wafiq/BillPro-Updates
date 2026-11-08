package com.billpro.app.data

import org.junit.Assert.*
import org.junit.Test

class InventoryLedgerTest {

    @Test
    fun testRestockMovementCalculation() {
        val currentStock = 20.0
        val restockQty = 50.0
        val unitCostPaise = 12000L // ₹ 120.00
        val resultingStock = currentStock + restockQty
        val totalCostPaise = (unitCostPaise * restockQty).toLong()

        val ledgerEntry = InventoryLedgerEntity(
            productId = 1L,
            productName = "Basmati Rice 5kg",
            businessId = "biz_001",
            movementType = "RESTOCK",
            quantityChange = restockQty,
            balanceAfter = resultingStock,
            unitCostPaise = unitCostPaise,
            totalCostPaise = totalCostPaise,
            reference = "PO-2026-88 (National Traders)",
            notes = "Bulk restock received"
        )

        assertEquals(70.0, ledgerEntry.balanceAfter, 0.001)
        assertEquals(50.0, ledgerEntry.quantityChange, 0.001)
        assertEquals(600000L, ledgerEntry.totalCostPaise)
        assertEquals("RESTOCK", ledgerEntry.movementType)
    }

    @Test
    fun testWastageMovementCalculation() {
        val currentStock = 70.0
        val damagedQty = 5.0
        val resultingStock = currentStock - damagedQty

        val ledgerEntry = InventoryLedgerEntity(
            productId = 1L,
            productName = "Basmati Rice 5kg",
            businessId = "biz_001",
            movementType = "WASTAGE",
            quantityChange = -damagedQty,
            balanceAfter = resultingStock,
            reference = "Damage Incident #12",
            notes = "Water damage in warehouse storage"
        )

        assertEquals(65.0, ledgerEntry.balanceAfter, 0.001)
        assertEquals(-5.0, ledgerEntry.quantityChange, 0.001)
        assertEquals("WASTAGE", ledgerEntry.movementType)
    }

    @Test
    fun testSaleMovementCalculation() {
        val currentStock = 65.0
        val soldQty = 2.0
        val resultingStock = currentStock - soldQty

        val ledgerEntry = InventoryLedgerEntity(
            productId = 1L,
            productName = "Basmati Rice 5kg",
            businessId = "biz_001",
            movementType = "SALE",
            quantityChange = -soldQty,
            balanceAfter = resultingStock,
            reference = "INV-2026-001",
            notes = "Sold to customer on invoice INV-2026-001"
        )

        assertEquals(63.0, ledgerEntry.balanceAfter, 0.001)
        assertEquals(-2.0, ledgerEntry.quantityChange, 0.001)
        assertEquals("SALE", ledgerEntry.movementType)
    }
}

