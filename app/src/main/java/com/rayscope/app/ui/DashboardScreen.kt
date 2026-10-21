package com.rayscope.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.rayscope.app.core.XrayConfig
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

@Composable
fun DashboardScreen(onToggle: () -> Unit, onGoServers: () -> Unit, onGoIp: () -> Unit) {
    val c = ray
    val vpn by VpnState.status.collectAsState()
    val servers by Repo.servers.collectAsState()
    val selectedId by Repo.selectedId.collectAsState()
    val ip by IpCheckModel.state.collectAsState()
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
    val scrolled by remember { derivedStateOf { scroll.value > 140 } }
    val stateText = when (vpn.state) {
        ConnState.CONNECTED -> "Подключено"
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
                "Ошибка: ${e.message}"
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
            LargeTitle("RayScope", if (connected) "Защищено · ${server?.cleanName() ?: ""}" else stateText)

            // ---- hero
            Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 22.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ConnectOrb(vpn.state, onToggle)
                    Spacer(Modifier.height(4.dp))
                    TickerText(stateText, c.text, 19.sp, FontWeight.Bold)
                    Text(
                        when {
                            connected -> "Сессия ${Fmt.duration(now - vpn.since)}"
                            busy -> "Подождите…"
                            else -> "Нажмите, чтобы подключиться"
                        },
                        color = if (connected) Good else c.hint, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    if (vpn.error.isNotEmpty()) {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(RayIcons.Alert, contentDescription = null, tint = Bad, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(vpn.error, color = Bad, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                RowDivider()
                ServerLine(server, onGoServers)
            }

            // ---- quick actions
            Row(
                Modifier.appear(1).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTile(RayIcons.Globe, "IP", Blue, Modifier.weight(1f)) {
                    onGoIp()
                    scope.launch { IpCheckModel.run() }
                }
                QuickTile(RayIcons.Gauge, "Скорость", Orange, Modifier.weight(1f), busy = speedRunning, onClick = runSpeed)
                QuickTile(RayIcons.Bolt, "Лучший", Good, Modifier.weight(1f), busy = bestRunning, enabled = !busy, onClick = runBest)
                QuickTile(RayIcons.Servers, "Серверы", Purple, Modifier.weight(1f), onClick = onGoServers)
            }

            val sp = speedText
            if (sp != null) {
                Row(
                    Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 18.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(RayIcons.Gauge, Orange, 36.dp, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        TickerText(sp, c.text, 20.sp, FontWeight.Bold)
                        Text(
                            "speed.cloudflare.com · " + (if (connected) "через VPN" else "напрямую"),
                            color = c.hint, fontSize = 12.sp
                        )
                    }
                }
            }

            // ---- live stats
            if (connected) {
                Column(Modifier.appear(2).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 18.dp).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(RayIcons.ArrowDown, "Загрузка", Fmt.speed(vpn.downSpeed), Good)
                        StatCell(RayIcons.ArrowUp, "Отдача", Fmt.speed(vpn.upSpeed), Blue)
                        StatCell(RayIcons.Activity, "Пинг", Fmt.ms(vpn.livePing), delayColor(vpn.livePing))
                    }
                    Spacer(Modifier.height(10.dp))
                    SpeedGraph(vpn.history)
                    Spacer(Modifier.height(10.dp))
                    RowDivider()
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        MiniStat("Получено", Fmt.bytes(vpn.totalDown))
                        MiniStat("Отправлено", Fmt.bytes(vpn.totalUp))
                        MiniStat("Пик ↓", Fmt.speed(vpn.peakDown))
                    }
                }
                if (vpn.pingHistory.isNotEmpty()) {
                    val okList = vpn.pingHistory.filter { it >= 0 }
                    val fails = vpn.pingHistory.size - okList.size
                    Section("Контрольный пинг", modifier = Modifier.appear(3)) {
                        KV("Последние", vpn.pingHistory.takeLast(8).joinToString(" ") { if (it < 0) "✕" else "$it" })
                        KV("Средний", if (okList.isEmpty()) "—" else "${okList.average().toLong()} ms")
                        KV("Джиттер", if (okList.size < 2) "—" else "${okList.zipWithNext { a, b -> abs(a - b) }.average().toLong()} ms")
                        KV("Потери", "${fails * 100 / vpn.pingHistory.size}%", if (fails > 0) Bad else Good)
                    }
                }
            }

            if (server != null) {
                Section("Подключение", modifier = Modifier.appear(4)) {
                    KV("Адрес", "${server.address}:${server.port}")
                    KV("Транспорт", server.network + (if (server.path.isNotEmpty()) " ${server.path}" else ""))
                    KVIf("SNI", server.sni)
                    KV("Защита", server.securityLabel())
                    val exit = ip.vpn
                    if (connected && ip.wasConnected && exit != null) {
                        KV("Выходной IP", "${Fmt.flag(exit.countryCode)} ${exit.ip}")
                    }
                    KV("SOCKS5 / HTTP", "127.0.0.1:${XrayConfig.SOCKS_PORT} / ${XrayConfig.HTTP_PORT}")
                    if (vpn.switches > 0) KV("Переключений", "${vpn.switches}")
                    KV("Ядро", CoreManager.version())
                }
            }
            TabBottomSpacer()
        }
    }
}

/** Power button with motion: spinning arc while connecting, breathing halo and drawn shield when connected. */
@Composable
private fun ConnectOrb(state: ConnState, onClick: () -> Unit) {
    val c = ray
    val connected = state == ConnState.CONNECTED
    val busy = state == ConnState.CONNECTING || state == ConnState.STOPPING
    val color by animateColorAsState(
        targetValue = when {
            connected -> Good
            busy -> Orange
            else -> c.accent
        },
        animationSpec = tween(400),
        label = "orbColor"
    )
    val spin = if (busy) spinAngle(1000) else null
    val pulse = if (connected) breathe() else null
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(138.dp)
                .graphicsLayer {
                    val b = pulse?.value ?: 0f
                    val s = 1f + 0.06f * b
                    scaleX = s
                    scaleY = s
                    alpha = 0.55f + 0.45f * b
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
        )
        if (spin != null) {
            Canvas(Modifier.size(138.dp).graphicsLayer { rotationZ = spin.value }) {
                drawArc(
                    color = color, startAngle = 0f, sweepAngle = 100f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier.size(104.dp).bounceClick(enabled = !busy, onClick = onClick).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = connected,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.5f) + fadeIn(tween(160))) togetherWith
                        (scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(120)))
                },
                label = "orbIcon"
            ) { on ->
                if (on) {
                    val p = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                    DrawnIcon(listOf(SHIELD_PATH, CHECK_SMALL_PATH), p.value, Color.White, Modifier.size(46.dp), strokeWidth = 2f)
                } else {
                    Icon(RayIcons.Power, contentDescription = "Подключить", tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerLine(server: Server?, onClick: () -> Unit) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (server == null) {
            IconBadge(RayIcons.Plus, c.accent, 38.dp, 20.dp)
            Spacer(Modifier.width(12.dp))
            Text("Добавьте сервер", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        } else {
            FlagBox(server.flagEmoji(), 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.cleanName(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.protocolLabel()} · ${server.network} · ${server.securityLabel()}", color = c.hint, fontSize = 12.sp, maxLines = 1)
            }
            DelayBadge(server.delay)
        }
        Icon(RayIcons.Chevron, contentDescription = null, tint = c.hint.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = ray
    val spin = if (busy) spinAngle() else null
    Column(
        modifier.bounceClick(enabled = enabled && !busy, onClick = onClick).card(c, 18.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(
            icon, color, 38.dp, 20.dp,
            iconModifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f }
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun RowScope.StatCell(icon: ImageVector, label: String, value: String, color: Color) {
    val c = ray
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = c.hint, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        TickerText(value, c.text, 17.sp)
    }
}

@Composable
private fun RowScope.MiniStat(label: String, value: String) {
    val c = ray
    Column(Modifier.weight(1f)) {
        Text(label, color = c.hint, fontSize = 11.sp, maxLines = 1)
        Text(value, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SpeedGraph(history: List<Pair<Long, Long>>) {
    val c = ray
    val downColor = Good
    val upColor = Blue
    val gridColor = c.divider
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        drawLine(gridColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2))
        drawLine(gridColor, start = Offset(0f, size.height - 1f), end = Offset(size.width, size.height - 1f))
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
        drawPath(area, color = downColor.copy(alpha = 0.12f))
        drawPath(down, color = downColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line { it.first }, color = upColor, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
