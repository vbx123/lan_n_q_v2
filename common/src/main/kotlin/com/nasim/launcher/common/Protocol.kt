package com.nasim.launcher.common

import android.net.Uri

object Protocol {
    const val PORT = 8765
    /** اسم الخدمة للاكتشاف التلقائي على الشبكة (NSD / mDNS). */
    const val SERVICE_TYPE = "_nasimtv._tcp."
    /** أقصى طول لسطر JSON واحد في الطلبات. */
    const val MAX_LINE = 65536

    const val KIND_WALLPAPER = "wallpaper"
    const val KIND_SCREENSAVER = "screensaver"
    const val KIND_ADHAN = "adhan"
    const val KIND_ADHAN_FAJR = "adhan_fajr"

    fun maxBytes(kind: String): Long = when (kind) {
        KIND_WALLPAPER -> 8L * 1024 * 1024
        KIND_SCREENSAVER -> 60L * 1024 * 1024
        KIND_ADHAN, KIND_ADHAN_FAJR -> 15L * 1024 * 1024
        else -> 0L
    }

    fun allowedExt(kind: String): Set<String> = when (kind) {
        KIND_WALLPAPER -> setOf("jpg", "jpeg", "png", "webp")
        KIND_SCREENSAVER -> setOf("jpg", "jpeg", "png", "webp", "mp4", "webm", "mkv", "3gp")
        KIND_ADHAN, KIND_ADHAN_FAJR -> setOf("mp3", "ogg", "m4a", "wav", "aac")
        else -> emptySet()
    }
}

/** محتوى الـ QR اللي بتعرضه الشاشة والموبايل بيقراه: moolan://pair?ip=..&port=..&pin=.. */
object Pairing {
    fun uri(ip: String, pin: String): String = "moolan://pair?ip=$ip&port=${Protocol.PORT}&pin=$pin"

    /** يرجّع (ip, pin) أو null لو الـ QR مش بتاعنا. */
    fun parse(text: String): Pair<String, String>? = try {
        val u = Uri.parse(text.trim())
        if (u.scheme != "moolan" || u.host != "pair") null
        else {
            val ip = u.getQueryParameter("ip").orEmpty()
            val pin = u.getQueryParameter("pin").orEmpty()
            if (ip.isBlank() || pin.length != 6) null else ip to pin
        }
    } catch (e: Exception) {
        null
    }
}
