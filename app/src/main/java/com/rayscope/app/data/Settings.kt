package com.rayscope.app.data

import android.content.Context
import android.content.SharedPreferences

class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun str(k: String, d: String): String = p.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun putInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    var testUrl: String
        get() = str("testUrl", DEFAULT_TEST_URL)
        set(v) = putStr("testUrl", v)

    var dns: String
        get() = str("dns", "1.1.1.1, 8.8.8.8")
        set(v) = putStr("dns", v)

    var userAgent: String
        get() = str("ua", DEFAULT_UA)
        set(v) = putStr("ua", v)

    var selectedId: String
        get() = str("selected", "")
        set(v) = putStr("selected", v)

    var mtu: Int
        get() = p.getInt("mtu", 1500)
        set(v) = putInt("mtu", v)

    var concurrency: Int
        get() = p.getInt("conc", 8)
        set(v) = putInt("conc", v)

    /** 0 = system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = p.getInt("theme", 0)
        set(v) = putInt("theme", v)

    /** 0 = all apps, 1 = bypass selected apps, 2 = only selected apps */
    var perAppMode: Int
        get() = p.getInt("perAppMode", 0)
        set(v) = putInt("perAppMode", v)

    var perAppSet: Set<String>
        get() = p.getStringSet("perAppSet", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("perAppSet", v).apply()

    var bypassLan: Boolean
        get() = p.getBoolean("bypassLan", true)
        set(v) = putBool("bypassLan", v)

    var ipv6: Boolean
        get() = p.getBoolean("ipv6", false)
        set(v) = putBool("ipv6", v)

    var sniffing: Boolean
        get() = p.getBoolean("sniffing", true)
        set(v) = putBool("sniffing", v)

    var autoUpdateSubs: Boolean
        get() = p.getBoolean("autoUpdateSubs", true)
        set(v) = putBool("autoUpdateSubs", v)

    var autoFailover: Boolean
        get() = p.getBoolean("autoFailover", false)
        set(v) = putBool("autoFailover", v)

    var testAfterUpdate: Boolean
        get() = p.getBoolean("testAfterUpdate", false)
        set(v) = putBool("testAfterUpdate", v)

    fun dnsList(): List<String> =
        dns.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("1.1.1.1") }

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_UA = "v2rayNG/1.10.20"
    }
}
