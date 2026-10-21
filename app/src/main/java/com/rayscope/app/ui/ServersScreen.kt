package com.rayscope.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
private fun MItem(icon: ImageVector, text: String, color: Color = ray.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 15.sp, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp)) },
        onClick = onClick
    )
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
    var sort by rememberSaveable { mutableStateOf(SSort.entries.getOrElse(Repo.settings.sortMode) { SSort.DEFAULT }) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var addSubOpen by remember { mutableStateOf(false) }
    var confirmDead by remember { mutableStateOf(false) }
    val connected = vpn.state == ConnState.CONNECTED
    val busy = vpn.state == ConnState.CONNECTING || vpn.state == ConnState.STOPPING
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 100 } }

    val shown = remember(servers, filter, sort, query) { filterSort(servers, filter, sort, query) }
    val counts = remember(servers) { SFilter.entries.associateWith { f -> filterSort(servers, f, SSort.DEFAULT, "").size } }

    val pasteAction: () -> Unit = {
        val t = clipboard.getText()?.text.orEmpty()
        scope.launch { Actions.importText(t) }
    }
    val top = pageTop()
    val bottom = navBottom() + 170.dp
    val spin = if (testing) spinAngle(800) else null

    Page(
        title = "Серверы",
        scrolled = scrolled,
        actions = {
            ActionIcon(
                RayIcons.Refresh, "Проверить все", tint = c.accent, enabled = !testing,
                iconModifier = Modifier.graphicsLayer { rotationZ = spin?.value ?: 0f }
            ) { scope.launch { Actions.testAll() } }
            Box {
                ActionIcon(RayIcons.Sort, "Сортировка") { sortOpen = true }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.title, fontSize = 15.sp) },
                            trailingIcon = {
                                if (s == sort) Icon(RayIcons.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                sort = s
                                Repo.settings.sortMode = s.ordinal
                                sortOpen = false
                            }
                        )
                    }
                }
            }
            Box {
                ActionIcon(RayIcons.More, "Меню") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MItem(RayIcons.Clipboard, "Импорт из буфера") {
                        menuOpen = false
                        pasteAction()
                    }
                    MItem(RayIcons.Plus, "Добавить подписку") {
                        menuOpen = false
                        addSubOpen = true
                    }
                    MItem(RayIcons.Download, "Обновить подписки") {
                        menuOpen = false
                        scope.launch { Actions.updateAllSubs(false) }
                    }
                    MItem(RayIcons.Signal, "TCP-пинг всех") {
                        menuOpen = false
                        scope.launch { Actions.tcpAll() }
                    }
                    MItem(RayIcons.Bolt, "Лучший сервер") {
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
                    MItem(RayIcons.Share, "Скопировать все ссылки") {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(Actions.exportAll()))
                        toast("Ссылки скопированы")
                    }
                    MItem(RayIcons.Clock, "Сбросить результаты") {
                        menuOpen = false
                        Repo.resetTests()
                    }
                    MItem(RayIcons.Trash, "Удалить нерабочие", color = Bad) {
                        menuOpen = false
                        confirmDead = true
                    }
                }
            }
        },
        overlay = {
            val fabColor = if (connected) Bad else c.accent
            GlassSurface(
                26.dp,
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 88.dp).height(52.dp),
                tint = fabColor.copy(alpha = 0.86f),
                enabled = !busy,
                onClick = onToggle
            ) {
                val fg = if (connected) Color.White else c.onAccent
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (connected) RayIcons.Close else RayIcons.Power,
                        contentDescription = null, tint = fg, modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (vpn.state) {
                            ConnState.CONNECTED -> "Отключить"
                            ConnState.CONNECTING -> "Подключение…"
                            ConnState.STOPPING -> "Отключение…"
                            ConnState.STOPPED -> "Подключить"
                        },
                        color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") {
                ScreenTitle(
                    "Серверы",
                    "${servers.size} всего · ${counts[SFilter.WORKING] ?: 0} рабочих · ${sort.title.lowercase()}"
                )
            }
            item(key = "search") {
                InputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Имя, адрес, протокол…",
                    onCard = false,
                    leading = RayIcons.Search,
                    trailing = if (query.isNotEmpty()) {
                        {
                            Icon(
                                RayIcons.Close, contentDescription = "Очистить", tint = c.hint,
                                modifier = Modifier.size(18.dp).clip(CircleShape).clickable { query = "" }
                            )
                        }
                    } else null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(SFilter.entries.toList()) { f ->
                        Chip(f.title, selected = filter == f, count = counts[f] ?: 0) { filter = f }
                    }
                }
            }
            if (testing) {
                item(key = "progress") {
                    Column(Modifier.animateItem().padding(horizontal = 22.dp, vertical = 6.dp)) {
                        SlimProgress(if (prog.second == 0) 0f else prog.first.toFloat() / prog.second)
                        Text(
                            "Проверка ${prog.first} из ${prog.second} · ${Actions.modeLabel()}",
                            color = c.hint, fontSize = 12.sp, style = TNum, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            if (servers.isEmpty()) {
                item(key = "empty") { EmptyServers(onPaste = pasteAction, onAddSub = { addSubOpen = true }) }
            } else {
                if (filter == SFilter.ALL && query.isBlank() && subs.isNotEmpty()) {
                    item(key = "subs") {
                        if (subs.size == 1) {
                            SubInfoCard(subs.first(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(subs, key = { it.id }) { sub -> SubInfoCard(sub, Modifier.width(290.dp)) }
                            }
                        }
                    }
                    item(key = "gap") { Spacer(Modifier.height(6.dp)) }
                }
                if (shown.isEmpty()) {
                    item(key = "nothing") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconTile(RayIcons.Filter, c.hint, 52.dp, 26.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Нет серверов под этот фильтр", color = c.hint, fontSize = 15.sp)
                        }
                    }
                }
                items(shown, key = { it.id }) { s ->
                    ServerCard(
                        s = s,
                        selected = s.id == selectedId,
                        active = connected && vpn.serverId == s.id,
                        modifier = Modifier.animateItem(),
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
            containerColor = c.surface,
            icon = { IconTile(RayIcons.Trash, Bad, 46.dp, 24.dp) },
            title = { Text("Удалить нерабочие?", color = c.text) },
            text = { Text("Будет удалено серверов: $n. Серверы из подписок вернутся при следующем обновлении.", color = c.hint) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDead = false
                    val k = Actions.removeDead()
                    toast("Удалено: $k")
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDead = false }) { Text("Отмена", color = c.hint) } }
        )
    }
}

/** One server = one card: flag, name, tags, latency readout with signal bars. */
@Composable
fun ServerCard(
    s: Server,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onFav: () -> Unit,
    onTest: () -> Unit,
) {
    val c = ray
    val borderColor = when {
        active -> Good
        selected -> c.accent
        else -> c.stroke
    }
    val pop = remember { Animatable(1f) }
    val prevFav = remember { BooleanArray(1) { s.favorite } }
    LaunchedEffect(s.favorite) {
        if (prevFav[0] != s.favorite) {
            prevFav[0] = s.favorite
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .card(c, 18.dp, borderColor, if (active || selected) 1.5.dp else 1.dp)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FlagBox(s.flagEmoji(), 42.dp)
            if (selected || active) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(16.dp)
                        .clip(CircleShape).background(c.surface).padding(2.5.dp)
                        .clip(CircleShape).background(if (active) Good else c.accent)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.cleanName(), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(s.protocolLabel().uppercase(), if (active) Good else c.accent)
                Spacer(Modifier.width(6.dp))
                val extra = buildList {
                    add(s.network)
                    add(s.securityLabel())
                    if (s.tcpPing >= 0) add("tcp ${s.tcpPing}")
                    val st = s.stability()
                    if (st >= 0) add("$st%")
                }
                Text(
                    extra.joinToString(" · "), color = c.hint, fontSize = 12.sp, style = TNum,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (s.isDead && s.lastError.isNotEmpty()) {
                Text(
                    s.lastError, color = Bad, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Column(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).bounceClick(onClick = onTest)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            LatencyText(s.delay)
            Spacer(Modifier.height(4.dp))
            SignalBars(s.delay)
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onFav),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (s.favorite) RayIcons.StarFilled else RayIcons.Star,
                contentDescription = "Избранное",
                tint = if (s.favorite) Yellow else c.hint.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
            )
        }
        Box(
            Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onInfo),
            contentAlignment = Alignment.Center
        ) {
            Icon(RayIcons.Chevron, contentDescription = "Подробнее", tint = c.hint.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SubInfoCard(sub: Subscription, modifier: Modifier = Modifier) {
    val c = ray
    val now = System.currentTimeMillis()
    val used = sub.upload + sub.download
    Column(modifier.fillMaxWidth().card(c, 18.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(RayIcons.Package, Indigo, 32.dp, 17.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name.ifEmpty { "Подписка" }, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${sub.serverCount} серверов · обновлена ${Fmt.ago(sub.lastUpdated)}",
                    color = c.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (sub.total > 0) {
            val frac = used.toFloat() / sub.total.toFloat()
            SlimProgress(frac, Modifier.padding(top = 10.dp, bottom = 5.dp), color = if (frac > 0.9f) Bad else c.accent)
            Text(
                "${Fmt.bytes(used)} из ${Fmt.bytes(sub.total)} · осталось ${Fmt.bytes((sub.total - used).coerceAtLeast(0))}",
                color = c.hint, fontSize = 12.sp, style = TNum, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        } else if (used > 0) {
            Text("Трафик: ${Fmt.bytes(used)} · без лимита", color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (sub.expire > 0) {
            val days = (sub.expire * 1000 - now) / 86_400_000L
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(RayIcons.Clock, contentDescription = null, tint = if (days < 3) Bad else c.hint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (days >= 0) "до ${Fmt.date(sub.expire * 1000)} · осталось $days дн." else "истекла ${Fmt.date(sub.expire * 1000)}",
                    color = if (days < 3) Bad else c.hint, fontSize = 12.sp, maxLines = 1
                )
            }
        }
        if (sub.announce.isNotBlank()) {
            Text(sub.announce, color = c.text, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun EmptyServers(onPaste: () -> Unit, onAddSub: () -> Unit) {
    val c = ray
    Column(
        Modifier.appear(0).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).card(c, 24.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).border(1.dp, c.stroke, CircleShape).background(c.raised),
            contentAlignment = Alignment.Center
        ) {
            Icon(RayIcons.Radar, contentDescription = null, tint = c.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Серверов пока нет", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Добавьте подписку или вставьте ссылки vless / vmess / trojan / ss / hy2",
            color = c.hint, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        PillButton("Добавить подписку", onClick = onAddSub, primary = true, icon = RayIcons.Plus, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        PillButton("Вставить из буфера", onClick = onPaste, icon = RayIcons.Clipboard, modifier = Modifier.fillMaxWidth())
    }
}
