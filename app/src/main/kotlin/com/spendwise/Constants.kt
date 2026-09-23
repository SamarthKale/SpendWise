package com.spendwise

import androidx.compose.ui.graphics.Color

// Fake company office, used by the Map screen (Phase 6). Tweak the coordinates if you like.
const val HQ_NAME = "SpendWise HQ (Demo Office)"
const val HQ_ADDRESS = "Sector 9A, Vashi, Navi Mumbai, Maharashtra 400703"
const val HQ_LAT = 19.0745
const val HQ_LNG = 72.9985

/** The 8 expense categories from CLAUDE.md 6.1. */
val CATEGORIES = listOf(
    "Food", "Groceries", "Fuel", "Shopping", "Travel", "Bills", "Entertainment", "Health"
)

/** Category colours are used for chips, list dots and map markers. */
val CATEGORY_COLORS: Map<String, Color> = mapOf(
    "Food" to Color(0xFFEF6C00),
    "Groceries" to Color(0xFF43A047),
    "Fuel" to Color(0xFF6D4C41),
    "Shopping" to Color(0xFF8E24AA),
    "Travel" to Color(0xFF1E88E5),
    "Bills" to Color(0xFF546E7A),
    "Entertainment" to Color(0xFFE91E63),
    "Health" to Color(0xFFE53935)
)

fun categoryColor(category: String?): Color = CATEGORY_COLORS[category] ?: Color.Gray
