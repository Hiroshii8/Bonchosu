package com.example.moneyapp.statement

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR (ML Kit): statements and receipts both go through here. Blocking, so call it off the main thread. */
class OcrReader(private val context: Context) {

    fun readImage(uri: Uri): List<String> = read(InputImage.fromFilePath(context, uri))

    fun read(image: InputImage): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = Tasks.await(recognizer.process(image))
            val boxes = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                StatementParser.TextBox(line.text, box.left, box.centerY(), box.height())
            }
            StatementParser.groupIntoLines(boxes)
        } finally {
            recognizer.close()
        }
    }
}
