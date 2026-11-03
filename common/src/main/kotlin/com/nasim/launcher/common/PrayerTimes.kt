package com.nasim.launcher.common

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer(val key: String, val ar: String, val hasAdhan: Boolean) {
    FAJR("fajr", "الفجر", true),
    SUNRISE("sunrise", "الشروق", false),
    DHUHR("dhuhr", "الظهر", true),
    ASR("asr", "العصر", true),
    MAGHRIB("maghrib", "المغرب", true),
    ISHA("isha", "العشاء", true);

    companion object {
        fun byKey(k: String): Prayer? = values().firstOrNull { it.key == k }
    }
}

data class CalcMethod(val id: String, val ar: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0)

/** حساب مواقيت الصلاة بدون إنترنت (نفس خوارزمية PrayTimes). */
object PrayerCalc {
    val methods = listOf(
        CalcMethod("egypt", "الهيئة المصرية العامة للمساحة", 19.5, 17.5),
        CalcMethod("mwl", "رابطة العالم الإسلامي", 18.0, 17.0),
        CalcMethod("makkah", "أم القرى (مكة المكرمة)", 18.5, 0.0, 90),
        CalcMethod("karachi", "جامعة العلوم الإسلامية - كراتشي", 18.0, 18.0),
        CalcMethod("isna", "أمريكا الشمالية (ISNA)", 15.0, 15.0)
    )

    fun method(id: String): CalcMethod = methods.firstOrNull { it.id == id } ?: methods[0]

    private fun dtr(d: Double) = Math.toRadians(d)
    private fun rtd(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double): Double { var x = a % 360.0; if (x < 0) x += 360.0; return x }
    private fun fixHour(a: Double): Double { var x = a % 24.0; if (x < 0) x += 24.0; return x }

    private class Sun(val decl: Double, val eqt: Double)

    private fun sunPos(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(dtr(g)) + 0.020 * sin(dtr(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(rtd(atan2(cos(dtr(e)) * sin(dtr(l)), cos(dtr(l)))) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = rtd(asin(sin(dtr(e)) * sin(dtr(l))))
        return Sun(decl, eqt)
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun midDay(jd: Double, t: Double) = fixHour(12 - sunPos(jd + t).eqt)

    private fun angleTime(jd: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sunPos(jd + t).decl
        val noon = midDay(jd, t)
        val x = (-sin(dtr(angle)) - sin(dtr(decl)) * sin(dtr(lat))) / (cos(dtr(decl)) * cos(dtr(lat)))
        val a = rtd(acos(x.coerceIn(-1.0, 1.0))) / 15.0
        return if (ccw) noon - a else noon + a
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double, t: Double): Double {
        val decl = sunPos(jd + t).decl
        val angle = -rtd(atan(1.0 / (factor + tan(dtr(abs(lat - decl))))))
        return angleTime(jd, lat, angle, t, false)
    }

    /** ساعات محلية (ممكن تعدي 24 في حالات نادرة) لكل صلاة. */
    fun hours(
        year: Int, month: Int, day: Int, lat: Double, lon: Double, tzHours: Double,
        m: CalcMethod, hanafi: Boolean
    ): Map<Prayer, Double> {
        val jd = julian(year, month, day) - lon / (15.0 * 24.0)
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asr = 13.0; var maghrib = 18.0; var isha = 18.0
        val factor = if (hanafi) 2.0 else 1.0
        repeat(2) {
            val tf = fajr / 24; val ts = sunrise / 24; val td = dhuhr / 24
            val ta = asr / 24; val tm = maghrib / 24; val ti = isha / 24
            fajr = angleTime(jd, lat, m.fajr, tf, true)
            sunrise = angleTime(jd, lat, 0.833, ts, true)
            dhuhr = midDay(jd, td)
            asr = asrTime(jd, lat, factor, ta)
            maghrib = angleTime(jd, lat, 0.833, tm, false)
            isha = angleTime(jd, lat, if (m.ishaMinutes > 0) 0.833 else m.isha, ti, false)
        }
        val adj = tzHours - lon / 15.0
        fajr += adj; sunrise += adj; dhuhr += adj; asr += adj; maghrib += adj; isha += adj
        if (m.ishaMinutes > 0) isha = maghrib + m.ishaMinutes / 60.0
        return mapOf(
            Prayer.FAJR to fajr, Prayer.SUNRISE to sunrise, Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr, Prayer.MAGHRIB to maghrib, Prayer.ISHA to isha
        )
    }

    /** مواقيت يوم معيّن (بتوقيت الجهاز) كـ epoch millis. [dayOffset] 0 = النهارده، 1 = بكره. */
    fun times(cfg: ThemeConfig, nowMillis: Long = System.currentTimeMillis(), dayOffset: Int = 0): Map<Prayer, Long> {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val midnight = cal.timeInMillis
        val noon = midnight + 12 * 3600_000L
        val tzHours = tz.getOffset(noon) / 3600_000.0
        val h = hours(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            cfg.lat, cfg.lon, tzHours, method(cfg.prayerMethod), cfg.asrHanafi
        )
        return h.mapValues { (_, v) -> midnight + Math.round(v * 60.0) * 60_000L }
    }

    /** الصلاة القادمة (من اللي ليها أذان فقط) ووقتها. */
    fun next(cfg: ThemeConfig, nowMillis: Long = System.currentTimeMillis()): Pair<Prayer, Long> {
        for (off in 0..1) {
            val t = times(cfg, nowMillis, off)
            for (p in Prayer.values()) {
                if (!p.hasAdhan) continue
                val at = t[p] ?: continue
                if (at > nowMillis) return p to at
            }
        }
        // احتياطي (لا يحدث عملياً)
        return Prayer.FAJR to nowMillis + 3600_000L
    }
}
