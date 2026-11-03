package com.nasim.launcher.common

import android.graphics.Color
import androidx.annotation.DrawableRes

/** كل ما يخص شكل الثيم (موارد + ألوان) في مكان واحد، يستخدمه التلفزيون وشاشة المعاينة. */
data class ThemeStyle(
    @DrawableRes val background: Int,
    @DrawableRes val cardBg: Int,
    @DrawableRes val panelBg: Int,
    @DrawableRes val rowBg: Int,
    val textColor: Int,
    val subTextColor: Int,
    val accent: Int,
    val nextColor: Int,
    val showStatusBar: Boolean,
    val useSerif: Boolean
)

object Wallpapers {
    val builtin = intArrayOf(
        R.drawable.bg_gallery_1, R.drawable.bg_gallery_2, R.drawable.bg_gallery_3,
        R.drawable.bg_gallery_4, R.drawable.bg_gallery_5, R.drawable.bg_gallery_6
    )
}

object ThemeStyles {
    private val gold = Color.parseColor("#E8C26A")
    private val goldSoft = Color.parseColor("#FFE9B5")
    private val emerald = Color.parseColor("#3EE0A1")

    fun of(id: String): ThemeStyle = when (id) {
        ThemeConfig.THEME_CYBER -> ThemeStyle(
            R.drawable.bg_cyber, R.drawable.card_cyber, R.drawable.panel_cyber, R.drawable.row_cyber,
            Color.WHITE, Color.parseColor("#9FE8FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FF4DDE"),
            true, false
        )
        ThemeConfig.THEME_GALLERY -> ThemeStyle(
            R.drawable.bg_gallery_1, R.drawable.card_gold, R.drawable.panel_gold, R.drawable.row_gold,
            Color.WHITE, goldSoft, gold, emerald, false, false
        )
        else -> ThemeStyle(
            R.drawable.bg_jerusalem, R.drawable.card_gold, R.drawable.panel_gold, R.drawable.row_gold,
            Color.WHITE, goldSoft, gold, emerald, false, true
        )
    }
}
