package com.rayscope.app.data

import android.content.Context
import android.content.SharedPreferences

class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun str(k: String, d: String): String = p.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    // ------------------------------------------------------------------ general

    var selectedId: String
        get() = str("selected", "")
        set(v) = putStr("selected", v)

    /** 0 = system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = p.getInt("theme", 0)
        set(v) = putInt("theme", v)

    /** Servers list sort: 0 default, 1 ping, 2 name, 3 stability */
    var sortMode: Int
        get() = p.getInt("sortMode", 0)
        set(v) = putInt("sortMode", v)

    // ------------------------------------------------------------------ subscriptions

    var userAgent: String
        get() = str("ua", DEFAULT_UA)
        set(v) = putStr("ua", v)

    var autoUpdateSubs: Boolean
        get() = p.getBoolean("autoUpdateSubs", true)
        set(v) = putBool("autoUpdateSubs", v)

    var testAfterUpdate: Boolean
        get() = p.getBoolean("testAfterUpdate", false)
        set(v) = putBool("testAfterUpdate", v)

    // ------------------------------------------------------------------ connection behaviour

    var connectOnLaunch: Boolean
        get() = p.getBoolean("connectOnLaunch", false)
        set(v) = putBool("connectOnLaunch", v)

    var pickBestOnConnect: Boolean
        get() = p.getBoolean("pickBestOnConnect", false)
        set(v) = putBool("pickBestOnConnect", v)

    var reconnectOnNetworkChange: Boolean
        get() = p.getBoolean("netReconnect", true)
        set(v) = putBool("netReconnect", v)

    var autoFailover: Boolean
        get() = p.getBoolean("autoFailover", false)
        set(v) = putBool("autoFailover", v)

    /** Failed control pings in a row before auto-failover kicks in. */
    var failoverThreshold: Int
        get() = p.getInt("failoverThreshold", 2)
        set(v) = putInt("failoverThreshold", v)

    // ------------------------------------------------------------------ mux

    var muxEnabled: Boolean
        get() = p.getBoolean("mux", false)
        set(v) = putBool("mux", v)

    var muxConcurrency: Int
        get() = p.getInt("muxConc", 8)
        set(v) = putInt("muxConc", v)

    var muxXudpConcurrency: Int
        get() = p.getInt("muxXudp", 16)
        set(v) = putInt("muxXudp", v)

    /** reject / allow / skip */
    var muxXudpQuic: String
        get() = str("muxXudpQuic", "reject")
        set(v) = putStr("muxXudpQuic", v)

    // ------------------------------------------------------------------ tcp / timeouts / tunnel

    var tcpFastOpen: Boolean
        get() = p.getBoolean("tfo", false)
        set(v) = putBool("tfo", v)

    /** TCP keep-alive idle, seconds. 0 = system default. */
    var keepAliveIdle: Int
        get() = p.getInt("keepAlive", 0)
        set(v) = putInt("keepAlive", v)

    /** Xray policy: handshake timeout, seconds. */
    var handshakeTimeout: Int
        get() = p.getInt("handshake", 4)
        set(v) = putInt("handshake", v)

    /** Xray policy: idle connection timeout, seconds. */
    var connIdle: Int
        get() = p.getInt("connIdle", 300)
        set(v) = putInt("connIdle", v)

    var mtu: Int
        get() = p.getInt("mtu", 1500)
        set(v) = putInt("mtu", v)

    /** none / error / warning / info / debug */
    var logLevel: String
        get() = str("logLevel", "warning")
        set(v) = putStr("logLevel", v)

    // ------------------------------------------------------------------ anti-censorship

    var fragmentEnabled: Boolean
        get() = p.getBoolean("fragment", false)
        set(v) = putBool("fragment", v)

    var fragmentPackets: String
        get() = str("fragPackets", "tlshello")
        set(v) = putStr("fragPackets", v)

    var fragmentLength: String
        get() = str("fragLength", "100-200")
        set(v) = putStr("fragLength", v)

    var fragmentInterval: String
        get() = str("fragInterval", "10-20")
        set(v) = putStr("fragInterval", v)

    /** uTLS fingerprint for servers that have none. Empty = leave as is. */
    var defaultFingerprint: String
        get() = str("defaultFp", "")
        set(v) = putStr("defaultFp", v)

    // ------------------------------------------------------------------ routing

    var bypassLan: Boolean
        get() = p.getBoolean("bypassLan", true)
        set(v) = putBool("bypassLan", v)

    var ipv6: Boolean
        get() = p.getBoolean("ipv6", false)
        set(v) = putBool("ipv6", v)

    var sniffing: Boolean
        get() = p.getBoolean("sniffing", true)
        set(v) = putBool("sniffing", v)

    var sniffRouteOnly: Boolean
        get() = p.getBoolean("sniffRouteOnly", false)
        set(v) = putBool("sniffRouteOnly", v)

    var blockQuic: Boolean
        get() = p.getBoolean("blockQuic", false)
        set(v) = putBool("blockQuic", v)

    /** AsIs / IPIfNonMatch / IPOnDemand */
    var domainStrategy: String
        get() = str("domainStrategy", "AsIs")
        set(v) = putStr("domainStrategy", v)

    /** Comma separated domains / IPs / CIDRs that go direct. */
    var directRules: String
        get() = str("directRules", "")
        set(v) = putStr("directRules", v)

    /** Comma separated domains / IPs / CIDRs that are blocked. */
    var blockRules: String
        get() = str("blockRules", "")
        set(v) = putStr("blockRules", v)

    /** 0 = all apps, 1 = bypass selected apps, 2 = only selected apps */
    var perAppMode: Int
        get() = p.getInt("perAppMode", 0)
        set(v) = putInt("perAppMode", v)

    var perAppSet: Set<String>
        get() = p.getStringSet("perAppSet", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("perAppSet", v).apply()

    // ------------------------------------------------------------------ dns

    var dns: String
        get() = str("dns", "1.1.1.1, 8.8.8.8")
        set(v) = putStr("dns", v)

    /** UseIP / UseIPv4 / UseIPv6 */
    var dnsQueryStrategy: String
        get() = str("dnsQuery", "UseIP")
        set(v) = putStr("dnsQuery", v)

    // ------------------------------------------------------------------ local proxy

    var socksPort: Int
        get() = p.getInt("socksPort", 10808)
        set(v) = putInt("socksPort", v)

    var httpPort: Int
        get() = p.getInt("httpPort", 10809)
        set(v) = putInt("httpPort", v)

    var allowLan: Boolean
        get() = p.getBoolean("allowLan", false)
        set(v) = putBool("allowLan", v)

    // ------------------------------------------------------------------ ping / tests

    var testUrl: String
        get() = str("testUrl", DEFAULT_TEST_URL)
        set(v) = putStr("testUrl", v)

    /** 0 = real delay + TCP, 1 = real delay only, 2 = TCP only (fast) */
    var pingMode: Int
        get() = p.getInt("pingMode", 0)
        set(v) = putInt("pingMode", v)

    /** Results slower than this are treated as failures; also the TCP connect timeout. */
    var pingTimeout: Int
        get() = p.getInt("pingTimeout", 5000)
        set(v) = putInt("pingTimeout", v)

    var pingAttempts: Int
        get() = p.getInt("pingAttempts", 1)
        set(v) = putInt("pingAttempts", v)

    /** 0 = best of attempts, 1 = average */
    var pingAggregate: Int
        get() = p.getInt("pingAggregate", 0)
        set(v) = putInt("pingAggregate", v)

    var concurrency: Int
        get() = p.getInt("conc", 8)
        set(v) = putInt("conc", v)

    /** Latency below this is "good" (green). */
    var goodPing: Int
        get() = p.getInt("goodPing", 300)
        set(v) = putInt("goodPing", v)

    /** Latency below this is "ok" (amber), above is slow. */
    var okPing: Int
        get() = p.getInt("okPing", 800)
        set(v) = putInt("okPing", v)

    /** Control ping interval while connected, seconds. */
    var livePingInterval: Int
        get() = p.getInt("livePing", 30)
        set(v) = putInt("livePing", v)

    /** Re-test all servers every N minutes while the app is alive. 0 = off. */
    var autoRetestMinutes: Int
        get() = p.getInt("autoRetest", 0)
        set(v) = putInt("autoRetest", v)

    var testOnLaunch: Boolean
        get() = p.getBoolean("testOnLaunch", false)
        set(v) = putBool("testOnLaunch", v)

    fun dnsList(): List<String> =
        dns.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("1.1.1.1") }

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_UA = "v2rayNG/1.10.20"
    }
}
