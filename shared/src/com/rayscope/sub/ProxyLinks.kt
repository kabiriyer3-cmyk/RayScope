/*
 * Plain proxies as servers: SOCKS5 and HTTP(S) proxies the way proxy shops, Telegram and v2rayN hand them out.
 * Every form is read into a [ProxyEntry] (protocol "socks" or "http", the login and password in uuid as user:pass),
 * so the apps treat a proxy like any other server: ping, real exit IP, country filter, sharing.
 *
 *   socks5://user:pass@host:port#name, socks5h://, socks:// (v2rayN puts base64(user:pass) before the @)
 *   http://user:pass@host:port, https://host:port (an HTTP proxy behind TLS)
 *   tg://socks?server=&port=&user=&pass=, https://t.me/socks?...
 *   host:port, host:port:user:pass, user:pass:host:port, user:pass@host:port, host:port@user:pass (all SOCKS5)
 *
 * Shared by Android and the desktop app.
 */
package com.rayscope.sub

import java.net.URLDecoder
import java.net.URLEncoder

object ProxyLinks {
    private val SOCKS = setOf("socks", "socks5", "socks5h")
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
    private val IPV6 = Regex("^[0-9a-f:.]+$", RegexOption.IGNORE_CASE)
    private val DOMAIN = Regex(
        "^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+([a-z]{2,63}|xn--[a-z0-9-]{1,59})$",
        RegexOption.IGNORE_CASE
    )
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$", RegexOption.IGNORE_CASE)

    /** Protocols plain proxies get inside the apps. */
    fun isPlain(protocol: String): Boolean = protocol == "socks" || protocol == "http"

    fun isProxy(line: String): Boolean = parse(line) != null

    /** t.me and telegram.me addresses: a Telegram proxy or a channel, never a subscription to download. */
    fun isTelegram(s: String): Boolean {
        val rest = s.trim().substringAfter("://", "")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').lowercase()
        return host == "t.me" || host == "telegram.me"
    }

    /** A SOCKS5 or HTTP(S) proxy in any of the forms above, null for everything else. */
    fun parse(line: String): ProxyEntry? {
        val t = line.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val scheme = t.substringBefore("://", "").lowercase()
        return try {
            when {
                scheme == "tg" -> telegram(t.substringAfter("://"))
                scheme.isEmpty() -> when {
                    t.startsWith("t.me/", true) || t.startsWith("telegram.me/", true) -> telegram(t.substringAfter('/'))
                    else -> bare(t)
                }
                scheme == "http" || scheme == "https" ->
                    if (isTelegram(t)) telegram(t.substringAfter("://").substringAfter('/', "")) else url(t, "http")
                scheme in SOCKS -> url(t, "socks")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** [e] as socks5://user:pass@host:port#name or http(s)://..., the form other clients import. */
    fun link(e: ProxyEntry): String {
        val tls = e.security == "tls"
        val scheme = when {
            e.protocol == "socks" -> "socks5"
            tls -> "https"
            else -> "http"
        }
        val host = if (e.address.contains(':') && !e.address.startsWith("[")) "[" + e.address + "]" else e.address
        val auth = if (e.uuid.isEmpty()) "" else enc(e.uuid.substringBefore(':')) + ":" + enc(e.uuid.substringAfter(':', "")) + "@"
        val tag = if (e.name.isEmpty() || e.name == defaultName(e.protocol, tls, e.address, e.port)) "" else "#" + enc(e.name)
        return "$scheme://$auth$host:${e.port}$tag"
    }

    private fun defaultName(kind: String, tls: Boolean, host: String, port: Int): String {
        val label = when {
            kind == "socks" -> "SOCKS5"
            tls -> "HTTPS"
            else -> "HTTP"
        }
        return label + " " + (if (host.contains(':')) "[$host]" else host) + ":" + port
    }

    private fun telegram(path: String): ProxyEntry? {
        if (!path.substringBefore('?').trimEnd('/').equals("socks", ignoreCase = true)) return null
        val q = query(path.substringAfter('?', ""))
        val host = q["server"].orEmpty().removePrefix("[").removeSuffix("]")
        val port = q["port"]?.let { port(it) ?: return null } ?: 1080
        if (!validHost(host, strict = false)) return null
        return entry("socks", host, port, q["user"].orEmpty(), q["pass"].orEmpty(), tls = false, name = "")
    }

    private fun url(t: String, kind: String): ProxyEntry? {
        val tls = kind == "http" && t.startsWith("https://", ignoreCase = true)
        var rest = t.substringAfter("://")
        val name = if (rest.contains('#')) dec(rest.substringAfter('#')).trim() else ""
        rest = rest.substringBefore('#')
        val query = rest.substringAfter('?', "")
        rest = rest.substringBefore('?')
        val path = rest.substringAfter('/', "")
        rest = rest.substringBefore('/')
        val at = rest.lastIndexOf('@')
        val auth = if (at >= 0) rest.substring(0, at) else ""
        val (host, explicitPort) = hostPort(if (at >= 0) rest.substring(at + 1) else rest, allowNoPort = true) ?: return null
        if (!validHost(host, strict = false)) return null
        if (kind == "http") {
            // a path or a query means a web page or a subscription, and so does a site address without a port
            if (path.isNotEmpty() || query.isNotEmpty()) return null
            if (at < 0 && explicitPort == null) return null
        }
        val port = explicitPort ?: when {
            kind == "socks" -> 1080
            tls -> 443
            else -> 80
        }
        val (user, pass) = credentials(auth)
        return entry(kind, host, port, user, pass, tls, name)
    }

    private fun bare(t: String): ProxyEntry? {
        val at = t.lastIndexOf('@')
        if (at >= 0) {
            val left = t.substring(0, at)
            val right = t.substring(at + 1)
            hostPort(right, allowNoPort = false)?.let { (h, p) -> if (p != null && validHost(h, true)) return socks(h, p, left) }
            hostPort(left, allowNoPort = false)?.let { (h, p) -> if (p != null && validHost(h, true)) return socks(h, p, right) }
            return null
        }
        hostPort(t, allowNoPort = false)?.let { (h, p) ->
            return if (p != null && validHost(h, true)) entry("socks", h, p, "", "", false, "") else null
        }
        val parts = t.split(':')
        if (parts.size < 4) return null
        // host:port:user:pass, the password may hold more colons
        val p1 = port(parts[1])
        if (p1 != null && validHost(parts[0], true)) {
            return entry("socks", parts[0], p1, parts[2], parts.drop(3).joinToString(":"), false, "")
        }
        // user:pass:host:port
        if (parts.size == 4) {
            val p3 = port(parts[3])
            if (p3 != null && validHost(parts[2], true)) return entry("socks", parts[2], p3, parts[0], parts[1], false, "")
        }
        return null
    }

    private fun socks(host: String, port: Int, auth: String): ProxyEntry {
        val i = auth.indexOf(':')
        val user = if (i >= 0) auth.substring(0, i) else auth
        val pass = if (i >= 0) auth.substring(i + 1) else ""
        return entry("socks", host, port, user, pass, false, "")
    }

    /** "host:port", "[v6]:port" and, with [allowNoPort], a host alone. */
    private fun hostPort(s: String, allowNoPort: Boolean): Pair<String, Int?>? {
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            val host = s.substring(1, end)
            val tail = s.substring(end + 1)
            if (!host.contains(':')) return null
            return when {
                tail.isEmpty() -> if (allowNoPort) host to null else null
                tail.startsWith(":") -> port(tail.substring(1))?.let { host to it }
                else -> null
            }
        }
        val i = s.lastIndexOf(':')
        if (i < 0) return if (allowNoPort && s.isNotEmpty()) s to null else null
        val host = s.substring(0, i)
        if (host.isEmpty() || host.contains(':')) return null
        return port(s.substring(i + 1))?.let { host to it }
    }

    private fun port(s: String): Int? =
        if (s.isEmpty() || s.length > 5 || !s.all { it.isDigit() }) null else s.toInt().takeIf { it in 1..65535 }

    private fun validHost(h: String, strict: Boolean): Boolean = when {
        h.isEmpty() -> false
        h.contains(':') -> h.count { it == ':' } >= 2 && IPV6.matches(h)
        IPV4.matches(h) -> true
        DOMAIN.matches(h) -> true
        else -> !strict && LABEL.matches(h)
    }

    private fun isIp(h: String): Boolean = IPV4.matches(h) || h.contains(':')

    private fun credentials(auth: String): Pair<String, String> {
        if (auth.isEmpty()) return "" to ""
        val i = auth.indexOf(':')
        if (i >= 0) return dec(auth.substring(0, i)) to dec(auth.substring(i + 1))
        // v2rayN: socks://base64(user:pass)@host:port
        val d = SubFormats.b64(dec(auth))
        if (d != null && d.contains(':') && d.none { it.isISOControl() || it == '\uFFFD' }) {
            return d.substringBefore(':') to d.substringAfter(':')
        }
        return dec(auth) to ""
    }

    private fun entry(kind: String, host: String, port: Int, user: String, pass: String, tls: Boolean, name: String): ProxyEntry =
        ProxyEntry(
            name = name.ifEmpty { defaultName(kind, tls, host, port) },
            protocol = kind,
            address = host,
            port = port,
            uuid = if (user.isEmpty() && pass.isEmpty()) "" else "$user:$pass",
            network = "tcp",
            security = if (tls) "tls" else "none",
            sni = if (tls && !isIp(host)) host else "",
        )

    private fun query(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate { it.substringBefore('=').lowercase() to dec(it.substringAfter('=')) }

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
}
