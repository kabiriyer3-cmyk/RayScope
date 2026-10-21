package com.rayscope.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.data.Repo
import com.rayscope.app.data.Settings
import com.rayscope.app.net.Tester

enum class SetPage(val title: String, val subtitle: String) {
    CONNECTION("Подключение", "Поведение туннеля, Mux, TCP и таймауты"),
    PING("Пинг и проверка", "Как измерять задержку и отделять рабочие серверы"),
    DNS("DNS", "Резолверы внутри туннеля"),
    ROUTING("Маршрутизация", "Что идёт через VPN, что напрямую, что блокируется"),
    DPI("Обход блокировок", "TLS Fragment и отпечаток браузера"),
    PROXY("Локальный прокси", "SOCKS5 / HTTP на телефоне"),
    SPLIT("Раздельное туннелирование", "Какие приложения идут через VPN"),
    SUBS("Подписки", "Обновление и User-Agent"),
    LOOK("Оформление", "Тема, акцент, стекло, форма и виджеты"),
    DEV("Режим разработчика", "Технические детали, журнал и инструменты"),
}

@Composable
fun SettingsPageScreen(page: SetPage, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 90 } }
    Page(title = page.title, scrolled = scrolled, onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            ScreenTitle(page.title, page.subtitle, big = false)
            when (page) {
                SetPage.CONNECTION -> ConnectionPrefs()
                SetPage.PING -> PingPrefs()
                SetPage.DNS -> DnsPrefs()
                SetPage.ROUTING -> RoutingPrefs()
                SetPage.DPI -> DpiPrefs()
                SetPage.PROXY -> ProxyPrefs()
                SetPage.SPLIT -> SplitPrefs(onOpen)
                SetPage.SUBS -> SubsPrefs(onOpen)
                SetPage.LOOK -> LookPrefs()
                SetPage.DEV -> DevPrefs(onOpen)
            }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }
}

private val st: Settings get() = Repo.settings

// ------------------------------------------------------------------ connection

@Composable
private fun ConnectionPrefs() {
    val dev = look.dev
    var onLaunch by remember { mutableStateOf(st.connectOnLaunch) }
    var pickBest by remember { mutableStateOf(st.pickBestOnConnect) }
    var netRe by remember { mutableStateOf(st.reconnectOnNetworkChange) }
    var failover by remember { mutableStateOf(st.autoFailover) }
    var failN by remember { mutableIntStateOf(st.failoverThreshold) }
    var mux by remember { mutableStateOf(st.muxEnabled) }
    var muxC by remember { mutableIntStateOf(st.muxConcurrency) }
    var xudpC by remember { mutableIntStateOf(st.muxXudpConcurrency) }
    var quic by remember { mutableStateOf(st.muxXudpQuic) }
    var tfo by remember { mutableStateOf(st.tcpFastOpen) }
    var ka by remember { mutableIntStateOf(st.keepAliveIdle) }
    var hs by remember { mutableIntStateOf(st.handshakeTimeout) }
    var idle by remember { mutableIntStateOf(st.connIdle) }
    var mtu by remember { mutableIntStateOf(st.mtu) }
    var log by remember { mutableStateOf(st.logLevel) }

    Group("Поведение", modifier = Modifier.appear(0)) {
        PrefSwitch(RayIcons.Play, Good, "Подключаться при запуске", "Когда открываете приложение и разрешение VPN уже выдано", onLaunch) {
            onLaunch = it
            st.connectOnLaunch = it
        }
        PrefSwitch(RayIcons.Bolt, Yellow, "Сначала выбрать лучший", "Перед каждым подключением перепроверить серверы и взять самый быстрый", pickBest) {
            pickBest = it
            st.pickBestOnConnect = it
        }
        PrefSwitch(RayIcons.Wifi, Blue, "Переподключение при смене сети", "Wi-Fi ⇄ мобильная сеть: ядро перезапускается без разрыва туннеля", netRe) {
            netRe = it
            st.reconnectOnNetworkChange = it
        }
        PrefSwitch(RayIcons.Repeat, Orange, "Авто-переключение", "Если контрольный пинг падает, уйти на самый быстрый рабочий сервер", failover) {
            failover = it
            st.autoFailover = it
        }
        PrefSlider(
            icon = null, tint = Orange, title = "Сбоев подряд до переключения",
            value = failN, range = 1..6, enabled = failover, divider = false
        ) {
            failN = it
            st.failoverThreshold = it
        }
    }

    Group(
        "Мультиплексирование (Mux)",
        modifier = Modifier.appear(1),
        footer = "Несколько соединений внутри одного TCP: быстрее открываются сайты на медленном канале. Автоматически отключается для XTLS Vision (flow) и Hysteria2."
    ) {
        PrefSwitch(RayIcons.Layers, Purple, "Включить Mux", null, mux) {
            mux = it
            st.muxEnabled = it
        }
        PrefSlider(icon = null, tint = Purple, title = "Потоков TCP", value = muxC, range = 1..128, enabled = mux, divider = dev) {
            muxC = it
            st.muxConcurrency = it
        }
        if (dev) {
            PrefSlider(icon = null, tint = Purple, title = "Потоков XUDP", value = xudpC, range = 1..128, enabled = mux) {
                xudpC = it
                st.muxXudpConcurrency = it
            }
            val quicValues = listOf("reject", "allow", "skip")
            PrefChoice(
                icon = null, tint = Purple, title = "QUIC (UDP 443) в Mux",
                options = listOf("Блокировать", "Разрешить", "Мимо Mux"),
                selected = quicValues.indexOf(quic).coerceAtLeast(0), enabled = mux, divider = false
            ) {
                quic = quicValues[it]
                st.muxXudpQuic = quic
            }
        }
    }

    Group("TCP", modifier = Modifier.appear(2)) {
        PrefSwitch(RayIcons.Bolt, Teal, "TCP Fast Open", "Меньше задержка на новых соединениях, если сервер поддерживает", tfo, divider = dev) {
            tfo = it
            st.tcpFastOpen = it
        }
        if (dev) {
            PrefSlider(
                icon = RayIcons.Timer, tint = Teal, title = "Keep-alive",
                subtitle = "Как часто проверять простаивающие соединения",
                value = ka, range = 0..300, step = 15, divider = false,
                format = { if (it == 0) "система" else "$it с" }
            ) {
                ka = it
                st.keepAliveIdle = it
            }
        }
    }

    if (dev) {
        Group("Таймауты ядра", modifier = Modifier.appear(3), trailing = { DevTag() }) {
            PrefSlider(
                icon = RayIcons.Clock, tint = Blue, title = "Рукопожатие",
                subtitle = "Сколько ждать установления соединения с сервером",
                value = hs, range = 1..16, format = { "$it с" }
            ) {
                hs = it
                st.handshakeTimeout = it
            }
            PrefSlider(
                icon = RayIcons.Timer, tint = Blue, title = "Простой соединения",
                subtitle = "Через сколько закрывать неактивные соединения",
                value = idle, range = 30..1200, step = 30, divider = false, format = { "$it с" }
            ) {
                idle = it
                st.connIdle = it
            }
        }
    }

    Group("Туннель", modifier = Modifier.appear(4)) {
        val mtus = listOf(1280, 1380, 1400, 1420, 1460, 1500, 9000)
        PrefChoice(
            icon = RayIcons.Network, tint = Indigo, title = "MTU",
            subtitle = "Уменьшите, если сайты грузятся не до конца (мобильная сеть, PPPoE)",
            options = mtus.map { "$it" }, selected = mtus.indexOf(mtu), divider = dev
        ) {
            mtu = mtus[it]
            st.mtu = mtu
        }
        if (dev) {
            val levels = listOf("none", "error", "warning", "info", "debug")
            PrefChoice(
                icon = RayIcons.Logs, tint = Gray, title = "Уровень логов ядра",
                options = levels, selected = levels.indexOf(log).coerceAtLeast(0), divider = false
            ) {
                log = levels[it]
                st.logLevel = log
            }
        }
    }
}

// ------------------------------------------------------------------ ping

@Composable
private fun PingPrefs() {
    var mode by remember { mutableIntStateOf(st.pingMode) }
    var url by remember { mutableStateOf(st.testUrl) }
    var timeout by remember { mutableIntStateOf(st.pingTimeout) }
    var attempts by remember { mutableIntStateOf(st.pingAttempts) }
    var agg by remember { mutableIntStateOf(st.pingAggregate) }
    var conc by remember { mutableIntStateOf(st.concurrency) }
    var good by remember { mutableIntStateOf(st.goodPing) }
    var ok by remember { mutableIntStateOf(st.okPing) }
    var onLaunch by remember { mutableStateOf(st.testOnLaunch) }
    var afterUpd by remember { mutableStateOf(st.testAfterUpdate) }
    var retest by remember { mutableIntStateOf(st.autoRetestMinutes) }
    var live by remember { mutableIntStateOf(st.livePingInterval) }

    Group(
        "Метод",
        modifier = Modifier.appear(0),
        footer = when (mode) {
            1 -> "Только полный запрос через ядро Xray: честно, но медленнее на больших подписках."
            2 -> "Только TCP-рукопожатие с портом: мгновенно, но «рабочий» здесь значит лишь «порт открыт». Hysteria2 всё равно проверяется через ядро."
            else -> "Реальная задержка через ядро (полный хендшейк) плюс TCP-пинг для справки. Рекомендуется."
        }
    ) {
        Segmented(
            listOf("Реальный+TCP", "Реальный", "TCP"),
            selected = mode,
            onSelect = {
                mode = it
                st.pingMode = it
            },
            modifier = Modifier.padding(14.dp)
        )
    }

    Group("Адрес теста", modifier = Modifier.appear(1)) {
        val presets = Tester.TEST_URLS
        PrefChoice(
            icon = RayIcons.Target, tint = Cyan, title = "Готовые",
            subtitle = "Куда ходит проверочный запрос (и контрольный пинг в сессии)",
            options = presets.map { it.first },
            selected = presets.indexOfFirst { it.second == url }
        ) {
            url = presets[it].second
            st.testUrl = url
        }
        PrefText(
            icon = RayIcons.Link, tint = Cyan, title = "Свой URL", value = url,
            placeholder = Settings.DEFAULT_TEST_URL, divider = false
        ) {
            url = it
            st.testUrl = it.trim().ifEmpty { Settings.DEFAULT_TEST_URL }
        }
    }

    Group("Точность", modifier = Modifier.appear(2)) {
        PrefSlider(
            icon = RayIcons.Timer, tint = Orange, title = "Таймаут",
            subtitle = "Медленнее считается нерабочим. Также лимит на TCP-подключение",
            value = timeout, range = 1000..10000, step = 500,
            format = { if (it % 1000 == 0) "${it / 1000} с" else "${it / 1000}.5 с" }
        ) {
            timeout = it
            st.pingTimeout = it
        }
        PrefSlider(
            icon = RayIcons.Repeat, tint = Orange, title = "Попыток на сервер",
            subtitle = "Несколько замеров сглаживают случайные всплески",
            value = attempts, range = 1..5
        ) {
            attempts = it
            st.pingAttempts = it
        }
        PrefChoice(
            icon = null, tint = Orange, title = "Итог по попыткам",
            options = listOf("Лучший", "Средний"), selected = agg, enabled = attempts > 1
        ) {
            agg = it
            st.pingAggregate = it
        }
        PrefSlider(
            icon = RayIcons.Activity, tint = Orange, title = "Параллельно",
            subtitle = "Сколько серверов проверять одновременно",
            value = conc, range = 1..32, divider = false
        ) {
            conc = it
            st.concurrency = it
        }
    }

    Group("Цвета задержки", modifier = Modifier.appear(3)) {
        PrefSlider(
            icon = null, tint = Good, title = "«Хорошо» до",
            value = good, range = 50..1000, step = 50, format = { "$it ms" }
        ) {
            good = it
            st.goodPing = it
            if (ok <= it) {
                ok = it + 100
                st.okPing = ok
            }
        }
        PrefSlider(
            icon = null, tint = Mid, title = "«Средне» до",
            value = ok, range = 200..3000, step = 100, format = { "$it ms" }
        ) {
            ok = it.coerceAtLeast(good + 50)
            st.okPing = ok
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            key(good, ok) {
                DelayBadge((good * 0.6f).toLong())
                DelayBadge(((good + ok) / 2).toLong())
                DelayBadge((ok + 200).toLong())
                DelayBadge(-1L)
            }
        }
    }

    Group("Автоматизация", modifier = Modifier.appear(4)) {
        PrefSwitch(RayIcons.Play, Good, "Проверять при запуске", "Сразу после открытия приложения", onLaunch) {
            onLaunch = it
            st.testOnLaunch = it
        }
        PrefSwitch(RayIcons.Download, Teal, "Проверять после обновления подписки", null, afterUpd) {
            afterUpd = it
            st.testAfterUpdate = it
        }
        val periods = listOf(0, 5, 15, 30, 60)
        PrefChoice(
            icon = RayIcons.Refresh, tint = Blue, title = "Периодическая перепроверка",
            subtitle = "Пока приложение или VPN работают",
            options = periods.map { if (it == 0) "Выкл" else "$it мин" },
            selected = periods.indexOf(retest).coerceAtLeast(0)
        ) {
            retest = periods[it]
            st.autoRetestMinutes = retest
        }
        PrefSlider(
            icon = RayIcons.Activity, tint = Good, title = "Контрольный пинг в сессии",
            subtitle = "Как часто проверять канал при подключении (качество, авто-переключение)",
            value = live, range = 5..120, step = 5, divider = false, format = { "каждые $it с" }
        ) {
            live = it
            st.livePingInterval = it
        }
    }
}

// ------------------------------------------------------------------ dns

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsPrefs() {
    var dns by remember { mutableStateOf(st.dns) }
    var qs by remember { mutableStateOf(st.dnsQueryStrategy) }
    val presets = listOf(
        "Cloudflare" to "1.1.1.1, 1.0.0.1",
        "Google" to "8.8.8.8, 8.8.4.4",
        "Quad9" to "9.9.9.9, 149.112.112.112",
        "AdGuard" to "94.140.14.14, 94.140.15.15",
        "Яндекс" to "77.88.8.8, 77.88.8.1",
        "DoH Cloudflare" to "https://1.1.1.1/dns-query, 1.1.1.1",
        "DoH Google" to "https://dns.google/dns-query, 8.8.8.8",
    )
    Group(
        "Серверы",
        modifier = Modifier.appear(0),
        footer = "Через запятую. DoH-адреса (https://…) использует ядро; системе передаются только IPv4-адреса из списка."
    ) {
        PrefText(icon = RayIcons.Servers, tint = Blue, title = "DNS", value = dns, placeholder = "1.1.1.1, 8.8.8.8") {
            dns = it
            st.dns = it
        }
        PrefRow(null, Blue, "Быстрый выбор", divider = false)
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { (name, value) ->
                Chip(name, selected = dns.trim() == value, small = true) {
                    dns = value
                    st.dns = value
                }
            }
        }
    }
    val strategies = listOf("UseIP", "UseIPv4", "UseIPv6")
    Group("Запросы", modifier = Modifier.appear(1)) {
        PrefChoice(
            icon = RayIcons.Filter, tint = Indigo, title = "Стратегия",
            subtitle = "Какие записи запрашивать у DNS. «Только IPv4» помогает, если IPv6 не работает",
            options = listOf("Авто", "Только IPv4", "Только IPv6"),
            selected = strategies.indexOf(qs).coerceAtLeast(0), divider = false
        ) {
            qs = strategies[it]
            st.dnsQueryStrategy = qs
        }
    }
}

// ------------------------------------------------------------------ routing

@Composable
private fun RoutingPrefs() {
    val dev = look.dev
    var bypassLan by remember { mutableStateOf(st.bypassLan) }
    var ipv6 by remember { mutableStateOf(st.ipv6) }
    var quic by remember { mutableStateOf(st.blockQuic) }
    var ds by remember { mutableStateOf(st.domainStrategy) }
    var sniff by remember { mutableStateOf(st.sniffing) }
    var routeOnly by remember { mutableStateOf(st.sniffRouteOnly) }
    var direct by remember { mutableStateOf(st.directRules) }
    var block by remember { mutableStateOf(st.blockRules) }

    Group("Основное", modifier = Modifier.appear(0)) {
        PrefSwitch(RayIcons.Wifi, Indigo, "Локальная сеть напрямую", "192.168.x.x, 10.x.x.x и др. мимо VPN", bypassLan) {
            bypassLan = it
            st.bypassLan = it
        }
        PrefSwitch(RayIcons.Globe, Blue, "Маршрутизировать IPv6", "Защита от утечки IPv6 мимо туннеля", ipv6) {
            ipv6 = it
            st.ipv6 = it
        }
        PrefSwitch(RayIcons.Ban, Pink, "Блокировать QUIC", "UDP 443 в блок: браузеры и YouTube перейдут на TCP, часто это стабильнее", quic, divider = dev) {
            quic = it
            st.blockQuic = it
        }
        if (dev) {
            val strategies = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
            PrefChoice(
                icon = RayIcons.Route, tint = Purple, title = "Domain strategy",
                subtitle = "Как сопоставлять домены с IP-правилами",
                options = strategies, selected = strategies.indexOf(ds).coerceAtLeast(0), divider = false
            ) {
                ds = strategies[it]
                st.domainStrategy = ds
            }
        }
    }

    if (dev) {
        Group("Sniffing", modifier = Modifier.appear(1), trailing = { DevTag() }) {
            PrefSwitch(RayIcons.Eye, Gray, "Определять домен", "По TLS SNI / HTTP Host / QUIC", sniff) {
                sniff = it
                st.sniffing = it
            }
            PrefSwitch(
                null, Gray, "Только для маршрутизации",
                "Не подменять адрес назначения: помогает, если не открываются отдельные сайты или игры",
                routeOnly, enabled = sniff, divider = false
            ) {
                routeOnly = it
                st.sniffRouteOnly = it
            }
        }
    }

    Group(
        "Свои правила",
        modifier = Modifier.appear(2),
        footer = "Через запятую: example.com, full:api.site.ru, keyword:ads, 10.0.0.0/8. Без префикса домен совпадает со всеми поддоменами. geosite/geoip не поддерживаются."
    ) {
        val (dd, di) = remember(direct) { com.rayscope.app.core.XrayConfig.splitRules(direct) }
        PrefText(
            icon = RayIcons.Route, tint = Good, title = "Напрямую",
            subtitle = if (direct.isBlank()) "Сайты и сети мимо VPN" else "правил: домены ${dd.size}, IP ${di.size}",
            value = direct, placeholder = "sberbank.ru, gosuslugi.ru"
        ) {
            direct = it
            st.directRules = it
        }
        val (bd, bi) = remember(block) { com.rayscope.app.core.XrayConfig.splitRules(block) }
        PrefText(
            icon = RayIcons.Ban, tint = Bad, title = "Блокировать",
            subtitle = if (block.isBlank()) "Реклама, трекеры, нежелательные домены" else "правил: домены ${bd.size}, IP ${bi.size}",
            value = block, placeholder = "keyword:ads, doubleclick.net", divider = false
        ) {
            block = it
            st.blockRules = it
        }
    }
}

// ------------------------------------------------------------------ anti-DPI

@Composable
private fun DpiPrefs() {
    val dev = look.dev
    var frag by remember { mutableStateOf(st.fragmentEnabled) }
    var packets by remember { mutableStateOf(st.fragmentPackets) }
    var length by remember { mutableStateOf(st.fragmentLength) }
    var interval by remember { mutableStateOf(st.fragmentInterval) }
    var fp by remember { mutableStateOf(st.defaultFingerprint) }

    val presets = listOf(
        Triple("Мягкий", "tlshello", "100-200" to "10-20"),
        Triple("Средний", "tlshello", "10-30" to "10-20"),
        Triple("Агрессивный", "1-3", "1-5" to "1-5"),
    )
    Group(
        "TLS Fragment",
        modifier = Modifier.appear(0),
        footer = "Дробит TLS ClientHello на куски, чтобы DPI не видел SNI целиком. Помогает при блокировках по SNI, немного увеличивает время рукопожатия. Не применяется к Hysteria2."
    ) {
        PrefSwitch(RayIcons.Scissors, Orange, "Включить фрагментацию", null, frag) {
            frag = it
            st.fragmentEnabled = it
        }
        PrefChoice(
            icon = null, tint = Orange, title = "Пресет",
            options = presets.map { it.first },
            selected = presets.indexOfFirst { it.second == packets && it.third.first == length && it.third.second == interval },
            enabled = frag, divider = dev
        ) {
            val p = presets[it]
            packets = p.second
            length = p.third.first
            interval = p.third.second
            st.fragmentPackets = packets
            st.fragmentLength = length
            st.fragmentInterval = interval
        }
        if (dev) {
            val packetOptions = listOf("tlshello", "1-1", "1-3", "1-5")
            PrefChoice(
                icon = null, tint = Orange, title = "Что дробить",
                subtitle = "tlshello = только ClientHello; 1-3 = первые пакеты соединения",
                options = packetOptions, selected = packetOptions.indexOf(packets), enabled = frag
            ) {
                packets = packetOptions[it]
                st.fragmentPackets = packets
            }
            PrefText(icon = null, tint = Orange, title = "Размер куска, байт", value = length, placeholder = "100-200") {
                length = it
                st.fragmentLength = it.trim()
            }
            PrefText(icon = null, tint = Orange, title = "Пауза между кусками, мс", value = interval, placeholder = "10-20", divider = false) {
                interval = it
                st.fragmentInterval = it.trim()
            }
        }
    }

    val fps = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "randomized")
    Group(
        "Отпечаток TLS (uTLS)",
        modifier = Modifier.appear(1),
        footer = "Подставляется только серверам, у которых fp не задан в ссылке. TLS-рукопожатие становится неотличимым от браузера."
    ) {
        PrefChoice(
            icon = RayIcons.Key, tint = Indigo, title = "Fingerprint по умолчанию",
            options = fps.map { it.ifEmpty { "Не задавать" } }, selected = fps.indexOf(fp).coerceAtLeast(0), divider = false
        ) {
            fp = fps[it]
            st.defaultFingerprint = fp
        }
    }
}

// ------------------------------------------------------------------ local proxy

@Composable
private fun ProxyPrefs() {
    var socks by remember { mutableStateOf(st.socksPort.toString()) }
    var http by remember { mutableStateOf(st.httpPort.toString()) }
    var lan by remember { mutableStateOf(st.allowLan) }
    val sOk = socks.toIntOrNull()?.let { it in 1025..65535 } == true
    val hOk = http.toIntOrNull()?.let { it in 1025..65535 && it != socks.toIntOrNull() } == true
    val host = if (lan) "0.0.0.0" else "127.0.0.1"
    Group(
        "Порты",
        modifier = Modifier.appear(0),
        footer = "Любое приложение на телефоне может ходить через прокси без VPN: SOCKS5 $host:${st.socksPort}, HTTP $host:${st.httpPort}. Порты 1025–65535."
    ) {
        PrefText(
            icon = RayIcons.Hash, tint = Teal, title = "SOCKS5", value = socks, placeholder = "10808",
            subtitle = if (sOk) null else "некорректный порт",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        ) { v ->
            socks = v.filter { it.isDigit() }.take(5)
            val n = socks.toIntOrNull()
            if (n != null && n in 1025..65535) st.socksPort = n
        }
        PrefText(
            icon = RayIcons.Hash, tint = Teal, title = "HTTP", value = http, placeholder = "10809",
            subtitle = if (hOk) null else "некорректный порт или совпадает с SOCKS",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), divider = false
        ) { v ->
            http = v.filter { it.isDigit() }.take(5)
            val n = http.toIntOrNull()
            if (n != null && n in 1025..65535 && n != st.socksPort) st.httpPort = n
        }
    }
    Group("Доступ", modifier = Modifier.appear(1)) {
        PrefSwitch(
            RayIcons.Network, Orange, "Разрешить из локальной сети",
            "Прокси слушает 0.0.0.0: можно раздать VPN ноутбуку или ТВ через точку доступа. Без пароля!",
            lan, divider = false
        ) {
            lan = it
            st.allowLan = it
        }
    }
}

// ------------------------------------------------------------------ split tunnel

@Composable
private fun SplitPrefs(onOpen: (Route) -> Unit) {
    var mode by remember { mutableIntStateOf(st.perAppMode) }
    Group(
        "Режим",
        modifier = Modifier.appear(0),
        footer = when (mode) {
            1 -> "Выбранные приложения пойдут мимо VPN (банки, госуслуги, локальные сервисы)"
            2 -> "Через VPN пойдут только выбранные приложения"
            else -> "Все приложения идут через VPN"
        }
    ) {
        Segmented(
            listOf("Все", "Исключить", "Только"),
            selected = mode,
            onSelect = {
                mode = it
                st.perAppMode = it
            },
            modifier = Modifier.padding(14.dp)
        )
        Hairline()
        PrefRow(
            RayIcons.Apps, Pink, "Выбрать приложения", value = "${st.perAppSet.size}",
            chevron = true, enabled = mode != 0, divider = false, onClick = { onOpen(Route.Apps) }
        )
    }
}

// ------------------------------------------------------------------ subscriptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubsPrefs(onOpen: (Route) -> Unit) {
    var autoUpd by remember { mutableStateOf(st.autoUpdateSubs) }
    var testAfter by remember { mutableStateOf(st.testAfterUpdate) }
    var ua by remember { mutableStateOf(st.userAgent) }
    val uas = listOf("v2rayNG" to Settings.DEFAULT_UA, "Happ" to "Happ/1.0", "Clash Meta" to "clash.meta", "sing-box" to "sing-box", "Hiddify" to "HiddifyNext/2.5.7")
    Group("Управление", modifier = Modifier.appear(0)) {
        PrefRow(RayIcons.Package, Indigo, "Мои подписки", value = "${Repo.subs.value.size}", chevron = true, divider = false, onClick = { onOpen(Route.Subs) })
    }
    Group("Обновление", modifier = Modifier.appear(1)) {
        PrefSwitch(RayIcons.Download, Good, "Обновлять при запуске", null, autoUpd) {
            autoUpd = it
            st.autoUpdateSubs = it
        }
        PrefSwitch(RayIcons.Check, Teal, "Проверять после обновления", "Сразу отделить рабочие от нерабочих", testAfter, divider = false) {
            testAfter = it
            st.testAfterUpdate = it
        }
    }
    Group(
        "User-Agent",
        modifier = Modifier.appear(2),
        footer = "Некоторые панели отдают разный набор серверов в зависимости от клиента."
    ) {
        PrefText(icon = RayIcons.Code, tint = Gray, title = "Заголовок", value = ua, placeholder = Settings.DEFAULT_UA, divider = false) {
            ua = it
            st.userAgent = it.trim()
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uas.forEach { (name, value) ->
                Chip(name, selected = ua.trim() == value, small = true) {
                    ua = value
                    st.userAgent = value
                }
            }
        }
    }
    Text(
        "Подписки обновляются напрямую, а если не вышло и VPN подключён — через туннель.",
        color = ray.hint, fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
    )
}
