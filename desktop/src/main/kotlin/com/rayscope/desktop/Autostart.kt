package com.rayscope.desktop

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * "Start with Windows": a value in HKCU\...\Run pointing at RayScope.exe with --tray.
 * The registry is the source of truth, so the switch also reflects changes made in Task Manager.
 */
object Autostart {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "RayScope"

    private fun exe(): File? = File(Paths.home, "RayScope.exe").takeIf { it.isFile }

    /** Only the packaged build has a launcher to register. */
    val available: Boolean get() = Paths.isWindows && exe() != null

    fun isEnabled(): Boolean {
        if (!Paths.isWindows) return false
        return run(listOf("reg", "query", KEY, "/v", NAME)) == 0
    }

    fun set(enable: Boolean): Boolean {
        if (!Paths.isWindows) return false
        if (!enable) return run(listOf("reg", "delete", KEY, "/v", NAME, "/f")) == 0 || !isEnabled()
        val path = exe()?.absolutePath ?: return false
        // the value holds quotes and maybe spaces: go through PowerShell with an encoded command
        val value = "\"$path\" --tray".replace("'", "''")
        val script = "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$NAME' -Value '$value'"
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return run(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)) == 0
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
