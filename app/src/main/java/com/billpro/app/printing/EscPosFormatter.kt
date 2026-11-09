package com.billpro.app.printing

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale

/**
 * ESC/POS Formatter for Thermal Printers (Standard 58mm and 80mm).
 * 80mm printers have 48 character line width.
 */
object EscPosFormatter {
    private val charset: Charset = Charset.forName("UTF-8")

    fun receipt(storeName: String, invoiceNo: String, lines: List<Pair<String, Long>>, totalPaise: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40)) // initialize
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // center
        out.write(storeName.toByteArray(charset))
        out.write("\nInvoice: $invoiceNo\n".toByteArray(charset))
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // left
        out.write("--------------------------------\n".toByteArray(charset))
        lines.forEach { (name, amountPaise) ->
            out.write("$name  ₹${formatRupees(amountPaise)}\n".toByteArray(charset))
        }
        out.write("--------------------------------\n".toByteArray(charset))
        out.write("TOTAL: ₹${formatRupees(totalPaise)}\n\n\n".toByteArray(charset))
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // cut command
        return out.toByteArray()
    }

    /**
     * Standard 80mm ESC/POS Thermal Receipt (48 columns).
     */
    fun receipt80mm(
        storeName: String,
        storeAddress: String = "",
        storePhone: String = "",
        invoiceNo: String,
        dateStr: String,
        customerName: String,
        lines: List<Triple<String, Double, Long>>, // description, quantity, lineTotalPaise
        subtotalPaise: Long,
        discountPaise: Long = 0L,
        previousBalancePaise: Long = 0L,
        totalPaise: Long,
        paidPaise: Long
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x1B, 0x40)) // initialize
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // center

        // Store name (Bold, Double-Height)
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
        out.write(byteArrayOf(0x1D, 0x21, 0x01)) // Double height
        out.write("$storeName\n".toByteArray(charset))
        out.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal height

        if (storeAddress.isNotBlank()) {
            out.write("$storeAddress\n".toByteArray(charset))
        }
        if (storePhone.isNotBlank()) {
            out.write("Tel: $storePhone\n".toByteArray(charset))
        }
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off

        out.write("------------------------------------------------\n".toByteArray(charset))
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Left align
        out.write("Inv: $invoiceNo   Date: $dateStr\n".toByteArray(charset))
        if (customerName.isNotBlank()) {
            out.write("Customer: $customerName\n".toByteArray(charset))
        }
        out.write("------------------------------------------------\n".toByteArray(charset))
        out.write(String.format(Locale.ENGLISH, "%-22s %5s %8s %10s\n", "ITEM", "QTY", "RATE", "AMOUNT").toByteArray(charset))
        out.write("------------------------------------------------\n".toByteArray(charset))

        for (item in lines) {
            val name = item.first.take(22)
            val qty = if (item.second % 1.0 == 0.0) item.second.toInt().toString() else "%.1f".format(item.second)
            val total = "%.2f".format(item.third / 100.0)
            val rate = if (item.second > 0) "%.2f".format((item.third / item.second) / 100.0) else total
            out.write(String.format(Locale.ENGLISH, "%-22s %5s %8s %10s\n", name, qty, rate, total).toByteArray(charset))
        }

        out.write("------------------------------------------------\n".toByteArray(charset))
        out.write(String.format(Locale.ENGLISH, "%36s %10.2f\n", "Subtotal:", subtotalPaise / 100.0).toByteArray(charset))
        if (discountPaise > 0) {
            out.write(String.format(Locale.ENGLISH, "%36s -%9.2f\n", "Discount:", discountPaise / 100.0).toByteArray(charset))
        }
        if (previousBalancePaise > 0) {
            out.write(String.format(Locale.ENGLISH, "%36s +%9.2f\n", "Prev Balance:", previousBalancePaise / 100.0).toByteArray(charset))
        }

        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
        out.write(String.format(Locale.ENGLISH, "%36s %10.2f\n", "NET TOTAL:", totalPaise / 100.0).toByteArray(charset))
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off
        out.write(String.format(Locale.ENGLISH, "%36s %10.2f\n", "Paid:", paidPaise / 100.0).toByteArray(charset))
        val due = (totalPaise - paidPaise).coerceAtLeast(0)
        if (due > 0) {
            out.write(String.format(Locale.ENGLISH, "%36s %10.2f\n", "Balance Due:", due / 100.0).toByteArray(charset))
        }

        out.write("------------------------------------------------\n".toByteArray(charset))
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
        out.write("Thank you for your business!\n\n\n\n".toByteArray(charset))
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // Paper cut (GS V 0)

        return out.toByteArray()
    }

    private fun formatRupees(paise: Long): String =
        "%d.%02d".format(paise / 100, kotlin.math.abs(paise % 100))
}
