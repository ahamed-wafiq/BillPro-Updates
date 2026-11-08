package com.billpro.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataBackupManagerTest {

    @Test
    fun testJsonBackupStructureIsValid() {
        val root = JSONObject()
        root.put("app", "BillPro")
        root.put("version", 1)
        root.put("exportedAtEpochMs", 1700000000000L)

        val productsArray = JSONArray()
        val prodObj = JSONObject().apply {
            put("id", 1L)
            put("name", "Test Item")
            put("sku", "SKU-001")
            put("salePricePaise", 10000L)
            put("gstRatePercent", 18.0)
            put("stockQuantity", 50.0)
        }
        productsArray.put(prodObj)
        root.put("products", productsArray)

        assertEquals("BillPro", root.getString("app"))
        assertEquals(1, root.getInt("version"))
        assertEquals(1, root.getJSONArray("products").length())

        val parsedItem = root.getJSONArray("products").getJSONObject(0)
        assertEquals("Test Item", parsedItem.getString("name"))
        assertEquals(10000L, parsedItem.getLong("salePricePaise"))
        assertEquals(18.0, parsedItem.getDouble("gstRatePercent"), 0.001)
    }

    @Test
    fun testStoreProfileDefaultValues() {
        val defaultProfile = StoreProfile()
        assertEquals("BillPro Store", defaultProfile.storeName)
        assertTrue(defaultProfile.phone.isNotBlank())
        assertTrue(defaultProfile.gstin.isNotBlank())
        assertTrue(defaultProfile.termsAndConditions.contains("Goods once sold"))
    }
}

