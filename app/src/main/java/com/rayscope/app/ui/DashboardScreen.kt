package com.rayscope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(onToggle: () -> Unit, onGoServers: () -> Unit, onGoIp: () -> Unit) {
    val c = ray
    val ui = look
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
    val scrolled by remember { derivedStateOf { scroll.value > 60 } }
    val working = servers.count { it.isWorking }

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
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RayMark(Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("RayScope", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                Spacer(Modifier.weight(1f))
                Text(
                    if (servers.isEmpty()) "нет серверов" else "$working из ${servers.size} работают",
                    color = c.hint, fontSize = 13.sp, style = TNum
                )
            }

            ConnectionCard(vpn, server, now, onToggle, onGoServers, Modifier.appear(0))

            if (connected) {
                LiveGroup(vpn, Modifier.appear(1))
                if (vpn.pingHistory.isNotEmpty()) QualityGroup(vpn.pingHistory, ui.dev, Modifier.appear(2))
            }

            Group("Действия", modifier = Modifier.appear(3)) {
                PrefRow(
                    RayIcons.Bolt, c.hint, "Лучший сервер", "Проверить все и подключиться к самому быстрому",
                    enabled = !busy && !bestRunning && servers.isNotEmpty(),
                    onClick = runBest,
                    trailing = if (bestRunning) ({ BusyIcon() }) else null
                )
                PrefRow(
                    RayIcons.Globe, c.hint, "Проверка IP", "Реальный адрес против адреса через VPN",
                    chevron = true,
                    onClick = {
                        onGoIp()
                        scope.launch { IpCheckModel.run() }
                    }
                )
                PrefRow(
                    RayIcons.Gauge, c.hint, "Тест скорости",
                    value = speedText ?: (if (connected) "через VPN" else "напрямую"),
                    enabled = !speedRunning,
                    onClick = runSpeed,
                    trailing = if (speedRunning) ({ BusyIcon() }) else null
                )
                PrefRow(
                    RayIcons.Refresh, c.hint, "Пинг всех",
                    value = if (testing) "${prog.first} из ${prog.second}" else Actions.modeLabel(),
                    enabled = !testing && servers.isNotEmpty(),
                    divider = false,
                    onClick = { scope.launch { Actions.testAll() } },
                    trailing = if (testing) ({ BusyIcon() }) else null
                )
            }

            // technical details: developer mode only
            if (ui.dev && server != null) {
                val tweaks = buildList {
                    if (st.muxEnabled) add("mux")
                    if (st.fragmentEnabled) add("fragment")
                    if (st.tcpFastOpen) add("tfo")
                    if (st.blockQuic) add("no-quic")
                    if (st.ipv6) add("ipv6")
                    if (st.allowLan) add("lan")
                    if (st.defaultFingerprint.isNotEmpty()) add("fp:" + st.defaultFingerprint)
                }
                Group("Подключение", modifier = Modifier.appear(4), trailing = { DevTag() }) {
                    if (tweaks.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tweaks.forEach { Tag(it) }
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

/** Status, server and the one button that matters. */
@Composable
private fun ConnectionCard(
    vpn: VpnStatus,
    server: Server?,
    now: Long,
    onToggle: () -> Unit,
    onGoServers: () -> Unit,
    modifier: Modifier,
) {
    val c = ray
    val connected = vpn.state == ConnState.CONNECTED
    val busy = vpn.state == ConnState.CONNECTING || vpn.state == ConnState.STOPPING
    val dot = when {
        connected -> Good
        busy -> Mid
        else -> c.hint.copy(alpha = 0.6f)
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).card(c).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(
                when (vpn.state) {
                    ConnState.CONNECTED -> "Подключено"
                    ConnState.CONNECTING -> "Подключение…"
                    ConnState.STOPPING -> "Отключение…"
                    ConnState.STOPPED -> "Не подключено"
                },
                color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f)
            )
            if (connected) {
                Text(Fmt.duration(now - vpn.since), color = c.hint, fontSize = 14.sp, style = TNum)
            }
        }
        if (vpn.error.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(RayIcons.Alert, contentDescription = null, tint = Bad, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(vpn.error, color = Bad, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        ServerRow(server, onGoServers)
        Spacer(Modifier.height(12.dp))
        PillButton(
            when (vpn.state) {
                ConnState.CONNECTED -> "Отключить"
                ConnState.CONNECTING -> "Отменить"
                ConnState.STOPPING -> "Отключение…"
                ConnState.STOPPED -> "Подключить"
            },
            onClick = onToggle,
            primary = vpn.state == ConnState.STOPPED,
            enabled = vpn.state != ConnState.STOPPING && server != null,
            icon = RayIcons.Power,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ServerRow(server: Server?, onClick: () -> Unit) {
    val c = ray
    val shape = c.shape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.raised).bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (server == null) {
            Icon(RayIcons.Plus, contentDescription = null, tint = c.hint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Добавить сервер", color = c.text, fontSize = 15.sp)
                Text("Подписка или ссылки vless, vmess, trojan, ss, hy2", color = c.hint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            FlagBox(server.flagEmoji(), 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.cleanName(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(server.protocolLabel(), server.network, server.securityLabel()).joinToString(" · ").lowercase(),
                    color = c.hint, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            LatencyText(server.delay, 15.sp)
        }
        Icon(RayIcons.Chevron, contentDescription = null, tint = c.hint.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

@Composable
private fun LiveGroup(vpn: VpnStatus, modifier: Modifier) {
    val c = ray
    Group("Сейчас", modifier = modifier) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Metric("Загрузка", Fmt.speed(vpn.downSpeed), "пик ${Fmt.speed(vpn.peakDown)}", Modifier.weight(1f))
            VLine()
            Metric("Отдача", Fmt.speed(vpn.upSpeed), "пик ${Fmt.speed(vpn.peakUp)}", Modifier.weight(1f))
        }
        Hairline(0.dp)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Metric("Пинг", Fmt.ms(vpn.livePing), null, Modifier.weight(1f), dot = delayColor(vpn.livePing)) {
                Sparkline(vpn.pingHistory, c.hint, Modifier.fillMaxWidth().height(16.dp).padding(top = 4.dp))
            }
            VLine()
            Metric(
                "Трафик", Fmt.bytes(vpn.totalDown + vpn.totalUp),
                "↓ ${Fmt.bytes(vpn.totalDown)}  ↑ ${Fmt.bytes(vpn.totalUp)}", Modifier.weight(1f)
            )
        }
        Hairline(0.dp)
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Скорость за минуту", color = c.hint, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Legend("загрузка", c.accent)
                Spacer(Modifier.width(12.dp))
                Legend("отдача", c.hint)
            }
            Spacer(Modifier.height(10.dp))
            SpeedGraph(vpn.history)
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    foot: String?,
    modifier: Modifier,
    dot: Color? = null,
    extra: @Composable () -> Unit = {},
) {
    val c = ray
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = c.hint, fontSize = 12.5.sp, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = TNum, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (foot != null) {
            Text(foot, color = c.hint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
        }
        extra()
    }
}

@Composable
private fun VLine() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(ray.stroke))
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 10.dp, height = 2.dp).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = ray.hint, fontSize = 11.5.sp)
    }
}

@Composable
private fun BusyIcon() {
    val spin = spinAngle()
    Icon(
        RayIcons.Refresh, contentDescription = null, tint = ray.hint,
        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = spin.value }
    )
}

@Composable
private fun QualityGroup(history: List<Long>, dev: Boolean, modifier: Modifier) {
    val st = Repo.settings
    val okList = history.filter { it >= 0 }
    val fails = history.size - okList.size
    val avg = if (okList.isEmpty()) -1L else okList.average().toLong()
    val jitter = if (okList.size < 2) -1L else okList.zipWithNext { a, b -> abs(a - b) }.average().toLong()
    val loss = if (history.isEmpty()) 0 else fails * 100 / history.size
    val (grade, gradeColor) = when {
        okList.isEmpty() || loss > 20 || avg > st.okPing -> "плохо" to Bad
        avg > st.goodPing || jitter > 120 || loss > 0 -> "средне" to Mid
        else -> "отлично" to Good
    }
    Group("Качество канала", modifier = modifier, trailing = { Tag(grade, gradeColor) }) {
        KV("Средний пинг", if (avg < 0) "—" else "$avg ms")
        KV("Джиттер", if (jitter < 0) "—" else "$jitter ms")
        KV("Потери", "$loss%", if (fails > 0) Bad else Color.Unspecified)
        if (dev) {
            KV("Последние", history.takeLast(8).joinToString(" ") { if (it < 0) "✕" else "$it" }, mono = true)
            KV("Интервал проверки", "${st.livePingInterval} с")
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier) {
    val bad = Bad
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val okMax = values.filter { it >= 0 }.maxOrNull() ?: 1L
        val max = okMax.coerceAtLeast(1L).toFloat()
        val step = size.width / (values.size - 1)
        val p = Path()
        var started = false
        values.forEachIndexed { i, v ->
            val x = i * step
            if (v < 0) {
                drawCircle(bad, 1.5.dp.toPx(), Offset(x, size.height - 1.5.dp.toPx()))
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
        drawPath(p, color, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Two thin lines on a faint grid: download in the accent colour, upload in grey. */
@Composable
fun SpeedGraph(history: List<Pair<Long, Long>>) {
    val c = ray
    val downColor = c.accent
    val upColor = c.hint
    val gridColor = c.stroke
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
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
        drawPath(line { it.first }, color = upColor, style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line { it.second }, color = downColor, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
