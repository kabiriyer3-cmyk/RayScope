package com.rayscope.sub

import java.util.Locale

/**
 * The address a server really lets you out on. Names and flags in a subscription are only text: a server called
 * "Germany" can leave through the Netherlands. A request through the server to Cloudflare's trace page returns the
 * exit address and its country, ip-api.com is the fallback. Shared by Android and Windows; each side makes the
 * request through its own core and hands the text to [parseTrace] or [parseIpApiLine].
 */
object ExitIp {
    const val TRACE = "https://www.cloudflare.com/cdn-cgi/trace"

    /** The fields come back one per line in the order of the ip-api docs: status, countryCode, query. */
    const val FALLBACK = "http://ip-api.com/line/?fields=status,countryCode,query"

    data class Exit(val ip: String, val cc: String)

    /** "ip=203.0.113.7", "loc=NL" and so on from the trace page. Null without an address. */
    fun parseTrace(text: String): Exit? {
        var ip = ""
        var cc = ""
        for (line in text.lineSequence()) {
            val k = line.substringBefore('=', "").trim()
            val v = line.substringAfter('=', "").trim()
            if (k == "ip") ip = v
            if (k == "loc") cc = v.uppercase(Locale.US)
        }
        if (ip.isEmpty()) return null
        // XX: unknown, T1: Tor
        return Exit(ip, if (cc.length == 2 && cc.all { it in 'A'..'Z' } && cc != "XX" && cc != "T1") cc else "")
    }

    /** "success", "NL", "203.0.113.7" from ip-api.com/line. */
    fun parseIpApiLine(text: String): Exit? {
        val l = text.lines().map { it.trim() }
        if (l.size < 3 || l[0] != "success" || l[2].isEmpty()) return null
        val cc = l[1].uppercase(Locale.US)
        return Exit(l[2], if (cc.length == 2) cc else "")
    }

    private val RU = Locale("ru")

    /** "Нидерланды" for NL; the code itself when Java doesn't know it. */
    fun countryName(cc: String): String {
        if (cc.length != 2) return cc
        val n = Locale("", cc.uppercase(Locale.US)).getDisplayCountry(RU)
        return if (n.isBlank() || n.equals(cc, ignoreCase = true)) cc.uppercase(Locale.US) else n
    }

    /** The name points at one country and the exit address sits in another. "Европа" fits anywhere. */
    fun mismatch(claimed: String, exit: String): Boolean =
        claimed.length == 2 && exit.length == 2 && !claimed.equals(exit, ignoreCase = true) && !claimed.equals("EU", ignoreCase = true)

    /** Country code of a flag emoji ("🇳🇱" -> "NL"), empty for anything else. */
    fun codeOf(flag: String): String {
        val cps = flag.codePoints().toArray()
        if (cps.size < 2) return ""
        val a = cps[0] - 0x1F1E6
        val b = cps[1] - 0x1F1E6
        if (a !in 0..25 || b !in 0..25) return ""
        return "" + ('A' + a) + ('A' + b)
    }

    /** Flag emoji for a country code ("NL" -> "🇳🇱"), empty for anything else. */
    fun flagOf(cc: String): String {
        val u = cc.trim().uppercase(Locale.US)
        if (u.length != 2 || !u.all { it in 'A'..'Z' }) return ""
        return String(Character.toChars(0x1F1E6 + (u[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (u[1] - 'A')))
    }

    /** Does a search query hit this exit: part of the address, the code ("nl") or the country's name ("нидерл"). */
    fun matches(q: String, ip: String, cc: String): Boolean {
        val t = q.trim()
        if (t.isEmpty()) return true
        if (ip.isNotEmpty() && ip.contains(t)) return true
        if (cc.isEmpty()) return false
        if (t.length == 2 && cc.equals(t, ignoreCase = true)) return true
        return t.length >= 3 && countryName(cc).contains(t, ignoreCase = true)
    }
}
