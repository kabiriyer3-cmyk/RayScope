package com.rayscope.app

import android.app.Application
import com.rayscope.app.core.CoreManager
import com.rayscope.app.data.Actions
import com.rayscope.app.data.Repo
import com.rayscope.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        if (Repo.settings.autoUpdateSubs && Repo.subs.value.isNotEmpty()) {
            scope.launch { Actions.updateAllSubs(silent = true) }
        }
    }

    companion object {
        lateinit var instance: App
            private set
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
