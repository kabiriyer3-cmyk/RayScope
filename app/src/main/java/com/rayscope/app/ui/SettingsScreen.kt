@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
    val st = Repo.settings
    val ctx = LocalContext.current
    val vpn by VpnState.status.collectAsState()
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
        TopAppBar(title = { Text("Настройки") })
        if (vpn.state == ConnState.CONNECTED) {
            Text(
                "Изменения параметров VPN применятся после переподключения",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Mid
            )
        }

        SectionCard("📦 Подписки") {
            OutlinedButton(onClick = onSubs, modifier = Modifier.fillMaxWidth()) { Text("Управление подписками") }
            SwitchRow("Обновлять при запуске", null, autoUpd) {
                autoUpd = it
                st.autoUpdateSubs = it
            }
            SwitchRow("Проверять серверы после обновления", "Сразу отделить рабочие от нерабочих", testAfter) {
                testAfter = it
                st.testAfterUpdate = it
            }
            OutlinedTextField(
                value = ua,
                onValueChange = {
                    ua = it
                    st.userAgent = it.trim()
                },
                label = { Text("User-Agent для подписок") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("🧪 Проверка серверов") {
            OutlinedTextField(
                value = testUrl,
                onValueChange = {
                    testUrl = it
                    st.testUrl = it.trim()
                },
                label = { Text("URL для теста задержки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Параллельных проверок: ${conc.toInt()}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = conc,
                onValueChange = {
                    conc = it
                    st.concurrency = it.toInt()
                },
                valueRange = 1f..32f,
                steps = 30
            )
            SwitchRow(
                "Авто-переключение при сбое",
                "Если сервер перестал отвечать, перейти на самый быстрый рабочий",
                failover
            ) {
                failover = it
                st.autoFailover = it
            }
        }

        SectionCard("🛡 VPN") {
            OutlinedTextField(
                value = dns,
                onValueChange = {
                    dns = it
                    st.dns = it
                },
                label = { Text("DNS-серверы (через запятую)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = mtu,
                onValueChange = { v ->
                    mtu = v.filter { it.isDigit() }.take(5)
                    val n = mtu.toIntOrNull()
                    if (n != null && n in 1280..9000) st.mtu = n
                },
                label = { Text("MTU (1280–9000)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow("Локальная сеть напрямую", "192.168.x.x, 10.x.x.x и т.п. мимо VPN", bypassLan) {
                bypassLan = it
                st.bypassLan = it
            }
            SwitchRow("Маршрутизировать IPv6", "Защита от утечки IPv6 мимо туннеля", ipv6) {
                ipv6 = it
                st.ipv6 = it
            }
            SwitchRow("Sniffing", "Определять домен по TLS/HTTP для корректной маршрутизации", sniff) {
                sniff = it
                st.sniffing = it
            }
        }

        SectionCard("📱 Раздельное туннелирование") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Все", "Исключить", "Только").forEachIndexed { i, t ->
                    FilterChip(
                        selected = perAppMode == i,
                        onClick = {
                            perAppMode = i
                            st.perAppMode = i
                        },
                        label = { Text(t) }
                    )
                }
            }
            Text(
                when (perAppMode) {
                    1 -> "Выбранные приложения пойдут мимо VPN"
                    2 -> "Через VPN пойдут только выбранные приложения"
                    else -> "Все приложения идут через VPN"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onApps, enabled = perAppMode != 0, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать приложения ($perAppCount)")
            }
        }

        SectionCard("🔧 Система") {
            OutlinedButton(
                onClick = {
                    try {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                    } catch (e: Exception) {
                        toast("Не удалось открыть настройки VPN")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Always-on VPN и Kill switch") }
            Text(
                "В системных настройках VPN включите «Постоянная VPN» и «Блокировать соединения без VPN», чтобы трафик не утекал при обрыве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onLogs, modifier = Modifier.fillMaxWidth()) { Text("Журнал событий") }
            Spacer(Modifier.height(6.dp))
            KV("Ядро", CoreManager.version())
            KV("Версия приложения", BuildConfig.VERSION_NAME)
        }
        Spacer(Modifier.height(24.dp))
    }
}
