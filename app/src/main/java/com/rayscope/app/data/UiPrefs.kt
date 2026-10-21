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
    /** Liquid Glass: 0 off, 1 soft, 2 strong */
    val glass: Int = 1,
    /** 0 sharp, 1 normal, 2 round */
    val corners: Int = 1,
    /** 0 small, 1 normal, 2 large */
    val textScale: Int = 1,
    val compact: Boolean = false,
    /** home dial: 0 scope, 1 minimal ring, 2 pulse */
    val dial: Int = 0,
    val motion: Boolean = true,
    val tabLabels: Boolean = true,
    val flags: Boolean = true,
    /** developer mode: logs, histories, raw configs, core knobs */
    val dev: Boolean = false,
) {
    val cornerScale: Float
        get() = when (corners) {
            0 -> 0.4f
            2 -> 1.4f
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
            .putInt("uiGlass", glass)
            .putInt("uiCorners", corners)
            .putInt("uiText", textScale)
            .putBoolean("uiCompact", compact)
            .putInt("uiDial", dial)
            .putBoolean("uiMotion", motion)
            .putBoolean("uiTabLabels", tabLabels)
            .putBoolean("uiFlags", flags)
            .putBoolean("devMode", dev)
            .apply()
    }

    companion object {
        val ACCENT_NAMES = listOf("Циан", "Мята", "Лайм", "Синий", "Фиалка", "Розовый", "Янтарь", "Коралл")
        val ACCENTS_DARK = longArrayOf(0xFF38E1FF, 0xFF3DDC97, 0xFFC6F432, 0xFF5B9BFF, 0xFFA78BFA, 0xFFFF6B9A, 0xFFFFB547, 0xFFFF7A59)
        val ACCENTS_LIGHT = longArrayOf(0xFF0091BA, 0xFF0E9F6E, 0xFF5E8C00, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFC27803, 0xFFE0482A)

        /** ARGB of the dark-theme accent (widgets are always dark). */
        fun accentArgb(index: Int): Int = ACCENTS_DARK.getOrElse(index) { ACCENTS_DARK[0] }.toInt()

        fun load(p: SharedPreferences): UiPrefs = UiPrefs(
            theme = p.getInt("theme", 0),
            accent = p.getInt("uiAccent", 0),
            amoled = p.getBoolean("uiAmoled", false),
            glass = p.getInt("uiGlass", 1),
            corners = p.getInt("uiCorners", 1),
            textScale = p.getInt("uiText", 1),
            compact = p.getBoolean("uiCompact", false),
            dial = p.getInt("uiDial", 0),
            motion = p.getBoolean("uiMotion", true),
            tabLabels = p.getBoolean("uiTabLabels", true),
            flags = p.getBoolean("uiFlags", true),
            dev = p.getBoolean("devMode", false),
        )
    }
}
