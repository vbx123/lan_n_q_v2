package com.nasim.launcher.tv

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtil {
    fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: "—"
    } catch (e: Exception) {
        "—"
    }
}
