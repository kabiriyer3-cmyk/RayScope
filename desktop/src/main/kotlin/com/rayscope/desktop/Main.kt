package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

val APP_VERSION: String = System.getProperty("rayscope.version") ?: "dev"

fun stateText(s: ConnState): String = when (s) {
    ConnState.CONNECTED -> "Защищено"
    ConnState.CONNECTING -> "Подключение…"
    ConnState.STOPPING -> "Отключение…"
    ConnState.STOPPED -> "Не подключено"
}

fun main() {
    System.setProperty("http.keepAlive", "false")
    Store.load()
    Log.add("RayScope $APP_VERSION для ПК. Данные: ${Paths.data.absolutePath}")
    if (Paths.xray() == null) Log.add("Ядро xray не найдено в ${Paths.home.absolutePath}")
    Runtime.getRuntime().addShutdownHook(Thread { Engine.shutdownNow() })
    Actions.go {
        SystemProxy.cleanupStale()
        val p = Store.prefs.value
        if (p.connectOnLaunch && Store.selected() != null) Engine.connect()
        if (p.autoUpdateSubs && Store.subs.value.isNotEmpty()) Actions.updateAllSubs(silent = true)
        if (p.testOnLaunch && Store.servers.value.isNotEmpty()) Actions.testAll()
    }

    application {
        val prefs by Store.prefs.collectAsState()
        val st by Engine.status.collectAsState()
        var visible by remember { mutableStateOf(true) }
        val icon = rememberVectorPainter(Ico.App)
        val tray = isTraySupported
        val quit: () -> Unit = {
            Engine.shutdownNow()
            exitApplication()
        }
        if (tray) {
            Tray(
                icon = icon,
                tooltip = "RayScope · " + stateText(st.state),
                onAction = { visible = true },
                menu = {
                    Item("Открыть RayScope", onClick = { visible = true })
                    Item(if (st.state == ConnState.STOPPED) "Подключить" else "Отключить", onClick = { Engine.toggle() })
                    Separator()
                    Item("Выход", onClick = quit)
                }
            )
        }
        Window(
            onCloseRequest = { if (prefs.closeToTray && tray) visible = false else quit() },
            visible = visible,
            title = "RayScope",
            icon = icon,
            state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 660) }
            App()
        }
    }
}

enum class Page(val title: String) {
    HOME("Главная"),
    SERVERS("Серверы"),
    CHECK("Проверка"),
    SETTINGS("Настройки"),
    LOG("Журнал"),
}

@Composable
fun App() {
    val prefs by Store.prefs.collectAsState()
    RayTheme(prefs.theme, prefs.accent) {
        val p = pal
        var page by remember { mutableStateOf(Page.HOME) }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            Notice.flow.collect { snack.showSnackbar(it) }
        }
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(page, prefs.dev) { page = it }
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.stroke))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (page) {
                        Page.HOME -> HomeScreen(onGoServers = { page = Page.SERVERS }, onGoCheck = { page = Page.CHECK })
                        Page.SERVERS -> ServersScreen()
                        Page.CHECK -> CheckScreen()
                        Page.SETTINGS -> SettingsScreen()
                        Page.LOG -> LogScreen()
                    }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(20.dp)) { d ->
                Snackbar(snackbarData = d, containerColor = p.raised, contentColor = p.text, shape = RoundedCornerShape(12.dp))
            }
        }
    }
}

@Composable
private fun Sidebar(page: Page, dev: Boolean, onPage: (Page) -> Unit) {
    val p = pal
    val st by Engine.status.collectAsState()
    Column(Modifier.width(236.dp).fillMaxHeight().background(p.side).padding(16.dp)) {
        Row(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(rememberVectorPainter(Ico.App), contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("RayScope", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("для Windows · $APP_VERSION", color = p.hint, fontSize = 11.sp)
            }
        }
        NavItem(Page.HOME, Ico.Home, page, onPage)
        NavItem(Page.SERVERS, Ico.Servers, page, onPage)
        NavItem(Page.CHECK, Ico.Radar, page, onPage)
        NavItem(Page.SETTINGS, Ico.Sliders, page, onPage)
        if (dev) NavItem(Page.LOG, Ico.Logs, page, onPage)
        Spacer(Modifier.weight(1f))

        val connected = st.state == ConnState.CONNECTED
        val busy = st.state == ConnState.CONNECTING || st.state == ConnState.STOPPING
        val col = when {
            connected -> Good
            busy -> Mid
            else -> p.hint
        }
        Column(Modifier.fillMaxWidth().card(p, 16.dp).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(8.dp))
                Text(stateText(st.state), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            val name = if (connected || busy) st.serverName else (Store.selected()?.cleanName() ?: "нет сервера")
            Text(name, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (connected) {
                Text(
                    "↓ ${Fmt.speed(st.down)}  ↑ ${Fmt.speed(st.up)}", color = p.hint, fontSize = 11.sp, style = TNum,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Btn(
                if (connected || busy) "Отключить" else "Подключить",
                { Engine.toggle() },
                Modifier.fillMaxWidth(),
                primary = !(connected || busy),
                color = if (connected || busy) Bad else Color.Unspecified,
                icon = Ico.Power,
                enabled = st.state != ConnState.STOPPING
            )
        }
    }
}

@Composable
private fun NavItem(target: Page, icon: ImageVector, page: Page, onPage: (Page) -> Unit) {
    val p = pal
    val sel = target == page
    val bg by animateColorAsState(if (sel) p.accent.copy(alpha = if (p.dark) 0.15f else 0.12f) else Color.Transparent)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable { onPage(target) }.hand().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (sel) p.accent else p.hint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            target.title, color = if (sel) p.text else p.hint, fontSize = 14.sp,
            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
