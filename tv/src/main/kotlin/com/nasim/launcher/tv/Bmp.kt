package com.nasim.launcher.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** تحميل صور بحجم مناسب للشاشة (RGB_565 = نص الذاكرة، مناسب للأجهزة الضعيفة). */
object Bmp {
    private fun sample(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxW && h / (s * 2) >= maxH) s *= 2
        return s
    }

    fun decodeFile(path: String, maxW: Int = 1920, maxH: Int = 1080): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        val o = BitmapFactory.Options().apply {
            inSampleSize = sample(b.outWidth, b.outHeight, maxW, maxH)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, o)
    } catch (e: Throwable) {
        null
    }

    fun decodeRes(ctx: Context, res: Int, maxW: Int = 1920, maxH: Int = 1080): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, res, b)
        val o = BitmapFactory.Options().apply {
            inSampleSize = sample(b.outWidth, b.outHeight, maxW, maxH)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeResource(ctx.resources, res, o)
    } catch (e: Throwable) {
        null
    }
}
