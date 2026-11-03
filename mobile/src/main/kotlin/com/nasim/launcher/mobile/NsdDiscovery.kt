package com.nasim.launcher.mobile

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.nasim.launcher.common.Protocol
import java.net.Inet4Address
import java.util.LinkedList

/** اكتشاف الشاشة تلقائياً على الـ Wi-Fi (mDNS/NSD) بدل كتابة الـ IP يدوي. */
class NsdDiscovery(ctx: Context, private val onFound: (String) -> Unit) {
    private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val queue = LinkedList<NsdServiceInfo>()
    private var listener: NsdManager.DiscoveryListener? = null
    private var busy = false

    fun start() {
        stop()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (i.serviceType.contains("nasimtv")) main.post { queue.add(i); next() }
            }
        }
        listener = l
        try { nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) {}
    }

    // resolveService لا يقبل أكتر من طلب في نفس الوقت، فبنعمل طابور
    private fun next() {
        if (busy) return
        val info = queue.poll() ?: return
        busy = true
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) {
                    main.post { busy = false; next() }
                }

                override fun onServiceResolved(s: NsdServiceInfo) {
                    val h = s.host
                    main.post {
                        if (h is Inet4Address) onFound(h.hostAddress ?: "")
                        busy = false
                        next()
                    }
                }
            })
        } catch (_: Exception) {
            busy = false
        }
    }

    fun stop() {
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listener = null
        queue.clear()
        busy = false
    }
}
