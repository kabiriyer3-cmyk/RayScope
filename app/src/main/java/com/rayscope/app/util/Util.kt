package com.rayscope.app.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.rayscope.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX = 600
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun add(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        android.util.Log.i("RayScope", msg)
        lines.update { (it + "$time  $msg").takeLast(MAX) }
    }

    fun clear() {
        lines.value = emptyList()
    }
}

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun speed(bps: Long): String = bytes(bps) + "/s"

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    fun ago(ms: Long): String {
        if (ms <= 0) return "никогда"
        val d = (System.currentTimeMillis() - ms) / 1000
        return when {
            d < 60 -> "только что"
            d < 3600 -> "${d / 60} мин назад"
            d < 86400 -> "${d / 3600} ч назад"
            else -> "${d / 86400} дн назад"
        }
    }

    fun flag(cc: String): String {
        val u = cc.trim().uppercase(Locale.US)
        if (u.length != 2 || !u.all { it in 'A'..'Z' }) return "\uD83C\uDF10"
        val a = 0x1F1E6 + (u[0] - 'A')
        val b = 0x1F1E6 + (u[1] - 'A')
        return String(Character.toChars(a)) + String(Character.toChars(b))
    }

    fun ms(v: Long): String = when {
        v == -2L -> "—"
        v < 0 -> "ошибка"
        else -> "$v ms"
    }
}

fun toast(msg: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(App.instance, msg, Toast.LENGTH_SHORT).show()
    }
}
