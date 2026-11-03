package com.nasim.launcher.tv

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nasim.launcher.common.ThemeStyle
import com.nasim.launcher.common.R as CR

data class InputEntry(val id: String, val label: String, val type: String)

/** نافذة "المدخلات": كل مخارج/مداخل الشاشة (HDMI 1/2/3 وAV وغيرها) في قائمة واحدة. */
class InputsDialog(
    ctx: Context,
    private val entries: List<InputEntry>,
    private val style: ThemeStyle,
    private val tf: Typeface?,
    private val onOpen: (InputEntry) -> Unit,
    private val onOem: () -> Unit,
    private val onSettings: () -> Unit
) : Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val title = TextView(context).apply {
            text = "المدخلات"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = tf
            setPadding(dp(24), dp(18), dp(24), dp(4))
        }
        val hint = TextView(context).apply {
            text = if (entries.isEmpty()) "لم يعثر النظام على مداخل تلقائياً — جرّب الخيارات بالأسفل"
            else "اختر المدخل اللي عاوز تفتحه"
            textSize = 15f
            setTextColor(Color.parseColor("#FFE9B5"))
            typeface = tf
            setPadding(dp(24), 0, dp(24), dp(8))
        }

        val tiles = ArrayList<Tile>()
        val icon = context.getDrawable(CR.drawable.ic_hdmi)
        for (e in entries) {
            tiles += Tile(e.label, icon) { dismiss(); onOpen(e) }
        }
        tiles += Tile("مصدر الإدخال (تطبيق الجهاز)", context.getDrawable(CR.drawable.ic_more)) { dismiss(); onOem() }
        tiles += Tile("إعدادات الجهاز", context.getDrawable(CR.drawable.ic_more)) { dismiss(); onSettings() }

        rv = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            itemAnimator = null
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val ad = TileAdapter(ViewGroup.LayoutParams.MATCH_PARENT, dp(112), dp(6))
        ad.typeface = tf
        ad.submit(tiles, style)
        rv.adapter = ad

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.START
            addView(title)
            addView(hint)
            addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        window?.setBackgroundDrawableResource(R.drawable.dialog_bg)
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        window?.setLayout((dm.widthPixels * 0.72).toInt(), (dm.heightPixels * 0.72).toInt())
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }
}
