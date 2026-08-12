package com.diprish.utilitymeter.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of running OCR on a meter photo.
 *
 * @param reading the best-guess numeric reading, or null if nothing usable was found
 * @param rawText the full block of recognised text inside the target box
 */
data class OcrResult(
    val reading: Double?,
    val rawText: String,
)

/**
 * Google ML Kit on-device text recognition, restricted to the reticle region
 * of the photo so only the framed meter digits are read — not serial numbers,
 * tariff labels, or anything else visible in the frame.
 *
 * The captured JPEG is loaded upright (respecting EXIF orientation), cropped to
 * [CaptureReticle], and only that crop is handed to the recognizer.
 */
object MeterOcr {

    // Created lazily so the pure text-parsing helpers can be unit-tested on the
    // JVM without pulling in the Android-only ML Kit client.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Matches sequences such as 001234, 12345.6, 4 321 (spaces later stripped).
    private val numberRegex = Regex("""\d[\d.,\s]*\d|\d""")

    suspend fun recognize(photoFile: File): OcrResult {
        val crop = withContext(Dispatchers.IO) { loadReticleCrop(photoFile) }
            ?: return OcrResult(reading = null, rawText = "")
        val text = recognizer.process(InputImage.fromBitmap(crop, 0)).await()
        val candidates = extractCandidates(text.text)
        return OcrResult(reading = pickBest(candidates), rawText = text.text)
    }

    /** Loads the photo upright and crops it to the reticle band. */
    private fun loadReticleCrop(file: File): Bitmap? {
        val upright = loadUprightBitmap(file) ?: return null
        val left = (upright.width * CaptureReticle.LEFT).toInt().coerceIn(0, upright.width - 1)
        val top = (upright.height * CaptureReticle.TOP).toInt().coerceIn(0, upright.height - 1)
        val right = (upright.width * CaptureReticle.RIGHT).toInt().coerceIn(left + 1, upright.width)
        val bottom = (upright.height * CaptureReticle.BOTTOM).toInt().coerceIn(top + 1, upright.height)
        return Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
    }

    /** Decodes the JPEG and rotates it upright according to its EXIF orientation. */
    private fun loadUprightBitmap(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Exposed for unit testing the number-selection heuristic. */
    fun extractCandidates(raw: String): List<Double> =
        numberRegex.findAll(raw)
            .mapNotNull { normalize(it.value) }
            .toList()

    /**
     * Normalise a raw token into a Double. Handles thousands separators and
     * both '.' and ',' as the decimal mark, and drops stray spaces that OCR
     * inserts between digits.
     */
    private fun normalize(token: String): Double? {
        val compact = token.replace(" ", "")
        if (compact.isEmpty()) return null

        val lastDot = compact.lastIndexOf('.')
        val lastComma = compact.lastIndexOf(',')
        val decimalIndex = maxOf(lastDot, lastComma)

        val cleaned = if (decimalIndex >= 0) {
            val intPart = compact.substring(0, decimalIndex).filter { it.isDigit() }
            val fracPart = compact.substring(decimalIndex + 1).filter { it.isDigit() }
            if (fracPart.isEmpty()) intPart else "$intPart.$fracPart"
        } else {
            compact.filter { it.isDigit() }
        }
        return cleaned.toDoubleOrNull()
    }

    /**
     * Pick the candidate most likely to be the meter reading: prefer the token
     * with the most digits (meter dials show many), breaking ties by value.
     */
    private fun pickBest(candidates: List<Double>): Double? =
        candidates.maxWithOrNull(
            compareBy({ digitCount(it) }, { it })
        )

    private fun digitCount(value: Double): Int =
        value.toLong().toString().length +
            (value.toString().substringAfter('.', "").takeIf { it != "0" }?.length ?: 0)
}

/** Bridges an ML Kit [com.google.android.gms.tasks.Task] into a coroutine. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
