package com.rayscope.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings backup as one JSON string (clipboard friendly): every preference, the subscription URLs
 * and the servers that were added as plain links. Servers from subscriptions come back on update.
 */
object Backup {
    private const val FORMAT = "rayscope-backup"
    private const val PREFS = "settings"

    /** Device state, not preferences. */
    private val SKIP = setOf("selected")

    fun export(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefs = JSONObject()
        for ((k, v) in p.all) {
            if (k in SKIP) continue
            val e = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            prefs.put(k, e)
        }
        val subs = JSONArray()
        Repo.subs.value.forEach { subs.put(JSONObject().put("url", it.url).put("name", it.name)) }
        val links = JSONArray()
        Repo.servers.value.filter { it.subId.isEmpty() && it.rawLink.isNotEmpty() }.forEach { links.put(it.rawLink) }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("prefs", prefs)
            .put("subs", subs)
            .put("links", links)
            .toString()
    }

    /** Applies a backup and returns a short summary. Throws [IllegalArgumentException] for foreign text. */
    suspend fun import(ctx: Context, text: String): String {
        val o = try {
            JSONObject(text.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("В буфере нет резервной копии RayScope")
        }
        if (o.optString("format") != FORMAT) throw IllegalArgumentException("В буфере нет резервной копии RayScope")

        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit()
        val prefs = o.optJSONObject("prefs") ?: JSONObject()
        var restored = 0
        for (k in prefs.keys()) {
            if (k in SKIP) continue
            val e = prefs.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> edit.putBoolean(k, e.optBoolean("v"))
                "i" -> edit.putInt(k, e.optInt("v"))
                "l" -> edit.putLong(k, e.optLong("v"))
                "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                "s" -> edit.putString(k, e.optString("v"))
                "ss" -> {
                    val a = e.optJSONArray("v") ?: JSONArray()
                    edit.putStringSet(k, (0 until a.length()).map { a.optString(it) }.toSet())
                }
                else -> continue
            }
            restored++
        }
        edit.commit()
        Repo.setUi { UiPrefs.load(p) }

        val known = Repo.subs.value.map { it.url }.toSet()
        val subs = o.optJSONArray("subs") ?: JSONArray()
        var added = 0
        for (i in 0 until subs.length()) {
            val s = subs.optJSONObject(i) ?: continue
            val url = s.optString("url")
            if (url.isBlank() || url in known) continue
            Actions.addSubscription(url, s.optString("name"))
            added++
        }
        val links = o.optJSONArray("links") ?: JSONArray()
        if (links.length() > 0) {
            Actions.importText((0 until links.length()).joinToString("\n") { links.optString(it) })
        }
        return "настроек $restored, новых подписок $added, ссылок ${links.length()}"
    }

    /** Every preference back to default. Servers, subscriptions and the selected server stay. */
    fun reset(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = p.getString("selected", null)
        val edit = p.edit().clear()
        if (selected != null) edit.putString("selected", selected)
        edit.commit()
        Repo.setUi { UiPrefs() }
    }
}
