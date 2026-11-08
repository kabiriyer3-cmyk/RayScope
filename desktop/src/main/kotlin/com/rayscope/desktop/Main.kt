package com.rayscope.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.rayscope.fluid.ConnectTile
import com.rayscope.fluid.Fluid
import com.rayscope.fluid.NotchFrame
import com.rayscope.fluid.NotchSpec
import com.rayscope.fluid.PillTabs
import com.rayscope.fluid.fluidSpan
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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

    /** Lets a new copy (RayScope restarted as administrator) take over. */
    fun release() {
        try {
            server?.close()
        } catch (e: Exception) {
            // ignore
        }
        server = null
    }

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

/** Compact window: the shapes are the window, there is no system frame (see App). */
val WIN_SIZE = DpSize(392.dp, 700.dp)

/** Transparent room around the shapes, as wide as the gap: the light around the tile runs in it. */
private val WIN_MARGIN = 8.dp

/** Connect tile 112 dp in the top-left corner, 8 dp gap, 28 dp corners. */
private val NOTCH = NotchSpec(tile = 112.dp, gap = 8.dp, tileRadius = 28.dp, radius = 28.dp, cusp = 14.dp)

fun main(args: Array<String>) {
    if (!SingleInstance.acquire()) exitProcess(0)
    System.setProperty("http.keepAlive", "false")
    Store.load()
    Log.add("RayScope $APP_VERSION для ПК. Данные: ${Paths.data.absolutePath}")
    if (Paths.xray() == null) Log.add("Ядро xray не найдено в ${Paths.home.absolutePath}")
    Runtime.getRuntime().addShutdownHook(Thread { Engine.shutdownNow() })
    // --connect comes from the restart as administrator for TUN
    val connectNow = args.contains("--connect")
    Actions.go {
        SystemProxy.cleanupStale()
        val p = Store.prefs.value
        if ((p.connectOnLaunch || connectNow) && Store.selected() != null) Engine.connect()
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
        var menuOpen by remember { mutableStateOf(false) }
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
        val close: () -> Unit = { if (prefs.closeToTray && tray) visible = false else quit() }
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
        val winState = rememberWindowState(position = WindowPosition(Alignment.Center), size = WIN_SIZE)
        Window(
            onCloseRequest = close,
            visible = visible,
            title = "RayScope",
            icon = icon,
            state = winState,
            // no system frame and no background: only the tile and the panel are visible, the desktop shows
            // through everywhere else (the gap and the margin)
            undecorated = true,
            transparent = true,
            resizable = false,
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) {
                    false
                } else {
                    when (e.key) {
                        Key.K -> {
                            menuOpen = !menuOpen
                            true
                        }
                        Key.One -> { page = Page.HOME; menuOpen = false; true }
                        Key.Two -> { page = Page.SERVERS; menuOpen = false; true }
                        Key.Three -> { page = Page.CHECK; menuOpen = false; true }
                        Key.Four -> { page = Page.SETTINGS; menuOpen = false; true }
                        else -> false
                    }
                }
            },
        ) {
            LaunchedEffect(Unit) { Log.add("Отрисовка окна: " + window.renderApi) }
            LaunchedEffect(raise) {
                if (raise > 0) {
                    winState.isMinimized = false
                    if ((window.extendedState and Frame.ICONIFIED) != 0) window.extendedState = Frame.NORMAL
                    window.toFront()
                    window.requestFocus()
                }
            }
            App(this, page, { page = it }, menuOpen, { menuOpen = it }, onMinimize = { winState.isMinimized = true }, onClose = close)
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

private val TABS: List<Pair<Page, ImageVector>> = listOf(
    Page.HOME to Ico.Home,
    Page.SERVERS to Ico.Servers,
    Page.CHECK to Ico.Radar,
    Page.SETTINGS to Ico.Settings,
)

/**
 * The whole window: connect tile in the top-left corner, the panel wrapping around it, light in the gap while the
 * tunnel is up. The strip right of the tile is the header (drag the window by it); pages live under the tile and
 * the pill tab bar sits at the bottom of the panel.
 */
@Composable
fun App(
    win: FrameWindowScope,
    page: Page,
    onPage: (Page) -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val prefs by Store.prefs.collectAsState()
    RayTheme(prefs.theme, prefs.accent) {
        val p = pal
        val st by Engine.status.collectAsState()
        val servers by Store.servers.collectAsState()
        val selId by Store.selectedId.collectAsState()
        val sel = remember(servers, selId) { Store.selected() }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            Notice.flow.collect { snack.showSnackbar(it) }
        }
        LaunchedEffect(prefs.dev) {
            if (!prefs.dev && page == Page.LOG) onPage(Page.SETTINGS)
        }
        val connected = st.state == ConnState.CONNECTED
        val busy = st.state == ConnState.CONNECTING || st.state == ConnState.STOPPING
        // once per session: look up the exit IP for the header (runs in the app scope, not in a screen)
        LaunchedEffect(connected, st.since) {
            if (connected && IpCheck.state.value.checkedAt < st.since) {
                delay(1500)
                if (Engine.isConnected && !IpCheck.state.value.running) Actions.go { IpCheck.run() }
            }
        }
        val tab = TABS.indexOfFirst { it.first == page }.let { if (it < 0) TABS.lastIndex else it }
        val askAdmin by Modes.askAdmin.collectAsState()
        if (askAdmin) {
            AdminDialog(onRestart = { Actions.go { Modes.restartElevated() } }, onDismiss = { Modes.askAdmin.value = false })
        }
        Box(Modifier.fillMaxSize().padding(WIN_MARGIN)) {
            NotchFrame(
                spec = NOTCH,
                panel = p.side,
                edge = p.stroke,
                modifier = Modifier.fillMaxSize(),
                fill = true,
                ray = when {
                    connected -> 2
                    st.state == ConnState.CONNECTING -> 1
                    else -> 0
                },
                rayColor = p.accent,
                tile = {
                    ConnectTile(
                        on = connected,
                        busy = busy,
                        onClick = { Engine.toggle() },
                        idleFill = p.tileIdle,
                        idleInk = p.hint,
                        onFill = p.accent,
                        onInk = p.onAccent,
                        label = when (st.state) {
                            ConnState.CONNECTED -> "ВКЛ"
                            ConnState.STOPPED -> "ВЫКЛ"
                            else -> "• • •"
                        },
                        modifier = Modifier.fillMaxSize(),
                        radius = NOTCH.tileRadius,
                        enabled = st.state != ConnState.STOPPING && (sel != null || st.state != ConnState.STOPPED),
                    )
                },
                shoulder = {
                    with(win) {
                        WindowDraggableArea(Modifier.fillMaxSize()) {
                            Shoulder(st, sel, onMinimize, onClose)
                        }
                    }
                },
                body = {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                            modifier = Modifier.fillMaxSize(),
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
                        if (menuOpen) {
                            CommandMenu(onPage = onPage, onClose = { onMenu(false) })
                        }
                        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) { d ->
                            Snackbar(snackbarData = d, containerColor = p.raised, contentColor = p.text, shape = RoundedCornerShape(16.dp))
                        }
                    }
                    PillTabs(
                        items = TABS.map { it.second to it.first.title },
                        selected = tab,
                        onSelect = { i ->
                            onPage(TABS[i].first)
                            onMenu(false)
                        },
                        track = p.surface,
                        pill = p.accent,
                        onPill = p.onAccent,
                        ink = p.hint,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                        dotIndex = 0,
                        dot = if (connected && tab != 0) Good else Color.Unspecified,
                    )
                }
            )
        }
    }
}

/** Header strip right of the tile: brand and window buttons, then the state in large type and one line of facts. */
@Composable
private fun Shoulder(st: Status, sel: Server?, onMinimize: () -> Unit, onClose: () -> Unit) {
    val p = pal
    val cs by IpCheck.state.collectAsState()
    val connected = st.state == ConnState.CONNECTED
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.state, st.since) {
        while (st.state == ConnState.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val title = when (st.state) {
        ConnState.CONNECTED -> "Защищено"
        ConnState.CONNECTING -> "Подключение…"
        ConnState.STOPPING -> "Отключение…"
        ConnState.STOPPED -> "Не защищено"
    }
    val err = st.state == ConnState.STOPPED && st.error.isNotEmpty()
    val vpnIp = cs.vpn?.takeIf { connected && cs.wasConnected && cs.checkedAt >= st.since }
    val realIp = cs.real?.takeIf { !connected && !cs.wasConnected }
    val ipPart = when {
        vpnIp != null -> vpnIp.ip + if (vpnIp.countryCode.isNotEmpty()) " " + vpnIp.countryCode else ""
        cs.running -> "проверка IP…"
        else -> ""
    }
    val sub = when {
        err -> st.error
        connected -> listOf(if (st.tun) "TUN" else "", ipPart, Fmt.duration(now - st.since)).filter { it.isNotEmpty() }.joinToString("  ·  ")
        st.state == ConnState.CONNECTING -> st.serverName.ifEmpty { sel?.cleanName().orEmpty() }
        realIp != null -> "Ваш IP " + realIp.ip
        sel == null -> "Добавьте сервер во вкладке «Серверы»"
        else -> "Нажмите на плитку, чтобы подключиться"
    }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RayMark(Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "RayScope", color = p.text.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            IconBtn(Ico.Minus, "Свернуть", onMinimize, size = 28.dp)
            IconBtn(Ico.Close, "Закрыть", onClose, size = 28.dp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(sub, color = if (err) Bad else p.hint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum)
    }
}

/** TUN needs administrator rights: explain and offer the restart. */
@Composable
private fun AdminDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    val p = pal
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Btn("Перезапустить", onRestart, primary = true, icon = Ico.Shield) },
        dismissButton = { Btn("Отмена", onDismiss, color = p.hint) },
        title = { Text("Режим TUN", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "TUN пускает через VPN весь трафик компьютера, включая игры и Telegram. Для этого Windows нужно " +
                    "разрешение администратора: RayScope перезапустится и спросит его.",
                color = p.hint, fontSize = 13.5.sp, lineHeight = 19.sp
            )
        },
        containerColor = p.surface,
        shape = RoundedCornerShape(16.dp),
    )
}

// ------------------------------------------------------------------ command menu

private class Command(val title: String, val hint: String, val icon: ImageVector, val run: () -> Unit)

private const val CMD_ROW = 42f

/** Ctrl+K: type to filter, arrows to move, Enter to run, Esc to close. Server names are commands too. */
@Composable
private fun CommandMenu(onPage: (Page) -> Unit, onClose: () -> Unit) {
    val p = pal
    val st by Engine.status.collectAsState()
    val servers by Store.servers.collectAsState()
    var query by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { CMD_ROW.dp.toPx() }
    var viewport by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        appear.animateTo(1f, Fluid.jelly())
    }

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
        Command("Обновить подписки", "все", Ico.Refresh) { Actions.go { Actions.updateAllSubs(silent = false) } },
        Command("Проверить IP", "адрес и страна", Ico.ShieldCheck) {
            onPage(Page.CHECK)
            Actions.go { IpCheck.run() }
        },
        Command("Системный прокси", "режим", Ico.Globe) { Modes.request(Mode.SYSTEM) },
        Command("TUN: весь трафик", "режим", Ico.Shield) { Modes.request(Mode.TUN) },
        Command("Только локальный прокси", "режим", Ico.Link) { Modes.request(Mode.LOCAL) },
        Command("Главная", "Ctrl 1", Ico.Home) { onPage(Page.HOME) },
        Command("Серверы", "Ctrl 2", Ico.Servers) { onPage(Page.SERVERS) },
        Command("Проверка", "Ctrl 3", Ico.Radar) { onPage(Page.CHECK) },
        Command("Настройки", "Ctrl 4", Ico.Settings) { onPage(Page.SETTINGS) },
        Command("Тёмная тема", "оформление", Ico.Moon) { Store.setPrefs { it.copy(theme = 2) } },
        Command("Светлая тема", "оформление", Ico.Sun) { Store.setPrefs { it.copy(theme = 1) } },
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
    LaunchedEffect(shown.size, q) {
        index = 0
        scroll.scrollTo(0)
    }
    val span = fluidSpan(index * CMD_ROW, index * CMD_ROW + CMD_ROW)
    val hl = p.accent.copy(alpha = if (p.dark) 0.12f else 0.09f)
    fun ensureVisible(i: Int) {
        val top = (i * rowPx).toInt()
        val bottom = top + rowPx.toInt()
        scope.launch {
            if (top < scroll.value) {
                scroll.animateScrollTo(top)
            } else if (viewport > 0 && bottom > scroll.value + viewport) {
                scroll.animateScrollTo(bottom - viewport)
            }
        }
    }
    val runAt: (Int) -> Unit = { i ->
        shown.getOrNull(i)?.let {
            onClose()
            it.run()
        }
    }

    Box(
        Modifier.fillMaxSize().background(p.side.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp).fillMaxWidth()
                .graphicsLayer {
                    val k = appear.value
                    alpha = k.coerceIn(0f, 1f)
                    scaleX = 0.94f + 0.06f * k
                    scaleY = 0.94f + 0.06f * k
                    translationY = (1f - k) * -12.dp.toPx()
                }
                .shadow(32.dp, shape).clip(shape).background(p.surface).border(1.dp, p.stroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.Search, contentDescription = null, tint = p.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    val ts = TextStyle(color = p.text, fontSize = 15.sp)
                    if (query.isEmpty()) Text("Команда или сервер", style = ts.copy(color = p.hint.copy(alpha = 0.7f)))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = ts,
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> {
                                    if (shown.isNotEmpty()) {
                                        index = (index + 1) % shown.size
                                        ensureVisible(index)
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (shown.isNotEmpty()) {
                                        index = (index - 1 + shown.size) % shown.size
                                        ensureVisible(index)
                                    }
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
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ico.Search, contentDescription = null, tint = p.hint.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Ничего не нашлось", color = p.text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    Text("Попробуйте другое слово или имя сервера", color = p.hint, fontSize = 12.sp)
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(max = 296.dp).padding(6.dp).onSizeChanged { viewport = it.height }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                        Column(
                            Modifier.fillMaxWidth().drawBehind {
                                val y0 = span.start.value.dp.toPx()
                                val y1 = span.end.value.dp.toPx()
                                val r = 12.dp.toPx()
                                drawRoundRect(hl, Offset(0f, y0), Size(size.width, y1 - y0), CornerRadius(r, r))
                            }
                        ) {
                            shown.forEachIndexed { i, c ->
                                CommandRow(c, i == index, onHover = { index = i }) { runAt(i) }
                            }
                        }
                    }
                }
            }
            Line()
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Kbd("↑ ↓")
                Text("  выбрать", color = p.hint, fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
                Kbd("Enter")
                Text("  запустить", color = p.hint, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("${shown.size}", color = p.hint.copy(alpha = 0.8f), fontSize = 12.sp, style = TNum)
            }
        }
    }
}

@Composable
private fun CommandRow(c: Command, selected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val p = pal
    val hover = rememberUpdatedState(onHover)
    val fg by animateColorAsState(if (selected) p.text else p.text.copy(alpha = 0.82f), tween(120), label = "cmdFg")
    val ic by animateColorAsState(if (selected) p.accent else p.hint, tween(120), label = "cmdIcon")
    val enter by animateFloatAsState(if (selected) 1f else 0f, tween(120), label = "cmdEnter")
    Row(
        Modifier.fillMaxWidth().height(CMD_ROW.dp)
            .pointerInput(Unit) {
                // a real mouse move picks the row; rows sliding under a still pointer during keyboard scroll do not
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move) hover.value()
                    }
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).hand()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(c.icon, contentDescription = null, tint = ic, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(c.title, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(c.hint, color = p.hint, fontSize = 12.sp, maxLines = 1, style = TNum)
        Spacer(Modifier.width(10.dp))
        Kbd("Enter", Modifier.alpha(enter))
    }
}
