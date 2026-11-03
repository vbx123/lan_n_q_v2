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

/** نافذة "المزيد": شبكة بكل التطبيقات. الـ D-Pad بيتنقل تلقائياً بين العناصر. */
class AllAppsDialog(
    ctx: Context,
    private val apps: List<InstalledApp>,
    private val style: ThemeStyle,
    private val tf: Typeface?,
    private val onLaunch: (InstalledApp) -> Unit
) : Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = context.resources.displayMetrics.density

        rv = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 5)
            itemAnimator = null
            clipChildren = false
            clipToPadding = false
            val p = (16 * d).toInt()
            setPadding(p, p, p, p)
        }
        val ad = TileAdapter(ViewGroup.LayoutParams.MATCH_PARENT, (112 * d).toInt(), (6 * d).toInt())
        ad.typeface = tf
        ad.submit(apps.map { a -> Tile(a.label, a.icon) { dismiss(); onLaunch(a) } }, style)
        rv.adapter = ad

        val title = TextView(context).apply {
            text = "كل التطبيقات"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = tf
            gravity = Gravity.START
            setPadding((24 * d).toInt(), (18 * d).toInt(), (24 * d).toInt(), 0)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(title)
            addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        window?.setBackgroundDrawableResource(R.drawable.dialog_bg)
    }

    override fun onStart() {
        super.onStart()
        val dm = context.resources.displayMetrics
        window?.setLayout((dm.widthPixels * 0.82).toInt(), (dm.heightPixels * 0.82).toInt())
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }
}
