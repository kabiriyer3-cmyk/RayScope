package com.rayscope.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Net {
    fun conn(url: String, proxyPort: Int?, timeout: Int): HttpURLConnection {
        val u = URI(url).toURL()
        val proxy = if (proxyPort != null) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort)) else Proxy.NO_PROXY
        val c = u.openConnection(proxy) as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.useCaches = false
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) RayScope")
        return c
    }

    /** Time to the first response (any HTTP code), -1 on failure. */
    fun delay(url: String, proxyPort: Int?, timeout: Int): Long {
        val t0 = System.nanoTime()
        return try {
            val c = conn(url, proxyPort, timeout)
            try {
                val code = c.responseCode
                if (code <= 0) -1L else (System.nanoTime() - t0) / 1_000_000
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            -1L
        }
    }

    fun getText(url: String, proxyPort: Int?, timeout: Int): String {
        val c = conn(url, proxyPort, timeout)
        c.instanceFollowRedirects = true
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}

object Tester {
    val TEST_URLS = listOf(
        "Google 204" to "https://www.gstatic.com/generate_204",
        "Cloudflare" to "https://cp.cloudflare.com/generate_204",
        "Apple" to "https://captive.apple.com/hotspot-detect.html",
        "Яндекс" to "https://ya.ru/",
    )

    fun tcp(s: Server, timeout: Int): Long {
        if (s.protocol == "hysteria2") return Server.DELAY_UNTESTED
        return try {
            val t0 = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(s.address, s.port), timeout) }
            (System.nanoTime() - t0) / 1_000_000
        } catch (e: Exception) {
            -1L
        }
    }

    /** Real latency through the core: one xray process per chunk, one HTTP inbound per server. */
    suspend fun realDelays(list: List<Server>, p: Prefs, onResult: (String, Long, String) -> Unit) {
        val sem = Semaphore(p.concurrency.coerceIn(1, 64))
        for (chunk in list.chunked(32)) testChunk(chunk, p, sem, onResult)
    }

    private suspend fun testChunk(chunk: List<Server>, p: Prefs, sem: Semaphore, onResult: (String, Long, String) -> Unit) {
        val items = mutableListOf<Pair<Server, Int>>()
        val usedPorts = HashSet<Int>()
        for (s in chunk) {
            val valid = runCatching { XrayConfig.outbound(s, p, false) }.isSuccess
            if (!valid) {
                onResult(s.id, -1L, "Неподдерживаемый конфиг")
                continue
            }
            var port = Core.freePort()
            while (!usedPorts.add(port)) port = Core.freePort()
            items += s to port
        }
        if (items.isEmpty()) return
        val tag = "test-" + items.first().second
        val pr = try {
            Core.start(XrayConfig.buildTest(items, p), tag, log = false)
        } catch (e: Exception) {
            items.forEach { onResult(it.first.id, -1L, e.message ?: "ядро не запустилось") }
            return
        }
        try {
            val ready = withContext(Dispatchers.IO) { Core.waitPort(items.last().second, pr, 5000) }
            if (!ready) {
                pr.destroy()
                if (items.size > 1) {
                    // one broken config kills the whole batch: isolate it
                    for (it in items) testChunk(listOf(it.first), p, sem, onResult)
                } else {
                    onResult(items[0].first.id, -1L, "Ядро отклонило конфиг")
                }
                return
            }
            coroutineScope {
                items.map { (s, port) ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val d = Net.delay(p.testUrl, port, p.pingTimeout)
                            onResult(s.id, d, if (d < 0) "Нет ответа за ${p.pingTimeout} мс" else "")
                        }
                    }
                }.awaitAll()
            }
        } finally {
            pr.destroy()
            Core.cleanup(tag)
        }
    }
}

data class IpInfo(
    val ip: String,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val org: String = "",
    val asn: String = "",
    val timezone: String = "",
    val hosting: Boolean? = null,
    val proxy: Boolean? = null,
    val latencyMs: Long = 0,
    val source: String = "",
)

object IpChecker {
    fun lookup(proxyPort: Int?): IpInfo {
        var last: Exception? = null
        try {
            return ipApi(proxyPort)
        } catch (e: Exception) {
            last = e
        }
        try {
            return ipWho(proxyPort)
        } catch (e: Exception) {
            last = e
        }
        throw IOException(last?.message ?: "нет ответа")
    }

    private fun ipApi(port: Int?): IpInfo {
        val t0 = System.currentTimeMillis()
        val o = JSONObject(
            Net.getText(
                "http://ip-api.com/json/?fields=status,message,country,countryCode,regionName,city,isp,org,as,timezone,proxy,hosting,query&lang=ru",
                port, 8000
            )
        )
        if (o.optString("status") != "success") throw IOException(o.optString("message", "ip-api: ошибка"))
        return IpInfo(
            ip = o.optString("query"),
            country = o.optString("country"),
            countryCode = o.optString("countryCode"),
            region = o.optString("regionName"),
            city = o.optString("city"),
            isp = o.optString("isp"),
            org = o.optString("org"),
            asn = o.optString("as"),
            timezone = o.optString("timezone"),
            hosting = if (o.has("hosting")) o.optBoolean("hosting") else null,
            proxy = if (o.has("proxy")) o.optBoolean("proxy") else null,
            latencyMs = System.currentTimeMillis() - t0,
            source = "ip-api.com",
        )
    }

    private fun ipWho(port: Int?): IpInfo {
        val t0 = System.currentTimeMillis()
        val o = JSONObject(Net.getText("https://ipwho.is/?lang=ru", port, 8000))
        if (!o.optBoolean("success", true)) throw IOException(o.optString("message", "ipwho.is: ошибка"))
        val c = o.optJSONObject("connection")
        val tz = o.optJSONObject("timezone")
        return IpInfo(
            ip = o.optString("ip"),
            country = o.optString("country"),
            countryCode = o.optString("country_code"),
            region = o.optString("region"),
            city = o.optString("city"),
            isp = c?.optString("isp").orEmpty(),
            org = c?.optString("org").orEmpty(),
            asn = c?.opt("asn")?.toString()?.let { "AS$it" }.orEmpty(),
            timezone = tz?.optString("id").orEmpty(),
            latencyMs = System.currentTimeMillis() - t0,
            source = "ipwho.is",
        )
    }
}

data class Site(val name: String, val url: String, val direct: Long = -2L, val vpn: Long = -2L)

data class CheckState(
    val running: Boolean = false,
    val real: IpInfo? = null,
    val realErr: String? = null,
    val vpn: IpInfo? = null,
    val vpnErr: String? = null,
    val checkedAt: Long = 0,
    val wasConnected: Boolean = false,
    val serverName: String = "",
    val sites: List<Site> = emptyList(),
    val sitesRunning: Boolean = false,
    val history: List<String> = emptyList(),
)

/** IP check: real IP (direct) vs IP through the core + service reachability. */
object IpCheck {
    val state = MutableStateFlow(CheckState())

    val SITES = listOf(
        "YouTube" to "https://www.youtube.com/",
        "Instagram" to "https://www.instagram.com/",
        "Telegram" to "https://web.telegram.org/",
        "ChatGPT" to "https://chatgpt.com/",
        "Discord" to "https://discord.com/",
        "X (Twitter)" to "https://x.com/",
        "Google" to "https://www.google.com/",
    )

    suspend fun run() = withContext(Dispatchers.IO) {
        if (state.value.running) return@withContext
        val port = if (Engine.isConnected) Engine.httpPort else null
        val server = if (port != null) Engine.status.value.serverName else ""
        state.update { it.copy(running = true) }
        val realD = async { runCatching { IpChecker.lookup(null) } }
        val vpnD = async { if (port != null) runCatching { IpChecker.lookup(port) } else null }
        val r = realD.await()
        val v = vpnD.await()
        val real = r.getOrNull()
        val vpn = v?.getOrNull()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val line = when {
            port == null -> "$time  без VPN: ${real?.ip ?: "?"} ${real?.countryCode.orEmpty()}"
            vpn == null -> "$time  ✕ VPN не отвечает"
            vpn.ip == real?.ip -> "$time  ✕ IP не изменился (${vpn.ip})"
            else -> "$time  ✓ ${real?.ip ?: "?"} → ${vpn.ip} ${vpn.countryCode}"
        }
        state.update { old ->
            old.copy(
                running = false,
                real = real,
                realErr = r.exceptionOrNull()?.message,
                vpn = vpn,
                vpnErr = if (port != null) v?.exceptionOrNull()?.message else null,
                checkedAt = System.currentTimeMillis(),
                wasConnected = port != null,
                serverName = server,
                history = (listOf(line) + old.history).take(20),
            )
        }
        Log.add("Проверка IP: $line")
    }

    suspend fun runSites() = withContext(Dispatchers.IO) {
        if (state.value.sitesRunning) return@withContext
        val port = if (Engine.isConnected) Engine.httpPort else null
        state.update { it.copy(sitesRunning = true) }
        val res = SITES.map { (name, url) ->
            async {
                Site(name, url, Net.delay(url, null, 6000), if (port != null) Net.delay(url, port, 6000) else -2L)
            }
        }.awaitAll()
        state.update { it.copy(sitesRunning = false, sites = res) }
    }
}
