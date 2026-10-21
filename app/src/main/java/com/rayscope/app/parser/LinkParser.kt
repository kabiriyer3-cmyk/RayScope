package com.rayscope.app.parser

import android.util.Base64
import com.rayscope.app.model.Server
import org.json.JSONObject
import java.net.URLDecoder

/** Parses share links: vless://, vmess://, trojan://, ss://, hysteria2:// (hy2://). */
object LinkParser {
    private val SCHEMES = listOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")

    fun isLink(s: String): Boolean {
        val t = s.trim()
        return SCHEMES.any { t.startsWith("$it://", ignoreCase = true) }
    }

    fun parseMany(text: String): List<Server> {
        val t = text.trim()
        var lines = t.lines().map { it.trim() }.filter { isLink(it) }
        if (lines.isEmpty()) {
            val decoded = b64(t)
            if (decoded != null) lines = decoded.lines().map { it.trim() }.filter { isLink(it) }
        }
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
        val clean = s.trim().replace(Regex("\\s"), "").replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        val out = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
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
