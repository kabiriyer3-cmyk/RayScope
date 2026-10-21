package com.rayscope.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rayscope.app.R
import com.rayscope.app.data.Repo
import com.rayscope.app.data.Settings
import com.rayscope.app.ui.MainActivity
import com.rayscope.app.util.AppLog
import com.rayscope.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * VPN service: creates the TUN interface and hands its fd to Xray-core (native tun inbound).
 * The app itself is excluded from the VPN, so the core's own sockets bypass the tunnel
 * and the app can measure the "real" IP directly and the VPN IP through the local proxy.
 */
class RayVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var statsJob: Job? = null
    private var liveFails = 0
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastNet: Network? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        goForeground(Repo.selected()?.name ?: "RayScope", "Подключение…")
        scope.launch { startVpn() }
        return START_STICKY
    }

    private suspend fun startVpn() {
        lock.withLock {
            if (tun != null && CoreManager.isRunning) return@withLock
            val server = Repo.selected()
            if (server == null) {
                fail("Сервер не выбран")
                return@withLock
            }
            VpnState.update { it.copy(state = ConnState.CONNECTING, serverId = server.id, serverName = server.name, error = "") }
            AppLog.add("Подключение к ${server.name} (${server.protocol}/${server.network}/${server.security})")
            try {
                val st = Repo.settings
                val b = Builder()
                b.setSession(server.cleanName())
                b.setMtu(st.mtu.coerceIn(1280, 9000))
                b.addAddress("10.10.14.1", 30)
                b.addRoute("0.0.0.0", 0)
                var dnsAdded = 0
                st.dnsList().filter { isIpv4(it) }.forEach { d ->
                    try {
                        b.addDnsServer(d)
                        dnsAdded++
                    } catch (e: Exception) {
                        AppLog.add("DNS $d пропущен: ${e.message}")
                    }
                }
                if (dnsAdded == 0) {
                    // only DoH / hostnames configured: give the system a plain resolver, the core still routes it
                    try {
                        b.addDnsServer("1.1.1.1")
                    } catch (e: Exception) {
                        AppLog.add("DNS fallback: ${e.message}")
                    }
                }
                if (st.ipv6) {
                    b.addAddress("fd66:6c6f:7261::1", 126)
                    b.addRoute("::", 0)
                }
                applyPerApp(b, st)
                if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
                b.setConfigureIntent(openAppIntent())
                val fd = b.establish() ?: throw IllegalStateException("Нет разрешения VPN")
                tun = fd
                val cfg = XrayConfig.build(server, st, tun = true)
                CoreManager.controller.startLoop(cfg, fd.fd)
                if (!CoreManager.isRunning) throw IllegalStateException("Ядро Xray не запустилось")
                VpnState.update {
                    it.copy(
                        state = ConnState.CONNECTED, since = System.currentTimeMillis(), totalUp = 0, totalDown = 0,
                        peakUp = 0, peakDown = 0, history = emptyList(), livePing = -2, pingHistory = emptyList()
                    )
                }
                AppLog.add("Подключено: ${server.name}")
                notifyText(server.cleanName(), "Подключено")
                startStats()
                registerNetWatch()
            } catch (e: Throwable) {
                AppLog.add("Ошибка подключения: ${e.message}")
                fail(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && p.toInt() <= 255 }
    }

    private fun applyPerApp(b: Builder, st: Settings) {
        val self = packageName
        when (st.perAppMode) {
            2 -> {
                var added = 0
                (st.perAppSet - self).forEach { pkg ->
                    try {
                        b.addAllowedApplication(pkg)
                        added++
                    } catch (e: Exception) {
                        AppLog.add("Приложение не найдено: $pkg")
                    }
                }
                if (added == 0) b.addDisallowedApplication(self)
            }
            1 -> {
                (st.perAppSet + self).forEach { pkg ->
                    try {
                        b.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        AppLog.add("Приложение не найдено: $pkg")
                    }
                }
            }
            else -> b.addDisallowedApplication(self)
        }
    }

    // ------------------------------------------------------------------ network change watch

    private fun registerNetWatch() {
        unregisterNetWatch()
        if (!Repo.settings.reconnectOnNetworkChange) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        lastNet = null
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val prev = lastNet
                lastNet = network
                if (prev != null && prev != network && tun != null) {
                    AppLog.add("Сеть сменилась: перезапускаю ядро")
                    switchServer(countAsSwitch = false)
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Throwable) {
            AppLog.add("Не удалось следить за сетью: ${e.message}")
        }
    }

    private fun unregisterNetWatch() {
        val cb = netCallback ?: return
        netCallback = null
        try {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        } catch (e: Throwable) {
            // already unregistered
        }
    }

    // ------------------------------------------------------------------ stats + live ping

    private fun startStats() {
        statsJob?.cancel()
        liveFails = 0
        statsJob = scope.launch {
            var tick = 0
            while (isActive) {
                val every = Repo.settings.livePingInterval.coerceIn(5, 600)
                if (tick % every == 0) launch { measureLive() }
                tick++
                delay(1000)
                if (!CoreManager.isRunning) continue
                var up = 0L
                var down = 0L
                val raw = try {
                    CoreManager.controller.queryAllOutboundTrafficStats()
                } catch (e: Throwable) {
                    ""
                }
                for (entry in raw.split(';')) {
                    val p = entry.split(',')
                    if (p.size != 3 || p[0] != "proxy") continue
                    val v = p[2].toLongOrNull() ?: 0L
                    if (p[1] == "uplink") {
                        up += v
                    } else if (p[1] == "downlink") {
                        down += v
                    }
                }
                VpnState.update { s ->
                    s.copy(
                        upSpeed = up, downSpeed = down,
                        totalUp = s.totalUp + up, totalDown = s.totalDown + down,
                        peakUp = maxOf(s.peakUp, up), peakDown = maxOf(s.peakDown, down),
                        history = (s.history + (up to down)).takeLast(60)
                    )
                }
                val s = VpnState.status.value
                notifyText(
                    s.serverName.ifEmpty { "RayScope" },
                    "↓ ${Fmt.speed(down)}   ↑ ${Fmt.speed(up)}   •  всего ${Fmt.bytes(s.totalDown + s.totalUp)}"
                )
            }
        }
    }

    private suspend fun measureLive() {
        if (!CoreManager.isRunning) return
        val d = try {
            CoreManager.controller.measureDelay(Repo.settings.testUrl)
        } catch (e: Throwable) {
            -1L
        }
        VpnState.update { it.copy(livePing = d, pingHistory = (it.pingHistory + d).takeLast(24)) }
        if (d < 0) {
            liveFails++
            AppLog.add("Контрольный пинг не прошёл ($liveFails)")
            val st = Repo.settings
            if (st.autoFailover && liveFails >= st.failoverThreshold.coerceIn(1, 10)) {
                liveFails = 0
                val current = VpnState.status.value.serverId
                val alt = Repo.servers.value.filter { it.delay >= 0 && it.id != current }.minByOrNull { it.delay }
                if (alt != null) {
                    AppLog.add("Авто-переключение на ${alt.name}")
                    Repo.select(alt.id)
                    switchServer()
                }
            }
        } else {
            liveFails = 0
        }
    }

    /** Restart the core with the currently selected server, keeping the TUN interface. */
    fun switchServer(countAsSwitch: Boolean = true) {
        scope.launch {
            lock.withLock {
                val fd = tun ?: return@withLock
                val server = Repo.selected() ?: return@withLock
                VpnState.update { it.copy(state = ConnState.CONNECTING, serverId = server.id, serverName = server.name) }
                try {
                    CoreManager.controller.stopLoop()
                } catch (e: Throwable) {
                    AppLog.add("stopLoop: ${e.message}")
                }
                try {
                    CoreManager.controller.startLoop(XrayConfig.build(server, Repo.settings, tun = true), fd.fd)
                    VpnState.update {
                        it.copy(
                            state = ConnState.CONNECTED,
                            since = if (countAsSwitch) System.currentTimeMillis() else it.since,
                            livePing = -2,
                            error = "",
                            switches = it.switches + (if (countAsSwitch) 1 else 0)
                        )
                    }
                    AppLog.add(if (countAsSwitch) "Переключено на ${server.name}" else "Ядро перезапущено")
                    liveFails = 0
                    launch { measureLive() }
                } catch (e: Throwable) {
                    AppLog.add("Ошибка переключения: ${e.message}")
                    VpnState.update { it.copy(error = e.message ?: "error") }
                }
            }
        }
    }

    fun stopVpn() {
        VpnState.update { it.copy(state = ConnState.STOPPING) }
        unregisterNetWatch()
        scope.launch {
            lock.withLock {
                statsJob?.cancel()
                statsJob = null
                try {
                    CoreManager.controller.stopLoop()
                } catch (e: Throwable) {
                    AppLog.add("stopLoop: ${e.message}")
                }
                try {
                    tun?.close()
                } catch (e: Throwable) {
                    AppLog.add("tun close: ${e.message}")
                }
                tun = null
                VpnState.update { VpnStatus() }
                AppLog.add("Отключено")
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun fail(msg: String) {
        statsJob?.cancel()
        unregisterNetWatch()
        try {
            CoreManager.controller.stopLoop()
        } catch (e: Throwable) {
            AppLog.add("stopLoop: ${e.message}")
        }
        try {
            tun?.close()
        } catch (e: Throwable) {
            AppLog.add("tun close: ${e.message}")
        }
        tun = null
        VpnState.update { VpnStatus(error = msg) }
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onRevoke() {
        AppLog.add("VPN отозан системой или другим VPN")
        stopVpn()
    }

    override fun onDestroy() {
        instance = null
        statsJob?.cancel()
        unregisterNetWatch()
        if (tun != null) {
            try {
                CoreManager.controller.stopLoop()
            } catch (e: Throwable) {
                AppLog.add("stopLoop: ${e.message}")
            }
            try {
                tun?.close()
            } catch (e: Throwable) {
                AppLog.add("tun close: ${e.message}")
            }
            tun = null
        }
        if (VpnState.status.value.state != ConnState.STOPPED) {
            VpnState.update { VpnStatus(error = it.error) }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(title: String, text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            .addAction(0, "Отключить", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun goForeground(title: String, text: String) {
        val n = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifyText(title: String, text: String) {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(title, text))
        } catch (e: Throwable) {
            // notifications may be disabled
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        const val ACTION_STOP = "com.rayscope.app.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 7

        @Volatile
        var instance: RayVpnService? = null
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RayVpnService::class.java))
        }

        fun stop() {
            val i = instance
            if (i != null) i.stopVpn() else VpnState.update { VpnStatus() }
        }

        fun switchServer() {
            instance?.switchServer()
        }
    }
}
