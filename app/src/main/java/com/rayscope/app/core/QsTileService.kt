package com.rayscope.app.core

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rayscope.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Quick Settings tile: one-tap connect / disconnect. */
class QsTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            VpnState.status.collect { refresh() }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val st = VpnState.status.value.state
        if (st == ConnState.CONNECTED || st == ConnState.CONNECTING) {
            RayVpnService.stop()
            return
        }
        if (VpnService.prepare(this) != null) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
        } else {
            RayVpnService.start(this)
        }
    }

    private fun refresh() {
        val t = qsTile ?: return
        val s = VpnState.status.value
        t.state = when (s.state) {
            ConnState.CONNECTED -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        t.label = "RayScope"
        if (Build.VERSION.SDK_INT >= 29) {
            t.subtitle = when (s.state) {
                ConnState.CONNECTED -> s.serverName
                ConnState.CONNECTING -> "Подключение…"
                else -> "Отключено"
            }
        }
        t.updateTile()
    }
}
