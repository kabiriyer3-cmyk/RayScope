package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.fluid.Aurora
import com.rayscope.fluid.GooeyConnectButton
import com.rayscope.fluid.PulseDot
import com.rayscope.fluid.smoothPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

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

/** Bottom padding of every page. */
val TAB_SPACE = 32.dp

private val HERO_INK = Color(0xFFF2F4FA)
private val HERO_DIM = Color(0xFFF2F4FA).copy(alpha = 0.6f)
private val HERO_BG = Color(0xFF0A0D19)

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
    val connected = st.state == ConnState.CONNECTED

    // once per session: look up the exit IP so the hero can show it (runs in the app scope, not this screen)
    LaunchedEffect(connected, st.since) {
        if (connected && IpCheck.state.value.checkedAt < st.since) {
            delay(1500)
            if (Engine.isConnected && !IpCheck.state.value.running) Actions.go { IpCheck.run() }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = TAB_SPACE)
    ) {
        Header("Главная", if (connected) "Трафик идёт через " + st.serverName else "${servers.size} серверов · Ctrl K — команды и поиск") {
            StatusPill(st.state)
        }
        Hero(st, prefs, sel)
        Spacer(Modifier.height(16.dp))
        StatRow(st)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpeedCard(st, Modifier.weight(0.58f).fillMaxHeight())
            FastList(sel, servers, testing, onGoServers, Modifier.weight(0.42f).fillMaxHeight())
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionsCard(testing, prog, updating, subs.isNotEmpty(), onGoCheck, Modifier.weight(0.58f).fillMaxHeight())
            ModeCard(st, prefs, Modifier.weight(0.42f).fillMaxHeight())
        }
        if (prefs.dev) {
            Spacer(Modifier.height(16.dp))
            DevConnection(st, sel)
        }
    }
}

private fun stateColor(s: ConnState, idle: Color): Color = when (s) {
    ConnState.CONNECTED -> Good
    ConnState.STOPPED -> idle
    else -> Mid
}

@Composable
fun StatusPill(s: ConnState) {
    val p = pal
    val col = stateColor(s, p.hint)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(30.dp).clip(shape).background(col.copy(alpha = 0.12f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulseDot(col, dot = 7.dp, live = s != ConnState.STOPPED)
        Spacer(Modifier.width(8.dp))
        Text(stateText(s), color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ hero

/**
 * Always-dark hero: aurora tinted by the connection state, a dot field behind the button, the server in large type,
 * live facts underneath and the gooey connect button on the right.
 */
@Composable
private fun Hero(st: Status, prefs: Prefs, sel: Server?) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val busy = st.state == ConnState.CONNECTING || st.state == ConnState.STOPPING
    val cs by IpCheck.state.collectAsState()
    val servers by Store.servers.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.state, st.since) {
        while (st.state == ConnState.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val srv = remember(servers, st.serverId, sel) { st.serverId?.let { id -> servers.firstOrNull { it.id == id } } ?: sel }
    val dot = when {
        connected -> Good
        busy -> Mid
        else -> Color(0xFF8C94A8)
    }
    val aurora = when {
        connected -> listOf(p.accent.copy(alpha = 0.55f), p.accent2.copy(alpha = 0.42f), Good.copy(alpha = 0.16f))
        busy -> listOf(Mid.copy(alpha = 0.3f), p.accent.copy(alpha = 0.34f), p.accent2.copy(alpha = 0.2f))
        else -> listOf(p.accent.copy(alpha = 0.18f), p.accent2.copy(alpha = 0.1f), Color(0xFF3A4466).copy(alpha = 0.3f))
    }
    val err = !connected && !busy && st.error.isNotEmpty()
    val title = srv?.cleanName() ?: "Сервер не выбран"
    val sub = when {
        err -> st.error
        srv != null -> listOf(srv.protocolLabel(), srv.network.uppercase(), srv.securityLabel()).filter { it.isNotBlank() }.joinToString("  ·  ")
        else -> "Добавьте подписку или ссылку на сервер"
    }
    val vpnIp = cs.vpn?.takeIf { connected && cs.wasConnected && cs.checkedAt >= st.since }
    val realIp = cs.real?.takeIf { !connected && !cs.wasConnected }
    val ipText = when {
        vpnIp != null -> vpnIp.ip + if (vpnIp.countryCode.isNotEmpty()) "  " + vpnIp.countryCode else ""
        cs.running -> "проверка…"
        realIp != null -> realIp.ip
        else -> "проверить"
    }
    val ping = when {
        connected -> Fmt.ms(st.livePing)
        srv != null && srv.delay >= 0 -> "${srv.delay} ms"
        else -> "—"
    }
    val caption = when {
        connected -> "Нажмите, чтобы отключить"
        st.state == ConnState.CONNECTING -> "Поднимаю туннель…"
        st.state == ConnState.STOPPING -> "Отключаю…"
        srv == null -> "Нет сервера"
        else -> "Нажмите, чтобы подключиться"
    }
    val iconCol by animateColorAsState(if (connected || busy) Color.White else Color(0xFFC7CEE2), tween(300), label = "heroIcon")
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().height(276.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF121734), HERO_BG)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        Aurora(aurora, Modifier.matchParentSize().graphicsLayer(), live = connected || busy)
        DotGrid(Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to HERO_BG.copy(alpha = 0.6f), 0.6f to HERO_BG.copy(alpha = 0f))))
        Row(
            Modifier.matchParentSize().padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroChip {
                        PulseDot(dot, dot = 7.dp, live = connected || busy)
                        Spacer(Modifier.width(8.dp))
                        Text(stateText(st.state), color = HERO_INK, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    if (connected) {
                        Spacer(Modifier.width(8.dp))
                        HeroChip {
                            Icon(Ico.Clock, contentDescription = null, tint = HERO_DIM, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Fmt.duration(now - st.since), color = HERO_INK, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
                        }
                    }
                    if (st.systemProxy) {
                        Spacer(Modifier.width(8.dp))
                        HeroChip {
                            Icon(Ico.Globe, contentDescription = null, tint = HERO_DIM, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Системный прокси", color = HERO_INK, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagBox(srv?.countryCode().orEmpty(), 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(title, color = HERO_INK, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, color = if (err) Bad else HERO_DIM, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroStat("IP-адрес", ipText, Modifier.weight(1.4f), onClick = if (cs.running) null else ({ Actions.go { IpCheck.run() } }))
                    HeroStat("Пинг", ping, Modifier.weight(1f))
                    HeroStat("Трафик", if (connected) Fmt.bytes(st.totalDown + st.totalUp) else "—", Modifier.weight(1f))
                    HeroStat("Режим", if (prefs.mode == 1) "Системный" else "Локальный", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GooeyConnectButton(
                    on = connected,
                    busy = busy,
                    onClick = { Engine.toggle() },
                    accent = p.accent,
                    accent2 = p.accent2,
                    idle = Color(0xFF2B3352),
                    idle2 = Color(0xFF151A2D),
                    modifier = Modifier.graphicsLayer(),
                    diameter = 188.dp,
                    iconColor = iconCol,
                    ringColor = Color.White.copy(alpha = 0.09f),
                    enabled = st.state != ConnState.STOPPING && (sel != null || st.state != ConnState.STOPPED),
                )
                Text(caption, color = HERO_DIM, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HeroChip(content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(28.dp).clip(shape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    var m = modifier
    if (onClick != null) {
        m = m.clip(RoundedCornerShape(8.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).hand()
    }
    Column(m) {
        Text(label, color = HERO_DIM, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(
            value, color = if (onClick != null && value == "проверить") Color(0xFFB9C3FF) else HERO_INK,
            fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum
        )
    }
}

/** Dot field that fades in toward the button side. Static, so it lives in its own layer. */
@Composable
private fun DotGrid(modifier: Modifier) {
    Canvas(modifier.graphicsLayer()) {
        val step = 18.dp.toPx()
        val r = 1.dp.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                val f = x / size.width
                val a = 0.08f * f * f
                if (a > 0.004f) drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

// ------------------------------------------------------------------ stats

@Composable
private fun StatRow(st: Status) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val hist = st.history
    val pings = st.pings.filter { it >= 0 }
    val jitter = if (pings.size > 2) pings.zipWithNext { a, b -> abs(a - b) }.average().toLong() else -1L
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(
            Ico.ArrowDown, p.accent, "Загрузка", if (connected) Fmt.speed(st.down) else "—", Modifier.weight(1f),
            chart = if (connected) hist.map { it.second.toFloat() } else null, foot = "после подключения"
        )
        StatCard(
            Ico.ArrowUp, p.accent2, "Отдача", if (connected) Fmt.speed(st.up) else "—", Modifier.weight(1f),
            chart = if (connected) hist.map { it.first.toFloat() } else null, foot = "после подключения"
        )
        StatCard(
            Ico.Activity, if (connected) delayColor(st.livePing) else p.hint, "Пинг", if (connected) Fmt.ms(st.livePing) else "—", Modifier.weight(1f),
            chart = if (connected && pings.size > 1) pings.map { it.toFloat() } else null,
            foot = "замер раз в ${Store.prefs.value.livePingInterval} с",
            trailing = if (connected && jitter >= 0) "±$jitter ms" else null
        )
        StatCard(
            Ico.Layers, Purple, "Трафик", if (connected) Fmt.bytes(st.totalDown + st.totalUp) else "—", Modifier.weight(1f),
            foot = if (connected) "↓ " + Fmt.bytes(st.totalDown) + "   ↑ " + Fmt.bytes(st.totalUp) else "за сессию"
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    modifier: Modifier,
    chart: List<Float>? = null,
    foot: String? = null,
    trailing: String? = null,
) {
    val p = pal
    Column(modifier.card(p, 20.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, box = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = p.hint, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = p.hint, fontSize = 11.5.sp, maxLines = 1, style = TNum)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, color = p.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.BottomStart) {
            if (chart != null && chart.size >= 2) {
                MiniChart(chart, tint, Modifier.fillMaxSize())
            } else if (foot != null) {
                Text(foot, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
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
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(smoothPath(pts, 0f, size.height), color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------ speed chart

@Composable
private fun SpeedCard(st: Status, modifier: Modifier) {
    val p = pal
    val hist = st.history
    val peak = hist.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L
    val downC = p.accent
    val upC = p.accent2
    Column(modifier.card(p, 20.dp).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Скорость", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (hist.size >= 2) "пик " + Fmt.speed(peak) else "за последнюю минуту", color = p.hint, fontSize = 12.sp, style = TNum)
            }
            Legend("загрузка", downC)
            Spacer(Modifier.width(14.dp))
            Legend("отдача", upC)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 150.dp)) {
            if (hist.size < 2) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ico.Activity, contentDescription = null, tint = p.hint.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (st.state == ConnState.CONNECTED) "Собираю данные…" else "График появится после подключения",
                        color = p.hint, fontSize = 12.5.sp
                    )
                }
            } else {
                val grid = p.stroke
                Canvas(Modifier.fillMaxSize()) {
                    val max = peak.coerceAtLeast(1024L).toFloat() * 1.15f
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                    for (k in 0..3) {
                        val y = size.height * k / 3f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = if (k == 3) null else dash)
                    }
                    series(hist.map { it.second }, max, downC, fill = true)
                    series(hist.map { it.first }, max, upC, fill = false)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text("1 мин назад", color = p.hint.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("сейчас", color = p.hint.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

private fun DrawScope.series(v: List<Long>, max: Float, color: Color, fill: Boolean) {
    if (v.size < 2) return
    val step = size.width / (v.size - 1)
    val pts = v.mapIndexed { i, x -> Offset(i * step, size.height - (x.toFloat() / max) * size.height) }
    if (fill) {
        val area = smoothPath(pts, 0f, size.height).apply {
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0f))))
    }
    drawPath(smoothPath(pts, 0f, size.height), color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val last = pts.last()
    drawCircle(color.copy(alpha = 0.22f), 7.dp.toPx(), last)
    drawCircle(color, 3.5.dp.toPx(), last)
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = pal.hint, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------------ fastest servers

@Composable
private fun FastList(sel: Server?, servers: List<Server>, testing: Boolean, onGoServers: () -> Unit, modifier: Modifier) {
    val p = pal
    val best = remember(servers) { servers.filter { it.isWorking }.sortedBy { it.delay }.take(6) }
    Column(modifier.card(p, 20.dp).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Быстрые серверы", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Btn("Все", onGoServers, color = p.hint, icon = Ico.Arrow)
        }
        Spacer(Modifier.height(6.dp))
        when {
            servers.isEmpty() -> EmptyHint(Ico.Servers, "Серверов пока нет", "Добавьте подписку или вставьте ссылки vless://, vmess://, trojan://", "Добавить", onAction = onGoServers)
            best.isEmpty() -> EmptyHint(Ico.Activity, "Ещё не проверены", "Пинг покажет, какие серверы работают и как быстро", if (testing) "Проверяю…" else "Пинг всех", enabled = !testing) {
                Actions.go { Actions.testAll() }
            }
            else -> best.forEach { s -> FastRow(s, s.id == sel?.id) { pickServer(s.id) } }
        }
    }
}

@Composable
private fun FastRow(s: Server, selected: Boolean, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp).clip(shape)
            .background(if (selected) p.accent.copy(alpha = if (p.dark) 0.12f else 0.08f) else Color.Transparent)
            .press(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagBox(s.countryCode(), 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.cleanName(), color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.protocolLabel() + " · " + s.network.uppercase(), color = p.hint, fontSize = 11.5.sp, maxLines = 1)
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Ico.Check, contentDescription = "выбран", tint = p.accent, modifier = Modifier.size(16.dp))
        }
        SignalBars(s.delay)
        Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterEnd) {
            Text("${s.delay} ms", color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
        }
    }
}

@Composable
private fun EmptyHint(icon: ImageVector, title: String, sub: String, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, p.accent, box = 40.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = p.hint, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Btn(action, onAction, enabled = enabled)
    }
}

// ------------------------------------------------------------------ actions and mode

@Composable
private fun ActionsCard(testing: Boolean, prog: Pair<Int, Int>, updating: Boolean, hasSubs: Boolean, onGoCheck: () -> Unit, modifier: Modifier) {
    val p = pal
    Column(modifier.card(p, 20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Быстрые действия", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Ico.Bolt, "Лучший сервер", "проверить и подключиться", p.accent, Modifier.weight(1f).fillMaxHeight(), enabled = !testing) {
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
            ActionTile(
                Ico.Activity, "Пинг всех", if (testing) "проверено " + prog.first + " из " + prog.second else "реальная задержка",
                Teal, Modifier.weight(1f).fillMaxHeight(), enabled = !testing,
                progress = if (testing && prog.second > 0) prog.first.toFloat() / prog.second else null
            ) {
                Actions.go { Actions.testAll() }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Ico.ShieldCheck, "Проверка IP", "адрес, страна и сервисы", Good, Modifier.weight(1f).fillMaxHeight(), kbd = "Ctrl 3") { onGoCheck() }
            ActionTile(
                Ico.Refresh, "Подписки", if (updating) "обновляю…" else if (hasSubs) "обновить все" else "пока нет",
                Purple, Modifier.weight(1f).fillMaxHeight(), enabled = hasSubs && !updating, spinning = updating
            ) {
                Actions.go { Actions.updateAllSubs(silent = false) }
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    sub: String,
    tint: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    kbd: String? = null,
    progress: Float? = null,
    spinning: Boolean = false,
    onClick: () -> Unit,
) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && enabled) lerp(p.raised, p.text, 0.045f) else p.raised, tween(140), label = "tileBg")
    val line by animateColorAsState(if (hovered && enabled) tint.copy(alpha = 0.4f) else p.stroke, tween(160), label = "tileLine")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.squish(src, enabled).alpha(if (enabled) 1f else 0.5f).clip(shape).background(bg).border(1.dp, line, shape)
            .hoverable(src, enabled).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick).hand()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, box = 32.dp, rotation = if (spinning) spin() else null)
            Spacer(Modifier.weight(1f))
            if (kbd != null) {
                Kbd(kbd)
            } else {
                Icon(Ico.Arrow, contentDescription = null, tint = if (hovered && enabled) p.text else p.hint.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(sub, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            SlimProgress(progress)
        }
    }
}

@Composable
private fun ModeCard(st: Status, prefs: Prefs, modifier: Modifier) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val http = if (connected) Engine.httpPort else prefs.httpPort
    val socks = if (connected) Engine.socksPort else prefs.socksPort
    Column(modifier.card(p, 20.dp).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Режим", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (st.systemProxy) Tag("прокси Windows включён", Good)
        }
        Spacer(Modifier.height(12.dp))
        Segmented(
            listOf("Системный прокси", "Только локальный"),
            selected = if (prefs.mode == 1) 0 else 1,
            onSelect = { Engine.setMode(if (it == 0) 1 else 0) },
            modifier = Modifier.padding(horizontal = 18.dp),
            accent = true
        )
        Text(
            if (prefs.mode == 1) "Windows направит браузеры и программы через RayScope. Для Telegram и игр берите SOCKS5."
            else "Укажите адрес ниже в настройках прокси нужной программы.",
            color = p.hint, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        CopyRow("HTTP", "127.0.0.1:$http")
        CopyRow("SOCKS5", "127.0.0.1:$socks")
    }
}

@Composable
fun CopyRow(k: String, v: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().height(42.dp).padding(start = 18.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(96.dp))
        Text(v, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum, modifier = Modifier.weight(1f))
        IconBtn(Ico.Copy, "Скопировать", {
            Clip.copy(v)
            Notice.show("Скопировано: $v")
        }, size = 30.dp)
    }
}

@Composable
private fun DevConnection(st: Status, sel: Server?) {
    var core by remember { mutableStateOf("…") }
    LaunchedEffect(Unit) { core = withContext(Dispatchers.IO) { Core.version() } }
    Section("Подключение", trailing = { DevTag() }) {
        KV("Ядро", core, mono = true)
        KV("Состояние", st.state.name, mono = true)
        KV("Сервер", sel?.let { it.address + ":" + it.port } ?: "", mono = true)
        KV("С момента", if (st.since > 0) Fmt.date(st.since) else "")
        KV("Загружено / отдано", Fmt.bytes(st.totalDown) + " / " + Fmt.bytes(st.totalUp))
        KV("Системный прокси", if (SystemProxy.active) "127.0.0.1:" + SystemProxy.port else "выкл", mono = true)
        KV("Файл ядра", Paths.xray()?.absolutePath ?: "не найдено", mono = true)
        KV("Данные", Paths.data.absolutePath, mono = true)
    }
}
