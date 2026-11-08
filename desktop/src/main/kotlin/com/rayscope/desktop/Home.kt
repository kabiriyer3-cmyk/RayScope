package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.fluid.LiveIcon
import com.rayscope.fluid.rememberIconFx
import com.rayscope.fluid.smoothPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Select a server and connect to it (or switch the running session to it). */
fun connectTo(id: String) {
    Store.select(id)
    val st = Engine.status.value
    when (st.state) {
        ConnState.STOPPED -> Engine.connect()
        ConnState.CONNECTED, ConnState.CONNECTING -> if (st.serverId != id) Engine.reconnect()
        ConnState.STOPPING -> Unit
    }
}

/** Select a server; if a session is running, move it to that server. */
fun pickServer(id: String) {
    if (Engine.status.value.state == ConnState.STOPPED) Store.select(id) else connectTo(id)
}

/** Bottom padding of every page (the tab bar sits right under it). */
val TAB_SPACE = 20.dp

/** Space between blocks on a page. */
private val GAP = 10.dp

@Composable
fun HomeScreen(onGoServers: () -> Unit, onGoCheck: () -> Unit) {
    val st by Engine.status.collectAsState()
    val prefs by Store.prefs.collectAsState()
    val servers by Store.servers.collectAsState()
    val subs by Store.subs.collectAsState()
    val selId by Store.selectedId.collectAsState()
    val testing by Actions.testing.collectAsState()
    val prog by Actions.progress.collectAsState()
    val updating by Actions.updating.collectAsState()
    val sel = remember(servers, selId) { Store.selected() }

    ScrollColumn(PaddingValues(start = PAGE_PAD, end = PAGE_PAD, top = PAGE_TOP, bottom = TAB_SPACE)) {
        ServerCard(st, sel, servers, onGoServers)
        Spacer(Modifier.height(GAP))
        StatGrid(st)
        Spacer(Modifier.height(GAP))
        ModeCard(st, prefs)
        Spacer(Modifier.height(GAP))
        FastList(sel, servers, testing, onGoServers)
        Spacer(Modifier.height(GAP))
        ActionsCard(testing, prog, updating, subs.isNotEmpty(), onGoCheck)
        if (prefs.dev) {
            Spacer(Modifier.height(GAP))
            DevConnection(st, sel)
        }
    }
}

// ------------------------------------------------------------------ server

/** The server the tile connects to. Click opens the list. */
@Composable
private fun ServerCard(st: Status, sel: Server?, servers: List<Server>, onGoServers: () -> Unit) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val srv = remember(servers, st.serverId, sel) { st.serverId?.let { id -> servers.firstOrNull { it.id == id } } ?: sel }
    val connected = st.state == ConnState.CONNECTED
    val bg by animateColorAsState(if (hovered) lerp(p.surface, p.text, 0.035f) else p.surface, tween(140), label = "srvBg")
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().height(64.dp).squish(src).clip(shape).background(bg)
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onGoServers).hand()
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagBox(srv?.countryCode().orEmpty(), 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                srv?.cleanName() ?: "Сервер не выбран", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (srv != null) listOf(srv.protocolLabel(), srv.network.uppercase(), srv.securityLabel()).filter { it.isNotBlank() }.joinToString(" · ")
                else "Добавьте подписку или ссылку",
                color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (srv != null) {
            val d = if (connected && st.livePing >= 0) st.livePing else srv.delay
            if (d >= 0) {
                SignalBars(d)
                Spacer(Modifier.width(8.dp))
                Text("$d ms", color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
                Spacer(Modifier.width(4.dp))
            }
        }
        LiveIcon(Ico.Arrow, if (hovered) p.text else p.hint.copy(alpha = 0.7f), Modifier.size(16.dp), hover = hovered)
    }
}

// ------------------------------------------------------------------ stats

@Composable
private fun StatGrid(st: Status) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val hist = st.history
    val pings = st.pings.filter { it >= 0 }
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            StatCard(
                Ico.ArrowDown, "Загрузка", if (connected) Fmt.speed(st.down) else "—", Modifier.weight(1f),
                chart = if (connected) hist.map { it.second.toFloat() } else null, tint = p.accent
            )
            StatCard(
                Ico.ArrowUp, "Отдача", if (connected) Fmt.speed(st.up) else "—", Modifier.weight(1f),
                chart = if (connected) hist.map { it.first.toFloat() } else null, tint = p.accent
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            StatCard(
                Ico.Activity, "Пинг", if (connected) Fmt.ms(st.livePing) else "—", Modifier.weight(1f),
                chart = if (connected && pings.size > 1) pings.map { it.toFloat() } else null,
                tint = if (connected) delayColor(st.livePing) else p.hint
            )
            StatCard(
                Ico.Layers, "Трафик", if (connected) Fmt.bytes(st.totalDown + st.totalUp) else "—", Modifier.weight(1f),
                foot = if (connected) "↓ " + Fmt.bytes(st.totalDown) + "  ↑ " + Fmt.bytes(st.totalUp) else "за сессию", tint = p.hint
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier,
    tint: Color,
    chart: List<Float>? = null,
    foot: String? = null,
) {
    val p = pal
    Column(modifier.height(96.dp).clip(RoundedCornerShape(18.dp)).background(p.surface).padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = p.hint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = p.hint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.BottomStart) {
            if (chart != null && chart.size >= 2) {
                MiniChart(chart, tint, Modifier.fillMaxSize())
            } else {
                Text(foot ?: "после подключения", color = p.hint.copy(alpha = 0.8f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
            }
        }
    }
}

@Composable
private fun MiniChart(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.15f
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - v / max * size.height) }
        val area = smoothPath(pts, 0f, size.height).apply {
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.10f))
        drawPath(smoothPath(pts, 0f, size.height), color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------ mode

@Composable
private fun ModeCard(st: Status, prefs: Prefs) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val http = if (connected) Engine.httpPort else prefs.httpPort
    val socks = if (connected) Engine.socksPort else prefs.socksPort
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Режим", color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (st.systemProxy) Tag("прокси Windows", Good)
            if (st.tun) Tag("TUN", Good)
        }
        Spacer(Modifier.height(10.dp))
        Segmented(
            listOf("Системный", "TUN", "Локальный"),
            selected = when (prefs.mode) {
                Mode.SYSTEM -> 0
                Mode.TUN -> 1
                else -> 2
            },
            onSelect = {
                Modes.request(
                    when (it) {
                        0 -> Mode.SYSTEM
                        1 -> Mode.TUN
                        else -> Mode.LOCAL
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 14.dp),
            accent = true
        )
        Text(
            when (prefs.mode) {
                Mode.SYSTEM -> "Браузеры и программы идут через RayScope. Telegram и игры: SOCKS5 или TUN."
                Mode.TUN -> "Весь трафик компьютера идёт через VPN, включая игры и Telegram. Нужны права администратора."
                else -> "Пропишите адрес ниже в настройках прокси нужной программы."
            },
            color = p.hint, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp)
        )
        if (prefs.mode == Mode.TUN && !Elevation.isAdmin) {
            Btn(
                "Перезапустить от администратора", { Modes.askAdmin.value = true },
                Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp), icon = Ico.Shield
            )
        }
        CopyRow("HTTP", "127.0.0.1:$http")
        CopyRow("SOCKS5", "127.0.0.1:$socks")
    }
}

@Composable
fun CopyRow(k: String, v: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().height(38.dp).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 12.5.sp, maxLines = 1, modifier = Modifier.width(72.dp))
        Text(v, color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum, modifier = Modifier.weight(1f))
        IconBtn(Ico.Copy, "Скопировать", {
            Clip.copy(v)
            Notice.show("Скопировано: $v")
        }, size = 30.dp)
    }
}

// ------------------------------------------------------------------ fastest servers

@Composable
private fun FastList(sel: Server?, servers: List<Server>, testing: Boolean, onGoServers: () -> Unit) {
    val p = pal
    val best = remember(servers) { servers.filter { it.isWorking }.sortedBy { it.delay }.take(4) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(top = 8.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Быстрые серверы", color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Btn("Все", onGoServers, color = p.hint, icon = Ico.Arrow)
        }
        when {
            servers.isEmpty() -> EmptyHint("Серверов пока нет", "Добавьте подписку или ссылки vless://, vmess://, trojan://", "Добавить", onAction = onGoServers)
            best.isEmpty() -> EmptyHint("Ещё не проверены", "Пинг покажет, какие серверы живые", if (testing) "Проверяю…" else "Пинг всех", enabled = !testing) {
                Actions.go { Actions.testAll() }
            }
            else -> best.forEach { s -> FastRow(s, s.id == sel?.id) { pickServer(s.id) } }
        }
    }
}

@Composable
private fun FastRow(s: Server, selected: Boolean, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp).clip(shape)
            .background(if (selected) p.raised else Color.Transparent)
            .press(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagBox(s.countryCode(), 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(s.cleanName(), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (selected) LiveIcon(Ico.Check, p.text, Modifier.size(15.dp), contentDescription = "выбран", appear = true)
        }
        SignalBars(s.delay)
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            Text("${s.delay} ms", color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
        }
    }
}

@Composable
private fun EmptyHint(title: String, sub: String, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = p.hint, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Btn(action, onAction, enabled = enabled)
    }
}

// ------------------------------------------------------------------ actions

@Composable
private fun ActionsCard(testing: Boolean, prog: Pair<Int, Int>, updating: Boolean, hasSubs: Boolean, onGoCheck: () -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(vertical = 6.dp)) {
        ActionRow(Ico.Bolt, "Лучший сервер", "проверить и подключиться", enabled = !testing) {
            Actions.go {
                val b = Actions.pickBest()
                if (b != null) {
                    connectTo(b.id)
                    Notice.show("Лучший: " + b.cleanName() + " · " + b.delay + " ms")
                } else {
                    Notice.show("Рабочих серверов не найдено")
                }
            }
        }
        ActionRow(
            Ico.Activity, "Пинг всех", if (testing) "проверено " + prog.first + " из " + prog.second else "реальная задержка",
            enabled = !testing, progress = if (testing && prog.second > 0) prog.first.toFloat() / prog.second else null,
            busy = testing
        ) {
            Actions.go { Actions.testAll() }
        }
        ActionRow(Ico.ShieldCheck, "Проверка IP", "адрес, страна, сервисы", kbd = "Ctrl 3") { onGoCheck() }
        ActionRow(
            Ico.Refresh, "Подписки", if (updating) "обновляю…" else if (hasSubs) "обновить все" else "пока нет",
            enabled = hasSubs && !updating, busy = updating
        ) {
            Actions.go { Actions.updateAllSubs(silent = false) }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    sub: String,
    enabled: Boolean = true,
    kbd: String? = null,
    progress: Float? = null,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val fx = rememberIconFx()
    val bg by animateColorAsState(if (hovered && enabled) p.raised else p.raised.copy(alpha = 0f), tween(140), label = "actBg")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).squish(src, enabled).alpha(if (enabled) 1f else 0.5f).clip(shape).background(bg)
            .hoverable(src, enabled).clickable(interactionSource = src, indication = null, enabled = enabled) {
                onClick()
                fx.fire(icon)
            }.hand()
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, p.text, box = 32.dp, hover = hovered && enabled, play = fx.plays, busy = busy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(sub, color = p.hint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
            }
            if (kbd != null) {
                Kbd(kbd)
            } else {
                LiveIcon(Ico.Arrow, if (hovered && enabled) p.text else p.hint.copy(alpha = 0.6f), Modifier.size(16.dp), hover = hovered && enabled)
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            SlimProgress(progress, Modifier.padding(start = 44.dp))
        }
    }
}

// ------------------------------------------------------------------ developer

@Composable
private fun DevConnection(st: Status, sel: Server?) {
    var core by remember { mutableStateOf("…") }
    LaunchedEffect(Unit) { core = withContext(Dispatchers.IO) { Core.version() } }
    Section("Подключение", trailing = { DevTag() }) {
        KV("Ядро", core, mono = true)
        KV("Состояние", st.state.name, mono = true)
        KV("Сервер", sel?.let { it.address + ":" + it.port } ?: "", mono = true)
        KV("С момента", if (st.since > 0) Fmt.date(st.since) else "")
        KV("Трафик", Fmt.bytes(st.totalDown) + " / " + Fmt.bytes(st.totalUp))
        KV("Системный прокси", if (SystemProxy.active) "127.0.0.1:" + SystemProxy.port else "выкл", mono = true)
        KV("Файл ядра", Paths.xray()?.absolutePath ?: "не найдено", mono = true)
        KV("Данные", Paths.data.absolutePath, mono = true)
    }
}
