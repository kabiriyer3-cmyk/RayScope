package com.rayscope.app

import android.app.Application
import com.rayscope.app.core.CoreManager
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Repo.init(this)
        try {
            CoreManager.init(this)
        } catch (e: Throwable) {
            AppLog.add("Ошибка инициализации ядра: ${e.message}")
        }
        AppLog.add("RayScope запущен. ${CoreManager.version()}")
        val st = Repo.settings
        scope.launch {
            val updating = st.autoUpdateSubs && Repo.subs.value.isNotEmpty()
            if (updating) Actions.updateAllSubs(silent = true)
            // after an update with "test after update" the servers are already tested
            val alreadyTested = updating && st.testAfterUpdate
            if (st.testOnLaunch && !alreadyTested && Repo.servers.value.isNotEmpty()) Actions.testAll(silent = true)
        }
        // periodic re-test while the process is alive
        scope.launch {
            while (true) {
                val m = Repo.settings.autoRetestMinutes
                delay(if (m <= 0) 60_000L else m * 60_000L)
                if (Repo.settings.autoRetestMinutes > 0 && Repo.servers.value.isNotEmpty()) {
                    AppLog.add("Автопроверка серверов")
                    Actions.testAll(silent = true)
                }
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
