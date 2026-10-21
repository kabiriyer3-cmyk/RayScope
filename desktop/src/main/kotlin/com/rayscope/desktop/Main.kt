package com.rayscope.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    ConnState.CONNECTED -> "Защищено"
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

fun main() {
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

    application {
        val prefs by Store.prefs.collectAsState()
        val st by Engine.status.collectAsState()
        var visible by remember { mutableStateOf(true) }
        var raise by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            UiSignals.show.collect {
                visible = true
                raise += 1
            }
        }
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
        ) {
            val dark = when (prefs.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val caption = palette(dark, prefs.accent).bg
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

private val TABS: List<Pair<Page, ImageVector>> = listOf(
    Page.HOME to Ico.Home,
    Page.SERVERS to Ico.Servers,
    Page.CHECK to Ico.Radar,
    Page.SETTINGS to Ico.Sliders,
)

@Composable
fun App() {
    val prefs by Store.prefs.collectAsState()
    RayTheme(prefs.theme, prefs.accent, prefs.glass) {
        val p = pal
        var page by remember { mutableStateOf(Page.HOME) }
        val snack = remember { SnackbarHostState() }
        val backdrop = remember { Backdrop() }
        LaunchedEffect(Unit) {
            Notice.flow.collect { snack.showSnackbar(it) }
        }
        LaunchedEffect(prefs.dev) {
            if (!prefs.dev && page == Page.LOG) page = Page.SETTINGS
        }
        Box(Modifier.fillMaxSize().background(p.bg)) {
            // everything the glass refracts lives inside the backdrop
            BackdropSource(backdrop, Modifier.fillMaxSize(), background = p.bg) {
                Ambient()
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.985f)) togetherWith fadeOut(tween(140))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "page"
                ) { pg ->
                    when (pg) {
                        Page.HOME -> HomeScreen(onGoServers = { page = Page.SERVERS }, onGoCheck = { page = Page.CHECK })
                        Page.SERVERS -> ServersScreen()
                        Page.CHECK -> CheckScreen()
                        Page.SETTINGS -> SettingsScreen(onOpenLog = { page = Page.LOG })
                        Page.LOG -> LogScreen(onBack = { page = Page.SETTINGS })
                    }
                }
            }
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                GlassTabBar(page, prefs.tabLabels, { page = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)) { d ->
                Snackbar(snackbarData = d, containerColor = p.raised, contentColor = p.text, shape = RoundedCornerShape(14.dp))
            }
        }
    }
}

/** Soft accent light in the corners: gives the glass something to bend. */
@Composable
private fun Ambient() {
    val p = pal
    val a = p.accent
    val dark = p.dark
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(a.copy(alpha = if (dark) 0.10f else 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.maxDimension * 0.55f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Purple.copy(alpha = if (dark) 0.07f else 0.05f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height),
                    radius = size.maxDimension * 0.5f
                )
            )
        }
    )
}

/**
 * Floating Liquid Glass tab bar, a port of the phone one: the highlight is a liquid drop whose
 * leading edge runs on a stiff spring and trailing edge on a soft one, so it stretches and catches up.
 */
@Composable
private fun GlassTabBar(page: Page, labels: Boolean, onPage: (Page) -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    val st by Engine.status.collectAsState()
    val sel = if (page == Page.LOG) 3 else TABS.indexOfFirst { it.first == page }.coerceAtLeast(0)
    val small = if (labels) 58.dp else 66.dp
    val big = if (labels) 138.dp else 66.dp
    val itemH = 46.dp
    val prev = remember { intArrayOf(sel) }
    val goingRight = sel >= prev[0]
    SideEffect { prev[0] = sel }

    fun widthOf(i: Int): Dp = if (i == sel) big else small
    var start = 0.dp
    for (i in 0 until sel) start += widthOf(i)
    val end = start + widthOf(sel)
    val stiff = spring<Dp>(dampingRatio = 0.72f, stiffness = 650f)
    val soft = spring<Dp>(dampingRatio = 0.78f, stiffness = 260f)
    val left by animateDpAsState(start, if (goingRight) soft else stiff, label = "tabLeft")
    val right by animateDpAsState(end, if (goingRight) stiff else soft, label = "tabRight")

    GlassBox(modifier.height(itemH + 10.dp), radius = 30.dp) {
        Box(Modifier.padding(5.dp)) {
            Box(
                Modifier.offset(x = left).width((right - left).coerceAtLeast(0.dp)).height(itemH)
                    .clip(RoundedCornerShape(25.dp)).background(p.accent.copy(alpha = 0.17f))
            )
            Row {
                TABS.forEachIndexed { i, (pg, ic) ->
                    TabButton(
                        icon = ic,
                        label = pg.title,
                        selected = i == sel,
                        labels = labels,
                        width = widthOf(i),
                        height = itemH,
                        dot = if (pg == Page.HOME && st.state == ConnState.CONNECTED) Good else null,
                    ) { onPage(pg) }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    labels: Boolean,
    width: Dp,
    height: Dp,
    dot: Color?,
    onClick: () -> Unit,
) {
    val p = pal
    val w by animateDpAsState(width, spring(dampingRatio = 0.8f, stiffness = 500f), label = "tabWidth")
    val tint by animateColorAsState(if (selected) p.accent else p.hint, tween(200), label = "tabTint")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bump.snapTo(0.78f)
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
        }
    }
    Row(
        Modifier.width(w).height(height).clip(RoundedCornerShape(23.dp)).clickable(onClick = onClick).hand(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                icon, contentDescription = label, tint = tint,
                modifier = Modifier.size(21.dp).graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                }
            )
            if (dot != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(CircleShape).background(dot))
            }
        }
        AnimatedVisibility(
            visible = selected && labels,
            enter = fadeIn(tween(180)) + expandHorizontally(),
            exit = fadeOut(tween(120)) + shrinkHorizontally()
        ) {
            Text(
                label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
