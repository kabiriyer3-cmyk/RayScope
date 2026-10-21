package com.rayscope.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.RemoteViews
import com.rayscope.app.R
import com.rayscope.app.core.ConnState
import com.rayscope.app.core.RayVpnService
import com.rayscope.app.core.VpnState
import com.rayscope.app.data.Repo
import com.rayscope.app.data.UiPrefs
import com.rayscope.app.ui.MainActivity
import com.rayscope.app.util.Fmt

/** 2x2: big connect button + state. */
class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.update(context)
    }
}

/** 4x1: flag, server, state with live ping / speed, connect button. */
class WideWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.update(context)
    }
}

object Widgets {
    private val GOOD = 0xFF3DDC97.toInt()
    private val MID = 0xFFFFB547.toInt()
    private val BAD = 0xFFFF5C6C.toInt()
    private val INK = 0xFF051116.toInt()
    private val RAISED = 0xFF1C2028.toInt()
    private val TEXT = 0xFFF1F3F7.toInt()
    private val HINT = 0xFF8A93A2.toInt()

    /** Re-render every placed RayScope widget from the current VPN state. Safe to call from any thread. */
    fun update(ctx: Context) {
        try {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            val small = m.getAppWidgetIds(ComponentName(ctx, SmallWidget::class.java))
            val wide = m.getAppWidgetIds(ComponentName(ctx, WideWidget::class.java))
            if (small.isEmpty() && wide.isEmpty()) return

            val v = VpnState.status.value
            val server = Repo.server(v.serverId ?: "") ?: Repo.selected()
            val accent = UiPrefs.accentArgb(Repo.ui.value.accent)
            val connected = v.state == ConnState.CONNECTED
            val busy = v.state == ConnState.CONNECTING || v.state == ConnState.STOPPING
            val failed = !connected && !busy && v.error.isNotEmpty()
            val stateText = when (v.state) {
                ConnState.CONNECTED -> "Защищено"
                ConnState.CONNECTING -> "Подключение…"
                ConnState.STOPPING -> "Отключение…"
                ConnState.STOPPED -> if (failed) "Ошибка" else "Выключено"
            }
            val name = server?.cleanName() ?: "Нет сервера"
            val flag = server?.flagEmoji().orEmpty().ifEmpty { "\uD83C\uDF10" }
            val sub = when {
                connected -> listOfNotNull(
                    if (v.livePing >= 0) "${v.livePing} ms" else null,
                    "↓ ${Fmt.speed(v.downSpeed)}"
                ).joinToString(" · ")
                busy -> "подождите"
                failed -> v.error
                server != null && server.delay >= 0 -> "${server.delay} ms"
                else -> "нажмите кнопку"
            }
            val btnBg = when {
                connected -> GOOD
                busy -> MID
                else -> RAISED
            }
            val btnFg = if (connected || busy) INK else accent
            val stateColor = when {
                connected -> GOOD
                busy -> MID
                failed -> BAD
                else -> HINT
            }
            val icon = if (connected || busy) R.drawable.w_ic_stop else R.drawable.w_ic_power

            if (small.isNotEmpty()) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_small)
                rv.setTextViewText(R.id.w_state, stateText)
                rv.setTextColor(R.id.w_state, if (connected || busy || failed) stateColor else TEXT)
                rv.setTextViewText(R.id.w_sub, name)
                rv.setInt(R.id.w_btn_bg, "setColorFilter", btnBg)
                rv.setImageViewResource(R.id.w_btn_icon, icon)
                rv.setInt(R.id.w_btn_icon, "setColorFilter", btnFg)
                rv.setOnClickPendingIntent(R.id.w_btn, toggleIntent(ctx, connected || busy))
                rv.setOnClickPendingIntent(R.id.w_root, openIntent(ctx))
                m.updateAppWidget(small, rv)
            }
            if (wide.isNotEmpty()) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_wide)
                rv.setTextViewText(R.id.w_flag, flag)
                rv.setTextViewText(R.id.w_name, name)
                rv.setTextViewText(R.id.w_state, stateText)
                rv.setTextColor(R.id.w_state, stateColor)
                rv.setTextViewText(R.id.w_sub, sub)
                rv.setInt(R.id.w_btn_bg, "setColorFilter", btnBg)
                rv.setImageViewResource(R.id.w_btn_icon, icon)
                rv.setInt(R.id.w_btn_icon, "setColorFilter", btnFg)
                rv.setOnClickPendingIntent(R.id.w_btn, toggleIntent(ctx, connected || busy))
                rv.setOnClickPendingIntent(R.id.w_root, openIntent(ctx))
                m.updateAppWidget(wide, rv)
            }
        } catch (e: Throwable) {
            android.util.Log.w("RayScope", "widget update failed", e)
        }
    }

    private fun openIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 10,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun toggleIntent(ctx: Context, running: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        if (running) {
            val stop = Intent(ctx, RayVpnService::class.java).setAction(RayVpnService.ACTION_STOP)
            return PendingIntent.getService(ctx, 11, stop, flags)
        }
        val needsUi = try {
            VpnService.prepare(ctx) != null
        } catch (e: Exception) {
            true
        } || Repo.servers.value.isEmpty() || Repo.settings.pickBestOnConnect
        if (needsUi) {
            val i = Intent(ctx, MainActivity::class.java)
                .setAction(MainActivity.ACTION_CONNECT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, 12, i, flags)
        }
        val start = Intent(ctx, RayVpnService::class.java)
        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(ctx, 13, start, flags)
        } else {
            PendingIntent.getService(ctx, 13, start, flags)
        }
    }
}
