package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rayscope.app.BuildConfig
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.CoreManager
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.data.UiPrefs
import com.rayscope.app.util.toast
import com.rayscope.app.widget.SmallWidget
import com.rayscope.app.widget.WideWidget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpen: (Route) -> Unit) {
    val c = ray
    val st = Repo.settings
    val ctx = LocalContext.current
    val vpn by VpnState.status.collectAsState()
    val subs by Repo.subs.collectAsState()
    val ui by Repo.ui.collectAsState()
    val dev = ui.dev
    var taps by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 120 } }

    val profile = buildList {
        add("PING: " + when (st.pingMode) {
            1 -> "REAL"
            2 -> "TCP"
            else -> "REAL+TCP"
        })
        add("TIMEOUT ${st.pingTimeout / 1000.0}s".replace(".0s", "s"))
        add("x${st.concurrency}")
        add("DNS " + st.dnsList().first())
        if (st.muxEnabled) add("MUX ${st.muxConcurrency}")
        if (st.fragmentEnabled) add("FRAGMENT")
        if (st.tcpFastOpen) add("TFO")
        if (st.blockQuic) add("NO-QUIC")
        if (st.autoFailover) add("FAILOVER")
        add("MTU ${st.mtu}")
    }

    val accentName = if (ui.accent < 0) "Material You" else UiPrefs.ACCENT_NAMES.getOrElse(ui.accent) { "" }
    val glassName = when (ui.glass) {
        0 -> "стекло выкл"
        2 -> "сильное стекло"
        else -> "мягкое стекло"
    }

    Page(title = "Настройки", scrolled = scrolled) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            ScreenTitle(
                "Настройки",
                if (vpn.state == ConnState.CONNECTED) "Изменения туннеля применятся после переподключения" else "Подключение, пинг, оформление"
            )

            // ---- active profile summary (dev)
            if (dev) {
                Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 22.dp).padding(16.dp)) {
                    Row {
                        CapsLabel("Активный профиль", Modifier.weight(1f))
                        DevTag()
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.forEachIndexed { i, t -> Tag(t, if (i < 3) c.accent else Color2.tag(t)) }
                    }
                }
            }

            Group("Сеть", modifier = Modifier.appear(1)) {
                PrefRow(
                    RayIcons.Plug, Cyan, "Подключение",
                    subtitle = listOf(
                        if (st.muxEnabled) "Mux ${st.muxConcurrency}" else "Mux выкл",
                        if (st.tcpFastOpen) "TFO" else null,
                        if (dev) "рукопожатие ${st.handshakeTimeout} с" else null,
                        if (st.autoFailover) "авто-переключение" else null,
                        if (st.reconnectOnNetworkChange) "переподключение при смене сети" else null
                    ).filterNotNull().joinToString(" · "),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.CONNECTION)) }
                )
                PrefRow(
                    RayIcons.Activity, Good, "Пинг и проверка",
                    subtitle = (if (dev) "${Actions.modeLabel()} · " else "") + "таймаут ${st.pingTimeout} ms · попыток ${st.pingAttempts}",
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.PING)) }
                )
                PrefRow(
                    RayIcons.Servers, Blue, "DNS",
                    subtitle = st.dnsList().joinToString(", "),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.DNS)) }
                )
                PrefRow(
                    RayIcons.Route, Purple, "Маршрутизация",
                    subtitle = listOf(
                        if (st.bypassLan) "LAN напрямую" else null,
                        if (st.ipv6) "IPv6" else null,
                        if (st.blockQuic) "QUIC блок" else null,
                        if (dev) st.domainStrategy else null
                    ).filterNotNull().joinToString(" · ").ifEmpty { "всё через VPN" },
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.ROUTING)) }
                )
                PrefRow(
                    RayIcons.Scissors, Orange, "Обход блокировок",
                    subtitle = (if (st.fragmentEnabled) (if (dev) "Fragment ${st.fragmentPackets} · ${st.fragmentLength}" else "Fragment вкл") else "Fragment выкл") +
                        " · fp " + st.defaultFingerprint.ifEmpty { "из ссылки" },
                    chevron = true, divider = dev, onClick = { onOpen(Route.Pref(SetPage.DPI)) }
                )
                if (dev) {
                    PrefRow(
                        RayIcons.Hash, Teal, "Локальный прокси",
                        subtitle = "SOCKS ${st.socksPort} · HTTP ${st.httpPort}" + (if (st.allowLan) " · доступен в LAN" else ""),
                        chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.PROXY)) }
                    )
                }
            }

            Group("Приложения и подписки", modifier = Modifier.appear(2)) {
                PrefRow(
                    RayIcons.Fork, Pink, "Раздельное туннелирование",
                    subtitle = when (st.perAppMode) {
                        1 -> "Исключить выбранные · ${st.perAppSet.size}"
                        2 -> "Только выбранные · ${st.perAppSet.size}"
                        else -> "Все приложения через VPN"
                    },
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.SPLIT)) }
                )
                PrefRow(
                    RayIcons.Package, Indigo, "Подписки",
                    subtitle = "${subs.size} · " + (if (st.autoUpdateSubs) "обновление при запуске" else "ручное обновление"),
                    chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.SUBS)) }
                )
            }

            Group("Оформление", modifier = Modifier.appear(3)) {
                PrefRow(
                    if (ui.theme == 2 || (ui.theme == 0 && c.dark)) RayIcons.Moon else RayIcons.Sun,
                    c.accent, "Оформление",
                    subtitle = "$accentName · $glassName" + (if (ui.compact) " · компактно" else "") + (if (ui.amoled) " · AMOLED" else ""),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.LOOK)) }
                )
                Segmented(
                    listOf("Система", "Светлая", "Тёмная"),
                    selected = ui.theme,
                    onSelect = { v -> Repo.setUi { it.copy(theme = v) } },
                    modifier = Modifier.padding(14.dp)
                )
            }

            Group(
                "Виджеты",
                modifier = Modifier.appear(4),
                footer = "Если лаунчер не умеет добавлять сам: долгое нажатие на рабочем столе → Виджеты → RayScope."
            ) {
                PrefRow(
                    RayIcons.Power, Good, "Кнопка 2×2", subtitle = "Подключение одним нажатием",
                    chevron = true, onClick = { pinWidget(ctx, SmallWidget::class.java) }
                )
                PrefRow(
                    RayIcons.Signal, Cyan, "Панель 4×1", subtitle = "Сервер, статус, пинг и скорость",
                    chevron = true, divider = false, onClick = { pinWidget(ctx, WideWidget::class.java) }
                )
            }

            Group(
                "Система",
                modifier = Modifier.appear(5),
                footer = "Включите «Постоянная VPN» и «Блокировать без VPN», чтобы трафик не утекал при обрыве."
            ) {
                PrefRow(RayIcons.Lock, Bad, "Always-on VPN и Kill switch", chevron = true, onClick = {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    } catch (e: Exception) {
                        toast("Не удалось открыть настройки VPN")
                    }
                })
                if (dev) {
                    PrefRow(RayIcons.Logs, Gray, "Журнал событий", chevron = true, onClick = { onOpen(Route.Logs) })
                    PrefRow(RayIcons.Code, Teal, "Ядро", value = CoreManager.version())
                }
                PrefRow(
                    RayIcons.Info, Blue, "Версия", value = BuildConfig.VERSION_NAME, divider = dev,
                    onClick = {
                        if (dev) {
                            toast("Режим разработчика уже включён")
                        } else {
                            taps += 1
                            val left = 7 - taps
                            if (left <= 0) {
                                taps = 0
                                Repo.setUi { it.copy(dev = true) }
                                toast("Режим разработчика включён")
                            } else if (left <= 3) {
                                toast("Ещё $left до режима разработчика")
                            }
                        }
                    }
                )
                if (dev) {
                    PrefRow(
                        RayIcons.Code, Purple, "Режим разработчика",
                        subtitle = "Журнал, история проверок, JSON, инструменты",
                        chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.DEV)) },
                        trailing = { DevTag() }
                    )
                }
            }
            TabBottomSpacer()
        }
    }
}

/** Tag colours for the profile summary. */
private object Color2 {
    fun tag(t: String) = when {
        t.startsWith("MUX") -> Purple
        t.startsWith("FRAGMENT") -> Orange
        t.startsWith("NO-QUIC") -> Pink
        t.startsWith("FAILOVER") -> Good
        t.startsWith("TFO") -> Teal
        else -> Gray
    }
}
