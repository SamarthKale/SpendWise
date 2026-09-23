package com.spendwise.ui.screens.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendwise.ui.components.PlaceholderScreen

/** Tab row with three tabs (pattern 9 in 8.1). Content is filled in Phase 5. */
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    val tabs = listOf("This Month", "Categories", "Trends")
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
            }
        }
        PlaceholderScreen(tabs[selected], "Analytics content arrives in Phase 5.")
    }
}
