package com.billpro.app.printing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.billpro.app.data.CustomerEntity
import com.billpro.app.data.InvoiceEntity
import com.billpro.app.data.InvoiceLineEntity
import com.billpro.app.data.StoreProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfGenerator {

    fun generateInvoicePdf(
        context: Context,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customer: CustomerEntity?,
        store: StoreProfile
    ): File {
        val invoiceDir = File(context.cacheDir, "invoices")
        if (!invoiceDir.exists()) {
            invoiceDir.mkdirs()
        }

        val sanitizedNumber = invoice.invoiceNumber.replace("[^a-zA-Z0-9-]".toRegex(), "_")
        val pdfFile = File(invoiceDir, "${sanitizedNumber}.pdf")

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (points)
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawInvoicePage(canvas, invoice, lines, customer, store)

        document.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return pdfFile
    }

    private fun drawInvoicePage(
        canvas: Canvas,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customer: CustomerEntity?,
        store: StoreProfile
    ) {
        val paint = Paint().apply { isAntiAlias = true }

        // Top Accent Banner (Signature Yellow #FFC928)
        paint.color = Color.rgb(255, 201, 40)
        canvas.drawRect(0f, 0f, 595f, 10f, paint)

        // Store Header (Left)
        paint.color = Color.rgb(25, 25, 25)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText(store.storeName, 40f, 50f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.rgb(110, 115, 125)
        var storeY = 66f
        if (store.address.isNotBlank()) {
            canvas.drawText(store.address, 40f, storeY, paint)
            storeY += 14f
        }
        val contactLine = listOfNotNull(
            store.phone.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
            store.email.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  |  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, 40f, storeY, paint)
        }

        // Invoice Title & Badges (Right)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.color = Color.rgb(25, 25, 25)
        canvas.drawText("INVOICE", 440f, 50f, paint)

        paint.textSize = 10f
        paint.color = Color.rgb(100, 105, 115)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Invoice No: ${invoice.invoiceNumber}", 410f, 68f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
        canvas.drawText("Date: $dateStr", 410f, 82f, paint)
        canvas.drawText("Status: ${invoice.status}", 410f, 96f, paint)

        // Divider
        paint.color = Color.rgb(235, 235, 240)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 112f, 555f, 112f, paint)

        // Billed To Section
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        paint.color = Color.rgb(25, 25, 25)
        canvas.drawText("BILLED TO:", 40f, 132f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.rgb(80, 85, 95)
        if (customer != null) {
            canvas.drawText(customer.name, 40f, 147f, paint)
            if (customer.phone.isNotBlank()) {
                canvas.drawText("Phone: ${customer.phone}", 40f, 161f, paint)
            }
            if (customer.address.isNotBlank()) {
                canvas.drawText("Address: ${customer.address}", 40f, 175f, paint)
            }
        } else {
            canvas.drawText("Walk-in / Cash Customer", 40f, 147f, paint)
        }

        // Table Header (Clean table without GST columns)
        val tableTop = 195f
        paint.color = Color.rgb(247, 246, 242) // Warm neutral
        canvas.drawRect(40f, tableTop, 555f, tableTop + 24f, paint)

        paint.color = Color.rgb(30, 30, 30)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("#", 48f, tableTop + 16f, paint)
        canvas.drawText("ITEM DESCRIPTION", 80f, tableTop + 16f, paint)
        canvas.drawText("QTY", 330f, tableTop + 16f, paint)
        canvas.drawText("RATE", 415f, tableTop + 16f, paint)
        canvas.drawText("AMOUNT", 505f, tableTop + 16f, paint)

        // Table Rows
        var currentY = tableTop + 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.rgb(40, 45, 55)

        lines.forEachIndexed { index, line ->
            canvas.drawText("${index + 1}", 48f, currentY, paint)

            val desc = if (line.description.length > 42) line.description.take(39) + "..." else line.description
            canvas.drawText(desc, 80f, currentY, paint)

            val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.2f".format(line.quantity)
            canvas.drawText(qtyStr, 330f, currentY, paint)
            canvas.drawText(formatPaise(line.unitPricePaise), 415f, currentY, paint)
            canvas.drawText(formatPaise(line.lineTotalPaise), 505f, currentY, paint)

            // Row separator
            paint.color = Color.rgb(242, 242, 245)
            canvas.drawLine(40f, currentY + 6f, 555f, currentY + 6f, paint)
            paint.color = Color.rgb(40, 45, 55)

            currentY += 22f
        }

        // Totals & Summary Box (Right)
        val summaryX = 330f
        var sumY = currentY + 20f

        val boxHeight = 135f + (if (invoice.previousBalancePaise > 0) 20f else 0f) + (if (invoice.discountPaise > 0) 20f else 0f)

        paint.color = Color.rgb(250, 249, 245)
        canvas.drawRect(summaryX - 15f, sumY - 10f, 555f, sumY + boxHeight, paint)
        paint.color = Color.rgb(230, 228, 220)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(summaryX - 15f, sumY - 10f, 555f, sumY + boxHeight, paint)
        paint.style = Paint.Style.FILL

        fun drawSummaryRow(label: String, value: String, isBold: Boolean = false, isAccent: Boolean = false) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = if (isBold) 10.5f else 9f
            paint.color = if (isAccent) Color.rgb(20, 20, 20) else Color.rgb(60, 65, 75)

            canvas.drawText(label, summaryX, sumY + 8f, paint)
            canvas.drawText(value, 490f, sumY + 8f, paint)
            sumY += 18f
        }

        drawSummaryRow("Subtotal:", formatPaise(invoice.subtotalPaise))
        if (invoice.discountPaise > 0) {
            drawSummaryRow("Discount:", "- " + formatPaise(invoice.discountPaise))
        }
        if (invoice.previousBalancePaise > 0) {
            drawSummaryRow("Previous Balance:", "+ " + formatPaise(invoice.previousBalancePaise), isBold = true)
        }
        drawSummaryRow("TOTAL PAYABLE:", formatPaise(invoice.totalPaise), isBold = true, isAccent = true)
        drawSummaryRow("Amount Paid:", formatPaise(invoice.paidPaise))
        val balance = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
        drawSummaryRow("Balance Due:", formatPaise(balance), isBold = true)

        // Notes and Terms (Bottom Left)
        if (store.termsAndConditions.isNotBlank()) {
            paint.color = Color.rgb(90, 95, 105)
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Terms & Conditions:", 40f, sumY + 24f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val termsLines = store.termsAndConditions.split("\n")
            var termY = sumY + 38f
            for (term in termsLines.take(4)) {
                canvas.drawText(term, 40f, termY, paint)
                termY += 13f
            }
        }

        // Footer Note
        paint.color = Color.rgb(160, 165, 175)
        paint.textSize = 8f
        canvas.drawText("Thank you for your business! Generated by BillPro.", 170f, 810f, paint)
    }

    private fun formatPaise(paise: Long): String {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", paise / 100.0)
    }

    /**
     * Downloads/saves the generated PDF file directly to the device's public Downloads directory.
     * Supports Android 10+ (Scoped Storage) and legacy storage.
     */
    fun savePdfToDownloads(context: Context, pdfFile: File): File? {
        try {
            val fileName = pdfFile.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BillPro")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        pdfFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    return pdfFile
                }
            }

            // Fallback for older devices or direct file copy
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val billProDir = File(downloadsDir, "BillPro")
            if (!billProDir.exists()) {
                billProDir.mkdirs()
            }
            val destFile = File(billProDir, fileName)
            pdfFile.copyTo(destFile, overwrite = true)
            return destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareInvoicePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Please find attached invoice ${pdfFile.nameWithoutExtension} from BillPro.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Invoice via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openInvoicePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Open Invoice with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
