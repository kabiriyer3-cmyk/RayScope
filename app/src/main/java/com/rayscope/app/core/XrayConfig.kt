package com.rayscope.app.core

import com.rayscope.app.data.Settings
import com.rayscope.app.model.Server
import org.json.JSONArray
import org.json.JSONObject

/** Builds Xray-core JSON configs (flat outbound settings format, as used by v2rayNG). */
object XrayConfig {
    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    private val PRIVATE_IPS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
        "127.0.0.0/8", "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128"
    )

    fun build(s: Server, st: Settings, tun: Boolean): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("stats", JSONObject())
        root.put(
            "policy", JSONObject()
                .put("levels", JSONObject().put("8", JSONObject().put("handshake", 4).put("connIdle", 300).put("uplinkOnly", 1).put("downlinkOnly", 1)))
                .put("system", JSONObject().put("statsOutboundUplink", true).put("statsOutboundDownlink", true))
        )

        val inbounds = JSONArray()
        inbounds.put(
            JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", SOCKS_PORT).put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("userLevel", 8))
                .put("sniffing", sniffing(st))
        )
        inbounds.put(
            JSONObject().put("tag", "http").put("listen", "127.0.0.1").put("port", HTTP_PORT).put("protocol", "http")
                .put("settings", JSONObject().put("userLevel", 8))
                .put("sniffing", sniffing(st))
        )
        if (tun) {
            inbounds.put(
                JSONObject().put("tag", "tun").put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "xray0").put("MTU", st.mtu).put("userLevel", 8))
                    .put("sniffing", sniffing(st))
            )
        }
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(outbound(s))
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        root.put("outbounds", outbounds)

        val rules = JSONArray()
        if (st.bypassLan) {
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray(PRIVATE_IPS)).put("outboundTag", "direct"))
        }
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        root.put("dns", JSONObject().put("servers", JSONArray(st.dnsList())))
        return root.toString()
    }

    private fun sniffing(st: Settings): JSONObject =
        JSONObject().put("enabled", st.sniffing).put("destOverride", JSONArray(listOf("http", "tls", "quic")))

    fun outbound(s: Server): JSONObject {
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
        o.put("streamSettings", stream(s))
        o.put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
        return o
    }

    private fun isDomain(h: String): Boolean =
        h.isNotEmpty() && !h.contains(':') && !h.matches(Regex("^[0-9.]+$"))

    private fun stream(s: Server): JSONObject {
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
            if (sec == "tls") {
                if (s.fingerprint.isNotEmpty()) t.put("fingerprint", s.fingerprint)
                val alpn = s.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (alpn.isNotEmpty()) t.put("alpn", JSONArray(alpn))
                if (s.insecure) t.put("allowInsecure", true)
                ss.put("tlsSettings", t)
            } else {
                t.put("fingerprint", s.fingerprint.ifEmpty { "chrome" })
                t.put("publicKey", s.publicKey)
                if (s.shortId.isNotEmpty()) t.put("shortId", s.shortId)
                if (s.spiderX.isNotEmpty()) t.put("spiderX", s.spiderX)
                ss.put("realitySettings", t)
            }
        }
        return ss
    }
}
