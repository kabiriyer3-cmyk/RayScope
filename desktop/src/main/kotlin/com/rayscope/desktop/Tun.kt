package com.rayscope.desktop

import com.sun.jna.NativeLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Connection modes, stored in [Prefs.mode]. */
object Mode {
    const val LOCAL = 0
    const val SYSTEM = 1
    const val TUN = 2
}

/**
 * TUN mode: sing-box puts a virtual network adapter in front of the system, catches the traffic of every program
 * (games and Telegram included) and hands it to the local SOCKS port of xray. xray itself, RayScope and the
 * server address stay outside of the adapter, so nothing loops back.
 *
 * Windows: RayScope itself runs as administrator. Linux: a small helper gets root once through the pkexec password
 * dialog and stays until the reboot ([LinuxHelper]), so switching TUN, servers or reconnecting never asks again.
 */
object Tun {
    /** Windows: sing-box is our own child process. */
    @Volatile
    private var proc: Process? = null

    /** Linux: the session of the helper that is ours now. */
    @Volatile
    private var token: String? = null

    private const val IFACE = "RayScope"

    val running: Boolean
        get() = if (Paths.isWindows) proc?.isAlive == true else token?.let { LinuxHelper.running(it) } == true

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val important = Regex("\\b(WARN|ERROR|FATAL|PANIC)\\b")

    /** Windows always (UAC), Linux when pkexec (polkit) is installed or the helper already runs. */
    val supported: Boolean get() = Paths.isWindows || Paths.which("pkexec") != null || LinuxHelper.alive()

    fun exe(): File? {
        val core = Paths.xray()?.parentFile ?: File(Paths.home, "core")
        return File(core, if (Paths.isWindows) "sing-box.exe" else "sing-box").takeIf { it.isFile }
    }

    /** Starts the adapter for [s] in front of the SOCKS port of the running core. Throws with a readable message. */
    fun start(s: Server, socksPort: Int, p: Prefs) {
        if (!supported) throw IOException("Для TUN в Linux нужен pkexec: установите пакет pkexec или policykit-1")
        if (!Elevation.isAdmin) {
            throw IOException("Для TUN нужны права администратора: выберите TUN ещё раз, RayScope перезапустится")
        }
        val name = if (Paths.isWindows) "sing-box.exe" else "sing-box"
        val exe = exe() ?: throw IOException("Не найден $name в папке core: переустановите RayScope")
        stop()
        val json = config(s, resolve(s.address), socksPort, p)
        if (Paths.isWindows) startWindows(exe, json) else startLinux(exe, json)
        Log.add("TUN включён: весь трафик компьютера идёт через RayScope")
    }

    private fun startWindows(exe: File, json: String) {
        val dir = File(Paths.data, "run").apply { mkdirs() }
        val cfg = File(dir, "tun.json")
        cfg.writeText(json)
        val pr = ProcessBuilder(exe.absolutePath, "run", "-c", cfg.absolutePath, "-D", dir.absolutePath, "--disable-color")
            .directory(exe.parentFile)
            .redirectErrorStream(true)
            .start()
        val wait = 12L
        val up = CountDownLatch(1)
        val started = AtomicBoolean(false)
        thread(isDaemon = true, name = "sing-box") {
            try {
                pr.inputStream.bufferedReader().forEachLine { line ->
                    val isStart = line.contains("sing-box started")
                    // every connection is logged at info level: keep only the start line and problems
                    if (isStart || important.containsMatchIn(line) || line.startsWith("Error")) Log.add("tun: " + line.trim().take(400))
                    if (isStart) {
                        started.set(true)
                        up.countDown()
                    }
                }
            } catch (e: Exception) {
                // process ended
            }
            up.countDown()
        }
        up.await(wait, TimeUnit.SECONDS)
        if (!started.get()) {
            if (!pr.waitFor(2, TimeUnit.SECONDS)) {
                kill(pr)
                throw IOException("TUN не запустился за $wait секунд: смотрите журнал")
            }
            throw IOException("TUN не запустился: " + Log.tail(2))
        }
        proc = pr
    }

    private fun startLinux(exe: File, json: String) {
        if (!exe.canExecute()) exe.setExecutable(true)
        // the password dialog is shown here, but only if the helper isn't running since the last boot
        LinuxHelper.ensure(exe)
        val tok = LinuxHelper.newToken()
        token = tok
        LinuxHelper.send("start $tok ${ProcessHandle.current().pid()} " + json.replace('\n', ' ').replace('\r', ' '))
        val tail = LogTail(LinuxHelper.log, tok)
        val end = System.currentTimeMillis() + 20_000L
        var ok = false
        while (System.currentTimeMillis() < end) {
            tail.poll()
            val st = LinuxHelper.state()
            if (st.size >= 2 && st[0] == "exit" && st[1] == tok) {
                Thread.sleep(200)
                tail.poll()
                token = null
                throw IOException("TUN не запустился: " + Log.tail(2))
            }
            if (LinuxHelper.running(tok) && ifaceUp()) {
                ok = true
                break
            }
            Thread.sleep(100)
        }
        if (!ok) {
            stop()
            throw IOException("TUN не запустился за 20 секунд: смотрите журнал")
        }
        // the adapter is there, give sing-box a moment for the routes
        Thread.sleep(400)
        tail.poll()
        thread(isDaemon = true, name = "sing-box-log") {
            while (token == tok) {
                try {
                    Thread.sleep(500)
                    tail.poll()
                } catch (e: Exception) {
                    // keep following
                }
            }
        }
    }

    private fun ifaceUp(): Boolean = try {
        NetworkInterface.getByName(IFACE)?.isUp == true
    } catch (e: Exception) {
        false
    }

    /** Follows tun.log of the helper for one session: the first line is "# TOKEN". */
    private class LogTail(private val f: File, private val tok: String) {
        private var pos = 0L
        private var ours = false

        fun poll() {
            try {
                RandomAccessFile(f, "r").use { r ->
                    val len = r.length()
                    // until the helper has written our header the file may still hold the previous session
                    if (!ours) pos = 0L
                    if (len < pos) {
                        pos = 0L
                        ours = false
                        return
                    }
                    if (len == pos) return
                    r.seek(pos)
                    val size = minOf(len - pos, 262_144L).toInt()
                    val bytes = ByteArray(size)
                    r.readFully(bytes)
                    val text = String(bytes, Charsets.UTF_8)
                    val cut = text.lastIndexOf('\n')
                    if (cut < 0) return
                    val lines = text.substring(0, cut).split('\n')
                    var from = 0
                    if (!ours) {
                        if (lines.first().trim() != "# $tok") return
                        ours = true
                        from = 1
                    }
                    pos += text.substring(0, cut + 1).toByteArray(Charsets.UTF_8).size.toLong()
                    for (raw in lines.drop(from)) {
                        val line = raw.trim()
                        if (line.isEmpty()) continue
                        if (line.contains("sing-box started") || Tun.important.containsMatchIn(line) ||
                            line.startsWith("Error") || line.startsWith("rayscope:")
                        ) {
                            Log.add("tun: " + line.take(400))
                        }
                    }
                }
            } catch (e: Exception) {
                // no log yet
            }
        }
    }

    fun stop() {
        if (Paths.isWindows) {
            val pr = proc ?: return
            proc = null
            kill(pr)
            try {
                if (!pr.waitFor(5, TimeUnit.SECONDS)) pr.destroyForcibly()
            } catch (e: Exception) {
                pr.destroyForcibly()
            }
        } else {
            val tok = token ?: return
            token = null
            try {
                LinuxHelper.send("stop $tok")
                val end = System.currentTimeMillis() + 8_000L
                while (System.currentTimeMillis() < end && LinuxHelper.running(tok)) Thread.sleep(100)
            } catch (e: Exception) {
                Log.add("TUN: ${e.message}")
            }
        }
        Log.add("TUN выключен")
    }

    private fun kill(pr: Process) {
        try {
            pr.outputStream.close()
        } catch (e: Exception) {
            // already closed
        }
        pr.destroy()
    }

    private fun resolve(host: String): List<String> = try {
        InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.distinct()
    } catch (e: Exception) {
        emptyList()
    }

    private fun plainDns(p: Prefs): String =
        p.dns.split(',', ' ', ';').map { it.trim() }.firstOrNull { IPV4.matches(it) } ?: "1.1.1.1"

    /** Exact match for a path, case-insensitive on Windows (sing-box uses Go regular expressions). */
    private fun pathRegex(f: File): String {
        val sb = StringBuilder(if (Paths.isWindows) "(?i)^" else "^")
        for (ch in f.absolutePath) {
            if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0) sb.append('\\')
            sb.append(ch)
        }
        return sb.append('$').toString()
    }

    private fun config(s: Server, serverIps: List<String>, socksPort: Int, p: Prefs): String {
        val dns = plainDns(p)
        val self = JSONArray()
        val cores = JSONArray()
        if (Paths.isWindows) {
            self.put(pathRegex(File(Paths.home, "runtime\\bin\\javaw.exe"))).put(pathRegex(File(Paths.home, "runtime\\bin\\java.exe")))
            cores.put("xray.exe").put("sing-box.exe")
        } else {
            self.put(pathRegex(File(Paths.home, "runtime/bin/java")))
            cores.put("xray").put("sing-box")
        }

        val dnsRules = JSONArray()
        // the server name must resolve without the tunnel, otherwise the core waits for itself
        if (!IPV4.matches(s.address) && !s.address.contains(':')) {
            dnsRules.put(JSONObject().put("domain", JSONArray().put(s.address)).put("server", "dns-direct"))
        }
        dnsRules.put(JSONObject().put("process_name", cores).put("server", "dns-direct"))
        dnsRules.put(JSONObject().put("process_path_regex", self).put("server", "dns-direct"))
        val dnsBlock = JSONObject()
            .put(
                "servers", JSONArray()
                    .put(JSONObject().put("type", "tcp").put("tag", "dns-remote").put("server", dns).put("detour", "proxy"))
                    .put(JSONObject().put("type", "udp").put("tag", "dns-direct").put("server", dns))
            )
            .put("rules", dnsRules)
            .put("final", "dns-remote")
            .put("strategy", "ipv4_only")

        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("interface_name", IFACE)
            .put("address", JSONArray().put("172.19.0.1/30"))
            .put("auto_route", true)
            .put("strict_route", true)
        if (!Paths.isWindows) {
            // the default "mixed" stack takes TCP through the kernel: firewalld / ufw drop those connections on the
            // new interface, so only DNS (UDP) got through. gVisor handles TCP itself, no firewall in the way.
            tun.put("stack", "gvisor")
        }
        val exclude = JSONArray()
        serverIps.forEach { exclude.put("$it/32") }
        if (exclude.length() > 0) tun.put("route_exclude_address", exclude)

        val outbounds = JSONArray()
            .put(JSONObject().put("type", "socks").put("tag", "proxy").put("server", "127.0.0.1").put("server_port", socksPort).put("version", "5"))
            .put(JSONObject().put("type", "direct").put("tag", "direct"))

        val rules = JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
            .put(JSONObject().put("process_name", cores).put("outbound", "direct"))
            .put(JSONObject().put("process_path_regex", self).put("outbound", "direct"))
            .put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))

        // Linux: the log lives in /run (RAM) for the whole session, so no line per connection
        val level = if (Paths.isWindows) "info" else "warn"
        val o = JSONObject()
            .put("log", JSONObject().put("level", level).put("timestamp", false))
            .put("dns", dnsBlock)
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", outbounds)
            .put(
                "route", JSONObject()
                    .put("rules", rules)
                    .put("final", "proxy")
                    .put("auto_detect_interface", true)
                    .put("default_domain_resolver", "dns-direct")
            )
        // one line: on Linux the config goes to the helper as a single command
        return if (Paths.isWindows) o.toString(2) else o.toString()
    }
}

/**
 * Linux TUN helper. The first TUN start asks the administrator password once (pkexec) and installs a root helper in
 * /run/rayscope-UID: a system service (or a detached process) that runs sing-box on request through a FIFO only this
 * user can write. /run is emptied on every boot, so the password is asked again only after a full reboot.
 * sing-box itself runs as the user with just the network capabilities when the system allows it (setpriv).
 */
object LinuxHelper {
    /** Bump when tun-install.sh / tun-helper.sh change: a helper of an older version is replaced. */
    private const val VERSION = "1"

    /** Separator of the arguments in /proc/PID/cmdline. */
    private val NUL: Char = Char(0)

    private val uid: Int by lazy {
        try {
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("Uid:") }
                ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private val dir: File get() = File("/run/rayscope-$uid")
    private val ctl: File get() = File(dir, "ctl")
    val log: File get() = File(dir, "tun.log")

    private val rnd = SecureRandom()

    private fun read(f: File): String? = try {
        f.readText()
    } catch (e: Exception) {
        null
    }

    fun newToken(): String =
        java.lang.Long.toHexString(rnd.nextLong() and Long.MAX_VALUE) + java.lang.Long.toHexString(System.nanoTime() and 0xffffffL)

    /** The helper of this boot is up and listening. */
    fun alive(): Boolean {
        if (Paths.isWindows || uid < 0) return false
        val pid = read(File(dir, "pid"))?.trim()?.toLongOrNull() ?: return false
        val cmd = try {
            String(File("/proc/$pid/cmdline").readBytes(), Charsets.UTF_8).replace(NUL, ' ')
        } catch (e: Exception) {
            return false
        }
        return cmd.contains(File(dir, "helper.sh").path) && ctl.exists()
    }

    /** "run TOKEN PID", "exit TOKEN CODE", "stopped TOKEN" or "idle". */
    fun state(): List<String> = read(File(dir, "state"))?.trim()?.split(' ').orEmpty()

    fun running(tok: String): Boolean {
        val st = state()
        if (st.size < 3 || st[0] != "run" || st[1] != tok) return false
        val pid = st[2].toLongOrNull() ?: return false
        return ProcessHandle.of(pid).map { it.isAlive }.orElse(false) == true
    }

    @Synchronized
    fun ensure(exe: File) {
        if (alive()) {
            if (read(File(dir, "version"))?.trim() == VERSION) return
            // a helper of an older RayScope: the installer below replaces it
            try {
                send("quit")
            } catch (e: Exception) {
                // the installer stops it anyway
            }
            Thread.sleep(500)
        }
        if (Paths.which("pkexec") == null) throw IOException("Для TUN в Linux нужен pkexec: установите пакет pkexec или policykit-1")
        val install = resource("/tun-install.sh")
        val helper = resource("/tun-helper.sh")
        Log.add("TUN: система спросит пароль администратора, один раз до перезагрузки")
        val pr = ProcessBuilder("pkexec", "/bin/sh", "-c", install, "rayscope-tun", helper, exe.absolutePath, VERSION)
            .redirectErrorStream(true)
            .start()
        val out = StringBuffer()
        val reader = thread(isDaemon = true, name = "tun-install") {
            try {
                pr.inputStream.bufferedReader().forEachLine { out.append(it).append(' ') }
            } catch (e: Exception) {
                // ended
            }
        }
        if (!pr.waitFor(180, TimeUnit.SECONDS)) {
            pr.destroy()
            throw IOException("TUN не включён: окно пароля администратора не закрыто")
        }
        reader.join(1000)
        val code = pr.exitValue()
        if (code == 126 || code == 127) throw IOException("TUN не включён: пароль администратора не подтверждён")
        if (code != 0) throw IOException("TUN: не удалось запустить помощник ($code) " + out.toString().trim().take(300))
        repeat(30) {
            if (alive()) {
                Log.add("TUN: помощник запущен, до перезагрузки пароль больше не нужен")
                return
            }
            Thread.sleep(100)
        }
        throw IOException("TUN: помощник не запустился")
    }

    /** One command to the helper. Opening the FIFO would hang forever without the helper, so it's checked first. */
    @Synchronized
    fun send(line: String) {
        if (!alive()) throw IOException("помощник TUN не запущен")
        val err = AtomicReference<Exception?>(null)
        val t = thread(isDaemon = true, name = "tun-ctl") {
            try {
                FileOutputStream(ctl).use { it.write((line + "\n").toByteArray(Charsets.UTF_8)) }
            } catch (e: Exception) {
                err.set(e)
            }
        }
        t.join(3000)
        if (t.isAlive) throw IOException("помощник TUN не отвечает")
        val e = err.get()
        if (e != null) throw IOException("помощник TUN: " + e.message)
    }

    private fun resource(name: String): String {
        val stream = LinuxHelper::class.java.getResourceAsStream(name) ?: throw IOException("нет $name в сборке")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}

/**
 * Administrator rights on Windows: TUN needs them to create the network adapter. Linux needs nothing up front,
 * TUN asks for the password itself, so it counts as ready.
 */
object Elevation {
    val isAdmin: Boolean by lazy {
        if (!Paths.isWindows) {
            true
        } else {
            try {
                NativeLibrary.getInstance("shell32").getFunction("IsUserAnAdmin").invokeInt(arrayOf<Any>()) != 0
            } catch (t: Throwable) {
                false
            }
        }
    }

    /** Starts RayScope.exe again with the UAC prompt. false if Windows or the user refused. */
    fun relaunch(args: List<String>): Boolean {
        val exe = File(Paths.home, "RayScope.exe")
        if (!Paths.isWindows || !exe.isFile) {
            Log.add("Перезапуск от имени администратора: не найден " + exe.absolutePath)
            return false
        }
        fun q(v: String) = "'" + v.replace("'", "''") + "'"
        val argPart = if (args.isEmpty()) "" else " -ArgumentList " + args.joinToString(",") { q(it) }
        val script = "try { Start-Process -FilePath " + q(exe.absolutePath) + " -WorkingDirectory " + q(exe.parentFile.absolutePath) +
            " -Verb RunAs" + argPart + " -ErrorAction Stop; exit 0 } catch { Write-Output \$_.Exception.Message; exit 1 }"
        return try {
            val pr = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true)
                .start()
            val out = pr.inputStream.bufferedReader().readText()
            val done = pr.waitFor(180, TimeUnit.SECONDS)
            val ok = done && pr.exitValue() == 0
            if (!ok) Log.add("Перезапуск от имени администратора: " + out.trim().take(300))
            ok
        } catch (e: Exception) {
            Log.add("Перезапуск от имени администратора: ${e.message}")
            false
        }
    }
}

/** Mode switching from any screen. TUN without admin rights asks to restart RayScope elevated. */
object Modes {
    val askAdmin = MutableStateFlow(false)

    fun request(mode: Int) {
        if (mode == Mode.TUN && !Tun.supported) {
            Notice.show("Для TUN нужен pkexec: установите пакет pkexec или policykit-1")
            return
        }
        if (mode == Mode.TUN && !Elevation.isAdmin) {
            askAdmin.value = true
            return
        }
        Engine.setMode(mode)
    }

    /** Saves TUN as the mode and restarts elevated. Blocks while the UAC prompt is open: call off the UI thread. */
    fun restartElevated() {
        askAdmin.value = false
        val prev = Store.prefs.value.mode
        val wasOn = Engine.status.value.state != ConnState.STOPPED
        Store.setPrefs { it.copy(mode = Mode.TUN) }
        SingleInstance.release()
        if (Elevation.relaunch(if (wasOn) listOf("--connect") else emptyList())) {
            Engine.shutdownNow()
            exitProcess(0)
        }
        SingleInstance.acquire()
        Store.setPrefs { it.copy(mode = prev) }
        Notice.show("Без прав администратора TUN не включить")
    }
}
