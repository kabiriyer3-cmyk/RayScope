package com.rayscope.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.CoreManager
import com.rayscope.app.core.RayVpnService
import com.rayscope.app.core.VpnState
import com.rayscope.app.core.VpnStatus
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.model.Server
import com.rayscope.app.net.IpCheckModel
import com.rayscope.app.net.Tester
import com.rayscope.app.util.Fmt
import com.rayscope.app.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(onToggle: () -> Unit, onGoServers: () -> Unit, onGoIp: () -> Unit) {
    val c = ray
    val st = Repo.settings
    val vpn by VpnState.status.collectAsState()
    val servers by Repo.servers.collectAsState()
    val selectedId by Repo.selectedId.collectAsState()
    val ip by IpCheckModel.state.collectAsState()
    val testing by Actions.testing.collectAsState()
    val prog by Actions.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val currentId = vpn.serverId ?: selectedId
    val server = servers.firstOrNull { it.id == currentId } ?: servers.firstOrNull()
    val connected = vpn.state == ConnState.CONNECTED
    val busy = vpn.state == ConnState.CONNECTING || vpn.state == ConnState.STOPPING
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connected) {
        while (connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    var speedText by remember { mutableStateOf<String?>(null) }
    var speedRunning by remember { mutableStateOf(false) }
    var bestRunning by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 120 } }
    val working = servers.count { it.isWorking }
    val stateText = when (vpn.state) {
        ConnState.CONNECTED -> "Защищено"
        ConnState.CONNECTING -> "Подключение…"
        ConnState.STOPPING -> "Отключение…"
        ConnState.STOPPED -> "Не подключено"
    }

    val runSpeed: () -> Unit = {
        speedRunning = true
        speedText = "Измеряю…"
        val via = connected
        scope.launch {
            speedText = try {
                val r = Tester.speedTest(via) { v -> speedText = String.format(Locale.US, "%.1f Мбит/с", v) }
                String.format(Locale.US, "%.1f Мбит/с", r)
            } catch (e: Exception) {
                "ошибка"
            }
            speedRunning = false
        }
    }
    val runBest: () -> Unit = {
        bestRunning = true
        scope.launch {
            val b = Actions.pickBest()
            bestRunning = false
            if (b == null) {
                toast("Нет рабочих серверов")
            } else {
                Repo.select(b.id)
                toast("Лучший: ${b.cleanName()} (${b.delay} ms)")
                if (VpnState.isConnected) RayVpnService.switchServer() else onToggle()
            }
        }
    }

    Page(title = "RayScope", scrolled = scrolled) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            ScreenTitle(
                "RayScope",
                if (servers.isEmpty()) "Добавьте подписку, чтобы начать" else "${servers.size} серверов · $working рабочих",
                trailing = { StatusPill(vpn.state) }
            )

            // ---- hero: the scope
            Box(Modifier.appear(0).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScopeDial(vpn.state, vpn.pingHistory, onToggle)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TickerText(stateText, c.text, 24.sp, FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        connected -> Fmt.duration(now - vpn.since)
                        busy -> "подождите"
                        else -> "нажмите на круг, чтобы подключиться"
                    },
                    color = if (connected) Good else c.hint, fontSize = if (connected) 16.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold, style = TNum
                )
                if (vpn.error.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(RayIcons.Alert, contentDescription = null, tint = Bad, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(vpn.error, color = Bad, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            ServerPicker(server, Modifier.appear(1), onGoServers)

            // ---- live metrics
            if (connected) {
                LiveBento(vpn, Modifier.appear(2))
                if (vpn.pingHistory.isNotEmpty()) QualityGroup(vpn.pingHistory, Modifier.appear(3))
            }

            // ---- quick actions
            Column(
                Modifier.appear(3).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        RayIcons.Bolt, "Лучший сервер", "проверить и подключить", Good, Modifier.weight(1f),
                        busy = bestRunning, enabled = !busy && servers.isNotEmpty(), onClick = runBest
                    )
                    ActionTile(RayIcons.Globe, "Проверка IP", "реальный против VPN", Blue, Modifier.weight(1f)) {
                        onGoIp()
                        scope.launch { IpCheckModel.run() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(
                        RayIcons.Gauge, "Скорость", speedText ?: (if (connected) "через VPN" else "напрямую"), Orange, Modifier.weight(1f),
                        busy = speedRunning, highlight = speedText != null && !speedRunning, onClick = runSpeed
                    )
                    ActionTile(
                        RayIcons.Refresh, "Пинг всех",
                        if (testing) "${prog.first} из ${prog.second}" else Actions.modeLabel(), Purple, Modifier.weight(1f),
                        busy = testing, enabled = servers.isNotEmpty()
                    ) { scope.launch { Actions.testAll() } }
                }
            }

            // ---- connection details
            if (server != null) {
                val tweaks = buildList {
                    if (st.muxEnabled) add("MUX")
                    if (st.fragmentEnabled) add("FRAGMENT")
                    if (st.tcpFastOpen) add("TFO")
                    if (st.blockQuic) add("NO-QUIC")
                    if (st.ipv6) add("IPv6")
                    if (st.allowLan) add("LAN")
                    if (st.defaultFingerprint.isNotEmpty()) add("FP:" + st.defaultFingerprint.uppercase())
                }
                Group("Подключение", modifier = Modifier.appear(4)) {
                    if (tweaks.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tweaks.forEach { Tag(it, c.accent) }
                        }
                    }
                    KV("Адрес", "${server.address}:${server.port}", mono = true)
                    KV("Транспорт", server.network + (if (server.path.isNotEmpty()) " ${server.path}" else ""), mono = true)
                    KVIf("SNI", server.sni, mono = true)
                    KV("Защита", server.securityLabel())
                    val exit = ip.vpn
                    if (connected && ip.wasConnected && exit != null) {
                        KV("Выходной IP", "${Fmt.flag(exit.countryCode)} ${exit.ip}", mono = true)
                    }
                    val host = if (st.allowLan) "0.0.0.0" else "127.0.0.1"
                    KV("Локальный прокси", "$host:${st.socksPort} · ${st.httpPort}", mono = true)
                    if (vpn.switches > 0) KV("Переключений", "${vpn.switches}")
                    KV("Ядро", CoreManager.version())
                }
            }
            TabBottomSpacer()
        }
    }
}

@Composable
private fun StatusPill(state: ConnState) {
    val c = ray
    val on = state == ConnState.CONNECTED
    val busy = state == ConnState.CONNECTING || state == ConnState.STOPPING
    val col by animateColorAsState(
        when {
            on -> Good
            busy -> Mid
            else -> c.hint
        },
        tween(300), label = "pill"
    )
    val pulse = breathe(1400)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(col.copy(alpha = 0.12f)).border(1.dp, col.copy(alpha = 0.25f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(7.dp)
                .graphicsLayer { alpha = if (on || busy) 0.5f + 0.5f * pulse.value else 1f }
                .clip(CircleShape).background(col)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                on -> "ON"
                busy -> "…"
                else -> "OFF"
            },
            color = col, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
    }
}

/**
 * The scope: a dial of 72 ticks around a power disc. Idle = dim ticks; connecting = a bright comet
 * sweeping round; connected = all ticks lit with a slow radar sweep, and the last control pings
 * plotted as blips (further out = faster).
 */
@Composable
private fun ScopeDial(state: ConnState, pings: List<Long>, onClick: () -> Unit) {
    val c = ray
    val connected = state == ConnState.CONNECTED
    val busy = state == ConnState.CONNECTING || state == ConnState.STOPPING
    val col by animateColorAsState(
        targetValue = when {
            connected -> Good
            busy -> Mid
            else -> c.accent
        },
        animationSpec = tween(500),
        label = "dialColor"
    )
    val energy by animateFloatAsState(
        when {
            connected -> 1f
            busy -> 0.85f
            else -> 0f
        },
        tween(700), label = "energy"
    )
    val inf = rememberInfiniteTransition(label = "dial")
    val sweep by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (busy) 1300 else 6500, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val faint = c.faint
    val idleTick = c.hint.copy(alpha = 0.28f)
    val disc by animateColorAsState(if (connected) Good else c.raised, tween(400), label = "disc")
    val shown = pings.takeLast(24)
    val blipColors = shown.map { delayColor(it) }

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ctr = Offset(cx, cy)
            val rr = size.minDimension / 2f

            val glowA = (0.05f + 0.14f * energy) * (0.75f + 0.25f * pulse)
            drawCircle(
                brush = Brush.radialGradient(listOf(col.copy(alpha = glowA), Color.Transparent), center = ctr, radius = rr),
                radius = rr, center = ctr
            )

            val outer = rr * 0.93f
            drawCircle(faint, radius = outer, center = ctr, style = Stroke(1.dp.toPx()))

            val n = 72
            for (i in 0 until n) {
                val deg = i * 360f / n
                val d = ((sweep - deg) % 360f + 360f) % 360f
                val tail = (1f - d / (if (busy) 110f else 150f)).coerceAtLeast(0f)
                val k = when {
                    busy -> tail
                    connected -> 0.45f + 0.55f * tail
                    else -> 0f
                } * energy
                val long = i % 6 == 0
                val r1 = outer - 6.dp.toPx()
                val r2 = r1 - (if (long) 12.dp else 7.dp).toPx()
                val a = Math.toRadians(deg.toDouble() - 90.0)
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(
                    color = lerp(idleTick, col, k.coerceIn(0f, 1f)),
                    start = Offset(cx + ca * r1, cy + sa * r1),
                    end = Offset(cx + ca * r2, cy + sa * r2),
                    strokeWidth = (if (long) 2.dp else 1.5.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }

            val inner = rr * 0.52f
            drawCircle(faint, radius = inner, center = ctr, style = Stroke(1.dp.toPx()))

            if (connected && shown.isNotEmpty()) {
                val band0 = inner + 8.dp.toPx()
                val band1 = outer - 28.dp.toPx()
                shown.forEachIndexed { i, p ->
                    val a = Math.toRadians(-90.0 + i * (360.0 / 24))
                    val q = if (p < 0) 0f else (1f - p.toFloat() / 1200f).coerceIn(0.05f, 1f)
                    val r = band0 + (band1 - band0) * q
                    val last = i == shown.lastIndex
                    drawCircle(
                        color = blipColors[i],
                        radius = (if (last) 4.dp else 2.5.dp).toPx(),
                        center = Offset(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r),
                        alpha = if (last) 1f else 0.65f
                    )
                }
            }
        }
        Box(
            Modifier.size(120.dp)
                .bounceClick(enabled = !busy, onClick = onClick)
                .clip(CircleShape)
                .background(disc)
                .border(1.5.dp, col.copy(alpha = if (connected) 0f else 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.5f) + fadeIn(tween(160))) togetherWith
                        (scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(120)))
                },
                label = "dialIcon"
            ) { on ->
                if (on) {
                    val p = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                    DrawnIcon(listOf(SHIELD_PATH, CHECK_SMALL_PATH), p.value, InkOn, Modifier.size(46.dp), strokeWidth = 2.1f)
                } else {
                    Icon(RayIcons.Power, contentDescription = "Подключить", tint = col, modifier = Modifier.size(44.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerPicker(server: Server?, modifier: Modifier, onClick: () -> Unit) {
    val c = ray
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .bounceClick(onClick = onClick).card(c, 22.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (server == null) {
            IconTile(RayIcons.Plus, c.accent, 44.dp, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Добавьте сервер", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("подписка или ссылки vless / vmess / trojan / ss / hy2", color = c.hint, fontSize = 12.sp, maxLines = 1)
            }
        } else {
            FlagBox(server.flagEmoji(), 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CapsLabel("Сервер")
                Text(
                    server.cleanName(), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tag(server.protocolLabel().uppercase())
                    Tag(server.network.uppercase())
                    Tag(server.securityLabel().uppercase())
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                LatencyText(server.delay, 18.sp)
                Spacer(Modifier.height(4.dp))
                SignalBars(server.delay)
            }
        }
        Icon(RayIcons.Chevron, contentDescription = null, tint = c.hint.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp).size(16.dp))
    }
}

@Composable
private fun LiveBento(vpn: VpnStatus, modifier: Modifier) {
    val c = ray
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("Загрузка", Fmt.speed(vpn.downSpeed), "пик ${Fmt.speed(vpn.peakDown)}", RayIcons.ArrowDown, Good, Modifier.weight(1f))
            MetricTile("Отдача", Fmt.speed(vpn.upSpeed), "пик ${Fmt.speed(vpn.peakUp)}", RayIcons.ArrowUp, Blue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).card(c, 20.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(RayIcons.Activity, delayColor(vpn.livePing), 26.dp, 15.dp)
                    Spacer(Modifier.width(8.dp))
                    CapsLabel("Пинг")
                }
                Spacer(Modifier.height(10.dp))
                TickerText(Fmt.ms(vpn.livePing), delayColor(vpn.livePing), 20.sp, FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Sparkline(vpn.pingHistory, delayColor(vpn.livePing), Modifier.fillMaxWidth().height(18.dp))
            }
            MetricTile(
                "Трафик", Fmt.bytes(vpn.totalDown + vpn.totalUp),
                "↓ ${Fmt.bytes(vpn.totalDown)} · ↑ ${Fmt.bytes(vpn.totalUp)}", RayIcons.Layers, Purple, Modifier.weight(1f)
            )
        }
        Column(Modifier.fillMaxWidth().card(c, 20.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsLabel("Скорость · 60 с", Modifier.weight(1f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Good))
                Text(" ↓", color = c.hint, fontSize = 11.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Blue))
                Text(" ↑", color = c.hint, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            SpeedGraph(vpn.history)
        }
    }
}

@Composable
private fun QualityGroup(history: List<Long>, modifier: Modifier) {
    val st = Repo.settings
    val okList = history.filter { it >= 0 }
    val fails = history.size - okList.size
    val avg = if (okList.isEmpty()) -1L else okList.average().toLong()
    val jitter = if (okList.size < 2) -1L else okList.zipWithNext { a, b -> abs(a - b) }.average().toLong()
    val loss = if (history.isEmpty()) 0 else fails * 100 / history.size
    val (grade, gradeColor) = when {
        okList.isEmpty() || loss > 20 || avg > st.okPing -> "ПЛОХО" to Bad
        avg > st.goodPing || jitter > 120 || loss > 0 -> "СРЕДНЕ" to Mid
        else -> "ОТЛИЧНО" to Good
    }
    Group("Качество канала", modifier = modifier, trailing = { Tag(grade, gradeColor) }) {
        KV("Средний пинг", if (avg < 0) "—" else "$avg ms")
        KV("Джиттер", if (jitter < 0) "—" else "$jitter ms")
        KV("Потери", "$loss%", if (fails > 0) Bad else Good)
        KV("Последние", history.takeLast(8).joinToString(" ") { if (it < 0) "✕" else "$it" }, mono = true)
        KV("Интервал проверки", "${st.livePingInterval} с")
    }
}

@Composable
private fun MetricTile(label: String, value: String, foot: String, icon: ImageVector, color: Color, modifier: Modifier) {
    val c = ray
    Column(modifier.card(c, 20.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, 26.dp, 15.dp)
            Spacer(Modifier.width(8.dp))
            CapsLabel(label)
        }
        Spacer(Modifier.height(10.dp))
        TickerText(value, c.text, 20.sp, FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(foot, color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color,
    modifier: Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val c = ray
    val spin = if (busy) spinAngle() else null
    Column(
        modifier.bounceClick(enabled = enabled && !busy, onClick = onClick).card(c, 20.dp).padding(14.dp)
    ) {
        IconTile(
            icon, color, 36.dp, 19.dp,
            iconModifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f }
        )
        Spacer(Modifier.height(12.dp))
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            desc, color = if (highlight) color else c.hint, fontSize = 12.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum
        )
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier) {
    val bad = Bad
    val faint = ray.faint
    Canvas(modifier) {
        drawLine(faint, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        if (values.size < 2) return@Canvas
        val okMax = values.filter { it >= 0 }.maxOrNull() ?: 1L
        val max = okMax.coerceAtLeast(1L).toFloat()
        val step = size.width / (values.size - 1)
        val p = Path()
        var started = false
        values.forEachIndexed { i, v ->
            val x = i * step
            if (v < 0) {
                drawCircle(bad, 2.dp.toPx(), Offset(x, size.height - 2.dp.toPx()))
                started = false
            } else {
                val y = size.height - (v / max) * size.height * 0.85f - 1f
                if (!started) {
                    p.moveTo(x, y)
                    started = true
                } else {
                    p.lineTo(x, y)
                }
            }
        }
        drawPath(p, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SpeedGraph(history: List<Pair<Long, Long>>) {
    val c = ray
    val downColor = Good
    val upColor = Blue
    val gridColor = c.faint
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        for (k in 1..3) {
            val y = size.height * k / 4f
            drawLine(gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
        }
        if (history.size < 2) return@Canvas
        val max = history.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1L).toFloat()
        val stepX = size.width / (history.size - 1)
        fun line(sel: (Pair<Long, Long>) -> Long): Path {
            val p = Path()
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (sel(v) / max) * size.height * 0.9f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        val down = line { it.second }
        val area = Path().apply {
            addPath(down)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(listOf(downColor.copy(alpha = 0.28f), downColor.copy(alpha = 0f)))
        )
        drawPath(down, color = downColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line { it.first }, color = upColor, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
