package com.nasim.launcher.tv

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nasim.launcher.common.ThemeStyle
import java.util.Locale
import java.util.concurrent.Executors

/** القرآن الكريم: (1) إذاعات القرآن المشهورة  (2) اختيار قارئ ثم البحث عن السورة. */
class QuranDialog(
    ctx: Context,
    private val style: ThemeStyle,
    private val tf: Typeface?
) : Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private enum class Mode { RADIO, RECITERS, SURAHS }

    private class Row(val text: String, val key: String, val onClick: () -> Unit)

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        var rows: List<Row> = emptyList()
        var activeKey: String = ""

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = parent.context.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (54 * d).toInt()).apply {
                    setMargins(0, (3 * d).toInt(), 0, (3 * d).toInt())
                }
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding((20 * d).toInt(), 0, (20 * d).toInt(), 0)
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = tf
                isFocusable = true
                isClickable = true
                setBackgroundResource(style.rowBg)
            }
            return VH(tv)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = rows[position]
            h.tv.text = r.text
            h.tv.isActivated = r.key == activeKey
            h.tv.setOnClickListener { r.onClick() }
        }

        override fun getItemCount() = rows.size
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val adapter = RowAdapter()

    private var mode = Mode.RADIO
    private var reciter: Reciter? = null
    private var allRows: List<Row> = emptyList()

    private lateinit var rv: RecyclerView
    private lateinit var search: EditText
    private lateinit var title: TextView
    private lateinit var tabRadio: Button
    private lateinit var tabRec: Button
    private lateinit var nowText: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        fun btn(text: String, onClick: () -> Unit) = Button(context).apply {
            this.text = text
            typeface = tf
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(style.cardBg)
            setPadding(dp(18), dp(6), dp(18), dp(6))
            setOnClickListener { onClick() }
        }
        fun lp(m: Int = 6) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(m), 0, dp(m), 0) }

        title = TextView(context).apply {
            textSize = 24f; setTextColor(Color.WHITE); typeface = tf
        }
        tabRadio = btn("الإذاعات") { showRadios() }
        tabRec = btn("القرّاء") { showReciters() }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(tabRadio, lp())
            addView(tabRec, lp())
        }

        search = EditText(context).apply {
            hint = "ابحث…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            typeface = tf
            textSize = 18f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { applyFilter() }
            })
        }

        rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            adapter = this@QuranDialog.adapter
            setPadding(0, dp(4), 0, dp(4))
            clipToPadding = false
        }

        nowText = TextView(context).apply {
            textSize = 16f; setTextColor(Color.parseColor("#FFE9B5")); typeface = tf
            maxLines = 1
        }
        btnPrev = btn("السابقة") { PlaybackService.start(context, PlaybackService.ACTION_PREV) }
        btnPlay = btn("تشغيل") { PlaybackService.start(context, PlaybackService.ACTION_TOGGLE) }
        btnNext = btn("التالية") { PlaybackService.start(context, PlaybackService.ACTION_NEXT) }
        btnStop = btn("إيقاف") { PlaybackService.start(context, PlaybackService.ACTION_STOP) }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(nowText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnPrev, lp(4)); addView(btnPlay, lp(4)); addView(btnNext, lp(4)); addView(btnStop, lp(4))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(24), dp(18), dp(24), dp(16))
            addView(header)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }
        setContentView(root)
        window?.setBackgroundDrawableResource(R.drawable.dialog_bg)

        showRadios()
        // تحديث الروابط من الإنترنت (لو فيه) ثم إعادة العرض
        io.execute {
            QuranRepo.refresh(context)
            main.post {
                if (!isShowing) return@post
                if (mode == Mode.RADIO && search.text.isNullOrEmpty()) showRadios(keepFocus = true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        window?.setLayout((dm.widthPixels * 0.84).toInt(), (dm.heightPixels * 0.88).toInt())
        PlaybackService.listener = { updateBar() }
        updateBar()
    }

    override fun onStop() {
        PlaybackService.listener = null
        super.onStop()
    }

    override fun dismiss() {
        io.shutdownNow()
        super.dismiss()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mode == Mode.SURAHS) showReciters() else dismiss()
    }

    // ------------------------------------------------------------------ screens
    private fun showRadios(keepFocus: Boolean = false) {
        mode = Mode.RADIO
        reciter = null
        title.text = "إذاعات القرآن الكريم"
        tabRadio.isSelected = true; tabRec.isSelected = false
        allRows = QuranRepo.radios().map { r ->
            Row(r.name, r.url) { PlaybackService.playRadio(context, r.name, r.url) }
        }
        setRows(keepFocus)
    }

    private fun showReciters() {
        mode = Mode.RECITERS
        reciter = null
        title.text = "اختر القارئ"
        tabRadio.isSelected = false; tabRec.isSelected = true
        allRows = QuranData.reciters.map { r -> Row(r.name, r.id) { showSurahs(r) } }
        setRows(false)
    }

    private fun showSurahs(r: Reciter) {
        mode = Mode.SURAHS
        reciter = r
        title.text = "${r.name} — اختر السورة"
        val server = QuranRepo.serverOf(r)
        allRows = (1..114).map { n ->
            val key = server + String.format(Locale.US, "%03d.mp3", n)
            Row("$n.  سورة ${QuranData.surahs[n - 1]}", key) { PlaybackService.playSurah(context, r.name, server, n) }
        }
        setRows(false)
    }

    private fun setRows(keepFocus: Boolean) {
        if (!keepFocus) search.setText("")
        applyFilter()
        if (!keepFocus) rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }

    private fun applyFilter() {
        val q = search.text?.toString()?.trim().orEmpty()
        adapter.rows = if (q.isEmpty()) allRows else allRows.filter { it.text.contains(q) }
        adapter.activeKey = PlaybackService.instance?.lastUrl.orEmpty()
        adapter.notifyDataSetChanged()
    }

    private fun updateBar() {
        val s = PlaybackService.instance
        if (s == null || (s.state == PlaybackService.State.IDLE && s.title.isBlank())) {
            nowText.text = "لا يوجد تشغيل حالياً"
            btnPlay.text = "تشغيل"
            btnPrev.visibility = View.GONE
            btnNext.visibility = View.GONE
        } else {
            val st = when (s.state) {
                PlaybackService.State.LOADING -> "جاري التحميل…"
                PlaybackService.State.PLAYING -> "يعمل الآن"
                PlaybackService.State.PAUSED -> "متوقف مؤقتاً"
                PlaybackService.State.ERROR -> "تعذر التشغيل (تأكد من الإنترنت)"
                else -> ""
            }
            nowText.text = "${s.title} • ${s.sub} • $st"
            btnPlay.text = if (s.state == PlaybackService.State.PLAYING || s.state == PlaybackService.State.LOADING) "إيقاف مؤقت" else "تشغيل"
            val v = if (s.canStep) View.VISIBLE else View.GONE
            btnPrev.visibility = v
            btnNext.visibility = v
        }
        adapter.activeKey = s?.lastUrl.orEmpty()
        adapter.notifyDataSetChanged()
    }
}
