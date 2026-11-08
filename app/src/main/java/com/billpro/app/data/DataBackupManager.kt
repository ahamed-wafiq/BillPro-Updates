package com.billpro.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupImportResult(
    val success: Boolean,
    val importedProducts: Int = 0,
    val importedCustomers: Int = 0,
    val importedInvoices: Int = 0,
    val message: String = ""
)

object DataBackupManager {

    suspend fun exportBackupJson(
        context: Context,
        dao: BillProDao,
        businessId: String = "default_biz"
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanBizId = businessId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val backupFile = File(backupDir, "billpro_backup_${cleanBizId}_$timestamp.json")

        val root = JSONObject()
        root.put("app", "BillPro")
        root.put("version", 2)
        root.put("businessId", businessId)
        root.put("exportedAtEpochMs", System.currentTimeMillis())

        // 1. Products
        val products = dao.observeProducts(businessId).first()
        val productsArray = JSONArray()
        for (p in products) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("businessId", p.businessId)
                put("name", p.name)
                put("sku", p.sku)
                put("barcode", p.barcode)
                put("unit", p.unit)
                put("salePricePaise", p.salePricePaise)
                put("purchasePricePaise", p.purchasePricePaise)
                put("gstRatePercent", p.gstRatePercent)
                put("stockQuantity", p.stockQuantity)
                put("lowStockThreshold", p.lowStockThreshold)
                put("updatedAtEpochMs", p.updatedAtEpochMs)
            }
            productsArray.put(obj)
        }
        root.put("products", productsArray)

        // 2. Customers
        val customers = dao.observeCustomers(businessId).first()
        val customersArray = JSONArray()
        for (c in customers) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("businessId", c.businessId)
                put("name", c.name)
                put("phone", c.phone)
                put("gstin", c.gstin)
                put("address", c.address)
                put("openingBalancePaise", c.openingBalancePaise)
                put("updatedAtEpochMs", c.updatedAtEpochMs)
            }
            customersArray.put(obj)
        }
        root.put("customers", customersArray)

        // 3. Invoices & Lines
        val invoices = dao.observeInvoices(businessId).first()
        val invoicesArray = JSONArray()
        for (inv in invoices) {
            val invObj = JSONObject().apply {
                put("id", inv.id)
                put("businessId", inv.businessId)
                put("invoiceNumber", inv.invoiceNumber)
                put("customerId", inv.customerId ?: JSONObject.NULL)
                put("createdAtEpochMs", inv.createdAtEpochMs)
                put("placeOfSupplyStateCode", inv.placeOfSupplyStateCode)
                put("isInterState", inv.isInterState)
                put("subtotalPaise", inv.subtotalPaise)
                put("cgstPaise", inv.cgstPaise)
                put("sgstPaise", inv.sgstPaise)
                put("igstPaise", inv.igstPaise)
                put("discountPaise", inv.discountPaise)
                put("totalPaise", inv.totalPaise)
                put("paidPaise", inv.paidPaise)
                put("status", inv.status)
                put("notes", inv.notes)

                // Lines
                val lines = dao.getInvoiceLines(inv.id, businessId)
                val linesArray = JSONArray()
                for (l in lines) {
                    val lObj = JSONObject().apply {
                        put("productId", l.productId ?: JSONObject.NULL)
                        put("description", l.description)
                        put("quantity", l.quantity)
                        put("unitPricePaise", l.unitPricePaise)
                        put("gstRatePercent", l.gstRatePercent)
                        put("taxableValuePaise", l.taxableValuePaise)
                        put("taxPaise", l.taxPaise)
                        put("lineTotalPaise", l.lineTotalPaise)
                    }
                    linesArray.put(lObj)
                }
                put("lines", linesArray)
            }
            invoicesArray.put(invObj)
        }
        root.put("invoices", invoicesArray)

        // 4. Cashbook
        val cashbook = dao.observeCashbook(businessId).first()
        val cashArray = JSONArray()
        for (cb in cashbook) {
            val obj = JSONObject().apply {
                put("id", cb.id)
                put("businessId", cb.businessId)
                put("createdAtEpochMs", cb.createdAtEpochMs)
                put("direction", cb.direction)
                put("category", cb.category)
                put("description", cb.description)
                put("amountPaise", cb.amountPaise)
                put("linkedInvoiceId", cb.linkedInvoiceId ?: JSONObject.NULL)
            }
            cashArray.put(obj)
        }
        root.put("cashbook", cashArray)

        FileOutputStream(backupFile).use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }

        backupFile
    }

    suspend fun exportSalesCsv(
        context: Context,
        dao: BillProDao,
        businessId: String = "default_biz"
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val cleanBizId = businessId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val csvFile = File(backupDir, "billpro_sales_${cleanBizId}_$timestamp.csv")

        val invoices = dao.observeInvoices(businessId).first()
        val customers = dao.observeCustomers(businessId).first().associateBy { it.id }

        val sb = StringBuilder()
        sb.append("InvoiceNumber,BusinessId,Date,Customer,Subtotal,CGST,SGST,IGST,Discount,Total,Paid,Balance,Status,Notes\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (inv in invoices) {
            val dateStr = sdf.format(Date(inv.createdAtEpochMs))
            val custName = inv.customerId?.let { customers[it]?.name } ?: "Walk-in Customer"
            val sanitizedCust = "\"" + custName.replace("\"", "\"\"") + "\""
            val sanitizedNotes = "\"" + inv.notes.replace("\"", "\"\"") + "\""
            val balance = (inv.totalPaise - inv.paidPaise).coerceAtLeast(0)

            sb.append("${inv.invoiceNumber},")
            sb.append("${inv.businessId},")
            sb.append("$dateStr,")
            sb.append("$sanitizedCust,")
            sb.append("%.2f,".format(inv.subtotalPaise / 100.0))
            sb.append("%.2f,".format(inv.cgstPaise / 100.0))
            sb.append("%.2f,".format(inv.sgstPaise / 100.0))
            sb.append("%.2f,".format(inv.igstPaise / 100.0))
            sb.append("%.2f,".format(inv.discountPaise / 100.0))
            sb.append("%.2f,".format(inv.totalPaise / 100.0))
            sb.append("%.2f,".format(inv.paidPaise / 100.0))
            sb.append("%.2f,".format(balance / 100.0))
            sb.append("${inv.status},")
            sb.append("$sanitizedNotes\n")
        }

        FileOutputStream(csvFile).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        csvFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun importBackupJson(
        dao: BillProDao,
        jsonString: String,
        businessId: String = "default_biz"
    ): BackupImportResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            var pCount = 0
            var cCount = 0

            // 1. Products
            if (root.has("products")) {
                val pArray = root.getJSONArray("products")
                for (i in 0 until pArray.length()) {
                    val obj = pArray.getJSONObject(i)
                    dao.upsertProduct(
                        ProductEntity(
                            id = 0L, // Generate new local ID to prevent conflict
                            businessId = businessId,
                            name = obj.getString("name"),
                            sku = obj.optString("sku", ""),
                            barcode = obj.optString("barcode", ""),
                            unit = obj.optString("unit", "pcs"),
                            salePricePaise = obj.getLong("salePricePaise"),
                            purchasePricePaise = obj.optLong("purchasePricePaise", 0L),
                            gstRatePercent = obj.optDouble("gstRatePercent", 0.0),
                            stockQuantity = obj.optDouble("stockQuantity", 0.0),
                            lowStockThreshold = obj.optDouble("lowStockThreshold", 5.0)
                        )
                    )
                    pCount++
                }
            }

            // 2. Customers
            if (root.has("customers")) {
                val cArray = root.getJSONArray("customers")
                for (i in 0 until cArray.length()) {
                    val obj = cArray.getJSONObject(i)
                    dao.upsertCustomer(
                        CustomerEntity(
                            id = 0L,
                            businessId = businessId,
                            name = obj.getString("name"),
                            phone = obj.optString("phone", ""),
                            gstin = obj.optString("gstin", ""),
                            address = obj.optString("address", ""),
                            openingBalancePaise = obj.optLong("openingBalancePaise", 0L)
                        )
                    )
                    cCount++
                }
            }

            BackupImportResult(
                success = true,
                importedProducts = pCount,
                importedCustomers = cCount,
                importedInvoices = 0,
                message = "Imported $pCount products and $cCount customers safely into business account."
            )
        } catch (e: Exception) {
            BackupImportResult(
                success = false,
                message = "Failed to parse backup file: ${e.localizedMessage}"
            )
        }
    }
}
