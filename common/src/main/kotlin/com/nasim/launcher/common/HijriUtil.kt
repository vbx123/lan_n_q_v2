package com.nasim.launcher.common

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/** التاريخ الهجري (أم القرى) مع إمكانية تعديل ±2 يوم حسب رؤية بلدك. */
object HijriUtil {
    private val months = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    fun format(offsetDays: Int, nowMillis: Long = System.currentTimeMillis()): String = try {
        val ld = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val h = HijrahChronology.INSTANCE.date(ld).plus(offsetDays.toLong(), ChronoUnit.DAYS)
        val day = h.get(ChronoField.DAY_OF_MONTH)
        val month = h.get(ChronoField.MONTH_OF_YEAR)
        val year = h.get(ChronoField.YEAR)
        "$day ${months[(month - 1).coerceIn(0, 11)]} $year هـ"
    } catch (e: Exception) {
        ""
    }
}
