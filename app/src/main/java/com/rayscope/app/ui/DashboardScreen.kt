package com.rayscope.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val orbColor by animateColorAsState(
        targetValue = when {
            connected -> Good
            busy -> Orange
            else -> c.accent
        },
        label = "orb"
    )
    val stateText = when (vpn.state) {
        ConnState.CONNECTED -> "Подключено"
        ConnState.CONNECTING -> "Подключение…"
        ConnState.STOPPING -> "Отключение…"
        ConnState.STOPPED -> "Не подключено"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("RayScope", subtitle = if (connected) "Защищено · ${Fmt.duration(now - vpn.since)}" else stateText)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                .glass(c, RoundedCornerShape(26.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectOrb(orbColor, enabled = !busy, onClick = onToggle)
            Spacer(Modifier.height(10.dp))
            Text(stateText, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (connected) Fmt.duration(now - vpn.since) else "Нажмите на кнопку",
                color = if (connected) Good else c.hint, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            if (vpn.error.isNotEmpty()) {
                Text("⚠ ${vpn.error}", color = Bad, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            ServerPill(server, onGoServers)
        }

        if (connected) {
            Section("Статистика") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("↓ Загрузка", Fmt.speed(vpn.downSpeed), Good)
                    Stat("↑ Отдача", Fmt.speed(vpn.upSpeed), Blue)
                    Stat("Пинг", Fmt.ms(vpn.livePing), delayColor(vpn.livePing))
                }
                RowDivider()
                Row(Modifier.fillMaxWidth()) {
                    Stat("Получено", Fmt.bytes(vpn.totalDown))
                    Stat("Отправлено", Fmt.bytes(vpn.totalUp))
                    Stat("Пик ↓", Fmt.speed(vpn.peakDown))
                }
                Spacer(Modifier.height(4.dp))
                SpeedGraph(vpn.history)
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("● загрузка   ", color = Good, fontSize = 11.sp)
                    Text("● отдача", color = Blue, fontSize = 11.sp)
                }
            }
            if (vpn.pingHistory.isNotEmpty()) {
                Section("Контрольный пинг") {
                    val okList = vpn.pingHistory.filter { it >= 0 }
                    val fails = vpn.pingHistory.size - okList.size
                    KV("Последние", vpn.pingHistory.takeLast(8).joinToString(" ") { if (it < 0) "✕" else "$it" })
                    KV("Средний", if (okList.isEmpty()) "—" else "${okList.average().toLong()} ms")
                    KV("Джиттер", if (okList.size < 2) "—" else "${okList.zipWithNext { a, b -> abs(a - b) }.average().toLong()} ms")
                    KV("Потери", "${fails * 100 / vpn.pingHistory.size}%", if (fails > 0) Bad else Good)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                "Проверить IP",
                onClick = {
                    onGoIp()
                    scope.launch { IpCheckModel.run() }
                },
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                if (speedRunning) "Тест…" else "Скорость",
                enabled = !speedRunning,
                onClick = {
                    speedRunning = true
                    speedText = "Измеряю…"
                    val via = connected
                    scope.launch {
                        speedText = try {
                            val r = Tester.speedTest(via) { v -> speedText = String.format(Locale.US, "%.1f Мбит/с…", v) }
                            String.format(Locale.US, "%.1f Мбит/с", r)
                        } catch (e: Exception) {
                            "Ошибка: ${e.message}"
                        }
                        speedRunning = false
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        GlassButton(
            if (bestRunning) "Ищу лучший сервер…" else "⚡ Лучший сервер",
            primary = true,
            enabled = !bestRunning && !busy,
            onClick = {
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
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
        )

        val sp = speedText
        if (sp != null) {
            Section("Тест скорости " + (if (connected) "· через VPN" else "· напрямую")) {
                Text(sp, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                Text("speed.cloudflare.com, до 10 сек", color = c.hint, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        if (server != null) {
            Section("Подключение") {
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
        Spacer(Modifier.height(BottomInset))
    }
}

@Composable
private fun ConnectOrb(color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(150.dp).clip(CircleShape).background(
            Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f))))
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.45f),
                        0.45f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.15f)
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.55f))),
                    CircleShape
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text("⏻", fontSize = 44.sp, color = Color.White)
        }
    }
}

@Composable
private fun ServerPill(server: Server?, onClick: () -> Unit) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (server == null) {
            Text("Добавьте сервер", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        } else {
            FlagBox(server.flagEmoji(), 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(server.cleanName(), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${server.protocolLabel()} · ${server.network} · ${server.securityLabel()}", color = c.hint, fontSize = 12.sp, maxLines = 1)
            }
            DelayBadge(server.delay)
        }
        Text("  ›", color = c.hint, fontSize = 18.sp)
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, color: Color = Color.Unspecified) {
    val c = ray
    Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
        Text(label, color = c.hint, fontSize = 11.sp, maxLines = 1)
        Text(
            value, color = if (color == Color.Unspecified) c.text else color,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

@Composable
fun SpeedGraph(history: List<Pair<Long, Long>>) {
    val c = ray
    val downColor = Good
    val upColor = Blue
    val gridColor = c.divider
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        drawLine(gridColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2))
        drawLine(gridColor, start = Offset(0f, size.height - 1f), end = Offset(size.width, size.height - 1f))
        if (history.size < 2) return@Canvas
        val max = history.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1L).toFloat()
        val stepX = size.width / (history.size - 1)
        fun line(sel: (Pair<Long, Long>) -> Long): Path {
            val p = Path()
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (sel(v) / max) * size.height * 0.92f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        drawPath(line { it.second }, color = downColor, style = Stroke(width = 4f))
        drawPath(line { it.first }, color = upColor, style = Stroke(width = 3f))
    }
}
