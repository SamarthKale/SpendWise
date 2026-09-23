package com.mj.spendwise.ui.screens.scan

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.mj.spendwise.R
import com.mj.spendwise.ml.CategoryClassifier
import com.mj.spendwise.ml.OcrEngine
import com.mj.spendwise.ml.ReceiptTextParser
import com.mj.spendwise.util.formatInr
import kotlinx.coroutines.launch
import java.io.File

/** What the sheet hands back to the Add screen when the user taps Confirm. */
data class ScanResult(val merchant: String?, val amount: Double?, val category: String?)

/**
 * Modal bottom sheet (pattern 5 in 8.1). Pipeline:
 * image (camera / gallery / demo) -> ML Kit OCR -> ReceiptTextParser -> CategoryClassifier -> Confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScannerSheet(onDismiss: () -> Unit, onConfirm: (ScanResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var ocrText by rememberSaveable { mutableStateOf<String?>(null) }
    var merchant by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf<Double?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showRaw by rememberSaveable { mutableStateOf(false) }

    // Runs OCR + parsing + classification for any image source.
    fun process(uri: Uri) {
        imageUri = uri
        busy = true
        error = null
        scope.launch {
            try {
                val text = OcrEngine.recognize(context, uri)
                val parsed = ReceiptTextParser.parse(text)
                ocrText = text
                merchant = parsed.merchant
                amount = parsed.amount
                category = CategoryClassifier.classify(parsed.merchant, null)
                if (text.isBlank()) error = "No text found in this image. Try a clearer photo."
            } catch (e: Exception) {
                error = "Could not read the image (${e.message ?: "unknown error"})."
            } finally {
                busy = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) process(uri) else if (!ok) error = "Photo cancelled."
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) process(uri)
    }

    fun openCamera() {
        try {
            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File.createTempFile("receipt_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            error = "No camera app available. Use Gallery or the demo receipt."
        } catch (e: Exception) {
            error = "Camera unavailable (${e.message}). Use Gallery or the demo receipt."
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Scan receipt", style = MaterialTheme.typography.titleLarge)
            Text(
                "On-device OCR (ML Kit) reads the receipt; nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openCamera() }) { Text("Camera") }
                OutlinedButton(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
                Button(onClick = {
                    // Bundled sample receipt: guarantees the demo works without a camera.
                    process(Uri.parse("android.resource://${context.packageName}/${R.drawable.demo_receipt}"))
                }) { Text("Use demo receipt") }
            }

            imageUri?.let {
                AsyncImage(
                    model = it, contentDescription = "Receipt preview",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                    Text("Reading receipt…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (ocrText != null && !busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Merchant: ${merchant ?: "—"}") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Amount: ${amount?.let { formatInr(it) } ?: "—"}") })
                    AssistChip(onClick = {}, label = { Text("Category: ${category ?: "—"}") })
                }
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide raw OCR text" else "Show raw OCR text")
                }
                if (showRaw) {
                    Text(
                        ocrText.orEmpty(), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { onConfirm(ScanResult(merchant, amount, category)) },
                    enabled = merchant != null || amount != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirm and fill the form") }
            }
            TextButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}
