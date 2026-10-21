package com.rayscope.desktop

import org.json.JSONObject

/** All desktop settings, stored as prefs.json in the data folder. */
data class Prefs(
    /** 1 = Windows system proxy, 0 = only local proxy ports */
    val mode: Int = 1,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val allowLan: Boolean = false,
    val logLevel: String = "warning",
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val tcpFastOpen: Boolean = false,
    val testUrl: String = DEFAULT_TEST_URL,
    val pingTimeout: Int = 5000,
    val concurrency: Int = 8,
    val goodPing: Int = 300,
    val okPing: Int = 800,
    val testOnLaunch: Boolean = false,
    val livePingInterval: Int = 10,
    val dns: String = "1.1.1.1, 8.8.8.8",
    val dnsQueryStrategy: String = "UseIP",
    val bypassLan: Boolean = true,
    val directRu: Boolean = false,
    val blockAds: Boolean = false,
    val blockQuic: Boolean = false,
    val domainStrategy: String = "AsIs",
    val sniffing: Boolean = true,
    val sniffRouteOnly: Boolean = false,
    val directRules: String = "",
    val blockRules: String = "",
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "100-200",
    val fragmentInterval: String = "10-20",
    val defaultFingerprint: String = "",
    val userAgent: String = DEFAULT_UA,
    val autoUpdateSubs: Boolean = true,
    /** 0 system, 1 light, 2 dark */
    val theme: Int = 2,
    val accent: Int = 0,
    /** Liquid Glass: 0 off, 1 soft, 2 strong */
    val glass: Int = 1,
    /** label of the active tab in the floating tab bar */
    val tabLabels: Boolean = true,
    val compact: Boolean = false,
    val connectOnLaunch: Boolean = false,
    val closeToTray: Boolean = true,
    val dev: Boolean = false,
    val selectedId: String = "",
    val sortMode: Int = 0,
) {
    fun dnsList(): List<String> =
        dns.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("1.1.1.1") }

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("socksPort", socksPort)
        .put("httpPort", httpPort)
        .put("allowLan", allowLan)
        .put("logLevel", logLevel)
        .put("muxEnabled", muxEnabled)
        .put("muxConcurrency", muxConcurrency)
        .put("tcpFastOpen", tcpFastOpen)
        .put("testUrl", testUrl)
        .put("pingTimeout", pingTimeout)
        .put("concurrency", concurrency)
        .put("goodPing", goodPing)
        .put("okPing", okPing)
        .put("testOnLaunch", testOnLaunch)
        .put("livePingInterval", livePingInterval)
        .put("dns", dns)
        .put("dnsQueryStrategy", dnsQueryStrategy)
        .put("bypassLan", bypassLan)
        .put("directRu", directRu)
        .put("blockAds", blockAds)
        .put("blockQuic", blockQuic)
        .put("domainStrategy", domainStrategy)
        .put("sniffing", sniffing)
        .put("sniffRouteOnly", sniffRouteOnly)
        .put("directRules", directRules)
        .put("blockRules", blockRules)
        .put("fragmentEnabled", fragmentEnabled)
        .put("fragmentPackets", fragmentPackets)
        .put("fragmentLength", fragmentLength)
        .put("fragmentInterval", fragmentInterval)
        .put("defaultFingerprint", defaultFingerprint)
        .put("userAgent", userAgent)
        .put("autoUpdateSubs", autoUpdateSubs)
        .put("theme", theme)
        .put("accent", accent)
        .put("glass", glass)
        .put("tabLabels", tabLabels)
        .put("compact", compact)
        .put("connectOnLaunch", connectOnLaunch)
        .put("closeToTray", closeToTray)
        .put("dev", dev)
        .put("selectedId", selectedId)
        .put("sortMode", sortMode)

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_UA = "v2rayN/7.10.0"

        fun fromJson(o: JSONObject): Prefs {
            val d = Prefs()
            return Prefs(
                mode = o.optInt("mode", d.mode),
                socksPort = o.optInt("socksPort", d.socksPort),
                httpPort = o.optInt("httpPort", d.httpPort),
                allowLan = o.optBoolean("allowLan", d.allowLan),
                logLevel = o.optString("logLevel", d.logLevel),
                muxEnabled = o.optBoolean("muxEnabled", d.muxEnabled),
                muxConcurrency = o.optInt("muxConcurrency", d.muxConcurrency),
                tcpFastOpen = o.optBoolean("tcpFastOpen", d.tcpFastOpen),
                testUrl = o.optString("testUrl", d.testUrl).ifBlank { DEFAULT_TEST_URL },
                pingTimeout = o.optInt("pingTimeout", d.pingTimeout),
                concurrency = o.optInt("concurrency", d.concurrency),
                goodPing = o.optInt("goodPing", d.goodPing),
                okPing = o.optInt("okPing", d.okPing),
                testOnLaunch = o.optBoolean("testOnLaunch", d.testOnLaunch),
                livePingInterval = o.optInt("livePingInterval", d.livePingInterval),
                dns = o.optString("dns", d.dns),
                dnsQueryStrategy = o.optString("dnsQueryStrategy", d.dnsQueryStrategy),
                bypassLan = o.optBoolean("bypassLan", d.bypassLan),
                directRu = o.optBoolean("directRu", d.directRu),
                blockAds = o.optBoolean("blockAds", d.blockAds),
                blockQuic = o.optBoolean("blockQuic", d.blockQuic),
                domainStrategy = o.optString("domainStrategy", d.domainStrategy),
                sniffing = o.optBoolean("sniffing", d.sniffing),
                sniffRouteOnly = o.optBoolean("sniffRouteOnly", d.sniffRouteOnly),
                directRules = o.optString("directRules", d.directRules),
                blockRules = o.optString("blockRules", d.blockRules),
                fragmentEnabled = o.optBoolean("fragmentEnabled", d.fragmentEnabled),
                fragmentPackets = o.optString("fragmentPackets", d.fragmentPackets),
                fragmentLength = o.optString("fragmentLength", d.fragmentLength),
                fragmentInterval = o.optString("fragmentInterval", d.fragmentInterval),
                defaultFingerprint = o.optString("defaultFingerprint", d.defaultFingerprint),
                userAgent = o.optString("userAgent", d.userAgent).ifBlank { DEFAULT_UA },
                autoUpdateSubs = o.optBoolean("autoUpdateSubs", d.autoUpdateSubs),
                theme = o.optInt("theme", d.theme),
                accent = o.optInt("accent", d.accent),
                glass = o.optInt("glass", d.glass).coerceIn(0, 2),
                tabLabels = o.optBoolean("tabLabels", d.tabLabels),
                compact = o.optBoolean("compact", d.compact),
                connectOnLaunch = o.optBoolean("connectOnLaunch", d.connectOnLaunch),
                closeToTray = o.optBoolean("closeToTray", d.closeToTray),
                dev = o.optBoolean("dev", d.dev),
                selectedId = o.optString("selectedId", d.selectedId),
                sortMode = o.optInt("sortMode", d.sortMode),
            )
        }
    }
}
