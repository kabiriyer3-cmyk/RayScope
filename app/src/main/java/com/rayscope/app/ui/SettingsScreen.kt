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
    val scrolled by remember { derivedStateOf { scroll.value > 60 } }

    val profile = buildList {
        add("ping " + when (st.pingMode) {
            1 -> "http"
            2 -> "tcp"
            3 -> "icmp"
            else -> "http+tcp"
        })
        add("timeout ${st.pingTimeout / 1000.0}s".replace(".0s", "s"))
        add("x${st.concurrency}")
        add("dns " + st.dnsList().first())
        if (st.muxEnabled) add("mux ${st.muxConcurrency}")
        if (st.fragmentEnabled) add("fragment")
        if (st.tcpFastOpen) add("tfo")
        if (st.blockQuic) add("no-quic")
        if (st.autoFailover) add("failover")
        add("mtu ${st.mtu}")
    }

    val accentName = if (ui.accent < 0) "Material You" else UiPrefs.ACCENT_NAMES.getOrElse(ui.accent) { "" }
    val themeName = when (ui.theme) {
        1 -> "светлая"
        2 -> "тёмная"
        else -> "как в системе"
    }

    Page(title = "Настройки", scrolled = scrolled) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            ScreenTitle(
                "Настройки",
                if (vpn.state == ConnState.CONNECTED) "Изменения туннеля применятся после переподключения" else null
            )

            if (dev) {
                Group("Активный профиль", modifier = Modifier.appear(0), trailing = { DevTag() }) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.forEach { Tag(it) }
                    }
                }
            }

            Group("Сеть", modifier = Modifier.appear(1)) {
                PrefRow(
                    RayIcons.Plug, c.hint, "Подключение",
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
                    RayIcons.Activity, c.hint, "Пинг и проверка",
                    subtitle = (if (dev) "${Actions.modeLabel()} · " else "") + "таймаут ${st.pingTimeout} ms · попыток ${st.pingAttempts}",
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.PING)) }
                )
                PrefRow(
                    RayIcons.Servers, c.hint, "DNS",
                    subtitle = st.dnsList().joinToString(", "),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.DNS)) }
                )
                PrefRow(
                    RayIcons.Route, c.hint, "Маршрутизация",
                    subtitle = listOf(
                        if (st.bypassLan) "LAN напрямую" else null,
                        if (st.ipv6) "IPv6" else null,
                        if (st.blockQuic) "QUIC блок" else null,
                        if (dev) st.domainStrategy else null
                    ).filterNotNull().joinToString(" · ").ifEmpty { "всё через VPN" },
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.ROUTING)) }
                )
                PrefRow(
                    RayIcons.Scissors, c.hint, "Обход блокировок",
                    subtitle = (if (st.fragmentEnabled) (if (dev) "Fragment ${st.fragmentPackets} · ${st.fragmentLength}" else "Fragment вкл") else "Fragment выкл") +
                        " · fp " + st.defaultFingerprint.ifEmpty { "из ссылки" },
                    chevron = true, divider = dev, onClick = { onOpen(Route.Pref(SetPage.DPI)) }
                )
                if (dev) {
                    PrefRow(
                        RayIcons.Hash, c.hint, "Локальный прокси",
                        subtitle = "SOCKS ${st.socksPort} · HTTP ${st.httpPort}" + (if (st.allowLan) " · доступен в LAN" else ""),
                        chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.PROXY)) }
                    )
                }
            }

            Group("Приложения и подписки", modifier = Modifier.appear(2)) {
                PrefRow(
                    RayIcons.Fork, c.hint, "Раздельное туннелирование",
                    subtitle = when (st.perAppMode) {
                        1 -> "Исключить выбранные · ${st.perAppSet.size}"
                        2 -> "Только выбранные · ${st.perAppSet.size}"
                        else -> "Все приложения через VPN"
                    },
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.SPLIT)) }
                )
                PrefRow(
                    RayIcons.Package, c.hint, "Подписки",
                    subtitle = "${subs.size} · " + (if (st.autoUpdateSubs) "обновление при запуске" else "ручное обновление"),
                    chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.SUBS)) }
                )
            }

            Group("Интерфейс", modifier = Modifier.appear(3)) {
                PrefRow(
                    if (ui.theme == 2 || (ui.theme == 0 && c.dark)) RayIcons.Moon else RayIcons.Sun,
                    c.hint, "Оформление",
                    subtitle = "$themeName · $accentName" + (if (ui.compact) " · компактно" else "") + (if (ui.amoled) " · AMOLED" else ""),
                    chevron = true, onClick = { onOpen(Route.Pref(SetPage.LOOK)) }
                )
                PrefRow(
                    RayIcons.Upload, c.hint, "Резервная копия",
                    subtitle = "Экспорт и импорт настроек и подписок, сброс",
                    chevron = true, divider = false, onClick = { onOpen(Route.Pref(SetPage.BACKUP)) }
                )
            }

            Group(
                "Система",
                modifier = Modifier.appear(4),
                footer = "Включите «Постоянная VPN» и «Блокировать без VPN», чтобы трафик не утекал при обрыве."
            ) {
                PrefRow(RayIcons.Lock, c.hint, "Always-on VPN и Kill switch", chevron = true, onClick = {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    } catch (e: Exception) {
                        toast("Не удалось открыть настройки VPN")
                    }
                })
                if (dev) {
                    PrefRow(RayIcons.Logs, c.hint, "Журнал событий", chevron = true, onClick = { onOpen(Route.Logs) })
                    PrefRow(RayIcons.Code, c.hint, "Ядро", value = CoreManager.version())
                }
                PrefRow(
                    RayIcons.Info, c.hint, "Версия", value = BuildConfig.VERSION_NAME, divider = dev,
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
                        RayIcons.Code, c.hint, "Режим разработчика",
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
