package com.hmtracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

/** Potongan teks di watermark. framed = true berarti diberi bingkai dengan warna yang sama. */
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

    /** Menulis watermark berwarna di pojok kanan bawah, di atas kotak gelap semi transparan. */
    fun stamp(src: Bitmap, lines: List<List<Seg>>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val size = maxOf(out.width / 34f, 16f)
        val lineH = size * 1.7f
        val pad = size * 0.5f
        val padH = size * 0.35f
        val gap = size * 0.3f

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(size / 14f, 1.5f)
        }
        val boxBg = Paint().apply { color = Color.argb(150, 0, 0, 0) }

        fun segWidth(s: Seg): Float = text.measureText(s.text) + if (s.framed) padH * 2 else 0f
        fun lineWidth(line: List<Seg>): Float =
            line.fold(0f) { acc, s -> acc + segWidth(s) } + gap * (line.size - 1).coerceAtLeast(0)

        val widest = lines.maxOfOrNull { lineWidth(it) } ?: 0f
        val boxH = lineH * lines.size + pad
        val boxTop = out.height - boxH
        val right = out.width - pad

        canvas.drawRect(
            RectF(right - widest - pad, boxTop, out.width.toFloat(), out.height.toFloat()),
            boxBg
        )

        lines.forEachIndexed { i, line ->
            val baseline = boxTop + pad / 2 + lineH * i + lineH * 0.7f
            var x = right - lineWidth(line)
            line.forEach { s ->
                val color = Color.parseColor(s.color)
                text.color = color
                if (s.framed) {
                    frame.color = color
                    canvas.drawRoundRect(
                        RectF(x, baseline - size * 0.95f, x + segWidth(s), baseline + size * 0.3f),
                        size * 0.25f,
                        size * 0.25f,
                        frame
                    )
                    canvas.drawText(s.text, x + padH, baseline, text)
                } else {
                    canvas.drawText(s.text, x, baseline, text)
                }
                x += segWidth(s) + gap
            }
        }
        return out
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}
