package ru.platezhi2.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Уменьшение картинок-значков.
 *
 * Значок в списке платежей мелкий, поэтому хранить и пересылать исходный
 * многомегабайтный файл незачем: уменьшенная копия быстрее синхронизируется
 * и точно помещается в облачную копию.
 */
object ImageScaling {

    /** Максимальная длина стороны сохранённого значка, точки. */
    const val MAX_SIDE = 512

    /**
     * Уменьшает картинку до [maxSide] по длинной стороне и возвращает PNG
     * (PNG сохраняет прозрачность логотипов). null — если картинку прочитать не удалось.
     */
    fun shrink(bytes: ByteArray, maxSide: Int = MAX_SIDE): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@runCatching null

        val longest = max(decoded.width, decoded.height)
        val result = if (longest > maxSide) {
            val ratio = maxSide.toFloat() / longest
            val width = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val height = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }

        ByteArrayOutputStream().use { out ->
            result.compress(Bitmap.CompressFormat.PNG, 100, out)
            result.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxSide && height / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample
    }
}
