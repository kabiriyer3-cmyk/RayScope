package com.rayscope.desktop

import com.sun.jna.NativeLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
 * Windows: RayScope itself runs as administrator. Linux: sing-box gets root through the pkexec password dialog, and
 * since a root process can't be killed from here, it lives exactly as long as our end of its stdin stays open.
 */
object Tun {
    @Volatile
    private var proc: Process? = null

    val running: Boolean get() = proc?.isAlive == true

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Root shell for Linux: $1 sing-box, $2 config, $3 work dir. Stops sing-box when stdin (kept in fd 3) closes. */
    private const val ROOT_SCRIPT =
        "exec 3<&0; \"\$1\" run -c \"\$2\" -D \"\$3\" --disable-color </dev/null & p=\$!; " +
            "( cat <&3 >/dev/null; kill \$p ) >/dev/null 2>&1 & exec 3<&-; wait \$p"

    /** Windows always (UAC), Linux when pkexec (polkit) is installed. */
    val supported: Boolean get() = Paths.isWindows || Paths.which("pkexec") != null

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
        val dir = File(Paths.data, "run").apply { mkdirs() }
        val cfg = File(dir, "tun.json")
        cfg.writeText(config(s, resolve(s.address), socksPort, p))
        val pr = if (Paths.isWindows) {
            ProcessBuilder(exe.absolutePath, "run", "-c", cfg.absolutePath, "-D", dir.absolutePath, "--disable-color")
                .directory(exe.parentFile)
                .redirectErrorStream(true)
                .start()
        } else {
            if (!exe.canExecute()) exe.setExecutable(true)
            ProcessBuilder("pkexec", "/bin/sh", "-c", ROOT_SCRIPT, "sh", exe.absolutePath, cfg.absolutePath, dir.absolutePath)
                .redirectErrorStream(true)
                .start()
        }
        // on Linux the password dialog is open meanwhile
        val wait = if (Paths.isWindows) 12L else 120L
        val up = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val important = Regex("\\b(WARN|ERROR|FATAL|PANIC)\\b")
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
            val code = pr.exitValue()
            if (!Paths.isWindows && (code == 126 || code == 127)) throw IOException("TUN не включён: пароль администратора не подтверждён")
            throw IOException("TUN не запустился: " + Log.tail(2))
        }
        proc = pr
        Log.add("TUN включён: весь трафик компьютера идёт через RayScope")
    }

    fun stop() {
        val pr = proc ?: return
        proc = null
        kill(pr)
        try {
            if (!pr.waitFor(5, TimeUnit.SECONDS)) pr.destroyForcibly()
        } catch (e: Exception) {
            pr.destroyForcibly()
        }
        Log.add("TUN выключен")
    }

    /** Closing stdin stops the root sing-box on Linux; destroy() is enough on Windows. */
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
            .put("interface_name", "RayScope")
            .put("address", JSONArray().put("172.19.0.1/30"))
            .put("auto_route", true)
            .put("strict_route", true)
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

        return JSONObject()
            .put("log", JSONObject().put("level", "info").put("timestamp", false))
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
            .toString(2)
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
