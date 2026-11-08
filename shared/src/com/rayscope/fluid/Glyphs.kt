/*
 * RayScope glyphs. Drawn for this app on one 24 px grid: 1.75 px round strokes plus a 16 % "soft" fill under
 * the main shape, so every icon reads as one family from 14 to 24 px. Shared by Android and Windows.
 */
package com.rayscope.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val INK = SolidColor(Color.Black)

private fun nodes(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()

private fun parts(s: String): List<String> = if (s.isBlank()) emptyList() else s.split('|')

/**
 * [line]: stroke paths joined by "|". [soft]: items are an index into [line] or a path of their own, filled at
 * 16 %. [solid]: paths filled at full strength (dots, filled star).
 */
fun glyph(name: String, line: String, soft: String = "", solid: String = "", width: Float = 1.75f): ImageVector {
    val lines = parts(line)
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    parts(soft).forEach { s ->
        val d = s.toIntOrNull()?.let { lines.getOrNull(it) } ?: s
        b.addPath(nodes(d), fill = INK, fillAlpha = 0.16f)
    }
    lines.forEach { d ->
        b.addPath(nodes(d), stroke = INK, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }
    parts(solid).forEach { d -> b.addPath(nodes(d), fill = INK) }
    return b.build()
}

object Glyphs {
    val Home by lazy { glyph("home", "M4.5 10.4l6.3-5.4a1.8 1.8 0 0 1 2.4 0l6.3 5.4v8.1a2.5 2.5 0 0 1-2.5 2.5h-10a2.5 2.5 0 0 1-2.5-2.5z|M9.75 21v-4.6a2.25 2.25 0 0 1 4.5 0V21", "0") }
    val Servers by lazy { glyph("servers", "M6 4h12a2.5 2.5 0 0 1 2.5 2.5v2a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-2a2.5 2.5 0 0 1 2.5 -2.5z|M6 13h12a2.5 2.5 0 0 1 2.5 2.5v2a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-2a2.5 2.5 0 0 1 2.5 -2.5z|M7.25 7.5h.01|M7.25 16.5h.01|M13.5 7.5h3.25|M13.5 16.5h3.25", "0|1") }
    val Globe by lazy { glyph("globe", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M12 3c-2.5 2.5-3.75 5.5-3.75 9s1.25 6.5 3.75 9c2.5-2.5 3.75-5.5 3.75-9S14.5 5.5 12 3z|M3.2 12h17.6", "0") }
    val Settings by lazy { glyph("settings", "M10.34 5.2L10.49 2.92A9.2 9.2 0 0 1 13.51 2.92L13.66 5.2A7 7 0 0 1 15.64 6.02L17.35 4.51A9.2 9.2 0 0 1 19.49 6.65L17.98 8.36A7 7 0 0 1 18.8 10.34L21.08 10.49A9.2 9.2 0 0 1 21.08 13.51L18.8 13.66A7 7 0 0 1 17.98 15.64L19.49 17.35A9.2 9.2 0 0 1 17.35 19.49L15.64 17.98A7 7 0 0 1 13.66 18.8L13.51 21.08A9.2 9.2 0 0 1 10.49 21.08L10.34 18.8A7 7 0 0 1 8.36 17.98L6.65 19.49A9.2 9.2 0 0 1 4.51 17.35L6.02 15.64A7 7 0 0 1 5.2 13.66L2.92 13.51A9.2 9.2 0 0 1 2.92 10.49L5.2 10.34A7 7 0 0 1 6.02 8.36L4.51 6.65A9.2 9.2 0 0 1 6.65 4.51L8.36 6.02A7 7 0 0 1 10.34 5.2z|M9.25 12a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0 -5.5 0z", "0") }
    val Sliders by lazy { glyph("sliders", "M4 7h6.5|M15.5 7H20|M4 17h2.5|M11.5 17H20|M10.5 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M6.5 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z", "4|5") }
    val Radar by lazy { glyph("radar", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z|M12 12l5.2-5.2", "M12 12V3a9 9 0 0 1 6.36 2.64z", "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0z") }
    val Search by lazy { glyph("search", "M4 10.75a6.75 6.75 0 1 0 13.5 0a6.75 6.75 0 1 0 -13.5 0z|M15.75 15.75l4.5 4.5", "0") }
    val Refresh by lazy { glyph("refresh", "M20 12a8 8 0 1 1-2.34-5.66L20 8.5|M20 4v4.5h-4.5") }
    val Repeat by lazy { glyph("repeat", "M16.5 3.5L20 7l-3.5 3.5|M4 11.5v-.5A4 4 0 0 1 8 7h12|M7.5 20.5L4 17l3.5-3.5|M20 12.5v.5a4 4 0 0 1-4 4H4") }
    val Sort by lazy { glyph("sort", "M7.5 4.5v15|M4 8l3.5-3.5L11 8|M16.5 19.5v-15|M13 16l3.5 3.5L20 16") }
    val More by lazy { glyph("more", "", "", "M3.9 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z|M10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z|M16.9 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z") }
    val Star by lazy { glyph("star", "M12 3.6l2.47 5.02c.1.2.3.34.52.38l5.54.8c.55.08.77.76.37 1.15l-4 3.9c-.17.16-.24.39-.2.62l.94 5.51c.1.55-.48.97-.97.71L12.2 19.1a.68.68 0 0 0-.4 0l-4.97 2.61c-.49.26-1.06-.16-.97-.71l.95-5.51a.7.7 0 0 0-.2-.62l-4-3.9c-.4-.39-.18-1.07.37-1.15l5.53-.8a.7.7 0 0 0 .53-.38z", "0") }
    val StarFilled by lazy { glyph("starFilled", "", "", "M12 3.6l2.47 5.02c.1.2.3.34.52.38l5.54.8c.55.08.77.76.37 1.15l-4 3.9c-.17.16-.24.39-.2.62l.94 5.51c.1.55-.48.97-.97.71L12.2 19.1a.68.68 0 0 0-.4 0l-4.97 2.61c-.49.26-1.06-.16-.97-.71l.95-5.51a.7.7 0 0 0-.2-.62l-4-3.9c-.4-.39-.18-1.07.37-1.15l5.53-.8a.7.7 0 0 0 .53-.38z") }
    val Info by lazy { glyph("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M12 11v5.25|M12 7.75h.01", "0") }
    val Back by lazy { glyph("back", "M14.5 5.5L8 12l6.5 6.5") }
    val Chevron by lazy { glyph("chevron", "M9.5 5.5L16 12l-6.5 6.5") }
    val Plus by lazy { glyph("plus", "M12 5v14|M5 12h14") }
    val Trash by lazy { glyph("trash", "M4 6.75h16|M9.25 6.75V5.25c0-.83.67-1.5 1.5-1.5h2.5c.83 0 1.5.67 1.5 1.5v1.5|M6 6.75l.85 12.4A2 2 0 0 0 8.84 21h6.32a2 2 0 0 0 1.99-1.85L18 6.75|M10 11v5.5|M14 11v5.5", "M6 6.75l.85 12.4A2 2 0 0 0 8.84 21h6.32a2 2 0 0 0 1.99-1.85L18 6.75z") }
    val Close by lazy { glyph("close", "M6.5 6.5l11 11|M17.5 6.5l-11 11") }
    val Power by lazy { glyph("power", "M12 3.25v7.75|M17.3 6.7a7.75 7.75 0 1 1-10.6 0") }
    val Play by lazy { glyph("play", "M7.5 5.2v13.6c0 .78.85 1.26 1.52.86l11-6.8a1 1 0 0 0 0-1.72l-11-6.8a1 1 0 0 0-1.52.86z", "0") }
    val Bolt by lazy { glyph("bolt", "M13.2 3.1L5.6 12.7a.8.8 0 0 0 .62 1.3H11l-.9 6.9c-.1.5.55.78.87.38l7.5-9.6a.8.8 0 0 0-.62-1.3H13l.9-6.9c.1-.5-.55-.78-.87-.38z", "0") }
    val Copy by lazy { glyph("copy", "M11.25 8.5h6.5a2.75 2.75 0 0 1 2.75 2.75v6.5a2.75 2.75 0 0 1 -2.75 2.75h-6.5a2.75 2.75 0 0 1 -2.75 -2.75v-6.5a2.75 2.75 0 0 1 2.75 -2.75z|M15.5 8.5V6.25A2.75 2.75 0 0 0 12.75 3.5h-6.5A2.75 2.75 0 0 0 3.5 6.25v6.5a2.75 2.75 0 0 0 2.75 2.75H8.5", "0") }
    val Share by lazy { glyph("share", "M12 14.5V3.75|M8 7.25L12 3.5l4 3.75|M5 12v5.5A3.5 3.5 0 0 0 8.5 21h7a3.5 3.5 0 0 0 3.5-3.5V12") }
    val Shield by lazy { glyph("shield", "M12 3.2l6.8 2.6c.4.15.7.55.7 1v4.7c0 4.3-2.8 7.9-7.1 9.3a1.3 1.3 0 0 1-.8 0c-4.3-1.4-7.1-5-7.1-9.3V6.8c0-.45.3-.85.7-1z", "0") }
    val ShieldCheck by lazy { glyph("shieldCheck", "M12 3.2l6.8 2.6c.4.15.7.55.7 1v4.7c0 4.3-2.8 7.9-7.1 9.3a1.3 1.3 0 0 1-.8 0c-4.3-1.4-7.1-5-7.1-9.3V6.8c0-.45.3-.85.7-1z|M9 12.25l2.1 2.1L15.25 10", "0") }
    val Gauge by lazy { glyph("gauge", "M5.2 18.4a8.5 8.5 0 1 1 13.6 0|M12 14.25l3.5-3.75", "", "M10.4 14.25a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z") }
    val Activity by lazy { glyph("activity", "M3 12.5h3.6l2.4-6 4.5 11 2.4-5h5.1") }
    val ArrowDown by lazy { glyph("arrowDown", "M12 4.5v15|M18 13.5l-6 6-6-6") }
    val ArrowUp by lazy { glyph("arrowUp", "M12 19.5v-15|M6 10.5l6-6 6 6") }
    val Clock by lazy { glyph("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M12 7.25V12l3.25 2", "0") }
    val Timer by lazy { glyph("timer", "M9.75 2.75h4.5|M4.25 13.5a7.75 7.75 0 1 0 15.5 0a7.75 7.75 0 1 0 -15.5 0z|M12 13.5l2.75-2.75|M18.25 6.75l1.25-1.25", "1") }
    val Link by lazy { glyph("link", "M10.2 13.8a4.1 4.1 0 0 0 5.8 0l3-3a4.1 4.1 0 1 0-5.8-5.8l-.95.95|M13.8 10.2a4.1 4.1 0 0 0-5.8 0l-3 3a4.1 4.1 0 1 0 5.8 5.8l.95-.95") }
    val Apps by lazy { glyph("apps", "M5.75 3.75h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z|M15.25 3.75h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z|M5.75 13.25h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z|M15.25 13.25h3a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-3a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z", "1") }
    val Logs by lazy { glyph("logs", "M13.5 3.25H7.25A2.5 2.5 0 0 0 4.75 5.75v12.5a2.5 2.5 0 0 0 2.5 2.5h9.5a2.5 2.5 0 0 0 2.5-2.5V9z|M13.5 3.25V7a2 2 0 0 0 2 2h3.75|M8.5 12.75h7|M8.5 16.5h4.5", "0") }
    val Check by lazy { glyph("check", "M5 12.75l4.5 4.5L19 7.75") }
    val Clipboard by lazy { glyph("clipboard", "M15.25 4.75h1.25a2.5 2.5 0 0 1 2.5 2.5v11.25a2.5 2.5 0 0 1-2.5 2.5h-9a2.5 2.5 0 0 1-2.5-2.5V7.25a2.5 2.5 0 0 1 2.5-2.5h1.25|M10.15 3h3.7a1.4 1.4 0 0 1 1.4 1.4v0.95a1.4 1.4 0 0 1 -1.4 1.4h-3.7a1.4 1.4 0 0 1 -1.4 -1.4v-0.95a1.4 1.4 0 0 1 1.4 -1.4z|M9 12h6|M9 15.75h4", "M15.25 4.75h1.25a2.5 2.5 0 0 1 2.5 2.5v11.25a2.5 2.5 0 0 1-2.5 2.5h-9a2.5 2.5 0 0 1-2.5-2.5V7.25a2.5 2.5 0 0 1 2.5-2.5h1.25z") }
    val Package by lazy { glyph("package", "M11.1 3.3a1.9 1.9 0 0 1 1.8 0l6.1 3.4c.6.33.97.96.97 1.65v7.3c0 .69-.37 1.32-.97 1.65l-6.1 3.4a1.9 1.9 0 0 1-1.8 0L5 17.3c-.6-.33-.97-.96-.97-1.65v-7.3c0-.69.37-1.32.97-1.65z|M4.4 7.6l7.6 4.3 7.6-4.3|M12 11.9v9.1", "M11.1 3.3a1.9 1.9 0 0 1 1.8 0l6.7 3.75-7.6 4.3-7.6-4.3z") }
    val Pin by lazy { glyph("pin", "M12 21.25c-.3 0-6.75-5.2-6.75-11.1a6.75 6.75 0 0 1 13.5 0c0 5.9-6.45 11.1-6.75 11.1z|M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z", "0") }
    val Alert by lazy { glyph("alert", "M10.3 4.4a1.95 1.95 0 0 1 3.4 0l7.05 12.6a1.95 1.95 0 0 1-1.7 2.9H4.95a1.95 1.95 0 0 1-1.7-2.9z|M12 9.5v4|M12 16.4h.01", "0") }
    val Lock by lazy { glyph("lock", "M7.5 10.25h9a3 3 0 0 1 3 3v4.75a3 3 0 0 1 -3 3h-9a3 3 0 0 1 -3 -3v-4.75a3 3 0 0 1 3 -3z|M8 10.25V7.5a4 4 0 0 1 8 0v2.75|M12 14.25v2.75", "0") }
    val Key by lazy { glyph("key", "M3.75 15.5a4.25 4.25 0 1 0 8.5 0a4.25 4.25 0 1 0 -8.5 0z|M11.1 12.4L19.75 3.75|M17 6.5l2.25 2.25|M14.4 9.1l2 2", "0") }
    val Wifi by lazy { glyph("wifi", "M2.75 9.2a13.2 13.2 0 0 1 18.5 0|M5.9 12.6a8.7 8.7 0 0 1 12.2 0|M9.05 16a4.2 4.2 0 0 1 5.9 0|M12 19.4h.01") }
    val Code by lazy { glyph("code", "M8.5 7L3.75 12l4.75 5|M15.5 7l4.75 5-4.75 5|M13.25 5l-2.5 14") }
    val Sun by lazy { glyph("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z|M12 2.75v1.75|M12 19.5v1.75|M2.75 12H4.5|M19.5 12h1.75|M5.45 5.45l1.25 1.25|M17.3 17.3l1.25 1.25|M5.45 18.55l1.25-1.25|M17.3 6.7l1.25-1.25", "0") }
    val Moon by lazy { glyph("moon", "M20.1 14.4A8.4 8.4 0 1 1 9.6 3.9a6.8 6.8 0 0 0 10.5 10.5z", "0") }
    val Signal by lazy { glyph("signal", "M4.5 20v-3|M9.5 20v-7|M14.5 20V9|M19.5 20V4.5") }
    val Eye by lazy { glyph("eye", "M2.75 12S6.1 5.75 12 5.75 21.25 12 21.25 12 17.9 18.25 12 18.25 2.75 12 2.75 12z|M9.25 12a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0 -5.5 0z", "1") }
    val Plug by lazy { glyph("plug", "M9 3.25v4.5|M15 3.25v4.5|M6.25 7.75h11.5v3a5.75 5.75 0 0 1-11.5 0z|M12 16.5v4.75", "2") }
    val Route by lazy { glyph("route", "M3.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M15.5 6a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M8.5 18h7.75a3.5 3.5 0 0 0 0-7H7.75a3.5 3.5 0 0 1 0-7h7.75", "0|1") }
    val Fork by lazy { glyph("fork", "M3.5 5.75a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M15.5 5.75a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M9.5 18.25a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z|M6 8.25v1.25A2.5 2.5 0 0 0 8.5 12h7A2.5 2.5 0 0 0 18 9.5V8.25|M12 12v3.75", "2") }
    val Layers by lazy { glyph("layers", "M12 3.5l8.5 4.4-8.5 4.4-8.5-4.4z|M3.5 12.1l8.5 4.4 8.5-4.4|M3.5 16.1l8.5 4.4 8.5-4.4", "0") }
    val Scissors by lazy { glyph("scissors", "M3.5 6.5a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0 -5.5 0z|M3.5 17.5a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0 -5.5 0z|M8.45 8.1L19.75 18|M8.45 15.9L19.75 6", "0|1") }
    val Hash by lazy { glyph("hash", "M4.75 9h15|M4.25 15h15|M10.5 4l-1.5 16|M16 4l-1.5 16") }
    val Network by lazy { glyph("network", "M11 3h2a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1 -1.5 1.5h-2a1.5 1.5 0 0 1 -1.5 -1.5v-2a1.5 1.5 0 0 1 1.5 -1.5z|M4.5 16h2a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1 -1.5 1.5h-2a1.5 1.5 0 0 1 -1.5 -1.5v-2a1.5 1.5 0 0 1 1.5 -1.5z|M17.5 16h2a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1 -1.5 1.5h-2a1.5 1.5 0 0 1 -1.5 -1.5v-2a1.5 1.5 0 0 1 1.5 -1.5z|M12 8v4|M5.5 16v-1.75a2.25 2.25 0 0 1 2.25-2.25h8.5a2.25 2.25 0 0 1 2.25 2.25V16", "0") }
    val Ban by lazy { glyph("ban", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M5.65 5.65l12.7 12.7", "0") }
    val Filter by lazy { glyph("filter", "M4 5.6c0-.88.72-1.6 1.6-1.6h12.8c.88 0 1.6.72 1.6 1.6v.73c0 .5-.2.98-.56 1.33L14 12.9v5.7c0 .4-.22.76-.58.94l-2.25 1.12A.8.8 0 0 1 10 19.94V12.9L4.56 7.66A1.86 1.86 0 0 1 4 6.33z", "0") }
    val Download by lazy { glyph("download", "M12 3.5v11|M7.5 10.25L12 14.75l4.5-4.5|M4 16.5v1.75A2.75 2.75 0 0 0 6.75 21h10.5A2.75 2.75 0 0 0 20 18.25V16.5") }
    val Upload by lazy { glyph("upload", "M12 14.75v-11|M7.5 8L12 3.5 16.5 8|M4 16.5v1.75A2.75 2.75 0 0 0 6.75 21h10.5A2.75 2.75 0 0 0 20 18.25V16.5") }
    val Vibrate by lazy { glyph("vibrate", "M10.25 3.5h3.5a2.25 2.25 0 0 1 2.25 2.25v12.5a2.25 2.25 0 0 1 -2.25 2.25h-3.5a2.25 2.25 0 0 1 -2.25 -2.25v-12.5a2.25 2.25 0 0 1 2.25 -2.25z|M4 8.5v7|M20 8.5v7|M11 17h2", "0") }
    val User by lazy { glyph("user", "M8.25 8a3.75 3.75 0 1 0 7.5 0a3.75 3.75 0 1 0 -7.5 0z|M4.75 20.25a7.25 7.25 0 0 1 14.5 0", "0") }
    val Target by lazy { glyph("target", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z|M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0z", "1", "M10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z") }
    val Folder by lazy { glyph("folder", "M3.5 7.25A2.75 2.75 0 0 1 6.25 4.5h2.9c.53 0 1.04.21 1.41.59L12 6.5h5.75a2.75 2.75 0 0 1 2.75 2.75v8a2.75 2.75 0 0 1-2.75 2.75H6.25a2.75 2.75 0 0 1-2.75-2.75z", "0") }
    val Arrow by lazy { glyph("arrow", "M4.5 12h15|M13.5 6l6 6-6 6") }
}
