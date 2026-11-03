package com.nasim.launcher.tv

import android.content.Intent
import android.graphics.drawable.Drawable

/** الأيقونة بتتحمل عند أول استخدام فقط (عشان قائمة التطبيقات للهاتف متتأخرش). */
class InstalledApp(val label: String, val pkg: String, val intent: Intent, loader: () -> Drawable) {
    val icon: Drawable by lazy(loader)
}

class Tile(val label: String, val icon: Drawable?, val onClick: () -> Unit)
