package com.rayscope.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.Dimension
import java.awt.Frame
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess

val APP_VERSION: String = System.getProperty("rayscope.version")
    ?: runCatching { File(Paths.home, "version.txt").readText().trim() }.getOrNull()?.ifEmpty { null }
    ?: "dev"

fun stateText(s: ConnState): String = when (s) {
    ConnState.CONNECTED -> "Подключено"
    ConnState.CONNECTING -> "Подключение…"
    ConnState.STOPPING -> "Отключение…"
    ConnState.STOPPED -> "Не подключено"
}

/** Signals from outside of Compose (second launch, tray) to the window. */
object UiSignals {
    val show = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
}

/** One RayScope at a time: a second launch asks the first one to show its window and exits. */
object SingleInstance {
    private const val PORT = 48791
    private var server: ServerSocket? = null

    /** true if this is the first copy */
    fun acquire(): Boolean {
        val s = try {
            ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            null
        }
        if (s != null) {
            server = s
            val t = Thread({
                while (!s.isClosed) {
                    try {
                        s.accept().use { c ->
                            c.soTimeout = 2000
                            val line = BufferedReader(InputStreamReader(c.getInputStream())).readLine()
                            if (line == "show") {
                                PrintWriter(c.getOutputStream(), true).println("rayscope")
                                UiSignals.show.tryEmit(Unit)
                            }
                        }
                    } catch (e: Exception) {
                        // keep listening
                    }
                }
            }, "single-instance")
            t.isDaemon = true
            t.start()
            return true
        }
        return try {
            Socket().use { c ->
                c.connect(InetSocketAddress("127.0.0.1", PORT), 1000)
                c.soTimeout = 1500
                PrintWriter(c.getOutputStream(), true).println("show")
                val r = BufferedReader(InputStreamReader(c.getInputStream())).readLine()
                r != "rayscope"
            }
        } catch (e: Exception) {
            true
        }
    }
}

/** Dark or light Windows title bar matching the app theme (Windows 10 2004+ / 11). */
object WinChrome {
    private val windows = System.getProperty("os.name", "").lowercase().contains("win")

    fun apply(w: java.awt.Window, dark: Boolean, caption: Color) {
        if (!windows) return
        try {
            val hwnd = Native.getWindowPointer(w) ?: return
            val fn = NativeLibrary.getInstance("dwmapi").getFunction("DwmSetWindowAttribute")
            fn.invokeInt(arrayOf<Any>(hwnd, 20, IntByReference(if (dark) 1 else 0), 4))
            val argb = caption.toArgb()
            val bgr = ((argb and 0xFF) shl 16) or (argb and 0xFF00) or ((argb shr 16) and 0xFF)
            fn.invokeInt(arrayOf<Any>(hwnd, 35, IntByReference(bgr), 4))
        } catch (t: Throwable) {
            Log.add("Заголовок окна: " + t.message)
        }
    }
}

fun main(args: Array<String>) {
    if (!SingleInstance.acquire()) exitProcess(0)
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
    // --tray comes from "start with Windows"
    val toTray = args.contains("--tray") || Store.prefs.value.startHidden

    application {
        val prefs by Store.prefs.collectAsState()
        val st by Engine.status.collectAsState()
        val tray = isTraySupported
        var visible by remember { mutableStateOf(!(toTray && tray)) }
        var raise by remember { mutableStateOf(0) }
        var page by remember { mutableStateOf(Page.entries.getOrElse(Store.prefs.value.startPage) { Page.HOME }) }
        var palette by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            UiSignals.show.collect {
                visible = true
                raise += 1
            }
        }
        val icon = rememberVectorPainter(Ico.App)
        val quit: () -> Unit = {
            Engine.shutdownNow()
            exitApplication()
        }
        if (tray) {
            Tray(
                icon = icon,
                tooltip = "RayScope · " + stateText(st.state),
                onAction = {
                    visible = true
                    raise += 1
                },
                menu = {
                    Item("Открыть RayScope", onClick = {
                        visible = true
                        raise += 1
                    })
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
            state = rememberWindowState(size = DpSize(1240.dp, 820.dp)),
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) {
                    false
                } else {
                    when (e.key) {
                        Key.K -> {
                            palette = !palette
                            true
                        }
                        Key.One -> { page = Page.HOME; palette = false; true }
                        Key.Two -> { page = Page.SERVERS; palette = false; true }
                        Key.Three -> { page = Page.CHECK; palette = false; true }
                        Key.Four -> { page = Page.SETTINGS; palette = false; true }
                        else -> false
                    }
                }
            },
        ) {
            val dark = when (prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val caption = palette(dark, prefs.accent).side
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 680) }
            LaunchedEffect(dark, visible) {
                if (visible) WinChrome.apply(window, dark, caption)
            }
            LaunchedEffect(raise) {
                if (raise > 0) {
                    if ((window.extendedState and Frame.ICONIFIED) != 0) window.extendedState = Frame.NORMAL
                    window.toFront()
                    window.requestFocus()
                }
            }
            App(page, { page = it }, palette, { palette = it })
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

private val NAV: List<Triple<Page, ImageVector, String>> = listOf(
    Triple(Page.HOME, Ico.Home, "1"),
    Triple(Page.SERVERS, Ico.Servers, "2"),
    Triple(Page.CHECK, Ico.Radar, "3"),
    Triple(Page.SETTINGS, Ico.Sliders, "4"),
)

@Composable
fun App(page: Page, onPage: (Page) -> Unit, palette: Boolean, onPalette: (Boolean) -> Unit) {
    val prefs by Store.prefs.collectAsState()
    RayTheme(prefs.theme, prefs.accent) {
        val p = pal
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            Notice.flow.collect { snack.showSnackbar(it) }
        }
        LaunchedEffect(prefs.dev) {
            if (!prefs.dev && page == Page.LOG) onPage(Page.SETTINGS)
        }
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(page, prefs.dev, onPage) { onPalette(true) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.stroke))
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "page"
                ) { pg ->
                    when (pg) {
                        Page.HOME -> HomeScreen(onGoServers = { onPage(Page.SERVERS) }, onGoCheck = { onPage(Page.CHECK) })
                        Page.SERVERS -> ServersScreen()
                        Page.CHECK -> CheckScreen()
                        Page.SETTINGS -> SettingsScreen(onOpenLog = { onPage(Page.LOG) })
                        Page.LOG -> LogScreen(onBack = { onPage(Page.SETTINGS) })
                    }
                }
            }
            if (palette) {
                CommandMenu(onPage = onPage, onClose = { onPalette(false) })
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { d ->
                Snackbar(snackbarData = d, containerColor = p.raised, contentColor = p.text, shape = RoundedCornerShape(8.dp))
            }
        }
    }
}

/** Left rail: brand, command menu, pages, and the connection at the bottom. */
@Composable
private fun Sidebar(page: Page, dev: Boolean, onPage: (Page) -> Unit, onPalette: () -> Unit) {
    val p = pal
    val st by Engine.status.collectAsState()
    Column(Modifier.width(232.dp).fillMaxHeight().background(p.side).padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RayMark(Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("RayScope", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(APP_VERSION, color = p.hint, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        val shape = RoundedCornerShape(6.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).border(1.dp, p.stroke, shape).press(onClick = onPalette)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Ico.Search, contentDescription = null, tint = p.hint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Команды", color = p.hint, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Kbd("Ctrl K")
        }
        Spacer(Modifier.height(14.dp))
        NAV.forEach { (pg, ic, key) ->
            NavItem(ic, pg.title, page == pg || (pg == Page.SETTINGS && page == Page.LOG && !dev), key,
                dot = if (pg == Page.HOME && st.state == ConnState.CONNECTED) Good else null) { onPage(pg) }
        }
        if (dev) {
            NavItem(Ico.Logs, Page.LOG.title, page == Page.LOG, null, null) { onPage(Page.LOG) }
        }
        Spacer(Modifier.weight(1f))
        ConnectionBlock(st)
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, key: String?, dot: Color?, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(shape)
            .background(if (selected) p.hover.copy(alpha = p.hover.alpha * 2.2f) else Color.Transparent)
            .press(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) p.text else p.hint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (selected) p.text else p.hint, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (dot != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        if (key != null) Text("Ctrl $key", color = p.hint.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Kbd(text: String) {
    val p = pal
    val shape = RoundedCornerShape(4.dp)
    Text(
        text, color = p.hint, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.clip(shape).border(1.dp, p.stroke, shape).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun ConnectionBlock(st: Status) {
    val p = pal
    val dot = when (st.state) {
        ConnState.CONNECTED -> Good
        ConnState.STOPPED -> p.hint.copy(alpha = 0.6f)
        else -> Mid
    }
    Column(Modifier.fillMaxWidth().card(p, 16.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(stateText(st.state), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        val name = if (st.state == ConnState.STOPPED) Store.selected()?.cleanName().orEmpty() else st.serverName
        if (name.isNotEmpty()) {
            Text(name, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 15.dp))
        }
        Spacer(Modifier.height(10.dp))
        Btn(
            if (st.state == ConnState.STOPPED) "Подключить" else "Отключить",
            { Engine.toggle() },
            Modifier.fillMaxWidth(),
            primary = st.state == ConnState.STOPPED,
            icon = Ico.Power,
            enabled = st.state != ConnState.STOPPING
        )
    }
}

private class Command(val title: String, val hint: String, val icon: ImageVector, val run: () -> Unit)

/** Ctrl+K: type to filter, arrows to move, Enter to run, Esc to close. Server names are commands too. */
@Composable
private fun CommandMenu(onPage: (Page) -> Unit, onClose: () -> Unit) {
    val p = pal
    val st by Engine.status.collectAsState()
    val servers by Store.servers.collectAsState()
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { focus.requestFocus() }

    val base = listOf(
        Command(if (st.state == ConnState.STOPPED) "Подключить" else "Отключить", "VPN", Ico.Power) { Engine.toggle() },
        Command("Лучший сервер", "проверить и подключиться", Ico.Bolt) {
            Actions.go {
                val b = Actions.pickBest()
                if (b != null) {
                    connectTo(b.id)
                    Notice.show("Лучший: " + b.cleanName() + " · " + b.delay + " ms")
                } else {
                    Notice.show("Рабочих серверов не найдено")
                }
            }
        },
        Command("Пинг всех серверов", "реальная задержка", Ico.Activity) { Actions.go { Actions.testAll() } },
        Command("Обновить подписки", "все", Ico.Download) { Actions.go { Actions.updateAllSubs(silent = false) } },
        Command("Системный прокси", "режим", Ico.Globe) { Engine.setMode(1) },
        Command("Только локальный прокси", "режим", Ico.Link) { Engine.setMode(0) },
        Command("Главная", "перейти", Ico.Home) { onPage(Page.HOME) },
        Command("Серверы", "перейти", Ico.Servers) { onPage(Page.SERVERS) },
        Command("Проверка IP", "перейти", Ico.Radar) { onPage(Page.CHECK) },
        Command("Настройки", "перейти", Ico.Sliders) { onPage(Page.SETTINGS) },
        Command("Тёмная тема", "оформление", Ico.Layers) { Store.setPrefs { it.copy(theme = 2) } },
        Command("Светлая тема", "оформление", Ico.Layers) { Store.setPrefs { it.copy(theme = 1) } },
        Command("Тема как в системе", "оформление", Ico.Layers) { Store.setPrefs { it.copy(theme = 0) } },
    )
    val q = query.trim()
    val shown = remember(q, st.state, servers) {
        val cmds = if (q.isEmpty()) base else base.filter { it.title.contains(q, true) || it.hint.contains(q, true) }
        val srv = if (q.isEmpty()) emptyList() else servers.filter { it.cleanName().contains(q, true) }.take(8).map { s ->
            Command(s.cleanName(), if (s.delay >= 0) "${s.delay} ms" else "сервер", Ico.Arrow) { connectTo(s.id) }
        }
        cmds + srv
    }
    LaunchedEffect(shown.size) { index = 0 }
    LaunchedEffect(index) { if (shown.isNotEmpty()) list.animateScrollToItem(index.coerceIn(0, shown.size - 1)) }
    val runAt: (Int) -> Unit = { i ->
        shown.getOrNull(i)?.let {
            onClose()
            it.run()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (p.dark) 0.45f else 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter
    ) {
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier.padding(top = 96.dp).width(580.dp).shadow(24.dp, shape).clip(shape).background(p.surface).border(1.dp, p.stroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.Search, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Команда или сервер", color = p.hint.copy(alpha = 0.7f), fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> {
                                    if (shown.isNotEmpty()) index = (index + 1) % shown.size
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (shown.isNotEmpty()) index = (index - 1 + shown.size) % shown.size
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    runAt(index)
                                    true
                                }
                                Key.Escape -> {
                                    onClose()
                                    true
                                }
                                else -> false
                            }
                        }
                    )
                }
                Kbd("Esc")
            }
            Line()
            if (shown.isEmpty()) {
                Text("Ничего не нашлось", color = p.hint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(6.dp), state = list) {
                    itemsIndexed(shown) { i, c ->
                        val sel = i == index
                        Row(
                            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (sel) p.hover.copy(alpha = p.hover.alpha * 2.2f) else Color.Transparent)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { runAt(i) }
                                .hand().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(c.icon, contentDescription = null, tint = if (sel) p.text else p.hint, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(c.title, color = p.text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(c.hint, color = p.hint, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
