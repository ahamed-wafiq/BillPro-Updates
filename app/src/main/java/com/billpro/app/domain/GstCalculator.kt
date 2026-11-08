package com.billpro.app.domain

import kotlin.math.roundToLong

/**
 * Amounts are integer paise to avoid floating-point currency rounding errors.
 * Assumes unit prices are tax-exclusive. Caller must determine place-of-supply
 * and tax treatment correctly for the transaction.
 */
data class GstBreakdown(
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long,
    val totalPaise: Long
)

object GstCalculator {
    fun calculate(
        taxablePaise: Long,
        gstRatePercent: Double,
        interState: Boolean
    ): GstBreakdown {
        require(taxablePaise >= 0) { "Taxable amount cannot be negative" }
        require(gstRatePercent >= 0.0) { "GST rate cannot be negative" }

        val totalTax = (taxablePaise * gstRatePercent / 100.0).roundToLong()
        return if (interState) {
            GstBreakdown(taxablePaise, 0, 0, totalTax, taxablePaise + totalTax)
        } else {
            val cgst = totalTax / 2
            val sgst = totalTax - cgst
            GstBreakdown(taxablePaise, cgst, sgst, 0, taxablePaise + totalTax)
        }
    }
}
