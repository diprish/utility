package com.diprish.utilitymeter.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of running OCR on a meter photo.
 *
 * @param reading the best-guess numeric reading, or null if nothing usable was found
 * @param rawText the full block of recognised text, useful for debugging / manual pick
 */
data class OcrResult(
    val reading: Double?,
    val rawText: String,
)

/**
 * Runs Google ML Kit's on-device Latin text recognizer over a captured photo and
 * tries to pull out the meter reading.
 *
 * Meter displays vary a lot, so rather than trust a single line we collect every
 * numeric token in the image and pick the most "reading-like" one: the longest
 * run of digits (optionally with a decimal separator). The caller always shows
 * the value for confirmation before it is saved.
 */
object MeterOcr {

    // Created lazily so the pure text-parsing helpers can be unit-tested on the
    // JVM without pulling in the Android-only ML Kit client.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Matches sequences such as 001234, 12345.6, 4 321 (spaces later stripped).
    private val numberRegex = Regex("""\d[\d.,\s]*\d|\d""")

    suspend fun recognize(context: Context, photoUri: Uri): OcrResult {
        val image = InputImage.fromFilePath(context, photoUri)
        val text = recognizer.process(image).await()
        val candidates = extractCandidates(text.text)
        return OcrResult(reading = pickBest(candidates), rawText = text.text)
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
