package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rayscope.app.BuildConfig
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.CoreManager
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Repo
import com.rayscope.app.util.toast

@Composable
fun SettingsScreen(onApps: () -> Unit, onLogs: () -> Unit, onSubs: () -> Unit) {
    val c = ray
    val st = Repo.settings
    val ctx = LocalContext.current
    val vpn by VpnState.status.collectAsState()
    val subs by Repo.subs.collectAsState()
    val theme by Repo.themeMode.collectAsState()
    var testUrl by remember { mutableStateOf(st.testUrl) }
    var dns by remember { mutableStateOf(st.dns) }
    var ua by remember { mutableStateOf(st.userAgent) }
    var mtu by remember { mutableStateOf(st.mtu.toString()) }
    var conc by remember { mutableFloatStateOf(st.concurrency.toFloat()) }
    var bypassLan by remember { mutableStateOf(st.bypassLan) }
    var ipv6 by remember { mutableStateOf(st.ipv6) }
    var sniff by remember { mutableStateOf(st.sniffing) }
    var autoUpd by remember { mutableStateOf(st.autoUpdateSubs) }
    var testAfter by remember { mutableStateOf(st.testAfterUpdate) }
    var failover by remember { mutableStateOf(st.autoFailover) }
    var perAppMode by remember { mutableIntStateOf(st.perAppMode) }
    val perAppCount = st.perAppSet.size
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 120 } }

    Page(title = "Настройки", scrolled = scrolled) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            LargeTitle(
                "Настройки",
                if (vpn.state == ConnState.CONNECTED) "Параметры VPN применятся после переподключения" else null
            )

            Section("Оформление", modifier = Modifier.appear(0), padded = false) {
                SettingRow(
                    if (theme == 2 || (theme == 0 && c.dark)) RayIcons.Moon else RayIcons.Sun,
                    Indigo, "Тема", divider = false
                )
                Segmented(
                    listOf("Система", "Светлая", "Тёмная"),
                    selected = theme,
                    onSelect = { Repo.setTheme(it) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            Section("Подписки", modifier = Modifier.appear(1), padded = false) {
                SettingRow(RayIcons.Package, Blue, "Подписки", value = "${subs.size}", chevron = true, onClick = onSubs)
                SettingSwitch(RayIcons.Refresh, Good, "Обновлять при запуске", null, autoUpd) {
                    autoUpd = it
                    st.autoUpdateSubs = it
                }
                SettingSwitch(RayIcons.Check, Teal, "Проверять после обновления", "Сразу отделить рабочие от нерабочих", testAfter, divider = false) {
                    testAfter = it
                    st.testAfterUpdate = it
                }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    InputField(
                        value = ua,
                        onValueChange = {
                            ua = it
                            st.userAgent = it.trim()
                        },
                        placeholder = "v2rayNG/1.10.20",
                        label = "User-Agent"
                    )
                }
            }

            Section("Проверка серверов", modifier = Modifier.appear(2), padded = false) {
                SettingRow(RayIcons.Activity, Orange, "Параллельных проверок", value = "${conc.toInt()}", divider = false)
                Slider(
                    value = conc,
                    onValueChange = {
                        conc = it
                        st.concurrency = it.toInt()
                    },
                    valueRange = 1f..32f,
                    steps = 30,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.field),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                SettingSwitch(RayIcons.Bolt, Yellow, "Авто-переключение", "Если сервер упал, перейти на самый быстрый", failover, divider = false) {
                    failover = it
                    st.autoFailover = it
                }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    InputField(
                        value = testUrl,
                        onValueChange = {
                            testUrl = it
                            st.testUrl = it.trim()
                        },
                        placeholder = "https://www.gstatic.com/generate_204",
                        label = "URL для теста задержки",
                        leading = RayIcons.Link
                    )
                }
            }

            Section("VPN", modifier = Modifier.appear(3), padded = false) {
                SettingSwitch(RayIcons.Wifi, Indigo, "Локальная сеть напрямую", "192.168.x.x, 10.x.x.x мимо VPN", bypassLan) {
                    bypassLan = it
                    st.bypassLan = it
                }
                SettingSwitch(RayIcons.Globe, Blue, "Маршрутизировать IPv6", "Защита от утечки IPv6", ipv6) {
                    ipv6 = it
                    st.ipv6 = it
                }
                SettingSwitch(RayIcons.Eye, Gray, "Sniffing", "Определять домен по TLS/HTTP", sniff, divider = false) {
                    sniff = it
                    st.sniffing = it
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputField(
                        value = dns,
                        onValueChange = {
                            dns = it
                            st.dns = it
                        },
                        placeholder = "1.1.1.1, 8.8.8.8",
                        label = "DNS",
                        modifier = Modifier.weight(0.65f)
                    )
                    InputField(
                        value = mtu,
                        onValueChange = { v ->
                            mtu = v.filter { it.isDigit() }.take(5)
                            val n = mtu.toIntOrNull()
                            if (n != null && n in 1280..9000) st.mtu = n
                        },
                        placeholder = "1500",
                        label = "MTU",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f)
                    )
                }
            }

            Section(
                "Раздельное туннелирование",
                modifier = Modifier.appear(4),
                padded = false,
                footer = when (perAppMode) {
                    1 -> "Выбранные приложения пойдут мимо VPN"
                    2 -> "Через VPN пойдут только выбранные приложения"
                    else -> "Все приложения идут через VPN"
                }
            ) {
                Segmented(
                    listOf("Все", "Исключить", "Только"),
                    selected = perAppMode,
                    onSelect = {
                        perAppMode = it
                        st.perAppMode = it
                    },
                    modifier = Modifier.padding(16.dp)
                )
                RowDivider()
                SettingRow(
                    RayIcons.Apps, Purple, "Выбрать приложения", value = "$perAppCount",
                    chevron = true, enabled = perAppMode != 0, divider = false, onClick = onApps
                )
            }

            Section(
                "Система",
                modifier = Modifier.appear(5),
                padded = false,
                footer = "Включите «Постоянная VPN» и «Блокировать без VPN», чтобы трафик не утекал при обрыве."
            ) {
                SettingRow(RayIcons.Lock, Bad, "Always-on VPN и Kill switch", chevron = true, onClick = {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    } catch (e: Exception) {
                        toast("Не удалось открыть настройки VPN")
                    }
                })
                SettingRow(RayIcons.Logs, Gray, "Журнал событий", chevron = true, onClick = onLogs)
                SettingRow(RayIcons.Code, Teal, "Ядро", value = CoreManager.version())
                SettingRow(RayIcons.Info, Blue, "Версия", value = BuildConfig.VERSION_NAME, divider = false)
            }
            TabBottomSpacer()
        }
    }
}
