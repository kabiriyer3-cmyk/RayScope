package com.rayscope.desktop

import com.rayscope.sub.ProxyEntry
import com.rayscope.sub.SubFormats
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * Parses share links (vless://, vmess://, trojan://, ss://, hysteria2://) and everything subscriptions wrap them
 * in: base64, Xray JSON configs, sing-box profiles, web pages with links inside.
 */
object LinkParser {
    private val SCHEMES = listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")

    fun isLink(s: String): Boolean {
        val t = s.trim()
        return SCHEMES.any { t.startsWith("$it://", ignoreCase = true) }
    }

    fun parseMany(text: String): List<Server> {
        val t = text.trim().removePrefix("\uFEFF").trim()
        var lines = t.lines().map { it.trim() }.filter { isLink(it) }
        if (lines.isEmpty()) {
            // Remnawave, Marzban and friends give Happ / v2rayNG full Xray configs instead of links
            SubFormats.parseJson(t)?.let { return it.map { e -> e.toServer() } }
            val decoded = b64(t)
            if (decoded != null) {
                lines = decoded.lines().map { it.trim() }.filter { isLink(it) }
                if (lines.isEmpty()) SubFormats.parseJson(decoded)?.let { return it.map { e -> e.toServer() } }
            }
        }
        // a web page or a message with links somewhere inside
        if (lines.isEmpty()) lines = SubFormats.linksIn(t)
        return lines.mapNotNull { runCatching { parse(it) }.getOrNull() }
    }

    fun parse(link: String): Server? = when (link.substringBefore("://").lowercase()) {
        "vless" -> parseStd(link, "vless")
        "trojan" -> parseStd(link, "trojan")
        "vmess" -> parseVmess(link)
        "ss" -> parseSs(link)
        "hysteria2", "hy2" -> parseHy2(link)
        else -> null
    }

    private class Parts(val user: String, val host: String, val port: Int, val q: Map<String, String>, val name: String)

    private fun split(link: String): Parts? {
        var rest = link.trim().substringAfter("://")
        val name = if (rest.contains('#')) dec(rest.substringAfter('#')) else ""
        rest = rest.substringBefore('#')
        val query = if (rest.contains('?')) rest.substringAfter('?') else ""
        rest = rest.substringBefore('?').trimEnd('/')
        val at = rest.lastIndexOf('@')
        val user = if (at >= 0) dec(rest.substring(0, at)) else ""
        val hp = if (at >= 0) rest.substring(at + 1) else rest
        val hostPort = hostPort(hp) ?: return null
        return Parts(user, hostPort.first, hostPort.second, query(query), name.trim())
    }

    fun hostPort(hp: String): Pair<String, Int>? {
        if (hp.startsWith("[")) {
            val end = hp.indexOf(']')
            if (end < 0) return null
            val host = hp.substring(1, end)
            val port = hp.substring(end + 1).removePrefix(":").takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            return host to port
        }
        val idx = hp.lastIndexOf(':')
        if (idx < 0) return null
        val port = hp.substring(idx + 1).takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        return hp.substring(0, idx) to port
    }

    private fun query(q: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        if (q.isEmpty()) return m
        for (kv in q.split('&')) {
            if (kv.isEmpty()) continue
            val k = dec(kv.substringBefore('='))
            val v = if (kv.contains('=')) dec(kv.substringAfter('=')) else ""
            m[k] = v
        }
        return m
    }

    fun dec(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    fun b64(s: String): String? = try {
        val clean = s.trim().filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        val out = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        if (out.isBlank()) null else out
    } catch (e: Exception) {
        null
    }

    private fun normNet(n: String?): String {
        val v = (n ?: "tcp").trim().lowercase()
        return when (v) {
            "", "raw" -> "tcp"
            "splithttp" -> "xhttp"
            "http" -> "h2"
            else -> v
        }
    }

    private fun parseStd(link: String, proto: String): Server? {
        val p = split(link) ?: return null
        val q = p.q
        val net = normNet(q["type"])
        val defSec = if (proto == "trojan") "tls" else "none"
        return Server(
            name = p.name.ifEmpty { "${p.host}:${p.port}" },
            protocol = proto,
            address = p.host,
            port = p.port,
            uuid = p.user,
            method = if (proto == "vless") (q["encryption"] ?: "none").ifEmpty { "none" } else "",
            flow = q["flow"].orEmpty(),
            network = net,
            headerType = q["headerType"].orEmpty(),
            host = q["host"].orEmpty(),
            path = q["path"].orEmpty(),
            serviceName = q["serviceName"] ?: (if (net == "grpc") q["path"].orEmpty() else ""),
            mode = q["mode"].orEmpty(),
            security = (q["security"] ?: defSec).ifEmpty { defSec },
            sni = q["sni"] ?: q["peer"].orEmpty(),
            fingerprint = q["fp"].orEmpty(),
            alpn = q["alpn"].orEmpty(),
            publicKey = q["pbk"].orEmpty(),
            shortId = q["sid"].orEmpty(),
            spiderX = q["spx"].orEmpty(),
            insecure = q["allowInsecure"] == "1" || q["allowInsecure"] == "true" || q["insecure"] == "1",
            extra = q["extra"].orEmpty(),
            rawLink = link.trim(),
        )
    }

    private fun parseVmess(link: String): Server? {
        val body = link.trim().substringAfter("://").substringBefore('#')
        val json = b64(body) ?: return null
        val o = JSONObject(json)
        val port = o.optString("port").trim().toIntOrNull() ?: return null
        val net = normNet(o.optString("net"))
        val tls = o.optString("tls")
        val address = o.optString("add")
        return Server(
            name = o.optString("ps").ifEmpty { "$address:$port" },
            protocol = "vmess",
            address = address,
            port = port,
            uuid = o.optString("id"),
            method = o.optString("scy").ifEmpty { "auto" },
            network = net,
            headerType = o.optString("type").let { if (it == "none") "" else it },
            host = o.optString("host"),
            path = o.optString("path"),
            serviceName = if (net == "grpc") o.optString("path") else "",
            mode = o.optString("mode"),
            security = if (tls.isEmpty() || tls == "none") "none" else tls,
            sni = o.optString("sni"),
            fingerprint = o.optString("fp"),
            alpn = o.optString("alpn"),
            insecure = o.optString("allowInsecure") == "1" || o.optString("allowInsecure") == "true",
            rawLink = link.trim(),
        )
    }

    private fun parseSs(link: String): Server? {
        var body = link.trim().substringAfter("://")
        val name = if (body.contains('#')) dec(body.substringAfter('#')).trim() else ""
        body = body.substringBefore('#').substringBefore('?').trimEnd('/')
        if (!body.contains('@')) body = b64(body) ?: return null
        val at = body.lastIndexOf('@')
        if (at < 0) return null
        val userDec = dec(body.substring(0, at))
        val user = if (userDec.contains(':')) userDec else (b64(userDec) ?: return null)
        val hp = hostPort(body.substring(at + 1)) ?: return null
        return Server(
            name = name.ifEmpty { "${hp.first}:${hp.second}" },
            protocol = "ss",
            address = hp.first,
            port = hp.second,
            uuid = user.substringAfter(':'),
            method = user.substringBefore(':'),
            security = "none",
            rawLink = link.trim(),
        )
    }

    private fun parseHy2(link: String): Server? {
        val p = split(link) ?: return null
        val q = p.q
        return Server(
            name = p.name.ifEmpty { "${p.host}:${p.port}" },
            protocol = "hysteria2",
            address = p.host,
            port = p.port,
            uuid = p.user,
            network = "hysteria",
            security = "tls",
            sni = q["sni"].orEmpty(),
            insecure = q["insecure"] == "1" || q["allowInsecure"] == "1",
            obfsPassword = if (q["obfs"] == "salamander") q["obfs-password"].orEmpty() else "",
            alpn = q["alpn"] ?: "h3",
            rawLink = link.trim(),
        )
    }
}

/** A server from a JSON subscription: share-link fields for the list, the provider's own outbound for the core. */
fun ProxyEntry.toServer(): Server = Server(
    name = name,
    protocol = protocol,
    address = address,
    port = port,
    uuid = uuid,
    method = method,
    flow = flow,
    network = network,
    headerType = headerType,
    host = host,
    path = path,
    serviceName = serviceName,
    mode = mode,
    security = security,
    sni = sni,
    fingerprint = fingerprint,
    alpn = alpn,
    publicKey = publicKey,
    shortId = shortId,
    spiderX = spiderX,
    insecure = insecure,
    obfsPassword = obfsPassword,
    extra = extra,
    rawLink = link(),
    outbound = outbound,
)

/** Downloads a subscription and reads Happ-style headers (subscription-userinfo, profile-title, announce...). */
object SubFetcher {
    class Result(val sub: Subscription, val servers: List<Server>)

    /** The panel answered, but with nothing we can read (a web page, Clash YAML, a refusal): another client name may help. */
    private class Unreadable(msg: String) : IOException(msg)

    /**
     * Asks with the User-Agent from the settings first. Panels pick the format by it, so when the answer is
     * unreadable the request goes again as Happ, v2rayNG and v2rayN. Network errors are thrown at once.
     */
    fun fetch(sub: Subscription, userAgent: String, proxyPort: Int?): Result {
        var last: IOException? = null
        for (ua in (listOf(userAgent) + SubFormats.FALLBACK_UAS).filter { it.isNotBlank() }.distinct()) {
            try {
                return fetchOnce(sub, ua, proxyPort)
            } catch (e: Unreadable) {
                last = e
            }
        }
        throw last ?: IOException("В подписке нет поддерживаемых серверов")
    }

    /** Follows redirects by hand: HttpURLConnection refuses to switch between http and https on its own. */
    private fun open(url: String, ua: String, proxyPort: Int?): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = Net.conn(u, proxyPort, 20000)
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Accept", "*/*")
            c.setRequestProperty("Accept-Encoding", "gzip")
            val code = c.responseCode
            if (code !in 300..399) return c
            val loc = c.getHeaderField("Location")
            c.disconnect()
            if (loc.isNullOrBlank()) throw IOException("HTTP $code без адреса перенаправления")
            u = URI(u).resolve(loc.trim()).toString()
        }
        throw IOException("Слишком много перенаправлений")
    }

    private fun fetchOnce(sub: Subscription, ua: String, proxyPort: Int?): Result {
        val c = open(sub.url, ua, proxyPort)
        try {
            val code = c.responseCode
            if (code in 400..499) throw Unreadable("HTTP $code")
            if (code !in 200..299) throw IOException("HTTP $code")
            val raw: InputStream = if (c.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(c.inputStream) else c.inputStream
            val body = raw.use { it.readBytes().toString(Charsets.UTF_8) }
            var up = 0L
            var down = 0L
            var total = 0L
            var expire = 0L
            c.getHeaderField("subscription-userinfo")?.split(';')?.forEach { part ->
                val k = part.substringBefore('=').trim().lowercase()
                val v = part.substringAfter('=', "").trim().toDoubleOrNull()?.toLong() ?: 0L
                when (k) {
                    "upload" -> up = v
                    "download" -> down = v
                    "total" -> total = v
                    "expire" -> expire = v
                    else -> Unit
                }
            }
            val title = header(c, "profile-title")?.let { decodeHeader(it) }?.trim()
            val interval = c.getHeaderField("profile-update-interval")?.trim()?.toIntOrNull() ?: sub.updateIntervalH
            val support = c.getHeaderField("support-url") ?: sub.supportUrl
            val announce = header(c, "announce")?.let { decodeHeader(it) } ?: sub.announce
            val servers = LinkParser.parseMany(body).map { it.copy(subId = sub.id) }
            if (servers.isEmpty()) throw Unreadable("В подписке нет поддерживаемых серверов")
            val host = try {
                URI(sub.url).host
            } catch (e: Exception) {
                null
            }
            val name = when {
                !title.isNullOrBlank() -> title
                sub.name.isNotBlank() -> sub.name
                else -> host ?: "Подписка"
            }
            val updated = sub.copy(
                name = name,
                upload = up,
                download = down,
                total = total,
                expire = expire,
                lastUpdated = System.currentTimeMillis(),
                updateIntervalH = interval,
                supportUrl = support,
                announce = announce,
                serverCount = servers.size,
            )
            return Result(updated, servers)
        } finally {
            c.disconnect()
        }
    }

    private fun decodeHeader(v: String): String =
        if (v.startsWith("base64:")) LinkParser.b64(v.removePrefix("base64:")) ?: v else v

    /** HttpURLConnection reads header bytes as Latin-1: a title sent as plain UTF-8 is turned back into text. */
    private fun header(c: HttpURLConnection, name: String): String? {
        val v = c.getHeaderField(name) ?: return null
        val mangled = v.any { it.code in 0x80..0xFF } && v.all { it.code <= 0xFF }
        return if (mangled) String(v.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) else v
    }
}
