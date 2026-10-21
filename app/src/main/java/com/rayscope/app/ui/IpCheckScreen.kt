@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayscope.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Repo
import com.rayscope.app.net.IpCheckModel
import com.rayscope.app.net.IpCheckResult
import com.rayscope.app.net.IpInfo
import com.rayscope.app.util.Fmt
import kotlinx.coroutines.launch

private data class Verdict(val icon: String, val title: String, val sub: String, val color: Color)

private fun verdict(r: IpCheckResult): Verdict {
    val real = r.real
    val vpn = r.vpn
    return when {
        r.running -> Verdict("⏳", "Проверка…", "", Idle)
        r.checkedAt == 0L -> Verdict("ℹ", "Ещё не проверялось", "Нажмите «Проверить»", Idle)
        !r.wasConnected -> Verdict(
            "○", "VPN не подключён",
            if (real != null) "Ваш IP: ${Fmt.flag(real.countryCode)} ${real.ip} ${real.country}" else (r.realErr ?: ""),
            Idle
        )
        vpn == null -> Verdict("✕", "VPN-сервер не отвечает", r.vpnErr ?: "Нет ответа через туннель", Bad)
        real != null && real.ip == vpn.ip -> Verdict("✕", "IP не изменился", "Сайты видят ваш реальный адрес ${vpn.ip}", Bad)
        else -> Verdict(
            "✓", "VPN работает, IP изменён",
            (if (real != null) "${Fmt.flag(real.countryCode)} ${real.ip}" else "?") + "  →  " + "${Fmt.flag(vpn.countryCode)} ${vpn.ip}",
            Good
        )
    }
}

@Composable
fun IpCheckScreen() {
    val stateVal by IpCheckModel.state.collectAsState()
    val vpnStatus by VpnState.status.collectAsState()
    val scope = rememberCoroutineScope()
    val res = stateVal
    val connected = vpnStatus.state == ConnState.CONNECTED
    val v = verdict(res)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("Проверка IP") },
            actions = {
                IconButton(enabled = !res.running, onClick = { scope.launch { IpCheckModel.run() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Проверить")
                }
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = v.color.copy(alpha = 0.14f)),
            border = BorderStroke(1.dp, v.color)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(v.icon + "  " + v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = v.color)
                if (v.sub.isNotEmpty()) Text(v.sub, style = MaterialTheme.typography.bodyMedium)
                if (res.checkedAt > 0) {
                    Text(
                        "Проверено: ${Fmt.date(res.checkedAt)}" + (if (res.serverName.isNotEmpty()) " · ${res.serverName}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            enabled = !res.running,
            onClick = { scope.launch { IpCheckModel.run() } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                when {
                    res.running -> "Проверяю…"
                    connected -> "Проверить: реальный IP vs IP через VPN"
                    else -> "Проверить мой IP"
                }
            )
        }
        if (!connected) {
            Text(
                "Подключитесь к VPN, чтобы сравнить IP до и после и убедиться, что сервер работает.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (res.checkedAt > 0) {
            IpCard("🏠 Реальный IP (без VPN)", res.real, res.realErr)
            if (res.wasConnected) IpCard("🛡 IP через VPN", res.vpn, res.vpnErr)

            if (res.wasConnected) {
                val entry = res.entryIp
                val exitIp = res.vpn?.ip
                SectionCard("🧭 Маршрут") {
                    KV("Вход (адрес сервера)", entry ?: "—")
                    KV("Выход (видят сайты)", exitIp ?: "—")
                    if (entry != null && exitIp != null) {
                        Text(
                            if (entry == exitIp) "Выход совпадает с адресом сервера"
                            else "Выход отличается от входа: каскад / CDN / отдельный exit-узел",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard("🔎 DNS") {
                KV("Резолвер (напрямую)", res.dnsReal ?: "не определён")
                if (res.wasConnected) KV("Резолвер (через VPN)", res.dnsVpn ?: "не определён")
                KV("DNS в туннеле", Repo.settings.dnsList().joinToString(", "))
            }

            SectionCard("IPv6") {
                val v6 = res.ipv6Direct
                KV("IPv6 в сети", v6 ?: "нет")
                if (v6 != null && res.wasConnected && !Repo.settings.ipv6) {
                    Text(
                        "⚠ В вашей сети есть IPv6, а маршрутизация IPv6 в VPN выключена. Часть трафика приложений может идти мимо туннеля. Включите «Маршрутизировать IPv6» в настройках.",
                        color = Mid,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (res.wasConnected) {
                    Text("✓ Утечки IPv6 не обнаружено", color = Good, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard("🌍 Доступность сервисов") {
            Text(
                "Проверка популярных сайтов напрямую и через VPN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(enabled = !res.sitesRunning, onClick = { scope.launch { IpCheckModel.runSites() } }) {
                Text(if (res.sitesRunning) "Проверяю…" else "Проверить сервисы")
            }
            if (res.sites.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Сервис", Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Напрямую", Modifier.weight(0.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Через VPN", Modifier.weight(0.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                res.sites.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(s.name, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                        SiteCell(s.direct)
                        SiteCell(s.vpn)
                    }
                }
            }
        }

        if (res.history.isNotEmpty()) {
            SectionCard("🕘 История проверок") {
                res.history.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.SiteCell(v: Long) {
    val txt = when {
        v == -2L -> "—"
        v < 0 -> "✕ недоступен"
        else -> "✓ $v ms"
    }
    val c = when {
        v == -2L -> Idle
        v < 0 -> Bad
        else -> Good
    }
    Text(txt, Modifier.weight(0.3f), color = c, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun IpCard(title: String, info: IpInfo?, err: String?) {
    SectionCard(title) {
        if (info == null) {
            Text(
                err ?: "Нет данных",
                color = if (err != null) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("${Fmt.flag(info.countryCode)}  ${info.ip}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            KV("Страна", listOf(info.country, info.countryCode).filter { it.isNotEmpty() }.distinct().joinToString(" / "))
            KV("Регион / город", listOf(info.region, info.city).filter { it.isNotEmpty() }.joinToString(", "))
            KV("Провайдер (ISP)", info.isp)
            KV("Организация", info.org)
            KV("ASN", info.asn)
            KV("Часовой пояс", info.timezone)
            val hosting = info.hosting
            if (hosting != null) KV("Тип IP", if (hosting) "Хостинг / дата-центр" else "Домашний / мобильный")
            if (info.proxy == true) KV("Метка", "Известен как прокси / VPN", Mid)
            KV("Ответ API", "${info.latencyMs} ms · ${info.source}")
        }
    }
}
