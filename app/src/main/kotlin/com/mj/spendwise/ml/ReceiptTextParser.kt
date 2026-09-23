package com.mj.spendwise.ml

data class ParsedReceipt(val merchant: String?, val amount: Double?)

/**
 * Turns raw OCR text into a merchant + total amount using simple rules.
 * The signature parse(rawText): ParsedReceipt is stable, so a trained model could replace the
 * rules later without touching the UI (viva note).
 */
object ReceiptTextParser {

    // A line that is only an amount, e.g. "₹ 1,250.50" or "Rs. 659.00" (safe to pair with a "TOTAL" line above it).
    private val pureAmountLine = Regex("""\s*(?:₹|Rs\.?|INR)?\s*[\d,]+(?:\.\d{1,2})?\s*""", RegexOption.IGNORE_CASE)
    private val totalKeys =listOf("total", "netpayable", "amountdue")
    private val telWords = Regex("""tel|phone|ph\b|mob|gstin|fax""", RegexOption.IGNORE_CASE)
    private val decimalRegex = Regex("""(\d{1,3}(?:,\d{2,3})*\.\d{1,2}|\d+\.\d{1,2})""")

    // 1,234.50 | 659.00 | 659 | with an optional ₹ / Rs. / INR in front
    private val numberRegex = Regex(
        """(?:₹|Rs\.?|INR)?\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val dateRegex = Regex("""\d{1,2}\s*[/\-.]\s*\d{1,2}\s*[/\-.]\s*\d{2,4}""")
    private val addressWords = Regex(
        """sector|road|street|\bst\b|nagar|floor|shop\s*no|plot|phone|\btel\b|gstin|\bgst\b|www\.|@|invoice|receipt|bill\s*no""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedReceipt(findMerchant(lines), findAmount(lines))
    }

    /** First line among the top 5 that isn't mostly digits, a date, or address-like. */
    private fun findMerchant(lines: List<String>): String? {
        val candidate = lines.take(5).firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            val digits = line.count { it.isDigit() }
            letters >= 3 && digits <= letters && !dateRegex.containsMatchIn(line) && !addressWords.containsMatchIn(line)
        } ?: return null
        return niceCase(candidate)
    }

    /** "DOMINO'S PIZZA" -> "Domino's Pizza" (only when the OCR line is all caps). */
    private fun niceCase(s: String): String =
        if (s == s.uppercase()) {
            s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        } else s

    private fun findAmount(lines: List<String>): Double? {
        // 1) A line with total / grand total / amount due / net payable (but not "subtotal").
        //    Spaces are removed before matching because OCR often splits words: "TO TAL".
        for ((i, line) in lines.withIndex()) {
            val squashed = line.lowercase().replace(" ", "")
            if (!totalKeys.any { squashed.contains(it) } || squashed.contains("subtotal")) continue
            // The amount is normally last on the line; OCR sometimes puts it on the next line.
            val next = lines.getOrElse(i + 1) { "" }
            val value = numbersIn(line).lastOrNull()
                ?: if (pureAmountLine.matches(next)) numbersIn(next).firstOrNull() else null
            if (value != null) return value
        }
        // 2) No usable total line. Prefer prices written with decimals ("659.00"): phone numbers, dates
        //    and quantities are usually plain integers. Then fall back to the largest plain number.
        val decimals = lines.flatMap { decimalNumbersIn(it) }
        if (decimals.isNotEmpty()) return decimals.max()
        return lines.filterNot { telWords.containsMatchIn(it) }.flatMap { numbersIn(it) }
            .filter { it < 1_000_000 }.maxOrNull()
    }

    private fun decimalNumbersIn(text: String): List<Double> =
        decimalRegex.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()

    private fun numbersIn(text: String): List<Double> =
        numberRegex.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()
}
