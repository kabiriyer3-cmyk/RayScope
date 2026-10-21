package com.rayscope.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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

    Page(
        onBack = onBack,
        actions = { ActionIcon(Icons.Filled.Add, "Добавить", tint = c.accent) { addOpen = true } }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") { LargeTitle("Подписки", "${subs.size}") }
            if (subs.isEmpty()) {
                item(key = "empty") {
                    Text("Подписок нет. Нажмите +, чтобы добавить URL.", color = c.hint, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
                }
            }
            items(subs, key = { it.id }) { sub ->
                SubInfoCard(sub)
                Text(
                    sub.url, color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 2.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Обновить", false) { scope.launch { Actions.updateSub(sub) } }
                    Chip("URL", false) {
                        clipboard.setText(AnnotatedString(sub.url))
                        toast("URL скопирован")
                    }
                    if (sub.supportUrl.isNotEmpty()) {
                        Chip("Поддержка", false) {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.supportUrl)))
                            } catch (e: Exception) {
                                toast("Не удалось открыть")
                            }
                        }
                    }
                    Chip("Удалить", false) { toDelete = sub }
                }
                Spacer(Modifier.height(8.dp))
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
        onBack = onBack,
        actions = {
            ActionText("Сброс") {
                selected = emptySet()
                Repo.settings.perAppSet = emptySet()
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = bottom)) {
            item(key = "title") { LargeTitle("Приложения", "Выбрано ${selected.size}") }
            item(key = "search") {
                InputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Поиск приложения",
                    onCard = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item(key = "sys") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).card(c).padding(horizontal = 16.dp)) {
                    SwitchRow("Показывать системные", null, showSystem) { showSystem = it }
                }
            }
            if (all == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent)
                    }
                }
            } else {
                items(shown, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).card(c, 12.dp)
                            .clickable { toggle(a.pkg) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = a.pkg in selected,
                            onCheckedChange = { toggle(a.pkg) },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.hint, checkmarkColor = Color.White)
                        )
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
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size)
    }
    Page(
        onBack = onBack,
        actions = {
            ActionText("Копировать") {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                toast("Журнал скопирован")
            }
            ActionText("Очистить", color = Bad) { AppLog.clear() }
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = bottom)
        ) {
            item(key = "title") { LargeTitle("Журнал", "${lines.size} записей") }
            items(lines) { l ->
                Text(
                    l,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (l.contains("Ошибка") || l.contains("error", ignoreCase = true)) Bad else c.text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp)
                )
            }
        }
    }
}
