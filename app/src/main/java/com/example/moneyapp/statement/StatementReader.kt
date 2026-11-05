package com.hiroshi.moneyapp.statement

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The PDF is locked: ask for the password (many banks protect e-statements) and call again. */
class PasswordRequiredException : Exception()

/**
 * Turns a PDF or image into text lines, entirely on the device.
 * Real PDFs give their text directly (exact digits); screenshots and scanned PDFs go through OCR.
 * Nothing is stored here, including the password.
 */
class StatementReader(private val context: Context, private val ocr: OcrReader) {

    suspend fun readLines(uri: Uri, password: String?): List<String> = withContext(Dispatchers.IO) {
        if (context.contentResolver.getType(uri) == "application/pdf") readPdf(uri, password) else ocr.readImage(uri)
    }

    private fun readPdf(uri: Uri, password: String?): List<String> {
        PDFBoxResourceLoader.init(context)
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "can't open file" }
        val document = try {
            input.use { PDDocument.load(it, password.orEmpty()) }
        } catch (_: InvalidPasswordException) {
            throw PasswordRequiredException()
        }
        return document.use { doc ->
            val text = PDFTextStripper().apply { setSortByPosition(true) }.getText(doc)
            val enoughText = text.count(Char::isLetterOrDigit) >= doc.numberOfPages * MIN_CHARS_PER_PAGE
            if (enoughText) text.lines() else ocrPages(doc)
        }
    }

    /** A PDF without a text layer is just pictures of pages. */
    private fun ocrPages(doc: PDDocument): List<String> {
        val renderer = PDFRenderer(doc)
        return (0 until minOf(doc.numberOfPages, MAX_OCR_PAGES)).flatMap { page ->
            val bitmap = renderer.renderImage(page, OCR_SCALE)
            try {
                ocr.read(InputImage.fromBitmap(bitmap, 0))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val MIN_CHARS_PER_PAGE = 50
        const val MAX_OCR_PAGES = 30
        const val OCR_SCALE = 2f
    }
}
