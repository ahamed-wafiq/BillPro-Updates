package com.billpro.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.billpro.app.data.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Restoring : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Offline(val reason: String) : SyncStatus()
    data class Error(val error: String) : SyncStatus()
}

class BusinessSyncManager(
    private val context: Context,
    private val dao: BillProDao
) {
    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    /**
     * Upload local business data (Store profile, products, customers, invoices with lines, and cashbook) to Firestore.
     */
    suspend fun syncBusinessData(businessId: String): SyncStatus = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable) {
            return@withContext SyncStatus.Offline("Local storage active. Cloud synchronization requires Firebase to be configured.")
        }
        if (!isOnline()) {
            return@withContext SyncStatus.Offline("No active internet connection. Changes saved locally on device.")
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val bizRef = firestore.collection("businesses").document(businessId)

            // 0. Sync Store Profile
            val store = StoreProfile.load(context, businessId)
            val storeDoc = hashMapOf(
                "storeName" to store.storeName,
                "phone" to store.phone,
                "email" to store.email,
                "address" to store.address,
                "gstin" to store.gstin,
                "termsAndConditions" to store.termsAndConditions,
                "updatedAtEpochMs" to System.currentTimeMillis()
            )
            bizRef.set(storeDoc, SetOptions.merge()).await()

            // 1. Sync Products
            val localProducts = dao.observeProducts(businessId).first()
            for (p in localProducts) {
                val doc = hashMapOf(
                    "id" to p.id,
                    "businessId" to businessId,
                    "name" to p.name,
                    "sku" to p.sku,
                    "barcode" to p.barcode,
                    "unit" to p.unit,
                    "salePricePaise" to p.salePricePaise,
                    "purchasePricePaise" to p.purchasePricePaise,
                    "gstRatePercent" to p.gstRatePercent,
                    "stockQuantity" to p.stockQuantity,
                    "lowStockThreshold" to p.lowStockThreshold,
                    "updatedAtEpochMs" to p.updatedAtEpochMs
                )
                bizRef.collection("products").document("prod_${p.id}").set(doc, SetOptions.merge()).await()
            }

            // 2. Sync Customers
            val localCustomers = dao.observeCustomers(businessId).first()
            for (c in localCustomers) {
                val doc = hashMapOf(
                    "id" to c.id,
                    "businessId" to businessId,
                    "name" to c.name,
                    "phone" to c.phone,
                    "gstin" to c.gstin,
                    "address" to c.address,
                    "openingBalancePaise" to c.openingBalancePaise,
                    "updatedAtEpochMs" to c.updatedAtEpochMs
                )
                bizRef.collection("customers").document("cust_${c.id}").set(doc, SetOptions.merge()).await()
            }

            // 3. Sync Invoices (including line items and previous balance)
            val localInvoices = dao.observeInvoices(businessId).first()
            for (inv in localInvoices) {
                val lines = dao.getInvoiceLines(inv.id, businessId)
                val linesList = lines.map { l ->
                    hashMapOf(
                        "id" to l.id,
                        "productId" to (l.productId ?: 0L),
                        "description" to l.description,
                        "quantity" to l.quantity,
                        "unitPricePaise" to l.unitPricePaise,
                        "unit" to l.unit,
                        "gstRatePercent" to l.gstRatePercent,
                        "taxableValuePaise" to l.taxableValuePaise,
                        "taxPaise" to l.taxPaise,
                        "lineTotalPaise" to l.lineTotalPaise
                    )
                }

                val doc = hashMapOf(
                    "id" to inv.id,
                    "businessId" to businessId,
                    "invoiceNumber" to inv.invoiceNumber,
                    "customerId" to (inv.customerId ?: 0L),
                    "createdAtEpochMs" to inv.createdAtEpochMs,
                    "placeOfSupplyStateCode" to inv.placeOfSupplyStateCode,
                    "isInterState" to inv.isInterState,
                    "subtotalPaise" to inv.subtotalPaise,
                    "cgstPaise" to inv.cgstPaise,
                    "sgstPaise" to inv.sgstPaise,
                    "igstPaise" to inv.igstPaise,
                    "discountPaise" to inv.discountPaise,
                    "previousBalancePaise" to inv.previousBalancePaise,
                    "totalPaise" to inv.totalPaise,
                    "paidPaise" to inv.paidPaise,
                    "status" to inv.status,
                    "notes" to inv.notes,
                    "lines" to linesList
                )
                bizRef.collection("invoices").document("inv_${inv.id}").set(doc, SetOptions.merge()).await()
            }

            // 4. Sync Cashbook Entries
            val localCashbook = dao.observeCashbook(businessId).first()
            for (cb in localCashbook) {
                val doc = hashMapOf(
                    "id" to cb.id,
                    "businessId" to businessId,
                    "createdAtEpochMs" to cb.createdAtEpochMs,
                    "direction" to cb.direction,
                    "category" to cb.category,
                    "description" to cb.description,
                    "amountPaise" to cb.amountPaise,
                    "linkedInvoiceId" to (cb.linkedInvoiceId ?: 0L)
                )
                bizRef.collection("cashbook").document("cb_${cb.id}").set(doc, SetOptions.merge()).await()
            }

            SyncStatus.Success("Backed up ${localProducts.size} products, ${localCustomers.size} customers, ${localInvoices.size} invoices to Cloud Firestore.")
        } catch (e: Exception) {
            SyncStatus.Error(e.localizedMessage ?: "Sync error occurred.")
        }
    }

    /**
     * Download and restore business data from Cloud Firestore into the local Room SQLite database.
     * Perfect for setting up or logging in from a second device.
     */
    suspend fun restoreBusinessDataFromCloud(businessId: String): SyncStatus = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable) {
            return@withContext SyncStatus.Offline("Cloud synchronization requires Firebase to be configured.")
        }
        if (!isOnline()) {
            return@withContext SyncStatus.Offline("No active internet connection to restore from cloud.")
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val bizRef = firestore.collection("businesses").document(businessId)

            // 0. Restore Store Profile
            val storeSnapshot = bizRef.get().await()
            if (storeSnapshot != null && storeSnapshot.exists()) {
                val storeName = storeSnapshot.getString("storeName") ?: ""
                val phone = storeSnapshot.getString("phone") ?: ""
                val email = storeSnapshot.getString("email") ?: ""
                val address = storeSnapshot.getString("address") ?: ""
                val gstin = storeSnapshot.getString("gstin") ?: ""
                val terms = storeSnapshot.getString("termsAndConditions") ?: ""
                if (storeName.isNotBlank()) {
                    val profile = StoreProfile(
                        storeName = storeName,
                        phone = phone,
                        email = email,
                        address = address,
                        gstin = gstin,
                        termsAndConditions = terms
                    )
                    StoreProfile.save(context, profile, businessId)
                }
            }

            // 1. Restore Products
            val prodSnapshots = bizRef.collection("products").get().await()
            var restoredProductsCount = 0
            for (doc in prodSnapshots.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: ""
                if (name.isBlank()) continue
                val sku = doc.getString("sku") ?: ""
                val barcode = doc.getString("barcode") ?: ""
                val unit = doc.getString("unit") ?: "pcs"
                val salePrice = doc.getLong("salePricePaise") ?: 0L
                val purchasePrice = doc.getLong("purchasePricePaise") ?: 0L
                val gst = doc.getDouble("gstRatePercent") ?: 0.0
                val stock = doc.getDouble("stockQuantity") ?: 0.0
                val lowThreshold = doc.getDouble("lowStockThreshold") ?: 5.0
                val updated = doc.getLong("updatedAtEpochMs") ?: System.currentTimeMillis()

                val prod = ProductEntity(
                    id = id,
                    businessId = businessId,
                    name = name,
                    sku = sku,
                    barcode = barcode,
                    unit = unit,
                    salePricePaise = salePrice,
                    purchasePricePaise = purchasePrice,
                    gstRatePercent = gst,
                    stockQuantity = stock,
                    lowStockThreshold = lowThreshold,
                    updatedAtEpochMs = updated
                )
                dao.upsertProduct(prod)
                restoredProductsCount++
            }

            // 2. Restore Customers
            val custSnapshots = bizRef.collection("customers").get().await()
            var restoredCustomersCount = 0
            for (doc in custSnapshots.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: ""
                if (name.isBlank()) continue
                val phone = doc.getString("phone") ?: ""
                val gstin = doc.getString("gstin") ?: ""
                val address = doc.getString("address") ?: ""
                val opening = doc.getLong("openingBalancePaise") ?: 0L
                val updated = doc.getLong("updatedAtEpochMs") ?: System.currentTimeMillis()

                val cust = CustomerEntity(
                    id = id,
                    businessId = businessId,
                    name = name,
                    phone = phone,
                    gstin = gstin,
                    address = address,
                    openingBalancePaise = opening,
                    updatedAtEpochMs = updated
                )
                dao.upsertCustomer(cust)
                restoredCustomersCount++
            }

            // 3. Restore Invoices with line items
            val invSnapshots = bizRef.collection("invoices").get().await()
            var restoredInvoicesCount = 0
            for (doc in invSnapshots.documents) {
                val id = doc.getLong("id") ?: 0L
                val invNum = doc.getString("invoiceNumber") ?: "INV-${id}"
                val custId = doc.getLong("customerId")?.takeIf { it > 0 }
                val created = doc.getLong("createdAtEpochMs") ?: System.currentTimeMillis()
                val posState = doc.getString("placeOfSupplyStateCode") ?: ""
                val isInter = doc.getBoolean("isInterState") ?: false
                val subtotal = doc.getLong("subtotalPaise") ?: 0L
                val cgst = doc.getLong("cgstPaise") ?: 0L
                val sgst = doc.getLong("sgstPaise") ?: 0L
                val igst = doc.getLong("igstPaise") ?: 0L
                val discount = doc.getLong("discountPaise") ?: 0L
                val previousBalance = doc.getLong("previousBalancePaise") ?: 0L
                val total = doc.getLong("totalPaise") ?: 0L
                val paid = doc.getLong("paidPaise") ?: 0L
                val status = doc.getString("status") ?: "PAID"
                val notes = doc.getString("notes") ?: ""

                val invoice = InvoiceEntity(
                    id = id,
                    businessId = businessId,
                    invoiceNumber = invNum,
                    customerId = custId,
                    createdAtEpochMs = created,
                    placeOfSupplyStateCode = posState,
                    isInterState = isInter,
                    subtotalPaise = subtotal,
                    cgstPaise = cgst,
                    sgstPaise = sgst,
                    igstPaise = igst,
                    discountPaise = discount,
                    previousBalancePaise = previousBalance,
                    totalPaise = total,
                    paidPaise = paid,
                    status = status,
                    notes = notes
                )
                dao.upsertInvoice(invoice)

                // Restore line items
                val rawLines = doc.get("lines") as? List<*>
                if (rawLines != null) {
                    val dbLines = rawLines.mapNotNull { item ->
                        val m = item as? Map<*, *> ?: return@mapNotNull null
                        val lineId = (m["id"] as? Number)?.toLong() ?: 0L
                        val prodId = (m["productId"] as? Number)?.toLong()?.takeIf { it > 0 }
                        val desc = (m["description"] as? String) ?: "Item"
                        val qty = (m["quantity"] as? Number)?.toDouble() ?: 1.0
                        val unitPrice = (m["unitPricePaise"] as? Number)?.toLong() ?: 0L
                        val unit = (m["unit"] as? String)?.ifBlank { "pcs" } ?: "pcs"
                        val gst = (m["gstRatePercent"] as? Number)?.toDouble() ?: 0.0
                        val taxable = (m["taxableValuePaise"] as? Number)?.toLong() ?: (qty * unitPrice).toLong()
                        val tax = (m["taxPaise"] as? Number)?.toLong() ?: 0L
                        val lineTotal = (m["lineTotalPaise"] as? Number)?.toLong() ?: (qty * unitPrice).toLong()
                        InvoiceLineEntity(
                            id = lineId,
                            businessId = businessId,
                            invoiceId = id,
                            productId = prodId,
                            description = desc,
                            quantity = qty,
                            unitPricePaise = unitPrice,
                            unit = unit,
                            gstRatePercent = gst,
                            taxableValuePaise = taxable,
                            taxPaise = tax,
                            lineTotalPaise = lineTotal
                        )
                    }
                    if (dbLines.isNotEmpty()) {
                        dao.deleteInvoiceLines(id, businessId)
                        dao.insertInvoiceLines(dbLines)
                    }
                }
                restoredInvoicesCount++
            }

            // 4. Restore Cashbook Entries
            val cbSnapshots = bizRef.collection("cashbook").get().await()
            for (doc in cbSnapshots.documents) {
                val id = doc.getLong("id") ?: 0L
                val created = doc.getLong("createdAtEpochMs") ?: System.currentTimeMillis()
                val direction = doc.getString("direction") ?: "IN"
                val category = doc.getString("category") ?: "SALES"
                val desc = doc.getString("description") ?: ""
                val amount = doc.getLong("amountPaise") ?: 0L
                val linkedInv = doc.getLong("linkedInvoiceId")?.takeIf { it > 0 }

                val entry = CashbookEntryEntity(
                    id = id,
                    businessId = businessId,
                    createdAtEpochMs = created,
                    direction = direction,
                    category = category,
                    description = desc,
                    amountPaise = amount,
                    linkedInvoiceId = linkedInv
                )
                dao.insertCashbookEntry(entry)
            }

            SyncStatus.Success("Restored $restoredProductsCount products, $restoredCustomersCount customers, and $restoredInvoicesCount invoices from Cloud Firestore.")
        } catch (e: Exception) {
            SyncStatus.Error(e.localizedMessage ?: "Failed to restore data from Cloud Firestore.")
        }
    }
}
