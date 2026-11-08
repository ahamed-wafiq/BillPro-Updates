package com.billpro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GstCalculatorTest {
    @Test
    fun intraStateSplitsTaxIntoCgstAndSgst() {
        val result = GstCalculator.calculate(100_00, 18.0, interState = false)
        assertEquals(9_00L, result.cgstPaise)
        assertEquals(9_00L, result.sgstPaise)
        assertEquals(118_00L, result.totalPaise)
    }

    @Test
    fun interStateUsesIgst() {
        val result = GstCalculator.calculate(100_00, 18.0, interState = true)
        assertEquals(0L, result.cgstPaise)
        assertEquals(0L, result.sgstPaise)
        assertEquals(18_00L, result.igstPaise)
        assertEquals(118_00L, result.totalPaise)
    }

    @Test
    fun zeroPercentGstProducesZeroTax() {
        val result = GstCalculator.calculate(250_00, 0.0, interState = false)
        assertEquals(0L, result.cgstPaise)
        assertEquals(0L, result.sgstPaise)
        assertEquals(0L, result.igstPaise)
        assertEquals(250_00L, result.totalPaise)
    }

    @Test
    fun fivePercentGstHandlesOddPaiseAccurately() {
        // ₹45.55 taxable -> 4555 paise * 0.05 = 227.75 -> 228 paise total tax
        val result = GstCalculator.calculate(45_55, 5.0, interState = false)
        assertEquals(1_14L, result.cgstPaise)
        assertEquals(1_14L, result.sgstPaise)
        assertEquals(2_28L, result.cgstPaise + result.sgstPaise)
        assertEquals(47_83L, result.totalPaise)
    }

    @Test
    fun twentyEightPercentLuxurySlabCalculatesCorrectly() {
        val result = GstCalculator.calculate(1000_00, 28.0, interState = false)
        assertEquals(140_00L, result.cgstPaise)
        assertEquals(140_00L, result.sgstPaise)
        assertEquals(1280_00L, result.totalPaise)
    }
}
