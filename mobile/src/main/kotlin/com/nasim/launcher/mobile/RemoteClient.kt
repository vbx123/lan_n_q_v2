package com.nasim.launcher.mobile

import com.nasim.launcher.common.Protocol
import com.nasim.launcher.common.ThemeConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/** عميل TCP: سطر JSON (وأحياناً بايتات ملف) ثم قراءة سطر الرد. لازم يتنادى من Thread خلفي. */
object RemoteClient {
    private fun readReply(s: Socket): JSONObject {
        val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
            ?: throw IOException("no reply")
        return JSONObject(line)
    }

    fun request(host: String, payload: JSONObject): JSONObject {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, Protocol.PORT), 3000)
            s.soTimeout = 20000
            val out = s.getOutputStream()
            out.write((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            return readReply(s)
        }
    }

    private fun authed(pin: String, type: String) = JSONObject().put("type", type).put("pin", pin)

    fun hello(host: String) = request(host, JSONObject().put("type", "hello"))
    fun apply(host: String, pin: String, cfg: ThemeConfig) = request(host, authed(pin, "apply").put("config", cfg.toJson()))
    fun apps(host: String, pin: String) = request(host, authed(pin, "apps"))
    fun key(host: String, pin: String, key: String) = request(host, authed(pin, "key").put("key", key))
    fun media(host: String, pin: String, kind: String) = request(host, authed(pin, "media").put("kind", kind))
    fun delete(host: String, pin: String, kind: String, name: String) =
        request(host, authed(pin, "delete").put("kind", kind).put("name", name))

    fun upload(host: String, pin: String, kind: String, ext: String, size: Long, data: InputStream): JSONObject {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, Protocol.PORT), 3000)
            s.soTimeout = 30000
            val out = s.getOutputStream()
            val head = authed(pin, "upload").put("kind", kind).put("ext", ext).put("size", size)
            out.write((head.toString() + "\n").toByteArray(Charsets.UTF_8))
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = data.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.flush()
            return readReply(s)
        }
    }
}
