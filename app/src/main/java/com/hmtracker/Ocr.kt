package com.hmtracker

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class OcrResult(
    val hmCandidates: List<Double>,
    val rawText: String
)

object MeterOcr {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun read(
        bitmap: Bitmap,
        lastHm: Double?,
        onResult: (OcrResult) -> Unit,
        onError: (String) -> Unit
    ) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { onResult(parse(it.text, lastHm)) }
            .addOnFailureListener { onError(it.message ?: "tidak diketahui") }
    }

    /**
     * Mencari angka HM (6 digit, digit persepuluhan ikut) dari teks foto.
     * Kandidat diurutkan: yang paling dekat dengan HM terakhir (dan tidak lebih kecil) di atas.
     */
    fun parse(raw: String, lastHm: Double?): OcrResult {
        // Gabungkan digit yang terpisah spasi atau titik, misal "0 0 9 8 4 5" -> "009845"
        val joined = raw.replace(Regex("(?<=\\d)[ .](?=\\d)"), "")
        val ref = lastHm ?: 0.0

        val candidates = Regex("(?<!\\d)\\d{6}(?!\\d)")
            .findAll(joined)
            .map { it.value.toDouble() / 10.0 }
            .distinct()
            .sortedBy { c -> if (c >= ref) c - ref else 1e9 + (ref - c) }
            .toList()

        return OcrResult(candidates, raw)
    }
}
