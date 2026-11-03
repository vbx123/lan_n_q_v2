package com.nasim.launcher.tv

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Base64
import com.nasim.launcher.common.Protocol
import java.io.ByteArrayOutputStream
import java.io.File

/** ملفات الوسائط اللي بتترفع من الهاتف (خلفيات / شاشة توقف / أذان). */
object MediaFiles {
    private val videoExt = setOf("mp4", "webm", "mkv", "3gp")

    fun dir(ctx: Context, kind: String): File {
        val name = when (kind) {
            Protocol.KIND_WALLPAPER -> "wallpapers"
            Protocol.KIND_SCREENSAVER -> "screensaver"
            else -> "adhan"
        }
        return File(ctx.filesDir, name).apply { mkdirs() }
    }

    fun list(ctx: Context, kind: String): List<File> {
        val all = dir(ctx, kind).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: emptyList()
        val f = when (kind) {
            Protocol.KIND_ADHAN -> all.filter { it.name.startsWith("adhan.") }
            Protocol.KIND_ADHAN_FAJR -> all.filter { it.name.startsWith("adhan_fajr.") }
            else -> all
        }
        return f.sortedBy { it.lastModified() }
    }

    fun isVideo(f: File) = f.extension.lowercase() in videoExt

    /** يمنع أي اسم فيه مسارات (حماية من الكتابة خارج المجلد). */
    fun safeFile(ctx: Context, kind: String, name: String): File? {
        if (name.isBlank() || name != File(name).name || name.contains("..")) return null
        return File(dir(ctx, kind), name)
    }

    fun thumbBase64(f: File): String? = try {
        val bmp: Bitmap? = if (isVideo(f)) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                r.getFrameAtTime(0)
            } finally {
                r.release()
            }
        } else Bmp.decodeFile(f.path, 320, 180)
        if (bmp == null) null else {
            val h = (160f * bmp.height / bmp.width).toInt().coerceAtLeast(1)
            val s = Bitmap.createScaledBitmap(bmp, 160, h, true)
            val bo = ByteArrayOutputStream()
            s.compress(Bitmap.CompressFormat.JPEG, 60, bo)
            Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Throwable) {
        null
    }
}
