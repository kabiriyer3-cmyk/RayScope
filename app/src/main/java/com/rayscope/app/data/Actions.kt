package com.rayscope.app.data

import com.rayscope.app.core.VpnState
import com.rayscope.app.model.Server
import com.rayscope.app.model.Subscription
import com.rayscope.app.net.Tester
import com.rayscope.app.parser.LinkParser
import com.rayscope.app.parser.SubFetcher
import com.rayscope.app.util.AppLog
import com.rayscope.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Business operations used by the UI. */
object Actions {
    val testing = MutableStateFlow(false)
    val progress = MutableStateFlow(0 to 0)

    suspend fun importText(raw: String) {
        var text = raw.trim()
        if (text.isEmpty()) {
            toast("Пусто: нечего импортировать")
            return
        }
        val lower = text.lowercase()
        if (lower.startsWith("rayscope://") || lower.startsWith("happ://add/") || lower.startsWith("v2rayng://install-config")) {
            val after = text.substringAfter("://")
            text = when {
                after.startsWith("add/", ignoreCase = true) -> after.substring(4)
                after.contains("url=") -> LinkParser.dec(after.substringAfter("url=").substringBefore('&'))
                else -> after
            }.trim()
        }
        if (text.lowercase().startsWith("happ://")) {
            toast("Зашифрованные happ:// ссылки не поддерживаются")
            return
        }
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            addSubscription(text.lines().first().trim())
            return
        }
        val list = LinkParser.parseMany(text)
        if (list.isEmpty()) {
            toast("Не найдено ссылок vless / vmess / trojan / ss / hy2")
            return
        }
        Repo.add(list)
        if (Repo.selectedId.value == null) Repo.select(list.first().id)
        AppLog.add("Импортировано серверов: ${list.size}")
        toast("Добавлено серверов: ${list.size}")
    }

    suspend fun addSubscription(url: String, name: String = "") {
        val existing = Repo.subs.value.firstOrNull { it.url == url }
        val sub = existing ?: Subscription(name = name, url = url)
        if (existing == null) Repo.upsertSub(sub)
        updateSub(sub, silent = false)
    }

    suspend fun updateSub(sub: Subscription, silent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val ua = Repo.settings.userAgent
        var res: SubFetcher.Result? = null
        var err: String? = null
        try {
            res = SubFetcher.fetch(sub, ua, false)
        } catch (e: Exception) {
            err = e.message ?: e.javaClass.simpleName
        }
        if (res == null && VpnState.isConnected) {
            try {
                res = SubFetcher.fetch(sub, ua, true)
                err = null
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            }
        }
        val r = res
        if (r == null) {
            AppLog.add("Подписка «${sub.name.ifEmpty { sub.url }}»: ошибка $err")
            if (!silent) toast("Ошибка обновления: $err")
            return@withContext false
        }
        Repo.upsertSub(r.sub)
        Repo.replaceSubServers(sub.id, r.servers)
        if (Repo.selectedId.value == null) Repo.select(Repo.servers.value.firstOrNull()?.id)
        AppLog.add("Подписка «${r.sub.name}»: ${r.servers.size} серверов")
        if (!silent) toast("${r.sub.name}: ${r.servers.size} серверов")
        if (Repo.settings.testAfterUpdate) {
            testAll(Repo.servers.value.filter { it.subId == sub.id }.map { it.id }, silent = silent)
        }
        true
    }

    suspend fun updateAllSubs(silent: Boolean) {
        val list = Repo.subs.value
        if (list.isEmpty()) {
            if (!silent) toast("Нет подписок")
            return
        }
        for (s in list) updateSub(s, silent)
    }

    /** Latency test for every (or given) server, according to the ping settings. Drives the working/dead filter. */
    suspend fun testAll(onlyIds: Collection<String>? = null, silent: Boolean = false) {
        if (!testing.compareAndSet(false, true)) return
        try {
            val idSet = onlyIds?.toSet()
            val targets = Repo.servers.value.filter { idSet == null || it.id in idSet }
            if (targets.isEmpty()) return
            progress.value = 0 to targets.size
            val sem = Semaphore(Repo.settings.concurrency.coerceIn(1, 32))
            val done = AtomicInteger(0)
            AppLog.add("Проверка ${targets.size} серверов (${modeLabel()})…")
            coroutineScope {
                targets.map { s ->
                    async(Dispatchers.IO) {
                        sem.withPermit { testOne(s.id) }
                        progress.value = done.incrementAndGet() to targets.size
                    }
                }.awaitAll()
            }
            Repo.save()
            val ids = targets.map { it.id }.toSet()
            val ok = Repo.servers.value.count { it.id in ids && it.isWorking }
            AppLog.add("Проверка завершена: рабочих $ok из ${targets.size}")
            if (!silent) toast("Рабочих: $ok из ${targets.size}")
        } finally {
            testing.value = false
        }
    }

    fun modeLabel(): String = when (Repo.settings.pingMode) {
        1 -> "реальная задержка"
        2 -> "только TCP"
        else -> "реальная задержка + TCP"
    }

    suspend fun testOne(id: String, save: Boolean = false) {
        val s = Repo.server(id) ?: return
        val st = Repo.settings
        val mode = st.pingMode
        val udp = s.protocol == "hysteria2"
        val tcp = when {
            udp -> Server.DELAY_UNTESTED
            mode == 1 -> s.tcpPing
            else -> Tester.tcpPing(s.address, s.port, st.pingTimeout)
        }
        val (d, err) = if (mode == 2 && !udp) {
            if (tcp >= 0) tcp to "" else -1L to "TCP: порт не отвечает"
        } else {
            Tester.realDelay(s)
        }
        Repo.update(id) {
            it.copy(
                delay = d,
                tcpPing = tcp,
                lastTested = System.currentTimeMillis(),
                lastError = err,
                successCount = it.successCount + (if (d >= 0) 1 else 0),
                failCount = it.failCount + (if (d < 0) 1 else 0),
            )
        }
        if (save) Repo.save()
    }

    suspend fun tcpAll() {
        if (!testing.compareAndSet(false, true)) return
        try {
            val list = Repo.servers.value
            val timeout = Repo.settings.pingTimeout
            progress.value = 0 to list.size
            val done = AtomicInteger(0)
            val sem = Semaphore(16)
            coroutineScope {
                list.map { s ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val t = if (s.protocol == "hysteria2") Server.DELAY_UNTESTED else Tester.tcpPing(s.address, s.port, timeout)
                            Repo.update(s.id) { it.copy(tcpPing = t) }
                        }
                        progress.value = done.incrementAndGet() to list.size
                    }
                }.awaitAll()
            }
            Repo.save()
            toast("TCP-пинг завершён")
        } finally {
            testing.value = false
        }
    }

    fun removeDead(): Int {
        val dead = Repo.servers.value.filter { it.isDead }.map { it.id }.toSet()
        if (dead.isNotEmpty()) Repo.remove(dead)
        AppLog.add("Удалено нерабочих: ${dead.size}")
        return dead.size
    }

    fun exportAll(): String = Repo.servers.value.filter { it.rawLink.isNotEmpty() }.joinToString("\n") { it.rawLink }

    /** Lowest real delay among working servers; retests if results are stale or missing. */
    suspend fun pickBest(): Server? {
        val now = System.currentTimeMillis()
        if (Repo.servers.value.none { it.isWorking }) {
            testAll(silent = true)
        } else {
            val stale = Repo.servers.value.filter { it.isWorking && now - it.lastTested > 10 * 60_000L }
            if (stale.isNotEmpty()) testAll(stale.map { it.id }, silent = true)
        }
        return Repo.servers.value.filter { it.isWorking }.minByOrNull { it.delay }
    }
}
