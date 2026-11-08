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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.rayscope.fluid.Fluid
import com.rayscope.fluid.LogoTile
import com.rayscope.fluid.PulseDot
import com.rayscope.fluid.fluidSpan
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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
            state = rememberWindowState(size = DpSize(1260.dp, 840.dp)),
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
            val dark = when (prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val caption = palette(dark, prefs.accent).side
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1000, 700) }
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
            App(page, { page = it }, menuOpen, { menuOpen = it })
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
    Triple(Page.SETTINGS, Ico.Settings, "4"),
)

@Composable
fun App(page: Page, onPage: (Page) -> Unit, menuOpen: Boolean, onMenu: (Boolean) -> Unit) {
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
                Sidebar(page, prefs.dev, onPage) { onMenu(true) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.stroke))
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
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
            if (menuOpen) {
                CommandMenu(onPage = onPage, onClose = { onMenu(false) })
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { d ->
                Snackbar(snackbarData = d, containerColor = p.raised, contentColor = p.text, shape = RoundedCornerShape(14.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ sidebar

private const val NAV_ROW = 40f
private const val NAV_GAP = 4f

/** Left rail: brand, command menu, pages with a fluid highlight, and the connection at the bottom. */
@Composable
private fun Sidebar(page: Page, dev: Boolean, onPage: (Page) -> Unit, onMenu: () -> Unit) {
    val p = pal
    val st by Engine.status.collectAsState()
    Column(Modifier.width(248.dp).fillMaxHeight().background(p.side).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoTile(tile = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RayScope", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1)
                Text(
                    if (APP_VERSION.firstOrNull()?.isDigit() == true) "версия $APP_VERSION" else APP_VERSION,
                    color = p.hint, fontSize = 11.5.sp, maxLines = 1, style = TNum
                )
            }
        }
        CommandButton(onMenu)
        Spacer(Modifier.height(18.dp))
        NavList(page, dev, st, onPage)
        Spacer(Modifier.weight(1f))
        ConnectionBlock(st)
    }
}

@Composable
private fun CommandButton(onClick: () -> Unit) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val line by animateColorAsState(if (hovered) p.accent.copy(alpha = 0.45f) else p.stroke, tween(160), label = "cmdLine")
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(38.dp).squish(src, depth = 0.98f).clip(shape).background(p.raised).border(1.dp, line, shape)
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).hand()
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ico.Search, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text("Поиск и команды", color = p.hint, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Kbd("Ctrl K")
    }
}

/**
 * One highlight for the whole list, drawn behind fixed 40 dp rows: it stretches toward the new page and
 * catches up (fluidSpan). Every row has the same icon box and label weight, so nothing shifts on select.
 */
@Composable
private fun NavList(page: Page, dev: Boolean, st: Status, onPage: (Page) -> Unit) {
    val p = pal
    val items = if (dev) NAV + Triple(Page.LOG, Ico.Logs, "") else NAV
    val target = items.indexOfFirst { it.first == page }.takeIf { it >= 0 } ?: items.indexOfFirst { it.first == Page.SETTINGS }
    val top = target * (NAV_ROW + NAV_GAP)
    val span = fluidSpan(top, top + NAV_ROW)
    val fill = Brush.horizontalGradient(listOf(p.accent.copy(alpha = if (p.dark) 0.18f else 0.12f), p.accent2.copy(alpha = 0.04f)))
    val bead = Brush.verticalGradient(listOf(p.accent, p.accent2))
    Column(
        Modifier.fillMaxWidth().drawBehind {
            val y0 = span.start.value.dp.toPx()
            val y1 = span.end.value.dp.toPx()
            val r = 12.dp.toPx()
            drawRoundRect(fill, Offset(0f, y0), Size(size.width, y1 - y0), CornerRadius(r, r))
            val bw = 3.dp.toPx()
            val inset = 11.dp.toPx()
            val bh = (y1 - y0 - inset * 2f).coerceAtLeast(bw)
            drawRoundRect(bead, Offset(0f, y0 + inset), Size(bw, bh), CornerRadius(bw / 2f, bw / 2f))
        },
        verticalArrangement = Arrangement.spacedBy(NAV_GAP.dp)
    ) {
        items.forEachIndexed { i, (pg, ic, key) ->
            NavItem(
                ic, pg.title, i == target, if (key.isEmpty()) null else "Ctrl $key",
                dot = if (pg == Page.HOME && st.state == ConnState.CONNECTED) Good else null
            ) { onPage(pg) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, key: String?, dot: Color?, onClick: () -> Unit) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val fg by animateColorAsState(if (selected || hovered) p.text else p.hint, tween(160), label = "navFg")
    val ic by animateColorAsState(if (selected) p.accent else if (hovered) p.text else p.hint, tween(160), label = "navIcon")
    val hoverBg by animateColorAsState(if (hovered && !selected) p.hover else p.hover.copy(alpha = 0f), tween(140), label = "navHover")
    val keyAlpha by animateFloatAsState(if (hovered) 1f else 0f, tween(160), label = "navKey")
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(NAV_ROW.dp).squish(src, depth = 0.98f).clip(shape).background(hoverBg)
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).hand()
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ic, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        if (dot != null) {
            PulseDot(dot, dot = 6.dp)
            Spacer(Modifier.width(10.dp))
        }
        if (key != null) Kbd(key, Modifier.alpha(keyAlpha))
    }
}

@Composable
private fun ConnectionBlock(st: Status) {
    val p = pal
    val servers by Store.servers.collectAsState()
    val selId by Store.selectedId.collectAsState()
    val sel = remember(servers, selId) { Store.selected() }
    val srv = remember(servers, st.serverId, sel) { st.serverId?.let { id -> servers.firstOrNull { it.id == id } } ?: sel }
    val connected = st.state == ConnState.CONNECTED
    val busy = st.state == ConnState.CONNECTING || st.state == ConnState.STOPPING
    val dot = when {
        connected -> Good
        busy -> Mid
        else -> p.hint.copy(alpha = 0.6f)
    }
    val name = if (st.state == ConnState.STOPPED) sel?.cleanName().orEmpty() else st.serverName.ifEmpty { srv?.cleanName().orEmpty() }
    Column(Modifier.fillMaxWidth().card(p, 18.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(dot, dot = 7.dp, live = connected || busy)
            Spacer(Modifier.width(10.dp))
            Text(stateText(st.state), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (connected && st.livePing >= 0) {
                Text("${st.livePing} ms", color = delayColor(st.livePing), fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TNum)
            }
        }
        if (name.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagBox(srv?.countryCode().orEmpty(), 22.dp)
                Spacer(Modifier.width(9.dp))
                Text(name, color = p.hint, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Btn(
            if (st.state == ConnState.STOPPED) "Подключить" else "Отключить",
            { Engine.toggle() },
            Modifier.fillMaxWidth(),
            primary = st.state == ConnState.STOPPED,
            icon = Ico.Power,
            enabled = st.state != ConnState.STOPPING && (sel != null || st.state != ConnState.STOPPED)
        )
    }
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
        Command("Системный прокси", "режим", Ico.Globe) { Engine.setMode(1) },
        Command("Только локальный прокси", "режим", Ico.Link) { Engine.setMode(0) },
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
    val hl = Brush.horizontalGradient(listOf(p.accent.copy(alpha = if (p.dark) 0.2f else 0.13f), p.accent2.copy(alpha = 0.05f)))
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
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (p.dark) 0.5f else 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter
    ) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.padding(top = 92.dp).width(600.dp)
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
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Box(Modifier.fillMaxWidth().heightIn(max = 392.dp).padding(6.dp).onSizeChanged { viewport = it.height }) {
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
                Text("Результатов: ${shown.size}", color = p.hint.copy(alpha = 0.8f), fontSize = 12.sp, style = TNum)
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
