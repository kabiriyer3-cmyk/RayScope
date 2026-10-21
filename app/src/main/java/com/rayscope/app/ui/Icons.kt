/*
 * RayScope icon set: 24x24 rounded line icons drawn in the style of SF Symbols.
 * Many glyphs follow the geometry of Lucide (https://lucide.dev, ISC License).
 */
package com.rayscope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun circ(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0,1 $r,${r}v${h - 2 * r}a$r,$r 0 0,1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0,1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0,1 $r,${-r}z"

private fun lineIcon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { d ->
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

private fun solidIcon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { d ->
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}

const val SHIELD_PATH =
    "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
const val CHECK_SMALL_PATH = "M9 12l2 2l4-4"
const val CHECK_PATH = "M20 6L9 17l-5-5"

object RayIcons {
    val Home by lazy {
        lineIcon(
            "home",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        )
    }
    val Servers by lazy {
        lineIcon("servers", rrect(2f, 2f, 20f, 8f, 2.5f), rrect(2f, 14f, 20f, 8f, 2.5f), "M6 6h.01", "M6 18h.01", "M10 6h.01", "M10 18h.01")
    }
    val Globe by lazy {
        lineIcon("globe", circ(12f, 12f, 10f), "M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0-20", "M2 12h20")
    }
    val Settings by lazy {
        lineIcon("settings", "M20 7h-9", "M14 17H5", circ(17f, 17f, 3f), circ(7f, 7f, 3f))
    }
    val Search by lazy { lineIcon("search", circ(11f, 11f, 7.5f), "M21 21l-4.6-4.6") }
    val Refresh by lazy {
        lineIcon("refresh", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")
    }
    val Sort by lazy { lineIcon("sort", "M21 16l-4 4l-4-4", "M17 20V4", "M3 8l4-4l4 4", "M7 4v16") }
    val More by lazy { solidIcon("more", circ(12f, 12f, 1.3f), circ(19f, 12f, 1.3f), circ(5f, 12f, 1.3f)) }
    val Star by lazy {
        lineIcon("star", "M12 2.5l2.94 5.96l6.56.96l-4.75 4.63l1.12 6.54L12 17.5l-5.87 3.09l1.12-6.54L2.5 9.42l6.56-.96z")
    }
    val StarFilled by lazy {
        solidIcon("starFilled", "M12 2.5l2.94 5.96l6.56.96l-4.75 4.63l1.12 6.54L12 17.5l-5.87 3.09l1.12-6.54L2.5 9.42l6.56-.96z")
    }
    val Info by lazy { lineIcon("info", circ(12f, 12f, 10f), "M12 16v-4", "M12 8h.01") }
    val Back by lazy { lineIcon("back", "M15 18l-6-6l6-6") }
    val Chevron by lazy { lineIcon("chevron", "M9 18l6-6l-6-6") }
    val Plus by lazy { lineIcon("plus", "M5 12h14", "M12 5v14") }
    val Trash by lazy {
        lineIcon("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6")
    }
    val Close by lazy { lineIcon("close", "M18 6L6 18", "M6 6l12 12") }
    val Power by lazy { lineIcon("power", "M12 2v10", "M18.4 6.6a9 9 0 1 1-12.77.04") }
    val Bolt by lazy { lineIcon("bolt", "M13 2L4 14h8l-1 8l9-12h-8z") }
    val Copy by lazy { lineIcon("copy", rrect(8f, 8f, 14f, 14f, 2f), "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2") }
    val Share by lazy { lineIcon("share", "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4l-4 4", "M12 2v13") }
    val Shield by lazy { lineIcon("shield", SHIELD_PATH) }
    val ShieldCheck by lazy { lineIcon("shieldCheck", SHIELD_PATH, CHECK_SMALL_PATH) }
    val Gauge by lazy { lineIcon("gauge", "M12 14l4-4", "M3.34 19a10 10 0 1 1 17.32 0") }
    val Activity by lazy { lineIcon("activity", "M22 12h-4l-3 9L9 3l-3 9H2") }
    val ArrowDown by lazy { lineIcon("down", "M12 5v14", "M19 12l-7 7l-7-7") }
    val ArrowUp by lazy { lineIcon("up", "M12 19V5", "M5 12l7-7l7 7") }
    val Clock by lazy { lineIcon("clock", circ(12f, 12f, 10f), "M12 6v6l4 2") }
    val Link by lazy {
        lineIcon(
            "link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
        )
    }
    val Apps by lazy {
        lineIcon("apps", rrect(3f, 3f, 7f, 7f, 1.5f), rrect(14f, 3f, 7f, 7f, 1.5f), rrect(14f, 14f, 7f, 7f, 1.5f), rrect(3f, 14f, 7f, 7f, 1.5f))
    }
    val Logs by lazy {
        lineIcon("logs", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8")
    }
    val Check by lazy { lineIcon("check", CHECK_PATH) }
    val Clipboard by lazy {
        lineIcon("clipboard", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", rrect(8f, 2f, 8f, 4f, 1f))
    }
    val Package by lazy {
        lineIcon(
            "package",
            "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
            "M12 22V12",
            "M3.3 7l7.7 4.73a2 2 0 0 0 2 0L20.7 7"
        )
    }
    val Pin by lazy {
        lineIcon("pin", "M20 10c0 5-5.54 10.19-7.4 11.8a1 1 0 0 1-1.2 0C9.54 20.19 4 15 4 10a8 8 0 0 1 16 0", circ(12f, 10f, 3f))
    }
    val Alert by lazy {
        lineIcon("alert", "M21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01")
    }
    val Lock by lazy { lineIcon("lock", rrect(3f, 11f, 18f, 11f, 2f), "M7 11V7a5 5 0 0 1 10 0v4") }
    val Wifi by lazy {
        lineIcon("wifi", "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.86a10 10 0 0 1 14 0", "M8.5 16.43a5 5 0 0 1 7 0")
    }
    val Code by lazy { lineIcon("code", "M16 18l6-6l-6-6", "M8 6l-6 6l6 6") }
    val Sun by lazy {
        lineIcon(
            "sun", circ(12f, 12f, 4f), "M12 2v2", "M12 20v2", "M4.93 4.93l1.41 1.41", "M17.66 17.66l1.41 1.41",
            "M2 12h2", "M20 12h2", "M6.34 17.66l-1.41 1.41", "M19.07 4.93l-1.41 1.41"
        )
    }
    val Moon by lazy { lineIcon("moon", "M12 3a6 6 0 0 0 9 9a9 9 0 1 1-9-9z") }
    val Signal by lazy { lineIcon("signal", "M2 20h.01", "M7 20v-4", "M12 20v-8", "M17 20V8", "M22 4v16") }
    val Eye by lazy {
        lineIcon("eye", "M2.06 12.35a1 1 0 0 1 0-.7a10.75 10.75 0 0 1 19.88 0a1 1 0 0 1 0 .7a10.75 10.75 0 0 1-19.88 0", circ(12f, 12f, 3f))
    }
}

/**
 * Motion icon: strokes are "drawn" from 0 to [progress] (one path after another),
 * used for the shield-check that appears when the tunnel comes up.
 */
@Composable
fun DrawnIcon(
    paths: List<String>,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.9f,
    stagger: Float = 0.35f,
) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val n = parsed.size
        val span = (1f - (n - 1) * stagger).coerceAtLeast(0.1f)
        scale(s, s, pivot = Offset.Zero) {
            parsed.forEachIndexed { i, p ->
                val local = ((progress - i * stagger) / span).coerceIn(0f, 1f)
                if (local > 0f) {
                    measure.setPath(p, false)
                    val seg = Path()
                    measure.getSegment(0f, measure.length * local, seg, true)
                    drawPath(seg, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
