package com.mj.spendwise.ml

/**
 * Keyword classifier: merchant/notes -> one of the 8 categories, or null when nothing matches.
 * Not a trained model (honest label for the viva). classify(merchant, notes) keeps a stable
 * signature so an ML model can replace this class later.
 */
object CategoryClassifier {

    private val keywords: Map<String, List<String>> = linkedMapOf(
        "Food" to listOf(
            "swiggy", "zomato", "domino", "mcdonald", "starbucks", "cafe", "restaurant", "coffee",
            "pizza", "burger", "kitchen", "dhaba", "bakery", "kfc", "subway"
        ),
        "Groceries" to listOf("bigbasket", "d-mart", "dmart", "reliance fresh", "blinkit", "zepto", "supermarket", "grocery"),
        "Fuel" to listOf("hp", "indian oil", "bharat petroleum", "shell", "petrol", "fuel", "diesel"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "zara", "mall", "fashion"),
        "Travel" to listOf("uber", "ola", "irctc", "makemytrip", "metro", "rapido", "railway", "flight", "cab"),
        "Bills" to listOf("electricity", "airtel", "jio", "vi", "recharge", "bill", "broadband", "msedcl"),
        "Entertainment" to listOf("netflix", "pvr", "bookmyshow", "spotify", "cinema", "hotstar", "prime video"),
        "Health" to listOf("apollo", "pharmacy", "medplus", "clinic", "hospital", "medical", "diagnostic")
    )

    // Short keywords (<= 3 letters) must match a whole word, otherwise "vi" would match "Vivek".
    private val shortWordRegexes: Map<String, Regex> = keywords.values.flatten()
        .filter { it.length <= 3 }.associateWith { Regex("""\b${Regex.escape(it)}\b""") }

    fun classify(merchant: String?, notes: String? = null): String? {
        val text = "${merchant.orEmpty()} ${notes.orEmpty()}".lowercase().replace("’", "'")
        if (text.isBlank()) return null
        for ((category, words) in keywords) {
            if (words.any { matches(text, it) }) return category
        }
        return null
    }

    private fun matches(text: String, keyword: String): Boolean =
        shortWordRegexes[keyword]?.containsMatchIn(text) ?: text.contains(keyword)
}
