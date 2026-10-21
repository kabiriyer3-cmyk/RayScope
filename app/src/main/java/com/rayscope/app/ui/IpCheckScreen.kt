package com.rayscope.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    3 -> Mid
    4 -> Idle
    else -> Cyan
}

private fun verdict(r: IpCheckResult): Verdict {
    val real = r.real
    val vpn = r.vpn
    return when {
        r.running -> Verdict(3, "Проверяю…", "Сравниваю адреса напрямую и через туннель")
        r.checkedAt == 0L -> Verdict(0, "Ещё не проверялось", "Узнайте, меняет ли VPN ваш IP")
        !r.wasConnected -> Verdict(4, "VPN не подключён", if (real != null) "Сайты видят ваш настоящий адрес" else (r.realErr ?: ""))
        vpn == null -> Verdict(2, "Сервер не отвечает", r.vpnErr ?: "Нет ответа через туннель")
        real != null && real.ip == vpn.ip -> Verdict(2, "IP не изменился", "Сайты видят ваш реальный адрес")
        else -> Verdict(1, "VPN работает", "Адрес скрыт, сайты видят IP сервера")
    }
}

@Composable
fun IpCheckScreen() {
    val c = ray
    val dev = look.dev
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
        title = "Проверка",
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
            ScreenTitle("Проверка", if (connected) "IP, утечки и сервисы · VPN включён" else "IP, утечки и сервисы · VPN выключен")

            // ---- verdict + route
            Column(
                Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 24.dp).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            Modifier.size(52.dp).clip(CircleShape).background(col.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (k) {
                                1 -> {
                                    val p = remember { Animatable(0f) }
                                    LaunchedEffect(Unit) { p.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
                                    DrawnIcon(listOf(SHIELD_PATH, CHECK_SMALL_PATH), p.value, col, Modifier.size(28.dp), strokeWidth = 2f)
                                }
                                2 -> Icon(RayIcons.Alert, contentDescription = null, tint = col, modifier = Modifier.size(26.dp))
                                3 -> {
                                    val a = spinAngle(900)
                                    Icon(
                                        RayIcons.Refresh, contentDescription = null, tint = col,
                                        modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = a.value }
                                    )
                                }
                                4 -> Icon(RayIcons.Shield, contentDescription = null, tint = col, modifier = Modifier.size(26.dp))
                                else -> Icon(RayIcons.Radar, contentDescription = null, tint = col, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        TickerText(v.title, c.text, 19.sp, FontWeight.Bold)
                        if (v.sub.isNotEmpty()) {
                            Text(v.sub, color = c.hint, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                RouteViz(res, v.kind)
                if (res.checkedAt > 0) {
                    Text(
                        "Проверено ${Fmt.date(res.checkedAt)}" + (if (res.serverName.isNotEmpty()) " · ${res.serverName}" else ""),
                        color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp)
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
                IpCard("Реальный IP · без VPN", res.real, res.realErr, 1, dev)
                if (res.wasConnected) IpCard("IP через VPN", res.vpn, res.vpnErr, 2, dev)

                if (dev && res.wasConnected) {
                    val entry = res.entryIp
                    val exitIp = res.vpn?.ip
                    val footer = if (entry != null && exitIp != null) {
                        if (entry == exitIp) "Выход совпадает с адресом сервера"
                        else "Выход отличается от входа: каскад, CDN или отдельный exit-узел"
                    } else null
                    Group("Маршрут", modifier = Modifier.appear(3), footer = footer, trailing = { DevTag() }) {
                        PrefRow(RayIcons.Pin, Indigo, "Вход (сервер)", value = entry ?: "—")
                        PrefRow(RayIcons.Globe, Blue, "Выход (видят сайты)", value = exitIp ?: "—", divider = false)
                    }
                }

                Group("DNS", modifier = Modifier.appear(4)) {
                    PrefRow(RayIcons.Wifi, Teal, "Напрямую", value = res.dnsReal ?: "не определён", divider = res.wasConnected || dev)
                    if (res.wasConnected) PrefRow(RayIcons.Shield, Good, "Через VPN", value = res.dnsVpn ?: "не определён", divider = dev)
                    if (dev) PrefRow(RayIcons.Servers, Gray, "DNS в туннеле", value = Repo.settings.dnsList().joinToString(", "), divider = false)
                }

                val v6 = res.ipv6Direct
                val leak = v6 != null && res.wasConnected && !Repo.settings.ipv6
                val v6Note = when {
                    leak -> "В сети есть IPv6, а маршрутизация IPv6 выключена: часть трафика может идти мимо VPN. Включите её в Маршрутизации."
                    res.wasConnected -> "Утечки IPv6 не обнаружено"
                    else -> null
                }
                Group("IPv6", modifier = Modifier.appear(5), footer = v6Note) {
                    PrefRow(
                        if (leak) RayIcons.Alert else RayIcons.Lock,
                        if (leak) Mid else Good,
                        "IPv6 в сети", value = v6 ?: "нет", divider = false
                    )
                }
            }

            Group("Доступность сервисов", modifier = Modifier.appear(6)) {
                if (res.sites.isEmpty()) {
                    Text(
                        "YouTube, Instagram, Telegram, ChatGPT… напрямую и через VPN",
                        color = c.hint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                        CapsLabel("Сервис", Modifier.weight(0.4f))
                        CapsLabel("Напрямую", Modifier.weight(0.3f))
                        CapsLabel("Через VPN", Modifier.weight(0.3f))
                    }
                    res.sites.forEach { s ->
                        Hairline()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.4f))
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
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
                )
            }
            TabBottomSpacer()
        }
    }
}

/** You -> server -> internet, with a link that flows when the tunnel works and breaks when it doesn't. */
@Composable
private fun RouteViz(res: IpCheckResult, kind: Int) {
    val c = ray
    val col = kindColor(kind)
    val real = res.real
    val vpn = res.vpn
    val flowing = (kind == 1 || kind == 3) && look.motion
    val inf = rememberInfiniteTransition(label = "route")
    val phase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "phase")
    val exitFlag = when {
        res.wasConnected && vpn != null -> Fmt.flag(vpn.countryCode)
        !res.wasConnected && real != null -> Fmt.flag(real.countryCode)
        else -> ""
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RouteNode(
            Modifier.weight(1f), RayIcons.User,
            if (real != null) Fmt.flag(real.countryCode) else "", "Вы", real?.ip ?: "—", c.text
        )
        RouteLink(Modifier.weight(0.55f).padding(top = 19.dp), col, broken = kind == 2 && vpn == null, phase = if (flowing) phase else 0f, dim = !res.wasConnected)
        RouteNode(
            Modifier.weight(1f), RayIcons.Servers, "", "Сервер",
            when {
                !res.wasConnected -> "выкл"
                else -> res.entryIp ?: "?"
            },
            if (res.wasConnected) c.text else c.hint
        )
        RouteLink(Modifier.weight(0.55f).padding(top = 19.dp), col, broken = kind == 2, phase = if (flowing) phase else 0f, dim = false)
        RouteNode(
            Modifier.weight(1f), RayIcons.Globe, exitFlag, "Сайты видят",
            when {
                res.wasConnected -> vpn?.ip ?: "—"
                else -> real?.ip ?: "—"
            },
            if (kind == 2) Bad else c.text
        )
    }
}

@Composable
private fun RouteNode(modifier: Modifier, icon: ImageVector, flag: String, title: String, value: String, valueColor: Color) {
    val c = ray
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.raised).border(1.dp, c.stroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (flag.isNotEmpty() && look.flags) Text(flag, fontSize = 19.sp)
            else Icon(icon, contentDescription = null, tint = c.hint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = c.hint, fontSize = 11.sp, maxLines = 1)
        Text(
            value, color = valueColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RouteLink(modifier: Modifier, color: Color, broken: Boolean, phase: Float, dim: Boolean) {
    val faint = ray.faint
    Canvas(modifier.height(4.dp)) {
        val y = size.height / 2f
        val dash = 5.dp.toPx()
        val gap = 5.dp.toPx()
        val col = if (dim) faint else color
        if (broken) {
            val mid = size.width / 2f
            val g = 6.dp.toPx()
            drawLine(col, Offset(0f, y), Offset(mid - g, y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(col, Offset(mid + g, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            val x = 3.dp.toPx()
            drawLine(col, Offset(mid - x, y - x), Offset(mid + x, y + x), 2.dp.toPx(), StrokeCap.Round)
            drawLine(col, Offset(mid - x, y + x), Offset(mid + x, y - x), 2.dp.toPx(), StrokeCap.Round)
        } else {
            drawLine(
                col, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -(dash + gap) * phase)
            )
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
            Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TNum)
        }
    }
}

@Composable
private fun IpCard(title: String, info: IpInfo?, err: String?, index: Int, dev: Boolean) {
    val c = ray
    val clipboard = LocalClipboardManager.current
    Group(title, modifier = Modifier.appear(index)) {
        if (info == null) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(RayIcons.Alert, contentDescription = null, tint = if (err != null) Bad else c.hint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(err ?: "Нет данных", color = if (err != null) Bad else c.hint, fontSize = 14.sp)
            }
        } else {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                FlagBox(Fmt.flag(info.countryCode), 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.ip, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(info.country, info.city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "—" },
                        color = c.hint, fontSize = 13.sp
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.raised).clickable {
                        clipboard.setText(AnnotatedString(info.ip))
                        toast("IP скопирован")
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(RayIcons.Copy, contentDescription = "Копировать", tint = c.accent, modifier = Modifier.size(17.dp))
                }
            }
            Hairline()
            KV("Провайдер", info.isp)
            if (dev) {
                KV("Организация", info.org)
                KV("ASN", info.asn, mono = true)
            }
            KV("Регион", info.region)
            if (dev) KV("Часовой пояс", info.timezone)
            val hosting = info.hosting
            if (hosting != null) KV("Тип IP", if (hosting) "Дата-центр" else "Домашний / мобильный")
            if (info.proxy == true) KV("Метка", "Известен как прокси / VPN", Mid)
            if (dev) KV("Ответ API", "${info.latencyMs} ms · ${info.source}")
        }
    }
}
