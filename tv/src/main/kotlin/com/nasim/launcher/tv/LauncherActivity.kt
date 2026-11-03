package com.nasim.launcher.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nasim.launcher.common.HijriUtil
import com.nasim.launcher.common.HomeIds
import com.nasim.launcher.common.OpenMeteo
import com.nasim.launcher.common.Prayer
import com.nasim.launcher.common.PrayerCalc
import com.nasim.launcher.common.Protocol
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStore
import com.nasim.launcher.common.ThemeStyle
import com.nasim.launcher.common.ThemeStyles
import com.nasim.launcher.common.Wallpapers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import com.nasim.launcher.common.R as CR

/** الشاشة الرئيسية (Launcher). مصدر الحقيقة الوحيد للإعدادات = SharedPreferences (ThemeStore). */
class LauncherActivity : Activity() {

    private class PrayerRow(val prayer: Prayer, val view: View, val name: TextView, val time: TextView, val bell: ImageView)

    private lateinit var store: ThemeStore
    private lateinit var repo: AppRepository
    private lateinit var bg: ImageView
    private lateinit var bg2: ImageView
    private lateinit var clock: TextClock
    private lateinit var dateText: TextView
    private lateinit var hijriText: TextView
    private lateinit var weatherText: TextView
    private lateinit var statusRow: View
    private lateinit var wifiText: TextView
    private lateinit var storageText: TextView
    private lateinit var storageBar: ProgressBar
    private lateinit var settingsBtn: TextView
    private lateinit var prayerPanel: View
    private lateinit var prayerHeader: TextView
    private lateinit var nextText: TextView
    private lateinit var prayerList: LinearLayout
    private lateinit var rv: RecyclerView
    private lateinit var adapter: TileAdapter
    private lateinit var ss: ScreensaverView

    private val prayerRows = ArrayList<PrayerRow>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val dateFmt = SimpleDateFormat("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn"))

    private var config = ThemeConfig()
    private var firstApply = true
    private var style: ThemeStyle = ThemeStyles.of(ThemeConfig.THEME_JERUSALEM)
    private var typeface: Typeface? = null
    private var allApps: List<InstalledApp> = emptyList()
    private var lastWeatherAt = 0L
    private var openDialog: Dialog? = null
    private var bgToken = 0
    private var rotIdx = -1
    private var lastInteract = SystemClock.elapsedRealtime()
    private var cachedTimes: Map<Prayer, Long> = emptyMap()
    private var cachedKey = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ThemeStore.KEY_CONFIG) applyConfig(store.load())
    }

    private val tick = object : Runnable {
        override fun run() {
            updateDates()
            refreshPrayers()
            if (style.showStatusBar) updateStatus()
            if (SystemClock.elapsedRealtime() - lastWeatherAt > 15 * 60_000L) fetchWeather()
            maybeStartScreensaver()
            main.postDelayed(this, 15_000L)
        }
    }

    private val rotate = object : Runnable {
        override fun run() {
            if (config.themeId != ThemeConfig.THEME_GALLERY) return
            nextGalleryBackground()
            main.postDelayed(this, config.rotateSec * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)
        store = ThemeStore(this)
        repo = AppRepository(this)

        bg = findViewById(R.id.bg)
        bg2 = findViewById(R.id.bg2)
        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.dateText)
        hijriText = findViewById(R.id.hijriText)
        weatherText = findViewById(R.id.weatherText)
        statusRow = findViewById(R.id.statusRow)
        wifiText = findViewById(R.id.wifiText)
        storageText = findViewById(R.id.storageText)
        storageBar = findViewById(R.id.storageBar)
        settingsBtn = findViewById(R.id.settingsBtn)
        prayerPanel = findViewById(R.id.prayerPanel)
        prayerHeader = findViewById(R.id.prayerHeader)
        nextText = findViewById(R.id.nextText)
        prayerList = findViewById(R.id.prayerList)
        rv = findViewById(R.id.rv)

        ss = ScreensaverView(this)
        findViewById<FrameLayout>(R.id.root).addView(
            ss, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val d = resources.displayMetrics.density
        adapter = TileAdapter((150 * d).toInt(), (116 * d).toInt(), (8 * d).toInt())
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rv.adapter = adapter
        rv.itemAnimator = null
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(8)

        buildPrayerRows()

        settingsBtn.setOnClickListener {
            openDialog = SettingsDialog(this, store, typeface).also { it.show() }
        }
        settingsBtn.setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(120).start()
        }

        applyConfig(store.load())
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        (application as TvApp).server.start()
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val c = store.load()
        if (c != config) applyConfig(c)
        lastInteract = SystemClock.elapsedRealtime()
        main.removeCallbacks(tick)
        tick.run()
        if (config.themeId == ThemeConfig.THEME_GALLERY) {
            main.removeCallbacks(rotate)
            main.postDelayed(rotate, config.rotateSec * 1000L)
        }
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        main.removeCallbacks(tick)
        main.removeCallbacks(rotate)
        if (ss.isShowing) ss.stop()
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        ss.release()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        openDialog?.dismiss()
        if (ss.isShowing) ss.stop()
        rv.scrollToPosition(0)
        focusFirst()
    }

    @Deprecated("Launcher: Back لا يعمل شيئاً")
    override fun onBackPressed() { if (ss.isShowing) ss.stop() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInteract = SystemClock.elapsedRealtime()
        if (ss.isShowing) {
            if (event.action == KeyEvent.ACTION_DOWN) ss.stop()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastInteract = SystemClock.elapsedRealtime()
        if (ss.isShowing) { ss.stop(); return true }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        lastInteract = SystemClock.elapsedRealtime()
        if (ss.isShowing) { ss.stop(); return true }
        return super.dispatchGenericMotionEvent(ev)
    }

    // ------------------------------------------------------------------ theme
    private fun applyConfig(c: ThemeConfig) {
        val old = config
        val first = firstApply
        firstApply = false
        config = c
        style = ThemeStyles.of(c.themeId)

        typeface = pickTypeface()
        for (t in arrayOf(clock, dateText, hijriText, weatherText, wifiText, storageText, settingsBtn, prayerHeader, nextText)) {
            t.typeface = typeface
        }
        clock.setTextColor(style.textColor)
        dateText.setTextColor(style.textColor)
        hijriText.setTextColor(style.subTextColor)
        weatherText.setTextColor(style.subTextColor)
        wifiText.setTextColor(style.subTextColor)
        storageText.setTextColor(style.subTextColor)
        settingsBtn.setTextColor(style.textColor)
        settingsBtn.setBackgroundResource(style.cardBg)
        prayerPanel.setBackgroundResource(style.panelBg)
        prayerHeader.setTextColor(style.accent)
        nextText.setTextColor(style.nextColor)

        clock.format12Hour = if (c.use24h) "HH:mm" else "h:mm a"
        clock.format24Hour = "HH:mm"
        statusRow.visibility = if (style.showStatusBar) View.VISIBLE else View.GONE
        weatherText.visibility = if (c.showWeather) View.VISIBLE else View.GONE
        if (!c.showWeather) weatherText.text = ""

        adapter.typeface = typeface
        if (first || old.themeId != c.themeId || old.home != c.home) {
            rebuildRow(first || old.home != c.home)
        }

        cachedKey = ""
        updateDates()
        refreshPrayers()
        if (style.showStatusBar) updateStatus()
        if (first || old.themeId != c.themeId || old.customWallpaper != c.customWallpaper || old.rotateSec != c.rotateSec) {
            applyBackground()
        }
        if (old.lat != c.lat || old.lon != c.lon || old.cityName != c.cityName || lastWeatherAt == 0L) fetchWeather()
    }

    private fun pickTypeface(): Typeface {
        val id = resources.getIdentifier("theme_font", "font", packageName)
        if (id != 0) try { return resources.getFont(id) } catch (_: Exception) {}
        return if (style.useSerif) Typeface.create(Typeface.SERIF, Typeface.BOLD)
        else Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    // ------------------------------------------------------------------ background
    private fun applyBackground() {
        main.removeCallbacks(rotate)
        bgToken++
        if (config.themeId == ThemeConfig.THEME_GALLERY) {
            rotIdx = -1
            nextGalleryBackground()
            main.postDelayed(rotate, config.rotateSec * 1000L)
            return
        }
        val custom = config.customWallpaper
        val f = if (custom.isNotEmpty()) MediaFiles.safeFile(this, Protocol.KIND_WALLPAPER, custom)?.takeIf { it.exists() } else null
        showBackground(f ?: style.background)
    }

    private fun nextGalleryBackground() {
        val files = MediaFiles.list(this, Protocol.KIND_WALLPAPER)
        val items = ArrayList<Any>()
        for (r in Wallpapers.builtin) items.add(r)
        for (f in files) items.add(f)
        rotIdx = (rotIdx + 1) % items.size
        showBackground(items[rotIdx])
    }

    private fun showBackground(src: Any) {
        val my = ++bgToken
        io.execute {
            val bmp = if (src is File) Bmp.decodeFile(src.path) else Bmp.decodeRes(this, src as Int)
            main.post {
                if (isDestroyed || my != bgToken || bmp == null) return@post
                crossfade(bmp)
            }
        }
    }

    private fun crossfade(bm: Bitmap) {
        bg2.animate().cancel()
        if (bg2.drawable != null) bg.setImageDrawable(bg2.drawable)
        if (bg.drawable == null) {
            bg.setImageBitmap(bm)
            return
        }
        bg2.setImageBitmap(bm)
        bg2.alpha = 0f
        bg2.animate().alpha(1f).setDuration(700).withEndAction {
            bg.setImageDrawable(bg2.drawable)
            bg2.alpha = 0f
        }.start()
    }

    // ------------------------------------------------------------------ dates + prayers
    private fun updateDates() {
        dateText.text = dateFmt.format(Date())
        hijriText.text = HijriUtil.format(config.hijriOffset)
    }

    private fun buildPrayerRows() {
        val inflater = LayoutInflater.from(this)
        var prev: View? = null
        for (p in Prayer.values()) {
            val v = inflater.inflate(R.layout.item_prayer, prayerList, false)
            v.id = View.generateViewId()
            val row = PrayerRow(p, v, v.findViewById(R.id.pName), v.findViewById(R.id.pTime), v.findViewById(R.id.pBell))
            row.bell.visibility = View.GONE
            row.name.text = p.ar
            v.isFocusable = false
            prayerList.addView(v)
            prayerRows += row
            prev = v
        }
    }

    private fun refreshPrayers() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val key = "${config.lat},${config.lon},${config.prayerMethod},${config.asrHanafi},${cal.get(Calendar.DAY_OF_YEAR)}"
        if (key != cachedKey) {
            cachedTimes = PrayerCalc.times(config, now)
            cachedKey = key
        }
        val next = PrayerCalc.next(config, now)
        val fmt = SimpleDateFormat(if (config.use24h) "HH:mm" else "h:mm a", Locale.forLanguageTag("ar-u-nu-latn"))
        prayerHeader.text = "مواقيت الصلاة • ${config.cityName}"
        for (r in prayerRows) {
            r.name.setTextColor(style.textColor)
            r.time.setTextColor(style.textColor)
            r.name.typeface = typeface
            r.time.typeface = typeface
            r.view.setBackgroundResource(style.rowBg)
            r.view.isActivated = r.prayer == next.first
            r.time.text = cachedTimes[r.prayer]?.let { fmt.format(Date(it)) } ?: "--:--"
        }
        val mins = ((next.second - now) / 60_000L).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        val left = if (h > 0) String.format(Locale.US, "%d:%02d ساعة", h, m) else "$m دقيقة"
        nextText.text = "الصلاة القادمة: ${next.first.ar} بعد $left"
    }

    // ------------------------------------------------------------------ screensaver
    private fun maybeStartScreensaver() {
        if (config.ssMinutes <= 0 || ss.isShowing || openDialog?.isShowing == true) return
        if (SystemClock.elapsedRealtime() - lastInteract < config.ssMinutes * 60_000L) return
        ss.start(MediaFiles.list(this, Protocol.KIND_SCREENSAVER))
    }

    // ------------------------------------------------------------------ apps row
    private fun loadApps() {
        io.execute {
            val apps = repo.loadAll()
            main.post {
                if (isDestroyed) return@post
                allApps = apps
                rebuildRow(false)
            }
        }
    }

    private fun rebuildRow(focus: Boolean) {
        val tiles = ArrayList<Tile>()
        for (id in config.home) {
            val t: Tile? = when (id) {
                HomeIds.STORE -> shortcut("المتجر", CR.drawable.ic_store,
                    listOf("com.android.vending", "cm.aptoide.pt", "com.apkpure.aegon"))
                HomeIds.MEDIA -> shortcut("مركز الوسائط", CR.drawable.ic_media,
                    listOf("org.xbmc.kodi", "com.plexapp.android", "com.mxtech.videoplayer.ad", "com.google.android.videos"))
                HomeIds.YOUTUBE -> shortcut("يوتيوب", CR.drawable.ic_youtube,
                    listOf("com.google.android.youtube.tv", "com.liskovsoft.smarttubetv", "com.google.android.youtube"))
                HomeIds.INPUTS -> Tile("المدخلات", getDrawable(CR.drawable.ic_hdmi)) { showInputs() }
                HomeIds.QURAN -> Tile("القرآن الكريم", getDrawable(CR.drawable.ic_quran)) {
                    openDialog = QuranDialog(this, style, typeface).also { it.show() }
                }
                HomeIds.MORE -> Tile("المزيد", getDrawable(CR.drawable.ic_more)) { showAllApps() }
                else -> allApps.firstOrNull { it.pkg == id }?.let { app -> Tile(app.label, app.icon) { launch(app) } }
            }
            if (t != null) tiles += t
        }
        adapter.submit(tiles, style)
        if (focus) focusFirst()
    }

    private fun shortcut(label: String, fallbackIcon: Int, pkgs: List<String>): Tile {
        val app = pkgs.firstNotNullOfOrNull { p -> allApps.firstOrNull { it.pkg == p } }
        return if (app != null) Tile(label, app.icon) { launch(app) }
        else Tile(label, getDrawable(fallbackIcon)) { toast("$label غير مثبت على الجهاز") }
    }

    private fun launch(app: InstalledApp) {
        try { startActivity(app.intent) } catch (e: Exception) { toast("تعذر فتح ${app.label}") }
    }

    private fun showAllApps() {
        openDialog = AllAppsDialog(this, allApps, style, typeface) { launch(it) }.also { it.show() }
    }

    // ------------------------------------------------------------------ inputs (HDMI وغيره)
    private fun inputTypeName(t: Int): String = when (t) {
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_COMPOSITE -> "AV"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_SVIDEO -> "S-Video"
        TvInputInfo.TYPE_SCART -> "SCART"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_DVI -> "DVI"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        else -> "مدخل"
    }

    private fun loadInputs(): List<InputEntry> {
        val out = ArrayList<InputEntry>()
        try {
            val tv = getSystemService(TV_INPUT_SERVICE) as TvInputManager
            for (i in tv.tvInputList) {
                if (!i.isPassthroughInput) continue
                val custom = try { i.loadCustomLabel(this)?.toString() } catch (_: Exception) { null }
                val std = try { i.loadLabel(this)?.toString() } catch (_: Exception) { null }
                val label = listOf(custom, std).firstOrNull { !it.isNullOrBlank() } ?: inputTypeName(i.type)
                out += InputEntry(i.id, label, inputTypeName(i.type))
            }
        } catch (_: Exception) {
        }
        return out.sortedWith(compareBy({ if (it.type == "HDMI") 0 else 1 }, { it.label }))
    }

    private fun showInputs() {
        val list = loadInputs()
        openDialog = InputsDialog(this, list, style, typeface, { openInput(it) }, { openOemSource() }, {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { toast("تعذر فتح الإعدادات") }
        }).also { it.show() }
    }

    /** يجرّب أكتر من طريقة لفتح المدخل (تختلف من جهاز لجهاز). */
    private fun openInput(e: InputEntry) {
        val uri = TvContract.buildChannelUriForPassthroughInput(e.id)
        val attempts = ArrayList<Intent>()
        attempts += Intent(Intent.ACTION_VIEW, uri)
        for (pkg in listOf("com.google.android.tv", "com.android.tv", "com.google.android.tvlauncher", "com.droidlogic.tvsource")) {
            attempts += Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        }
        for (a in attempts) {
            try {
                a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(a)
                return
            } catch (_: Exception) {
            }
        }
        if (!openOemSource()) toast("تعذر فتح ${e.label} — جرّب «مصدر الإدخال» أو «إعدادات الجهاز»")
    }

    /** تطبيقات اختيار المصدر الخاصة بشركات الأجهزة (لو موجودة). */
    private fun openOemSource(): Boolean {
        val pm = packageManager
        val pkgs = listOf(
            "com.droidlogic.tvsource", "com.mediatek.tv.source", "com.mstar.tv.tvplayer.ui",
            "com.tcl.tv", "com.hisense.tv", "com.skyworth.tv", "com.android.tv.settings"
        )
        for (p in pkgs) {
            val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) ?: continue
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return true
            } catch (_: Exception) {
            }
        }
        try {
            startActivity(Intent("android.settings.INPUT_SOURCE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: Exception) {
        }
        toast("لم يتم العثور على تطبيق مصدر الإدخال في جهازك")
        return false
    }

    private fun focusFirst() {
        rv.post {
            if (openDialog?.isShowing == true) return@post
            rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    // ------------------------------------------------------------------ weather / status
    private fun fetchWeather() {
        if (!config.showWeather) return
        val c = config
        lastWeatherAt = SystemClock.elapsedRealtime()
        weatherText.text = c.cityName
        io.execute {
            val w = OpenMeteo.fetchWeather(c.lat, c.lon)
            main.post {
                if (isDestroyed || !config.showWeather) return@post
                weatherText.text = if (w == null) c.cityName
                else "${c.cityName} • ${w.tempC}° ${OpenMeteo.describe(w.code)}"
            }
        }
    }

    private fun updateStatus() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork
        val caps = if (net == null) null else cm.getNetworkCapabilities(net)
        wifiText.text = when {
            caps == null -> "لا يوجد اتصال"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi متصل"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet متصل"
            else -> "متصل"
        }
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes
        val used = total - st.availableBytes
        storageText.text = String.format(Locale.US, "التخزين %.1f / %.1f GB", used / 1e9, total / 1e9)
        storageBar.progress = if (total > 0) ((used * 100) / total).toInt() else 0
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
