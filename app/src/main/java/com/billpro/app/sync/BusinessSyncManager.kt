package com.billpro.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.billpro.app.data.BillProDao
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

    suspend fun syncBusinessData(businessId: String): SyncStatus = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable) {
            return@withContext SyncStatus.Offline("Local storage active. Cloud synchronization will activate when Firebase credentials are configured.")
        }
        if (!isOnline()) {
            return@withContext SyncStatus.Offline("No active internet connection. Changes saved locally in Room SQLite.")
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val bizRef = firestore.collection("businesses").document(businessId)

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

            // 3. Sync Invoices
            val localInvoices = dao.observeInvoices(businessId).first()
            for (inv in localInvoices) {
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
                    "totalPaise" to inv.totalPaise,
                    "paidPaise" to inv.paidPaise,
                    "status" to inv.status,
                    "notes" to inv.notes
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

            SyncStatus.Success("Synchronized ${localProducts.size} products, ${localCustomers.size} customers, ${localInvoices.size} invoices with Cloud Firestore.")
        } catch (e: Exception) {
            SyncStatus.Error(e.localizedMessage ?: "Sync error occurred.")
        }
    }
}

