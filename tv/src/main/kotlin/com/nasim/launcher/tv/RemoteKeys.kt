package com.nasim.launcher.tv

import android.app.Instrumentation
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

/**
 * تنفيذ أوامر ريموت الهاتف.
 *  - الاتجاهات/OK: بتشتغل جوه اللانشر (حقن داخل نفس التطبيق، مسموح بدون صلاحيات).
 *  - الصوت والتحكم في الوسائط: بتشتغل في أي تطبيق.
 *  - الرجوع/الرئيسية: في أي تطبيق لو فعّلت خدمة الإتاحة، وإلا جوه اللانشر فقط.
 */
object RemoteKeys {
    fun send(ctx: Context, key: String): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (key) {
            "volup" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true }
            "voldown" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true }
            "mute" -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); true }
            "play" -> media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> media(am, KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "home" -> {
                val a = TvAccessibilityService.instance
                if (a != null) a.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                else try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "back" -> {
                val a = TvAccessibilityService.instance
                if (a != null) a.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                else inject(KeyEvent.KEYCODE_BACK)
            }
            "up" -> inject(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> inject(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> inject(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> inject(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ok" -> inject(KeyEvent.KEYCODE_DPAD_CENTER)
            else -> false
        }
    }

    private fun media(am: AudioManager, code: Int): Boolean = try {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    } catch (e: Exception) {
        false
    }

    /** لازم يتنادى من Thread غير الـ UI. بيفشل (SecurityException) لو تطبيق تاني هو اللي قدّام. */
    private fun inject(code: Int): Boolean = try {
        Instrumentation().sendKeyDownUpSync(code)
        true
    } catch (e: Throwable) {
        false
    }
}
