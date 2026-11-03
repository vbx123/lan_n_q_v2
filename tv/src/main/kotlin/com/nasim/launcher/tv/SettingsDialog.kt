package com.nasim.launcher.tv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nasim.launcher.common.CityPresets
import com.nasim.launcher.common.Pairing
import com.nasim.launcher.common.PrayerCalc
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStore

/** الإعدادات السريعة على الشاشة + QR للربط مع الهاتف. */
class SettingsDialog(
    ctx: Context,
    private val store: ThemeStore,
    private val tf: Typeface?
) : Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private lateinit var btnJer: Button
    private lateinit var btnCyber: Button
    private lateinit var btnGallery: Button
    private lateinit var btnCity: Button
    private lateinit var btnTime: Button
    private lateinit var btnWeather: Button
    private lateinit var btnHijri: Button
    private lateinit var btnMethod: Button
    private lateinit var btnSs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        fun label(t: String, size: Float, color: Int = Color.WHITE) = TextView(context).apply {
            text = t; textSize = size; setTextColor(color); typeface = tf
            setPadding(0, dp(10), 0, dp(4))
        }
        fun button(onClick: () -> Unit) = Button(context).apply {
            typeface = tf; isAllCaps = false; setOnClickListener { onClick(); refresh() }
        }
        fun mutate(f: (ThemeConfig) -> ThemeConfig) = store.save(f(store.load()))

        btnJer = button { mutate { it.copy(themeId = ThemeConfig.THEME_JERUSALEM) } }
        btnCyber = button { mutate { it.copy(themeId = ThemeConfig.THEME_CYBER) } }
        btnGallery = button { mutate { it.copy(themeId = ThemeConfig.THEME_GALLERY) } }
        btnCity = button {
            val list = CityPresets.list
            val cur = store.load()
            val next = list[(list.indexOfFirst { it.name == cur.cityName } + 1) % list.size]
            mutate { it.copy(cityName = next.name, lat = next.lat, lon = next.lon) }
        }
        btnTime = button { mutate { it.copy(use24h = !it.use24h) } }
        btnWeather = button { mutate { it.copy(showWeather = !it.showWeather) } }
        btnHijri = button { mutate { it.copy(hijriOffset = if (it.hijriOffset >= 2) -2 else it.hijriOffset + 1) } }
        btnMethod = button {
            val ms = PrayerCalc.methods
            mutate { c -> c.copy(prayerMethod = ms[(ms.indexOfFirst { it.id == c.prayerMethod } + 1) % ms.size].id) }
        }
        btnSs = button {
            val opts = listOf(0, 1, 3, 5, 10, 15, 30)
            mutate { c -> c.copy(ssMinutes = opts[(opts.indexOf(c.ssMinutes) + 1) % opts.size]) }
        }
        val a11y = Button(context).apply {
            typeface = tf; isAllCaps = false
            text = "تفعيل زر الرجوع/الرئيسية من الهاتف داخل التطبيقات (إتاحة)"
            setOnClickListener {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        }
        val close = Button(context).apply { text = "إغلاق"; typeface = tf; setOnClickListener { dismiss() } }

        val themes = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in arrayOf(btnJer, btnCyber, btnGallery)) {
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        // QR + بيانات الربط
        val ip = NetUtil.localIp()
        val pin = store.pin()
        val qr = ImageView(context).apply {
            setImageBitmap(QrUtil.bitmap(Pairing.uri(ip, pin), dp(200)))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val pairInfo = TextView(context).apply {
            text = "افتح تطبيق «نسيم الأقصى ريموت» على الهاتف واضغط «مسح QR».\nأو اكتب يدوياً:\nIP: $ip\nPIN: $pin"
            textSize = 15f; setTextColor(Color.parseColor("#FFE9B5")); typeface = tf
            setPadding(dp(16), 0, 0, 0)
        }
        val pairRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(qr, LinearLayout.LayoutParams(dp(212), dp(212)))
            addView(pairInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(28), dp(20), dp(28), dp(20))
            addView(label("الإعدادات السريعة", 24f))
            addView(label("الثيم", 15f, Color.LTGRAY))
            addView(themes)
            addView(label("الموقع (الوقت والطقس والمواقيت)", 15f, Color.LTGRAY))
            addView(btnCity)
            addView(btnMethod)
            addView(btnHijri)
            addView(btnTime)
            addView(btnWeather)
            addView(btnSs)
            addView(a11y)
            addView(label("الربط مع الهاتف", 15f, Color.LTGRAY))
            addView(pairRow)
            addView(close)
        }
        setContentView(ScrollView(context).apply { addView(col) })
        window?.setBackgroundDrawableResource(R.drawable.dialog_bg)
        refresh()
        btnJer.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        window?.setLayout((dm.widthPixels * 0.66).toInt(), (dm.heightPixels * 0.9).toInt())
    }

    private fun refresh() {
        val c = store.load()
        fun mark(on: Boolean, t: String) = (if (on) "✓ " else "") + t
        btnJer.text = mark(c.themeId == ThemeConfig.THEME_JERUSALEM, "نسيم الأقصى")
        btnCyber.text = mark(c.themeId == ThemeConfig.THEME_CYBER, "السيبراني")
        btnGallery.text = mark(c.themeId == ThemeConfig.THEME_GALLERY, "خلفيات متغيرة")
        btnCity.text = "الموقع: ${c.cityName}"
        btnMethod.text = "طريقة حساب المواقيت: ${PrayerCalc.method(c.prayerMethod).ar}"
        btnHijri.text = "تعديل التاريخ الهجري: ${if (c.hijriOffset > 0) "+" else ""}${c.hijriOffset} يوم"
        btnTime.text = "نظام الوقت: " + if (c.use24h) "24 ساعة" else "12 ساعة"
        btnWeather.text = "إظهار الطقس: " + if (c.showWeather) "نعم" else "لا"
        btnSs.text = "شاشة التوقف: " + if (c.ssMinutes == 0) "إيقاف" else "بعد ${c.ssMinutes} دقيقة"
    }
}
