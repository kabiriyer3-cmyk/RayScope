package com.rayscope.app.data

import android.content.SharedPreferences

/** Look & feel preferences. Kept in one immutable object so the whole UI re-themes live. */
data class UiPrefs(
    /** 0 system, 1 light, 2 dark */
    val theme: Int = 0,
    /** index in [ACCENT_NAMES], -1 = Material You (Android 12+) */
    val accent: Int = 0,
    /** pure black background in dark theme */
    val amoled: Boolean = false,
    /** 0 sharp, 1 normal, 2 round */
    val corners: Int = 1,
    /** 0 small, 1 normal, 2 large */
    val textScale: Int = 1,
    val compact: Boolean = false,
    val motion: Boolean = true,
    val tabLabels: Boolean = true,
    val flags: Boolean = true,
    /** tab opened on launch: 0 home, 1 servers, 2 check */
    val startTab: Int = 0,
    /** short vibration when the tunnel goes up or down */
    val haptics: Boolean = true,
    /** developer mode: logs, histories, raw configs, core knobs */
    val dev: Boolean = false,
) {
    val cornerScale: Float
        get() = when (corners) {
            0 -> 0.45f
            2 -> 1.6f
            else -> 1f
        }

    val fontScale: Float
        get() = when (textScale) {
            0 -> 0.92f
            2 -> 1.1f
            else -> 1f
        }

    fun save(p: SharedPreferences) {
        p.edit()
            .putInt("theme", theme)
            .putInt("uiAccent", accent)
            .putBoolean("uiAmoled", amoled)
            .putInt("uiCorners", corners)
            .putInt("uiText", textScale)
            .putBoolean("uiCompact", compact)
            .putBoolean("uiMotion", motion)
            .putBoolean("uiTabLabels", tabLabels)
            .putBoolean("uiFlags", flags)
            .putInt("uiStartTab", startTab)
            .putBoolean("uiHaptics", haptics)
            .putBoolean("devMode", dev)
            .apply()
    }

    companion object {
        val ACCENT_NAMES = listOf("Индиго", "Синий", "Графит", "Зелёный", "Янтарь", "Красный", "Фиалка", "Бирюза")
        val ACCENTS_DARK = longArrayOf(0xFF7C84FF, 0xFF4E8EF7, 0xFFE6E6E9, 0xFF4CB782, 0xFFE2A93B, 0xFFEB5757, 0xFFA48CF2, 0xFF3FB8A9)
        val ACCENTS_LIGHT = longArrayOf(0xFF5864D8, 0xFF2F6FE0, 0xFF1A1B1F, 0xFF1F8A5B, 0xFFB07A10, 0xFFD13B3B, 0xFF7457D6, 0xFF168A7E)

        /** ARGB of the dark-theme accent (widgets are always dark). */
        fun accentArgb(index: Int): Int = ACCENTS_DARK.getOrElse(index) { ACCENTS_DARK[0] }.toInt()

        fun load(p: SharedPreferences): UiPrefs = UiPrefs(
            theme = p.getInt("theme", 0),
            accent = p.getInt("uiAccent", 0),
            amoled = p.getBoolean("uiAmoled", false),
            corners = p.getInt("uiCorners", 1),
            textScale = p.getInt("uiText", 1),
            compact = p.getBoolean("uiCompact", false),
            motion = p.getBoolean("uiMotion", true),
            tabLabels = p.getBoolean("uiTabLabels", true),
            flags = p.getBoolean("uiFlags", true),
            startTab = p.getInt("uiStartTab", 0).coerceIn(0, 2),
            haptics = p.getBoolean("uiHaptics", true),
            dev = p.getBoolean("devMode", false),
        )
    }
}
