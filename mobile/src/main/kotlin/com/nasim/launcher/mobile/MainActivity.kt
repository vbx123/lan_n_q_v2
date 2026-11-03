package com.nasim.launcher.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nasim.launcher.common.CityPresets
import com.nasim.launcher.common.HomeIds
import com.nasim.launcher.common.OpenMeteo
import com.nasim.launcher.common.Pairing
import com.nasim.launcher.common.PrayerCalc
import com.nasim.launcher.common.Prayer
import com.nasim.launcher.common.Protocol
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStore
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** ريموت الهاتف: تحكم + مظهر + تطبيقات + مواقيت + شاشة توقف. الإعدادات تتعدل ثم "تطبيق على الشاشة". */
class MainActivity : Activity() {

    private class AppInfo(val pkg: String, val label: String)
    private class MediaItem(val name: String, val video: Boolean, val thumb: Bitmap?)

    private val gold = Color.parseColor("#E8C26A")
    private val bgDark = Color.parseColor("#101420")

    private lateinit var store: ThemeStore
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout

    private var cfg = ThemeConfig()
    private var tab = 0
    private var apps: List<AppInfo> = emptyList()
    private var wallpapers: List<MediaItem> = emptyList()
    private var savers: List<MediaItem> = emptyList()
    private var pickKind = ""

    private val io = Executors.newSingleThreadExecutor()
    private val keyIo = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val host: String get() = store.prefs.getString("host", "") ?: ""
    private val pin: String get() = store.prefs.getString("pin", "") ?: ""
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun ui(f: () -> Unit) { main.post { if (!isDestroyed) f() } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ بناء الواجهة
    private fun text(t: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(2))
        }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1B2238")); setStroke(dp(1), gold); cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, m: Int = 4) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }

    private fun hrow(vararg v: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (x in v) addView(x, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
    }

    private fun sw(t: String, on: Boolean, change: (Boolean) -> Unit) = Switch(this).apply {
        text = t; setTextColor(Color.WHITE); isChecked = on
        setOnCheckedChangeListener { _, v -> change(v) }
    }

    private fun page(build: LinearLayout.() -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        col.build()
        return ScrollView(this).apply { addView(col) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ThemeStore(this)
        cfg = store.load()

        statusText = text("", 13f, Color.parseColor("#9FE8FF"))
        resultText = text("", 13f, gold)
        content = FrameLayout(this)
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf("الريموت", "المظهر", "التطبيقات", "المواقيت", "شاشة التوقف")
        for ((i, n) in names.withIndex()) {
            tabBar.addView(btn(n) { tab = i; renderTab() }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 3))
        }
        val apply = btn("تطبيق على الشاشة") { applyToTv() }
        val connect = btn("الاتصال بالشاشة (QR)") { showConnect() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(bgDark)
            setPadding(dp(10), dp(36), dp(10), dp(10))
            addView(text("نسيم الأقصى", 22f, gold, true))
            addView(statusText)
            addView(connect, lp())
            addView(HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false; addView(tabBar) })
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(resultText)
            addView(apply, lp(h = dp(52)))
        }
        setContentView(root)
        renderTab()
        updateConn()
        if (host.isNotEmpty()) syncFromTv()
    }

    override fun onDestroy() {
        io.shutdownNow(); keyIo.shutdownNow()
        super.onDestroy()
    }

    private fun renderTab() {
        content.removeAllViews()
        content.addView(
            when (tab) {
                0 -> remoteTab()
                1 -> themeTab()
                2 -> appsTab()
                3 -> prayerTab()
                else -> saverTab()
            }
        )
    }

    private fun changed() { store.save(cfg); resultText.text = "" }

    // ------------------------------------------------------------------ الريموت
    private fun sendKey(k: String, v: View?) {
        v?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (host.isEmpty()) return
        val h = host; val p = pin
        keyIo.execute {
            try {
                val r = RemoteClient.key(h, p, k)
                if (!r.optBoolean("ok")) {
                    val err = r.optString("error")
                    ui { resultText.text = if (err == "bad_pin") "الـ PIN غير صحيح" else "الأمر ده مش مدعوم وأنت جوه تطبيق تاني (فعّل «الإتاحة» من إعدادات الشاشة للرجوع/الرئيسية)" }
                }
            } catch (e: Exception) {
                ui { resultText.text = "تعذر الوصول للشاشة" }
            }
        }
    }

    private fun keyBtn(label: String, key: String, repeat: Boolean = false): Button {
        val b = btn(label) { if (!repeat) sendKey(key, null) }
        b.textSize = 20f
        if (repeat) {
            val r = object : Runnable {
                override fun run() { sendKey(key, null); main.postDelayed(this, 140) }
            }
            b.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sendKey(key, v); main.postDelayed(r, 450) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(r)
                }
                true
            }
        }
        return b
    }

    private fun remoteTab(): View = page {
        val blank = { View(this@MainActivity) }
        addView(hrow(blank(), keyBtn("▲", "up", true), blank()))
        addView(hrow(keyBtn("◀", "left", true), keyBtn("OK", "ok"), keyBtn("▶", "right", true)))
        addView(hrow(blank(), keyBtn("▼", "down", true), blank()))
        addView(hrow(keyBtn("رجوع", "back"), keyBtn("الرئيسية", "home")))
        addView(text("الصوت والوسائط", 14f, Color.LTGRAY))
        addView(hrow(keyBtn("صوت -", "voldown", true), keyBtn("كتم", "mute"), keyBtn("صوت +", "volup", true)))
        addView(hrow(keyBtn("⏮", "prev"), keyBtn("⏯", "play"), keyBtn("⏭", "next")))
        addView(text("الاتجاهات وOK بتشتغل جوه نسيم الأقصى. الصوت والوسائط في أي تطبيق.", 12f, Color.GRAY))
    }

    // ------------------------------------------------------------------ المظهر
    private fun themeTab(): View = page {
        val preview = TvPreviewView(this@MainActivity)
        preview.render(cfg)
        addView(text("المعاينة المباشرة", 14f, Color.LTGRAY))
        addView(preview, lp())

        addView(text("الثيم", 14f, Color.LTGRAY))
        fun theme(t: String, id: String) = btn((if (cfg.themeId == id) "✓ " else "") + t) {
            cfg = cfg.copy(themeId = id); changed(); renderTab()
        }
        addView(hrow(theme("نسيم الأقصى", ThemeConfig.THEME_JERUSALEM), theme("السيبراني", ThemeConfig.THEME_CYBER), theme("خلفيات متغيرة", ThemeConfig.THEME_GALLERY)))

        addView(text("الموقع (الوقت والطقس والمواقيت)", 14f, Color.LTGRAY))
        addView(text("الحالي: ${cfg.cityName}", 13f, gold))
        val chips = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in CityPresets.list) chips.addView(btn(c.name) {
            cfg = cfg.copy(cityName = c.name, lat = c.lat, lon = c.lon); changed(); renderTab()
        }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 3))
        addView(HorizontalScrollView(this@MainActivity).apply { addView(chips) })
        val et = EditText(this@MainActivity).apply {
            hint = "ابحث عن أي مدينة"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); isSingleLine = true
        }
        addView(hrow(et, btn("بحث") { searchCity(et.text.toString().trim()) }))

        addView(sw("نظام 24 ساعة", cfg.use24h) { cfg = cfg.copy(use24h = it); changed() })
        addView(sw("إظهار الطقس", cfg.showWeather) { cfg = cfg.copy(showWeather = it); changed() })

        addView(text("الخلفية", 15f, gold, true))
        addView(text("اختار صورة من الهاتف تكون خلفية الثيمات، وفي ثيم «خلفيات متغيرة» كل صورك بتدور مع الخلفيات الجاهزة.", 12f, Color.GRAY))
        addView(hrow(btn("إضافة خلفيات من الهاتف") { pick(Protocol.KIND_WALLPAPER) }, btn("الخلفية الافتراضية للثيم") {
            cfg = cfg.copy(customWallpaper = ""); changed(); resultText.text = "اضغط «تطبيق على الشاشة»"
        }))
        addView(mediaStrip(wallpapers, Protocol.KIND_WALLPAPER))
        addView(text("سرعة تغيير الخلفيات", 13f, Color.LTGRAY))
        val speeds = listOf("15 ث" to 15, "30 ث" to 30, "دقيقة" to 60, "5 د" to 300)
        addView(hrow(*speeds.map { (n, s) -> btn((if (cfg.rotateSec == s) "✓ " else "") + n) { cfg = cfg.copy(rotateSec = s); changed(); renderTab() } }.toTypedArray()))
        if (host.isNotEmpty() && wallpapers.isEmpty()) loadMedia(Protocol.KIND_WALLPAPER)
    }

    private fun searchCity(q: String) {
        if (q.isEmpty()) return
        resultText.text = "جاري البحث…"
        io.execute {
            val r = OpenMeteo.geocode(q)
            ui {
                if (r.isEmpty()) { resultText.text = "لم يتم العثور على نتائج (تأكد من الإنترنت)"; return@ui }
                resultText.text = ""
                AlertDialog.Builder(this).setTitle("اختر المدينة")
                    .setItems(r.map { it.name }.toTypedArray()) { _, i ->
                        cfg = cfg.copy(cityName = r[i].name.substringBefore("،"), lat = r[i].lat, lon = r[i].lon)
                        changed(); renderTab()
                    }.show()
            }
        }
    }

    // ------------------------------------------------------------------ التطبيقات
    private fun labelOf(id: String): String =
        HomeIds.specials.firstOrNull { it.first == id }?.second ?: apps.firstOrNull { it.pkg == id }?.label ?: id

    private fun appsTab(): View = page {
        addView(text("ترتيب الشاشة الرئيسية (الأول = أول كارت)", 14f, Color.LTGRAY))
        if (apps.isEmpty()) addView(text("اضغط «تحديث» لتحميل تطبيقات الشاشة", 12f, Color.GRAY))
        for ((i, id) in cfg.home.withIndex()) {
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text("${i + 1}. ${labelOf(id)}", 15f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun move(d: Int) {
                val j = i + d
                if (j !in cfg.home.indices) return
                val l = cfg.home.toMutableList(); val t = l[i]; l[i] = l[j]; l[j] = t
                cfg = cfg.copy(home = l); changed(); renderTab()
            }
            row.addView(btn("▲") { move(-1) }, lp(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT, 2))
            row.addView(btn("▼") { move(1) }, lp(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT, 2))
            row.addView(btn("✕") {
                cfg = cfg.copy(home = cfg.home.filterIndexed { k, _ -> k != i }); changed(); renderTab()
            }, lp(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT, 2))
            addView(row)
        }
        addView(hrow(btn("إضافة تطبيق / عنصر") { showAddDialog() }, btn("تحديث قائمة التطبيقات") { loadApps() }))
        addView(btn("استرجاع الترتيب الافتراضي") { cfg = cfg.copy(home = HomeIds.defaults); changed(); renderTab() }, lp())
    }

    private fun showAddDialog() {
        val cands = ArrayList<Pair<String, String>>()
        for (s in HomeIds.specials) if (s.first !in cfg.home) cands += s
        for (a in apps) if (a.pkg !in cfg.home) cands += a.pkg to a.label
        if (cands.isEmpty()) { toast("مفيش عناصر جديدة (حدّث قائمة التطبيقات)"); return }
        val picked = BooleanArray(cands.size)
        AlertDialog.Builder(this).setTitle("اختر ما تريد إضافته")
            .setMultiChoiceItems(cands.map { it.second }.toTypedArray(), picked) { _, i, c -> picked[i] = c }
            .setPositiveButton("إضافة") { _, _ ->
                val add = cands.filterIndexed { i, _ -> picked[i] }.map { it.first }
                cfg = cfg.copy(home = cfg.home + add); changed(); renderTab()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun loadApps() {
        if (host.isEmpty()) { showConnect(); return }
        val h = host; val p = pin
        resultText.text = "جاري تحميل التطبيقات…"
        io.execute {
            try {
                val r = RemoteClient.apps(h, p)
                if (!r.optBoolean("ok")) { ui { resultText.text = "الـ PIN غير صحيح" }; return@execute }
                val arr = r.getJSONArray("apps")
                val list = (0 until arr.length()).map { AppInfo(arr.getJSONObject(it).getString("pkg"), arr.getJSONObject(it).getString("label")) }
                ui { apps = list; resultText.text = "تم تحميل ${list.size} تطبيق"; if (tab == 2) renderTab() }
            } catch (e: Exception) {
                ui { resultText.text = "تعذر الوصول للشاشة" }
            }
        }
    }

    // ------------------------------------------------------------------ المواقيت
    private fun prayerTab(): View = page {
        addView(text("مواقيت اليوم — ${cfg.cityName}", 15f, gold, true))
        val t = PrayerCalc.times(cfg)
        val fmt = SimpleDateFormat(if (cfg.use24h) "HH:mm" else "h:mm a", Locale.forLanguageTag("ar-u-nu-latn"))
        for (p in Prayer.values()) addView(text("${p.ar}:   ${t[p]?.let { fmt.format(Date(it)) } ?: "--"}", 17f))
        addView(text("طريقة الحساب", 14f, Color.LTGRAY))
        addView(btn(PrayerCalc.method(cfg.prayerMethod).ar) {
            val ms = PrayerCalc.methods
            AlertDialog.Builder(this@MainActivity).setTitle("طريقة الحساب")
                .setItems(ms.map { it.ar }.toTypedArray()) { _, i -> cfg = cfg.copy(prayerMethod = ms[i].id); changed(); renderTab() }.show()
        }, lp())
        addView(sw("العصر بالمذهب الحنفي", cfg.asrHanafi) { cfg = cfg.copy(asrHanafi = it); changed(); renderTab() })
        addView(text("تعديل التاريخ الهجري (حسب رؤية بلدك)", 14f, Color.LTGRAY))
        addView(hrow(btn("-1") { cfg = cfg.copy(hijriOffset = (cfg.hijriOffset - 1).coerceAtLeast(-2)); changed(); renderTab() },
            text("${cfg.hijriOffset} يوم", 16f, gold).apply { gravity = Gravity.CENTER },
            btn("+1") { cfg = cfg.copy(hijriOffset = (cfg.hijriOffset + 1).coerceAtMost(2)); changed(); renderTab() }))
    }

    // ------------------------------------------------------------------ شاشة التوقف
    private fun saverTab(): View = page {
        addView(text("تظهر بعد خمول الريموت. صور (تنتقل تدريجياً) أو فيديو قصير يتكرر.", 12f, Color.GRAY))
        addView(text("تبدأ بعد", 14f, Color.LTGRAY))
        val opts = listOf("إيقاف" to 0, "1 د" to 1, "3 د" to 3, "5 د" to 5, "10 د" to 10, "30 د" to 30)
        addView(hrow(*opts.take(3).map { (n, m) -> btn((if (cfg.ssMinutes == m) "✓ " else "") + n) { cfg = cfg.copy(ssMinutes = m); changed(); renderTab() } }.toTypedArray()))
        addView(hrow(*opts.drop(3).map { (n, m) -> btn((if (cfg.ssMinutes == m) "✓ " else "") + n) { cfg = cfg.copy(ssMinutes = m); changed(); renderTab() } }.toTypedArray()))
        addView(btn("إضافة صور أو فيديو من الهاتف") { pick(Protocol.KIND_SCREENSAVER) }, lp())
        addView(mediaStrip(savers, Protocol.KIND_SCREENSAVER))
        if (host.isNotEmpty() && savers.isEmpty()) loadMedia(Protocol.KIND_SCREENSAVER)
        addView(text("لو مفيش ملفات، بتظهر الخلفيات الجاهزة.", 12f, Color.GRAY))
    }

    // ------------------------------------------------------------------ وسائط
    private fun mediaStrip(items: List<MediaItem>, kind: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in items) {
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (m.thumb != null) setImageBitmap(m.thumb) else setBackgroundColor(Color.DKGRAY)
                setOnClickListener { mediaMenu(m, kind) }
            }
            val box = FrameLayout(this)
            box.addView(iv, FrameLayout.LayoutParams(dp(110), dp(62)))
            if (m.video) box.addView(text("فيديو", 11f, Color.WHITE).apply { setBackgroundColor(Color.parseColor("#99000000")) })
            row.addView(box, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 3))
        }
        if (items.isEmpty()) row.addView(text(if (host.isEmpty()) "اتصل بالشاشة لعرض الملفات" else "لا توجد ملفات مرفوعة", 12f, Color.GRAY))
        return HorizontalScrollView(this).apply { addView(row) }
    }

    private fun mediaMenu(m: MediaItem, kind: String) {
        val use = kind == Protocol.KIND_WALLPAPER
        val labels = if (use) arrayOf("استخدام كخلفية", "حذف") else arrayOf("حذف")
        AlertDialog.Builder(this).setItems(labels) { _, i ->
            if (use && i == 0) {
                cfg = cfg.copy(customWallpaper = m.name); changed(); resultText.text = "اضغط «تطبيق على الشاشة»"
            } else {
                val h = host; val p = pin
                io.execute {
                    try { RemoteClient.delete(h, p, kind, m.name) } catch (_: Exception) {}
                    ui {
                        if (cfg.customWallpaper == m.name) { cfg = cfg.copy(customWallpaper = ""); changed() }
                        loadMedia(kind)
                    }
                }
            }
        }.show()
    }

    private fun loadMedia(kind: String) {
        val h = host; val p = pin
        if (h.isEmpty()) return
        io.execute {
            try {
                val r = RemoteClient.media(h, p, kind)
                if (!r.optBoolean("ok")) return@execute
                val arr = r.getJSONArray("items")
                val list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    val b64 = o.optString("thumb")
                    val bmp = if (b64.isEmpty()) null else try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) { null }
                    MediaItem(o.getString("name"), o.optBoolean("video"), bmp)
                }
                ui {
                    if (kind == Protocol.KIND_WALLPAPER) wallpapers = list else savers = list
                    if ((kind == Protocol.KIND_WALLPAPER && tab == 1) || (kind == Protocol.KIND_SCREENSAVER && tab == 4)) renderTab()
                }
            } catch (_: Exception) {}
        }
    }

    private fun pick(kind: String) {
        if (host.isEmpty()) { showConnect(); return }
        pickKind = kind
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            if (kind == Protocol.KIND_WALLPAPER) type = "image/*"
            else { type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")) }
        }
        startActivityForResult(Intent.createChooser(i, "اختر من الهاتف"), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        data.data?.let { uris += it }
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris += c.getItemAt(i).uri }
        val kind = pickKind
        val h = host; val p = pin
        io.execute {
            var okCount = 0
            for ((n, u) in uris.distinct().withIndex()) {
                ui { resultText.text = "جاري رفع ${n + 1} من ${uris.distinct().size}…" }
                try { if (upload(h, p, kind, u)) okCount++ } catch (e: Exception) { }
            }
            ui { resultText.text = "تم رفع $okCount من ${uris.distinct().size}"; loadMedia(kind) }
        }
    }

    private fun upload(h: String, p: String, kind: String, uri: Uri): Boolean {
        val mime = contentResolver.getType(uri) ?: ""
        if (mime.startsWith("video/")) {
            val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: return false
            val ext = when (mime) { "video/webm" -> "webm"; "video/x-matroska" -> "mkv"; "video/3gpp" -> "3gp"; else -> "mp4" }
            val ins = contentResolver.openInputStream(uri) ?: return false
            ins.use { return RemoteClient.upload(h, p, kind, ext, size, it).optBoolean("ok") }
        }
        val bytes = scaledJpeg(uri) ?: return false
        return RemoteClient.upload(h, p, kind, "jpg", bytes.size.toLong(), ByteArrayInputStream(bytes)).optBoolean("ok")
    }

    /** تصغير الصورة لحد 1920×1080 (توفير مساحة وسرعة على الشاشة) مع تصحيح الدوران. */
    private fun scaledJpeg(uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var s = 1
        while (bounds.outWidth / (s * 2) >= 1920 && bounds.outHeight / (s * 2) >= 1080) s *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = s }
        var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } ?: return null
        val rot = try {
            contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val k = minOf(1f, 1920f / bmp.width, 1080f / bmp.height)
        if (k < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        val bo = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo)
        return bo.toByteArray()
    }

    // ------------------------------------------------------------------ الاتصال والإرسال
    private fun updateConn() {
        statusText.text = if (host.isEmpty()) "غير متصل — اضغط «الاتصال بالشاشة»" else "الشاشة: $host"
    }

    private fun syncFromTv() {
        val h = host
        io.execute {
            val msg = try {
                val r = RemoteClient.hello(h)
                val c = ThemeConfig.fromJson(r.getJSONObject("config"))
                ui { cfg = c; store.save(c); renderTab() }
                "متصل بالشاشة: ${r.optString("name")} ($h)"
            } catch (e: Exception) {
                "تعذر الوصول للشاشة ($h)"
            }
            ui { statusText.text = msg }
        }
    }

    private fun applyToTv() {
        if (host.isEmpty() || pin.isEmpty()) { showConnect(); return }
        val h = host; val p = pin; val c = cfg
        resultText.text = "جاري الإرسال…"
        io.execute {
            val msg = try {
                val r = RemoteClient.apply(h, p, c)
                when {
                    r.optBoolean("ok") -> "تم التطبيق على الشاشة ✓"
                    r.optString("error") == "bad_pin" -> "الـ PIN غير صحيح — راجعه من إعدادات الشاشة"
                    else -> "رفضت الشاشة الطلب"
                }
            } catch (e: Exception) {
                "تعذر الوصول للشاشة — تأكد إن الهاتف والشاشة على نفس الـ Wi-Fi"
            }
            ui { resultText.text = msg }
        }
    }

    private fun saveConn(ip: String, pn: String) {
        store.prefs.edit().putString("host", ip).putString("pin", pn).apply()
        updateConn(); syncFromTv(); loadApps()
    }

    private fun scanQr() {
        try {
            GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener { b ->
                    val parsed = b.rawValue?.let { Pairing.parse(it) }
                    if (parsed == null) toast("ده مش QR الخاص بنسيم الأقصى") else { saveConn(parsed.first, parsed.second); toast("تم الربط ✓") }
                }
                .addOnFailureListener { toast("تعذر المسح — اكتب IP والـ PIN يدوياً") }
        } catch (e: Exception) {
            toast("الماسح غير متاح — اكتب IP والـ PIN يدوياً")
        }
    }

    private fun showConnect() {
        val pad = dp(16)
        val found = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val etHost = EditText(this).apply { hint = "IP الشاشة (مثال 192.168.1.20)"; inputType = InputType.TYPE_CLASS_TEXT; setText(host) }
        val etPin = EditText(this).apply {
            hint = "PIN (6 أرقام)"; inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6)); setText(pin)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(Button(this@MainActivity).apply { text = "مسح QR من الشاشة (الإعدادات)"; setOnClickListener { scanQr() } })
            addView(TextView(this@MainActivity).apply { text = "الشاشات المكتشفة على الشبكة:" })
            addView(found)
            addView(etHost); addView(etPin)
        }
        val seen = HashSet<String>()
        val disc = NsdDiscovery(this) { ip ->
            if (ip.isNotEmpty() && seen.add(ip)) {
                found.addView(Button(this).apply { text = "شاشة: $ip"; setOnClickListener { etHost.setText(ip) } })
                if (etHost.text.isNullOrBlank()) etHost.setText(ip)
            }
        }
        disc.start()
        AlertDialog.Builder(this).setTitle("الاتصال بالشاشة").setView(root)
            .setPositiveButton("حفظ") { _, _ -> saveConn(etHost.text.toString().trim(), etPin.text.toString().trim()) }
            .setNegativeButton("إلغاء", null)
            .setOnDismissListener { disc.stop() }
            .show()
    }
}
