package com.rayscope.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ConnState { STOPPED, CONNECTING, CONNECTED, STOPPING }

data class Status(
    val state: ConnState = ConnState.STOPPED,
    val serverId: String? = null,
    val serverName: String = "",
    val since: Long = 0,
    val error: String = "",
    val livePing: Long = -2L,
    val pings: List<Long> = emptyList(),
    val down: Long = 0,
    val up: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    /** (uplink, downlink) bytes per second, newest last */
    val history: List<Pair<Long, Long>> = emptyList(),
    val systemProxy: Boolean = false,
)

/** Runs the main xray process, toggles the system proxy and watches the session. */
object Engine {
    val status = MutableStateFlow(Status())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    @Volatile
    private var proc: Process? = null

    @Volatile
    private var monitors: Job? = null

    @Volatile
    var httpPort: Int = 10809
        private set

    @Volatile
    var socksPort: Int = 10808
        private set

    val isConnected: Boolean get() = status.value.state == ConnState.CONNECTED

    fun connect() {
        scope.launch { lock.withLock { if (proc == null) start() } }
    }

    fun disconnect() {
        scope.launch { lock.withLock { stop(keepProxy = false) } }
    }

    fun toggle() {
        when (status.value.state) {
            ConnState.STOPPED -> connect()
            ConnState.CONNECTED, ConnState.CONNECTING -> disconnect()
            ConnState.STOPPING -> Unit
        }
    }

    /** Restart the core with the selected server / new settings, keeping the system proxy on. */
    fun reconnect() {
        scope.launch {
            lock.withLock {
                if (proc != null) {
                    stop(keepProxy = true)
                    start()
                }
            }
        }
    }

    fun setMode(mode: Int) {
        Store.setPrefs { it.copy(mode = mode) }
        scope.launch {
            lock.withLock {
                if (proc != null && isConnected) {
                    if (mode == 1) {
                        val ok = SystemProxy.set(true, httpPort)
                        if (!ok && SystemProxy.supported) Notice.show("Не удалось включить системный прокси")
                    } else {
                        SystemProxy.set(false, httpPort)
                    }
                    status.update { it.copy(systemProxy = SystemProxy.active) }
                }
            }
        }
    }

    private fun validPort(p: Int, def: Int): Int = if (p in 1025..65535) p else def

    private fun start() {
        val s = Store.selected()
        if (s == null) {
            status.value = Status(error = "Сначала добавьте сервер")
            Notice.show("Сначала добавьте сервер")
            return
        }
        status.value = Status(state = ConnState.CONNECTING, serverId = s.id, serverName = s.cleanName(), systemProxy = SystemProxy.active)
        try {
            val p = Store.prefs.value
            val socks = validPort(p.socksPort, 10808)
            var http = validPort(p.httpPort, 10809)
            if (http == socks) http = socks + 1
            if (!Core.portFree(socks)) throw IOException("Порт $socks занят другим приложением (v2rayN, Clash?). Смените порт в настройках")
            if (!Core.portFree(http)) throw IOException("Порт $http занят другим приложением (v2rayN, Clash?). Смените порт в настройках")
            val api = Core.freePort()
            val cfg = XrayConfig.build(s, p, socks, http, api)
            val pr = Core.start(cfg, "main", log = true)
            if (!Core.waitPort(http, pr, 6000)) {
                val alive = pr.isAlive
                pr.destroy()
                throw IOException(if (alive) "Ядро не открыло порт $http" else "Ядро остановилось: " + Log.tail(2))
            }
            proc = pr
            httpPort = http
            socksPort = socks
            Log.add("Подключено: ${s.cleanName()} · HTTP $http · SOCKS $socks")
            var sys = false
            if (p.mode == 1) {
                if (SystemProxy.supported) {
                    sys = SystemProxy.set(true, http)
                    if (!sys) Notice.show("Не удалось включить системный прокси: используйте 127.0.0.1:$http вручную")
                } else {
                    Notice.show("Системный прокси есть только в Windows: используйте 127.0.0.1:$http")
                }
            } else if (SystemProxy.active) {
                SystemProxy.set(false, http)
            }
            status.value = Status(
                state = ConnState.CONNECTED,
                serverId = s.id,
                serverName = s.cleanName(),
                since = System.currentTimeMillis(),
                systemProxy = sys,
            )
            startMonitors(pr, api, http)
        } catch (e: Exception) {
            if (SystemProxy.active) SystemProxy.set(false, httpPort)
            status.value = Status(error = e.message ?: "ошибка подключения")
            Log.add("Ошибка подключения: ${e.message}")
        }
    }

    private fun stop(keepProxy: Boolean) {
        monitors?.cancel()
        monitors = null
        val pr = proc
        proc = null
        if (pr != null) {
            status.update { it.copy(state = ConnState.STOPPING) }
            pr.destroy()
            try {
                if (!pr.waitFor(2, TimeUnit.SECONDS)) pr.destroyForcibly()
            } catch (e: Exception) {
                pr.destroyForcibly()
            }
        }
        if (!keepProxy && SystemProxy.active) SystemProxy.set(false, httpPort)
        status.value = Status(systemProxy = SystemProxy.active)
        if (pr != null) Log.add("Отключено")
    }

    private fun startMonitors(pr: Process, api: Int, http: Int) {
        monitors = scope.launch {
            // watchdog: the core died on its own
            launch {
                while (isActive) {
                    delay(1000)
                    if (!pr.isAlive) {
                        scope.launch {
                            lock.withLock {
                                if (proc === pr) {
                                    monitors?.cancel()
                                    monitors = null
                                    proc = null
                                    if (SystemProxy.active) SystemProxy.set(false, http)
                                    Log.add("Ядро неожиданно остановилось")
                                    status.value = Status(error = "Ядро остановилось: смотрите журнал")
                                }
                            }
                        }
                        break
                    }
                }
            }
            // traffic counters
            launch {
                var lastUp = -1L
                var lastDown = -1L
                var lastT = System.nanoTime()
                while (isActive) {
                    delay(2000)
                    val r = Core.stats(api) ?: continue
                    val t = System.nanoTime()
                    val dt = ((t - lastT) / 1e9).coerceAtLeast(0.5)
                    if (lastUp >= 0) {
                        val up = ((r.first - lastUp).coerceAtLeast(0) / dt).toLong()
                        val down = ((r.second - lastDown).coerceAtLeast(0) / dt).toLong()
                        status.update {
                            it.copy(
                                up = up, down = down,
                                totalUp = r.first, totalDown = r.second,
                                history = (it.history + (up to down)).takeLast(60),
                            )
                        }
                    } else {
                        status.update { it.copy(totalUp = r.first, totalDown = r.second) }
                    }
                    lastUp = r.first
                    lastDown = r.second
                    lastT = t
                }
            }
            // live ping through the tunnel
            launch {
                delay(1500)
                while (isActive) {
                    val p = Store.prefs.value
                    val d = Net.delay(p.testUrl, http, p.pingTimeout)
                    status.update { it.copy(livePing = d, pings = (it.pings + d).takeLast(40)) }
                    delay(p.livePingInterval.coerceIn(3, 120) * 1000L)
                }
            }
        }
    }

    /** Called on exit: kill the core and give the system proxy back. */
    fun shutdownNow() {
        try {
            monitors?.cancel()
            proc?.destroy()
            proc = null
        } catch (e: Exception) {
            // ignore
        }
        if (SystemProxy.active) SystemProxy.set(false, httpPort)
    }
}
