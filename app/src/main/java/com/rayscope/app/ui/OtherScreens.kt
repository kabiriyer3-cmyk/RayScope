package com.rayscope.app.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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

    Column(Modifier.fillMaxSize()) {
        Header("Подписки", subtitle = "${subs.size}", onBack = onBack) {
            GlassIconButton(onClick = { addOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = c.accent, modifier = Modifier.size(20.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (subs.isEmpty()) {
                item {
                    Text("Подписок нет. Нажмите +, чтобы добавить URL.", color = c.hint, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                }
            }
            items(subs, key = { it.id }) { sub ->
                SubInfoCard(sub)
                Text(
                    sub.url, color = c.hint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 2.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip("Обновить", false) { scope.launch { Actions.updateSub(sub) } }
                    GlassChip("URL", false) {
                        clipboard.setText(AnnotatedString(sub.url))
                        toast("URL скопирован")
                    }
                    if (sub.supportUrl.isNotEmpty()) {
                        GlassChip("Поддержка", false) {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.supportUrl)))
                            } catch (e: Exception) {
                                toast("Не удалось открыть")
                            }
                        }
                    }
                    GlassChip("Удалить", false) { toDelete = sub }
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

    Column(Modifier.fillMaxSize()) {
        Header("Приложения", subtitle = "Выбрано ${selected.size}", onBack = onBack) {
            GlassChip("Сброс", false) {
                selected = emptySet()
                Repo.settings.perAppSet = emptySet()
            }
        }
        GlassField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Поиск приложения",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Box(Modifier.padding(horizontal = 16.dp)) {
            SwitchRow("Показывать системные", null, showSystem) { showSystem = it }
        }
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                            .glass(c, RoundedCornerShape(16.dp))
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
                            Text(a.label, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Header("Журнал", subtitle = "${lines.size} записей", onBack = onBack) {
            GlassChip("Копировать", false) {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                toast("Журнал скопирован")
            }
            GlassChip("Очистить", false) { AppLog.clear() }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp).navigationBarsPadding()
                .glass(c, RoundedCornerShape(20.dp)),
            state = listState,
            contentPadding = PaddingValues(12.dp)
        ) {
            items(lines) { l ->
                Text(
                    l,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (l.contains("Ошибка") || l.contains("error", ignoreCase = true)) Bad else c.text,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
