package com.nasim.launcher.tv

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Reciter(val id: String, val name: String, val kw: String, val server: String)
data class Radio(val name: String, val url: String)

object QuranData {
    val surahs = arrayOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس"
    )

    /** عشرة قرّاء مشهورين (رواية حفص - مرتل). الرابط الاحتياطي بيتحدّث تلقائياً من mp3quran.net لو فيه إنترنت. */
    val reciters = listOf(
        Reciter("afasy", "مشاري راشد العفاسي", "العفاسي", "https://server8.mp3quran.net/afs/"),
        Reciter("basit", "عبد الباسط عبد الصمد", "عبد الباسط|عبدالباسط", "https://server7.mp3quran.net/basit/"),
        Reciter("husary", "محمود خليل الحصري", "الحصري", "https://server13.mp3quran.net/husr/"),
        Reciter("minshawi", "محمد صديق المنشاوي", "المنشاوي", "https://server10.mp3quran.net/minsh/"),
        Reciter("tablawi", "محمد محمود الطبلاوي", "الطبلاوي", "https://server12.mp3quran.net/tblawi/"),
        Reciter("maher", "ماهر المعيقلي", "ماهر المعيقلي", "https://server12.mp3quran.net/maher/"),
        Reciter("ghamdi", "سعد الغامدي", "سعد الغامدي", "https://server7.mp3quran.net/s_gmd/"),
        Reciter("sudais", "عبد الرحمن السديس", "السديس", "https://server11.mp3quran.net/sds/"),
        Reciter("shuraim", "سعود الشريم", "الشريم", "https://server7.mp3quran.net/shur/"),
        Reciter("dosari", "ياسر الدوسري", "ياسر الدوسري", "https://server11.mp3quran.net/yasser/")
    )

    val fallbackRadios = listOf(
        Radio("إذاعة القرآن الكريم من القاهرة", "https://stream.radiojar.com/8s5u5tpdtwzuv")
    )

    /** الأشهر أولاً في قائمة الإذاعات. */
    val famousKeywords = listOf(
        "القاهرة", "مكة", "الحرم المكي", "المسجد الحرام", "المدينة", "المسجد النبوي",
        "الحصري", "عبد الباسط", "المنشاوي", "الطبلاوي", "مصطفى إسماعيل", "العفاسي",
        "ماهر", "السديس", "الشريم", "الغامدي", "الدوسري", "القطامي"
    )
}

/** يجيب روابط القرّاء والإذاعات من واجهة mp3quran.net (بدون مفتاح) ويحتفظ بنسخة مخزّنة أسبوع. */
object QuranRepo {
    @Volatile private var servers: Map<String, String> = emptyMap()
    @Volatile private var radios: List<Radio> = QuranData.fallbackRadios

    fun serverOf(r: Reciter): String = servers[r.id] ?: r.server
    fun radios(): List<Radio> = radios

    /** يتنادى من Thread خلفي. أي فشل = نكمّل بالروابط الاحتياطية. */
    fun refresh(ctx: Context) {
        try {
            fetchCached(ctx, "reciters.json", "https://mp3quran.net/api/v3/reciters?language=ar")?.let {
                val m = parseReciters(it)
                if (m.isNotEmpty()) servers = m
            }
        } catch (_: Exception) {}
        try {
            fetchCached(ctx, "radios.json", "https://mp3quran.net/api/v3/radios?language=ar")?.let {
                val r = parseRadios(it)
                if (r.isNotEmpty()) radios = r
            }
        } catch (_: Exception) {}
    }

    private fun fetchCached(ctx: Context, name: String, url: String): String? {
        val f = File(ctx.cacheDir, name)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 24 * 3600_000L) {
            try { return f.readText() } catch (_: Exception) {}
        }
        return try {
            val t = http(url)
            if (t.length > 100) f.writeText(t)
            t
        } catch (e: Exception) {
            if (f.exists()) try { f.readText() } catch (_: Exception) { null } else null
        }
    }

    private fun http(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "moo_lan_tv")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parseReciters(json: String): Map<String, String> {
        val out = HashMap<String, String>()
        val arr = JSONObject(json).optJSONArray("reciters") ?: return out
        for (def in QuranData.reciters) {
            val keys = def.kw.split("|")
            var bestScore = -1
            var bestServer = ""
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val nm = r.optString("name")
                if (keys.none { nm.contains(it) }) continue
                val ms = r.optJSONArray("moshaf") ?: continue
                for (j in 0 until ms.length()) {
                    val m = ms.optJSONObject(j) ?: continue
                    val srv = m.optString("server")
                    if (srv.isBlank()) continue
                    val mn = m.optString("name")
                    var score = 0
                    if (m.optInt("surah_total") == 114) score += 10
                    if (mn.contains("حفص")) score += 3
                    if (mn.contains("مرتل")) score += 2
                    if (mn.contains("المعلم") || mn.contains("مجود")) score -= 4
                    if (score > bestScore) { bestScore = score; bestServer = srv }
                }
            }
            if (bestServer.isNotBlank()) out[def.id] = if (bestServer.endsWith("/")) bestServer else "$bestServer/"
        }
        return out
    }

    private fun parseRadios(json: String): List<Radio> {
        val arr = JSONObject(json).optJSONArray("radios") ?: return emptyList()
        val seen = HashSet<String>()
        val list = ArrayList<Radio>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val url = o.optString("url").trim()
            if (name.isEmpty() || !url.startsWith("http") || !seen.add(url)) continue
            list += Radio(name, url)
        }
        if (list.none { it.name.contains("القاهرة") }) list.addAll(0, QuranData.fallbackRadios)
        fun rank(r: Radio): Int {
            val i = QuranData.famousKeywords.indexOfFirst { r.name.contains(it) }
            return if (i < 0) 999 else i
        }
        return list.sortedBy { rank(it) } // sortedBy ثابت (stable)
    }
}
