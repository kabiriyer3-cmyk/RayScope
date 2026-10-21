@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayscope.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.RayVpnService
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.model.Server
import com.rayscope.app.model.Subscription
import com.rayscope.app.util.Fmt
import com.rayscope.app.util.toast
import kotlinx.coroutines.launch

enum class SFilter(val title: String) {
    ALL("Все"), WORKING("Рабочие"), DEAD("Нерабочие"), UNTESTED("Не проверены"), FAV("★ Избранные")
}

enum class SSort(val title: String) {
    DEFAULT("Как в подписке"), PING("По пингу"), NAME("По имени"), STABILITY("По стабильности")
}

private fun filterSort(all: List<Server>, f: SFilter, sort: SSort, q: String): List<Server> {
    var l = all.filter { s ->
        when (f) {
            SFilter.ALL -> true
            SFilter.WORKING -> s.isWorking
            SFilter.DEAD -> s.isDead
            SFilter.UNTESTED -> s.isUntested
            SFilter.FAV -> s.favorite
        }
    }
    if (q.isNotBlank()) {
        l = l.filter {
            it.name.contains(q, true) || it.address.contains(q, true) ||
                it.protocol.contains(q, true) || it.network.contains(q, true)
        }
    }
    return when (sort) {
        SSort.DEFAULT -> l
        SSort.PING -> l.sortedWith(compareBy<Server>({ if (it.isWorking) 0 else if (it.isUntested) 1 else 2 }, { it.delay }))
        SSort.NAME -> l.sortedBy { it.cleanName().lowercase() }
        SSort.STABILITY -> l.sortedByDescending { it.stability() }
    }
}

@Composable
private fun MItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

@Composable
fun ServersScreen(onOpen: (String) -> Unit, onToggle: () -> Unit) {
    val servers by Repo.servers.collectAsState()
    val subs by Repo.subs.collectAsState()
    val selectedId by Repo.selectedId.collectAsState()
    val testing by Actions.testing.collectAsState()
    val prog by Actions.progress.collectAsState()
    val vpn by VpnState.status.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf(SFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(SSort.DEFAULT) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var addSubOpen by remember { mutableStateOf(false) }
    var confirmDead by remember { mutableStateOf(false) }
    val connected = vpn.state == ConnState.CONNECTED
    val busy = vpn.state == ConnState.CONNECTING || vpn.state == ConnState.STOPPING

    val shown = remember(servers, filter, sort, query) { filterSort(servers, filter, sort, query) }
    val counts = remember(servers) { SFilter.entries.associateWith { f -> filterSort(servers, f, SSort.DEFAULT, "").size } }

    val pasteAction: () -> Unit = {
        val t = clipboard.getText()?.text.orEmpty()
        scope.launch { Actions.importText(t) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Серверы") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск")
                    }
                    IconButton(onClick = { scope.launch { Actions.testAll() } }, enabled = !testing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Проверить все")
                    }
                    Box {
                        TextButton(onClick = { sortOpen = true }) { Text("⇅", fontSize = 18.sp) }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SSort.entries.forEach { s ->
                                MItem((if (s == sort) "✓ " else "    ") + s.title) {
                                    sort = s
                                    sortOpen = false
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MItem("📋 Импорт из буфера") {
                                menuOpen = false
                                pasteAction()
                            }
                            MItem("➕ Добавить подписку") {
                                menuOpen = false
                                addSubOpen = true
                            }
                            MItem("🔄 Обновить подписки") {
                                menuOpen = false
                                scope.launch { Actions.updateAllSubs(false) }
                            }
                            MItem("📡 TCP-пинг всех") {
                                menuOpen = false
                                scope.launch { Actions.tcpAll() }
                            }
                            MItem("⚡ Лучший сервер") {
                                menuOpen = false
                                scope.launch {
                                    val b = Actions.pickBest()
                                    if (b == null) {
                                        toast("Нет рабочих серверов")
                                    } else {
                                        Repo.select(b.id)
                                        toast("Лучший: ${b.cleanName()} (${b.delay} ms)")
                                        if (VpnState.isConnected) RayVpnService.switchServer()
                                    }
                                }
                            }
                            MItem("🗑 Удалить нерабочие") {
                                menuOpen = false
                                confirmDead = true
                            }
                            MItem("♻ Сбросить результаты тестов") {
                                menuOpen = false
                                Repo.resetTests()
                            }
                            MItem("📤 Скопировать все ссылки") {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(Actions.exportAll()))
                                toast("Ссылки скопированы")
                            }
                        }
                    }
                }
            )

            if (testing) {
                LinearProgressIndicator(
                    progress = { if (prog.second == 0) 0f else prog.first.toFloat() / prog.second },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Проверка ${prog.first} / ${prog.second}…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Имя, адрес, протокол…") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = null) }
                        }
                    }
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SFilter.entries.toList()) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.title} · ${counts[f] ?: 0}") }
                    )
                }
            }

            if (servers.isEmpty()) {
                EmptyServers(onPaste = pasteAction, onAddSub = { addSubOpen = true })
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                    if (filter == SFilter.ALL && query.isBlank()) {
                        items(subs, key = { "sub_" + it.id }) { sub -> SubInfoCard(sub) }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "Нет серверов под этот фильтр",
                                Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(shown, key = { it.id }) { s ->
                        ServerRow(
                            s = s,
                            selected = s.id == selectedId,
                            active = connected && vpn.serverId == s.id,
                            onClick = {
                                Repo.select(s.id)
                                if (connected && vpn.serverId != s.id) RayVpnService.switchServer()
                            },
                            onInfo = { onOpen(s.id) },
                            onFav = { Repo.toggleFavorite(s.id) },
                            onTest = { scope.launch { Actions.testOne(s.id, save = true) } }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { if (!busy) onToggle() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = if (connected) Bad else MaterialTheme.colorScheme.primary,
            contentColor = if (connected) Color.White else MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(if (connected) Icons.Filled.Close else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when (vpn.state) {
                    ConnState.CONNECTED -> "Отключить"
                    ConnState.CONNECTING -> "Подключение…"
                    ConnState.STOPPING -> "Отключение…"
                    ConnState.STOPPED -> "Подключить"
                }
            )
        }
    }

    if (addSubOpen) {
        AddSubDialog(
            onDismiss = { addSubOpen = false },
            onAdd = { url, name ->
                addSubOpen = false
                scope.launch { Actions.addSubscription(url, name) }
            }
        )
    }
    if (confirmDead) {
        val n = servers.count { it.isDead }
        AlertDialog(
            onDismissRequest = { confirmDead = false },
            title = { Text("Удалить нерабочие?") },
            text = { Text("Будет удалено серверов: $n. Серверы из подписок вернутся при следующем обновлении.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDead = false
                    val k = Actions.removeDead()
                    toast("Удалено: $k")
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDead = false }) { Text("Отмена") } }
        )
    }
}

@Composable
fun ServerRow(
    s: Server,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onFav: () -> Unit,
    onTest: () -> Unit,
) {
    val borderColor = when {
        active -> Good
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(s.flagEmoji().ifEmpty { "🌐" }, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.cleanName(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.protocolLabel()} · ${s.network} · ${s.securityLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val extra = buildList<String> {
                    if (s.tcpPing >= 0) add("TCP ${s.tcpPing} ms")
                    val st = s.stability()
                    if (st >= 0) add("стабильность $st%")
                    if (s.lastTested > 0) add(Fmt.ago(s.lastTested))
                }
                if (extra.isNotEmpty()) {
                    Text(
                        extra.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                if (s.isDead && s.lastError.isNotEmpty()) {
                    Text(
                        s.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = Bad,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                DelayBadge(s.delay, onClick = onTest)
                Row {
                    IconButton(onClick = onFav, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Избранное",
                            tint = if (s.favorite) Mid else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = onInfo, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = "Подробнее", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SubInfoCard(sub: Subscription) {
    val now = System.currentTimeMillis()
    SectionCard(title = "📦 " + sub.name.ifEmpty { "Подписка" } + "  ·  ${sub.serverCount} серв.") {
        val used = sub.upload + sub.download
        if (sub.total > 0) {
            LinearProgressIndicator(
                progress = { (used.toFloat() / sub.total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Трафик: ${Fmt.bytes(used)} из ${Fmt.bytes(sub.total)} (осталось ${Fmt.bytes((sub.total - used).coerceAtLeast(0))})",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (used > 0) {
            Text("Трафик: ${Fmt.bytes(used)} · без лимита", style = MaterialTheme.typography.bodySmall)
        }
        if (sub.expire > 0) {
            val days = (sub.expire * 1000 - now) / 86_400_000L
            Text(
                if (days >= 0) "Действует до ${Fmt.date(sub.expire * 1000)} · осталось $days дн."
                else "Истекла ${Fmt.date(sub.expire * 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (days < 3) Bad else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (sub.announce.isNotBlank()) Text("📢 ${sub.announce}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Обновлено: ${Fmt.ago(sub.lastUpdated)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyServers(onPaste: () -> Unit, onAddSub: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🛰", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text("Серверов пока нет", style = MaterialTheme.typography.titleMedium)
        Text(
            "Добавьте подписку или вставьте ссылки vless:// vmess:// trojan:// ss:// hy2://",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddSub) { Text("Добавить подписку") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPaste) { Text("Вставить из буфера") }
    }
}
