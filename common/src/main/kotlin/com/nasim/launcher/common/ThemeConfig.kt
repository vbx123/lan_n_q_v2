package com.nasim.launcher.common

import org.json.JSONArray
import org.json.JSONObject

/** معرّفات عناصر الصفحة الرئيسية الخاصة (أي معرّف تاني = اسم package لتطبيق). */
object HomeIds {
    const val STORE = "@store"
    const val MEDIA = "@media"
    const val YOUTUBE = "@youtube"
    const val INPUTS = "@inputs"
    const val QURAN = "@quran"
    const val MORE = "@more"

    val specials = listOf(
        STORE to "المتجر", MEDIA to "مركز الوسائط", YOUTUBE to "يوتيوب",
        INPUTS to "المدخلات (HDMI وغيره)", QURAN to "القرآن الكريم", MORE to "المزيد (كل التطبيقات)"
    )
    val defaults = listOf(STORE, MEDIA, YOUTUBE, INPUTS, QURAN, MORE)
}

/** كل إعدادات اللانشر اللي بتتنقل من الموبايل للشاشة. */
data class ThemeConfig(
    val themeId: String = THEME_JERUSALEM,
    val cityName: String = "القدس",
    val lat: Double = 31.7683,
    val lon: Double = 35.2137,
    val use24h: Boolean = false,
    val showWeather: Boolean = true,
    val hijriOffset: Int = 0,
    val customWallpaper: String = "",
    val rotateSec: Int = 30,
    val home: List<String> = HomeIds.defaults,
    val prayerMethod: String = "egypt",
    val asrHanafi: Boolean = false,
    val ssMinutes: Int = 5
) {
    fun toJson(): JSONObject = JSONObject()
        .put("themeId", themeId)
        .put("cityName", cityName)
        .put("lat", lat)
        .put("lon", lon)
        .put("use24h", use24h)
        .put("showWeather", showWeather)
        .put("hijriOffset", hijriOffset)
        .put("customWallpaper", customWallpaper)
        .put("rotateSec", rotateSec)
        .put("home", JSONArray(home))
        .put("prayerMethod", prayerMethod)
        .put("asrHanafi", asrHanafi)
        .put("ssMinutes", ssMinutes)

    companion object {
        const val THEME_JERUSALEM = "jerusalem"
        const val THEME_CYBER = "cyber"
        const val THEME_GALLERY = "gallery"

        private fun strList(a: JSONArray?, fallback: List<String>): List<String> {
            if (a == null) return fallback
            val out = ArrayList<String>()
            for (i in 0 until a.length()) {
                val s = a.optString(i, "")
                if (s.isNotBlank() && s.length <= 200 && !out.contains(s)) out += s
            }
            return out
        }

        /** يقرأ JSON بشكل آمن: أي قيمة غلط تتحول لقيمة افتراضية. */
        fun fromJson(o: JSONObject): ThemeConfig {
            val d = ThemeConfig()
            val id = o.optString("themeId", d.themeId)
            val wp = o.optString("customWallpaper", "")
            return ThemeConfig(
                themeId = if (id == THEME_CYBER || id == THEME_GALLERY) id else THEME_JERUSALEM,
                cityName = o.optString("cityName", d.cityName).take(40),
                lat = o.optDouble("lat", d.lat).takeIf { it in -90.0..90.0 } ?: d.lat,
                lon = o.optDouble("lon", d.lon).takeIf { it in -180.0..180.0 } ?: d.lon,
                use24h = o.optBoolean("use24h", d.use24h),
                showWeather = o.optBoolean("showWeather", d.showWeather),
                hijriOffset = o.optInt("hijriOffset", 0).coerceIn(-2, 2),
                customWallpaper = if (wp.contains('/') || wp.contains("..")) "" else wp.take(80),
                rotateSec = o.optInt("rotateSec", d.rotateSec).coerceIn(10, 3600),
                home = strList(o.optJSONArray("home"), d.home).take(40),
                prayerMethod = PrayerCalc.method(o.optString("prayerMethod", d.prayerMethod)).id,
                asrHanafi = o.optBoolean("asrHanafi", d.asrHanafi),
                ssMinutes = o.optInt("ssMinutes", d.ssMinutes).coerceIn(0, 120)
            )
        }
    }
}

data class City(val name: String, val lat: Double, val lon: Double)

object CityPresets {
    val list = listOf(
        City("القدس", 31.7683, 35.2137),
        City("غزة", 31.5017, 34.4668),
        City("رام الله", 31.9038, 35.2034),
        City("الخليل", 31.5326, 35.0998),
        City("نابلس", 32.2211, 35.2544),
        City("عمّان", 31.9454, 35.9284),
        City("القاهرة", 30.0444, 31.2357),
        City("الإسكندرية", 31.2001, 29.9187),
        City("الجيزة", 30.0131, 31.2089),
        City("المنصورة", 31.0409, 31.3785),
        City("أسيوط", 27.1809, 31.1837),
        City("الأقصر", 25.6872, 32.6396),
        City("أسوان", 24.0889, 32.8998),
        City("مكة المكرمة", 21.3891, 39.8579),
        City("المدينة المنورة", 24.5247, 39.5692),
        City("الرياض", 24.7136, 46.6753),
        City("دبي", 25.2048, 55.2708)
    )
}
