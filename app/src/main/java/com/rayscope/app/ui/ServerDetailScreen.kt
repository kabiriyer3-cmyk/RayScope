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
import androidx.compose.ui.text.font.FontFamily
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
    val c = ray
    val ui = look
    val dev = ui.dev
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
        } else {
            geoErr = "адрес не разрешён"
        }
    }

    val isActive = vpn.state == ConnState.CONNECTED && vpn.serverId == s.id
    val spin = if (testing) spinAngle(800) else null
    val stateColor = if (isActive) Good else delayColor(s.delay)
    val sub = Repo.subs.value.firstOrNull { it.id == s.subId }

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

            // ---- hero
            Column(Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).card(c, 24.dp).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagBox(s.flagEmoji(), 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.cleanName(), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp
                        )
                        Text(
                            if (dev) "${s.address}:${s.port}" else (sub?.name ?: "добавлен вручную"),
                            color = c.hint, fontSize = 13.sp,
                            fontFamily = if (dev) FontFamily.Monospace else FontFamily.Default,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tag(s.protocolLabel().uppercase(), c.accent)
                    Tag(s.network.uppercase())
                    Tag(s.securityLabel().uppercase())
                    if (s.flow.isNotEmpty()) Tag("VISION")
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        CapsLabel(
                            when {
                                isActive -> "подключён сейчас"
                                s.isWorking -> "сервер рабочий"
                                s.isDead -> "не отвечает"
                                else -> "ещё не проверялся"
                            },
                            color = stateColor
                        )
                        Spacer(Modifier.height(4.dp))
                        LatencyText(s.delay, 34.sp)
                    }
                    SignalBars(s.delay, Modifier.padding(bottom = 8.dp))
                }
                if (s.lastError.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(RayIcons.Alert, contentDescription = null, tint = Bad, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.lastError, color = Bad, fontSize = 12.sp, maxLines = if (dev) 4 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(16.dp))
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

            Group("Проверки", modifier = Modifier.appear(1)) {
                PrefRow(RayIcons.Activity, Good, "Задержка", value = Fmt.ms(s.delay))
                if (dev) {
                    PrefRow(RayIcons.Signal, Blue, "TCP-пинг", value = if (s.protocol == "hysteria2") "н/д (UDP)" else Fmt.ms(s.tcpPing))
                }
                val stab = s.stability()
                PrefRow(
                    RayIcons.Gauge, Orange, "Стабильность",
                    value = if (stab < 0) "—" else if (dev) "$stab% · ${s.successCount}/${s.successCount + s.failCount}" else "$stab%"
                )
                PrefRow(
                    RayIcons.Clock, Gray, "Последняя проверка",
                    value = if (s.lastTested > 0) Fmt.ago(s.lastTested) else "никогда", divider = false
                )
            }

            val warns = s.warnings()
            if (dev || warns.isNotEmpty()) {
                Group(
                    "Аудит конфигурации", modifier = Modifier.appear(2),
                    trailing = { Tag(if (warns.isEmpty()) "OK" else "${warns.size}", if (warns.isEmpty()) Good else Mid) }
                ) {
                    if (warns.isEmpty()) {
                        PrefRow(RayIcons.ShieldCheck, Good, "Явных проблем не найдено", divider = false)
                    } else {
                        warns.forEachIndexed { i, w ->
                            PrefRow(RayIcons.Alert, Mid, w, divider = i != warns.lastIndex)
                        }
                    }
                }
            }

            if (dev) {
                Group("Параметры", modifier = Modifier.appear(3), trailing = { DevTag() }) {
                    KV("Протокол", s.protocolLabel())
                    KV("Адрес", s.address, mono = true)
                    KV("Порт", s.port.toString(), mono = true)
                    KV(if (s.protocol == "vless" || s.protocol == "vmess") "UUID" else "Пароль", maskSecret(s.uuid), mono = true)
                    KVIf("Шифрование", s.method, mono = true)
                    KVIf("Flow", s.flow, mono = true)
                    KV("Транспорт", s.network, mono = true)
                    KVIf("Header", s.headerType, mono = true)
                    KVIf("Host", s.host, mono = true)
                    KVIf("Path", s.path, mono = true)
                    KVIf("gRPC service", s.serviceName, mono = true)
                    KVIf("Режим", s.mode, mono = true)
                    KV("Защита", s.securityLabel())
                    KVIf("SNI", s.sni, mono = true)
                    val fp = s.fingerprint.ifEmpty { Repo.settings.defaultFingerprint.let { if (it.isEmpty()) "" else "$it (по умолчанию)" } }
                    KVIf("Fingerprint", fp, mono = true)
                    KVIf("ALPN", s.alpn, mono = true)
                    KVIf("REALITY pbk", s.publicKey, mono = true)
                    KVIf("shortId", s.shortId, mono = true)
                    KVIf("spiderX", s.spiderX, mono = true)
                    if (s.insecure) KV("allowInsecure", "да", Mid)
                    if (s.obfsPassword.isNotEmpty()) KV("Obfs", "salamander", mono = true)
                    KV("Источник", sub?.name ?: "добавлен вручную")
                }
            } else {
                Group("Параметры", modifier = Modifier.appear(3)) {
                    KV("Протокол", s.protocolLabel())
                    KV("Транспорт", s.network)
                    KV("Защита", s.securityLabel())
                    KVIf("SNI", s.sni, mono = true)
                    KV("Источник", sub?.name ?: "добавлен вручную")
                }
            }

            Group("Где сервер", modifier = Modifier.appear(4)) {
                if (dev) KV("IP (DNS)", resolved ?: "определяю…", mono = true)
                val g = geo
                val ge = geoErr
                if (g != null) {
                    KV("Страна", "${Fmt.flag(g.countryCode)} ${g.country}")
                    KV("Город", listOf(g.region, g.city).filter { it.isNotEmpty() }.joinToString(", "))
                    KV("Провайдер", g.isp)
                    if (dev) {
                        KV("Организация", g.org)
                        KV("ASN", g.asn, mono = true)
                    }
                    val h = g.hosting
                    if (h != null) KV("Тип", if (h) "Дата-центр" else "Домашний / мобильный")
                } else if (ge != null) {
                    KV("Гео", if (dev) ge else "не удалось определить", Bad)
                } else if (!dev) {
                    KV("Страна", "определяю…")
                }
            }

            Group(if (dev) "Ссылка и конфиг" else "Поделиться", modifier = Modifier.appear(5)) {
                val raw = s.rawLink
                if (dev) {
                    Text(
                        if (showRaw || raw.length <= 80) raw else raw.take(80) + "…",
                        color = c.hint, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().clickable { showRaw = !showRaw }.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniAction(RayIcons.Copy, "Скопировать", c.accent) {
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
                    if (dev) {
                        MiniAction(RayIcons.Code, "JSON", Purple) {
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
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            icon = { IconTile(RayIcons.Trash, Bad, 46.dp, 24.dp) },
            title = { Text("Удалить сервер?", color = c.text) },
            text = { Text(s.cleanName(), color = c.hint) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.remove(setOf(s.id))
                    onBack()
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = c.hint) } }
        )
    }
}
