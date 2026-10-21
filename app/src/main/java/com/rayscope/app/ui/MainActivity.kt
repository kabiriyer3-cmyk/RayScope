package com.rayscope.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rayscope.app.App
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.RayVpnService
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.util.toast
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by Repo.themeMode.collectAsState()
            RayTheme(mode) {
                val dark = ray.dark
                LaunchedEffect(dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val text: String? = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (text.isNullOrBlank()) return
        val payload: String = text
        App.scope.launch { Actions.importText(payload) }
    }
}

sealed class Route {
    data object Tabs : Route()
    data class Detail(val id: String) : Route()
    data object Apps : Route()
    data object Logs : Route()
    data object Subs : Route()
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) RayVpnService.start(ctx) else toast("Разрешение на VPN не выдано")
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val connect: () -> Unit = {
        if (Repo.selected() == null) {
            toast("Сначала добавьте сервер")
        } else {
            val i = VpnService.prepare(ctx)
            if (i != null) vpnLauncher.launch(i) else RayVpnService.start(ctx)
        }
    }
    val toggle: () -> Unit = {
        when (VpnState.status.value.state) {
            ConnState.STOPPED -> connect()
            ConnState.CONNECTED, ConnState.CONNECTING -> RayVpnService.stop()
            else -> {}
        }
    }

    BackHandler(enabled = route != Route.Tabs) { route = Route.Tabs }

    val rootBackdrop = rememberBackdrop()
    RayBackground {
        BackdropSource(rootBackdrop, Modifier.fillMaxSize()) {
            when (val r = route) {
                is Route.Detail -> ServerDetailScreen(r.id, onBack = { route = Route.Tabs }, onConnect = connect)
                Route.Apps -> AppsScreen(onBack = { route = Route.Tabs })
                Route.Logs -> LogsScreen(onBack = { route = Route.Tabs })
                Route.Subs -> SubsScreen(onBack = { route = Route.Tabs })
                Route.Tabs -> when (tab) {
                    0 -> DashboardScreen(onToggle = toggle, onGoServers = { tab = 1 }, onGoIp = { tab = 2 })
                    1 -> ServersScreen(onOpen = { route = Route.Detail(it) }, onToggle = toggle)
                    2 -> IpCheckScreen()
                    else -> SettingsScreen(
                        onApps = { route = Route.Apps },
                        onLogs = { route = Route.Logs },
                        onSubs = { route = Route.Subs }
                    )
                }
            }
        }
        if (route == Route.Tabs) {
            CompositionLocalProvider(LocalBackdrop provides rootBackdrop) {
                GlassTabBar(tab = tab, onTab = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private data class TabItem(val icon: ImageVector, val label: String)

@Composable
private fun GlassTabBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ray
    val items = listOf(
        TabItem(Icons.Filled.Home, "Главная"),
        TabItem(Icons.AutoMirrored.Filled.List, "Серверы"),
        TabItem(Icons.Filled.LocationOn, "IP"),
        TabItem(Icons.Filled.Settings, "Настройки"),
    )
    GlassSurface(32.dp, modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { i, item ->
                val sel = tab == i
                Column(
                    Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (sel) c.text.copy(alpha = if (c.dark) 0.12f else 0.07f) else Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = if (sel) c.accent else c.text, modifier = Modifier.size(22.dp))
                    Text(
                        item.label, fontSize = 10.sp, color = if (sel) c.accent else c.text,
                        fontWeight = FontWeight.Medium, maxLines = 1
                    )
                }
            }
        }
    }
}
