package com.billpro.app.printing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.billpro.app.data.CustomerEntity
import com.billpro.app.data.InvoiceEntity
import com.billpro.app.data.InvoiceLineEntity
import com.billpro.app.data.StoreProfile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfGenerator {

    /**
     * Generates a clean, sanitized file name containing both customer name and invoice number.
     * Example: mushfiq_INV-20261109-1627.pdf
     */
    fun getInvoicePdfFileName(invoice: InvoiceEntity, customer: CustomerEntity?, is80mm: Boolean = false): String {
        val rawCustomer = customer?.name?.trim()?.takeIf { it.isNotBlank() } ?: "WalkIn"
        val cleanCustomer = rawCustomer.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val cleanInvoice = invoice.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return if (is80mm) {
            "${cleanCustomer}_${cleanInvoice}_80mm.pdf"
        } else {
            "${cleanCustomer}_${cleanInvoice}.pdf"
        }
    }

    /**
     * Standard A4 Invoice PDF Generator
     */
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

        val fileName = getInvoicePdfFileName(invoice, customer, is80mm = false)
        val pdfFile = File(invoiceDir, fileName)

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
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(110, 115, 125)
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 440f, 66f, paint)

        val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
        canvas.drawText("Date: $dateFormatted", 440f, 80f, paint)

        // Status Badge
        val statusText = invoice.status
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        when (invoice.status) {
            "PAID" -> paint.color = Color.rgb(36, 163, 106)
            "PARTIAL" -> paint.color = Color.rgb(233, 154, 40)
            else -> paint.color = Color.rgb(227, 75, 75)
        }
        canvas.drawText("Status: $statusText", 440f, 96f, paint)

        // Divider
        paint.color = Color.rgb(235, 232, 224)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 110f, 555f, 110f, paint)

        // Bill To section
        paint.color = Color.rgb(110, 115, 125)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BILLED TO:", 40f, 130f, paint)

        paint.color = Color.rgb(25, 25, 25)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val customerName = customer?.name ?: "Walk-in Customer / Cash Sale"
        canvas.drawText(customerName, 40f, 146f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.rgb(110, 115, 125)
        var custY = 160f
        if (customer != null && customer.phone.isNotBlank()) {
            canvas.drawText("Phone: ${customer.phone}", 40f, custY, paint)
            custY += 13f
        }
        if (customer != null && customer.address.isNotBlank()) {
            canvas.drawText("Address: ${customer.address}", 40f, custY, paint)
        }

        // Table Header
        val tableTopY = 195f
        paint.color = Color.rgb(247, 246, 242)
        canvas.drawRoundRect(40f, tableTopY, 555f, tableTopY + 26f, 6f, 6f, paint)

        paint.color = Color.rgb(60, 65, 75)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("#", 50f, tableTopY + 17f, paint)
        canvas.drawText("ITEM DESCRIPTION", 80f, tableTopY + 17f, paint)
        canvas.drawText("QTY", 330f, tableTopY + 17f, paint)
        canvas.drawText("RATE", 410f, tableTopY + 17f, paint)
        canvas.drawText("AMOUNT", 490f, tableTopY + 17f, paint)

        // Table Rows
        var currentY = tableTopY + 44f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        lines.forEachIndexed { index, line ->
            paint.color = Color.rgb(30, 30, 30)
            paint.textSize = 10f
            canvas.drawText("${index + 1}", 50f, currentY, paint)
            canvas.drawText(line.description.take(40), 80f, currentY, paint)

            val qtyFormatted = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.1f".format(line.quantity)
            canvas.drawText(qtyFormatted, 330f, currentY, paint)
            canvas.drawText(formatPaise(line.unitPricePaise), 410f, currentY, paint)
            canvas.drawText(formatPaise(line.lineTotalPaise), 490f, currentY, paint)

            currentY += 20f
        }

        // Horizontal line under table
        paint.color = Color.rgb(235, 232, 224)
        canvas.drawLine(40f, currentY + 5f, 555f, currentY + 5f, paint)

        // Summary Box (Right aligned)
        val sumBoxLeft = 350f
        var sumY = currentY + 24f

        fun drawSummaryRow(label: String, value: String, isBold: Boolean = false, isAccent: Boolean = false) {
            paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = if (isAccent) 12f else 10f
            paint.color = if (isAccent) Color.rgb(25, 25, 25) else Color.rgb(100, 105, 115)
            canvas.drawText(label, sumBoxLeft, sumY, paint)

            paint.color = if (isAccent) Color.rgb(25, 25, 25) else Color.rgb(25, 25, 25)
            canvas.drawText(value, 490f, sumY, paint)
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
            canvas.drawText("Terms & Conditions:", 40f, currentY + 24f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val termsLines = store.termsAndConditions.split("\n")
            var termY = currentY + 38f
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

    /**
     * Dedicated 80mm Thermal Receipt PDF Generator.
     * Dimensions: Width = 227 pt (approx 80mm roll width), Height = dynamic based on items.
     */
    fun generateInvoice80mmPdf(
        context: Context,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customer: CustomerEntity?,
        store: StoreProfile
    ): File {
        val invoiceDir = File(context.cacheDir, "invoices_80mm")
        if (!invoiceDir.exists()) {
            invoiceDir.mkdirs()
        }

        val fileName = getInvoicePdfFileName(invoice, customer, is80mm = true)
        val pdfFile = File(invoiceDir, fileName)

        // 80mm = ~227 points width. Calculate dynamic height.
        val dynamicHeight = (280 + (lines.size * 22)).coerceAtLeast(360)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(227, dynamicHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawThermal80mmPage(canvas, invoice, lines, customer, store, 227f, dynamicHeight.toFloat())

        document.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return pdfFile
    }

    private fun drawThermal80mmPage(
        canvas: Canvas,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customer: CustomerEntity?,
        store: StoreProfile,
        width: Float,
        height: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        var y = 22f

        // 1. Store Header (Centered)
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(store.storeName, width / 2f, y, paint)
        y += 14f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        if (store.address.isNotBlank()) {
            canvas.drawText(store.address, width / 2f, y, paint)
            y += 11f
        }
        if (store.phone.isNotBlank()) {
            canvas.drawText("Tel: ${store.phone}", width / 2f, y, paint)
            y += 11f
        }

        // Dashed Divider
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 8f
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText("------------------------------------------------", 8f, y, paint)
        y += 12f

        // 2. Invoice Meta Details
        paint.color = Color.BLACK
        paint.textSize = 8f
        canvas.drawText("INV: ${invoice.invoiceNumber}", 8f, y, paint)
        val dateText = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateText, width - 8f, y, paint)
        y += 11f

        paint.textAlign = Paint.Align.LEFT
        val custName = customer?.name ?: "Walk-in Customer"
        canvas.drawText("CUST: $custName", 8f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(invoice.status, width - 8f, y, paint)
        y += 12f

        // Dashed Divider
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText("------------------------------------------------", 8f, y, paint)
        y += 12f

        // 3. Table Header
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8f
        canvas.drawText("ITEM", 8f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTY", 120f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", 168f, y, paint)
        canvas.drawText("TOTAL", width - 8f, y, paint)
        y += 11f

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText("------------------------------------------------", 8f, y, paint)
        y += 12f

        // 4. Line Items
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f

        for (line in lines) {
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            val desc = line.description.take(18)
            canvas.drawText(desc, 8f, y, paint)

            paint.textAlign = Paint.Align.CENTER
            val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.1f".format(line.quantity)
            canvas.drawText(qtyStr, 120f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("%.2f".format(line.unitPricePaise / 100.0), 168f, y, paint)
            canvas.drawText("%.2f".format(line.lineTotalPaise / 100.0), width - 8f, y, paint)

            y += 13f
        }

        // Dashed Divider
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText("------------------------------------------------", 8f, y, paint)
        y += 12f

        // 5. Summary Section
        fun drawRow(label: String, amountPaise: Long, isBold: Boolean = false, prefix: String = "") {
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = if (isBold) 9.5f else 8f
            canvas.drawText(label, 60f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$prefix${formatPaise(amountPaise)}", width - 8f, y, paint)
            y += 12f
        }

        drawRow("Subtotal:", invoice.subtotalPaise)
        if (invoice.discountPaise > 0) {
            drawRow("Discount:", invoice.discountPaise, prefix = "-")
        }
        if (invoice.previousBalancePaise > 0) {
            drawRow("Prev Balance:", invoice.previousBalancePaise, prefix = "+")
        }
        drawRow("NET TOTAL:", invoice.totalPaise, isBold = true)
        drawRow("Paid:", invoice.paidPaise)
        val due = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
        if (due > 0) {
            drawRow("Balance Due:", due, isBold = true)
        }

        // Dashed Divider
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(100, 100, 100)
        canvas.drawText("------------------------------------------------", 8f, y, paint)
        y += 14f

        // 6. Footer
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you! Visit again.", width / 2f, y, paint)
        y += 10f
        paint.textSize = 7f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Printed via BillPro", width / 2f, y, paint)
    }

    /**
     * Prints the invoice in standard 80mm thermal receipt format using Android PrintManager.
     * Compatible with USB, Bluetooth, WiFi, and Android-connected 80mm thermal printers.
     */
    fun printInvoice80mm(
        context: Context,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        customer: CustomerEntity?,
        store: StoreProfile
    ) {
        try {
            val pdf80File = generateInvoice80mmPdf(context, invoice, lines, customer, store)
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service is unavailable on this device", Toast.LENGTH_SHORT).show()
                return
            }

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val fileName = getInvoicePdfFileName(invoice, customer, is80mm = true)
                    val pdi = PrintDocumentInfo.Builder(fileName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback?.onLayoutFinished(pdi, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        FileInputStream(pdf80File).use { input ->
                            FileOutputStream(destination?.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize("ROLL_80MM", "80mm Roll", 3150, 10000))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print("BillPro_${invoice.invoiceNumber}", printAdapter, printAttributes)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Printing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
