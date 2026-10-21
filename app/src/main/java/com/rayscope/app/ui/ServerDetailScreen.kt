@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.RayVpnService
import com.rayscope.app.core.VpnState
import com.rayscope.app.core.XrayConfig
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.model.Server
import com.rayscope.app.net.IpChecker
import com.rayscope.app.net.IpInfo
import com.rayscope.app.net.Tester
import com.rayscope.app.util.Fmt
import com.rayscope.app.util.toast
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ServerDetailScreen(id: String, onBack: () -> Unit, onConnect: () -> Unit) {
    val servers by Repo.servers.collectAsState()
    val s = servers.firstOrNull { it.id == id }
    if (s == null) {
        LaunchedEffect(Unit) { onBack() }
    } else {
        ServerDetail(s, onBack, onConnect)
    }
}

@Composable
private fun ServerDetail(s: Server, onBack: () -> Unit, onConnect: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val vpn by VpnState.status.collectAsState()
    var resolved by remember(s.id) { mutableStateOf<String?>(null) }
    var geo by remember(s.id) { mutableStateOf<IpInfo?>(null) }
    var geoErr by remember(s.id) { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    LaunchedEffect(s.id) {
        val (ips, ms) = Tester.resolve(s.address)
        resolved = if (ips.isEmpty()) "не удалось разрешить" else ips.joinToString(", ") + (if (ms >= 0) "  ($ms ms)" else "")
        val first = ips.firstOrNull()
        if (first != null) {
            try {
                geo = IpChecker.lookup(first)
            } catch (e: Exception) {
                geoErr = e.message ?: "ошибка"
            }
        }
    }

    val isActive = vpn.state == ConnState.CONNECTED && vpn.serverId == s.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.cleanName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = { Repo.toggleFavorite(s.id) }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Избранное",
                            tint = if (s.favorite) Mid else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            SectionCard("Состояние") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.flagEmoji().ifEmpty { "🌐" }, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.protocolLabel()} · ${s.network} · ${s.securityLabel()}", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                s.isWorking -> "Сервер рабочий"
                                s.isDead -> "Сервер не отвечает"
                                else -> "Ещё не проверялся"
                            },
                            color = delayColor(s.delay),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    DelayBadge(s.delay)
                }
                Spacer(Modifier.height(8.dp))
                KV("Задержка через прокси", Fmt.ms(s.delay))
                KV("TCP-пинг", if (s.protocol == "hysteria2") "н/д (UDP)" else Fmt.ms(s.tcpPing))
                KV("Последняя проверка", if (s.lastTested > 0) Fmt.date(s.lastTested) else "никогда")
                val stab = s.stability()
                KV("Стабильность", if (stab < 0) "—" else "$stab%  (${s.successCount} ок / ${s.failCount} сбоев)")
                if (s.lastError.isNotEmpty()) KV("Последняя ошибка", s.lastError, Bad)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            testing = true
                            scope.launch {
                                Actions.testOne(s.id, save = true)
                                testing = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (testing) "Тест…" else "Проверить") }
                    Button(
                        enabled = !isActive,
                        onClick = {
                            Repo.select(s.id)
                            if (vpn.state == ConnState.CONNECTED) RayVpnService.switchServer() else onConnect()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isActive) "Подключён" else "Подключить") }
                }
            }

            val warns = s.warnings()
            SectionCard("🛡 Аудит конфигурации") {
                if (warns.isEmpty()) {
                    Text("✓ Явных проблем не найдено", color = Good, style = MaterialTheme.typography.bodySmall)
                } else {
                    warns.forEach { w ->
                        Text("⚠ $w", color = Mid, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            SectionCard("⚙ Параметры") {
                KV("Протокол", s.protocolLabel())
                KV("Адрес", s.address)
                KV("Порт", s.port.toString())
                KV(if (s.protocol == "vless" || s.protocol == "vmess") "UUID" else "Пароль", maskSecret(s.uuid))
                KVIf("Шифрование", s.method)
                KVIf("Flow", s.flow)
                KV("Транспорт", s.network)
                KVIf("Header", s.headerType)
                KVIf("Host", s.host)
                KVIf("Path", s.path)
                KVIf("gRPC service", s.serviceName)
                KVIf("Режим", s.mode)
                KV("Защита", s.securityLabel())
                KVIf("SNI", s.sni)
                KVIf("Fingerprint", s.fingerprint)
                KVIf("ALPN", s.alpn)
                KVIf("REALITY publicKey", s.publicKey)
                KVIf("shortId", s.shortId)
                KVIf("spiderX", s.spiderX)
                if (s.insecure) KV("allowInsecure", "да", Mid)
                if (s.obfsPassword.isNotEmpty()) KV("Obfs", "salamander")
                val sub = Repo.subs.value.firstOrNull { it.id == s.subId }
                KV("Источник", sub?.name ?: "добавлен вручную")
            }

            SectionCard("🌐 Сервер в сети") {
                KV("IP-адреса (DNS)", resolved ?: "определяю…")
                val g = geo
                val ge = geoErr
                if (g != null) {
                    KV("Страна", "${Fmt.flag(g.countryCode)} ${g.country}")
                    KV("Город", listOf(g.region, g.city).filter { it.isNotEmpty() }.joinToString(", "))
                    KV("Провайдер", g.isp)
                    KV("Организация", g.org)
                    KV("ASN", g.asn)
                    val h = g.hosting
                    if (h != null) KV("Тип", if (h) "Хостинг / дата-центр" else "Домашний / мобильный")
                } else if (ge != null) {
                    KV("Гео", ge, Bad)
                }
            }

            SectionCard("🔗 Ссылка и конфиг") {
                val raw = s.rawLink
                Text(
                    if (showRaw || raw.length <= 70) raw else raw.take(70) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showRaw = !showRaw }
                )
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(raw))
                        toast("Ссылка скопирована")
                    }) { Text("Копировать") }
                    TextButton(onClick = {
                        try {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw)
                            ctx.startActivity(Intent.createChooser(i, "Поделиться"))
                        } catch (e: Exception) {
                            toast("Не удалось поделиться")
                        }
                    }) { Text("Поделиться") }
                    TextButton(onClick = {
                        try {
                            val json = JSONObject(XrayConfig.build(s, Repo.settings, tun = false)).toString(2)
                            clipboard.setText(AnnotatedString(json))
                            toast("JSON-конфиг Xray скопирован")
                        } catch (e: Exception) {
                            toast("Ошибка: ${e.message}")
                        }
                    }) { Text("JSON") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сервер?") },
            text = { Text(s.cleanName()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.remove(setOf(s.id))
                    onBack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}
