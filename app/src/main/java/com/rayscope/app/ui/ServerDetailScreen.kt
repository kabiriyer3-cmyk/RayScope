package com.rayscope.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    val c = ray
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
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 100 } }

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
    val spin = if (testing) spinAngle(800) else null

    Page(
        title = s.cleanName(),
        scrolled = scrolled,
        onBack = onBack,
        actions = {
            ActionIcon(
                if (s.favorite) RayIcons.StarFilled else RayIcons.Star, "Избранное",
                tint = if (s.favorite) Yellow else c.text
            ) { Repo.toggleFavorite(s.id) }
            ActionIcon(RayIcons.Trash, "Удалить", tint = Bad) { confirmDelete = true }
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(pageTop()))
            LargeTitle(s.cleanName(), "${s.protocolLabel()} · ${s.network} · ${s.securityLabel()}", big = false)

            Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 20.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagBox(s.flagEmoji(), 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                isActive -> "Подключён сейчас"
                                s.isWorking -> "Сервер рабочий"
                                s.isDead -> "Не отвечает"
                                else -> "Ещё не проверялся"
                            },
                            color = if (isActive) Good else delayColor(s.delay), fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("${s.address}:${s.port}", color = c.hint, fontSize = 13.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TickerText(if (s.delay >= 0) "${s.delay}" else "—", delayColor(s.delay), 24.sp, FontWeight.Bold)
                        Text("ms", color = c.hint, fontSize = 11.sp)
                    }
                }
                if (s.lastError.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(RayIcons.Alert, contentDescription = null, tint = Bad, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(s.lastError, color = Bad, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (testing) "Тест…" else "Проверить",
                        enabled = !testing,
                        icon = RayIcons.Refresh,
                        iconModifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f },
                        onClick = {
                            testing = true
                            scope.launch {
                                Actions.testOne(s.id, save = true)
                                testing = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PillButton(
                        if (isActive) "Подключён" else "Подключить",
                        primary = true,
                        color = if (isActive) Good else c.accent,
                        enabled = !isActive,
                        icon = if (isActive) RayIcons.ShieldCheck else RayIcons.Power,
                        onClick = {
                            Repo.select(s.id)
                            if (vpn.state == ConnState.CONNECTED) RayVpnService.switchServer() else onConnect()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Section("Проверки", modifier = Modifier.appear(1), padded = false) {
                SettingRow(RayIcons.Activity, Good, "Задержка через прокси", value = Fmt.ms(s.delay))
                SettingRow(RayIcons.Signal, Blue, "TCP-пинг", value = if (s.protocol == "hysteria2") "н/д (UDP)" else Fmt.ms(s.tcpPing))
                val stab = s.stability()
                SettingRow(
                    RayIcons.Gauge, Orange, "Стабильность",
                    value = if (stab < 0) "—" else "$stab% · ${s.successCount}/${s.successCount + s.failCount}"
                )
                SettingRow(RayIcons.Clock, Gray, "Последняя проверка", value = if (s.lastTested > 0) Fmt.date(s.lastTested) else "никогда", divider = false)
            }

            val warns = s.warnings()
            Section("Аудит конфигурации", modifier = Modifier.appear(2), padded = false) {
                if (warns.isEmpty()) {
                    SettingRow(RayIcons.ShieldCheck, Good, "Явных проблем не найдено", divider = false)
                } else {
                    warns.forEachIndexed { i, w ->
                        SettingRow(RayIcons.Alert, Orange, w, divider = i != warns.lastIndex)
                    }
                }
            }

            Section("Параметры", modifier = Modifier.appear(3)) {
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
                KVIf("REALITY pbk", s.publicKey)
                KVIf("shortId", s.shortId)
                KVIf("spiderX", s.spiderX)
                if (s.insecure) KV("allowInsecure", "да", Mid)
                if (s.obfsPassword.isNotEmpty()) KV("Obfs", "salamander")
                val sub = Repo.subs.value.firstOrNull { it.id == s.subId }
                KV("Источник", sub?.name ?: "добавлен вручную")
            }

            Section("Сервер в сети", modifier = Modifier.appear(4)) {
                KV("IP (DNS)", resolved ?: "определяю…")
                val g = geo
                val ge = geoErr
                if (g != null) {
                    KV("Страна", "${Fmt.flag(g.countryCode)} ${g.country}")
                    KV("Город", listOf(g.region, g.city).filter { it.isNotEmpty() }.joinToString(", "))
                    KV("Провайдер", g.isp)
                    KV("Организация", g.org)
                    KV("ASN", g.asn)
                    val h = g.hosting
                    if (h != null) KV("Тип", if (h) "Дата-центр" else "Домашний / мобильный")
                } else if (ge != null) {
                    KV("Гео", ge, Bad)
                }
            }

            Section("Ссылка и конфиг", modifier = Modifier.appear(5)) {
                val raw = s.rawLink
                Text(
                    if (showRaw || raw.length <= 70) raw else raw.take(70) + "…",
                    color = c.hint, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp).clickable { showRaw = !showRaw }
                )
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniAction(RayIcons.Copy, "Копия", c.accent) {
                        clipboard.setText(AnnotatedString(raw))
                        toast("Ссылка скопирована")
                    }
                    MiniAction(RayIcons.Share, "Поделиться", c.accent) {
                        try {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw)
                            ctx.startActivity(Intent.createChooser(i, "Поделиться"))
                        } catch (e: Exception) {
                            toast("Не удалось поделиться")
                        }
                    }
                    MiniAction(RayIcons.Code, "JSON", c.accent) {
                        try {
                            val json = JSONObject(XrayConfig.build(s, Repo.settings, tun = false)).toString(2)
                            clipboard.setText(AnnotatedString(json))
                            toast("JSON-конфиг Xray скопирован")
                        } catch (e: Exception) {
                            toast("Ошибка: ${e.message}")
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { IconBadge(RayIcons.Trash, Bad, 44.dp, 24.dp) },
            title = { Text("Удалить сервер?") },
            text = { Text(s.cleanName()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.remove(setOf(s.id))
                    onBack()
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}
