package com.mj.spendwise.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, bundled model: works offline). Never blocks the main thread. */
object OcrEngine {
    suspend fun recognize(context: Context, uri: Uri): String {
        // Decoding the image is heavy, so do it off the main thread.
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        return suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    cont.resume(toRows(result))
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    cont.resumeWithException(e)
                }
            cont.invokeOnCancellation { recognizer.close() }
        }
    }

    /**
     * ML Kit returns text in blocks (a receipt's label column and price column often come back as
     * separate blocks). We put lines that sit at the same height back on one row, so
     * "TOTAL" and "Rs. 659.00" become "TOTAL Rs. 659.00" again.
     */
    private fun toRows(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return result.text

        val sorted = lines.sortedBy { it.boundingBox!!.exactCenterY() }
        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in sorted) {
            val box = line.boundingBox!!
            val row = rows.lastOrNull()
            val rowBox = row?.first()?.boundingBox
            // Same row when the vertical centres are closer than 60% of the line height.
            if (row != null && rowBox != null && kotlin.math.abs(box.exactCenterY() - rowBox.exactCenterY()) < 0.6f * box.height()) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text }
        }
    }
}
