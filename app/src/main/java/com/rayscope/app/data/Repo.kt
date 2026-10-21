package com.rayscope.app.data

import android.content.Context
import com.rayscope.app.model.Server
import com.rayscope.app.model.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Repo {
    private lateinit var dir: File
    lateinit var settings: Settings
        private set

    val servers = MutableStateFlow<List<Server>>(emptyList())
    val subs = MutableStateFlow<List<Subscription>>(emptyList())
    val selectedId = MutableStateFlow<String?>(null)

    fun init(ctx: Context) {
        dir = ctx.filesDir
        settings = Settings(ctx)
        servers.value = dedupeIds(loadList(File(dir, "servers.json")) { Server.fromJson(it) })
        subs.value = loadList(File(dir, "subs.json")) { Subscription.fromJson(it) }
        selectedId.value = settings.selectedId.ifEmpty { null }
    }

    private fun dedupeIds(list: List<Server>): List<Server> {
        val used = HashSet<String>()
        return list.map { if (used.add(it.id)) it else it.copy(id = java.util.UUID.randomUUID().toString()) }
    }

    private fun <T> loadList(f: File, parse: (JSONObject) -> T): List<T> = try {
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> runCatching { parse(arr.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun save() {
        try {
            val a = JSONArray()
            servers.value.forEach { a.put(it.toJson()) }
            val tmp = File(dir, "servers.json.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(File(dir, "servers.json"))
            val b = JSONArray()
            subs.value.forEach { b.put(it.toJson()) }
            File(dir, "subs.json").writeText(b.toString())
        } catch (e: Exception) {
            android.util.Log.e("RayScope", "save failed", e)
        }
    }

    fun select(id: String?) {
        selectedId.value = id
        settings.selectedId = id ?: ""
    }

    fun selected(): Server? {
        val id = selectedId.value
        return servers.value.firstOrNull { it.id == id } ?: servers.value.firstOrNull()
    }

    fun server(id: String): Server? = servers.value.firstOrNull { it.id == id }

    fun update(id: String, f: (Server) -> Server) {
        servers.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    fun add(list: List<Server>) {
        servers.update { it + list }
        save()
    }

    fun remove(ids: Set<String>) {
        servers.update { l -> l.filterNot { it.id in ids } }
        val sel = selectedId.value
        if (sel != null && sel in ids) select(null)
        save()
    }

    fun toggleFavorite(id: String) {
        update(id) { it.copy(favorite = !it.favorite) }
        save()
    }

    fun resetTests() {
        servers.update { l ->
            l.map { it.copy(delay = Server.DELAY_UNTESTED, tcpPing = Server.DELAY_UNTESTED, lastError = "", successCount = 0, failCount = 0) }
        }
        save()
    }

    fun upsertSub(s: Subscription) {
        subs.update { l -> if (l.any { it.id == s.id }) l.map { if (it.id == s.id) s else it } else l + s }
        save()
    }

    fun removeSub(id: String) {
        subs.update { l -> l.filterNot { it.id == id } }
        servers.update { l -> l.filterNot { it.subId == id } }
        val sel = selectedId.value
        if (sel != null && servers.value.none { it.id == sel }) select(servers.value.firstOrNull()?.id)
        save()
    }

    /** Replace servers of a subscription while keeping ids, favorites and test history. */
    fun replaceSubServers(subId: String, fresh: List<Server>) {
        servers.update { list ->
            val old = list.filter { it.subId == subId }.associateBy { it.key() }
            val used = HashSet<String>()
            val merged = fresh.map { n ->
                val o = old[n.key()]
                if (o == null || !used.add(o.id)) n else n.copy(
                    id = o.id,
                    delay = o.delay,
                    tcpPing = o.tcpPing,
                    lastTested = o.lastTested,
                    lastError = o.lastError,
                    favorite = o.favorite,
                    successCount = o.successCount,
                    failCount = o.failCount,
                )
            }
            val firstIdx = list.indexOfFirst { it.subId == subId }
            val others = list.filter { it.subId != subId }
            if (firstIdx < 0) others + merged
            else {
                val insertAt = list.take(firstIdx).count { it.subId != subId }
                others.take(insertAt) + merged + others.drop(insertAt)
            }
        }
        val sel = selectedId.value
        if (sel != null && servers.value.none { it.id == sel }) select(servers.value.firstOrNull()?.id)
        save()
    }
}
