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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }

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

    val rootBackdrop = rememberBackdrop()
    RayBackground {
        BackdropSource(rootBackdrop, Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stack,
                transitionSpec = {
                    if (!motion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState.size >= initialState.size) {
                        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeOut(tween(200)))
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
                                (fadeIn(tween(220, delayMillis = 50)) + scaleIn(tween(260, delayMillis = 50), initialScale = 0.98f)) togetherWith
                                    fadeOut(tween(110))
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
        }
        AnimatedVisibility(
            visible = stack.isEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
        ) {
            CompositionLocalProvider(LocalBackdrop provides rootBackdrop) {
                GlassTabBar(tab = tab, onTab = { tab = it })
            }
        }
    }
}

private data class TabItem(val icon: ImageVector, val label: String)

/**
 * Floating Liquid Glass tab bar. The active tab expands into a labelled capsule (optional); the
 * highlight moves like a drop of liquid: the leading edge runs ahead on a stiff spring while the
 * trailing edge catches up on a soft one, so it stretches in flight and settles back into shape.
 */
@Composable
private fun GlassTabBar(tab: Int, onTab: (Int) -> Unit) {
    val c = ray
    val labels = look.tabLabels
    val items = listOf(
        TabItem(RayIcons.Home, "Главная"),
        TabItem(RayIcons.Servers, "Серверы"),
        TabItem(RayIcons.Radar, "Проверка"),
        TabItem(RayIcons.Sliders, "Настройки"),
    )
    val small = if (labels) 54.dp else 62.dp
    val big = if (labels) 122.dp else 62.dp
    val h = 50.dp
    val prev = remember { IntArray(1) { tab } }
    val movingRight = tab >= prev[0]
    val lead = spring<Dp>(dampingRatio = 0.72f, stiffness = 650f)
    val trail = spring<Dp>(dampingRatio = 0.78f, stiffness = 260f)
    val left by animateDpAsState(small * tab, if (movingRight) trail else lead, label = "tabLeft")
    val right by animateDpAsState(small * tab + big, if (movingRight) lead else trail, label = "tabRight")
    SideEffect { prev[0] = tab }

    GlassSurface(30.dp, Modifier.navigationBarsPadding().padding(bottom = 10.dp)) {
        Box(Modifier.padding(5.dp)) {
            Box(
                Modifier.offset(x = left).width((right - left).coerceAtLeast(0.dp)).height(h)
                    .clip(c.shape(25.dp))
                    .background(c.accent.copy(alpha = if (c.dark) 0.17f else 0.13f))
            )
            Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    val sel = i == tab
                    val w by animateDpAsState(
                        if (sel) big else small,
                        spring(dampingRatio = 0.8f, stiffness = 420f),
                        label = "tabW$i"
                    )
                    TabButton(item, sel, labels, Modifier.width(w).height(h)) { onTab(i) }
                }
            }
        }
    }
}

@Composable
private fun TabButton(item: TabItem, selected: Boolean, labels: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ray
    val color by animateColorAsState(if (selected) c.accent else c.text.copy(alpha = 0.78f), tween(220), label = "tabColor")
    val bump = remember { Animatable(1f) }
    val motion = look.motion
    LaunchedEffect(selected) {
        if (selected && motion) {
            bump.snapTo(0.8f)
            bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    Row(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon, contentDescription = item.label, tint = color,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
        )
        AnimatedVisibility(
            visible = selected && labels,
            enter = fadeIn(tween(180, delayMillis = 60)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180))
        ) {
            Text(
                item.label, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(start = 7.dp)
            )
        }
    }
}
