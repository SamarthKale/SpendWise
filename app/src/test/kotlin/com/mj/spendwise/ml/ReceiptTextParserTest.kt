package com.mj.spendwise.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTextParserTest {

    // Same text shape ML Kit returns for the bundled demo receipt.
    private val demoReceipt = """
        DOMINO'S PIZZA
        Sector 17, Vashi, Navi Mumbai
        Tel: 022 2765 4321
        Date: 24/09/2026 Time: 20:41
        Margherita Pizza 1 299.00
        Garlic Bread 1 129.00
        Subtotal 627.00
        GST 5% 31.35
        TOTAL Rs. 659.00
        Paid by UPI. Thank you!
    """.trimIndent()

    @Test
    fun demoReceipt_merchantAndTotal() {
        val r = ReceiptTextParser.parse(demoReceipt)
        assertEquals("Domino's Pizza", r.merchant)
        assertEquals(659.0, r.amount!!, 0.001) // TOTAL wins over the subtotal 627.00
    }

    @Test
    fun realOcrOutput_columnsSplitAndTotalSpaced() {
        // Actual ML Kit output for the demo receipt: labels and numbers come back as separate blocks.
        val raw = """
            DOMINO'S PIZZA
            Sector 17, Vashi, Navi Mumbai
            Tel: 022 2765 4321
            Date: 24/09/2026
            Margherita Pizza
            Subtotal
            GST 5%
            Round off
            TO TAL
            Time: 20:41
            1
            2
            299.00
            627.00
            31.35
            0.65
            Rs. 659.00
        """.trimIndent()
        val r = ReceiptTextParser.parse(raw)
        assertEquals("Domino's Pizza", r.merchant)
        assertEquals(659.0, r.amount!!, 0.001) // phone number 4321 must not win
    }

    @Test
    fun spacedTotalKeyword_onSameRow() {
        assertEquals(659.0, ReceiptTextParser.parse("Shop\nTO TAL Rs. 659.00").amount!!, 0.001)
    }

    @Test
    fun totalOnNextLine_isFound() {
        val r = ReceiptTextParser.parse("Cafe Coffee Day\nGrand Total\n₹ 1,250.50")
        assertEquals(1250.50, r.amount!!, 0.001)
    }

    @Test
    fun commasAndRupeeSymbol() {
        assertEquals(12500.0, ReceiptTextParser.parse("Shop\nAmount Due: ₹12,500").amount!!, 0.001)
    }

    @Test
    fun noTotalKeyword_usesLargestNumber() {
        val r = ReceiptTextParser.parse("Local Store\nMilk 60.00\nRice 480.00\nOil 220.00")
        assertEquals(480.0, r.amount!!, 0.001)
    }

    @Test
    fun merchantSkipsDatesNumbersAndAddresses() {
        val r = ReceiptTextParser.parse("24/09/2026\n12345\nSector 9A, Vashi\nBig Bazaar\nTOTAL 100")
        assertEquals("Big Bazaar", r.merchant)
    }

    @Test
    fun emptyText_givesNulls() {
        val r = ReceiptTextParser.parse("")
        assertNull(r.merchant)
        assertNull(r.amount)
    }
}
