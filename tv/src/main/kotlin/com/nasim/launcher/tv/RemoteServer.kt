package com.nasim.launcher.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.nasim.launcher.common.Protocol
import com.nasim.launcher.common.ThemeConfig
import com.nasim.launcher.common.ThemeStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * سيرفر TCP صغير على الشاشة (منفذ 8765). كل طلب = سطر JSON، والرد سطر JSON.
 *   hello (بدون PIN)  | apply | apps | key | media | delete | upload (سطر JSON ثم بايتات الملف)
 */
class RemoteServer(private val ctx: Context, private val store: ThemeStore) {
    @Volatile private var running = false
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private val pool = Executors.newFixedThreadPool(4)

    @Synchronized
    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "remote-server").apply { isDaemon = true }.start()
    }

    private fun acceptLoop() {
        var ss: ServerSocket? = null
        try {
            ss = ServerSocket(Protocol.PORT)
            registerNsd(ss.localPort)
            while (!ss.isClosed) {
                val s = ss.accept()
                pool.execute { handle(s) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "server stopped: $e")
        } finally {
            try { ss?.close() } catch (_: Exception) {}
            unregisterNsd()
            running = false
        }
    }

    private fun pinOk(p: String) = MessageDigest.isEqual(p.toByteArray(), store.pin().toByteArray())

    private fun handle(s: Socket) {
        try {
            s.soTimeout = 15000
            s.use { sock ->
                val input = sock.getInputStream()
                val line = readLine(input) ?: return
                val req = JSONObject(line)
                val type = req.optString("type")
                val res: JSONObject = if (type == "hello") {
                    JSONObject().put("ok", true).put("name", Build.MODEL).put("config", store.load().toJson())
                } else if (!pinOk(req.optString("pin"))) {
                    Thread.sleep(700) // تبطيء محاولات تخمين الـ PIN
                    JSONObject().put("ok", false).put("error", "bad_pin")
                } else when (type) {
                    "apply" -> {
                        store.save(ThemeConfig.fromJson(req.getJSONObject("config")))
                        JSONObject().put("ok", true)
                    }
                    "apps" -> {
                        val arr = JSONArray()
                        for (a in AppRepository(ctx).loadAll()) arr.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
                        JSONObject().put("ok", true).put("apps", arr)
                    }
                    "key" -> {
                        val ok = RemoteKeys.send(ctx, req.optString("key"))
                        JSONObject().put("ok", ok).apply { if (!ok) put("error", "unsupported") }
                    }
                    "media" -> {
                        val kind = req.optString("kind")
                        val arr = JSONArray()
                        if (Protocol.maxBytes(kind) > 0) for (f in MediaFiles.list(ctx, kind)) {
                            arr.put(
                                JSONObject().put("name", f.name).put("video", MediaFiles.isVideo(f))
                                    .put("thumb", MediaFiles.thumbBase64(f) ?: "")
                            )
                        }
                        JSONObject().put("ok", true).put("items", arr)
                    }
                    "delete" -> {
                        val f = MediaFiles.safeFile(ctx, req.optString("kind"), req.optString("name"))
                        JSONObject().put("ok", f != null && f.delete())
                    }
                    "upload" -> receiveUpload(req, input)
                    else -> JSONObject().put("ok", false).put("error", "unknown")
                }
                sock.getOutputStream().apply {
                    write((res.toString() + "\n").toByteArray())
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "client error: $e")
        }
    }

    private fun receiveUpload(req: JSONObject, input: InputStream): JSONObject {
        val kind = req.optString("kind")
        val size = req.optLong("size", -1)
        val ext = req.optString("ext").lowercase().trimStart('.')
        val max = Protocol.maxBytes(kind)
        if (max == 0L || size <= 0 || size > max || ext !in Protocol.allowedExt(kind)) {
            return JSONObject().put("ok", false).put("error", "rejected")
        }
        val dir = MediaFiles.dir(ctx, kind)
        val prefix = if (kind == Protocol.KIND_WALLPAPER) "wp_" else "ss_"
        val name = "$prefix${System.currentTimeMillis()}.$ext"
        val tmp = File(dir, "$name.tmp")
        var left = size
        val buf = ByteArray(32 * 1024)
        tmp.outputStream().use { o ->
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                o.write(buf, 0, n)
                left -= n
            }
        }
        if (left > 0 || !tmp.renameTo(File(dir, name))) {
            tmp.delete()
            return JSONObject().put("ok", false).put("error", "incomplete")
        }
        return JSONObject().put("ok", true).put("name", name)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (buf.size() < Protocol.MAX_LINE) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            buf.write(b)
        }
        return if (buf.size() == 0) null else buf.toString("UTF-8")
    }

    private fun registerNsd(port: Int) {
        try {
            val info = NsdServiceInfo().apply {
                serviceName = "NasimTV"
                serviceType = Protocol.SERVICE_TYPE
                setPort(port)
            }
            val m = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(TAG, "nsd reg failed $e") }
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m
            regListener = l
        } catch (e: Exception) {
            Log.w(TAG, "nsd error: $e")
        }
    }

    private fun unregisterNsd() {
        try { regListener?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
        regListener = null
    }

    companion object { private const val TAG = "RemoteServer" }
}
