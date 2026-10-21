@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
private fun BackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
}

// ---------------- Subscriptions ----------------

@Composable
fun SubsScreen(onBack: () -> Unit) {
    val subs by Repo.subs.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var addOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Subscription?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Подписки") },
                navigationIcon = { BackIcon(onBack) },
                actions = {
                    IconButton(onClick = { addOpen = true }) { Icon(Icons.Filled.Add, contentDescription = "Добавить") }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            if (subs.isEmpty()) {
                item {
                    Text(
                        "Подписок нет. Нажмите + чтобы добавить URL.",
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(subs, key = { it.id }) { sub ->
                SubInfoCard(sub)
                Text(
                    sub.url,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { scope.launch { Actions.updateSub(sub) } }) { Text("Обновить") }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(sub.url))
                        toast("URL скопирован")
                    }) { Text("URL") }
                    if (sub.supportUrl.isNotEmpty()) {
                        TextButton(onClick = {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.supportUrl)))
                            } catch (e: Exception) {
                                toast("Не удалось открыть")
                            }
                        }) { Text("Поддержка") }
                    }
                    TextButton(onClick = { toDelete = sub }) { Text("Удалить", color = Bad) }
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
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

// ---------------- Per-app picker ----------------

private data class AppItem(val label: String, val pkg: String, val system: Boolean)

@Composable
fun AppsScreen(onBack: () -> Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приложения (${selected.size})") },
                navigationIcon = { BackIcon(onBack) },
                actions = {
                    TextButton(onClick = {
                        selected = emptySet()
                        Repo.settings.perAppSet = emptySet()
                    }) { Text("Сбросить") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск приложения") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Box(Modifier.padding(horizontal = 12.dp)) {
                SwitchRow("Показывать системные", null, showSystem) { showSystem = it }
            }
            if (all == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle(a.pkg) }.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = a.pkg in selected, onCheckedChange = { toggle(a.pkg) })
                            Column(Modifier.weight(1f)) {
                                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(a.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
    val lines by AppLog.lines.collectAsState()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал") },
                navigationIcon = { BackIcon(onBack) },
                actions = {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                        toast("Журнал скопирован")
                    }) { Text("Копировать") }
                    TextButton(onClick = { AppLog.clear() }) { Text("Очистить") }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp), state = listState) {
            items(lines) { l ->
                Text(
                    l,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (l.contains("Ошибка") || l.contains("error", ignoreCase = true)) Bad else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
