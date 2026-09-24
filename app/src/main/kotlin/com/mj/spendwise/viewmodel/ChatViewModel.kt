package com.mj.spendwise.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mj.spendwise.ai.ChatContext
import com.mj.spendwise.ai.ChatMessage
import com.mj.spendwise.ai.MockAiEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Chat history lives only in memory (this ViewModel), as specified. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = MockAiEngine.fromAssets(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    private var nextId = 0L
    private var seeded = false

    /** Sign-out: the conversation contained the previous user's numbers, so start fresh. */
    fun reset() {
        _messages.value = emptyList()
        _typing.value = false
        seeded = false
    }

    /** First open: greeting, "Try asking…" chips, and a sample question with a real answer. */
    fun seedIfNeeded(ctx: ChatContext) {
        if (seeded) return
        seeded = true
        val greeting = engine.respond("hello", ctx)
        val help = engine.respond("help", ctx).copy(
            reply = "Try asking…",
            suggestions = listOf("How much did I spend on food?", "Will I exceed my budget?", "Take me to the office")
        )
        val sampleQ = "Will I exceed my budget?"
        _messages.value = listOf(
            bot(greeting.reply, greeting),
            bot(help.reply, help),
            ChatMessage(nextId++, true, sampleQ),
            engine.respond(sampleQ, ctx).let { bot(it.reply, it) }
        )
    }

    /** Adds the user's message, shows the typing indicator for 0.6-1.2 s, then adds the bot's reply. */
    fun send(text: String, ctx: ChatContext) {
        val question = text.trim()
        if (question.isEmpty() || _typing.value) return
        _messages.value = _messages.value + ChatMessage(nextId++, true, question)
        _typing.value = true
        viewModelScope.launch {
            delay(Random.nextLong(600, 1200)) // fake latency so it feels like an LLM
            val reply = engine.respond(question, ctx)
            _messages.value = _messages.value + bot(reply.reply, reply)
            _typing.value = false
        }
    }

    private fun bot(text: String, reply: com.mj.spendwise.ai.ChatReply) = ChatMessage(nextId++, false, text, reply)
}
