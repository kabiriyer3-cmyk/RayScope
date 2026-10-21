package com.rayscope.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
        actions = { ActionIcon(RayIcons.Plus, "Добавить", tint = c.accent) { addOpen = true } }
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") { LargeTitle("Подписки", "${subs.size}") }
            if (subs.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(RayIcons.Package, Indigo, 64.dp, 34.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Подписок нет", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Нажмите +, чтобы добавить URL", color = c.hint, fontSize = 14.sp)
                    }
                }
            }
            items(subs, key = { it.id }) { sub ->
                Column(Modifier.animateItem()) {
                    SubInfoCard(sub)
                    Text(
                        sub.url, color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 2.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Spacer(Modifier.height(10.dp))
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
            icon = { IconBadge(RayIcons.Trash, Bad, 44.dp, 24.dp) },
            title = { Text("Удалить подписку?") },
            text = { Text("«${d.name}» и все её серверы будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeSub(d.id)
                    toDelete = null
                }) { Text("Удалить", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
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
            item(key = "title") { LargeTitle("Приложения", "Выбрано ${selected.size}") }
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
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).card(c)) {
                    SettingSwitch(RayIcons.Settings, Gray, "Показывать системные", null, showSystem, divider = false) { showSystem = it }
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
                    val r = 14.dp
                    val shape = when {
                        first && last -> RoundedCornerShape(r)
                        first -> RoundedCornerShape(topStart = r, topEnd = r)
                        last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        else -> RectangleShape
                    }
                    val dividerColor = c.divider
                    Row(
                        Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp).clip(shape).background(c.card)
                            .clickable { toggle(a.pkg) }
                            .drawBehind {
                                if (!last) {
                                    val y = size.height - 0.25.dp.toPx()
                                    drawLine(dividerColor, Offset(52.dp.toPx(), y), Offset(size.width, y), 0.5.dp.toPx())
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CheckCircle(a.pkg in selected)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.pkg, color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
            item(key = "title") { LargeTitle("Журнал", "${lines.size} записей") }
            items(lines) { l ->
                val err = l.contains("Ошибка") || l.contains("error", ignoreCase = true)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 1.dp)) {
                    Box(
                        Modifier.padding(top = 5.dp).size(5.dp).clip(RoundedCornerShape(50))
                            .background(if (err) Bad else c.hint.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        l,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (err) Bad else c.text
                    )
                }
            }
        }
    }
}
