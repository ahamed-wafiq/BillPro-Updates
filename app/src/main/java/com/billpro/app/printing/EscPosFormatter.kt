package com.billpro.app.printing

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Small ESC/POS formatter scaffold. Printer compatibility varies by model.
 * Pair/connect to a printer with Android Bluetooth APIs and request the
 * required runtime permission before writing these bytes to its socket.
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
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // cut command; some printers ignore it
        return out.toByteArray()
    }

    private fun formatRupees(paise: Long): String =
        "%d.%02d".format(paise / 100, kotlin.math.abs(paise % 100))
}
