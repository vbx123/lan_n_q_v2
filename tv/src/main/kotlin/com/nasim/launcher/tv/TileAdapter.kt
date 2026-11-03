package com.nasim.launcher.tv

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStyle
import com.nasim.launcher.common.ThemeStyles

/**
 * Adapter خفيف: بنعمل الـ focus animation (تكبير 8%) على الـ View نفسه،
 * والإطار الملوّن (highlight) جاي من selector في الـ drawable (state_focused).
 */
class TileAdapter(
    private val widthPx: Int,   // أو ViewGroup.LayoutParams.MATCH_PARENT
    private val heightPx: Int,
    private val marginPx: Int
) : RecyclerView.Adapter<TileAdapter.VH>() {

    private var tiles: List<Tile> = emptyList()
    private var style: ThemeStyle = ThemeStyles.of(ThemeConfig.THEME_JERUSALEM)
    var typeface: Typeface? = null

    fun submit(t: List<Tile>, s: ThemeStyle) {
        tiles = t
        style = s
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        v.layoutParams = RecyclerView.LayoutParams(widthPx, heightPx).apply {
            setMargins(marginPx, marginPx, marginPx, marginPx)
        }
        v.isFocusable = true
        v.isClickable = true // الـ D-Pad Center / Enter بيعمل click تلقائياً
        v.onFocusChangeListener = View.OnFocusChangeListener { view, has ->
            val s = if (has) 1.08f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = tiles[position]
        h.itemView.setBackgroundResource(style.cardBg)
        h.itemView.setOnClickListener { t.onClick() }
        h.icon.setImageDrawable(t.icon)
        h.label.text = t.label
        h.label.setTextColor(style.textColor)
        h.label.typeface = typeface
    }

    override fun getItemCount() = tiles.size
}
