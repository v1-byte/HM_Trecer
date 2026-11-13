package com.hmtracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/** Potongan teks di watermark. Warna dan bingkai diabaikan: watermark dibuat putih dengan bayangan, rata kanan, tanpa kotak latar. */
data class Seg(val text: String, val color: String, val framed: Boolean = false)

object Watermark {

    /** Membaca foto, memperkecil sisi terpanjang ke 2048 px, dan memperbaiki rotasi EXIF. */
    fun decode(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = 2048f / maxOf(w, h)
            if (scale < 1f) {
                decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        }

    /** Menulis watermark putih rata kanan di pojok kanan bawah, seperti contoh foto. */
    fun stamp(src: Bitmap, lines: List<List<Seg>>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val size = maxOf(out.width / 34f, 16f)
        val lineH = size * 1.45f
        val pad = size * 0.6f
        val gap = size * 0.3f

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.WHITE
            setShadowLayer(size * 0.12f, 1f, 1f, Color.BLACK)
        }

        fun lineWidth(line: List<Seg>): Float =
            line.fold(0f) { acc, s -> acc + text.measureText(s.text) } + gap * (line.size - 1).coerceAtLeast(0)

        val blockTop = out.height - pad - lineH * lines.size
        val right = out.width - pad

        lines.forEachIndexed { i, line ->
            val baseline = blockTop + lineH * i + lineH * 0.8f
            var x = right - lineWidth(line)
            line.forEach { s ->
                canvas.drawText(s.text, x, baseline, text)
                x += text.measureText(s.text) + gap
            }
        }
        return out
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}
