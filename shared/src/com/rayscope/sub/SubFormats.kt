/*
 * Subscription formats beyond plain share links. Panels like Remnawave and Marzban answer Happ, v2rayNG and
 * v2RayTun with full Xray JSON configs instead of vless:// lines, Hiddify gets sing-box JSON, links arrive wrapped
 * in app deep links (happ://add/https://..., v2raytun://import/...) or inside an HTML page. All of that is read
 * into [ProxyEntry] here, and each app turns it into its own Server.
 * Shared by Android and Windows.
 */
package com.rayscope.sub

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

/** One proxy from a JSON subscription, in share-link terms, plus the provider's own Xray outbound. */
data class ProxyEntry(
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    /** id or password */
    val uuid: String = "",
    /** VLESS encryption, VMess security or the Shadowsocks cipher */
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
    /** the Xray outbound exactly as the provider wrote it, without its tag and dialer chain; empty for sing-box */
    val outbound: String = "",
) {
    fun key(): String = "$protocol|$address|$port|$uuid|$network|$path|$sni"

    /** The same server as a share link, for copying and export. */
    fun link(): String = SubFormats.link(this)
}

object SubFormats {
    /** Asked in turn when a panel answers our User-Agent with something we can't read (a web page, YAML, 4xx). */
    val FALLBACK_UAS = listOf("Happ/2.0.0", "v2rayNG/1.10.20", "v2rayN/7.10.0")

    private val SHARE = setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2")
    private val XRAY_PROXIES = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2")
    private val BOX_PROXIES = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria2")
    private val LINK = Regex("(?:vless|vmess|trojan|ss|hysteria2|hy2)://[^\\s\"'<>`\\\\]+", RegexOption.IGNORE_CASE)
    private val TECH_TAG = Regex("^(proxy|outbound|out)([-_ .].*|\\d*)$", RegexOption.IGNORE_CASE)
    private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun isHttp(s: String): Boolean = s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    // ------------------------------------------------------------------ JSON subscriptions

    /**
     * Servers from a JSON subscription: an array of Xray configs (one server per config, the name in "remarks"),
     * a single Xray config, a sing-box profile or a bare outbound. Null when [text] is not JSON at all.
     */
    fun parseJson(text: String): List<ProxyEntry>? {
        val t = text.trim().removePrefix("\uFEFF").trim()
        if (!t.startsWith("{") && !t.startsWith("[")) return null
        val root = try {
            JSONTokener(t).nextValue()
        } catch (e: Exception) {
            return null
        }
        val items = when (root) {
            is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
            is JSONObject -> listOf(root)
            else -> return null
        }
        val singles = ArrayList<ProxyEntry>()
        val pooled = ArrayList<ProxyEntry>()
        for (c in items) {
            val outs = c.optJSONArray("outbounds")
            if (outs == null) {
                entry(c, str(c, "remarks"))?.let { singles += it }
                continue
            }
            val title = str(c, "remarks").ifEmpty { str(c, "name") }.trim()
            val found = (0 until outs.length()).mapNotNull { outs.optJSONObject(it) }.mapNotNull { entry(it, "") }
            when {
                found.isEmpty() -> Unit
                found.size == 1 && title.isNotEmpty() -> singles += found[0].copy(name = title)
                found.size == 1 -> singles += found[0]
                // a balancer ("Auto | best server") or a sing-box profile: every outbound is a server of its own
                else -> pooled += found
            }
        }
        // the servers of a balancer usually come again one by one with proper names: keep those
        val seen = HashSet<String>()
        val out = ArrayList<ProxyEntry>()
        for (e in singles + pooled) if (seen.add(e.key())) out += e
        return out
    }

    private fun entry(o: JSONObject, name: String): ProxyEntry? = when {
        o.has("protocol") -> xray(o, name)
        o.has("type") && o.has("server") -> singBox(o, name)
        else -> null
    }

    /** A tag people wrote ("🇩🇪 Germany"), not a technical one ("proxy", "proxy-2", "out-1"). */
    private fun tagName(o: JSONObject): String {
        val tag = str(o, "tag").trim()
        return if (tag.isEmpty() || TECH_TAG.matches(tag)) "" else tag
    }

    private fun xray(o: JSONObject, name: String): ProxyEntry? {
        val proto = str(o, "protocol").lowercase()
        if (proto !in XRAY_PROXIES) return null
        val set = o.optJSONObject("settings") ?: JSONObject()
        val address: String
        val port: Int
        var secret = ""
        var method = ""
        var flow = ""
        when (proto) {
            "vless", "vmess" -> {
                val node = set.optJSONArray("vnext")?.optJSONObject(0) ?: set
                val user = node.optJSONArray("users")?.optJSONObject(0) ?: set
                address = str(node, "address")
                port = node.optInt("port")
                secret = str(user, "id")
                if (proto == "vless") {
                    method = str(user, "encryption").ifEmpty { "none" }
                    flow = str(user, "flow")
                } else {
                    method = str(user, "security").ifEmpty { "auto" }
                }
            }
            "trojan", "shadowsocks" -> {
                val node = set.optJSONArray("servers")?.optJSONObject(0) ?: set
                address = str(node, "address")
                port = node.optInt("port")
                secret = str(node, "password")
                if (proto == "shadowsocks") method = str(node, "method")
            }
            else -> {
                address = str(set, "address")
                port = set.optInt("port")
            }
        }
        if (address.isEmpty() || port !in 1..65535) return null
        val hy = proto == "hysteria" || proto == "hysteria2"
        val st = o.optJSONObject("streamSettings") ?: JSONObject()
        val network = when (val n = str(st, "network").lowercase()) {
            "" -> if (hy) "hysteria" else "tcp"
            "raw" -> "tcp"
            "splithttp" -> "xhttp"
            "http" -> "h2"
            else -> n
        }
        var host = ""
        var path = ""
        var headerType = ""
        var serviceName = ""
        var mode = ""
        var extra = ""
        when (network) {
            "ws" -> st.optJSONObject("wsSettings")?.let { w ->
                path = str(w, "path")
                host = str(w, "host").ifEmpty { w.optJSONObject("headers")?.let { str(it, "Host") }.orEmpty() }
            }
            "httpupgrade" -> st.optJSONObject("httpupgradeSettings")?.let { h ->
                path = str(h, "path")
                host = str(h, "host")
            }
            "xhttp" -> (st.optJSONObject("xhttpSettings") ?: st.optJSONObject("splithttpSettings"))?.let { x ->
                path = str(x, "path")
                host = str(x, "host")
                mode = str(x, "mode")
                x.optJSONObject("extra")?.let { extra = it.toString() }
            }
            "grpc" -> st.optJSONObject("grpcSettings")?.let { g ->
                serviceName = str(g, "serviceName")
                host = str(g, "authority")
                if (g.optBoolean("multiMode")) mode = "multi"
            }
            "h2" -> (st.optJSONObject("httpSettings") ?: st.optJSONObject("h2Settings"))?.let { h ->
                path = str(h, "path")
                host = joined(h, "host")
            }
            "kcp", "mkcp" -> st.optJSONObject("kcpSettings")?.let { k ->
                headerType = k.optJSONObject("header")?.let { str(it, "type") }.orEmpty()
                path = str(k, "seed")
            }
            "hysteria" -> st.optJSONObject("hysteriaSettings")?.let { secret = str(it, "auth").ifEmpty { secret } }
            else -> (st.optJSONObject("rawSettings") ?: st.optJSONObject("tcpSettings"))?.optJSONObject("header")?.let { h ->
                headerType = str(h, "type").let { if (it == "none") "" else it }
                if (headerType == "http") {
                    val req = h.optJSONObject("request")
                    path = req?.let { joined(it, "path") }.orEmpty()
                    host = req?.optJSONObject("headers")?.let { joined(it, "Host") }.orEmpty()
                }
            }
        }
        val security = str(st, "security").lowercase().ifEmpty { if (hy) "tls" else "none" }
        var sni = ""
        var fp = ""
        var alpn = ""
        var pbk = ""
        var sid = ""
        var spx = ""
        var insecure = false
        when (security) {
            "tls" -> st.optJSONObject("tlsSettings")?.let { t ->
                sni = str(t, "serverName")
                fp = str(t, "fingerprint")
                alpn = joined(t, "alpn")
                insecure = t.optBoolean("allowInsecure")
            }
            "reality" -> st.optJSONObject("realitySettings")?.let { r ->
                sni = str(r, "serverName")
                fp = str(r, "fingerprint")
                pbk = str(r, "publicKey").ifEmpty { str(r, "password") }
                sid = str(r, "shortId")
                spx = str(r, "spiderX")
            }
        }
        val obfs = st.optJSONObject("finalmask")?.optJSONArray("udp")?.optJSONObject(0)
            ?.takeIf { str(it, "type") == "salamander" }?.optJSONObject("settings")?.let { str(it, "password") }.orEmpty()

        // the outbound goes to the core as written; a dialer chain would point at outbounds we don't carry over
        val raw = JSONObject(o.toString())
        raw.remove("tag")
        raw.remove("proxySettings")
        raw.optJSONObject("streamSettings")?.let { s ->
            s.optJSONObject("sockopt")?.let { so ->
                so.remove("dialerProxy")
                if (so.length() == 0) s.remove("sockopt")
            }
        }
        return ProxyEntry(
            name = name.ifEmpty { tagName(o) }.ifEmpty { "$address:$port" },
            protocol = when (proto) {
                "shadowsocks" -> "ss"
                "hysteria" -> "hysteria2"
                else -> proto
            },
            address = address,
            port = port,
            uuid = secret,
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
            fingerprint = fp,
            alpn = alpn,
            publicKey = pbk,
            shortId = sid,
            spiderX = spx,
            insecure = insecure,
            obfsPassword = obfs,
            extra = extra,
            outbound = raw.toString(),
        )
    }

    private fun singBox(o: JSONObject, name: String): ProxyEntry? {
        val type = str(o, "type").lowercase()
        if (type !in BOX_PROXIES) return null
        val address = str(o, "server")
        val port = o.optInt("server_port")
        if (address.isEmpty() || port !in 1..65535) return null
        val tls = o.optJSONObject("tls")
        val tlsOn = tls?.optBoolean("enabled") == true || type == "hysteria2"
        val reality = tls?.optJSONObject("reality")?.takeIf { it.optBoolean("enabled") }
        var network = if (type == "hysteria2") "hysteria" else "tcp"
        var host = ""
        var path = ""
        var serviceName = ""
        var headerType = ""
        val tr = o.optJSONObject("transport")
        if (tr != null) {
            when (str(tr, "type").lowercase()) {
                "ws" -> {
                    network = "ws"
                    path = str(tr, "path")
                    host = tr.optJSONObject("headers")?.let { str(it, "Host") }.orEmpty()
                }
                "httpupgrade" -> {
                    network = "httpupgrade"
                    path = str(tr, "path")
                    host = str(tr, "host")
                }
                "grpc" -> {
                    network = "grpc"
                    serviceName = str(tr, "service_name")
                }
                "http" -> {
                    if (tlsOn) network = "h2" else headerType = "http"
                    path = str(tr, "path")
                    host = joined(tr, "host")
                }
            }
        }
        return ProxyEntry(
            name = name.ifEmpty { str(o, "tag").trim() }.ifEmpty { "$address:$port" },
            protocol = if (type == "shadowsocks") "ss" else type,
            address = address,
            port = port,
            uuid = if (type == "vless" || type == "vmess") str(o, "uuid") else str(o, "password"),
            method = when (type) {
                "vless" -> "none"
                "vmess" -> str(o, "security").ifEmpty { "auto" }
                "shadowsocks" -> str(o, "method")
                else -> ""
            },
            flow = str(o, "flow"),
            network = network,
            headerType = headerType,
            host = host,
            path = path,
            serviceName = serviceName,
            security = when {
                reality != null -> "reality"
                tlsOn -> "tls"
                else -> "none"
            },
            sni = tls?.let { str(it, "server_name") }.orEmpty(),
            fingerprint = tls?.optJSONObject("utls")?.takeIf { it.optBoolean("enabled") }?.let { str(it, "fingerprint") }.orEmpty(),
            alpn = tls?.let { joined(it, "alpn") }.orEmpty(),
            publicKey = reality?.let { str(it, "public_key") }.orEmpty(),
            shortId = reality?.let { str(it, "short_id") }.orEmpty(),
            insecure = tls?.optBoolean("insecure") == true,
            obfsPassword = o.optJSONObject("obfs")?.takeIf { str(it, "type") == "salamander" }?.let { str(it, "password") }.orEmpty(),
        )
    }

    /** String value; "" for a missing key and for JSON null (Android's org.json would turn it into "null"). */
    private fun str(o: JSONObject, key: String): String {
        val v = o.opt(key)
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    /** "a,b" from a string or from an array of strings. */
    private fun joined(o: JSONObject, key: String): String {
        val a = o.optJSONArray(key) ?: return str(o, key)
        return (0 until a.length()).mapNotNull { i -> a.opt(i)?.takeIf { it != JSONObject.NULL }?.toString() }
            .filter { it.isNotEmpty() }.joinToString(",")
    }

    // ------------------------------------------------------------------ links in other wrappers

    /** Share links found anywhere in [text]: an HTML subscription page, a message, a note. */
    fun linksIn(text: String): List<String> =
        LINK.findAll(text.replace("&amp;", "&")).map { it.value.trimEnd('.', ',', ';', ')', ']') }.distinct().toList()

    /**
     * The subscription address inside an app deep link: happ://add/https://..., v2raytun://import/...,
     * hiddify://import/..., streisand://import/..., v2rayng://install-config?url=..., clash://install-config?url=...,
     * sing-box://import-remote-profile?url=..., sub://<base64>. Null for anything else, encrypted happ links included.
     */
    fun deeplinkUrl(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.contains('\n')) return null
        val scheme = t.substringBefore("://", "").lowercase()
        if (scheme.isEmpty() || scheme == "http" || scheme == "https" || scheme in SHARE) return null
        val rest = t.substringAfter("://").substringBefore('#')
        if (scheme == "sub") return b64(rest)?.trim()?.takeIf { isHttp(it) }
        val q = rest.substringAfter('?', "").split('&').firstOrNull { it.startsWith("url=", ignoreCase = true) }
        if (q != null) return dec(q.substring(4)).trim().takeIf { isHttp(it) }
        val inner = if (isHttp(rest)) rest else rest.substringAfter('/', "")
        return when {
            isHttp(inner) -> inner.trim()
            isHttp(dec(inner)) -> dec(inner).trim()
            inner.startsWith("sub://", ignoreCase = true) -> b64(inner.substring(6))?.trim()?.takeIf { isHttp(it) }
            else -> null
        }
    }

    private fun dec(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun enc(s: String): String = try {
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    } catch (e: Exception) {
        s
    }

    // ------------------------------------------------------------------ share links

    /** [e] as a vless://, vmess://, trojan://, ss:// or hysteria2:// link. */
    fun link(e: ProxyEntry): String {
        val host = if (e.address.contains(':') && !e.address.startsWith("[")) "[" + e.address + "]" else e.address
        val tag = if (e.name.isEmpty()) "" else "#" + enc(e.name)
        return when (e.protocol) {
            "vmess" -> {
                val o = JSONObject()
                    .put("v", "2").put("ps", e.name).put("add", e.address).put("port", e.port.toString())
                    .put("id", e.uuid).put("aid", "0").put("scy", e.method.ifEmpty { "auto" }).put("net", e.network)
                    .put("type", e.headerType.ifEmpty { "none" }).put("host", e.host)
                    .put("path", if (e.network == "grpc") e.serviceName else e.path)
                    .put("tls", if (e.security == "tls") "tls" else "").put("sni", e.sni).put("alpn", e.alpn)
                    .put("fp", e.fingerprint)
                "vmess://" + b64encode(o.toString(), urlSafe = false, pad = true)
            }
            "ss" -> "ss://" + b64encode(e.method + ":" + e.uuid, urlSafe = true, pad = false) + "@" + host + ":" + e.port + tag
            "hysteria2" -> "hysteria2://" + enc(e.uuid) + "@" + host + ":" + e.port + params(
                "sni" to e.sni,
                "alpn" to e.alpn,
                "insecure" to if (e.insecure) "1" else "",
                "obfs" to if (e.obfsPassword.isNotEmpty()) "salamander" else "",
                "obfs-password" to e.obfsPassword,
            ) + tag
            else -> e.protocol + "://" + enc(e.uuid) + "@" + host + ":" + e.port + params(
                "encryption" to if (e.protocol == "vless") e.method.ifEmpty { "none" } else "",
                "flow" to e.flow,
                "security" to e.security,
                "sni" to e.sni,
                "fp" to e.fingerprint,
                "alpn" to e.alpn,
                "pbk" to e.publicKey,
                "sid" to e.shortId,
                "spx" to e.spiderX,
                "allowInsecure" to if (e.insecure) "1" else "",
                "type" to e.network,
                "headerType" to e.headerType,
                "host" to e.host,
                "path" to e.path,
                "serviceName" to e.serviceName,
                "mode" to e.mode,
                "extra" to e.extra,
            ) + tag
        }
    }

    private fun params(vararg kv: Pair<String, String>): String {
        val parts = kv.filter { it.second.isNotEmpty() }.map { it.first + "=" + enc(it.second) }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    // ------------------------------------------------------------------ base64 (java.util.Base64 needs Android 8)

    /** Standard or URL-safe base64, padded or not, to UTF-8 text. Null if [s] isn't base64 or decodes to nothing. */
    fun b64(s: String): String? {
        val clean = s.trim().filterNot { it.isWhitespace() }.trimEnd('=')
        if (clean.isEmpty()) return null
        val out = ByteArrayOutputStream(clean.length * 3 / 4 + 3)
        var buf = 0
        var bits = 0
        for (ch in clean) {
            val v = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a' + 26
                in '0'..'9' -> ch - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                else -> return null
            }
            buf = ((buf shl 6) or v) and 0xFFFFFF
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xFF)
            }
        }
        val r = out.toByteArray().toString(Charsets.UTF_8)
        return r.ifBlank { null }
    }

    fun b64encode(s: String, urlSafe: Boolean, pad: Boolean): String {
        val b = s.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder((b.size + 2) / 3 * 4)
        var i = 0
        while (i < b.size) {
            val n = minOf(3, b.size - i)
            var x = 0
            for (k in 0 until 3) x = (x shl 8) or (if (k < n) b[i + k].toInt() and 0xFF else 0)
            for (k in 0 until 4) {
                if (k <= n) sb.append(ABC[(x shr (18 - 6 * k)) and 0x3F]) else if (pad) sb.append('=')
            }
            i += 3
        }
        val r = sb.toString()
        return if (urlSafe) r.replace('+', '-').replace('/', '_') else r
    }
}
