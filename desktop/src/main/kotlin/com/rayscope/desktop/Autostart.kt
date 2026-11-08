package com.rayscope.desktop

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Start with the system, minimized to the tray (--tray).
 * Windows: a value in HKCU\...\Run pointing at RayScope.exe. The registry is the source of truth, so the switch also
 * reflects changes made in Task Manager. Linux: ~/.config/autostart/rayscope.desktop pointing at the rayscope launcher.
 */
object Autostart {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "RayScope"

    private fun exe(): File? = File(Paths.home, if (Paths.isWindows) "RayScope.exe" else "rayscope").takeIf { it.isFile }

    private val desktopFile: File
        get() {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: File(System.getProperty("user.home"), ".config")
            return File(File(base, "autostart"), "rayscope.desktop")
        }

    /** Only the packaged build has a launcher to register. */
    val available: Boolean get() = exe() != null

    fun isEnabled(): Boolean {
        if (!Paths.isWindows) return desktopFile.isFile
        return run(listOf("reg", "query", KEY, "/v", NAME)) == 0
    }

    fun set(enable: Boolean): Boolean {
        if (!Paths.isWindows) return setLinux(enable)
        if (!enable) return run(listOf("reg", "delete", KEY, "/v", NAME, "/f")) == 0 || !isEnabled()
        val path = exe()?.absolutePath ?: return false
        // the value holds quotes and maybe spaces: go through PowerShell with an encoded command
        val value = "\"$path\" --tray".replace("'", "''")
        val script = "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$NAME' -Value '$value'"
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return run(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)) == 0
    }

    private fun setLinux(enable: Boolean): Boolean = try {
        val f = desktopFile
        if (!enable) {
            f.delete() || !f.exists()
        } else {
            val path = exe()?.absolutePath ?: return false
            // Exec quoting from the desktop entry spec
            val quoted = "\"" + path.replace("\\", "\\\\\\\\").replace("\"", "\\\\\"").replace("`", "\\\\`").replace("$", "\\\\$") + "\""
            f.parentFile.mkdirs()
            f.writeText(
                "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=RayScope\n" +
                    "Comment=VPN-клиент на Xray\n" +
                    "Exec=$quoted --tray\n" +
                    "Icon=" + File(Paths.home, "rayscope.png").absolutePath + "\n" +
                    "Terminal=false\n" +
                    "X-GNOME-Autostart-enabled=true\n"
            )
            true
        }
    } catch (e: Exception) {
        Log.add("Автозапуск: ${e.message}")
        false
    }

    private fun run(cmd: List<String>): Int = try {
        val pr = ProcessBuilder(cmd).redirectErrorStream(true).start()
        pr.inputStream.bufferedReader().readText()
        if (pr.waitFor(15, TimeUnit.SECONDS)) pr.exitValue() else -1
    } catch (e: Exception) {
        Log.add("Автозапуск: ${e.message}")
        -1
    }
}
