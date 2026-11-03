package com.nasim.launcher.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.util.Locale

/**
 * خدمة التشغيل: القرآن/الإذاعات.
 * شغّالة كـ Foreground Service عشان الصوت يكمّل حتى لو فتحت أي تطبيق.
 */
class PlaybackService : Service() {

    enum class State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    companion object {
        private const val TAG = "PlaybackService"
        const val ACTION_PLAY = "com.nasim.launcher.tv.PLAY"
        const val ACTION_TOGGLE = "com.nasim.launcher.tv.TOGGLE"
        const val ACTION_STOP = "com.nasim.launcher.tv.STOP"
        const val ACTION_NEXT = "com.nasim.launcher.tv.NEXT"
        const val ACTION_PREV = "com.nasim.launcher.tv.PREV"
        private const val CHANNEL = "playback"
        private const val NOTIF_ID = 77

        @Volatile var instance: PlaybackService? = null
        /** بيتنادى على الـ Main thread كل ما حالة التشغيل تتغير (الواجهة بتسجّل نفسها هنا). */
        @Volatile var listener: (() -> Unit)? = null

        fun start(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
            val i = Intent(ctx, PlaybackService::class.java).setAction(action)
            i.extras()
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
                Log.w(TAG, "cannot start service: $e")
            }
        }

        fun playRadio(ctx: Context, name: String, url: String) = start(ctx, ACTION_PLAY) {
            putExtra("title", name); putExtra("sub", "بث مباشر")
            putExtra("radio", true); putExtra("url", url)
        }

        fun playSurah(ctx: Context, reciter: String, server: String, surah: Int) = start(ctx, ACTION_PLAY) {
            putExtra("title", "سورة ${QuranData.surahs[surah - 1]}"); putExtra("sub", reciter)
            putExtra("radio", false); putExtra("server", server); putExtra("surah", surah)
        }
    }

    var state: State = State.IDLE
        private set
    var title: String = ""
        private set
    var sub: String = ""
        private set
    var isRadio: Boolean = false
        private set
    var canStep: Boolean = false
        private set
    var surah: Int = 1
        private set
    var server: String? = null
        private set
    var lastUrl: String = ""
        private set

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusReq: AudioFocusRequest? = null
    private var resumeOnGain = false

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { pauseQuran(false) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeOnGain = state == State.PLAYING; pauseQuran(false) }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(1f, 1f)
                if (resumeOnGain) { resumeOnGain = false; resumeQuran() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        releaseQuran()
        instance = null
        notifyChanged()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_PLAY -> intent?.let { playFromIntent(it) }
            ACTION_TOGGLE -> toggle()
            ACTION_STOP -> stopAll()
            ACTION_NEXT -> step(1)
            ACTION_PREV -> step(-1)
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- foreground
    private fun ensureForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "التشغيل", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (title.isBlank()) "moo_lan_tv" else title)
            .setContentText(sub)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: $e")
        }
    }

    private fun notifyChanged() {
        listener?.let { l -> main.post { try { l() } catch (_: Exception) {} } }
    }

    private fun stopIfIdle() {
        if (player == null) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
        }
    }

    // ---------------------------------------------------------------- quran / radio
    private fun playFromIntent(i: Intent) {
        title = i.getStringExtra("title").orEmpty()
        sub = i.getStringExtra("sub").orEmpty()
        isRadio = i.getBooleanExtra("radio", false)
        server = i.getStringExtra("server")
        surah = i.getIntExtra("surah", 1).coerceIn(1, 114)
        canStep = !isRadio && server != null
        val url = if (isRadio) i.getStringExtra("url").orEmpty()
        else server + String.format(Locale.US, "%03d.mp3", surah)
        playUrl(url)
        ensureForeground()
    }

    private fun playUrl(url: String) {
        releaseQuran()
        lastUrl = url
        if (url.isBlank()) { state = State.ERROR; notifyChanged(); return }
        requestQuranFocus()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnPreparedListener { p ->
                if (p === player) {
                    p.start()
                    state = State.PLAYING
                    notifyChanged()
                }
            }
            mp.setOnCompletionListener { p -> if (p === player) onQuranComplete() }
            mp.setOnErrorListener { p, _, _ ->
                if (p === player) { state = State.ERROR; notifyChanged() }
                true
            }
            player = mp
            state = State.LOADING
            notifyChanged()
            mp.setDataSource(url)
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "playUrl failed: $e")
            try { mp.release() } catch (_: Exception) {}
            player = null
            state = State.ERROR
            notifyChanged()
        }
    }

    private fun onQuranComplete() {
        if (isRadio) { state = State.IDLE; notifyChanged(); return }
        if (surah < 114) step(1) else stopAll()
    }

    private fun step(delta: Int) {
        val s = server ?: return
        if (!canStep) return
        val n = (surah + delta).coerceIn(1, 114)
        if (n == surah) return
        surah = n
        title = "سورة ${QuranData.surahs[n - 1]}"
        playUrl(s + String.format(Locale.US, "%03d.mp3", n))
        ensureForeground()
    }

    private fun toggle() {
        when (state) {
            State.PLAYING -> pauseQuran(true)
            State.PAUSED -> resumeQuran()
            State.ERROR, State.IDLE -> if (lastUrl.isNotBlank()) playUrl(lastUrl)
            else -> {}
        }
    }

    private fun pauseQuran(userAction: Boolean) {
        try {
            player?.let { if (it.isPlaying) it.pause() }
            if (player != null) state = State.PAUSED
        } catch (_: Exception) {}
        if (userAction) resumeOnGain = false
        notifyChanged()
    }

    private fun resumeQuran() {
        try {
            player?.let { it.start(); state = State.PLAYING }
        } catch (_: Exception) {}
        notifyChanged()
    }

    private fun requestQuranFocus() {
        val am = getSystemService(AudioManager::class.java)
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusReq = r
        am.requestAudioFocus(r)
    }

    private fun releaseQuran() {
        player?.let { p ->
            try { p.setOnCompletionListener(null); p.setOnErrorListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        player = null
        focusReq?.let { try { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } catch (_: Exception) {} }
        focusReq = null
        state = State.IDLE
    }

    private fun stopAll() {
        releaseQuran()
        state = State.IDLE
        title = ""; sub = ""; canStep = false; server = null
        notifyChanged()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }
}
