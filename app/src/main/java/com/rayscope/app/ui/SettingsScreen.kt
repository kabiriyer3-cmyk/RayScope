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
import com.rayscope.app.util.toast

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpen: (Route) -> Unit) {
    val c = ray
    val st = Repo.settings
    val ctx = LocalContext.current
    val vpn by VpnState.status.collectAsState()
    val subs by Repo.subs.collectAsState()
    val theme by Repo.themeMode.collectAsState()
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

    Page(title = "Настройки", scrolled = scrolled) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            ScreenTitle(
                "Настройки",
                if (vpn.state == ConnState.CONNECTED) "Изменения туннеля применятся после переподключения" else "Подключение, пинг, маршруты"
            )

            // ---- active profile summary
            Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 22.dp).padding(16.dp)) {
                CapsLabel("Активный профиль")
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.forEachIndexed { i, t -> Tag(t, if (i < 3) c.accent else Color2.tag(t)) }
                }
            }

            Group("Сеть", modifier = Modifier.appear(1)) {
                PrefRow(
                    RayIcons.Plug, Cyan, "Подключение",
                    subtitle = listOf(
                        if (st.muxEnabled) "Mux ${st.muxConcurrency}" else "Mux выкл",
                        if (st.tcpFastOpen) "TFO" else null,
                        "рукопожатие ${st.handshakeTimeout} с",
                        if (st.reconnectOnNetworkChange) "переподключение при смене сети" else null
                    ).filterNotNull().joinToString(" · "),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.CONNECTION)) }
                )
                PrefRow(
                    RayIcons.Activity, Good, "Пинг и проверка",
                    subtitle = "${Actions.modeLabel()} · таймаут ${st.pingTimeout} ms · попыток ${st.pingAttempts}",
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
                        st.domainStrategy
                    ).filterNotNull().joinToString(" · "),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.ROUTING)) }
                )
                PrefRow(
                    RayIcons.Scissors, Orange, "Обход блокировок",
                    subtitle = (if (st.fragmentEnabled) "Fragment ${st.fragmentPackets} · ${st.fragmentLength}" else "Fragment выкл") +
                        " · fp " + st.defaultFingerprint.ifEmpty { "из ссылки" },
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.DPI)) }
                )
                PrefRow(
                    RayIcons.Hash, Teal, "Локальный прокси",
                    subtitle = "SOCKS ${st.socksPort} · HTTP ${st.httpPort}" + (if (st.allowLan) " · доступен в LAN" else ""),
                    chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.PROXY)) }
                )
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
                    if (theme == 2 || (theme == 0 && c.dark)) RayIcons.Moon else RayIcons.Sun,
                    Indigo, "Тема", divider = false
                )
                Segmented(
                    listOf("Система", "Светлая", "Тёмная"),
                    selected = theme,
                    onSelect = { Repo.setTheme(it) },
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                )
            }

            Group(
                "Система",
                modifier = Modifier.appear(4),
                footer = "Включите «Постоянная VPN» и «Блокировать без VPN», чтобы трафик не утекал при обрыве."
            ) {
                PrefRow(RayIcons.Lock, Bad, "Always-on VPN и Kill switch", chevron = true, onClick = {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    } catch (e: Exception) {
                        toast("Не удалось открыть настройки VPN")
                    }
                })
                PrefRow(RayIcons.Logs, Gray, "Журнал событий", chevron = true, onClick = { onOpen(Route.Logs) })
                PrefRow(RayIcons.Code, Teal, "Ядро", value = CoreManager.version())
                PrefRow(RayIcons.Info, Blue, "Версия", value = BuildConfig.VERSION_NAME, divider = false)
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
