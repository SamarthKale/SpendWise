package com.mj.spendwise.ai

import com.mj.spendwise.ml.AnomalyDetector
import com.mj.spendwise.ml.InsightsEngine
import com.mj.spendwise.ml.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MockAiEngineTest {
    private val today = LocalDate.of(2026, 9, 20)
    private val engine = MockAiEngine(File("src/main/assets/chat_intents.json").readText())

    private val expenses = listOf(
        expense(LocalDate.of(2026, 9, 3), 3000.0, "Food"),
        expense(LocalDate.of(2026, 9, 10), 500.0, "Fuel"),
        expense(LocalDate.of(2026, 8, 5), 1000.0, "Food"),
        expense(LocalDate.of(2026, 7, 5), 1000.0, "Food")
    )
    private val ctx = ChatContext(InsightsEngine(expenses, today, 10000.0), AnomalyDetector.detect(expenses, today))

    private fun intentOf(text: String) = engine.respond(text, ctx).intent

    @Test
    fun allTwelveIntentsAnswer() {
        assertEquals("greeting", intentOf("Hello"))
        assertEquals("total_spend", intentOf("How much did I spend this month?"))
        assertEquals("category_spend", intentOf("How much did I spend on food?"))
        assertEquals("top_category", intentOf("What's my top category?"))
        assertEquals("projection", intentOf("What's my projection?"))
        assertEquals("budget_status", intentOf("Will I exceed my budget?"))
        assertEquals("anomaly_check", intentOf("Any unusual spending?"))
        assertEquals("saving_tips", intentOf("Give me saving tips"))
        assertEquals("compare_last_month", intentOf("Compare with last month"))
        assertEquals("navigate_hq", intentOf("Take me to the office"))
        assertEquals("help", intentOf("What can you do?"))
        assertEquals("fallback", intentOf("blah blah xyz"))
    }

    @Test
    fun categoryAnswer_usesRealNumbers() {
        val r = engine.respond("How much did I spend on food?", ctx)
        assertTrue(r.reply, r.reply.contains("₹3,000"))   // Food total in September
        assertTrue(r.reply, r.reply.contains("86%"))       // 3000 of 3500
    }

    @Test
    fun navigateReply_carriesMapAction() {
        assertEquals(ChatActionType.NAVIGATE_MAP, engine.respond("take me to the office", ctx).action)
    }

    @Test
    fun replyIsRealJson_andConfidenceFollowsFormula() {
        val r = engine.respond("hello", ctx) // 1 keyword hit -> 0.55 + 0.15
        assertEquals(0.70, r.confidence, 0.001)
        assertTrue(r.rawJson.trim().startsWith("{") && r.rawJson.contains("\"intent\": \"greeting\""))
    }

    @Test
    fun unknownText_fallsBackWithLowConfidence() {
        assertTrue(engine.respond("qwerty", ctx).confidence < 0.6)
    }

    @Test
    fun wholeWordMatching_hiInsideOtherWordsIsIgnored() {
        assertEquals("fallback", intentOf("which one")) // "hi" appears inside "which"
    }
}
