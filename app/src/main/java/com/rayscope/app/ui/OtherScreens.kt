package com.rayscope.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.model.Subscription
import com.rayscope.app.util.AppLog
import com.rayscope.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- Subscriptions ----------------

@Composable
fun SubsScreen(onBack: () -> Unit) {
    val c = ray
    val subs by Repo.subs.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var addOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Subscription?>(null) }
    val top = pageTop()
    val bottom = navBottom() + 24.dp
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 100 } }

    Page(
        title = "Подписки",
        scrolled = scrolled,
        onBack = onBack,
        actions = {
            ActionIcon(RayIcons.Download, "Обновить все", tint = c.text) { scope.launch { Actions.updateAllSubs(false) } }
            ActionIcon(RayIcons.Plus, "Добавить", tint = c.accent) { addOpen = true }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") { ScreenTitle("Подписки", "${subs.size} · ${subs.sumOf { it.serverCount }} серверов") }
            if (subs.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp).card(c, 24.dp).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconTile(RayIcons.Package, Indigo, 64.dp, 32.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Подписок нет", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Нажмите +, чтобы добавить URL", color = c.hint, fontSize = 14.sp)
                    }
                }
            }
            items(subs, key = { it.id }) { sub ->
                Column(Modifier.animateItem().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SubInfoCard(sub)
                    Text(
                        sub.url, color = c.hint, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniAction(RayIcons.Refresh, "Обновить", c.accent) { scope.launch { Actions.updateSub(sub) } }
                        MiniAction(RayIcons.Copy, "URL", c.accent) {
                            clipboard.setText(AnnotatedString(sub.url))
                            toast("URL скопирован")
                        }
                        if (sub.supportUrl.isNotEmpty()) {
                            MiniAction(RayIcons.Link, "Поддержка", c.accent) {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.supportUrl)))
                                } catch (e: Exception) {
                                    toast("Не удалось открыть")
                                }
                            }
                        }
                        MiniAction(RayIcons.Trash, "Удалить", Bad) { toDelete = sub }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddSubDialog(
            onDismiss = { addOpen = false },
            onAdd = { url, name ->
                addOpen = false
                scope.launch { Actions.addSubscription(url, name) }
            }
        )
    }
    val d = toDelete
    if (d != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = c.surface,
            icon = { IconTile(RayIcons.Trash, Bad, 46.dp, 24.dp) },
            title = { Text("Удалить подписку?", color = c.text) },
            text = { Text("«${d.name}» и все её серверы будут удалены.", color = c.hint) },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeSub(d.id)
                    toDelete = null
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена", color = c.hint) } }
        )
    }
}

// ---------------- Per-app picker ----------------

private data class AppItem(val label: String, val pkg: String, val system: Boolean)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val c = ray
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var selected by remember { mutableStateOf(Repo.settings.perAppSet) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val top = pageTop()
    val bottom = navBottom() + 24.dp
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 100 } }
    val mode = Repo.settings.perAppMode

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.getInstalledApplications(0)
                .filter { it.packageName != ctx.packageName }
                .map { AppItem(pm.getApplicationLabel(it).toString(), it.packageName, (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
        Repo.settings.perAppSet = selected
    }

    val all = apps
    val shown = remember(all, query, showSystem, selected) {
        (all ?: emptyList())
            .filter { (showSystem || !it.system || it.pkg in selected) && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) }
            .sortedByDescending { it.pkg in selected }
    }

    Page(
        title = "Приложения",
        scrolled = scrolled,
        onBack = onBack,
        actions = {
            ActionText("Сброс") {
                selected = emptySet()
                Repo.settings.perAppSet = emptySet()
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") {
                ScreenTitle(
                    "Приложения",
                    "Выбрано ${selected.size} · " + when (mode) {
                        1 -> "пойдут мимо VPN"
                        2 -> "только они через VPN"
                        else -> "режим «все через VPN»"
                    }
                )
            }
            item(key = "search") {
                InputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Поиск приложения",
                    onCard = false,
                    leading = RayIcons.Search,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item(key = "sys") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).card(c, 18.dp)) {
                    PrefSwitch(RayIcons.Settings, Gray, "Показывать системные", null, showSystem, divider = false) { showSystem = it }
                }
            }
            if (all == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
                    val first = i == 0
                    val last = i == shown.lastIndex
                    val r = 18.dp
                    val shape = when {
                        first && last -> RoundedCornerShape(r)
                        first -> RoundedCornerShape(topStart = r, topEnd = r)
                        last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        else -> RectangleShape
                    }
                    val on = a.pkg in selected
                    Row(
                        Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp).clip(shape).background(c.surface)
                            .clickable { toggle(a.pkg) }
                            .bottomLine(c.stroke, 54.dp, !last)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckCircle(on)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.pkg, color = c.hint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (a.system) Tag("SYS")
                    }
                }
            }
        }
    }
}

// ---------------- Logs ----------------

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val c = ray
    val lines by AppLog.lines.collectAsState()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val top = pageTop()
    val bottom = navBottom() + 24.dp
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size)
    }
    Page(
        title = "Журнал",
        scrolled = scrolled,
        onBack = onBack,
        actions = {
            ActionIcon(RayIcons.Copy, "Копировать", tint = c.accent) {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                toast("Журнал скопирован")
            }
            ActionIcon(RayIcons.Trash, "Очистить", tint = Bad) { AppLog.clear() }
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = bottom)
        ) {
            item(key = "title") { ScreenTitle("Журнал", "${lines.size} записей") }
            items(lines) { l ->
                val err = l.contains("Ошибка") || l.contains("error", ignoreCase = true) || l.contains("не прошёл")
                val time = l.substringBefore("  ", "")
                val msg = if (time.isEmpty()) l else l.substringAfter("  ")
                Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                    Text(
                        time, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.hint,
                        modifier = Modifier.width(60.dp)
                    )
                    Box(
                        Modifier.padding(top = 5.dp, end = 8.dp).size(5.dp).clip(CircleShape)
                            .background(if (err) Bad else c.accent.copy(alpha = 0.6f))
                    )
                    Text(
                        msg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (err) Bad else c.text
                    )
                }
            }
        }
    }
}
