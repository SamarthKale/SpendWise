package com.spendwise.ui.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Modal bottom sheet (pattern 5 in 8.1). Camera/gallery/OCR are wired in Phase 4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScannerSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Scan receipt", style = MaterialTheme.typography.titleLarge)
            Text("OCR arrives in Phase 4.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Text("Camera") }
            OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Text("Gallery") }
            OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Text("Use demo receipt") }
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
