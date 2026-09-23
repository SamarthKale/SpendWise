package com.mj.spendwise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mj.spendwise.ml.Insight

/** One "AI insight" card (rule/statistics driven, labelled honestly in the UI). */
@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    val container = when (insight.severity) {
        "warning", "critical" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Text(insight.title, style = MaterialTheme.typography.titleSmall)
            Text(insight.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
