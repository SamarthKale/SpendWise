package com.mj.spendwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mj.spendwise.util.compactInr

/**
 * Simple bar chart built from boxes (no chart library). Values are real data; the tallest bar fills the height.
 * @param bars label + value pairs, left to right
 * @param highlightIndex bar drawn in the accent colour (e.g. today)
 */
@Composable
fun BarChart(
    bars: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    chartHeight: Int = 140
) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val normal = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val accent = MaterialTheme.colorScheme.primary

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        bars.forEachIndexed { index, (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (value > 0) compactInr(value) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Box(Modifier.height(chartHeight.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    // Even a zero value gets a hairline so the axis is visible.
                    val fraction = (value / max).toFloat().coerceIn(0.01f, 1f)
                    Box(
                        Modifier.fillMaxWidth(0.7f).fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (index == highlightIndex) accent else normal)
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Unspecified)
            }
        }
    }
}
