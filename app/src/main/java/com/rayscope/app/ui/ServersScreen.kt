package com.rayscope.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
    ALL("Все"), WORKING("Рабочие"), DEAD("Нерабочие"), UNTESTED("Не проверены"), FAV("★")
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
    DropdownMenuItem(text = { Text(text, fontSize = 15.sp) }, onClick = onClick)
}

@Composable
fun ServersScreen(onOpen: (String) -> Unit, onToggle: () -> Unit) {
    val c = ray
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
    val top = pageTop()
    val bottom = navBottom() + 170.dp

    Page(
        actions = {
            ActionIcon(Icons.Filled.Search, "Поиск") { showSearch = !showSearch }
            ActionIcon(Icons.Filled.Refresh, "Проверить все", tint = c.accent, enabled = !testing) {
                scope.launch { Actions.testAll() }
            }
            Box {
                ActionGlyph("⇅") { sortOpen = true }
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
                ActionIcon(Icons.Filled.MoreVert, "Меню") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MItem("📋  Импорт из буфера") {
                        menuOpen = false
                        pasteAction()
                    }
                    MItem("➕  Добавить подписку") {
                        menuOpen = false
                        addSubOpen = true
                    }
                    MItem("🔄  Обновить подписки") {
                        menuOpen = false
                        scope.launch { Actions.updateAllSubs(false) }
                    }
                    MItem("📡  TCP-пинг всех") {
                        menuOpen = false
                        scope.launch { Actions.tcpAll() }
                    }
                    MItem("⚡  Лучший сервер") {
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
                    MItem("🗑  Удалить нерабочие") {
                        menuOpen = false
                        confirmDead = true
                    }
                    MItem("♻  Сбросить результаты") {
                        menuOpen = false
                        Repo.resetTests()
                    }
                    MItem("📤  Скопировать все ссылки") {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(Actions.exportAll()))
                        toast("Ссылки скопированы")
                    }
                }
            }
        },
        overlay = {
            val fabColor = if (connected) Bad else c.accent
            GlassSurface(
                26.dp,
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 86.dp).height(52.dp),
                tint = fabColor.copy(alpha = 0.80f),
                enabled = !busy,
                onClick = onToggle
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (connected) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (vpn.state) {
                            ConnState.CONNECTED -> "Отключить"
                            ConnState.CONNECTING -> "Подключение…"
                            ConnState.STOPPING -> "Отключение…"
                            ConnState.STOPPED -> "Подключить"
                        },
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") {
                LargeTitle("Серверы", "${servers.size} всего · ${counts[SFilter.WORKING] ?: 0} рабочих")
            }
            if (showSearch) {
                item(key = "search") {
                    InputField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Имя, адрес, протокол…",
                        onCard = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(SFilter.entries.toList()) { f ->
                        Chip("${f.title} ${counts[f] ?: 0}", selected = filter == f) { filter = f }
                    }
                }
            }
            if (testing) {
                item(key = "progress") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        LinearProgressIndicator(
                            progress = { if (prog.second == 0) 0f else prog.first.toFloat() / prog.second },
                            modifier = Modifier.fillMaxWidth(),
                            color = c.accent,
                            trackColor = c.divider
                        )
                        Text("Проверка ${prog.first} / ${prog.second}…", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (servers.isEmpty()) {
                item(key = "empty") { EmptyServers(onPaste = pasteAction, onAddSub = { addSubOpen = true }) }
            } else {
                if (filter == SFilter.ALL && query.isBlank()) {
                    items(subs, key = { "sub_" + it.id }) { sub -> SubInfoCard(sub) }
                }
                if (shown.isEmpty()) {
                    item(key = "nothing") {
                        Text("Нет серверов под этот фильтр", color = c.hint, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
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
                }) { Text("Удалить", color = Bad) }
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
    val c = ray
    val shape = RoundedCornerShape(14.dp)
    var m: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).card(c)
    if (active) m = m.border(1.5.dp, Good, shape) else if (selected) m = m.border(1.5.dp, c.accent, shape)
    Row(
        m.clickable(onClick = onClick).padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagBox(s.flagEmoji(), 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.cleanName(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = buildList<String> {
                add(s.protocolLabel())
                add(s.network)
                add(s.securityLabel())
                if (s.tcpPing >= 0) add("TCP ${s.tcpPing}")
                val st = s.stability()
                if (st >= 0) add("$st%")
            }
            Text(extra.joinToString(" · "), color = c.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.isDead && s.lastError.isNotEmpty()) {
                Text(s.lastError, color = Bad, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DelayBadge(s.delay, onClick = onTest)
        IconButton(onClick = onFav, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Star, contentDescription = "Избранное",
                tint = if (s.favorite) Mid else c.hint.copy(alpha = 0.35f), modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "Подробнее", tint = c.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SubInfoCard(sub: Subscription) {
    val c = ray
    val now = System.currentTimeMillis()
    val used = sub.upload + sub.download
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).card(c).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📦", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                sub.name.ifEmpty { "Подписка" }, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text("${sub.serverCount} серв. · ${Fmt.ago(sub.lastUpdated)}", color = c.hint, fontSize = 12.sp)
        }
        if (sub.total > 0) {
            LinearProgressIndicator(
                progress = { (used.toFloat() / sub.total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(4.dp).clip(RoundedCornerShape(50)),
                color = c.accent,
                trackColor = c.field
            )
            Text(
                "${Fmt.bytes(used)} из ${Fmt.bytes(sub.total)} · осталось ${Fmt.bytes((sub.total - used).coerceAtLeast(0))}",
                color = c.hint, fontSize = 12.sp
            )
        } else if (used > 0) {
            Text("Трафик: ${Fmt.bytes(used)} · без лимита", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (sub.expire > 0) {
            val days = (sub.expire * 1000 - now) / 86_400_000L
            Text(
                if (days >= 0) "До ${Fmt.date(sub.expire * 1000)} · осталось $days дн." else "Истекла ${Fmt.date(sub.expire * 1000)}",
                color = if (days < 3) Bad else c.hint, fontSize = 12.sp
            )
        }
        if (sub.announce.isNotBlank()) {
            Text("📢 ${sub.announce}", color = c.text, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EmptyServers(onPaste: () -> Unit, onAddSub: () -> Unit) {
    val c = ray
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛰", fontSize = 52.sp)
        Spacer(Modifier.height(8.dp))
        Text("Серверов пока нет", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Добавьте подписку или вставьте ссылки vless / vmess / trojan / ss / hy2",
            color = c.hint, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PillButton("Добавить подписку", onClick = onAddSub, primary = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        PillButton("Вставить из буфера", onClick = onPaste, modifier = Modifier.fillMaxWidth())
    }
}
