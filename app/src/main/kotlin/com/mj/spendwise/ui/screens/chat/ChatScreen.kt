package com.mj.spendwise.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.spendwise.ai.ChatActionType
import com.mj.spendwise.ai.ChatContext
import com.mj.spendwise.ai.ChatMessage
import com.mj.spendwise.ui.components.LoadingState
import com.mj.spendwise.viewmodel.ChatViewModel
import com.mj.spendwise.viewmodel.ExpenseViewModel
import kotlin.math.roundToInt

/** Mock AI assistant: looks like an LLM chat, answers from chat_intents.json + the user's real data. */
@Composable
fun ChatScreen(
    expenseVm: ExpenseViewModel,
    onAction: (ChatActionType) -> Unit,
    modifier: Modifier = Modifier,
    chatVm: ChatViewModel = viewModel()
) {
    val engine by expenseVm.engine.collectAsStateWithLifecycle()
    val anomalies by expenseVm.anomalies.collectAsStateWithLifecycle()
    val messages by chatVm.messages.collectAsStateWithLifecycle()
    val typing by chatVm.typing.collectAsStateWithLifecycle()

    val e = engine
    if (e == null) {
        LoadingState(modifier)
        return
    }
    val ctx = remember(e, anomalies) { ChatContext(e, anomalies) }
    LaunchedEffect(Unit) { chatVm.seedIfNeeded(ctx) }

    var input by rememberSaveable { mutableStateOf("") }
    var jsonShown by rememberSaveable { mutableStateOf(setOf<Long>()) } // message ids whose raw JSON is open
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + if (typing) 0 else -1)
    }
    val lastBotId = messages.lastOrNull { !it.fromUser }?.id

    fun send(text: String) {
        chatVm.send(text, ctx)
        input = ""
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Text(
            "AI Assistant (demo) · answers come from your own data, no external AI",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                Bubble(
                    m, showChips = m.id == lastBotId && !typing,
                    jsonOpen = m.id in jsonShown,
                    onToggleJson = { jsonShown = if (m.id in jsonShown) jsonShown - m.id else jsonShown + m.id },
                    onSuggestion = ::send, onAction = onAction
                )
            }
            if (typing) item { Text("SpendWise AI is thinking…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask about your spending…") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !typing) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun Bubble(
    m: ChatMessage,
    showChips: Boolean,
    jsonOpen: Boolean,
    onToggleJson: () -> Unit,
    onSuggestion: (String) -> Unit,
    onAction: (ChatActionType) -> Unit
) {
    val reply = m.reply
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.text)
                if (reply != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${reply.intent} · ${(reply.confidence * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)
                        )
                        // The presentation hook: reveal the raw JSON the "LLM" returned.
                        IconButton(onClick = onToggleJson) { Icon(Icons.Default.Code, contentDescription = "View raw JSON") }
                    }
                    if (reply.action != ChatActionType.NONE) {
                        Button(onClick = { onAction(reply.action) }) { Text(actionLabel(reply.action)) }
                    }
                }
            }
        }
        if (reply != null && jsonOpen) {
            Card(
                Modifier.padding(top = 4.dp).widthIn(max = 320.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)
            ) {
                Text(
                    reply.rawJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(10.dp)
                )
            }
        }
        if (reply != null && showChips) {
            LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(reply.suggestions) { s -> SuggestionChip(onClick = { onSuggestion(s) }, label = { Text(s) }) }
            }
        }
    }
}

private fun actionLabel(a: ChatActionType) = when (a) {
    ChatActionType.NAVIGATE_MAP -> "Open map & directions"
    ChatActionType.OPEN_ALERTS -> "Open alerts"
    ChatActionType.OPEN_ANALYTICS -> "Open analytics"
    ChatActionType.NONE -> ""
}
