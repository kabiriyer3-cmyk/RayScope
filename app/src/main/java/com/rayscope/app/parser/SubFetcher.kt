package com.rayscope.app.parser

import android.net.Uri
import com.rayscope.app.core.XrayConfig
import com.rayscope.app.model.Server
import com.rayscope.app.model.Subscription
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Downloads a subscription and reads Happ-style headers (subscription-userinfo, profile-title, announce...). */
object SubFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class Result(val sub: Subscription, val servers: List<Server>)

    fun fetch(sub: Subscription, userAgent: String, viaProxy: Boolean): Result {
        val c = if (viaProxy) {
            client.newBuilder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))).build()
        } else client
        val req = Request.Builder().url(sub.url).header("User-Agent", userAgent).header("Accept", "*/*").build()
        c.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            var up = 0L
            var down = 0L
            var total = 0L
            var expire = 0L
            resp.header("subscription-userinfo")?.split(';')?.forEach { part ->
                val k = part.substringBefore('=').trim().lowercase()
                val v = part.substringAfter('=', "").trim().toDoubleOrNull()?.toLong() ?: 0L
                when (k) {
                    "upload" -> { up = v }
                    "download" -> { down = v }
                    "total" -> { total = v }
                    "expire" -> { expire = v }
                    else -> {}
                }
            }
            val title = resp.header("profile-title")?.let { decodeHeader(it) }?.trim()
            val interval = resp.header("profile-update-interval")?.trim()?.toIntOrNull() ?: sub.updateIntervalH
            val support = resp.header("support-url") ?: sub.supportUrl
            val announce = resp.header("announce")?.let { decodeHeader(it) } ?: sub.announce
            val servers = LinkParser.parseMany(body).map { it.copy(subId = sub.id) }
            if (servers.isEmpty()) throw IOException("В подписке нет поддерживаемых серверов")
            val host = try { Uri.parse(sub.url).host } catch (e: Exception) { null }
            val name = when {
                !title.isNullOrBlank() -> title
                sub.name.isNotBlank() -> sub.name
                else -> host ?: "Подписка"
            }
            val updated = sub.copy(
                name = name,
                upload = up,
                download = down,
                total = total,
                expire = expire,
                lastUpdated = System.currentTimeMillis(),
                updateIntervalH = interval,
                supportUrl = support,
                announce = announce,
                serverCount = servers.size,
            )
            return Result(updated, servers)
        }
    }

    private fun decodeHeader(v: String): String =
        if (v.startsWith("base64:")) LinkParser.b64(v.removePrefix("base64:")) ?: v else v
}
