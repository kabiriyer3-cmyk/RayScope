package com.rayscope.desktop

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** Country code from the flag emoji in the name, or guessed from the name itself ("Germany", "NL-2", "Москва"). */
    fun countryCode(): String = flagCode(flagEmoji()).ifEmpty { Countries.guess(name) }

    /** Name without emoji: Windows can't draw flag emoji, we draw our own flags instead. */
    fun cleanName(): String {
        val sb = StringBuilder()
        name.codePoints().forEach { cp -> if (!isEmojiCodePoint(cp)) sb.appendCodePoint(cp) }
        val n = sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
            .trim().trim('|', '-', '·', ',', '_', ':').trim()
        return n.ifEmpty { "$address:$port" }
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

/** Emoji, pictographs, regional indicators, variation selectors and joiners. */
fun isEmojiCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF ||
        cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 || cp in 0xE0020..0xE007F

fun flagCode(flag: String): String {
    val cps = flag.codePoints().toArray()
    if (cps.size < 2) return ""
    val a = cps[0] - 0x1F1E6
    val b = cps[1] - 0x1F1E6
    if (a !in 0..25 || b !in 0..25) return ""
    return "" + ('A' + a) + ('A' + b)
}

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** unix seconds */
    val expire: Long = 0,
    val lastUpdated: Long = 0,
    val updateIntervalH: Int = 0,
    val supportUrl: String = "",
    val announce: String = "",
    val serverCount: Int = 0,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("url", url)
        o.put("upload", upload)
        o.put("download", download)
        o.put("total", total)
        o.put("expire", expire)
        o.put("lastUpdated", lastUpdated)
        o.put("updateIntervalH", updateIntervalH)
        o.put("supportUrl", supportUrl)
        o.put("announce", announce)
        o.put("serverCount", serverCount)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Subscription = Subscription(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            url = o.optString("url"),
            upload = o.optLong("upload", 0L),
            download = o.optLong("download", 0L),
            total = o.optLong("total", 0L),
            expire = o.optLong("expire", 0L),
            lastUpdated = o.optLong("lastUpdated", 0L),
            updateIntervalH = o.optInt("updateIntervalH", 0),
            supportUrl = o.optString("supportUrl"),
            announce = o.optString("announce"),
            serverCount = o.optInt("serverCount", 0),
        )
    }
}

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b Б"
        val units = listOf("КБ", "МБ", "ГБ", "ТБ")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    fun speed(bps: Long): String = bytes(bps) + "/с"

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    fun ms(d: Long): String = when {
        d == -2L -> "—"
        d < 0 -> "нет ответа"
        else -> "$d ms"
    }

    fun ago(t: Long): String {
        if (t <= 0) return "никогда"
        val d = (System.currentTimeMillis() - t) / 1000
        return when {
            d < 60 -> "только что"
            d < 3600 -> "${d / 60} мин назад"
            d < 86400 -> "${d / 3600} ч назад"
            else -> "${d / 86400} дн назад"
        }
    }

    fun date(t: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(t))
}
