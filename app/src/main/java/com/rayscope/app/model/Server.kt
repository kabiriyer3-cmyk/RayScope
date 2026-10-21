package com.rayscope.app.model

import org.json.JSONObject
import java.util.UUID

data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val protocol: String = "vless",
    val address: String = "",
    val port: Int = 443,
    val uuid: String = "",
    val method: String = "",
    val flow: String = "",
    val network: String = "tcp",
    val headerType: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val insecure: Boolean = false,
    val obfsPassword: String = "",
    val extra: String = "",
    val rawLink: String = "",
    val subId: String = "",
    val delay: Long = DELAY_UNTESTED,
    val tcpPing: Long = DELAY_UNTESTED,
    val lastTested: Long = 0,
    val lastError: String = "",
    val favorite: Boolean = false,
    val successCount: Int = 0,
    val failCount: Int = 0,
) {
    val isWorking: Boolean get() = delay >= 0
    val isDead: Boolean get() = delay == DELAY_FAILED
    val isUntested: Boolean get() = delay == DELAY_UNTESTED

    fun key(): String = "$protocol|$address|$port|$uuid|$network|$path|$sni"

    /** Percentage of successful checks, -1 if never checked. */
    fun stability(): Int {
        val t = successCount + failCount
        return if (t == 0) -1 else successCount * 100 / t
    }

    fun flagEmoji(): String {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
                return String(cps, i, 2)
            }
        }
        return ""
    }

    fun cleanName(): String {
        val f = flagEmoji()
        val n = if (f.isNotEmpty()) name.replace(f, "") else name
        return n.trim().ifEmpty { "$address:$port" }
    }

    fun securityLabel(): String = when (security.lowercase()) {
        "reality" -> "REALITY"
        "tls" -> "TLS"
        else -> "без TLS"
    }

    fun protocolLabel(): String = when (protocol) {
        "ss" -> "Shadowsocks"
        "hysteria2" -> "Hysteria2"
        else -> protocol.uppercase()
    }

    /** Simple security audit of the config. */
    fun warnings(): List<String> {
        val w = mutableListOf<String>()
        val sec = security.lowercase()
        if (protocol != "ss" && sec != "tls" && sec != "reality") w += "Трафик идёт без TLS/REALITY: легко детектируется DPI"
        if (insecure) w += "allowInsecure: сертификат сервера не проверяется (риск MITM)"
        if (sec == "tls" && fingerprint.isEmpty() && protocol != "hysteria2") w += "Не задан uTLS fingerprint: TLS-рукопожатие отличается от браузера"
        if (protocol == "ss" && (method.contains("cfb") || method.contains("rc4") || method.contains("ctr"))) w += "Устаревший шифр Shadowsocks: $method"
        if (protocol == "vmess" && method == "none") w += "VMess без шифрования"
        if (sec == "reality" && publicKey.isEmpty()) w += "REALITY без publicKey: подключение не сработает"
        return w
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("protocol", protocol)
        o.put("address", address)
        o.put("port", port)
        o.put("uuid", uuid)
        o.put("method", method)
        o.put("flow", flow)
        o.put("network", network)
        o.put("headerType", headerType)
        o.put("host", host)
        o.put("path", path)
        o.put("serviceName", serviceName)
        o.put("mode", mode)
        o.put("security", security)
        o.put("sni", sni)
        o.put("fingerprint", fingerprint)
        o.put("alpn", alpn)
        o.put("publicKey", publicKey)
        o.put("shortId", shortId)
        o.put("spiderX", spiderX)
        o.put("insecure", insecure)
        o.put("obfsPassword", obfsPassword)
        o.put("extra", extra)
        o.put("rawLink", rawLink)
        o.put("subId", subId)
        o.put("delay", delay)
        o.put("tcpPing", tcpPing)
        o.put("lastTested", lastTested)
        o.put("lastError", lastError)
        o.put("favorite", favorite)
        o.put("successCount", successCount)
        o.put("failCount", failCount)
        return o
    }

    companion object {
        const val DELAY_UNTESTED = -2L
        const val DELAY_FAILED = -1L

        fun fromJson(o: JSONObject): Server = Server(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            protocol = o.optString("protocol", "vless"),
            address = o.optString("address"),
            port = o.optInt("port", 443),
            uuid = o.optString("uuid"),
            method = o.optString("method"),
            flow = o.optString("flow"),
            network = o.optString("network", "tcp"),
            headerType = o.optString("headerType"),
            host = o.optString("host"),
            path = o.optString("path"),
            serviceName = o.optString("serviceName"),
            mode = o.optString("mode"),
            security = o.optString("security", "none"),
            sni = o.optString("sni"),
            fingerprint = o.optString("fingerprint"),
            alpn = o.optString("alpn"),
            publicKey = o.optString("publicKey"),
            shortId = o.optString("shortId"),
            spiderX = o.optString("spiderX"),
            insecure = o.optBoolean("insecure", false),
            obfsPassword = o.optString("obfsPassword"),
            extra = o.optString("extra"),
            rawLink = o.optString("rawLink"),
            subId = o.optString("subId"),
            delay = o.optLong("delay", DELAY_UNTESTED),
            tcpPing = o.optLong("tcpPing", DELAY_UNTESTED),
            lastTested = o.optLong("lastTested", 0L),
            lastError = o.optString("lastError"),
            favorite = o.optBoolean("favorite", false),
            successCount = o.optInt("successCount", 0),
            failCount = o.optInt("failCount", 0),
        )
    }
}
