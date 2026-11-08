package com.billpro.app.printing

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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

        // Top Accent Banner
        paint.color = Color.rgb(243, 186, 79) // Gold accent
        canvas.drawRect(0f, 0f, 595f, 8f, paint)

        // Store Header (Left)
        paint.color = Color.rgb(20, 24, 33)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(store.storeName, 40f, 48f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.rgb(90, 100, 115)
        canvas.drawText(store.address, 40f, 64f, paint)
        canvas.drawText("Phone: ${store.phone}  |  Email: ${store.email}", 40f, 78f, paint)
        canvas.drawText("GSTIN: ${store.gstin}", 40f, 92f, paint)

        // Invoice Title & Badges (Right)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.color = Color.rgb(20, 24, 33)
        canvas.drawText("TAX INVOICE", 420f, 48f, paint)

        paint.textSize = 10f
        paint.color = Color.rgb(90, 100, 115)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Invoice No: ${invoice.invoiceNumber}", 420f, 66f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
        canvas.drawText("Date: $dateStr", 420f, 80f, paint)
        canvas.drawText("Status: ${invoice.status}", 420f, 94f, paint)

        // Divider
        paint.color = Color.rgb(220, 225, 235)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 110f, 555f, 110f, paint)

        // Billed To Section
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10.5f
        paint.color = Color.rgb(20, 24, 33)
        canvas.drawText("BILLED TO:", 40f, 130f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.rgb(70, 80, 95)
        if (customer != null) {
            canvas.drawText(customer.name, 40f, 145f, paint)
            canvas.drawText("Phone: ${customer.phone}", 40f, 159f, paint)
            if (customer.gstin.isNotBlank()) {
                canvas.drawText("GSTIN: ${customer.gstin}", 40f, 173f, paint)
            }
            if (customer.address.isNotBlank()) {
                canvas.drawText("Address: ${customer.address}", 40f, 187f, paint)
            }
        } else {
            canvas.drawText("Walk-in / Cash Customer", 40f, 145f, paint)
        }

        // Table Header
        val tableTop = 205f
        paint.color = Color.rgb(240, 243, 248)
        canvas.drawRect(40f, tableTop, 555f, tableTop + 24f, paint)

        paint.color = Color.rgb(40, 50, 65)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText("#", 48f, tableTop + 16f, paint)
        canvas.drawText("ITEM DESCRIPTION", 75f, tableTop + 16f, paint)
        canvas.drawText("QTY", 280f, tableTop + 16f, paint)
        canvas.drawText("RATE", 330f, tableTop + 16f, paint)
        canvas.drawText("TAXABLE", 400f, tableTop + 16f, paint)
        canvas.drawText("GST %", 465f, tableTop + 16f, paint)
        canvas.drawText("TOTAL", 515f, tableTop + 16f, paint)

        // Table Rows
        var currentY = tableTop + 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f
        paint.color = Color.rgb(50, 60, 75)

        lines.forEachIndexed { index, line ->
            canvas.drawText("${index + 1}", 48f, currentY, paint)

            val desc = if (line.description.length > 36) line.description.take(33) + "..." else line.description
            canvas.drawText(desc, 75f, currentY, paint)

            val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.2f".format(line.quantity)
            canvas.drawText(qtyStr, 280f, currentY, paint)
            canvas.drawText(formatPaise(line.unitPricePaise), 330f, currentY, paint)
            canvas.drawText(formatPaise(line.taxableValuePaise), 400f, currentY, paint)
            canvas.drawText("${line.gstRatePercent}%", 465f, currentY, paint)
            canvas.drawText(formatPaise(line.lineTotalPaise), 515f, currentY, paint)

            // Row separator
            paint.color = Color.rgb(240, 242, 247)
            canvas.drawLine(40f, currentY + 6f, 555f, currentY + 6f, paint)
            paint.color = Color.rgb(50, 60, 75)

            currentY += 22f
        }

        // Totals & Summary Box (Right)
        val summaryX = 350f
        var sumY = currentY + 20f

        paint.color = Color.rgb(248, 249, 252)
        canvas.drawRect(summaryX - 15f, sumY - 10f, 555f, sumY + 145f, paint)
        paint.color = Color.rgb(220, 225, 235)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(summaryX - 15f, sumY - 10f, 555f, sumY + 145f, paint)
        paint.style = Paint.Style.FILL

        fun drawSummaryRow(label: String, value: String, isBold: Boolean = false, isAccent: Boolean = false) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = if (isBold) 10.5f else 9f
            paint.color = if (isAccent) Color.rgb(180, 120, 20) else Color.rgb(40, 50, 65)

            canvas.drawText(label, summaryX, sumY + 8f, paint)
            canvas.drawText(value, 490f, sumY + 8f, paint)
            sumY += 18f
        }

        drawSummaryRow("Subtotal:", formatPaise(invoice.subtotalPaise))
        if (invoice.cgstPaise > 0) drawSummaryRow("CGST:", formatPaise(invoice.cgstPaise))
        if (invoice.sgstPaise > 0) drawSummaryRow("SGST:", formatPaise(invoice.sgstPaise))
        if (invoice.igstPaise > 0) drawSummaryRow("IGST:", formatPaise(invoice.igstPaise))
        if (invoice.discountPaise > 0) drawSummaryRow("Discount:", "- " + formatPaise(invoice.discountPaise))
        drawSummaryRow("TOTAL:", formatPaise(invoice.totalPaise), isBold = true, isAccent = true)
        drawSummaryRow("Amount Paid:", formatPaise(invoice.paidPaise))
        val balance = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
        drawSummaryRow("Balance Due:", formatPaise(balance), isBold = true)

        // Notes and Terms (Bottom Left)
        paint.color = Color.rgb(80, 90, 105)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Terms & Conditions:", 40f, sumY + 30f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val termsLines = store.termsAndConditions.split("\n")
        var termY = sumY + 44f
        for (term in termsLines) {
            canvas.drawText(term, 40f, termY, paint)
            termY += 14f
        }

        // Footer Note
        paint.color = Color.rgb(150, 160, 175)
        paint.textSize = 8f
        canvas.drawText("Thank you for your business! Computer-generated invoice generated by BillPro.", 130f, 810f, paint)
    }

    private fun formatPaise(paise: Long): String {
        return "₹" + String.format(Locale.ENGLISH, "%,.2f", paise / 100.0)
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

