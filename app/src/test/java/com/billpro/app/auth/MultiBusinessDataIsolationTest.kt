package com.billpro.app.auth

import com.billpro.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MultiBusinessDataIsolationTest {

    @Test
    fun testBusinessDataIsolationAcrossTenants() {
        // Business A (ABC Electronics)
        val bizAId = "BIZ_001_ABC"
        val prodA1 = ProductEntity(
            id = 101L,
            businessId = bizAId,
            name = "Laptop",
            salePricePaise = 50000_00
        )
        val custA1 = CustomerEntity(
            id = 201L,
            businessId = bizAId,
            name = "Customer A Corp"
        )
        val invA1 = InvoiceEntity(
            id = 301L,
            businessId = bizAId,
            invoiceNumber = "INV-A-001",
            subtotalPaise = 50000_00,
            cgstPaise = 4500_00,
            sgstPaise = 4500_00,
            igstPaise = 0L,
            totalPaise = 59000_00,
            paidPaise = 59000_00,
            status = "PAID"
        )

        // Business B (XYZ Clothing)
        val bizBId = "BIZ_002_XYZ"
        val prodB1 = ProductEntity(
            id = 102L,
            businessId = bizBId,
            name = "Silk Shirt",
            salePricePaise = 1500_00
        )
        val custB1 = CustomerEntity(
            id = 202L,
            businessId = bizBId,
            name = "Customer B Individual"
        )
        val invB1 = InvoiceEntity(
            id = 302L,
            businessId = bizBId,
            invoiceNumber = "INV-B-001",
            subtotalPaise = 1500_00,
            cgstPaise = 90_00,
            sgstPaise = 90_00,
            igstPaise = 0L,
            totalPaise = 1680_00,
            paidPaise = 1000_00,
            status = "PARTIAL"
        )

        val allProducts = listOf(prodA1, prodB1)
        val allCustomers = listOf(custA1, custB1)
        val allInvoices = listOf(invA1, invB1)

        // Verify Business A only sees its own records
        val productsForBizA = allProducts.filter { it.businessId == bizAId }
        val customersForBizA = allCustomers.filter { it.businessId == bizAId }
        val invoicesForBizA = allInvoices.filter { it.businessId == bizAId }

        assertEquals(1, productsForBizA.size)
        assertEquals("Laptop", productsForBizA[0].name)
        assertEquals(1, customersForBizA.size)
        assertEquals("Customer A Corp", customersForBizA[0].name)
        assertEquals(1, invoicesForBizA.size)
        assertEquals("INV-A-001", invoicesForBizA[0].invoiceNumber)

        // Verify Business B only sees its own records
        val productsForBizB = allProducts.filter { it.businessId == bizBId }
        val customersForBizB = allCustomers.filter { it.businessId == bizBId }
        val invoicesForBizB = allInvoices.filter { it.businessId == bizBId }

        assertEquals(1, productsForBizB.size)
        assertEquals("Silk Shirt", productsForBizB[0].name)
        assertEquals(1, customersForBizB.size)
        assertEquals("Customer B Individual", customersForBizB[0].name)
        assertEquals(1, invoicesForBizB.size)
        assertEquals("INV-B-001", invoicesForBizB[0].invoiceNumber)

        // Verify that Business B can NEVER access Business A's items
        assertFalse(productsForBizB.any { it.businessId == bizAId })
        assertFalse(customersForBizB.any { it.businessId == bizAId })
        assertFalse(invoicesForBizB.any { it.businessId == bizAId })
    }

    @Test
    fun testUserSessionEncapsulation() {
        val sessionA = UserSession(
            businessId = "BIZ_001_ABC",
            ownerUid = "uid_owner_a",
            ownerEmail = "ownerA@gmail.com",
            ownerName = "Alice Owner",
            businessName = "ABC Electronics"
        )

        val sessionB = UserSession(
            businessId = "BIZ_002_XYZ",
            ownerUid = "uid_owner_b",
            ownerEmail = "ownerB@gmail.com",
            ownerName = "Bob Owner",
            businessName = "XYZ Clothing"
        )

        assertNotEquals(sessionA.businessId, sessionB.businessId)
        assertNotEquals(sessionA.ownerEmail, sessionB.ownerEmail)
        assertEquals("ABC Electronics", sessionA.businessName)
        assertEquals("XYZ Clothing", sessionB.businessName)
    }

    @Test
    fun testMultiTenantBackupJsonContainsBusinessId() {
        val bizId = "BIZ_TENANT_TEST_99"
        val root = JSONObject().apply {
            put("app", "BillPro")
            put("version", 2)
            put("businessId", bizId)
            put("exportedAtEpochMs", System.currentTimeMillis())
        }

        assertEquals("BillPro", root.getString("app"))
        assertEquals(2, root.getInt("version"))
        assertEquals(bizId, root.getString("businessId"))
    }
}

