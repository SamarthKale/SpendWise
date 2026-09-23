package com.spendwise.data

import android.content.Context
import com.spendwise.backend.Expense
import com.spendwise.util.toTimestamp
import org.json.JSONArray
import java.time.LocalDate

/**
 * Loads assets/demo_expenses.json. Each row has "d" = days ago, so timestamps are shifted to
 * "now" at load time and the analytics always look real, whenever the demo is run.
 */
object DemoData {
    fun load(context: Context): List<Expense> {
        val json = context.assets.open("demo_expenses.json").bufferedReader().use { it.readText() }
        val rows = JSONArray(json)
        val today = LocalDate.now()
        return List(rows.length()) { i ->
            val r = rows.getJSONObject(i)
            Expense().apply {
                id = "demo_%02d".format(i) // fixed ids: reseeding overwrites instead of duplicating
                merchant = r.getString("m")
                category = r.getString("c")
                amount = r.getDouble("a")
                notes = r.optString("n", "")
                locationName = r.optString("loc", "")
                if (r.has("lat")) {
                    latitude = r.getDouble("lat")
                    longitude = r.getDouble("lng")
                }
                timestamp = today.minusDays(r.getLong("d")).atTime(r.optInt("h", 12), 0).toTimestamp()
                source = "manual"
            }
        }
    }
}
