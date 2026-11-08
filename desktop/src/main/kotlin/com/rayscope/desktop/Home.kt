package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** Bottom padding of every page. */
val TAB_SPACE = 32.dp

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
        Header("Главная", if (connected) "Трафик идёт через " + st.serverName else "${servers.size} серверов · Ctrl K для команд") {
            StatusPill(st.state)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.44f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ConnectCard(st, prefs, sel)
                ServerPicker(sel, servers, onGoServers)
            }
            Column(Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Metrics(st)
                SpeedGraph(st)
                QuickActions(testing, prog, onGoCheck)
                ProxyInfo(st, prefs)
                if (prefs.dev) DevConnection(st, sel)
            }
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
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).border(1.dp, p.stroke, shape).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(7.dp))
        Text(stateText(s), color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** Status first: what is going on, for how long, one button. No decoration around it. */
@Composable
private fun ConnectCard(st: Status, prefs: Prefs, sel: Server?) {
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
    val col by animateColorAsState(stateColor(st.state, p.hint), tween(250))
    Column(Modifier.fillMaxWidth().card(p, 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(9.dp))
                Text(stateText(st.state), color = p.hint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (connected) Fmt.duration(now - st.since) else if (busy) "…" else "00:00",
                color = if (connected) p.text else p.hint.copy(alpha = 0.55f),
                fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp, style = TNum, maxLines = 1
            )
            val err = !connected && !busy && st.error.isNotEmpty()
            val sub = when {
                connected || busy -> st.serverName
                err -> st.error
                sel != null -> "Готов подключиться к " + sel.cleanName()
                else -> "Сначала добавьте сервер"
            }
            Text(
                sub, color = if (err) Bad else p.hint, fontSize = 13.sp, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(18.dp))
            Btn(
                if (st.state == ConnState.STOPPED) "Подключить" else "Отключить",
                { Engine.toggle() },
                Modifier.fillMaxWidth().height(38.dp),
                primary = st.state == ConnState.STOPPED,
                icon = Ico.Power,
                enabled = st.state != ConnState.STOPPING && (sel != null || st.state != ConnState.STOPPED)
            )
        }
        Line()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Caps("Режим")
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf("Системный прокси", "Только локальный"),
                selected = if (prefs.mode == 1) 0 else 1,
                onSelect = { Engine.setMode(if (it == 0) 1 else 0) }
            )
            val port = if (connected) Engine.httpPort else prefs.httpPort
            Text(
                if (prefs.mode == 1) "Windows направит браузеры и программы через RayScope." else "Укажите 127.0.0.1:$port в настройках прокси нужной программы.",
                color = p.hint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ServerPicker(sel: Server?, servers: List<Server>, onGoServers: () -> Unit) {
    val p = pal
    Section("Сервер", trailing = {
        Text(
            "Все серверы", color = p.hint, fontSize = 12.5.sp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).press(onClick = onGoServers).padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }) {
        if (sel == null) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Серверов пока нет", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Добавьте подписку или вставьте ссылки vless://, vmess://, trojan://", color = p.hint, fontSize = 12.5.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(12.dp))
                Btn("Добавить сервер", onGoServers, icon = Ico.Plus)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().press(onClick = onGoServers).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlagBox(sel.countryCode(), 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sel.cleanName(), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Tag(sel.protocolLabel())
                        Tag(sel.network.uppercase())
                        val sec = sel.security.lowercase()
                        Tag(sel.securityLabel(), if (sec == "tls" || sec == "reality") Good else Mid)
                    }
                }
                Spacer(Modifier.width(8.dp))
                LatencyText(sel.delay, 15.sp)
            }
            val best = servers.filter { it.isWorking && it.id != sel.id }.sortedBy { it.delay }.take(4)
            if (best.isNotEmpty()) {
                Line()
                Caps("Самые быстрые", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                best.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().press { pickServer(s.id) }.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlagBox(s.countryCode(), 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.cleanName(), color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        SignalBars(s.delay)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (s.delay >= 0) "${s.delay} ms" else "—", color = p.hint, fontSize = 12.sp, style = TNum,
                            modifier = Modifier.widthIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** One card, four cells split by hairlines. */
@Composable
private fun Metrics(st: Status) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).card(p, 16.dp)) {
        Metric("Загрузка", if (connected) Fmt.speed(st.down) else "—", Modifier.weight(1f))
        VLine()
        Metric("Отдача", if (connected) Fmt.speed(st.up) else "—", Modifier.weight(1f))
        VLine()
        val pc = if (connected) delayColor(st.livePing) else p.hint
        Metric("Пинг", if (connected) Fmt.ms(st.livePing) else "—", Modifier.weight(1f)) {
            if (connected && st.pings.size > 1) Sparkline(st.pings, pc)
        }
        VLine()
        Metric("Трафик", if (connected) Fmt.bytes(st.totalDown + st.totalUp) else "—", Modifier.weight(1f))
    }
}

@Composable
private fun VLine() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(pal.stroke))
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, extra: @Composable () -> Unit = {}) {
    val p = pal
    Column(modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Caps(label)
        Spacer(Modifier.height(6.dp))
        Text(value, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = TNum, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(16.dp)) { extra() }
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val v = values.map { if (it < 0) 0f else it.toFloat() }
        val max = (v.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / (v.size - 1).coerceAtLeast(1)
        val path = Path()
        v.forEachIndexed { i, x ->
            val px = i * step
            val py = size.height - x / max * size.height * 0.9f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.series(v: List<Long>, max: Float, color: Color, fill: Boolean) {
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
    if (fill) drawPath(area, color.copy(alpha = 0.07f))
    drawPath(line, color, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(2.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = pal.hint, fontSize = 11.5.sp)
    }
}

@Composable
private fun SpeedGraph(st: Status) {
    val p = pal
    val hist = st.history
    val peak = hist.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L
    val up = p.hint.copy(alpha = 0.8f)
    Section("Скорость за минуту", footer = if (hist.size >= 2) "Пик: " + Fmt.speed(peak) else null, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend("загрузка", p.accent)
            Spacer(Modifier.width(14.dp))
            Legend("отдача", up)
        }
    }) {
        Box(Modifier.fillMaxWidth().height(140.dp).padding(16.dp)) {
            if (hist.size < 2) {
                Text(
                    if (st.state == ConnState.CONNECTED) "Собираю данные…" else "График появится после подключения",
                    color = p.hint, fontSize = 12.5.sp, modifier = Modifier.align(Alignment.Center)
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
                    series(hist.map { it.first }, max, up, fill = false)
                    series(hist.map { it.second }, max, down, fill = true)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(testing: Boolean, prog: Pair<Int, Int>, onGoCheck: () -> Unit) {
    Section("Действия") {
        ActionRow("Лучший сервер", "Проверить все и подключиться к самому быстрому", Ico.Bolt, enabled = !testing) {
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
        Line()
        ActionRow(
            "Пинг всех",
            if (testing) "Проверено " + prog.first + " из " + prog.second else "Реальная задержка через ядро",
            Ico.Activity, enabled = !testing
        ) {
            Actions.go { Actions.testAll() }
        }
        if (testing && prog.second > 0) {
            SlimProgress(prog.first.toFloat() / prog.second, Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp))
        }
        Line()
        ActionRow("Проверка IP", "Сменились ли адрес и страна", Ico.Radar, key = "Ctrl 3") { onGoCheck() }
    }
}

@Composable
private fun ActionRow(
    title: String,
    sub: String,
    icon: ImageVector,
    enabled: Boolean = true,
    key: String? = null,
    onClick: () -> Unit,
) {
    val p = pal
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).press(enabled, onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 13.5.sp, maxLines = 1)
            Text(sub, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
        }
        if (key != null) {
            Text(key, color = p.hint.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
        }
        Icon(Ico.Chevron, contentDescription = null, tint = p.hint.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
    }
}

@Composable
fun CopyRow(k: String, v: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(v, color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        IconBtn(Ico.Copy, "Скопировать", {
            Clip.copy(v)
            Notice.show("Скопировано: $v")
        }, size = 30.dp)
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
