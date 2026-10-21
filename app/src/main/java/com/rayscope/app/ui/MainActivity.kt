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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState != Route.Tabs) {
                        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "route"
            ) { r ->
                when (r) {
                    is Route.Detail -> ServerDetailScreen(r.id, onBack = { route = Route.Tabs }, onConnect = connect)
                    Route.Apps -> AppsScreen(onBack = { route = Route.Tabs })
                    Route.Logs -> LogsScreen(onBack = { route = Route.Tabs })
                    Route.Subs -> SubsScreen(onBack = { route = Route.Tabs })
                    Route.Tabs -> AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 50)) + scaleIn(tween(260, delayMillis = 50), initialScale = 0.97f)) togetherWith
                                fadeOut(tween(110))
                        },
                        label = "tab"
                    ) { t ->
                        when (t) {
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
            }
        }
        AnimatedVisibility(
            visible = route == Route.Tabs,
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
 * Floating Liquid Glass tab bar. The selection pill moves like a drop of liquid: the leading edge
 * runs ahead on a stiff spring while the trailing edge catches up on a soft one, so the pill
 * stretches in flight and settles back into shape.
 */
@Composable
private fun GlassTabBar(tab: Int, onTab: (Int) -> Unit) {
    val c = ray
    val items = listOf(
        TabItem(RayIcons.Home, "Главная"),
        TabItem(RayIcons.Servers, "Серверы"),
        TabItem(RayIcons.Globe, "IP"),
        TabItem(RayIcons.Settings, "Настройки"),
    )
    val itemW = 76.dp
    val prev = remember { IntArray(1) { tab } }
    val movingRight = tab >= prev[0]
    val lead = spring<Dp>(dampingRatio = 0.72f, stiffness = 700f)
    val trail = spring<Dp>(dampingRatio = 0.72f, stiffness = 240f)
    val left by animateDpAsState(itemW * tab, if (movingRight) trail else lead, label = "tabLeft")
    val right by animateDpAsState(itemW * (tab + 1), if (movingRight) lead else trail, label = "tabRight")
    SideEffect { prev[0] = tab }

    GlassSurface(32.dp, Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
        Box(Modifier.padding(4.dp)) {
            Box(
                Modifier.offset(x = left).width(right - left).height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(c.text.copy(alpha = if (c.dark) 0.13f else 0.07f))
            )
            Row(horizontalArrangement = Arrangement.Start) {
                items.forEachIndexed { i, item ->
                    TabButton(item, i == tab, Modifier.width(itemW).height(54.dp)) { onTab(i) }
                }
            }
        }
    }
}

@Composable
private fun TabButton(item: TabItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = ray
    val color by animateColorAsState(if (selected) c.accent else c.text, tween(220), label = "tabColor")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bump.snapTo(0.78f)
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 600f))
        }
    }
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            item.icon, contentDescription = item.label, tint = color,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.label, fontSize = 10.sp, color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1
        )
    }
}
