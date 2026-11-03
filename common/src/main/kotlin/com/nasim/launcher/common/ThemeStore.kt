package com.nasim.launcher.common

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.SecureRandom

/** التخزين المحلي (SharedPreferences) — نفس الكلاس بيستخدم على الشاشة والموبايل. */
class ThemeStore(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    fun load(): ThemeConfig = try {
        prefs.getString(KEY_CONFIG, null)?.let { ThemeConfig.fromJson(JSONObject(it)) } ?: ThemeConfig()
    } catch (e: Exception) {
        ThemeConfig()
    }

    fun save(c: ThemeConfig) {
        prefs.edit().putString(KEY_CONFIG, c.toJson().toString()).apply()
    }

    /** كود الاقتران (6 أرقام) — بيتولد مرة واحدة على الشاشة ويظهر في الإعدادات وفي الـ QR. */
    fun pin(): String {
        prefs.getString(KEY_PIN, null)?.let { return it }
        val p = (SecureRandom().nextInt(900000) + 100000).toString()
        prefs.edit().putString(KEY_PIN, p).apply()
        return p
    }

    companion object {
        const val KEY_CONFIG = "config"
        const val KEY_PIN = "pin"
    }
}
