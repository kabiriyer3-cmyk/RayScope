package com.rayscope.app.net

import com.rayscope.app.core.XrayConfig
import com.rayscope.app.data.Repo
import com.rayscope.app.model.Server
import com.rayscope.sub.ExitIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

data class SiteResult(val name: String, val url: String, val direct: Long, val vpn: Long)

object Tester {
    val SITES = listOf(
        "YouTube" to "https://www.youtube.com/generate_204",
        "Instagram" to "https://www.instagram.com/",
        "Telegram" to "https://telegram.org/",
        "ChatGPT" to "https://chatgpt.com/",
        "Discord" to "https://discord.com/",
        "X / Twitter" to "https://x.com/",
        "Google" to "https://www.google.com/generate_204",
        "Netflix" to "https://www.netflix.com/",
    )

    /** Preset URLs for the latency test. */
    val TEST_URLS = listOf(
        "gstatic" to "https://www.gstatic.com/generate_204",
        "Cloudflare" to "http://cp.cloudflare.com/",
        "Google" to "https://www.google.com/generate_204",
        "Apple" to "http://captive.apple.com/hotspot-detect.html",
        "Microsoft" to "http://www.msftconnecttest.com/connecttest.txt",
    )

    /** TCP handshake time to host:port (DNS excluded), -1 on failure. */
    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 3000): Long = withContext(Dispatchers.IO) {
        try {
            val addr = InetSocketAddress(host, port)
            Socket().use { s ->
                val t0 = System.nanoTime()
                s.connect(addr, timeoutMs.coerceIn(300, 30_000))
                (System.nanoTime() - t0) / 1_000_000
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /** "time=12.3 ms" in the system ping output; the summary line ("time 0ms") has no '=' and is skipped. */
    private val PING_TIME = Regex("time[=<]\\s*([\\d.]+)\\s*ms", RegexOption.IGNORE_CASE)

    /**
     * ICMP echo to the server through the system ping, -1 if there is no reply. The name is resolved first, so
     * DNS time is not part of the result. Our own app is outside the VPN, the echo goes straight to the server.
     */
    suspend fun icmpPing(host: String, timeoutMs: Int = 3000): Long = withContext(Dispatchers.IO) {
        val t = timeoutMs.coerceIn(300, 10_000)
        val ip = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return@withContext -1L
        }
        val text = ip.hostAddress.orEmpty()
        val bin = if (text.contains(':')) "/system/bin/ping6" else "/system/bin/ping"
        val sec = ((t + 999) / 1000).toString()
        val out = try {
            val pr = ProcessBuilder(bin, "-c", "1", "-w", sec, "-W", sec, text).redirectErrorStream(true).start()
            pr.inputStream.bufferedReader().use { it.readText() }.also { pr.waitFor() }
        } catch (e: Exception) {
            null
        }
        if (out != null) {
            PING_TIME.find(out)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                return@withContext Math.round(it).coerceAtLeast(1L)
            }
            if (out.contains("received") || out.contains("packet loss")) return@withContext -1L
        }
        // no ping tool, or it may not send ICMP here: Java's own echo (ICMP datagram socket, else TCP port 7)
        try {
            val t0 = System.nanoTime()
            if (ip.isReachable(t)) (System.nanoTime() - t0) / 1_000_000 else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Real HTTP delay through the server (full proxy handshake), using a temporary Xray instance.
     * Honors attempts / aggregate / timeout from settings.
     */
    suspend fun realDelay(s: Server): Pair<Long, String> = withContext(Dispatchers.IO) {
        val st = Repo.settings
        val cfg = try {
            XrayConfig.build(s, st, tun = false)
        } catch (e: Throwable) {
            return@withContext -1L to (e.message ?: e.javaClass.simpleName).take(300)
        }
        val attempts = st.pingAttempts.coerceIn(1, 5)
        val ok = mutableListOf<Long>()
        var err = ""
        for (i in 0 until attempts) {
            if (!isActive) break
            try {
                val d = Libv2ray.measureOutboundDelay(cfg, st.testUrl)
                if (d >= 0) ok += d else err = "timeout"
            } catch (e: Throwable) {
                err = (e.message ?: e.javaClass.simpleName).take(300)
            }
        }
        if (ok.isEmpty()) return@withContext -1L to err.ifEmpty { "timeout" }
        val v = if (st.pingAggregate == 1) ok.average().toLong() else ok.min()
        val limit = st.pingTimeout.coerceIn(500, 30_000)
        if (v > limit) -1L to "медленнее порога $limit ms ($v ms)" else v to ""
    }

    private val quiet = object : CoreCallbackHandler {
        override fun startup(): Long = 0

        override fun shutdown(): Long = 0

        override fun onEmitStatus(p0: Long, p1: String?): Long = 0
    }

    private val exitClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Where [s] really lets you out: a throwaway core with one local HTTP inbound and a request through it to
     * Cloudflare's trace page (ip-api.com when that fails). The app itself is outside the VPN, so this goes
     * straight to the server even while connected. Null when the server didn't carry the request.
     */
    suspend fun exit(s: Server): ExitIp.Exit? = withContext(Dispatchers.IO) {
        val port = try {
            ServerSocket(0).use { it.localPort }
        } catch (e: Exception) {
            return@withContext null
        }
        val cfg = try {
            exitConfig(s, port)
        } catch (e: Throwable) {
            return@withContext null
        }
        val core = try {
            Libv2ray.newCoreController(quiet)
        } catch (e: Throwable) {
            return@withContext null
        }
        try {
            core.startLoop(cfg, 0)
            val c = exitClient.newBuilder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))).build()
            val t = runCatching { ExitIp.parseTrace(IpChecker.get(c, ExitIp.TRACE)) }.getOrNull()
            if (t != null && t.cc.isNotEmpty()) t
            else runCatching { ExitIp.parseIpApiLine(IpChecker.get(c, ExitIp.FALLBACK)) }.getOrNull() ?: t
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { core.stopLoop() }
        }
    }

    /**
     * The usual config of [s] with one local HTTP inbound on [port] instead of the proxy ports and no user rules:
     * a "direct" rule must not answer instead of the server.
     */
    private fun exitConfig(s: Server, port: Int): String {
        val root = JSONObject(XrayConfig.build(s, Repo.settings, tun = false))
        root.put("log", JSONObject().put("loglevel", "none"))
        val inbound = JSONObject().put("tag", "exit-in").put("listen", "127.0.0.1").put("port", port).put("protocol", "http")
            .put("settings", JSONObject())
        root.put("inbounds", JSONArray().put(inbound))
        val rule = JSONObject().put("type", "field").put("inboundTag", JSONArray().put("exit-in")).put("outboundTag", "proxy")
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray().put(rule)))
        return root.toString()
    }

    suspend fun resolve(host: String): Pair<List<String>, Long> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val ips = InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
            ips to (System.currentTimeMillis() - t0)
        } catch (e: Exception) {
            emptyList<String>() to -1L
        }
    }

    /** Download speed test in Mbit/s (max ~10 s). */
    suspend fun speedTest(viaVpn: Boolean, onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val c = IpChecker.client(viaVpn).newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=50000000").build()
        var total = 0L
        val t0 = System.nanoTime()
        c.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val src = r.body?.byteStream() ?: throw IOException("пустой ответ")
            val buf = ByteArray(64 * 1024)
            var last = t0
            while (isActive) {
                val n = src.read(buf)
                if (n < 0) break
                total += n
                val now = System.nanoTime()
                if (now - last > 300_000_000L) {
                    onProgress(mbps(total, now - t0))
                    last = now
                }
                if (now - t0 > 10_000_000_000L) break
            }
        }
        mbps(total, System.nanoTime() - t0)
    }

    private fun mbps(bytes: Long, ns: Long): Double = if (ns <= 0) 0.0 else bytes * 8.0 / (ns / 1e9) / 1_000_000.0

    /** Time to first response from a site, -1 if unreachable. */
    suspend fun site(url: String, viaVpn: Boolean): Long = withContext(Dispatchers.IO) {
        try {
            val c = IpChecker.client(viaVpn).newBuilder().callTimeout(8, TimeUnit.SECONDS).followRedirects(false).build()
            val t0 = System.currentTimeMillis()
            c.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { }
            System.currentTimeMillis() - t0
        } catch (e: Exception) {
            -1L
        }
    }
}
