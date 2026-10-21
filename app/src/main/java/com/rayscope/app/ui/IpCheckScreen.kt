package com.rayscope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        r.running -> Verdict("…", "Проверка…", "", Idle)
        r.checkedAt == 0L -> Verdict("i", "Ещё не проверялось", "Нажмите «Проверить»", Blue)
        !r.wasConnected -> Verdict(
            "○", "VPN не подключён",
            if (real != null) "Ваш IP: ${Fmt.flag(real.countryCode)} ${real.ip}" else (r.realErr ?: ""),
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
    val c = ray
    val stateVal by IpCheckModel.state.collectAsState()
    val vpnStatus by VpnState.status.collectAsState()
    val scope = rememberCoroutineScope()
    val res = stateVal
    val connected = vpnStatus.state == ConnState.CONNECTED
    val v = verdict(res)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Проверка IP", subtitle = if (connected) "VPN подключён" else "VPN не подключён") {
            GlassIconButton(onClick = { scope.launch { IpCheckModel.run() } }, enabled = !res.running) {
                Icon(Icons.Filled.Refresh, contentDescription = "Проверить", tint = c.accent, modifier = Modifier.size(19.dp))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                .glass(c, RoundedCornerShape(24.dp), tint = v.color).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(v.color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) { Text(v.icon, color = v.color, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (v.sub.isNotEmpty()) Text(v.sub, color = c.hint, fontSize = 13.sp)
                }
            }
            if (res.checkedAt > 0) {
                Text(
                    "Проверено ${Fmt.date(res.checkedAt)}" + (if (res.serverName.isNotEmpty()) " · ${res.serverName}" else ""),
                    color = c.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        GlassButton(
            when {
                res.running -> "Проверяю…"
                connected -> "Сравнить реальный IP и IP через VPN"
                else -> "Проверить мой IP"
            },
            primary = true,
            enabled = !res.running,
            onClick = { scope.launch { IpCheckModel.run() } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
        )
        if (!connected) {
            Text(
                "Подключитесь к VPN, чтобы сравнить IP до и после.",
                color = c.hint, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }

        if (res.checkedAt > 0) {
            IpCard("Реальный IP · без VPN", res.real, res.realErr)
            if (res.wasConnected) IpCard("IP через VPN", res.vpn, res.vpnErr)

            if (res.wasConnected) {
                val entry = res.entryIp
                val exitIp = res.vpn?.ip
                Section("Маршрут") {
                    KV("Вход (сервер)", entry ?: "—")
                    KV("Выход (видят сайты)", exitIp ?: "—")
                    if (entry != null && exitIp != null) {
                        Text(
                            if (entry == exitIp) "Выход совпадает с адресом сервера"
                            else "Выход отличается от входа: каскад, CDN или отдельный exit-узел",
                            color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            Section("DNS") {
                KV("Резолвер напрямую", res.dnsReal ?: "не определён")
                if (res.wasConnected) KV("Резолвер через VPN", res.dnsVpn ?: "не определён")
                KV("DNS в туннеле", Repo.settings.dnsList().joinToString(", "))
            }

            Section("IPv6") {
                val v6 = res.ipv6Direct
                KV("IPv6 в сети", v6 ?: "нет")
                if (v6 != null && res.wasConnected && !Repo.settings.ipv6) {
                    Text(
                        "⚠ В сети есть IPv6, а маршрутизация IPv6 выключена: часть трафика может идти мимо VPN. Включите её в настройках.",
                        color = Mid, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
                    )
                } else if (res.wasConnected) {
                    Text("✓ Утечки IPv6 не обнаружено", color = Good, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }

        Section("Доступность сервисов") {
            if (res.sites.isEmpty()) {
                Text(
                    "YouTube, Instagram, Telegram, ChatGPT… напрямую и через VPN",
                    color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Сервис", color = c.hint, fontSize = 11.sp, modifier = Modifier.weight(0.4f))
                    Text("Напрямую", color = c.hint, fontSize = 11.sp, modifier = Modifier.weight(0.3f))
                    Text("Через VPN", color = c.hint, fontSize = 11.sp, modifier = Modifier.weight(0.3f))
                }
                res.sites.forEach { s ->
                    RowDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(s.name, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                        SiteCell(s.direct)
                        SiteCell(s.vpn)
                    }
                }
            }
            GlassButton(
                if (res.sitesRunning) "Проверяю…" else "Проверить сервисы",
                enabled = !res.sitesRunning,
                onClick = { scope.launch { IpCheckModel.runSites() } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }

        if (res.history.isNotEmpty()) {
            Section("История проверок") {
                res.history.forEach { Text(it, color = c.text, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        Spacer(Modifier.height(BottomInset))
    }
}

@Composable
private fun RowScope.SiteCell(v: Long) {
    val txt = when {
        v == -2L -> "—"
        v < 0 -> "✕ нет"
        else -> "✓ $v ms"
    }
    val col = when {
        v == -2L -> Idle
        v < 0 -> Bad
        else -> Good
    }
    Text(txt, color = col, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.3f))
}

@Composable
private fun IpCard(title: String, info: IpInfo?, err: String?) {
    val c = ray
    Section(title) {
        if (info == null) {
            Text(
                err ?: "Нет данных", color = if (err != null) Bad else c.hint, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        } else {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagBox(Fmt.flag(info.countryCode), 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(info.ip, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(info.country, info.city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "—" },
                        color = c.hint, fontSize = 13.sp
                    )
                }
            }
            RowDivider()
            KV("Провайдер", info.isp)
            KV("Организация", info.org)
            KV("ASN", info.asn)
            KV("Регион", info.region)
            KV("Часовой пояс", info.timezone)
            val hosting = info.hosting
            if (hosting != null) KV("Тип IP", if (hosting) "Дата-центр" else "Домашний / мобильный")
            if (info.proxy == true) KV("Метка", "Известен как прокси / VPN", Mid)
            KV("Ответ API", "${info.latencyMs} ms · ${info.source}")
        }
    }
}
