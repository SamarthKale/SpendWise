package com.mj.spendwise.ai

import android.content.Context
import com.mj.spendwise.util.formatInr
import com.mj.spendwise.util.formatInrWhole
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A mock "LLM": no network call anywhere. It matches the user's words against keyword lists from
 * assets/chat_intents.json, fills the answer template with REAL numbers from the user's data, builds the
 * reply as JSON and parses it back (so the JSON path is genuinely exercised), exactly like an API client would.
 */
class MockAiEngine(intentsJson: String) {

    private data class Intent(
        val name: String,
        val keywords: List<String>,
        val template: String,
        val templateNoCategory: String?,
        val action: String,
        val suggestions: List<String>
    )

    private val intents: List<Intent> = JSONObject(intentsJson).getJSONArray("intents").let { arr ->
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Intent(
                o.getString("name"), o.getJSONArray("keywords").toStrings(), o.getString("template"),
                o.optString("templateNoCategory").ifBlank { null }, o.optString("action", "NONE"),
                o.getJSONArray("suggestions").toStrings()
            )
        }
    }
    private val fallback = intents.first { it.name == "fallback" }

    /** Steps: normalise -> score intents by keyword hits -> confidence -> fill template -> JSON -> parse. */
    fun respond(userText: String, ctx: ChatContext): ChatReply {
        val padded = " ${normalize(userText)} "
        val category = detectCategory(padded)

        var best: Intent? = null
        var bestHits = 0
        var bestLongest = 0
        for (intent in intents) {
            if (intent === fallback) continue
            val matched = intent.keywords.filter { padded.contains(" ${normalize(it)} ") }
            var hits = matched.size
            if (intent.name == "category_spend" && category != null) hits += 2 // "food" etc. is a strong signal
            val longest = matched.maxOfOrNull { it.length } ?: 0 // longest phrase wins ties
            if (hits > bestHits || (hits == bestHits && hits > 0 && longest > bestLongest)) {
                best = intent; bestHits = hits; bestLongest = longest
            }
        }
        val confidence = min(0.99, 0.55 + 0.15 * bestHits)
        val chosen = if (best == null || confidence < 0.6) fallback else best
        val shownConfidence = if (chosen === fallback) 0.55 else confidence

        val values = placeholders(ctx, category)
        val template = if (chosen.name == "category_spend" && category == null) chosen.templateNoCategory ?: chosen.template else chosen.template
        // Both braces are escaped: Android's regex engine rejects a bare "}" (the JVM used by unit tests accepts it).
        val reply = Regex("\\{(\\w+)\\}").replace(template) { values[it.groupValues[1]] ?: "" }

        val json = JSONObject().apply {
            put("intent", chosen.name)
            put("confidence", (shownConfidence * 100).roundToInt() / 100.0)
            put("reply", reply)
            put("data", dataFor(chosen.name, ctx, category))
            put("action", JSONObject().put("type", chosen.action))
            put("suggestions", JSONArray(chosen.suggestions))
        }
        return parseReply(json.toString(2))
    }

    /** The "client side": turns the JSON text back into a typed object. */
    fun parseReply(jsonText: String): ChatReply {
        val o = JSONObject(jsonText)
        val actionName = o.optJSONObject("action")?.optString("type", "NONE") ?: "NONE"
        return ChatReply(
            intent = o.getString("intent"),
            confidence = o.getDouble("confidence"),
            reply = o.getString("reply"),
            action = runCatching { ChatActionType.valueOf(actionName) }.getOrDefault(ChatActionType.NONE),
            suggestions = o.optJSONArray("suggestions")?.toStrings().orEmpty(),
            rawJson = jsonText
        )
    }

    // ---------- helpers ----------

    private fun normalize(s: String) =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val categoryAliases = mapOf(
        "Food" to listOf("food", "eating", "restaurant", "restaurants", "dining", "swiggy", "zomato", "meals"),
        "Groceries" to listOf("groceries", "grocery", "vegetables"),
        "Fuel" to listOf("fuel", "petrol", "diesel"),
        "Shopping" to listOf("shopping", "clothes", "amazon"),
        "Travel" to listOf("travel", "uber", "cab", "transport", "commute"),
        "Bills" to listOf("bills", "bill", "electricity", "recharge"),
        "Entertainment" to listOf("entertainment", "movies", "movie", "netflix"),
        "Health" to listOf("health", "medical", "medicine", "pharmacy", "doctor")
    )

    private fun detectCategory(paddedText: String): String? =
        categoryAliases.entries.firstOrNull { (_, words) -> words.any { paddedText.contains(" $it ") } }?.key

    private val tips = mapOf(
        "Food" to "Try cooking at home a few more days a week; food delivery adds up fast.",
        "Groceries" to "Plan a weekly list and buy staples in bulk to cut impulse grocery orders.",
        "Fuel" to "Combine errands into one trip and keep tyre pressure right to save fuel.",
        "Shopping" to "Use a 48-hour rule for non-essential purchases before you buy.",
        "Travel" to "Shared rides or the metro for regular routes can cost much less than cabs.",
        "Bills" to "Review recharge plans and subscriptions; switch to the cheapest plan that fits your use.",
        "Entertainment" to "Share subscriptions or pause the ones you barely use this month.",
        "Health" to "Generic medicines and preventive check-ups can lower long-term health costs."
    )

    private fun placeholders(ctx: ChatContext, category: String?): Map<String, String> {
        val e = ctx.engine
        val total = e.monthTotal()
        val totals = e.categoryTotals()
        val top = totals.entries.firstOrNull()
        val projected = e.projectedMonthEnd()
        val diff = projected - e.monthlyBudget
        val spentOfCategory = category?.let { totals[it] ?: 0.0 } ?: 0.0
        fun pct(x: Double) = if (total > 0) (x / total * 100).roundToInt() else 0

        val verdict = if (diff > 0) "about ${formatInrWhole(diff)} over your budget" else "within your budget"
        val remaining = e.monthlyBudget - total
        val budgetMessage = buildString {
            append("You've used ${(e.budgetUsedFraction() * 100).roundToInt()}% of your ${formatInr(e.monthlyBudget)} budget (${formatInr(total)} spent). ")
            append(if (remaining >= 0) "${formatInr(remaining)} left. " else "You're already ${formatInr(-remaining)} over. ")
            append(
                if (diff > 0) "At this pace you'll likely exceed it by ${formatInrWhole(diff)}."
                else "At this pace you should stay within budget."
            )
        }
        val anomalyMessage = if (ctx.anomalies.isEmpty()) "No unusual spending detected. Everything looks normal."
        else "Yes, I noticed: " + ctx.anomalies.take(2).joinToString(" Also: ") { a ->
            if (a.kind == "transaction") "${a.merchant ?: "an expense"} (${a.category}) of ${formatInr(a.currentValue)} is unusually large."
            else "${a.category} is ${formatInr(a.currentValue)} vs a usual ${formatInrWhole(a.expectedValue)} per month."
        }
        val mom = e.monthOverMonthPercent()
        val compare = if (mom == null) "There's no spending last month to compare with."
        else "You've spent ${abs(mom).roundToInt()}% ${if (mom >= 0) "more" else "less"} than in the same days last month " +
            "(${formatInr(e.monthTotal())} vs ${formatInr(e.previousMonthSamePeriod())})."
        val tip = top?.let { "${it.key} is your biggest spend (${pct(it.value)}% of the month). ${tips[it.key].orEmpty()}" }
            ?: "Add a few expenses first and I'll suggest where to save."

        return mapOf(
            "monthTotal" to formatInr(total),
            "amount" to formatInr(spentOfCategory), "category" to (category ?: ""), "pct" to pct(spentOfCategory).toString(),
            "breakdown" to totals.entries.take(3).joinToString(", ") { "${it.key} ${formatInr(it.value)} (${pct(it.value)}%)" }.ifBlank { "nothing spent yet" },
            "topCategory" to (top?.key ?: "n/a"), "topAmount" to formatInr(top?.value ?: 0.0), "topPct" to pct(top?.value ?: 0.0).toString(),
            "projected" to formatInrWhole(projected), "budgetVerdict" to verdict,
            "budgetMessage" to budgetMessage, "anomalyMessage" to anomalyMessage, "tip" to tip, "compareMessage" to compare
        )
    }

    /** The "data" block of the JSON reply: the raw numbers behind the sentence. */
    private fun dataFor(intent: String, ctx: ChatContext, category: String?): JSONObject {
        val e = ctx.engine
        val o = JSONObject()
        when (intent) {
            "total_spend" -> o.put("monthTotal", e.monthTotal()).put("period", "month")
            "category_spend" -> if (category != null) {
                o.put("category", category).put("amount", e.categoryTotals()[category] ?: 0.0).put("period", "month")
            } else {
                e.categoryTotals().forEach { (k, v) -> o.put(k, v) }
            }
            "top_category" -> e.categoryTotals().entries.firstOrNull()?.let { o.put("category", it.key).put("amount", it.value) }
            "projection", "budget_status" -> o.put("projected", e.projectedMonthEnd()).put("budget", e.monthlyBudget)
                .put("spent", e.monthTotal())
            "anomaly_check" -> o.put("count", ctx.anomalies.size)
            "compare_last_month" -> o.put("thisMonth", e.monthTotal()).put("lastMonthSamePeriod", e.previousMonthSamePeriod())
                .put("percent", e.monthOverMonthPercent() ?: JSONObject.NULL)
        }
        return o
    }

    private fun JSONArray.toStrings(): List<String> = List(length()) { getString(it) }

    companion object {
        fun fromAssets(context: Context) = MockAiEngine(
            context.assets.open("chat_intents.json").bufferedReader().use { it.readText() }
        )
    }
}
