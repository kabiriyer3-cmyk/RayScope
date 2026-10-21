package com.rayscope.app.net

import com.rayscope.app.core.XrayConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    val source: String = "",
    val latencyMs: Long = 0,
)

/** Determines public IP + geo/ISP info, either directly or through the running Xray core. */
object IpChecker {
    private val base = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val viaCore: OkHttpClient by lazy {
        base.newBuilder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))).build()
    }

    fun client(viaVpn: Boolean): OkHttpClient = if (viaVpn) viaCore else base

    fun get(c: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "RayScope/1.0").build()
        c.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    private val providers: List<(OkHttpClient, String?) -> IpInfo> = listOf(
        { c, ip -> ipApi(c, ip) },
        { c, ip -> ipWho(c, ip) },
        { c, ip -> ipInfo(c, ip) },
        { c, ip -> ipify(c, ip) },
    )

    /** Public IP as seen by the internet. viaVpn = through the Xray tunnel. */
    suspend fun myIp(viaVpn: Boolean): IpInfo = withContext(Dispatchers.IO) {
        val c = client(viaVpn)
        val errors = mutableListOf<String>()
        for (p in providers) {
            try {
                val t0 = System.currentTimeMillis()
                val info = p(c, null)
                return@withContext info.copy(latencyMs = System.currentTimeMillis() - t0)
            } catch (e: Exception) {
                errors += (e.message ?: e.javaClass.simpleName)
            }
        }
        throw IOException(errors.joinToString("; "))
    }

    /** Geo info for an arbitrary IP (server entry point). */
    suspend fun lookup(ip: String): IpInfo = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (p in providers.take(3)) {
            try {
                return@withContext p(base, ip)
            } catch (e: Exception) {
                errors += (e.message ?: e.javaClass.simpleName)
            }
        }
        throw IOException(errors.joinToString("; "))
    }

    /** Which DNS resolver resolved our query (edns.ip-api.com trick). */
    suspend fun dnsResolver(viaVpn: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val rnd = UUID.randomUUID().toString().replace("-", "").take(16)
            val o = JSONObject(get(client(viaVpn), "http://$rnd.edns.ip-api.com/json"))
            val d = o.optJSONObject("dns") ?: return@withContext null
            "${d.optString("ip")} (${d.optString("geo")})"
        } catch (e: Exception) {
            null
        }
    }

    /** Direct IPv6 address, if the network has IPv6 at all. */
    suspend fun ipv6(viaVpn: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            JSONObject(get(client(viaVpn), "https://api6.ipify.org?format=json")).optString("ip").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun ipApi(c: OkHttpClient, ip: String?): IpInfo {
        val url = "http://ip-api.com/json/" + (ip ?: "") +
            "?fields=status,message,country,countryCode,regionName,city,isp,org,as,timezone,query,hosting,proxy"
        val o = JSONObject(get(c, url))
        if (o.optString("status") != "success") throw IOException(o.optString("message", "ip-api error"))
        return IpInfo(
            ip = o.optString("query"),
            country = o.optString("country"),
            countryCode = o.optString("countryCode"),
            region = o.optString("regionName"),
            city = o.optString("city"),
            isp = o.optString("isp"),
            org = o.optString("org"),
            asn = o.optString("as").substringBefore(' '),
            timezone = o.optString("timezone"),
            hosting = if (o.has("hosting")) o.optBoolean("hosting") else null,
            proxy = if (o.has("proxy")) o.optBoolean("proxy") else null,
            source = "ip-api.com",
        )
    }

    private fun ipWho(c: OkHttpClient, ip: String?): IpInfo {
        val o = JSONObject(get(c, "https://ipwho.is/" + (ip ?: "")))
        if (!o.optBoolean("success", true)) throw IOException(o.optString("message", "ipwho error"))
        val conn = o.optJSONObject("connection")
        val tz = o.optJSONObject("timezone")
        val asn = conn?.optLong("asn", 0L) ?: 0L
        return IpInfo(
            ip = o.optString("ip"),
            country = o.optString("country"),
            countryCode = o.optString("country_code"),
            region = o.optString("region"),
            city = o.optString("city"),
            isp = conn?.optString("isp").orEmpty(),
            org = conn?.optString("org").orEmpty(),
            asn = if (asn > 0) "AS$asn" else "",
            timezone = tz?.optString("id").orEmpty(),
            source = "ipwho.is",
        )
    }

    private fun ipInfo(c: OkHttpClient, ip: String?): IpInfo {
        val url = if (ip == null) "https://ipinfo.io/json" else "https://ipinfo.io/$ip/json"
        val o = JSONObject(get(c, url))
        val org = o.optString("org")
        return IpInfo(
            ip = o.optString("ip"),
            country = o.optString("country"),
            countryCode = o.optString("country"),
            region = o.optString("region"),
            city = o.optString("city"),
            isp = org.substringAfter(' ', org),
            org = org,
            asn = if (org.startsWith("AS")) org.substringBefore(' ') else "",
            timezone = o.optString("timezone"),
            source = "ipinfo.io",
        )
    }

    private fun ipify(c: OkHttpClient, ip: String?): IpInfo {
        if (ip != null) return IpInfo(ip = ip, source = "-")
        val o = JSONObject(get(c, "https://api.ipify.org?format=json"))
        return IpInfo(ip = o.optString("ip"), source = "ipify.org")
    }
}
