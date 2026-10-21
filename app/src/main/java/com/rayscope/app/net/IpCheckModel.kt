package com.rayscope.app.net

import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Repo
import com.rayscope.app.util.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IpCheckResult(
    val running: Boolean = false,
    val real: IpInfo? = null,
    val realErr: String? = null,
    val vpn: IpInfo? = null,
    val vpnErr: String? = null,
    val dnsReal: String? = null,
    val dnsVpn: String? = null,
    val ipv6Direct: String? = null,
    val entryIp: String? = null,
    val checkedAt: Long = 0,
    val wasConnected: Boolean = false,
    val serverName: String = "",
    val sites: List<SiteResult> = emptyList(),
    val sitesRunning: Boolean = false,
    val history: List<String> = emptyList(),
)

/** IP check: real IP (direct) vs IP through the VPN core + DNS / IPv6 / route info. */
object IpCheckModel {
    val state = MutableStateFlow(IpCheckResult())

    suspend fun run() {
        if (state.value.running) return
        val connected = VpnState.isConnected
        val server = if (connected) Repo.server(VpnState.status.value.serverId ?: "") else null
        state.update { it.copy(running = true) }
        try {
            coroutineScope {
                val realD = async { runCatching { IpChecker.myIp(false) } }
                val vpnD = async { if (connected) runCatching { IpChecker.myIp(true) } else null }
                val dnsR = async { IpChecker.dnsResolver(false) }
                val dnsV = async { if (connected) IpChecker.dnsResolver(true) else null }
                val v6 = async { IpChecker.ipv6(false) }
                val entry = async { if (server != null) Tester.resolve(server.address).first.firstOrNull() else null }

                val r = realD.await()
                val v = vpnD.await()
                val dnsRealV = dnsR.await()
                val dnsVpnV = dnsV.await()
                val v6v = v6.await()
                val entryV = entry.await()
                val real = r.getOrNull()
                val vpn = v?.getOrNull()
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val line = when {
                    !connected -> "$time  без VPN: ${real?.ip ?: "?"} ${real?.countryCode.orEmpty()}"
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
                        vpnErr = if (connected) v?.exceptionOrNull()?.message else null,
                        dnsReal = dnsRealV,
                        dnsVpn = dnsVpnV,
                        ipv6Direct = v6v,
                        entryIp = entryV,
                        checkedAt = System.currentTimeMillis(),
                        wasConnected = connected,
                        serverName = server?.cleanName().orEmpty(),
                        history = (listOf(line) + old.history).take(15),
                    )
                }
                AppLog.add("Проверка IP: $line")
            }
        } catch (e: Exception) {
            AppLog.add("Проверка IP: ошибка ${e.message}")
            state.update { it.copy(running = false) }
        }
    }

    suspend fun runSites() {
        if (state.value.sitesRunning) return
        val connected = VpnState.isConnected
        state.update { it.copy(sitesRunning = true) }
        try {
            val results = coroutineScope {
                Tester.SITES.map { (name, url) ->
                    async {
                        val direct = Tester.site(url, false)
                        val viaVpn = if (connected) Tester.site(url, true) else -2L
                        SiteResult(name, url, direct, viaVpn)
                    }
                }.awaitAll()
            }
            state.update { it.copy(sitesRunning = false, sites = results) }
        } catch (e: Exception) {
            state.update { it.copy(sitesRunning = false) }
        }
    }
}
