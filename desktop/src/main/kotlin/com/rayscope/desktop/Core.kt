package com.rayscope.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Paths {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** Folder with RayScope.cmd, core/ and runtime/. */
    val home: File = File(System.getProperty("rayscope.home") ?: System.getProperty("user.dir") ?: ".").absoluteFile

    /** Settings, servers, generated configs. */
    val data: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".config")
        File(base, "RayScope").apply { mkdirs() }
    }

    fun xray(): File? {
        val name = if (isWindows) "xray.exe" else "xray"
        return listOf(File(home, "core"), home, File(data, "core")).map { File(it, name) }.firstOrNull { it.isFile }
    }
}

/** Short messages for the snackbar. */
object Notice {
    val flow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    fun show(text: String) {
        flow.tryEmit(text)
    }
}

object Log {
    val lines = MutableStateFlow<List<String>>(emptyList())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(s: String) {
        val t = synchronized(fmt) { fmt.format(Date()) }
        lines.update { (it + "$t  $s").takeLast(800) }
    }

    fun tail(n: Int): String = lines.value.takeLast(n).joinToString(" | ") { it.substringAfter("  ") }
}

/** The xray executable: version, run, stats. */
object Core {
    @Volatile
    private var cachedVersion: String? = null

    fun version(): String {
        cachedVersion?.let { return it }
        val exe = Paths.xray() ?: return "не найдено"
        val v = try {
            val pr = ProcessBuilder(exe.absolutePath, "version").redirectErrorStream(true).start()
            val out = pr.inputStream.bufferedReader().readText()
            pr.waitFor(5, TimeUnit.SECONDS)
            out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.split(' ')?.take(2)?.joinToString(" ") ?: "?"
        } catch (e: Exception) {
            "ошибка: ${e.message}"
        }
        cachedVersion = v
        return v
    }

    fun start(config: String, tag: String, log: Boolean): Process {
        val exe = Paths.xray() ?: throw IOException("Не найдено ядро: положите xray.exe в папку core рядом с программой")
        val dir = File(Paths.data, "run").apply { mkdirs() }
        val cfg = File(dir, "$tag.json")
        cfg.writeText(config)
        val pb = ProcessBuilder(exe.absolutePath, "run", "-c", cfg.absolutePath)
            .directory(exe.parentFile)
            .redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = exe.parentFile.absolutePath
        val pr = pb.start()
        thread(isDaemon = true, name = "xray-$tag") {
            try {
                pr.inputStream.bufferedReader().forEachLine { line ->
                    if (log && line.isNotBlank()) Log.add(line.take(400))
                }
            } catch (e: Exception) {
                // process ended
            }
        }
        return pr
    }

    fun cleanup(tag: String) {
        try {
            File(File(Paths.data, "run"), "$tag.json").delete()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun waitPort(port: Int, pr: Process, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (!pr.isAlive) return false
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
                return true
            } catch (e: Exception) {
                // not yet
            }
            Thread.sleep(100)
        }
        return false
    }

    fun portFree(port: Int): Boolean = try {
        ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { true }
    } catch (e: Exception) {
        false
    }

    fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Cumulative (uplink, downlink) bytes of the proxy outbound via the stats API. */
    fun stats(apiPort: Int): Pair<Long, Long>? {
        val exe = Paths.xray() ?: return null
        return try {
            val pr = ProcessBuilder(exe.absolutePath, "api", "statsquery", "--server=127.0.0.1:$apiPort")
                .redirectErrorStream(true).start()
            val out = pr.inputStream.bufferedReader().readText()
            pr.waitFor(3, TimeUnit.SECONDS)
            val start = out.indexOf('{')
            if (start < 0) return null
            val o = JSONObject(out.substring(start))
            val arr = o.optJSONArray("stat") ?: return 0L to 0L
            var up = 0L
            var down = 0L
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val n = e.optString("name")
                val v = e.opt("value")?.toString()?.toLongOrNull() ?: 0L
                if (n.startsWith("outbound>>>proxy>>>")) {
                    if (n.endsWith("uplink")) up = v else if (n.endsWith("downlink")) down = v
                }
            }
            up to down
        } catch (e: Exception) {
            null
        }
    }
}

/** Windows system proxy (WinINet) through a small PowerShell script. */
object SystemProxy {
    @Volatile
    var active = false
        private set

    @Volatile
    var port = 0
        private set

    private const val BYPASS =
        "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>"

    private val marker: File get() = File(Paths.data, "proxy.on")

    val supported: Boolean get() = Paths.isWindows

    @Synchronized
    fun set(enable: Boolean, httpPort: Int): Boolean {
        if (!supported) return false
        if (enable && active && port == httpPort) return true
        val ok = try {
            val script = File(Paths.data, "proxy.ps1")
            val res = SystemProxy::class.java.getResourceAsStream("/proxy.ps1")?.use { it.readBytes() }
                ?: throw IOException("нет proxy.ps1")
            script.writeBytes(res)
            val pr = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.absolutePath,
                "-Enable", if (enable) "1" else "0",
                "-Server", "127.0.0.1:$httpPort",
                "-Bypass", BYPASS
            ).redirectErrorStream(true).start()
            val out = pr.inputStream.bufferedReader().readText()
            val done = pr.waitFor(20, TimeUnit.SECONDS)
            val good = done && pr.exitValue() == 0
            if (!good) Log.add("Системный прокси: " + out.trim().take(300))
            good
        } catch (e: Exception) {
            Log.add("Системный прокси: ${e.message}")
            false
        }
        if (ok) {
            active = enable
            port = if (enable) httpPort else 0
            try {
                if (enable) marker.writeText("$httpPort") else marker.delete()
            } catch (e: Exception) {
                // ignore
            }
            Log.add(if (enable) "Системный прокси включён: 127.0.0.1:$httpPort" else "Системный прокси выключен")
        }
        return ok
    }

    /** If the app crashed with the proxy on, browsers would be left without internet. */
    fun cleanupStale() {
        if (!supported || !marker.exists()) return
        Log.add("Остался системный прокси от прошлого запуска, выключаю")
        set(false, 0)
        try {
            marker.delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}
