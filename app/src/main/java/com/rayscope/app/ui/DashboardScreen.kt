@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayscope.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
    val ringColor by animateColorAsState(
        targetValue = when {
            connected -> Good
            busy -> Mid
            else -> Idle
        },
        label = "ring"
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("RayScope", fontWeight = FontWeight.Bold) },
            actions = {
                if (connected) {
                    Text(Fmt.duration(now - vpn.since), modifier = Modifier.padding(end = 16.dp), color = Good)
                }
            }
        )

        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.14f))
                    .border(4.dp, ringColor, CircleShape)
                    .clickable(enabled = !busy) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏻", fontSize = 54.sp, color = ringColor)
                    Text(
                        when (vpn.state) {
                            ConnState.CONNECTED -> "Подключено"
                            ConnState.CONNECTING -> "Подключение…"
                            ConnState.STOPPING -> "Отключение…"
                            ConnState.STOPPED -> "Нажмите, чтобы подключиться"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            }
        }

        SectionCard(title = "Сервер") {
            if (server == null) {
                Text("Нет серверов", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onGoServers) { Text("Добавить →") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.flagEmoji().ifEmpty { "🌐" }, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.cleanName(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${server.protocolLabel()} · ${server.network} · ${server.securityLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DelayBadge(server.delay)
                }
                TextButton(onClick = onGoServers) { Text("Сменить сервер →") }
            }
            if (vpn.error.isNotEmpty()) {
                Text("⚠ ${vpn.error}", color = Bad, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (connected) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("↓ Загрузка", Fmt.speed(vpn.downSpeed), Modifier.weight(1f), Good)
                StatTile("↑ Отдача", Fmt.speed(vpn.upSpeed), Modifier.weight(1f), Blue)
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Получено", Fmt.bytes(vpn.totalDown), Modifier.weight(1f))
                StatTile("Отправлено", Fmt.bytes(vpn.totalUp), Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Пинг (live)", Fmt.ms(vpn.livePing), Modifier.weight(1f), delayColor(vpn.livePing))
                StatTile("Пик ↓", Fmt.speed(vpn.peakDown), Modifier.weight(1f))
            }
            SectionCard(title = "Трафик за 60 сек") {
                SpeedGraph(vpn.history)
                Row {
                    Text("— загрузка  ", color = Good, style = MaterialTheme.typography.labelSmall)
                    Text("— отдача", color = Blue, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (vpn.pingHistory.isNotEmpty()) {
                SectionCard(title = "Контрольный пинг (каждые 30 сек)") {
                    val okList = vpn.pingHistory.filter { it >= 0 }
                    val fails = vpn.pingHistory.size - okList.size
                    KV("Последние", vpn.pingHistory.takeLast(10).joinToString("  ") { if (it < 0) "✕" else "$it" })
                    KV("Средний", if (okList.isEmpty()) "—" else "${okList.average().toLong()} ms")
                    KV(
                        "Джиттер",
                        if (okList.size < 2) "—" else "${okList.zipWithNext { a, b -> abs(a - b) }.average().toLong()} ms"
                    )
                    KV("Потери", "${fails * 100 / vpn.pingHistory.size}%", if (fails > 0) Bad else Good)
                }
            }
        }

        if (server != null) {
            SectionCard(title = "Детали подключения") {
                KV("Адрес", "${server.address}:${server.port}")
                KV("Транспорт", server.network + (if (server.path.isNotEmpty()) "  ${server.path}" else ""))
                KVIf("SNI", server.sni)
                KV("Защита", server.securityLabel())
                val exit = ip.vpn
                if (connected && ip.wasConnected && exit != null) {
                    KV("Выходной IP", "${Fmt.flag(exit.countryCode)} ${exit.ip}")
                }
                KV("Локальный SOCKS5", "127.0.0.1:${XrayConfig.SOCKS_PORT}")
                KV("Локальный HTTP", "127.0.0.1:${XrayConfig.HTTP_PORT}")
                if (vpn.switches > 0) KV("Переключений за сессию", "${vpn.switches}")
                KV("Ядро", CoreManager.version())
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onGoIp()
                    scope.launch { IpCheckModel.run() }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Проверить IP") }
            OutlinedButton(
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
            ) { Text(if (speedRunning) "Тест…" else "Тест скорости") }
        }
        Button(
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) { Text(if (bestRunning) "Ищу лучший сервер…" else "⚡ Подключиться к лучшему серверу") }

        val sp = speedText
        if (sp != null) {
            SectionCard(title = "Тест скорости " + (if (connected) "(через VPN)" else "(напрямую)")) {
                Text(sp, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Загрузка с speed.cloudflare.com, до 10 сек", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SpeedGraph(history: List<Pair<Long, Long>>) {
    val downColor = Good
    val upColor = Blue
    val gridColor = Color.White.copy(alpha = 0.06f)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        drawLine(gridColor, start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2))
        if (history.size < 2) return@Canvas
        val max = history.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1L).toFloat()
        val stepX = size.width / (history.size - 1)
        fun line(sel: (Pair<Long, Long>) -> Long): Path {
            val p = Path()
            history.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (sel(v) / max) * size.height
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        drawPath(line { it.second }, color = downColor, style = Stroke(width = 4f))
        drawPath(line { it.first }, color = upColor, style = Stroke(width = 3f))
    }
}
