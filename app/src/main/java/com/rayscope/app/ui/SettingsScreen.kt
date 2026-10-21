package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Настройки", subtitle = if (vpn.state == ConnState.CONNECTED) "Параметры VPN применятся после переподключения" else null)

        Section("Оформление", contentPadding = PaddingValues(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Система", "Светлая", "Тёмная").forEachIndexed { i, t ->
                    GlassChip(t, selected = theme == i) { Repo.setTheme(i) }
                }
            }
        }

        Section("Подписки") {
            NavRow("Управление подписками", "${subs.size}", onClick = onSubs)
            RowDivider()
            SwitchRow("Обновлять при запуске", null, autoUpd) {
                autoUpd = it
                st.autoUpdateSubs = it
            }
            RowDivider()
            SwitchRow("Проверять после обновления", "Сразу отделить рабочие от нерабочих", testAfter) {
                testAfter = it
                st.testAfterUpdate = it
            }
            GlassField(
                value = ua,
                onValueChange = {
                    ua = it
                    st.userAgent = it.trim()
                },
                placeholder = "v2rayNG/1.10.20",
                label = "User-Agent для подписок",
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Section("Проверка серверов") {
            GlassField(
                value = testUrl,
                onValueChange = {
                    testUrl = it
                    st.testUrl = it.trim()
                },
                placeholder = "https://www.gstatic.com/generate_204",
                label = "URL для теста задержки"
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Параллельных проверок", color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("${conc.toInt()}", color = c.accent, fontSize = 15.sp)
            }
            Slider(
                value = conc,
                onValueChange = {
                    conc = it
                    st.concurrency = it.toInt()
                },
                valueRange = 1f..32f,
                steps = 30,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.field)
            )
            RowDivider()
            SwitchRow("Авто-переключение при сбое", "Если сервер упал, перейти на самый быстрый рабочий", failover) {
                failover = it
                st.autoFailover = it
            }
        }

        Section("VPN") {
            GlassField(
                value = dns,
                onValueChange = {
                    dns = it
                    st.dns = it
                },
                placeholder = "1.1.1.1, 8.8.8.8",
                label = "DNS-серверы"
            )
            GlassField(
                value = mtu,
                onValueChange = { v ->
                    mtu = v.filter { it.isDigit() }.take(5)
                    val n = mtu.toIntOrNull()
                    if (n != null && n in 1280..9000) st.mtu = n
                },
                placeholder = "1500",
                label = "MTU (1280–9000)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            RowDivider()
            SwitchRow("Локальная сеть напрямую", "192.168.x.x, 10.x.x.x мимо VPN", bypassLan) {
                bypassLan = it
                st.bypassLan = it
            }
            RowDivider()
            SwitchRow("Маршрутизировать IPv6", "Защита от утечки IPv6", ipv6) {
                ipv6 = it
                st.ipv6 = it
            }
            RowDivider()
            SwitchRow("Sniffing", "Определять домен по TLS/HTTP", sniff) {
                sniff = it
                st.sniffing = it
            }
        }

        Section("Раздельное туннелирование") {
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "Исключить", "Только").forEachIndexed { i, t ->
                    GlassChip(t, selected = perAppMode == i) {
                        perAppMode = i
                        st.perAppMode = i
                    }
                }
            }
            Text(
                when (perAppMode) {
                    1 -> "Выбранные приложения пойдут мимо VPN"
                    2 -> "Через VPN пойдут только выбранные приложения"
                    else -> "Все приложения идут через VPN"
                },
                color = c.hint, fontSize = 12.sp
            )
            RowDivider()
            NavRow("Выбрать приложения", "$perAppCount", enabled = perAppMode != 0, onClick = onApps)
        }

        Section("Система") {
            NavRow("Always-on VPN и Kill switch") {
                try {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                } catch (e: Exception) {
                    toast("Не удалось открыть настройки VPN")
                }
            }
            Text(
                "Включите «Постоянная VPN» и «Блокировать без VPN», чтобы трафик не утекал при обрыве.",
                color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
            RowDivider()
            NavRow("Журнал событий", onClick = onLogs)
            RowDivider()
            KV("Ядро", CoreManager.version())
            KV("Версия", BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(BottomInset))
    }
}
