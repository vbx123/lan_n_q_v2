package com.nasim.launcher.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** خدمة اختيارية: بتسمح لريموت الهاتف بزر الرجوع والرئيسية داخل أي تطبيق (يوتيوب مثلاً). */
class TvAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var instance: TvAccessibilityService? = null
    }
}
