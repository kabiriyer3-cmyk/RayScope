package com.rayscope.desktop

import com.rayscope.sub.ExitIp
import com.rayscope.sub.SubFormats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger

object Store {
    val servers = MutableStateFlow<List<Server>>(emptyList())
    val subs = MutableStateFlow<List<Subscription>>(emptyList())
    val prefs = MutableStateFlow(Prefs())
    val selectedId = MutableStateFlow<String?>(null)

    private fun file(name: String) = File(Paths.data, name)

    fun load() {
        prefs.value = try {
            Prefs.fromJson(JSONObject(file("prefs.json").readText()))
        } catch (e: Exception) {
            Prefs()
        }
        servers.value = dedupe(loadList(file("servers.json")) { Server.fromJson(it) })
        subs.value = loadList(file("subs.json")) { Subscription.fromJson(it) }
        selectedId.value = prefs.value.selectedId.ifEmpty { null }
    }

    private fun dedupe(list: List<Server>): List<Server> {
        val used = HashSet<String>()
        return list.map { if (used.add(it.id)) it else it.copy(id = java.util.UUID.randomUUID().toString()) }
    }

    private fun <T> loadList(f: File, parse: (JSONObject) -> T): List<T> = try {
        if (!f.exists()) {
            emptyList()
        } else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> runCatching { parse(arr.getJSONObject(i)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(name: String, text: String) {
        try {
            val target = file(name)
            val tmp = file("$name.tmp")
            tmp.writeText(text)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Log.add("Не удалось сохранить $name: ${e.message}")
        }
    }

    @Synchronized
    fun saveServers() {
        val a = JSONArray()
        servers.value.forEach { a.put(it.toJson()) }
        write("servers.json", a.toString())
        val b = JSONArray()
        subs.value.forEach { b.put(it.toJson()) }
        write("subs.json", b.toString())
    }

    @Synchronized
    fun savePrefs() {
        write("prefs.json", prefs.value.toJson().toString(2))
    }

    fun setPrefs(f: (Prefs) -> Prefs) {
        prefs.update(f)
        savePrefs()
    }

    fun select(id: String?) {
        selectedId.value = id
        setPrefs { it.copy(selectedId = id ?: "") }
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
        saveServers()
    }

    fun remove(ids: Set<String>) {
        servers.update { l -> l.filterNot { it.id in ids } }
        val sel = selectedId.value
        if (sel != null && sel in ids) select(servers.value.firstOrNull()?.id)
        saveServers()
    }

    fun toggleFavorite(id: String) {
        update(id) { it.copy(favorite = !it.favorite) }
        saveServers()
    }

    fun resetTests() {
        servers.update { l ->
            l.map {
                it.copy(
                    delay = Server.DELAY_UNTESTED, pingKind = 0, tcpPing = Server.DELAY_UNTESTED, lastError = "",
                    successCount = 0, failCount = 0, exitIp = "", exitCc = "", exitAt = 0,
                )
            }
        }
        saveServers()
    }

    fun upsertSub(s: Subscription) {
        subs.update { l -> if (l.any { it.id == s.id }) l.map { if (it.id == s.id) s else it } else l + s }
        saveServers()
    }

    fun removeSub(id: String) {
        subs.update { l -> l.filterNot { it.id == id } }
        servers.update { l -> l.filterNot { it.subId == id } }
        val sel = selectedId.value
        if (sel != null && servers.value.none { it.id == sel }) select(servers.value.firstOrNull()?.id)
        saveServers()
    }

    /** Replace servers of a subscription while keeping ids, favorites and test history. */
    fun replaceSubServers(subId: String, fresh: List<Server>) {
        servers.update { list ->
            val old = list.filter { it.subId == subId }.associateBy { it.key() }
            val used = HashSet<String>()
            val merged = fresh.map { n ->
                val o = old[n.key()]
                if (o == null || !used.add(o.id)) {
                    n
                } else {
                    n.copy(
                        id = o.id,
                        delay = o.delay,
                        pingKind = o.pingKind,
                        tcpPing = o.tcpPing,
                        lastTested = o.lastTested,
                        lastError = o.lastError,
                        favorite = o.favorite,
                        successCount = o.successCount,
                        failCount = o.failCount,
                        exitIp = o.exitIp,
                        exitCc = o.exitCc,
                        exitAt = o.exitAt,
                    )
                }
            }
            val firstIdx = list.indexOfFirst { it.subId == subId }
            val others = list.filter { it.subId != subId }
            if (firstIdx < 0) {
                others + merged
            } else {
                val insertAt = list.take(firstIdx).count { it.subId != subId }
                others.take(insertAt) + merged + others.drop(insertAt)
            }
        }
        val sel = selectedId.value
        if (sel != null && servers.value.none { it.id == sel }) select(servers.value.firstOrNull()?.id)
        saveServers()
    }
}

/** User actions shared by the screens. Long jobs run in [scope] so leaving a screen doesn't cancel them. */
object Actions {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val testing = MutableStateFlow(false)
    val progress = MutableStateFlow(0 to 0)
    val updating = MutableStateFlow(false)

    /** Kind of the check running now (see [Tester.KINDS]), for the progress line. */
    val running = MutableStateFlow(0)

    fun go(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun proxyPort(): Int? = if (Engine.isConnected) Engine.httpPort else null

    suspend fun importText(text: String) = withContext(Dispatchers.IO) {
        val t = text.trim()
        if (t.isEmpty()) {
            Notice.show("Буфер обмена пуст")
            return@withContext
        }
        // happ://add/https://..., v2raytun://import/..., clash://install-config?url=...
        val deep = SubFormats.deeplinkUrl(t)
        if (deep != null) {
            addSubscription(deep, "")
            return@withContext
        }
        if (t.startsWith("happ://", ignoreCase = true)) {
            Notice.show("Зашифрованные ссылки happ:// не поддерживаются: скопируйте обычную ссылку на подписку")
            return@withContext
        }
        // every https:// line is a subscription, the rest are servers (links, JSON configs, base64)
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val urls = lines.filter { SubFormats.isHttp(it) && !it.contains(' ') }
        for (u in urls) addSubscription(u, "")
        val rest = lines.filterNot { it in urls }.joinToString("\n")
        if (rest.isEmpty()) return@withContext
        val list = LinkParser.parseMany(rest)
        if (list.isEmpty()) {
            if (urls.isEmpty()) Notice.show("Не найдено ни ссылок vless / vmess / trojan / ss / hy2, ни подписки")
        } else {
            Store.add(list)
            if (Store.selectedId.value == null) Store.select(list.first().id)
            Notice.show("Добавлено серверов: ${list.size}")
            Log.add("Импорт: ${list.size} серверов")
        }
    }

    suspend fun addSubscription(url: String, name: String) {
        val u = url.trim()
        val existing = Store.subs.value.firstOrNull { it.url == u }
        updateSub(existing ?: Subscription(url = u, name = name.trim()), silent = false)
    }

    suspend fun updateSub(sub: Subscription, silent: Boolean): Boolean = withContext(Dispatchers.IO) {
        val ua = Store.prefs.value.userAgent
        try {
            val r = try {
                SubFetcher.fetch(sub, ua, null)
            } catch (e: Exception) {
                val port = proxyPort() ?: throw e
                Log.add("Подписка напрямую не загрузилась (${e.message}), пробую через VPN")
                SubFetcher.fetch(sub, ua, port)
            }
            Store.upsertSub(r.sub)
            Store.replaceSubServers(r.sub.id, r.servers)
            if (Store.selectedId.value == null) Store.select(r.servers.first().id)
            Log.add("Подписка ${r.sub.name}: ${r.servers.size} серверов")
            if (!silent) Notice.show("${r.sub.name}: ${r.servers.size} серверов")
            true
        } catch (e: Exception) {
            Log.add("Ошибка подписки ${sub.url}: ${e.message}")
            if (!silent) Notice.show("Ошибка подписки: ${e.message}")
            false
        }
    }

    suspend fun updateAllSubs(silent: Boolean) {
        if (updating.value) return
        updating.value = true
        try {
            var ok = 0
            for (s in Store.subs.value) if (updateSub(s, silent = true)) ok++
            if (!silent) Notice.show("Обновлено подписок: $ok из ${Store.subs.value.size}")
        } finally {
            updating.value = false
        }
    }

    private fun applyResult(id: String, d: Long, err: String, kind: Int = 0) {
        val ok = d >= 0
        Store.update(id) {
            it.copy(
                delay = if (ok) d else Server.DELAY_FAILED,
                pingKind = kind,
                tcpPing = if (kind == 1) d else it.tcpPing,
                lastTested = System.currentTimeMillis(),
                lastError = err,
                successCount = it.successCount + (if (ok) 1 else 0),
                failCount = it.failCount + (if (ok) 0 else 1),
            )
        }
    }

    private fun applyExit(id: String, e: ExitIp.Exit) {
        Store.update(id) { it.copy(exitIp = e.ip, exitCc = e.cc, exitAt = System.currentTimeMillis()) }
    }

    /** "Рабочих 40 из 141", plus how many of them leave from another country than their name says. */
    private fun summary(ok: Int, total: Int): String {
        val fake = Store.servers.value.count { it.isWorking && it.isFake() }
        return "Рабочих серверов: $ok из $total" + if (fake > 0) " · страна не та, что в названии: $fake" else ""
    }

    /** Ping every server the way the settings say: a request through the core, a TCP handshake or ICMP. */
    suspend fun pingAll(kind: Int = Store.prefs.value.pingMode) {
        if (kind == 1 || kind == 2) quickAll(kind) else testAll()
    }

    /** Real latency of every server through the core (also what "best server" relies on). */
    suspend fun testAll() = withContext(Dispatchers.IO) {
        if (testing.value) return@withContext
        val list = Store.servers.value
        if (list.isEmpty()) return@withContext
        testing.value = true
        running.value = 0
        progress.value = 0 to list.size
        val done = AtomicInteger(0)
        try {
            Tester.realDelays(list, Store.prefs.value, onExit = { id, e -> applyExit(id, e) }) { id, d, err ->
                applyResult(id, d, err)
                progress.value = done.incrementAndGet() to list.size
            }
            Store.saveServers()
            val ok = Store.servers.value.count { it.isWorking }
            Log.add("Проверка: рабочих $ok из ${list.size}")
            Notice.show(summary(ok, list.size))
        } finally {
            testing.value = false
        }
    }

    /** TCP handshake or ICMP for every server: seconds instead of minutes, but "answers" is not "works". */
    private suspend fun quickAll(kind: Int) = withContext(Dispatchers.IO) {
        if (testing.value) return@withContext
        val list = Store.servers.value
        if (list.isEmpty()) return@withContext
        testing.value = true
        running.value = kind
        progress.value = 0 to list.size
        val p = Store.prefs.value
        val done = AtomicInteger(0)
        val sem = Semaphore(32)
        try {
            // Hysteria2 runs over UDP: there is no TCP port to knock on, so it goes through the core
            val udp = if (kind == 1) list.filter { it.protocol == "hysteria2" } else emptyList()
            val udpIds = udp.map { it.id }.toSet()
            coroutineScope {
                list.filter { it.id !in udpIds }.map { s ->
                    async {
                        sem.withPermit {
                            val (d, err) = quick(s, kind, p.pingTimeout)
                            applyResult(s.id, d, err, kind)
                            progress.value = done.incrementAndGet() to list.size
                        }
                    }
                }.awaitAll()
            }
            if (udp.isNotEmpty()) {
                Tester.realDelays(udp, p, onExit = { id, e -> applyExit(id, e) }) { id, d, err ->
                    applyResult(id, d, err)
                    progress.value = done.incrementAndGet() to list.size
                }
            }
            Store.saveServers()
            val ok = Store.servers.value.count { it.isWorking }
            Log.add("Пинг ${Tester.KINDS[kind]}: отвечают $ok из ${list.size}")
            Notice.show("${Tester.KINDS[kind]}: отвечают $ok из ${list.size}")
        } finally {
            testing.value = false
            running.value = 0
        }
    }

    private fun quick(s: Server, kind: Int, timeout: Int): Pair<Long, String> = if (kind == 2) {
        val d = Tester.icmp(s, timeout)
        d to if (d >= 0) "" else "Не отвечает на ping: ICMP часто закрыт, это не значит, что сервер не работает"
    } else {
        val d = Tester.tcp(s, timeout)
        d to if (d >= 0) "" else "Порт не отвечает"
    }

    /** One server, the same way as the Ping button. */
    suspend fun testOne(id: String, kind: Int = Store.prefs.value.pingMode) = withContext(Dispatchers.IO) {
        val s = Store.server(id) ?: return@withContext
        val p = Store.prefs.value
        if (kind == 2 || (kind == 1 && s.protocol != "hysteria2")) {
            val (d, err) = quick(s, kind, p.pingTimeout)
            applyResult(id, d, err, kind)
        } else {
            Tester.realDelays(listOf(s), p, onExit = { sid, e -> applyExit(sid, e) }) { sid, d, err -> applyResult(sid, d, err) }
            val t = Tester.tcp(s, p.pingTimeout)
            Store.update(id) { it.copy(tcpPing = t) }
        }
        Store.saveServers()
    }

    suspend fun tcpAll() = withContext(Dispatchers.IO) {
        val p = Store.prefs.value
        val sem = Semaphore(32)
        coroutineScope {
            Store.servers.value.map { s ->
                async {
                    sem.withPermit {
                        val t = Tester.tcp(s, p.pingTimeout)
                        Store.update(s.id) { it.copy(tcpPing = t) }
                    }
                }
            }.awaitAll()
        }
        Store.saveServers()
        Notice.show("TCP-пинг готов")
    }

    suspend fun pickBest(): Server? {
        testAll()
        return Store.servers.value.filter { it.isWorking }.minByOrNull { it.delay }
    }

    fun removeDead(): Int {
        val dead = Store.servers.value.filter { it.isDead }.map { it.id }.toSet()
        if (dead.isNotEmpty()) Store.remove(dead)
        return dead.size
    }

    fun exportAll(): String = Store.servers.value.mapNotNull { it.rawLink.ifEmpty { null } }.joinToString(System.lineSeparator())
}
