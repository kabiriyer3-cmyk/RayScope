package com.rayscope.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.rayscope.app.util.toast
import kotlinx.coroutines.launch

/** kind: 0 info, 1 good, 2 bad, 3 running, 4 idle */
private data class Verdict(val kind: Int, val title: String, val sub: String)

private fun kindColor(k: Int): Color = when (k) {
    1 -> Good
    2 -> Bad
    3, 4 -> Idle
    else -> Blue
}

private fun verdict(r: IpCheckResult): Verdict {
    val real = r.real
    val vpn = r.vpn
    return when {
        r.running -> Verdict(3, "Проверяю…", "Сравниваю адреса напрямую и через туннель")
        r.checkedAt == 0L -> Verdict(0, "Ещё не проверялось", "Узнайте, меняет ли VPN ваш IP")
        !r.wasConnected -> Verdict(
            4, "VPN не подключён",
            if (real != null) "Ваш IP: ${Fmt.flag(real.countryCode)} ${real.ip}" else (r.realErr ?: "")
        )
        vpn == null -> Verdict(2, "Сервер не отвечает", r.vpnErr ?: "Нет ответа через туннель")
        real != null && real.ip == vpn.ip -> Verdict(2, "IP не изменился", "Сайты видят ваш реальный адрес ${vpn.ip}")
        else -> Verdict(
            1, "VPN работает",
            (if (real != null) "${Fmt.flag(real.countryCode)} ${real.ip}" else "?") + "  →  " + "${Fmt.flag(vpn.countryCode)} ${vpn.ip}"
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
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 120 } }
    val spin = if (res.running) spinAngle(800) else null

    Page(
        title = "Проверка IP",
        scrolled = scrolled,
        actions = {
            ActionIcon(
                RayIcons.Refresh, "Проверить", tint = c.accent, enabled = !res.running,
                iconModifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f }
            ) { scope.launch { IpCheckModel.run() } }
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            LargeTitle("Проверка IP", if (connected) "VPN подключён" else "VPN не подключён")

            Column(
                Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 22.dp).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = v.kind,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.5f) + fadeIn(tween(150))) togetherWith
                            (scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(120)))
                    },
                    label = "verdictIcon"
                ) { k ->
                    val col = kindColor(k)
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(col.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (k) {
                            1 -> {
                                val p = remember { Animatable(0f) }
                                LaunchedEffect(Unit) { p.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
                                DrawnIcon(listOf(SHIELD_PATH, CHECK_SMALL_PATH), p.value, col, Modifier.size(34.dp), strokeWidth = 2f)
                            }
                            2 -> Icon(RayIcons.Alert, contentDescription = null, tint = col, modifier = Modifier.size(30.dp))
                            3 -> {
                                val a = spinAngle(900)
                                Icon(
                                    RayIcons.Refresh, contentDescription = null, tint = col,
                                    modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = a.value }
                                )
                            }
                            4 -> Icon(RayIcons.Shield, contentDescription = null, tint = col, modifier = Modifier.size(30.dp))
                            else -> Icon(RayIcons.Globe, contentDescription = null, tint = col, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TickerText(v.title, c.text, 19.sp, FontWeight.Bold)
                if (v.sub.isNotEmpty()) {
                    Text(v.sub, color = c.hint, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (res.checkedAt > 0) {
                    Text(
                        "Проверено ${Fmt.date(res.checkedAt)}" + (if (res.serverName.isNotEmpty()) " · ${res.serverName}" else ""),
                        color = c.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                PillButton(
                    when {
                        res.running -> "Проверяю…"
                        connected -> "Сравнить IP до и после VPN"
                        else -> "Проверить мой IP"
                    },
                    primary = true,
                    enabled = !res.running,
                    icon = RayIcons.Search,
                    onClick = { scope.launch { IpCheckModel.run() } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (res.checkedAt > 0) {
                IpCard("Реальный IP · без VPN", res.real, res.realErr, 1)
                if (res.wasConnected) IpCard("IP через VPN", res.vpn, res.vpnErr, 2)

                if (res.wasConnected) {
                    val entry = res.entryIp
                    val exitIp = res.vpn?.ip
                    val footer = if (entry != null && exitIp != null) {
                        if (entry == exitIp) "Выход совпадает с адресом сервера"
                        else "Выход отличается от входа: каскад, CDN или отдельный exit-узел"
                    } else null
                    Section("Маршрут", modifier = Modifier.appear(3), footer = footer, padded = false) {
                        SettingRow(RayIcons.Pin, Indigo, "Вход (сервер)", value = entry ?: "—")
                        SettingRow(RayIcons.Globe, Blue, "Выход (видят сайты)", value = exitIp ?: "—", divider = false)
                    }
                }

                Section("DNS", modifier = Modifier.appear(4), padded = false) {
                    SettingRow(RayIcons.Wifi, Teal, "Напрямую", value = res.dnsReal ?: "не определён")
                    if (res.wasConnected) SettingRow(RayIcons.Shield, Good, "Через VPN", value = res.dnsVpn ?: "не определён")
                    SettingRow(RayIcons.Settings, Gray, "DNS в туннеле", value = Repo.settings.dnsList().joinToString(", "), divider = false)
                }

                val v6 = res.ipv6Direct
                val v6Note = when {
                    v6 != null && res.wasConnected && !Repo.settings.ipv6 ->
                        "В сети есть IPv6, а маршрутизация IPv6 выключена: часть трафика может идти мимо VPN. Включите её в настройках."
                    res.wasConnected -> "Утечки IPv6 не обнаружено"
                    else -> null
                }
                val leak = v6 != null && res.wasConnected && !Repo.settings.ipv6
                Section("IPv6", modifier = Modifier.appear(5), footer = v6Note, padded = false) {
                    SettingRow(
                        if (leak) RayIcons.Alert else RayIcons.Lock,
                        if (leak) Orange else Good,
                        "IPv6 в сети", value = v6 ?: "нет", divider = false
                    )
                }
            }

            Section("Доступность сервисов", modifier = Modifier.appear(6)) {
                if (res.sites.isEmpty()) {
                    Text(
                        "YouTube, Instagram, Telegram, ChatGPT… напрямую и через VPN",
                        color = c.hint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("Сервис", color = c.hint, fontSize = 12.sp, modifier = Modifier.weight(0.4f))
                        Text("Напрямую", color = c.hint, fontSize = 12.sp, modifier = Modifier.weight(0.3f))
                        Text("Через VPN", color = c.hint, fontSize = 12.sp, modifier = Modifier.weight(0.3f))
                    }
                    res.sites.forEach { s ->
                        RowDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(0.4f))
                            SiteCell(s.direct)
                            SiteCell(s.vpn)
                        }
                    }
                }
                PillButton(
                    if (res.sitesRunning) "Проверяю…" else "Проверить сервисы",
                    enabled = !res.sitesRunning,
                    icon = RayIcons.Activity,
                    onClick = { scope.launch { IpCheckModel.runSites() } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            if (res.history.isNotEmpty()) {
                Section("История проверок", modifier = Modifier.appear(7)) {
                    res.history.forEach { Text(it, color = c.text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
            TabBottomSpacer()
        }
    }
}

@Composable
private fun RowScope.SiteCell(v: Long) {
    val txt = when {
        v == -2L -> "—"
        v < 0 -> "нет"
        else -> "$v ms"
    }
    val col = when {
        v == -2L -> Idle
        v < 0 -> Bad
        else -> Good
    }
    Box(Modifier.weight(0.3f)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (v != -2L) {
                Icon(if (v < 0) RayIcons.Close else RayIcons.Check, contentDescription = null, tint = col, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IpCard(title: String, info: IpInfo?, err: String?, index: Int) {
    val c = ray
    val clipboard = LocalClipboardManager.current
    Section(title, modifier = Modifier.appear(index)) {
        if (info == null) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(RayIcons.Alert, contentDescription = null, tint = if (err != null) Bad else c.hint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(err ?: "Нет данных", color = if (err != null) Bad else c.hint, fontSize = 14.sp)
            }
        } else {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagBox(Fmt.flag(info.countryCode), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.ip, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(info.country, info.city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "—" },
                        color = c.hint, fontSize = 13.sp
                    )
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.field).clickable {
                        clipboard.setText(AnnotatedString(info.ip))
                        toast("IP скопирован")
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(RayIcons.Copy, contentDescription = "Копировать", tint = c.accent, modifier = Modifier.size(17.dp))
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
