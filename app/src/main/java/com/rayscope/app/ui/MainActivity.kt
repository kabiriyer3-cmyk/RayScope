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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.rayscope.fluid.PillTabs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val ui by Repo.ui.collectAsState()
            RayTheme(ui) {
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
        if (i.action == ACTION_CONNECT) {
            connectRequests.value = connectRequests.value + 1
            return
        }
        val text: String? = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (text.isNullOrBlank()) return
        val payload: String = text
        App.scope.launch { Actions.importText(payload) }
    }

    companion object {
        /** Sent by the home screen widget when connecting needs the UI (VPN permission, pick best). */
        const val ACTION_CONNECT = "com.rayscope.app.CONNECT"

        /** "Connect on launch" fires once per process, not on every rotation. */
        @Volatile
        var autoConnectDone = false

        val connectRequests = MutableStateFlow(0)

        @Volatile
        var handledConnectRequests = 0
    }
}

sealed class Route {
    data class Detail(val id: String) : Route()
    data object Apps : Route()
    data object Logs : Route()
    data object Subs : Route()
    data class Pref(val page: SetPage) : Route()
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = look.motion
    val haptics = look.haptics
    val haptic = LocalHapticFeedback.current
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var tab by rememberSaveable { mutableIntStateOf(Repo.ui.value.startTab) }
    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }
    val vpn by VpnState.status.collectAsState()

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

    // a short tick when the tunnel goes up or down
    val lastState = remember { arrayOf(vpn.state) }
    LaunchedEffect(vpn.state) {
        val prev = lastState[0]
        lastState[0] = vpn.state
        val settled = vpn.state == ConnState.CONNECTED || vpn.state == ConnState.STOPPED
        if (haptics && settled && prev != vpn.state) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val startVpn: () -> Unit = {
        val i = VpnService.prepare(ctx)
        if (i != null) vpnLauncher.launch(i) else RayVpnService.start(ctx)
    }
    val connect: () -> Unit = {
        if (Repo.servers.value.isEmpty()) {
            toast("Сначала добавьте сервер")
        } else if (Repo.settings.pickBestOnConnect) {
            scope.launch {
                toast("Ищу самый быстрый сервер…")
                val b = Actions.pickBest()
                if (b != null) Repo.select(b.id)
                startVpn()
            }
        } else {
            startVpn()
        }
    }
    val toggle: () -> Unit = {
        when (VpnState.status.value.state) {
            ConnState.STOPPED -> connect()
            ConnState.CONNECTED, ConnState.CONNECTING -> RayVpnService.stop()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        if (!MainActivity.autoConnectDone) {
            MainActivity.autoConnectDone = true
            if (Repo.settings.connectOnLaunch &&
                VpnState.status.value.state == ConnState.STOPPED &&
                Repo.selected() != null &&
                VpnService.prepare(ctx) == null
            ) {
                connect()
            }
        }
    }

    // connect requests from the home screen widget
    val connectReq by MainActivity.connectRequests.collectAsState()
    LaunchedEffect(connectReq) {
        if (connectReq > MainActivity.handledConnectRequests) {
            MainActivity.handledConnectRequests = connectReq
            stack = emptyList()
            tab = 0
            if (VpnState.status.value.state == ConnState.STOPPED) connect()
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

    RayBackground {
        AnimatedContent(
            targetState = stack,
            transitionSpec = {
                if (!motion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (targetState.size >= initialState.size) {
                    (slideInHorizontally(tween(200)) { it / 10 } + fadeIn(tween(180))) togetherWith fadeOut(tween(120))
                } else {
                    fadeIn(tween(180)) togetherWith (slideOutHorizontally(tween(200)) { it / 10 } + fadeOut(tween(140)))
                }
            },
            label = "route"
        ) { st ->
            when (val r = st.lastOrNull()) {
                null -> AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        if (!motion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                        }
                    },
                    label = "tab"
                ) { t ->
                    when (t) {
                        0 -> DashboardScreen(onToggle = toggle, onGoServers = { tab = 1 }, onGoIp = { tab = 2 })
                        1 -> ServersScreen(onOpen = { push(Route.Detail(it)) }, onToggle = toggle)
                        2 -> IpCheckScreen()
                        else -> SettingsScreen(onOpen = push)
                    }
                }
                is Route.Detail -> ServerDetailScreen(r.id, onBack = pop, onConnect = connect)
                Route.Apps -> AppsScreen(onBack = pop)
                Route.Logs -> LogsScreen(onBack = pop)
                Route.Subs -> SubsScreen(onBack = pop)
                is Route.Pref -> SettingsPageScreen(r.page, onBack = pop, onOpen = push)
            }
        }
        AnimatedVisibility(
            visible = stack.isEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(100))
        ) {
            BottomBar(tab = tab, connected = vpn.state == ConnState.CONNECTED, onTab = { tab = it })
        }
    }
}

private data class TabItem(val icon: ImageVector, val label: String)

/** Floating pill tab bar, the same one as on Windows: the active tab stretches into a pill with its label. */
@Composable
private fun BottomBar(tab: Int, connected: Boolean, onTab: (Int) -> Unit) {
    val c = ray
    val items = listOf(
        RayIcons.Home to "Главная",
        RayIcons.Servers to "Серверы",
        RayIcons.Radar to "Проверка",
        RayIcons.Sliders to "Настройки",
    )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        PillTabs(
            items = items,
            selected = tab,
            onSelect = onTab,
            track = if (c.dark) Color(0xFF1C1D21) else Color.White,
            pill = c.accent,
            onPill = c.onAccent,
            ink = c.hint,
            modifier = Modifier.border(1.dp, c.stroke, RoundedCornerShape(50)),
            dotIndex = 0,
            dot = if (connected && tab != 0) Good else Color.Unspecified,
            motion = LocalUi.current.motion,
        )
    }
}
