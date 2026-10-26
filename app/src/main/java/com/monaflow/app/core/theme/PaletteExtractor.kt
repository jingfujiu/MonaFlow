package com.monaflow.app.core.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette

/**
 * 图片取色：本地处理，不上传原图；大图先采样缩放避免 OOM。
 * 取色失败返回 null（不崩溃，由调用方提示）。
 */
object PaletteExtractor {

    /** 从图片 URI 提取主色（种子色）。 */
    fun extractSeed(context: Context, uri: Uri): Int? {
        return try {
            val bitmap = sampleBitmap(context, uri) ?: return null
            val palette = Palette.from(bitmap).maximumColorCount(24).generate()
            bitmap.recycle()
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.getDominantColor(0).takeIf { it != 0 }
        } catch (e: Exception) {
            null
        }
    }

    /** 采样：长边不超过 512px，兼顾取色精度与内存。 */
    private fun sampleBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / 2 >= 512) {
            sample *= 2
            maxSide /= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}
