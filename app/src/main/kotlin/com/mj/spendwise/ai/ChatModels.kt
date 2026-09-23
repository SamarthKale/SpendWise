package com.mj.spendwise.ai

import com.mj.spendwise.ml.Anomaly
import com.mj.spendwise.ml.InsightsEngine

/** What a bot reply can ask the UI to do; the UI shows it as a button chip. */
enum class ChatActionType { NONE, NAVIGATE_MAP, OPEN_ALERTS, OPEN_ANALYTICS }

/** Parsed bot reply. [rawJson] is the exact JSON the "{ }" button shows. */
data class ChatReply(
    val intent: String,
    val confidence: Double,
    val reply: String,
    val action: ChatActionType,
    val suggestions: List<String>,
    val rawJson: String
)

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val reply: ChatReply? = null)

/** Real numbers the bot answers from (same engines as the dashboard). */
class ChatContext(val engine: InsightsEngine, val anomalies: List<Anomaly>)
