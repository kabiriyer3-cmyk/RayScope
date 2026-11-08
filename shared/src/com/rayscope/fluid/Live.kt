/*
 * Live glyphs: icons that move. Every icon is drawn from its own vector paths, so one stroke can turn, slide or
 * draw itself again: copy turns into a check after a click, refresh spins while it works, the bin lid lifts under
 * the pointer, a tab icon plays a short motion when its tab is picked. Curves ease in and settle, nothing
 * overshoots, and every motion ends exactly where it started. Works with any ImageVector made of plain paths
 * (the RayScope glyphs and the Android set); a glyph without a motion of its own simply stays still.
 * Shared by Android and Windows.
 */
package com.rayscope.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ public API

/**
 * An icon that can move. [hover] eases the hover pose in and out (or plays the motion once for glyphs without a
 * pose), every change of [play] plays the motion once, [busy] loops it and lets the last turn finish when it ends,
 * [done] turns a copy glyph into a check drawn in [doneTint], [appear] draws the strokes in on first show.
 * A new [icon] cross-fades from the old one (a star fills up from the bottom). [motion] = false: no movement.
 */
@Composable
fun LiveIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    hover: Boolean = false,
    play: Int = 0,
    busy: Boolean = false,
    done: Boolean = false,
    doneTint: Color = Color.Unspecified,
    appear: Boolean = false,
    motion: Boolean = true,
) {
    // a new icon cross-fades from the one shown before
    var shown by remember { mutableStateOf(icon) }
    var prev by remember { mutableStateOf<ImageVector?>(null) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(icon) {
        if (icon !== shown) {
            prev = shown
            shown = icon
            if (motion) {
                swap.snapTo(0f)
                swap.animateTo(1f, tween(440, easing = LinearEasing))
            }
            prev = null
        }
    }
    val g = remember(shown) { liveGlyph(shown) }
    val was = prev?.let { liveGlyph(it) }
    val spec = remember(g.name) { specOf(g.name) }

    val hv = animateFloatAsState(if (hover && motion) 1f else 0f, tween(220, easing = LinearEasing), label = "liveHover")
    val tv = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (play > 0 && motion) lvPlay(tv, spec.ms)
    }
    LaunchedEffect(hover) {
        if (hover && motion && spec.hoverPlays) lvPlay(tv, spec.ms)
    }
    val kv = animateFloatAsState(if (busy && motion) 1f else 0f, tween(260), label = "liveBusy")
    val bv = remember { Animatable(0f) }
    LaunchedEffect(busy, motion) {
        if (busy && motion) {
            while (true) {
                bv.animateTo(1f, tween(lvMs(1f - bv.value, spec.loop), easing = LinearEasing))
                bv.snapTo(0f)
            }
        } else if (bv.value > 0f) {
            // the work is over: finish the current turn and settle instead of jumping back
            bv.animateTo(1f, tween(lvMs(1f - bv.value, spec.loop * 3 / 2), easing = LinearOutSlowInEasing))
            bv.snapTo(0f)
        }
    }
    val dv = animateFloatAsState(
        if (done) 1f else 0f,
        if (motion) tween(if (done) 420 else 320, easing = LinearEasing) else snap(),
        label = "liveDone"
    )
    val av = remember { Animatable(if (appear && motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (av.value < 1f) av.animateTo(1f, tween(460, easing = LinearEasing))
    }
    val ink by animateColorAsState(if (tint == Color.Unspecified) Color.Black else tint, tween(180), label = "liveInk")
    val okInk = if (doneTint == Color.Unspecified) ink else doneTint

    val ch = remember { Ch() }
    val pose = remember { Pose() }
    val tmp = remember { Path() }
    val pm = remember { PathMeasure() }
    val desc = contentDescription
    var m = modifier.size(24.dp)
    if (desc != null) {
        m = m.semantics {
            this.contentDescription = desc
            role = Role.Image
        }
    }
    Canvas(m) {
        ch.h = hv.value
        ch.t = tv.value
        ch.d = dv.value
        ch.b = bv.value
        ch.k = kv.value
        ch.a = av.value
        val unit = min(size.width / g.vw, size.height / g.vh)
        withTransform({
            translate((size.width - g.vw * unit) / 2f, (size.height - g.vh * unit) / 2f)
            scale(unit, unit, Offset.Zero)
        }) {
            val s = swap.value
            if (was != null && s < 1f) {
                swapDraw(was, g, s, ink, ch, pose, tmp, pm)
            } else {
                glyphDraw(g, ink, ch, 1f, pose, tmp, pm)
            }
            marks(g, ch, ink, okInk, tmp, pm)
        }
    }
}

/** Click feedback of one icon: how many times it played, and whether the copy check is showing right now. */
@Stable
class IconFx {
    var plays by mutableIntStateOf(0)
    var done by mutableStateOf(false)
    internal var ticks by mutableIntStateOf(0)

    /** Call after the click: plays the motion; a copy glyph also shows the check for a moment. */
    fun fire(icon: ImageVector?) {
        plays++
        if (icon != null && confirms(icon)) ticks++
    }
}

/** Glyphs whose click is confirmed with a check (the action itself cannot fail: copying to the clipboard). */
fun confirms(icon: ImageVector): Boolean = icon.name == "copy"

@Composable
fun rememberIconFx(holdMs: Long = 1400L): IconFx {
    val fx = remember { IconFx() }
    LaunchedEffect(fx.ticks) {
        if (fx.ticks > 0) {
            fx.done = true
            delay(holdMs)
            fx.done = false
        }
    }
    return fx
}

/** A counter that goes up each time [flag] turns true after the first frame. Feed it to LiveIcon(play = ...). */
@Composable
fun playOnRise(flag: Boolean): Int {
    val plays = remember { mutableIntStateOf(0) }
    val last = remember { booleanArrayOf(flag) }
    LaunchedEffect(flag) {
        if (flag && !last[0]) plays.intValue++
        last[0] = flag
    }
    return plays.intValue
}

/** Rows that react to the pointer as a whole pass their hover down to the small icons inside them. */
val LocalIconHover = compositionLocalOf { false }

/** Chevron, folder and other row icons: still until their row is hovered. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    LiveIcon(icon, tint, modifier, hover = LocalIconHover.current)
}

// ------------------------------------------------------------------ geometry

/** One path of a glyph, on the glyph's own grid (24 units for every RayScope icon). */
class LivePart internal constructor(
    val path: Path,
    val bounds: Rect,
    /** fill alpha, 0 when the path has no fill */
    val fill: Float,
    /** stroke alpha, 0 when the path has no stroke */
    val line: Float,
    val width: Float,
    cap: StrokeCap,
    join: StrokeJoin,
) {
    val stroke = Stroke(width = width, cap = cap, join = join)
    val cx: Float get() = bounds.left + bounds.width / 2f
    val cy: Float get() = bounds.top + bounds.height / 2f
    val w: Float get() = bounds.width
    val h: Float get() = bounds.height
    val length: Float by lazy { lvLength(path) }
}

/** A glyph split into its paths, plus the landmarks the motions need. */
class LiveGlyph internal constructor(val name: String, val parts: List<LivePart>, val vw: Float, val vh: Float) {
    /** stroke width of the set, for marks drawn on top (the check that replaces copy) */
    val width: Float = parts.firstOrNull { it.line > 0f }?.width ?: 1.75f
    /** place of each path from left to right */
    val rankX: IntArray = lvRank(parts) { it.cx }
    /** hinge of the bin lid: left end of everything above y = 8.2 */
    val lid: Offset = lvLid(parts)
    /** a gear (one big outline) rather than slider knobs */
    val gear: Boolean = parts.any { it.line > 0f && it.w > 15f && it.h > 15f }
    /** slider knobs numbered by row from the top, -1 for everything else */
    val knobs: IntArray = lvKnobs(parts)
}

private val GLYPHS = IdentityHashMap<ImageVector, LiveGlyph>()

/** The paths of [icon], parsed once per icon. */
fun liveGlyph(icon: ImageVector): LiveGlyph = synchronized(GLYPHS) {
    GLYPHS.getOrPut(icon) { lvSplit(icon) }
}

private fun lvSplit(icon: ImageVector): LiveGlyph {
    val out = ArrayList<LivePart>()
    lvCollect(icon.root, out)
    return LiveGlyph(icon.name, out, icon.viewportWidth, icon.viewportHeight)
}

private fun lvCollect(group: VectorGroup, out: MutableList<LivePart>) {
    for (node in group) {
        if (node is VectorPath) {
            val path = PathParser().addPathNodes(node.pathData).toPath()
            path.fillType = node.pathFillType
            out.add(
                LivePart(
                    path = path,
                    bounds = path.getBounds(),
                    fill = if (node.fill != null) node.fillAlpha else 0f,
                    line = if (node.stroke != null && node.strokeLineWidth > 0f) node.strokeAlpha else 0f,
                    width = node.strokeLineWidth,
                    cap = node.strokeLineCap,
                    join = node.strokeLineJoin,
                )
            )
        } else if (node is VectorGroup) {
            lvCollect(node, out)
        }
    }
}

private fun lvRank(parts: List<LivePart>, key: (LivePart) -> Float): IntArray {
    val order = parts.indices.sortedBy { key(parts[it]) }
    val out = IntArray(parts.size)
    order.forEachIndexed { pos, i -> out[i] = pos }
    return out
}

private fun lvLid(parts: List<LivePart>): Offset {
    val lid = parts.filter { it.bounds.bottom <= 8.2f }
    if (lid.isEmpty()) return Offset(4f, 7f)
    return Offset(lid.minOf { it.bounds.left }, lid.maxOf { it.bounds.bottom })
}

private fun lvKnobs(parts: List<LivePart>): IntArray {
    val knob = parts.map { (abs(it.w - it.h) < 0.6f && it.w in 3f..7f) || (it.w < 0.6f && it.h in 2.5f..5.5f) }
    val rows = parts.indices.filter { knob[it] }.map { (parts[it].cy * 2f).roundToInt() }.distinct().sorted()
    return IntArray(parts.size) { i -> if (knob[i]) rows.indexOf((parts[i].cy * 2f).roundToInt()) else -1 }
}

private fun lvLength(path: Path): Float = PathMeasure().apply { setPath(path, false) }.length

private object LiveMarks {
    val check: Path by lazy { PathParser().parsePathString("M5 12.75l4.5 4.5L19 7.75").toPath() }
    val checkLength: Float by lazy { lvLength(check) }
    val tick: Path by lazy { PathParser().parsePathString("M9 12.25l2.1 2.1L15.25 10").toPath() }
    val tickLength: Float by lazy { lvLength(tick) }
}

// ------------------------------------------------------------------ motion

/** Pose of one path for one frame, on the glyph grid. */
private class Pose {
    var dx = 0f
    var dy = 0f
    var rot = 0f
    var sx = 1f
    var sy = 1f
    var px = 12f
    var py = 12f
    var alpha = 1f
    var fill = 1f
    var from = 0f
    var to = 1f
    var ghost = 0f

    fun reset(cx: Float, cy: Float) {
        dx = 0f
        dy = 0f
        rot = 0f
        sx = 1f
        sy = 1f
        px = cx
        py = cy
        alpha = 1f
        fill = 1f
        from = 0f
        to = 1f
        ghost = 0f
    }
}

/** Animation channels, all 0..1: hover, one-shot, done, loop phase, busy amount, appear. */
private class Ch {
    var h = 0f
    var t = 0f
    var d = 0f
    var b = 0f
    var k = 0f
    var a = 1f
}

/** Length of the one-shot, length of one busy loop, and whether hover plays the one-shot (no hover pose). */
private class Spec(val ms: Int, val loop: Int = 1000, val hoverPlays: Boolean = false)

private fun specOf(name: String): Spec = when (name) {
    "refresh" -> Spec(720, 900)
    "repeat" -> Spec(640, 1100)
    "plus", "close" -> Spec(440)
    "minus", "back", "chevron", "arrow", "play", "arrowDown", "arrowUp", "down", "up" -> Spec(380)
    "download", "upload", "share" -> Spec(460)
    "trash" -> Spec(520)
    "copy" -> Spec(400)
    "more" -> Spec(560, hoverPlays = true)
    "sort" -> Spec(460, hoverPlays = true)
    "power" -> Spec(420)
    "bolt" -> Spec(620, 1200, hoverPlays = true)
    "activity" -> Spec(620, 1300, hoverPlays = true)
    "radar" -> Spec(900, 1500, hoverPlays = true)
    "globe" -> Spec(760, 1500, hoverPlays = true)
    "home" -> Spec(560, hoverPlays = true)
    "servers" -> Spec(560, 1200, hoverPlays = true)
    "settings", "sliders" -> Spec(640, hoverPlays = true)
    "star", "starFilled" -> Spec(420)
    "shield" -> Spec(500)
    "shieldCheck" -> Spec(520, hoverPlays = true)
    "clipboard" -> Spec(560, hoverPlays = true)
    "link", "layers", "lock", "pin", "search", "moon", "code", "folder" -> Spec(480)
    "eye" -> Spec(300, hoverPlays = true)
    "key", "sun", "apps", "user", "filter" -> Spec(560, hoverPlays = true)
    "clock", "timer" -> Spec(800, 2000, hoverPlays = true)
    "gauge" -> Spec(700, 1400, hoverPlays = true)
    "wifi", "signal" -> Spec(700, 1200, hoverPlays = true)
    "check", "logs", "alert", "info", "ban", "target" -> Spec(480, hoverPlays = true)
    else -> Spec(400)
}

private const val LV_PI = 3.1415927f

private fun lvEase(x: Float): Float = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

/** 0 -> 1 -> 0 with no speed at either end. */
private fun lvBump(x: Float): Float = 0.5f - 0.5f * cos(2f * LV_PI * x.coerceIn(0f, 1f))

/** Local progress of [x] inside [from]..[to]. */
private fun lvWin(x: Float, from: Float, to: Float): Float = ((x - from) / (to - from)).coerceIn(0f, 1f)

private fun lvMs(fraction: Float, total: Int): Int = (fraction * total).roundToInt().coerceAtLeast(16)

private suspend fun lvPlay(t: Animatable<Float, AnimationVector1D>, ms: Int) {
    // a second click while running lets the current run finish instead of jumping back to the start
    t.animateTo(1f, tween(lvMs(1f - t.value, ms), easing = LinearEasing))
    t.snapTo(0f)
}

/** The stroke draws itself again over a faint copy of itself. */
private fun lvRedraw(o: Pose, t: Float) {
    if (t <= 0f || t >= 1f) return
    o.ghost = 1f - 0.75f * lvWin(t, 0f, 0.15f)
    o.to = lvEase(lvWin(t, 0.1f, 1f))
}

/** Arrow or line above the tray of download, upload and share. */
private fun lvTray(p: LivePart): Boolean = p.bounds.bottom > 19f && p.w > 10f

private fun lvKnob(g: LiveGlyph, i: Int, t: Float, o: Pose) {
    val r = g.knobs[i]
    if (r < 0) return
    val lag = 0.14f * r
    o.dx = (if (r % 2 == 0) 2.2f else -2.2f) * lvBump(lvWin(t, lag, lag + 0.7f))
}

/** Where path [i] of [g] is on this frame. Every pose is the rest pose at t = 0 and t = 1. */
private fun pose(g: LiveGlyph, i: Int, p: LivePart, c: Ch, o: Pose) {
    val h = lvEase(c.h)
    val t = c.t
    val b = p.bounds
    when (g.name) {
        "copy" -> {
            // hover: the sheets part a little; done: the front sheet slides onto the back one and both fold away
            val front = b.right > 18f
            val e = lvEase(lvWin(c.d, 0f, 0.6f))
            val apart = 0.7f * h * (1f - e)
            val slide = if (front) apart - 2.4f * e else -apart
            o.dx = slide
            o.dy = slide
            o.sx = 1f - 0.4f * e
            o.sy = o.sx
            o.alpha = 1f - e
        }
        "check" -> lvRedraw(o, t)
        "refresh" -> o.rot = 30f * h + 360f * lvEase(t) + 360f * c.b
        "repeat" -> o.rot = 180f * lvEase(t) + 360f * c.b
        "plus", "close" -> o.rot = 90f * h + 180f * lvEase(t)
        "minus" -> o.dy = 1.4f * h + 2f * lvBump(t)
        "back" -> o.dx = -(1.6f * h + 3f * lvBump(t))
        "chevron" -> o.dx = 1.6f * h + 3f * lvBump(t)
        "arrow", "play" -> o.dx = 2f * h + 3f * lvBump(t)
        "arrowDown", "down" -> o.dy = 1.6f * h + 3f * lvBump(t)
        "arrowUp", "up" -> o.dy = -(1.6f * h + 3f * lvBump(t))
        "download" -> {
            if (!lvTray(p)) o.dy = 1.4f * h + 2.6f * lvBump(t)
        }
        "upload", "share" -> {
            if (!lvTray(p)) o.dy = -(1.4f * h + 2.6f * lvBump(t))
        }
        "trash" -> {
            if (b.bottom <= 8.2f) {
                val open = 14f * h + 20f * lvBump(t)
                o.rot = -open
                o.px = g.lid.x
                o.py = g.lid.y
                o.dy = -0.05f * open
            }
        }
        "more" -> {
            val r = g.rankX[i].toFloat()
            val e = lvBump(lvWin(t, 0.15f * r, 0.15f * r + 0.7f))
            o.sx = 1f + 0.45f * e
            o.sy = o.sx
            o.px = p.cx
            o.py = p.cy
        }
        "sort" -> {
            val e = 2.2f * lvBump(t)
            o.dy = if (p.cx < 12f) -e else e
        }
        "power" -> {
            val e = lvBump(t)
            o.sx = 1f - 0.06f * e
            o.sy = o.sx
            if (p.w < 0.6f && p.h > 5f) o.dy = 2.2f * e
        }
        "bolt" -> {
            lvRedraw(o, t)
            o.fill = 1f + 2.6f * lvBump(t) + 2.4f * c.k * lvBump(c.b)
        }
        "activity" -> {
            lvRedraw(o, t)
            if (c.k > 0f) {
                // a short bright run travels along the dimmed line
                val head = 1.4f * c.b
                o.ghost = 1f - 0.7f * c.k
                o.from = (head - 0.4f).coerceIn(0f, 1f)
                o.to = head.coerceIn(0f, 1f)
            }
        }
        "radar" -> {
            if (abs(b.left - 12f) < 0.4f && abs(b.bottom - 12f) < 0.4f) o.rot = 360f * lvEase(t) + 360f * c.b
        }
        "globe" -> {
            if (p.w > 5f && p.w < 12f && p.h > 15f) {
                val s = cos(LV_PI * (lvEase(t) + c.b))
                o.sx = if (abs(s) < 0.12f) (if (s < 0f) -0.12f else 0.12f) else s
            }
        }
        "home" -> {
            if (p.w < 7.5f && b.bottom > 20.4f) lvRedraw(o, t) else o.fill = 1f + 1.6f * lvBump(t)
        }
        "servers" -> {
            val top = p.cy < 12f
            val e = 1.6f * lvBump(t)
            o.dx = if (top) -e else e
            if (p.w < 1f && p.h < 1f) {
                val ph = if (top) 0f else 0.5f
                o.alpha = 1f - 0.85f * max(lvBump(lvWin(t, 0.2f, 0.8f)), c.k * lvBump(lvWin(c.b, ph, ph + 0.5f)))
            }
        }
        "settings" -> {
            if (g.gear) o.rot = 90f * lvEase(t) else lvKnob(g, i, t, o)
        }
        "sliders" -> lvKnob(g, i, t, o)
        "star", "starFilled" -> {
            o.fill = 1f + 2f * lvBump(t)
            o.sx = 1f - 0.16f * lvBump(t)
            o.sy = o.sx
        }
        "shield" -> o.fill = 1f + 1.2f * h
        "shieldCheck" -> {
            if (p.line > 0f && p.w < 7.5f && p.h < 5.5f) lvRedraw(o, t) else o.fill = 1f + 1.2f * h
        }
        "clipboard" -> {
            if (p.h < 0.6f && p.w > 2.5f) {
                val lag = if (p.cy < 14f) 0.1f else 0.3f
                lvRedraw(o, lvWin(t, lag, lag + 0.7f))
            } else if (b.top < 4f && p.w < 9f) {
                o.dy = 0.9f * lvBump(t)
            }
        }
        "logs" -> {
            if (p.h < 0.6f && p.w >= 1.9f) {
                val lag = if (p.cy < 14f) 0f else 0.25f
                lvRedraw(o, lvWin(t, lag, lag + 0.75f))
            }
        }
        "link" -> {
            val e = 1.1f * lvBump(t) + 0.5f * h
            if (p.cx > 12f) {
                o.dx = e
                o.dy = -e
            } else {
                o.dx = -e
                o.dy = e
            }
        }
        "code" -> {
            if (p.cx < 10f) {
                o.dx = -(1.6f * lvBump(t) + 0.6f * h)
            } else if (p.cx > 14f) {
                o.dx = 1.6f * lvBump(t) + 0.6f * h
            } else {
                o.rot = 18f * lvBump(t)
            }
        }
        "eye" -> o.sy = 1f - 0.88f * lvBump(t)
        "lock" -> {
            if (b.bottom < 11.5f && b.top < 6f) o.dy = -(1.8f * lvBump(t) + 0.6f * h)
        }
        "key" -> o.rot = -28f * lvBump(t)
        "clock" -> {
            if (abs(b.left - 12f) < 0.3f && p.w < 5f && p.h < 9f) o.rot = 360f * lvEase(t) + 360f * c.b
        }
        "timer" -> {
            if (abs(b.left - 12f) < 0.3f && p.w < 4f && p.h < 4f) {
                o.rot = 360f * lvEase(t) + 360f * c.b
                o.px = b.left
                o.py = b.bottom
            }
        }
        "gauge" -> {
            if (p.line > 0f && abs(b.left - 12f) < 0.3f && p.w < 5f && p.h < 5f) {
                o.rot = -100f * max(lvBump(t), c.k * lvBump(c.b))
                o.px = b.left
                o.py = b.bottom
            }
        }
        "wifi" -> {
            if (p.w > 1f) {
                val lag = if (p.w < 8f) 0f else if (p.w < 15f) 0.2f else 0.4f
                o.alpha = 1f - 0.8f * lvBump(lvWin(t, lag, lag + 0.5f))
            }
        }
        "signal" -> {
            val lag = 0.13f * g.rankX[i]
            o.alpha = 1f - 0.8f * lvBump(lvWin(t, lag, lag + 0.45f))
        }
        "pin" -> o.dy = -(2.2f * lvBump(t) + 0.8f * h)
        "layers" -> {
            val e = 1.3f * lvBump(t) + 0.5f * h
            if (p.cy < 10f) o.dy = -e else if (p.cy > 16f) o.dy = e
        }
        "sun" -> {
            if (p.w < 3f && p.h < 3f) o.rot = 45f * lvEase(t)
        }
        "moon" -> o.rot = -(22f * lvBump(t) + 8f * h)
        "search" -> {
            o.rot = -16f * lvBump(t)
            o.px = 10.75f
            o.py = 10.75f
            o.dx = -0.7f * h
            o.dy = -0.7f * h
        }
        "alert", "info" -> {
            if (p.w < 0.6f && p.h > 2f) lvRedraw(o, t)
        }
        "ban" -> {
            if (p.w > 10f && p.w < 15f) lvRedraw(o, t)
        }
        "target" -> {
            if (p.w < 13f) {
                o.sx = 1f - 0.22f * lvBump(t)
                o.sy = o.sx
            }
        }
        "apps" -> {
            val lag = ((if (p.cx > 12f) 1 else 0) + (if (p.cy > 12f) 1 else 0)) * 0.15f
            o.sx = 1f - 0.2f * lvBump(lvWin(t, lag, lag + 0.6f))
            o.sy = o.sx
            o.px = p.cx
            o.py = p.cy
        }
        "user" -> {
            if (p.cy < 10f) o.dy = -1.2f * lvBump(t)
        }
        "filter" -> o.dy = 1.2f * lvBump(t)
        "folder" -> {
            // the folder lifts off its bottom-left corner
            o.rot = -(6f * h + 8f * lvBump(t))
            o.px = b.left
            o.py = b.bottom
            o.dy = -h
        }
    }
}

// ------------------------------------------------------------------ drawing

private fun DrawScope.glyphDraw(g: LiveGlyph, ink: Color, c: Ch, alpha: Float, o: Pose, tmp: Path, pm: PathMeasure) {
    val n = g.parts.size
    for (i in 0 until n) {
        val p = g.parts[i]
        o.reset(g.vw / 2f, g.vh / 2f)
        pose(g, i, p, c, o)
        if (c.a < 1f) {
            // first show: strokes draw themselves one after another, fills fade in
            val lag = if (n > 1) 0.35f * i / (n - 1) else 0f
            val e = lvEase(lvWin(c.a, lag, lag + 0.65f))
            if (p.line > 0f) o.to = min(o.to, e) else o.alpha *= e
        }
        partDraw(p, ink, o, alpha, tmp, pm)
    }
}

private fun DrawScope.partDraw(p: LivePart, ink: Color, o: Pose, alpha: Float, tmp: Path, pm: PathMeasure) {
    val a = o.alpha * alpha
    if (a <= 0.004f) return
    withTransform({
        translate(o.dx, o.dy)
        if (o.rot != 0f) rotate(o.rot, Offset(o.px, o.py))
        if (o.sx != 1f || o.sy != 1f) scale(o.sx, o.sy, Offset(o.px, o.py))
    }) {
        if (p.fill > 0f) drawPath(p.path, ink, alpha = (p.fill * o.fill).coerceAtMost(1f) * a)
        if (p.line > 0f) {
            val la = p.line * a
            if (o.from <= 0f && o.to >= 1f) {
                drawPath(p.path, ink, alpha = la, style = p.stroke)
            } else {
                if (o.ghost > 0f) drawPath(p.path, ink, alpha = la * o.ghost, style = p.stroke)
                val len = p.length
                if (o.to > o.from && len > 0f) {
                    pm.setPath(p.path, false)
                    tmp.reset()
                    pm.getSegment(o.from * len, o.to * len, tmp, true)
                    drawPath(tmp, ink, alpha = la, style = p.stroke)
                }
            }
        }
    }
}

/** Old icon out, new icon in. Star and filled star pour: the fill rises from the bottom or drains away. */
private fun DrawScope.swapDraw(was: LiveGlyph, now: LiveGlyph, s: Float, ink: Color, c: Ch, o: Pose, tmp: Path, pm: PathMeasure) {
    val e = lvEase(s)
    val pour = (was.name == "star" && now.name == "starFilled") || (was.name == "starFilled" && now.name == "star")
    if (pour) {
        val filling = now.name == "starFilled"
        val outline = if (filling) was else now
        val solid = if (filling) now else was
        val level = if (filling) e else 1f - e
        glyphDraw(outline, ink, c, 1f - lvWin(level, 0.7f, 1f), o, tmp, pm)
        clipRect(-4f, 23f - 22f * level, now.vw + 4f, now.vh + 4f) {
            glyphDraw(solid, ink, c, 1f, o, tmp, pm)
        }
    } else {
        val mid = Offset(now.vw / 2f, now.vh / 2f)
        withTransform({ scale(1f - 0.3f * e, 1f - 0.3f * e, mid) }) {
            glyphDraw(was, ink, c, 1f - e, o, tmp, pm)
        }
        withTransform({ scale(0.7f + 0.3f * e, 0.7f + 0.3f * e, mid) }) {
            glyphDraw(now, ink, c, e, o, tmp, pm)
        }
    }
}

/** Strokes some glyphs add on top: the check that replaces copy, the tick that appears in the shield. */
private fun DrawScope.marks(g: LiveGlyph, c: Ch, ink: Color, okInk: Color, tmp: Path, pm: PathMeasure) {
    if (g.name == "copy" && c.d > 0f) {
        markDraw(LiveMarks.check, LiveMarks.checkLength, okInk, lvEase(lvWin(c.d, 0.3f, 1f)), g.width * 1.1f, tmp, pm)
    } else if (g.name == "shield" && c.h > 0f) {
        markDraw(LiveMarks.tick, LiveMarks.tickLength, ink, lvEase(lvWin(c.h, 0.1f, 1f)), g.width, tmp, pm)
    }
}

private fun DrawScope.markDraw(path: Path, length: Float, ink: Color, to: Float, width: Float, tmp: Path, pm: PathMeasure) {
    if (to <= 0f) return
    val st = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (to >= 1f) {
        drawPath(path, ink, style = st)
    } else {
        pm.setPath(path, false)
        tmp.reset()
        pm.getSegment(0f, length * to, tmp, true)
        drawPath(tmp, ink, style = st)
    }
}
