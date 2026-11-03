package com.nasim.launcher.common

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** طقس + بحث مدن مجاني بدون مفتاح API (open-meteo.com). بدون أي مكتبات خارجية. */
object OpenMeteo {
    data class Weather(val tempC: Int, val code: Int)

    fun fetchWeather(lat: Double, lon: Double): Weather? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
        val cw = JSONObject(httpGet(url)).getJSONObject("current_weather")
        Weather(Math.round(cw.getDouble("temperature")).toInt(), cw.getInt("weathercode"))
    } catch (e: Exception) {
        null
    }

    fun geocode(query: String): List<City> = try {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=ar&format=json"
        val arr = JSONObject(httpGet(url)).optJSONArray("results")
        val out = ArrayList<City>()
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString("، ")
            out += City(label, o.getDouble("latitude"), o.getDouble("longitude"))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    fun describe(code: Int): String = when (code) {
        0 -> "صافٍ"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        in 51..67 -> "أمطار"
        in 71..77 -> "ثلوج"
        in 80..82 -> "زخات مطر"
        in 95..99 -> "عواصف رعدية"
        else -> ""
    }

    private fun httpGet(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
