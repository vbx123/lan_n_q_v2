package com.nasim.launcher.tv

import android.content.Context
import android.content.Intent

/** يجيب التطبيقات المثبتة (يفضّل LEANBACK_LAUNCHER ثم LAUNCHER). لازم يتنادى من Thread خلفي. */
class AppRepository(private val ctx: Context) {
    fun loadAll(): List<InstalledApp> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<InstalledApp>()
        for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val launch = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                out += InstalledApp(ri.loadLabel(pm).toString(), pkg, launch) { ri.loadIcon(pm) }
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }
}
