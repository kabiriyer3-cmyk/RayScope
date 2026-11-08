package com.rayscope.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.fluid.LiveIcon
import com.rayscope.fluid.RowIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

object Clip {
    fun copy(s: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s), null)
        } catch (e: Exception) {
            Notice.show("Буфер обмена недоступен")
        }
    }

    fun paste(): String = try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
    } catch (e: Exception) {
        ""
    }
}

fun openFolder(f: File) {
    Actions.go {
        try {
            java.awt.Desktop.getDesktop().open(f)
        } catch (e: Exception) {
            Notice.show("Не удалось открыть папку: " + f.absolutePath)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = pal
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Btn(action, onConfirm, primary = true, color = Bad) },
        dismissButton = { Btn("Отмена", onDismiss, color = p.hint) },
        title = { Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, color = p.hint, fontSize = 13.5.sp, lineHeight = 19.sp) },
        containerColor = p.surface,
        shape = RoundedCornerShape(10.dp),
    )
}

// ------------------------------------------------------------------ IP check

@Composable
fun CheckScreen() {
    val cs by IpCheck.state.collectAsState()
    val st by Engine.status.collectAsState()
    val prefs by Store.prefs.collectAsState()
    LaunchedEffect(Unit) {
        if (IpCheck.state.value.checkedAt == 0L) Actions.go { IpCheck.run() }
    }
    ScrollColumn(
        PaddingValues(start = PAGE_PAD, end = PAGE_PAD, top = PAGE_TOP, bottom = TAB_SPACE),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Header("Проверка", "Виден ли настоящий IP") {
            Btn(
                if (cs.running) "Проверяю…" else "Проверить", { Actions.go { IpCheck.run() } },
                primary = true, icon = Ico.Refresh, enabled = !cs.running, busy = cs.running
            )
        }
        Verdict(cs)
        IpCard(
            "Через VPN",
            if (cs.wasConnected) cs.serverName else "VPN был выключен",
            cs.vpn,
            if (cs.wasConnected) cs.vpnErr else "Подключитесь и проверьте ещё раз",
            prefs.dev,
            Modifier.fillMaxWidth()
        )
        IpCard("Настоящий IP", "Так вас видят без VPN", cs.real, cs.realErr, prefs.dev, Modifier.fillMaxWidth())
        Services(cs, st)
        if (prefs.dev) History(cs)
    }
}

@Composable
private fun Verdict(cs: CheckState) {
    val p = pal
    val real = cs.real
    val vpn = cs.vpn
    val (col, title, sub) = when {
        cs.checkedAt == 0L -> Triple(p.hint, if (cs.running) "Проверяю…" else "Ещё не проверяли", "Сравним ваш настоящий IP с адресом через VPN")
        !cs.wasConnected -> Triple(Mid, "VPN выключен", "Сайты видят ваш настоящий адрес" + (real?.let { ": " + it.ip } ?: ""))
        vpn == null -> Triple(Bad, "VPN не отвечает", cs.vpnErr ?: "Запрос через туннель не прошёл")
        real != null && vpn.ip == real.ip -> Triple(Bad, "IP не изменился", "Трафик идёт мимо туннеля")
        else -> Triple(
            Good, "Всё работает",
            "Сайты видят " + listOf(vpn.country, vpn.city).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { vpn.ip }
        )
    }
    val icon = when (col) {
        Good -> Ico.Shield
        p.hint -> Ico.Radar
        else -> Ico.Alert
    }
    Column(Modifier.fillMaxWidth().card(p, 20.dp, col.copy(alpha = 0.35f)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, col, 38.dp, 19.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = p.hint, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (cs.checkedAt > 0) Text("Проверено: " + Fmt.ago(cs.checkedAt), color = p.hint, fontSize = 11.5.sp)
            }
        }
        if (cs.wasConnected && vpn != null) {
            val ok = real == null || vpn.ip != real.ip
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                RouteNode("Вы", real?.countryCode.orEmpty())
                RouteLink(if (ok) Good else Bad)
                RouteNode("Сервер", vpn.countryCode)
                RouteLink(if (ok) Good else Bad)
                RouteNode("Сайты", "")
            }
        }
    }
}

@Composable
private fun RouteNode(label: String, code: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FlagBox(code, 36.dp)
        Text(label, color = pal.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RouteLink(c: Color) {
    Box(Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp).width(36.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.copy(alpha = 0.7f)))
}

@Composable
private fun IpCard(title: String, sub: String, info: IpInfo?, err: String?, dev: Boolean, modifier: Modifier) {
    val p = pal
    Column(modifier.card(p, 18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagBox(info?.countryCode.orEmpty(), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Caps(title)
                Text(sub, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Line()
        if (info == null) {
            Text(err ?: "Нет данных", color = if (err != null) Bad else p.hint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else {
            Text(
                info.ip, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
            )
            KVIf("Страна", info.country)
            KVIf("Город", listOf(info.city, info.region).filter { it.isNotEmpty() }.distinct().joinToString(", "))
            KVIf("Провайдер", info.isp)
            if (dev) {
                KVIf("Организация", info.org)
                KVIf("ASN", info.asn, mono = true)
                KVIf("Часовой пояс", info.timezone)
                KV("Ответ API", info.source + " · " + info.latencyMs + " ms", mono = true)
            }
            val flags = buildList {
                if (info.hosting == true) add("IP хостинга")
                if (info.proxy == true) add("помечен как прокси")
            }
            if (flags.isNotEmpty()) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    flags.forEach { Tag(it, Mid) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Services(cs: CheckState, st: Status) {
    val p = pal
    val connected = st.state == ConnState.CONNECTED
    Section(
        "Сервисы",
        footer = "Время ответа главной страницы напрямую и через туннель.",
        trailing = {
            Btn(
                if (cs.sitesRunning) "Проверяю…" else "Проверить", { Actions.go { IpCheck.runSites() } },
                enabled = !cs.sitesRunning, icon = Ico.Globe, busy = cs.sitesRunning
            )
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Caps("Сервис", Modifier.weight(1f))
            Caps("Напрямую", Modifier.width(84.dp))
            Caps("VPN", Modifier.width(84.dp))
        }
        Line()
        val list = cs.sites.ifEmpty { IpCheck.SITES.map { Site(it.first, it.second) } }
        list.forEachIndexed { i, s ->
            if (i > 0) Line()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, color = p.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.width(84.dp)) { DelayBadge(s.direct) }
                Box(Modifier.width(84.dp)) {
                    if (cs.sites.isNotEmpty() && s.vpn == -2L && !connected) Text("VPN выкл", color = p.hint, fontSize = 12.sp) else DelayBadge(s.vpn)
                }
            }
        }
    }
}

@Composable
private fun History(cs: CheckState) {
    val p = pal
    Section("История проверок", trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DevTag()
            Spacer(Modifier.width(10.dp))
            Text(
                "Очистить", color = p.hint, fontSize = 12.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).press { IpCheck.state.update { it.copy(history = emptyList()) } }.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }) {
        if (cs.history.isEmpty()) {
            Text("Пусто", color = p.hint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else {
            cs.history.forEachIndexed { i, line ->
                if (i > 0) Line()
                // the log line starts with a check or a cross: draw our own icons instead of font glyphs
                val ok = line.startsWith("✓")
                val bad = line.startsWith("✕")
                val parts = line.removePrefix("✓").removePrefix("✕").trim().split("→")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ok || bad) {
                        Icon(if (ok) Ico.Check else Ico.Close, contentDescription = null, tint = if (ok) Good else Bad, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    parts.forEachIndexed { j, part ->
                        if (j > 0) {
                            Icon(Ico.Arrow, contentDescription = null, tint = p.hint, modifier = Modifier.padding(horizontal = 6.dp).size(12.dp))
                        }
                        Text(part.trim(), color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings

private val LOG_LEVELS = listOf("debug", "info", "warning", "error", "none")
private val DNS_PRESETS = listOf(
    "Cloudflare" to "1.1.1.1, 1.0.0.1",
    "Google" to "8.8.8.8, 8.8.4.4",
    "Quad9" to "9.9.9.9, 149.112.112.112",
    "AdGuard" to "94.140.14.14, 94.140.15.15",
    "Яндекс" to "77.88.8.8, 77.88.8.1",
    "DoH Cloudflare" to "https://1.1.1.1/dns-query",
)
private val QUERY = listOf("UseIP", "UseIPv4", "UseIPv6")
private val STRATEGIES = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val FP = listOf("", "chrome", "firefox", "safari", "edge", "ios", "random")
private val FP_LABELS = listOf("Нет", "Chrome", "Firefox", "Safari", "Edge", "iOS", "Случайный")
private val UA = listOf("v2rayN/7.10.0", "v2rayNG/1.9.30", "Happ/1.0", "clash-verge/v2.0.0", "sing-box/1.10.0")
private val UA_LABELS = listOf("v2rayN", "v2rayNG", "Happ", "Clash Verge", "sing-box")

@Composable
private fun PortRow(title: String, value: Int, onChange: (Int) -> Unit) {
    var t by remember(value) { mutableStateOf(value.toString()) }
    val n = t.toIntOrNull()
    val bad = n == null || n !in 1025..65535
    SettingRow(title, if (bad) "Порт от 1025 до 65535" else null) {
        Field(t, { s ->
            val f = s.filter { it.isDigit() }.take(5)
            t = f
            val v = f.toIntOrNull()
            if (v != null && v in 1025..65535 && v != value) onChange(v)
        }, "10809", Modifier.width(110.dp), mono = true)
    }
}

@Composable
private fun Swatch(c: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).border(1.dp, if (selected) p.text.copy(alpha = 0.35f) else p.stroke, shape)
            .background(if (selected) p.hover else Color.Transparent)
            .press(onClick = onClick).padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(name, color = if (selected) p.text else p.hint, fontSize = 12.5.sp, maxLines = 1)
        if (selected) {
            Spacer(Modifier.width(6.dp))
            LiveIcon(Ico.Check, p.text, Modifier.size(12.dp), appear = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenLog: () -> Unit) {
    val p = pal
    val prefs by Store.prefs.collectAsState()
    val st by Engine.status.collectAsState()
    val connected = st.state == ConnState.CONNECTED
    var dirty by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf(false) }
    var customDns by remember { mutableStateOf(false) }
    var customUa by remember { mutableStateOf(false) }
    var taps by remember { mutableStateOf(0) }
    var core by remember { mutableStateOf("…") }
    var autostart by remember { mutableStateOf(false) }
    var autostartBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { core = withContext(Dispatchers.IO) { Core.version() } }
    LaunchedEffect(Unit) { if (Autostart.available) autostart = withContext(Dispatchers.IO) { Autostart.isEnabled() } }
    LaunchedEffect(connected) { if (!connected) dirty = false }

    fun setCore(f: (Prefs) -> Prefs) {
        Store.setPrefs(f)
        dirty = true
    }

    ScrollBox(contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().padding(start = PAGE_PAD, end = PAGE_PAD, top = PAGE_TOP, bottom = TAB_SPACE),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header("Настройки", "Подключение, маршруты, запуск, вид")

            if (dirty && connected) {
                Row(
                    Modifier.fillMaxWidth().card(p, 16.dp, p.accent.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ico.Refresh, contentDescription = null, tint = p.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Применится после переподключения", color = p.text, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    Btn("Обновить", {
                        Engine.reconnect()
                        dirty = false
                    }, primary = true)
                }
            }

            Section("Оформление") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Тема", color = p.text, fontSize = 13.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("Системная", "Светлая", "Тёмная"), prefs.theme.coerceIn(0, 2), { i -> Store.setPrefs { it.copy(theme = i) } })
                }
                Line()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Акцент", color = p.text, fontSize = 13.5.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ACCENT_NAMES.forEachIndexed { i, n ->
                            Swatch(accentColor(i, p.dark), n, prefs.accent == i) { Store.setPrefs { it.copy(accent = i) } }
                        }
                    }
                }
                Line()
                SwitchRow("Компактный список серверов", "Больше серверов на экране", prefs.compact) { v -> Store.setPrefs { it.copy(compact = v) } }
            }

            Section("Подключение") {
                ChoiceRow(
                    "Режим", listOf("Системный прокси Windows", "TUN: весь трафик", "Только локальный прокси"),
                    when (prefs.mode) {
                        Mode.SYSTEM -> 0
                        Mode.TUN -> 1
                        else -> 2
                    },
                    "Системный прокси направит через RayScope браузеры и большинство программ. TUN заберёт весь трафик компьютера, включая игры, и попросит права администратора"
                ) {
                    Modes.request(
                        when (it) {
                            0 -> Mode.SYSTEM
                            1 -> Mode.TUN
                            else -> Mode.LOCAL
                        }
                    )
                }
                Line()
                PortRow("HTTP-порт", prefs.httpPort) { v -> setCore { it.copy(httpPort = v) } }
                Line()
                PortRow("SOCKS5-порт", prefs.socksPort) { v -> setCore { it.copy(socksPort = v) } }
                Line()
                SwitchRow("Доступ из локальной сети", "Телефон или ТВ смогут ходить через прокси этого ПК", prefs.allowLan) { v ->
                    setCore { it.copy(allowLan = v) }
                }
                Line()
                SwitchRow("Мультиплексирование (Mux)", "Меньше рукопожатий, сайты открываются быстрее. Не для XTLS Vision", prefs.muxEnabled) { v ->
                    setCore { it.copy(muxEnabled = v) }
                }
                if (prefs.muxEnabled) {
                    Line()
                    SliderRow("Потоков Mux", prefs.muxConcurrency, 1..32) { v -> setCore { it.copy(muxConcurrency = v) } }
                }
                if (prefs.dev) {
                    Line()
                    SwitchRow("TCP Fast Open", "Быстрее первое соединение, но не все сети поддерживают", prefs.tcpFastOpen) { v ->
                        setCore { it.copy(tcpFastOpen = v) }
                    }
                    Line()
                    ChoiceRow("Уровень логов ядра", LOG_LEVELS, LOG_LEVELS.indexOf(prefs.logLevel).coerceAtLeast(0)) { i ->
                        setCore { it.copy(logLevel = LOG_LEVELS[i]) }
                    }
                }
            }

            Section("Пинг и проверка") {
                ChoiceRow(
                    "Способ пинга", Tester.KIND_NAMES, prefs.pingMode.coerceIn(0, 2),
                    when (prefs.pingMode) {
                        1 -> "Время TCP-рукопожатия с портом сервера: мгновенно, но «отвечает» значит только «порт открыт»"
                        2 -> "Обычный ping до адреса сервера. Многие серверы на ICMP не отвечают, хотя работают"
                        else -> "Полный запрос через ядро Xray: честная задержка, сервер точно пропускает трафик"
                    }
                ) { i -> Store.setPrefs { it.copy(pingMode = i) } }
                Line()
                val urlIdx = Tester.TEST_URLS.indexOfFirst { it.second == prefs.testUrl }
                val custom = customUrl || urlIdx < 0
                ChoiceRow(
                    "Адрес для проверки", Tester.TEST_URLS.map { it.first } + "Свой",
                    if (custom) Tester.TEST_URLS.size else urlIdx,
                    "Куда ходит запрос при замере реальной задержки"
                ) { i ->
                    if (i < Tester.TEST_URLS.size) {
                        customUrl = false
                        Store.setPrefs { it.copy(testUrl = Tester.TEST_URLS[i].second) }
                    } else {
                        customUrl = true
                    }
                }
                if (custom) {
                    FieldRow("Свой адрес", prefs.testUrl, "https://…") { v -> Store.setPrefs { it.copy(testUrl = v.trim()) } }
                }
                Line()
                SliderRow("Таймаут", prefs.pingTimeout, 1000..10000, 500, format = { "$it мс" }) { v ->
                    Store.setPrefs { it.copy(pingTimeout = v) }
                }
                Line()
                SliderRow("Одновременных проверок", prefs.concurrency, 1..32, subtitle = "Больше: быстрее, но может перегрузить слабый канал") { v ->
                    Store.setPrefs { it.copy(concurrency = v) }
                }
                Line()
                SliderRow("Хороший пинг до", prefs.goodPing, 50..1000, 50, subtitle = "Зелёный цвет", format = { "$it мс" }) { v ->
                    Store.setPrefs { it.copy(goodPing = v, okPing = maxOf(it.okPing, v + 100)) }
                }
                Line()
                SliderRow("Средний пинг до", prefs.okPing, 200..3000, 100, subtitle = "Жёлтый цвет, дальше оранжевый", format = { "$it мс" }) { v ->
                    Store.setPrefs { it.copy(okPing = v, goodPing = minOf(it.goodPing, v - 50)) }
                }
                Line()
                SliderRow("Живой пинг каждые", prefs.livePingInterval, 3..60, subtitle = "Контрольный запрос во время подключения", format = { "$it с" }) { v ->
                    Store.setPrefs { it.copy(livePingInterval = v) }
                }
                Line()
                SwitchRow("Проверять при запуске", "Замерить все серверы при старте программы", prefs.testOnLaunch) { v ->
                    Store.setPrefs { it.copy(testOnLaunch = v) }
                }
            }

            Section("DNS", footer = "Используются ядром для маршрутизации и запросов через прокси.") {
                val di = DNS_PRESETS.indexOfFirst { it.second == prefs.dns }
                val custom = customDns || di < 0
                ChoiceRow("Серверы DNS", DNS_PRESETS.map { it.first } + "Свои", if (custom) DNS_PRESETS.size else di) { i ->
                    if (i < DNS_PRESETS.size) {
                        customDns = false
                        setCore { it.copy(dns = DNS_PRESETS[i].second) }
                    } else {
                        customDns = true
                    }
                }
                if (custom) {
                    FieldRow("Адреса через запятую", prefs.dns, "1.1.1.1, 8.8.8.8") { v -> setCore { it.copy(dns = v) } }
                }
                Line()
                ChoiceRow("Версия IP", listOf("Авто", "Только IPv4", "Только IPv6"), QUERY.indexOf(prefs.dnsQueryStrategy).coerceAtLeast(0)) { i ->
                    setCore { it.copy(dnsQueryStrategy = QUERY[i]) }
                }
            }

            Section("Маршрутизация") {
                SwitchRow("Российские сайты напрямую", "Домены .ru, .рф и российские IP идут мимо VPN", prefs.directRu) { v ->
                    setCore { it.copy(directRu = v) }
                }
                Line()
                SwitchRow("Блокировать рекламу", "По списку geosite:category-ads-all", prefs.blockAds) { v ->
                    setCore { it.copy(blockAds = v) }
                }
                Line()
                SwitchRow("Блокировать QUIC", "Браузеры перейдут на TCP: часто стабильнее YouTube", prefs.blockQuic) { v ->
                    setCore { it.copy(blockQuic = v) }
                }
                Line()
                SwitchRow("Локальная сеть напрямую", "Роутер, принтеры и 192.168.x.x мимо VPN", prefs.bypassLan) { v ->
                    setCore { it.copy(bypassLan = v) }
                }
                Line()
                FieldRow("Всегда напрямую", prefs.directRules, "sber.ru, geosite:apple, 10.0.0.0/8", "Домены, IP и geosite / geoip через запятую") { v ->
                    setCore { it.copy(directRules = v) }
                }
                Line()
                FieldRow("Блокировать", prefs.blockRules, "ads.example.com, geosite:category-porn") { v ->
                    setCore { it.copy(blockRules = v) }
                }
                if (prefs.dev) {
                    Line()
                    ChoiceRow("Domain strategy", STRATEGIES, STRATEGIES.indexOf(prefs.domainStrategy).coerceAtLeast(0)) { i ->
                        setCore { it.copy(domainStrategy = STRATEGIES[i]) }
                    }
                    Line()
                    SwitchRow("Sniffing", "Определять домен по TLS / HTTP для правил", prefs.sniffing) { v ->
                        setCore { it.copy(sniffing = v) }
                    }
                    Line()
                    SwitchRow("Sniffing только для маршрутов", "routeOnly: не подменять адрес назначения", prefs.sniffRouteOnly, enabled = prefs.sniffing) { v ->
                        setCore { it.copy(sniffRouteOnly = v) }
                    }
                }
            }

            Section(
                "Обход блокировок",
                footer = "Фрагментация режет TLS ClientHello на части, чтобы DPI не увидел SNI. Помогает, когда сервер не отвечает только у вашего провайдера."
            ) {
                SwitchRow("Фрагментация TLS", "Обход DPI провайдера", prefs.fragmentEnabled) { v ->
                    setCore { it.copy(fragmentEnabled = v) }
                }
                if (prefs.fragmentEnabled && prefs.dev) {
                    Line()
                    FieldRow("Пакеты", prefs.fragmentPackets, "tlshello") { v -> setCore { it.copy(fragmentPackets = v.trim()) } }
                    FieldRow("Длина", prefs.fragmentLength, "100-200") { v -> setCore { it.copy(fragmentLength = v.trim()) } }
                    FieldRow("Интервал, мс", prefs.fragmentInterval, "10-20") { v -> setCore { it.copy(fragmentInterval = v.trim()) } }
                }
                Line()
                ChoiceRow("uTLS-отпечаток по умолчанию", FP_LABELS, FP.indexOf(prefs.defaultFingerprint).coerceAtLeast(0), "Для серверов без своего fingerprint") { i ->
                    setCore { it.copy(defaultFingerprint = FP[i]) }
                }
            }

            Section("Подписки") {
                SwitchRow("Обновлять при запуске", null, prefs.autoUpdateSubs) { v -> Store.setPrefs { it.copy(autoUpdateSubs = v) } }
                Line()
                val ua = UA.indexOf(prefs.userAgent)
                val custom = customUa || ua < 0
                ChoiceRow("User-Agent", UA_LABELS + "Свой", if (custom) UA.size else ua, "Некоторые панели отдают разные конфиги разным клиентам") { i ->
                    if (i < UA.size) {
                        customUa = false
                        Store.setPrefs { it.copy(userAgent = UA[i]) }
                    } else {
                        customUa = true
                    }
                }
                if (custom) {
                    FieldRow("Свой User-Agent", prefs.userAgent, Prefs.DEFAULT_UA) { v -> Store.setPrefs { it.copy(userAgent = v) } }
                }
            }

            Section("Приложение", footer = "Ctrl K открывает меню команд, Ctrl 1–4 переключают разделы.") {
                if (Autostart.available) {
                    SwitchRow("Запускать вместе с Windows", "Свёрнутым в трей, без окна", autostart, enabled = !autostartBusy) { v ->
                        autostartBusy = true
                        Actions.go {
                            val ok = withContext(Dispatchers.IO) { Autostart.set(v) }
                            autostart = withContext(Dispatchers.IO) { Autostart.isEnabled() }
                            autostartBusy = false
                            if (!ok) Notice.show("Не удалось изменить автозапуск")
                        }
                    }
                    Line()
                }
                SwitchRow("Запускать свёрнутым в трей", "Окно не открывается, иконка появляется в трее", prefs.startHidden) { v ->
                    Store.setPrefs { it.copy(startHidden = v) }
                }
                Line()
                SwitchRow("Подключаться при запуске", "К последнему выбранному серверу", prefs.connectOnLaunch) { v ->
                    Store.setPrefs { it.copy(connectOnLaunch = v) }
                }
                Line()
                SwitchRow("Сворачивать в трей", "Крестик прячет окно, VPN продолжает работать", prefs.closeToTray) { v ->
                    Store.setPrefs { it.copy(closeToTray = v) }
                }
                Line()
                ChoiceRow("Открывать при запуске", listOf("Главная", "Серверы", "Проверка"), prefs.startPage.coerceIn(0, 2)) { i ->
                    Store.setPrefs { it.copy(startPage = i) }
                }
            }

            Section("О программе") {
                SettingRow("Версия", "RayScope для Windows", onClick = {
                    if (prefs.dev) {
                        Notice.show("Режим разработчика уже включён")
                    } else {
                        taps += 1
                        val left = 7 - taps
                        if (left <= 0) {
                            taps = 0
                            Store.setPrefs { it.copy(dev = true) }
                            Notice.show("Режим разработчика включён")
                        } else if (left <= 4) {
                            Notice.show("Ещё $left до режима разработчика")
                        }
                    }
                }) {
                    Text(APP_VERSION, color = p.hint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Line()
                SettingRow("Ядро Xray", Paths.xray()?.parentFile?.absolutePath ?: "Не найдено: положите xray.exe в папку core") {
                    Text(core, color = p.hint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Line()
                SettingRow("Папка с данными", Paths.data.absolutePath, onClick = { openFolder(Paths.data) }) {
                    RowIcon(Ico.Folder, p.hint, Modifier.size(18.dp))
                }
                if (prefs.dev) {
                    Line()
                    SettingRow("Журнал", "Ядро и события программы", onClick = onOpenLog) {
                        DevTag()
                        Spacer(Modifier.width(8.dp))
                        RowIcon(Ico.Chevron, p.hint, Modifier.size(18.dp))
                    }
                    Line()
                    SwitchRow("Режим разработчика", "Журнал, TCP-пинг, сырые параметры серверов", true) { v ->
                        Store.setPrefs { it.copy(dev = v) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ log

@Composable
fun LogScreen(onBack: () -> Unit) {
    val p = pal
    val lines by Log.lines.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(start = PAGE_PAD, end = PAGE_PAD, top = PAGE_TOP, bottom = TAB_SPACE)) {
        Header("Журнал", "Строк: " + lines.size) {
            IconBtn(Ico.Back, "Назад", onBack)
            IconBtn(Ico.Copy, "Скопировать", {
                Clip.copy(lines.joinToString(System.lineSeparator()))
                Notice.show("Журнал скопирован")
            })
            IconBtn(Ico.Trash, "Очистить", { Log.lines.value = emptyList() }, tint = Bad)
        }
        Box(Modifier.fillMaxWidth().weight(1f).card(p, 16.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp)) {
                items(lines) { l ->
                    val err = l.contains("rror") || l.contains("Ошибка") || l.contains("failed")
                    Text(
                        l, color = if (err) Bad else p.text, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp, modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
            CardScrollbar(list)
        }
    }
}
