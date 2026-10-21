package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

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

/** Space at the bottom of every page so the floating tab bar never covers content. */
val TAB_SPACE = 110.dp

@Composable
fun HomeScreen(onGoServers: () -> Unit, onGoCheck: () -> Unit) {
    val st by Engine.status.collectAsState()
    val prefs by Store.prefs.collectAsState()
    val servers by Store.servers.collectAsState()
    val selId by Store.selectedId.collectAsState()
    val testing by Actions.testing.collectAsState()
    val prog by Actions.progress.collectAsState()
    val sel = remember(servers, selId) { Store.selected() }
    val connected = st.state == ConnState.CONNECTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = TAB_SPACE)
    ) {
        Header("Главная", if (connected) "Трафик идёт через " + st.serverName else "Готов к подключению") {
            StatusPill(st.state)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectCard(st, prefs)
                ServerPicker(sel, servers, onGoServers)
            }
            Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Metrics(st)
                SpeedGraph(st)
                QuickActions(testing, prog, onGoCheck)
                ProxyInfo(st, prefs)
                if (prefs.dev) DevConnection(st, sel)
            }
        }
    }
}

@Composable
fun StatusPill(s: ConnState) {
    val p = pal
    val col = when (s) {
        ConnState.CONNECTED -> Good
        ConnState.STOPPED -> p.hint
        else -> Mid
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(8.dp))
        Text(stateText(s), color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectCard(st: Status, prefs: Prefs) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    val busy = st.state == ConnState.CONNECTING || st.state == ConnState.STOPPING
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.state, st.since) {
        while (st.state == ConnState.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(
        Modifier.fillMaxWidth().card(p, 20.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Dial(st.state) { Engine.toggle() }
        Spacer(Modifier.height(18.dp))
        Text(stateText(st.state), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val err = !connected && !busy && st.error.isNotEmpty()
        val sub = when {
            connected -> Fmt.duration(now - st.since)
            busy -> st.serverName
            err -> st.error
            else -> "Нажмите на кнопку, чтобы подключиться"
        }
        Text(
            sub, color = if (err) Bad else p.hint, fontSize = 14.sp, style = TNum, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(20.dp))
        Segmented(
            listOf("Системный прокси", "Только локальный"),
            selected = if (prefs.mode == 1) 0 else 1,
            onSelect = { Engine.setMode(if (it == 0) 1 else 0) }
        )
        val port = if (connected) Engine.httpPort else prefs.httpPort
        Text(
            if (prefs.mode == 1) "Windows сам направит браузеры и программы через RayScope" else "Укажите 127.0.0.1:$port в настройках прокси нужной программы",
            color = p.hint, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * The scope: 72 ticks with a comet while connecting and a radar sweep in session.
 * The power button in the middle is a Liquid Glass lens over its own little backdrop
 * (glow, reticle, sweep), so the reticle visibly bends at the edge of the glass.
 */
@Composable
private fun Dial(state: ConnState, onClick: () -> Unit) {
    val p = pal
    val connected = state == ConnState.CONNECTED
    val busy = state == ConnState.CONNECTING || state == ConnState.STOPPING
    val target = when {
        connected -> Good
        busy -> Mid
        else -> p.hint
    }
    val col by animateColorAsState(target, tween(400))
    val angle: State<Float>? = if (connected || busy) spin(if (busy) 1300 else 5000) else null
    val idleTick = p.faint
    val local = remember { Backdrop() }
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        BackdropSource(local, Modifier.fillMaxSize(), background = p.surface) {
            Canvas(Modifier.fillMaxSize()) {
                val a = angle?.value ?: 0f
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                val c = Offset(cx, cy)
                val sw = 2.dp.toPx()
                val span = if (busy) 70f else 140f
                for (i in 0 until 72) {
                    val deg = i * 5f
                    val rad = Math.toRadians((deg - 90f).toDouble())
                    val long = i % 6 == 0
                    val r1 = r - (if (long) 16.dp.toPx() else 10.dp.toPx())
                    val r2 = r - 3.dp.toPx()
                    val tick = if (angle == null) {
                        idleTick
                    } else {
                        val diff = ((a - deg) % 360f + 360f) % 360f
                        if (diff < span) col.copy(alpha = 1f - diff / span * 0.8f) else idleTick
                    }
                    val ca = cos(rad).toFloat()
                    val sa = sin(rad).toFloat()
                    drawLine(
                        tick, Offset(cx + ca * r1, cy + sa * r1), Offset(cx + ca * r2, cy + sa * r2),
                        strokeWidth = sw, cap = StrokeCap.Round
                    )
                }
                val inner = r * 0.64f
                drawCircle(
                    Brush.radialGradient(
                        listOf(col.copy(alpha = 0.32f), col.copy(alpha = 0.10f), Color.Transparent),
                        center = c, radius = inner
                    ),
                    radius = inner, center = c
                )
                drawCircle(col.copy(alpha = 0.45f), radius = inner, center = c, style = Stroke(width = 1.5.dp.toPx()))
                if (connected && angle != null) {
                    rotate(a - 90f, c) {
                        drawArc(
                            Brush.sweepGradient(0.78f to Color.Transparent, 1f to col.copy(alpha = 0.38f), center = c),
                            startAngle = -80f, sweepAngle = 80f, useCenter = true,
                            topLeft = Offset(cx - inner, cy - inner), size = Size(inner * 2f, inner * 2f)
                        )
                    }
                }
                // reticle under the glass
                val ret = col.copy(alpha = 0.4f)
                val thin = 1.dp.toPx()
                drawCircle(ret, radius = r * 0.43f, center = c, style = Stroke(width = thin))
                drawCircle(ret, radius = r * 0.27f, center = c, style = Stroke(width = thin))
                val l1 = r * 0.1f
                val l2 = r * 0.6f
                drawLine(ret, Offset(cx - l2, cy), Offset(cx - l1, cy), strokeWidth = thin)
                drawLine(ret, Offset(cx + l1, cy), Offset(cx + l2, cy), strokeWidth = thin)
                drawLine(ret, Offset(cx, cy - l2), Offset(cx, cy - l1), strokeWidth = thin)
                drawLine(ret, Offset(cx, cy + l1), Offset(cx, cy + l2), strokeWidth = thin)
            }
        }
        GlassSurface(
            60.dp, Modifier.size(120.dp), backdrop = local,
            tint = if (p.glass == 0) p.raised else p.glassFill.copy(alpha = 0.22f),
            onClick = onClick
        ) {
            Icon(Ico.Power, contentDescription = if (connected || busy) "Отключить" else "Подключить", tint = col, modifier = Modifier.size(46.dp))
        }
    }
}

@Composable
private fun ServerPicker(sel: Server?, servers: List<Server>, onGoServers: () -> Unit) {
    val p = pal
    Section("Сервер", trailing = {
        Text(
            "Все серверы", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onGoServers).hand()
        )
    }) {
        if (sel == null) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Серверов пока нет", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Добавьте подписку или вставьте ссылки", color = p.hint, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Btn("Перейти к серверам", onGoServers, primary = true, icon = Ico.Plus)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onGoServers).hand().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlagBox(sel.countryCode(), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sel.cleanName(), color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(sel.protocolLabel())
                        Tag(sel.network.uppercase())
                        val sec = sel.security.lowercase()
                        Tag(sel.securityLabel(), if (sec == "tls" || sec == "reality") Good else Mid)
                    }
                }
                Spacer(Modifier.width(8.dp))
                LatencyText(sel.delay)
            }
            val best = servers.filter { it.isWorking && it.id != sel.id }.sortedBy { it.delay }.take(4)
            if (best.isNotEmpty()) {
                Line()
                Caps("Самые быстрые", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                best.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { pickServer(s.id) }.hand().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlagBox(s.countryCode(), 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.cleanName(), color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        SignalBars(s.delay)
                        Spacer(Modifier.width(8.dp))
                        LatencyText(s.delay, 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Metrics(st: Status) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("Загрузка", if (connected) Fmt.speed(st.down) else "—", Ico.Download, p.accent, Modifier.weight(1f))
        MetricTile("Отдача", if (connected) Fmt.speed(st.up) else "—", Ico.Upload, Purple, Modifier.weight(1f))
        val pc = if (connected) delayColor(st.livePing) else p.hint
        MetricTile("Пинг", if (connected) Fmt.ms(st.livePing) else "—", Ico.Activity, pc, Modifier.weight(1f)) {
            if (connected && st.pings.size > 1) Sparkline(st.pings, pc)
        }
        MetricTile("Трафик", if (connected) Fmt.bytes(st.totalDown + st.totalUp) else "—", Ico.Layers, Teal, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
    extra: @Composable () -> Unit = {},
) {
    val p = pal
    Column(modifier.card(p, 16.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 28.dp, 15.dp)
            Spacer(Modifier.width(8.dp))
            Caps(label)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = TNum, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        extra()
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val v = values.map { if (it < 0) 0f else it.toFloat() }
        val max = (v.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / (v.size - 1).coerceAtLeast(1)
        val path = Path()
        v.forEachIndexed { i, x ->
            val px = i * step
            val py = size.height - x / max * size.height * 0.9f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.series(v: List<Long>, max: Float, color: Color) {
    if (v.size < 2) return
    val step = size.width / (v.size - 1)
    val line = Path()
    val area = Path()
    area.moveTo(0f, size.height)
    v.forEachIndexed { i, x ->
        val px = i * step
        val py = size.height - (x.toFloat() / max) * size.height
        if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        area.lineTo(px, py)
    }
    area.lineTo((v.size - 1) * step, size.height)
    area.close()
    drawPath(area, color.copy(alpha = 0.12f))
    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = pal.hint, fontSize = 11.sp)
    }
}

@Composable
private fun SpeedGraph(st: Status) {
    val p = pal
    val hist = st.history
    val peak = hist.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L
    Section("Скорость", footer = if (hist.size >= 2) "Пик за минуту: " + Fmt.speed(peak) else null, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend("загрузка", p.accent)
            Spacer(Modifier.width(12.dp))
            Legend("отдача", Purple)
        }
    }) {
        Box(Modifier.fillMaxWidth().height(150.dp).padding(16.dp)) {
            if (hist.size < 2) {
                Text(
                    if (st.state == ConnState.CONNECTED) "Собираю данные…" else "График появится после подключения",
                    color = p.hint, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val grid = p.stroke
                val down = p.accent
                Canvas(Modifier.fillMaxSize()) {
                    val max = peak.coerceAtLeast(1024L).toFloat() * 1.1f
                    for (k in 1..3) {
                        val y = size.height * k / 4f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    series(hist.map { it.second }, max, down)
                    series(hist.map { it.first }, max, Purple)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(testing: Boolean, prog: Pair<Int, Int>, onGoCheck: () -> Unit) {
    val p = pal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile("Лучший сервер", "Проверить все и подключиться к быстрому", Ico.Bolt, Good, Modifier.weight(1f), enabled = !testing) {
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
            "Пинг всех",
            if (testing) "Проверено " + prog.first + " из " + prog.second else "Реальная задержка через ядро",
            Ico.Activity, p.accent, Modifier.weight(1f), enabled = !testing
        ) {
            Actions.go { Actions.testAll() }
        }
        ActionTile("Проверка IP", "Сменился ли адрес и страна", Ico.Radar, Blue, Modifier.weight(1f)) { onGoCheck() }
    }
}

@Composable
private fun ActionTile(
    title: String,
    sub: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = pal
    Column(
        modifier.alpha(if (enabled) 1f else 0.6f).card(p, 16.dp).clickable(enabled = enabled, onClick = onClick).hand().padding(14.dp)
    ) {
        IconTile(icon, tint, 34.dp, 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TNum)
    }
}

@Composable
fun CopyRow(k: String, v: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(v, color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconBtn(Ico.Copy, "Скопировать", {
            Clip.copy(v)
            Notice.show("Скопировано: $v")
        }, size = 32.dp)
    }
}

@Composable
private fun ProxyInfo(st: Status, prefs: Prefs) {
    val connected = st.state == ConnState.CONNECTED
    val http = if (connected) Engine.httpPort else prefs.httpPort
    val socks = if (connected) Engine.socksPort else prefs.socksPort
    Section(
        "Локальный прокси",
        footer = if (prefs.mode == 1) "Системный прокси Windows уже указывает на HTTP-порт. Для Telegram и игр берите SOCKS5." else "Укажите эти адреса в настройках прокси нужной программы."
    ) {
        CopyRow("HTTP", "127.0.0.1:$http")
        Line()
        CopyRow("SOCKS5", "127.0.0.1:$socks")
        if (st.systemProxy) {
            Line()
            KV("Системный прокси", "включён", Good)
        }
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
