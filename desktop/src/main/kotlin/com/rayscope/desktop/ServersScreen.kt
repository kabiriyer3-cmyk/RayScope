package com.rayscope.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SORT_NAMES = listOf("Как в подписке", "По пингу", "По имени", "По стабильности")
private val FILTERS = listOf("Все", "Рабочие", "Нерабочие", "Не проверены", "Избранные")

fun mask(v: String): String = if (v.length <= 12) v else v.take(6) + "…" + v.takeLast(4)

@Composable
fun ServersScreen() {
    val p = pal
    val servers by Store.servers.collectAsState()
    val subs by Store.subs.collectAsState()
    val selId by Store.selectedId.collectAsState()
    val prefs by Store.prefs.collectAsState()
    val st by Engine.status.collectAsState()
    val testing by Actions.testing.collectAsState()
    val updating by Actions.updating.collectAsState()
    val prog by Actions.progress.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var addSub by remember { mutableStateOf(false) }
    var confirmDead by remember { mutableStateOf(false) }

    val sortMode = prefs.sortMode.coerceIn(0, SORT_NAMES.size - 1)
    val shown = remember(servers, query, filter, sortMode) {
        val q = query.trim().lowercase()
        var l = servers.filter { s ->
            when (filter) {
                1 -> s.isWorking
                2 -> s.isDead
                3 -> s.isUntested
                4 -> s.favorite
                else -> true
            }
        }
        if (q.isNotEmpty()) {
            l = l.filter { it.name.lowercase().contains(q) || it.address.lowercase().contains(q) || it.protocol.contains(q) }
        }
        when (sortMode) {
            1 -> l.sortedWith(compareBy<Server>({ if (it.isWorking) 0 else if (it.isUntested) 1 else 2 }, { it.delay }))
            2 -> l.sortedBy { it.cleanName().lowercase() }
            3 -> l.sortedByDescending { it.stability() }
            else -> l
        }
    }
    val counts = listOf(
        servers.size,
        servers.count { it.isWorking },
        servers.count { it.isDead },
        servers.count { it.isUntested },
        servers.count { it.favorite },
    )
    val effSel = selId ?: servers.firstOrNull()?.id
    val open = openId?.let { id -> servers.firstOrNull { it.id == id } }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp, end = 24.dp, top = 28.dp)) {
            Header("Серверы", "Всего " + servers.size + " · рабочих " + counts[1]) {
                Btn("Вставить", { Actions.go { Actions.importText(Clip.paste()) } }, icon = Ico.Clipboard)
                Spacer(Modifier.width(8.dp))
                Btn("Подписка", { addSub = true }, icon = Ico.Plus)
                Spacer(Modifier.width(8.dp))
                Btn(
                    "Пинг всех", { Actions.go { Actions.testAll() } }, primary = true, icon = Ico.Bolt,
                    enabled = !testing && servers.isNotEmpty()
                )
                Spacer(Modifier.width(4.dp))
                Box {
                    IconBtn(Ico.More, "Ещё", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem("Обновить подписки", Ico.Refresh) {
                            menu = false
                            Actions.go { Actions.updateAllSubs(silent = false) }
                        }
                        MenuItem("Скопировать все ссылки", Ico.Copy) {
                            menu = false
                            Clip.copy(Actions.exportAll())
                            Notice.show("Ссылки скопированы")
                        }
                        MenuItem("Удалить нерабочие", Ico.Trash) {
                            menu = false
                            confirmDead = true
                        }
                        if (prefs.dev) {
                            MenuItem("TCP-пинг всех", Ico.Activity, dev = true) {
                                menu = false
                                Actions.go { Actions.tcpAll() }
                            }
                            MenuItem("Сбросить результаты", Ico.Close, dev = true) {
                                menu = false
                                Store.resetTests()
                            }
                        }
                    }
                }
            }

            if (testing) {
                SlimProgress(if (prog.second > 0) prog.first.toFloat() / prog.second else 0f)
                Text(
                    "Проверяю " + prog.first + " из " + prog.second, color = p.hint, fontSize = 12.sp, style = TNum,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
            }
            if (updating) {
                Text("Обновляю подписки…", color = p.hint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            }

            if (subs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    subs.forEach { sub -> SubCard(sub, servers.count { it.subId == sub.id }, updating) }
                }
            }

            if (servers.isEmpty()) {
                EmptyServers { addSub = true }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(query, { query = it }, "Поиск по имени, адресу, протоколу", Modifier.weight(1f), leading = Ico.Search, onCard = false)
                    Spacer(Modifier.width(10.dp))
                    Box {
                        Btn(SORT_NAMES[sortMode], { sortMenu = true }, icon = Ico.Sort, color = p.hint)
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SORT_NAMES.forEachIndexed { i, n ->
                                CheckItem(n, i == sortMode) {
                                    sortMenu = false
                                    Store.setPrefs { it.copy(sortMode = i) }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FILTERS.forEachIndexed { i, f ->
                        RChip(f, filter == i, counts[i], icon = if (i == 4) Ico.StarFilled else null) { filter = i }
                    }
                }
                if (shown.isEmpty()) {
                    Text("Ничего не найдено", color = p.hint, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = TAB_SPACE),
                        verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 4.dp else 8.dp)
                    ) {
                        items(shown, key = { it.id }) { s ->
                            val here = st.serverId == s.id
                            ServerRow(
                                s = s,
                                selected = s.id == effSel,
                                live = here && st.state == ConnState.CONNECTED,
                                connecting = here && st.state == ConnState.CONNECTING,
                                compact = prefs.compact,
                                dev = prefs.dev,
                                testingAll = testing,
                                onClick = {
                                    pickServer(s.id)
                                    openId = s.id
                                }
                            )
                        }
                    }
                }
            }
        }
        if (open != null) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(p.stroke))
            DetailPanel(open, subs, prefs.dev, st) { openId = null }
        }
    }

    if (addSub) AddSubDialog { addSub = false }
    if (confirmDead) {
        val n = servers.count { it.isDead }
        ConfirmDialog(
            "Удалить нерабочие?",
            "Будет удалено серверов: $n. Непроверенные останутся.",
            "Удалить",
            onConfirm = {
                confirmDead = false
                val r = Actions.removeDead()
                Notice.show("Удалено: $r")
            },
            onDismiss = { confirmDead = false }
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dev: Boolean = false, onClick: () -> Unit) {
    val p = pal
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = 14.sp)
                if (dev) {
                    Spacer(Modifier.width(8.dp))
                    DevTag()
                }
            }
        },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp)) }
    )
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    val p = pal
    DropdownMenuItem(
        text = { Text(text, fontSize = 14.sp, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.size(16.dp)) {
                if (checked) Icon(Ico.Check, contentDescription = null, tint = p.accent, modifier = Modifier.size(16.dp))
            }
        }
    )
}

@Composable
private fun ServerRow(
    s: Server,
    selected: Boolean,
    live: Boolean,
    connecting: Boolean,
    compact: Boolean,
    dev: Boolean,
    testingAll: Boolean,
    onClick: () -> Unit,
) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    var testingOne by remember { mutableStateOf(false) }
    val border = when {
        live -> Good.copy(alpha = 0.6f)
        selected -> p.accent.copy(alpha = 0.6f)
        hover -> p.faint
        else -> p.stroke
    }
    Row(
        Modifier.fillMaxWidth().card(p, 14.dp, border).hoverable(src).clickable(onClick = onClick).hand()
            .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagBox(s.countryCode(), if (compact) 30.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.cleanName(), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (live) {
                    Spacer(Modifier.width(8.dp))
                    Tag("АКТИВЕН", Good)
                } else if (connecting) {
                    Spacer(Modifier.width(8.dp))
                    Tag("ПОДКЛЮЧЕНИЕ", Mid)
                }
            }
            if (!compact) {
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    add(s.protocolLabel())
                    add(s.network.uppercase())
                    add(s.securityLabel())
                    if (dev && s.tcpPing >= 0) add("tcp " + s.tcpPing)
                    val stab = s.stability()
                    if (stab >= 0) add("стабильность $stab%")
                }.joinToString(" · ")
                Text(meta, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
                if (dev && s.isDead && s.lastError.isNotEmpty()) {
                    Text(s.lastError, color = Bad, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconBtn(
            if (s.favorite) Ico.StarFilled else Ico.Star, "Избранное", { Store.toggleFavorite(s.id) },
            tint = if (s.favorite) Yellow else if (hover) p.hint else Color.Transparent, size = 30.dp
        )
        Spacer(Modifier.width(4.dp))
        SignalBars(s.delay)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.width(76.dp).clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !testingOne && !testingAll) {
                    testingOne = true
                    Actions.go {
                        try {
                            Actions.testOne(s.id)
                        } finally {
                            testingOne = false
                        }
                    }
                }.hand().padding(4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (testingOne) {
                val r = spin()
                Icon(Ico.Refresh, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp).graphicsLayerRotation(r))
            } else {
                LatencyText(s.delay, if (compact) 14.sp else 16.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        IconBtn(
            Ico.Power, if (live) "Отключить" else "Подключить",
            { if (live || connecting) Engine.disconnect() else connectTo(s.id) },
            tint = if (live || connecting) Bad else p.accent, size = 34.dp
        )
    }
}

private fun Modifier.graphicsLayerRotation(r: State<Float>): Modifier =
    this.then(androidx.compose.ui.graphics.graphicsLayer { rotationZ = r.value })

@Composable
private fun SubCard(sub: Subscription, count: Int, updating: Boolean) {
    val p = pal
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.width(290.dp).card(p, 16.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.Link, p.accent, 30.dp, 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.name.ifEmpty { "Подписка" }, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count серв. · " + Fmt.ago(sub.lastUpdated), color = p.hint, fontSize = 12.sp, maxLines = 1)
            }
            IconBtn(Ico.Refresh, "Обновить", { Actions.go { Actions.updateSub(sub, silent = false) } }, enabled = !updating, size = 30.dp)
            IconBtn(Ico.Trash, "Удалить", { ask = true }, size = 30.dp)
        }
        if (sub.total > 0) {
            val used = sub.upload + sub.download
            Spacer(Modifier.height(10.dp))
            SlimProgress(used.toFloat() / sub.total)
            Text(Fmt.bytes(used) + " из " + Fmt.bytes(sub.total), color = p.hint, fontSize = 11.sp, style = TNum, modifier = Modifier.padding(top = 4.dp))
        }
        if (sub.expire > 0) {
            val days = (sub.expire * 1000 - System.currentTimeMillis()) / 86_400_000L
            Text(
                if (days >= 0) "Действует ещё $days дн" else "Подписка истекла",
                color = if (days < 3) Bad else p.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    if (ask) {
        ConfirmDialog(
            "Удалить подписку?",
            "Вместе с ней удалятся её серверы: $count.",
            "Удалить",
            onConfirm = {
                ask = false
                Store.removeSub(sub.id)
            },
            onDismiss = { ask = false }
        )
    }
}

@Composable
private fun EmptyServers(onAddSub: () -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(Ico.Servers, p.accent, 64.dp, 30.dp)
        Spacer(Modifier.height(16.dp))
        Text("Серверов пока нет", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Скопируйте ссылку vless://, vmess://, trojan://, ss:// или hy2:// и нажмите «Вставить», либо добавьте подписку.",
            color = p.hint, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 440.dp).padding(top = 6.dp)
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Вставить из буфера", { Actions.go { Actions.importText(Clip.paste()) } }, primary = true, icon = Ico.Clipboard)
            Btn("Добавить подписку", onAddSub, icon = Ico.Plus)
        }
    }
}

@Composable
private fun AddSubDialog(onDismiss: () -> Unit) {
    val p = pal
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val ok = url.trim().startsWith("http")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Btn("Добавить", {
                val u = url
                val n = name
                onDismiss()
                Actions.go { Actions.addSubscription(u, n) }
            }, primary = true, enabled = ok)
        },
        dismissButton = { Btn("Отмена", onDismiss, color = p.hint) },
        title = { Text("Новая подписка", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ссылка из бота или панели (Marzban, 3x-ui, Remnawave)", color = p.hint, fontSize = 13.sp)
                Field(url, { url = it }, "https://…", Modifier.fillMaxWidth(), mono = true)
                Field(name, { name = it }, "Название (необязательно)", Modifier.fillMaxWidth())
            }
        },
        containerColor = p.surface,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun DetailPanel(s: Server, subs: List<Subscription>, dev: Boolean, st: Status, onClose: () -> Unit) {
    val p = pal
    var confirm by remember(s.id) { mutableStateOf(false) }
    var testing by remember(s.id) { mutableStateOf(false) }
    val live = st.serverId == s.id && st.state != ConnState.STOPPED
    Column(
        Modifier.width(360.dp).fillMaxHeight().background(p.side).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = TAB_SPACE),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagBox(s.countryCode(), 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.cleanName(), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.protocolLabel() + " · " + s.network.uppercase(), color = p.hint, fontSize = 12.sp)
            }
            IconBtn(Ico.Close, "Закрыть", onClose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(
                if (live) "Отключить" else "Подключить",
                { if (live) Engine.disconnect() else connectTo(s.id) },
                Modifier.weight(1f), primary = !live, color = if (live) Bad else Color.Unspecified, icon = Ico.Power
            )
            Btn(
                if (testing) "Проверяю…" else "Проверить",
                {
                    testing = true
                    Actions.go {
                        try {
                            Actions.testOne(s.id)
                        } finally {
                            testing = false
                        }
                    }
                },
                Modifier.weight(1f), icon = Ico.Activity, enabled = !testing
            )
        }
        Section("Задержка") {
            KV("Через ядро", Fmt.ms(s.delay), delayColor(s.delay))
            if (dev) KV("TCP", Fmt.ms(s.tcpPing), delayColor(s.tcpPing))
            val stab = s.stability()
            KV("Стабильность", if (stab < 0) "" else "$stab% (" + s.successCount + " из " + (s.successCount + s.failCount) + ")")
            KV("Проверен", Fmt.ago(s.lastTested))
            if (s.lastError.isNotEmpty()) KV("Ошибка", s.lastError, Bad)
        }
        val source = subs.firstOrNull { it.id == s.subId }?.name?.ifEmpty { null } ?: "добавлен вручную"
        val sec = s.security.lowercase()
        Section("Параметры") {
            KV("Протокол", s.protocolLabel())
            val header = if (s.headerType.isNotEmpty() && s.headerType != "none") " · " + s.headerType else ""
            KV("Транспорт", s.network.uppercase() + header)
            KV("Защита", s.securityLabel(), if (sec == "tls" || sec == "reality") Good else Mid)
            KVIf("SNI", s.sni, mono = true)
            KV("Источник", source)
        }
        if (dev) {
            Section("Подробно", trailing = { DevTag() }) {
                KV("Адрес", s.address + ":" + s.port, mono = true)
                KVIf("ID / пароль", mask(s.uuid), mono = true)
                KVIf("Шифрование", s.method, mono = true)
                KVIf("Flow", s.flow, mono = true)
                KVIf("Host", s.host, mono = true)
                KVIf("Path", s.path, mono = true)
                KVIf("Service", s.serviceName, mono = true)
                KVIf("Mode", s.mode, mono = true)
                KVIf("Fingerprint", s.fingerprint, mono = true)
                KVIf("ALPN", s.alpn, mono = true)
                KVIf("Public key", mask(s.publicKey), mono = true)
                KVIf("Short ID", s.shortId, mono = true)
                KVIf("SpiderX", s.spiderX, mono = true)
                if (s.insecure) KV("allowInsecure", "да", Bad)
            }
        }
        val w = s.warnings()
        if (w.isNotEmpty()) {
            Section("Предупреждения") {
                w.forEach { msg ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Icon(Ico.Alert, contentDescription = null, tint = Mid, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, color = p.text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Ссылка", {
                Clip.copy(s.rawLink)
                Notice.show("Ссылка скопирована")
            }, Modifier.weight(1f), icon = Ico.Copy, enabled = s.rawLink.isNotEmpty())
            if (dev) {
                Btn("JSON", {
                    Clip.copy(runCatching { XrayConfig.outbound(s, Store.prefs.value, false).toString(2) }.getOrDefault(""))
                    Notice.show("JSON скопирован")
                }, Modifier.weight(1f), icon = Ico.Copy, color = Purple)
            }
        }
        Btn("Удалить сервер", { confirm = true }, Modifier.fillMaxWidth(), color = Bad, icon = Ico.Trash)
    }
    if (confirm) {
        ConfirmDialog(
            "Удалить сервер?",
            s.cleanName(),
            "Удалить",
            onConfirm = {
                confirm = false
                onClose()
                Store.remove(setOf(s.id))
            },
            onDismiss = { confirm = false }
        )
    }
}
