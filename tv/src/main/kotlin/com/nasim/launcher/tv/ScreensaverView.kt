package com.nasim.launcher.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import com.nasim.launcher.common.Protocol
import com.nasim.launcher.common.Wallpapers
import java.io.File
import java.util.concurrent.Executors

/**
 * شاشة التوقف: عرض صور (انتقال تدريجي) أو فيديو قصير بتكرار مستمر.
 * لو مفيش ملفات مرفوعة بتعرض الخلفيات الجاهزة.
 */
class ScreensaverView(ctx: Context) : FrameLayout(ctx) {
    private val a = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val b = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val video = VideoView(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val advance = Runnable { showNext() }

    private var items: List<Any> = emptyList() // File أو Int (مورد)
    private var idx = -1
    private var token = 0
    private var fails = 0

    var isShowing: Boolean = false
        private set

    init {
        setBackgroundColor(Color.BLACK)
        video.setZOrderOnTop(true) // عشان الفيديو يظهر فوق خلفية اللانشر
        video.visibility = View.GONE
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        addView(a, LayoutParams(mp, mp))
        addView(b, LayoutParams(mp, mp))
        addView(video, LayoutParams(mp, mp))
        visibility = View.GONE
    }

    fun start(files: List<File>) {
        items = if (files.isEmpty()) Wallpapers.builtin.toList() else files
        idx = -1
        fails = 0
        isShowing = true
        visibility = View.VISIBLE
        showNext()
    }

    fun stop() {
        isShowing = false
        token++
        main.removeCallbacksAndMessages(null)
        try { video.stopPlayback() } catch (_: Exception) {}
        video.visibility = View.GONE
        b.animate().cancel()
        a.setImageDrawable(null)
        b.setImageDrawable(null)
        b.alpha = 0f
        visibility = View.GONE
    }

    fun release() {
        stop()
        io.shutdownNow()
    }

    private fun showNext() {
        if (!isShowing || items.isEmpty()) return
        if (fails > items.size) { stop(); return } // كل الملفات فاشلة
        idx = (idx + 1) % items.size
        val item = items[idx]
        val my = ++token
        main.removeCallbacks(advance)
        if (item is File && MediaFiles.isVideo(item)) playVideo(item, my) else showImage(item, my)
    }

    private fun showImage(item: Any, my: Int) {
        try { video.stopPlayback() } catch (_: Exception) {}
        video.visibility = View.GONE
        io.execute {
            val bmp = if (item is File) Bmp.decodeFile(item.path) else Bmp.decodeRes(context, item as Int)
            main.post {
                if (!isShowing || my != token) return@post
                if (bmp == null) { fails++; showNext(); return@post }
                fails = 0
                crossfade(bmp)
                main.postDelayed(advance, 10_000L)
            }
        }
    }

    private fun crossfade(bm: Bitmap) {
        b.animate().cancel()
        if (b.drawable != null) a.setImageDrawable(b.drawable)
        if (a.drawable == null) {
            a.setImageBitmap(bm)
            return
        }
        b.setImageBitmap(bm)
        b.alpha = 0f
        b.animate().alpha(1f).setDuration(900).withEndAction {
            a.setImageDrawable(b.drawable)
            b.alpha = 0f
        }.start()
    }

    private fun playVideo(f: File, my: Int) {
        video.visibility = View.VISIBLE
        val single = items.size == 1
        video.setOnPreparedListener { mp ->
            if (my != token) return@setOnPreparedListener
            mp.setVolume(0f, 0f)
            mp.isLooping = single
            fails = 0
        }
        video.setOnCompletionListener { if (my == token) showNext() }
        video.setOnErrorListener { _, _, _ ->
            if (my == token) { fails++; showNext() }
            true
        }
        try {
            video.setVideoPath(f.path)
            video.start()
        } catch (e: Exception) {
            fails++
            showNext()
        }
    }
}

class ScreensaverDream : DreamService() {
    private var view: ScreensaverView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val v = ScreensaverView(this)
        view = v
        setContentView(v)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start(MediaFiles.list(this, Protocol.KIND_SCREENSAVER))
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.release()
        super.onDetachedFromWindow()
    }
}
