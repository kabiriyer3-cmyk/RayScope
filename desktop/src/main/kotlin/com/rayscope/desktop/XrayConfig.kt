package com.rayscope.desktop

import org.json.JSONArray
import org.json.JSONObject

/** Builds Xray-core JSON configs for the desktop core process. */
object XrayConfig {
    private val PRIVATE_IPS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
        "127.0.0.0/8", "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128"
    )

    fun build(s: Server, p: Prefs, socksPort: Int, httpPort: Int, apiPort: Int): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", p.logLevel.ifEmpty { "warning" }))
        root.put("stats", JSONObject())
        root.put("api", JSONObject().put("tag", "api").put("services", JSONArray(listOf("StatsService"))))
        root.put(
            "policy", JSONObject()
                .put(
                    "levels", JSONObject().put(
                        "8", JSONObject()
                            .put("handshake", 4)
                            .put("connIdle", 300)
                            .put("uplinkOnly", 1)
                            .put("downlinkOnly", 1)
                    )
                )
                .put("system", JSONObject().put("statsOutboundUplink", true).put("statsOutboundDownlink", true))
        )

        val listen = if (p.allowLan) "0.0.0.0" else "127.0.0.1"
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject().put("tag", "socks").put("listen", listen).put("port", socksPort).put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("userLevel", 8))
                .put("sniffing", sniffing(p))
        )
        inbounds.put(
            JSONObject().put("tag", "http").put("listen", listen).put("port", httpPort).put("protocol", "http")
                .put("settings", JSONObject().put("userLevel", 8))
                .put("sniffing", sniffing(p))
        )
        inbounds.put(
            JSONObject().put("tag", "api").put("listen", "127.0.0.1").put("port", apiPort).put("protocol", "dokodemo-door")
                .put("settings", JSONObject().put("address", "127.0.0.1"))
        )
        root.put("inbounds", inbounds)

        val frag = useFragment(s, p)
        val outbounds = JSONArray()
        outbounds.put(outbound(s, p, frag))
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        if (frag) outbounds.put(fragmentOutbound(p))
        root.put("outbounds", outbounds)

        val rules = JSONArray()
        rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("api"))).put("outboundTag", "api"))
        val (blockDomains, blockIps) = splitRules(p.blockRules)
        val (directDomains, directIps) = splitRules(p.directRules)
        if (blockDomains.isNotEmpty()) rules.put(rule("block").put("domain", JSONArray(blockDomains)))
        if (blockIps.isNotEmpty()) rules.put(rule("block").put("ip", JSONArray(blockIps)))
        if (p.blockAds) rules.put(rule("block").put("domain", JSONArray(listOf("geosite:category-ads-all"))))
        if (p.blockQuic) rules.put(rule("block").put("network", "udp").put("port", "443"))
        if (p.bypassLan) rules.put(rule("direct").put("ip", JSONArray(PRIVATE_IPS)))
        if (p.directRu) {
            rules.put(rule("direct").put("domain", JSONArray(listOf("domain:ru", "domain:su", "domain:xn--p1ai", "geosite:category-ru"))))
            rules.put(rule("direct").put("ip", JSONArray(listOf("geoip:ru"))))
        }
        if (directDomains.isNotEmpty()) rules.put(rule("direct").put("domain", JSONArray(directDomains)))
        if (directIps.isNotEmpty()) rules.put(rule("direct").put("ip", JSONArray(directIps)))
        root.put("routing", JSONObject().put("domainStrategy", p.domainStrategy.ifEmpty { "AsIs" }).put("rules", rules))

        val dns = JSONObject().put("servers", JSONArray(p.dnsList()))
        val qs = p.dnsQueryStrategy
        if (qs == "UseIPv4" || qs == "UseIPv6") dns.put("queryStrategy", qs)
        root.put("dns", dns)
        return root.toString(2)
    }

    /** One core process, one HTTP inbound per server: used for batch latency tests. */
    fun buildTest(items: List<Pair<Server, Int>>, p: Prefs): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "none"))
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()
        var anyFrag = false
        items.forEachIndexed { i, (s, port) ->
            inbounds.put(
                JSONObject().put("tag", "in$i").put("listen", "127.0.0.1").put("port", port).put("protocol", "http")
                    .put("settings", JSONObject())
            )
            val frag = useFragment(s, p)
            if (frag) anyFrag = true
            val o = outbound(s, p, frag)
            o.put("tag", "out$i")
            o.put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
            outbounds.put(o)
            rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray(listOf("in$i"))).put("outboundTag", "out$i"))
        }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        if (anyFrag) outbounds.put(fragmentOutbound(p))
        root.put("inbounds", inbounds)
        root.put("outbounds", outbounds)
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        root.put("dns", JSONObject().put("servers", JSONArray(p.dnsList())))
        return root.toString()
    }

    private fun useFragment(s: Server, p: Prefs): Boolean =
        p.fragmentEnabled && s.protocol != "hysteria2" && s.network != "hysteria"

    private fun rule(tag: String): JSONObject = JSONObject().put("type", "field").put("outboundTag", tag)

    private fun sniffing(p: Prefs): JSONObject =
        JSONObject().put("enabled", p.sniffing)
            .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            .put("routeOnly", p.sniffRouteOnly)

    private fun fragmentOutbound(p: Prefs): JSONObject =
        JSONObject().put("tag", "fragment").put("protocol", "freedom")
            .put(
                "settings", JSONObject().put(
                    "fragment", JSONObject()
                        .put("packets", p.fragmentPackets.ifEmpty { "tlshello" })
                        .put("length", p.fragmentLength.ifEmpty { "100-200" })
                        .put("interval", p.fragmentInterval.ifEmpty { "10-20" })
                )
            )

    private fun isIpv4Rule(t: String): Boolean {
        val ip = t.substringBefore('/')
        val parts = ip.split('.')
        if (parts.size != 4) return false
        if (!parts.all { it.isNotEmpty() && it.length <= 3 && it.all { ch -> ch.isDigit() } && it.toInt() <= 255 }) return false
        val mask = t.substringAfter('/', "")
        return mask.isEmpty() || (mask.all { it.isDigit() } && mask.toInt() <= 32)
    }

    private fun isIpv6Rule(t: String): Boolean {
        val ip = t.substringBefore('/')
        return ip.contains(':') && ip.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    }

    /** "example.com, full:api.x.ru, keyword:ads, geosite:google, geoip:ru, 10.0.0.0/8" -> (domains, ips). */
    fun splitRules(raw: String): Pair<List<String>, List<String>> {
        val domains = mutableListOf<String>()
        val ips = mutableListOf<String>()
        raw.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
            val low = t.lowercase()
            when {
                low.startsWith("geoip:") -> ips += low
                low.startsWith("geosite:") || low.startsWith("domain:") || low.startsWith("full:") ||
                    low.startsWith("keyword:") || low.startsWith("regexp:") -> domains += t
                isIpv4Rule(t) || isIpv6Rule(t) -> ips += t
                else -> domains += "domain:$low"
            }
        }
        return domains to ips
    }

    fun outbound(s: Server, p: Prefs, fragment: Boolean): JSONObject {
        val o = JSONObject().put("tag", "proxy")
        val set = JSONObject().put("address", s.address).put("port", s.port)
        when (s.protocol) {
            "vless" -> {
                o.put("protocol", "vless")
                set.put("id", s.uuid).put("encryption", s.method.ifEmpty { "none" }).put("level", 8)
                if (s.flow.isNotEmpty()) set.put("flow", s.flow)
            }
            "vmess" -> {
                o.put("protocol", "vmess")
                set.put("id", s.uuid).put("security", s.method.ifEmpty { "auto" }).put("level", 8)
            }
            "trojan" -> {
                o.put("protocol", "trojan")
                set.put("password", s.uuid).put("level", 8)
            }
            "ss" -> {
                o.put("protocol", "shadowsocks")
                set.put("password", s.uuid).put("method", s.method).put("level", 8)
            }
            "hysteria2" -> {
                o.put("protocol", "hysteria")
                set.put("version", 2)
            }
            else -> throw IllegalArgumentException("Неподдерживаемый протокол: ${s.protocol}")
        }
        o.put("settings", set)

        val stream = stream(s, p)
        val udpOnly = s.protocol == "hysteria2" || s.network == "hysteria"
        val sock = JSONObject()
        if (fragment && !udpOnly) sock.put("dialerProxy", "fragment")
        if (!udpOnly && p.tcpFastOpen) sock.put("tcpFastOpen", true)
        if (sock.length() > 0) stream.put("sockopt", sock)
        o.put("streamSettings", stream)

        if (p.muxEnabled && s.flow.isEmpty() && !udpOnly) {
            o.put(
                "mux", JSONObject()
                    .put("enabled", true)
                    .put("concurrency", p.muxConcurrency.coerceIn(1, 1024))
                    .put("xudpConcurrency", 16)
                    .put("xudpProxyUDP443", "reject")
            )
        } else {
            o.put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
        }
        return o
    }

    private fun isDomain(h: String): Boolean =
        h.isNotEmpty() && !h.contains(':') && !h.all { it.isDigit() || it == '.' }

    private fun stream(s: Server, p: Prefs): JSONObject {
        val ss = JSONObject()
        var hostSni = ""
        when (s.network) {
            "ws" -> {
                ss.put("network", "ws")
                ss.put("wsSettings", JSONObject().put("path", s.path.ifEmpty { "/" }).put("host", s.host))
                hostSni = s.host
            }
            "httpupgrade" -> {
                ss.put("network", "httpupgrade")
                ss.put("httpupgradeSettings", JSONObject().put("path", s.path.ifEmpty { "/" }).put("host", s.host))
                hostSni = s.host
            }
            "xhttp" -> {
                ss.put("network", "xhttp")
                val x = JSONObject().put("path", s.path.ifEmpty { "/" }).put("host", s.host)
                if (s.mode.isNotEmpty()) x.put("mode", s.mode)
                if (s.extra.isNotEmpty()) {
                    try {
                        x.put("extra", JSONObject(s.extra))
                    } catch (e: Exception) {
                        // ignore malformed extra
                    }
                }
                ss.put("xhttpSettings", x)
                hostSni = s.host
            }
            "grpc" -> {
                ss.put("network", "grpc")
                ss.put(
                    "grpcSettings", JSONObject().put("serviceName", s.serviceName).put("multiMode", s.mode == "multi")
                        .put("authority", s.host).put("idle_timeout", 60).put("health_check_timeout", 20)
                )
                hostSni = s.host
            }
            "h2" -> {
                ss.put("network", "h2")
                val hosts = s.host.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                ss.put("httpSettings", JSONObject().put("host", JSONArray(hosts)).put("path", s.path.ifEmpty { "/" }))
                hostSni = hosts.firstOrNull().orEmpty()
            }
            "hysteria" -> {
                ss.put("network", "hysteria")
                ss.put("hysteriaSettings", JSONObject().put("version", 2).put("auth", s.uuid))
                if (s.obfsPassword.isNotEmpty()) {
                    val mask = JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", s.obfsPassword))
                    ss.put("finalmask", JSONObject().put("udp", JSONArray().put(mask)))
                }
            }
            else -> {
                ss.put("network", "tcp")
                if (s.headerType == "http") {
                    val hosts = s.host.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val req = JSONObject()
                        .put("path", JSONArray(listOf(s.path.ifEmpty { "/" })))
                        .put("headers", JSONObject().put("Host", JSONArray(hosts)))
                    ss.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", req)))
                    hostSni = hosts.firstOrNull().orEmpty()
                } else {
                    hostSni = s.host
                }
            }
        }

        val sec = s.security.lowercase()
        if (sec == "tls" || sec == "reality") {
            ss.put("security", sec)
            val sni = s.sni.ifEmpty { listOf(hostSni, s.address).firstOrNull { isDomain(it) }.orEmpty() }
            val t = JSONObject()
            if (sni.isNotEmpty()) t.put("serverName", sni)
            val defFp = p.defaultFingerprint
            if (sec == "tls") {
                val fp = s.fingerprint.ifEmpty { defFp }
                if (fp.isNotEmpty()) t.put("fingerprint", fp)
                val alpn = s.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (alpn.isNotEmpty()) t.put("alpn", JSONArray(alpn))
                if (s.insecure) t.put("allowInsecure", true)
                ss.put("tlsSettings", t)
            } else {
                t.put("fingerprint", s.fingerprint.ifEmpty { defFp.ifEmpty { "chrome" } })
                t.put("publicKey", s.publicKey)
                if (s.shortId.isNotEmpty()) t.put("shortId", s.shortId)
                if (s.spiderX.isNotEmpty()) t.put("spiderX", s.spiderX)
                ss.put("realitySettings", t)
            }
        }
        return ss
    }
}
