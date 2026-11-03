package com.nasim.launcher.mobile

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nasim.launcher.common.R as CR

/** شاشة تلفزيون مصغّرة (16:9) بتحاكي اللانشر الحقيقي، وبتستخدم نفس الخلفيات والـ drawables من :common. */
class TvPreviewView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    FrameLayout(ctx, attrs) {

    private val bg: ImageView
    private val clock: TextClock
    private val date: TextView
    private val weather: TextView
    private val status: View
    private val settings: TextView
    private val cards = ArrayList<TextView>()
    private val dateFmt = SimpleDateFormat("EEEE، d MMMM", Locale.forLanguageTag("ar-u-nu-latn"))

    init {
        LayoutInflater.from(ctx).inflate(R.layout.view_tv_preview, this, true)
        bg = findViewById(R.id.pvBg)
        clock = findViewById(R.id.pvClock)
        date = findViewById(R.id.pvDate)
        weather = findViewById(R.id.pvWeather)
        status = findViewById(R.id.pvStatus)
        settings = findViewById(R.id.pvSettings)

        val row = findViewById<LinearLayout>(R.id.pvCards)
        val d = resources.displayMetrics.density
        val items = listOf(
            "المتجر" to CR.drawable.ic_store, "الوسائط" to CR.drawable.ic_media,
            "يوتيوب" to CR.drawable.ic_youtube, "المدخلات" to CR.drawable.ic_hdmi,
            "القرآن" to CR.drawable.ic_quran, "المزيد" to CR.drawable.ic_more
        )
        for ((i, it) in items.withIndex()) {
            val tv = TextView(ctx).apply {
                text = it.first
                textSize = 7f
                gravity = Gravity.CENTER
                compoundDrawablePadding = (2 * d).toInt()
                val ic = context.getDrawable(it.second)
                ic?.setBounds(0, 0, (16 * d).toInt(), (16 * d).toInt())
                setCompoundDrawables(null, ic, null, null)
                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                isSelected = (i == 2) // يحاكي إن الريموت واقف على كارت (Focus Highlight)
            }
            row.addView(tv, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0)
            })
            cards += tv
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY)
        )
    }

    fun render(c: ThemeConfig) {
        val s = ThemeStyles.of(c.themeId)
        bg.setImageResource(s.background)
        val tf = if (s.useSerif) Typeface.create(Typeface.SERIF, Typeface.BOLD)
        else Typeface.create("sans-serif-light", Typeface.NORMAL)
        for (t in arrayOf(clock, date, weather)) t.typeface = tf
        clock.format12Hour = if (c.use24h) "HH:mm" else "h:mm a"
        clock.format24Hour = "HH:mm"
        date.text = dateFmt.format(Date())
        weather.setTextColor(s.subTextColor)
        weather.text = if (c.showWeather) "${c.cityName} • 24° صافٍ" else ""   // قيمة تجريبية للمعاينة فقط
        status.visibility = if (s.showStatusBar) VISIBLE else GONE
        settings.setBackgroundResource(s.cardBg)
        for (t in cards) {
            t.setBackgroundResource(s.cardBg)
            t.setTextColor(s.textColor)
            t.typeface = tf
        }
    }
}
