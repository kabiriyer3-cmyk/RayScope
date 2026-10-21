package com.rayscope.app.core

import android.content.Context
import com.rayscope.app.util.AppLog
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/** Thin wrapper over AndroidLibXrayLite (libv2ray.aar). */
object CoreManager {
    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0

        override fun shutdown(): Long = 0

        override fun onEmitStatus(p0: Long, p1: String?): Long {
            if (!p1.isNullOrBlank()) AppLog.add("core: $p1")
            return 0
        }
    }

    val controller: CoreController by lazy { Libv2ray.newCoreController(callback) }

    @Volatile
    private var inited = false

    fun init(ctx: Context) {
        if (inited) return
        Libv2ray.initCoreEnv(ctx.filesDir.absolutePath, "")
        inited = true
    }

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (e: Throwable) {
        "core n/a"
    }

    val isRunning: Boolean
        get() = try {
            controller.isRunning
        } catch (e: Throwable) {
            false
        }
}
