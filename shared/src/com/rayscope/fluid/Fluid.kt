/*
 * Fluid: motion and shapes behind RayScope's gooey interface (connect button, switches, sliders, moving
 * highlights, aurora, lettermark). Shared by Android and Windows: both modules add shared/src to their sources.
 */
package com.rayscope.fluid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = 6.2831855f
private const val PI_F = 3.1415927f
private const val HALF_PI = 1.5707964f

// ------------------------------------------------------------------ springs

/** Spring presets. Everything that moves uses one of these, so the whole app feels like one material. */
object Fluid {
    /** Press squash and small bounces: underdamped and quick. */
    fun <T> jelly(): SpringSpec<T> = spring(dampingRatio = 0.45f, stiffness = 520f)

    /** Leading edge of a moving highlight. */
    fun <T> lead(): SpringSpec<T> = spring(dampingRatio = 0.78f, stiffness = 820f)

    /** Trailing edge: slower, so a highlight stretches like a drop and then pulls itself together. */
    fun <T> trail(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 190f)

    /** Colours and sizes that should settle without overshoot. */
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 260f)
}

/** Animated range [start, end] of a moving highlight, in any unit (item index, dp, px). */
class FluidSpan(val start: State<Float>, val end: State<Float>)

/**
 * The edge on the side of travel uses a stiff spring and the other a soft one, so a tab, segment or switch
 * highlight stretches toward its target and then contracts. The gooey look without blur tricks.
 */
@Composable
fun fluidSpan(start: Float, end: Float, enabled: Boolean = true): FluidSpan {
    val last = remember { floatArrayOf(start) }
    val forward = remember(start) {
        val f = start >= last[0]
        last[0] = start
        f
    }
    val a = animateFloatAsState(
        start,
        if (!enabled) snap() else if (forward) Fluid.trail() else Fluid.lead(),
        label = "spanStart"
    )
    val b = animateFloatAsState(
        end,
        if (!enabled) snap() else if (forward) Fluid.lead() else Fluid.trail(),
        label = "spanEnd"
    )
    return FluidSpan(a, b)
}

// ------------------------------------------------------------------ shapes

private fun polar(c: Offset, a: Float, r: Float): Offset = Offset(c.x + r * cos(a), c.y + r * sin(a))

private fun dist(a: Offset, b: Offset): Float = hypot(b.x - a.x, b.y - a.y)

/**
 * Neck between two circles (Hiroyuki Sato's metaball construction). Fill it with the same opaque brush as both
 * circles. Returns null when the circles are too far apart (the drop has broken off) or one contains the other.
 */
fun metaballBridge(c1: Offset, r1: Float, c2: Offset, r2: Float, v: Float = 0.5f, handle: Float = 2.4f): Path? {
    val d = dist(c1, c2)
    if (r1 <= 0f || r2 <= 0f || d > r1 + r2 * 2.5f || d <= abs(r1 - r2)) return null
    var u1 = 0f
    var u2 = 0f
    if (d < r1 + r2) {
        u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)).coerceIn(-1f, 1f))
        u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)).coerceIn(-1f, 1f))
    }
    val between = atan2(c2.y - c1.y, c2.x - c1.x)
    val spread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    val a1 = between + u1 + (spread - u1) * v
    val a2 = between - u1 - (spread - u1) * v
    val a3 = between + PI_F - u2 - (PI_F - u2 - spread) * v
    val a4 = between - PI_F + u2 + (PI_F - u2 - spread) * v
    val p1 = polar(c1, a1, r1)
    val p2 = polar(c1, a2, r1)
    val p3 = polar(c2, a3, r2)
    val p4 = polar(c2, a4, r2)
    val total = r1 + r2
    val k = min(v * handle, dist(p1, p3) / total) * min(1f, d * 2f / total)
    val h1 = polar(p1, a1 - HALF_PI, r1 * k)
    val h2 = polar(p2, a2 + HALF_PI, r1 * k)
    val h3 = polar(p3, a3 + HALF_PI, r2 * k)
    val h4 = polar(p4, a4 - HALF_PI, r2 * k)
    return Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(h1.x, h1.y, h3.x, h3.y, p3.x, p3.y)
        lineTo(p4.x, p4.y)
        cubicTo(h4.x, h4.y, h2.x, h2.y, p2.x, p2.y)
        close()
    }
}

/** Closed blob around [c]: radius [r] with a slow seamless wobble of [amp] px. [phase] loops on 0..1. */
fun wobblyCircle(c: Offset, r: Float, amp: Float, phase: Float, points: Int = 40): Path {
    if (amp < 0.05f) return Path().apply { addOval(Rect(c, r)) }
    val pts = ArrayList<Offset>(points)
    for (i in 0 until points) {
        val t = i.toFloat() / points * TAU
        val k = sin(3f * t + phase * TAU) * 0.55f +
            sin(5f * t - 2f * phase * TAU + 1.3f) * 0.3f +
            sin(2f * t + 3f * phase * TAU + 0.4f) * 0.15f
        val rr = r + amp * k
        pts += Offset(c.x + rr * cos(t), c.y + rr * sin(t))
    }
    return smoothClosed(pts)
}

/** Closed Catmull-Rom spline through [pts], as cubic Beziers. */
fun smoothClosed(pts: List<Offset>): Path {
    val p = Path()
    val n = pts.size
    if (n < 3) return p
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]
        val p1 = pts[i]
        val p2 = pts[(i + 1) % n]
        val p3 = pts[(i + 2) % n]
        p.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    p.close()
    return p
}

/**
 * Smooth open curve through [pts] (Catmull-Rom). Control points are clamped to [minY]..[maxY] so a chart line
 * never overshoots its baseline.
 */
fun smoothPath(pts: List<Offset>, minY: Float = Float.NEGATIVE_INFINITY, maxY: Float = Float.POSITIVE_INFINITY): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[max(i - 1, 0)]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[min(i + 2, pts.size - 1)]
        p.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(minY, maxY),
            p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(minY, maxY),
            p2.x, p2.y
        )
    }
    return p
}

// ------------------------------------------------------------------ ambient

/** Slow light behind a hero card: soft blobs drifting on looping paths (26 s period). */
@Composable
fun Aurora(colors: List<Color>, modifier: Modifier = Modifier, live: Boolean = true) {
    val t = if (live) {
        rememberInfiniteTransition(label = "aurora")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "auroraT")
    } else {
        remember { mutableFloatStateOf(0.2f) }
    }
    Canvas(modifier) {
        if (colors.isEmpty()) return@Canvas
        val k = t.value * TAU
        val w = size.width
        val h = size.height
        val big = max(w, h)
        colors.forEachIndexed { i, col ->
            val fx = 0.2f + 0.6f * ((i * 0.37f) % 1f)
            val cx = w * (fx + 0.16f * sin(k * (1 + i % 2) + i * 2.1f))
            val cy = h * (0.45f + 0.35f * cos(k * (2 - i % 2) + i * 1.3f))
            val r = big * (0.42f + 0.08f * sin(k + i))
            val at = Offset(cx, cy)
            drawCircle(
                Brush.radialGradient(
                    0f to col,
                    0.5f to col.copy(alpha = col.alpha * 0.35f),
                    1f to col.copy(alpha = 0f),
                    center = at, radius = r
                ),
                radius = r, center = at
            )
        }
    }
}

/** Status dot with a ring that keeps rippling out. Its layout size is just the dot, so rows stay aligned. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, dot: Dp = 8.dp, live: Boolean = true) {
    val t = if (live) {
        rememberInfiniteTransition(label = "pulse")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulseT")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier.size(dot)) {
        val r = size.minDimension / 2f
        if (live) {
            val p = t.value
            drawCircle(color.copy(alpha = color.alpha * 0.5f * (1f - p)), radius = r * (1f + 1.8f * p))
        }
        drawCircle(color, radius = r)
    }
}

// ------------------------------------------------------------------ controls

/**
 * The connect button. A liquid core with a power glyph. While connecting, drops orbit the core and stay
 * attached by metaball necks, break off and melt back in. Connected adds a halo and a slow ring. A press
 * squashes it like jelly.
 */
@Composable
fun GooeyConnectButton(
    on: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    accent: Color,
    accent2: Color,
    idle: Color,
    idle2: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    iconColor: Color = Color.White,
    ringColor: Color = Color.White.copy(alpha = 0.10f),
    enabled: Boolean = true,
    motion: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val squash by animateFloatAsState(
        when {
            pressed && enabled -> 0.9f
            hovered && enabled -> 1.035f
            else -> 1f
        },
        Fluid.jelly(), label = "squash"
    )
    val heat by animateFloatAsState(if (on) 1f else if (busy) 0.7f else 0f, tween(650), label = "heat")
    val spread by animateFloatAsState(if (busy && motion) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 120f), label = "spread")
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(800), label = "glow")
    val live = motion && (on || busy || spread > 0.01f || heat > 0.01f)
    val slow: State<Float>
    val orbit: State<Float>
    if (live) {
        val inf = rememberInfiniteTransition(label = "gooey")
        slow = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "slow")
        orbit = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "orbit")
    } else {
        slow = remember { mutableFloatStateOf(0f) }
        orbit = remember { mutableFloatStateOf(0f) }
    }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val half = size.minDimension / 2f
            val core = half * 0.6f * squash
            val s = slow.value
            val o = orbit.value
            val top = lerp(idle, accent, heat)
            val bottom = lerp(idle2, accent2, heat)
            if (glow > 0.01f) {
                drawCircle(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.42f * glow),
                        0.55f to accent2.copy(alpha = 0.14f * glow),
                        1f to accent2.copy(alpha = 0f),
                        center = c, radius = half
                    ),
                    radius = half, center = c
                )
            }
            val ringR = half * 0.84f
            drawCircle(ringColor, radius = ringR, center = c, style = Stroke(width = 1.dp.toPx()))
            val sweep = max(glow, spread)
            if (sweep > 0.01f) {
                rotate(if (spread > glow) o * 360f else s * 360f, c) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.85f * sweep), accent2.copy(alpha = 0f)),
                            center = c
                        ),
                        radius = ringR, center = c, style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
            val brush = Brush.linearGradient(listOf(top, bottom), start = Offset(c.x - core, c.y - core), end = Offset(c.x + core, c.y + core))
            val amp = core * (0.02f * heat + 0.028f * spread)
            if (spread > 0.01f) {
                for (i in 0 until 4) {
                    val ph = o + i * 0.25f
                    val ang = ph * TAU + sin((o * 2f + i * 0.37f) * TAU) * 0.35f
                    val out = 0.5f + 0.5f * sin((o * 2f + i * 0.25f) * TAU)
                    val d = core + half * (0.04f + 0.17f * out) * spread
                    val r = half * (0.085f + 0.035f * (1f - out)) * min(1f, spread)
                    val p = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d)
                    metaballBridge(c, core - amp, p, r)?.let { drawPath(it, brush) }
                    drawCircle(brush, radius = r, center = p)
                }
            }
            val blob = wobblyCircle(c, core, amp, s)
            drawPath(blob, brush)
            drawPath(blob, Color.White.copy(alpha = 0.12f * (1f - spread)), style = Stroke(width = 1.dp.toPx()))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0f)),
                    center = Offset(c.x - core * 0.3f, c.y - core * 0.55f), radius = core * 1.05f
                ),
                radius = core * 0.98f, center = c
            )
            val g = core * 0.36f
            val sw = max(2.dp.toPx(), core * 0.07f)
            val gy = c.y + g * 0.06f
            drawArc(
                iconColor, startAngle = -58f, sweepAngle = 296f, useCenter = false,
                topLeft = Offset(c.x - g, gy - g), size = Size(g * 2f, g * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawLine(iconColor, Offset(c.x, gy - g * 1.12f), Offset(c.x, gy - g * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        Box(
            Modifier.size(diameter * 0.64f).clip(CircleShape)
                .hoverable(src, enabled)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
        )
    }
}

/**
 * Switch whose thumb is a drop: on toggle the leading edge jumps ahead and the tail catches up, on press the
 * thumb swells toward the side it will travel.
 */
@Composable
fun GooeyToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    on: Color,
    on2: Color,
    off: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumb: Color = Color.White,
    width: Dp = 40.dp,
    height: Dp = 24.dp,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pad = 3f
    val d = height.value - pad * 2f
    val travel = width.value - pad * 2f - d
    val grow = if (pressed && enabled) d * 0.32f else 0f
    val start = if (checked) pad + travel - grow else pad
    val end = if (checked) pad + travel + d else pad + d + grow
    val span = fluidSpan(start, end)
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "toggle")
    Canvas(
        modifier.size(width, height).alpha(if (enabled) 1f else 0.45f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        val r = size.height / 2f
        drawRoundRect(off, cornerRadius = CornerRadius(r, r))
        if (t > 0.001f) drawRoundRect(Brush.horizontalGradient(listOf(on, on2)), alpha = t, cornerRadius = CornerRadius(r, r))
        val x0 = span.start.value.dp.toPx()
        val x1 = span.end.value.dp.toPx()
        val top = pad.dp.toPx()
        val h = d.dp.toPx()
        val cr = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(x0, top + 1.dp.toPx()), size = Size(x1 - x0, h), cornerRadius = cr)
        drawRoundRect(thumb, topLeft = Offset(x0, top), size = Size(x1 - x0, h), cornerRadius = cr)
    }
}

/**
 * Slider with a liquid thumb: it swells and stretches while dragged, the filled track follows on a spring.
 * [value] is 0..1; the caller maps it to its own range.
 */
@Composable
fun FluidSlider(
    value: Float,
    onChange: (Float) -> Unit,
    track: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    fill2: Color = fill,
    thumb: Color = Color.White,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val change = rememberUpdatedState(onChange)
    val done = rememberUpdatedState(onDone)
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), if (dragging) Fluid.lead() else Fluid.soft(), label = "sliderValue")
    val grow by animateFloatAsState(if (dragging) 1f else 0f, Fluid.jelly(), label = "sliderGrow")
    Canvas(
        modifier.fillMaxWidth().height(28.dp).alpha(if (enabled) 1f else 0.45f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    val pad = 10.dp.toPx()
                    change.value(((o.x - pad) / (size.width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f))
                    done.value?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 10.dp.toPx()
                val w = (size.width - 2f * pad).coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        change.value(((o.x - pad) / w).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        done.value?.invoke()
                    },
                    onDragCancel = { dragging = false },
                ) { ch, _ ->
                    ch.consume()
                    change.value(((ch.position.x - pad) / w).coerceIn(0f, 1f))
                }
            }
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        val pad = 10.dp.toPx()
        val cy = size.height / 2f
        val th = 4.dp.toPx()
        val w = size.width - 2f * pad
        val x = pad + w * shown
        val cr = CornerRadius(th / 2f, th / 2f)
        drawRoundRect(track, Offset(pad, cy - th / 2f), Size(w, th), cr)
        if (x > pad) {
            drawRoundRect(Brush.horizontalGradient(listOf(fill, fill2), startX = pad, endX = pad + w), Offset(pad, cy - th / 2f), Size(x - pad, th), cr)
        }
        val r = 8.dp.toPx() * (1f + 0.2f * grow)
        val rx = r * (1f + 0.16f * grow)
        if (grow > 0.01f) drawCircle(fill.copy(alpha = 0.18f * grow), radius = r * 1.9f, center = Offset(x, cy))
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(x - rx, cy - r + 1.dp.toPx()), Size(rx * 2f, r * 2f))
        drawOval(thumb, Offset(x - rx, cy - r), Size(rx * 2f, r * 2f))
    }
}

// ------------------------------------------------------------------ the lettermark

/**
 * RayScope lettermark: a geometric R on a 100-unit grid. A heavy P-shaped body with a round counter (even-odd
 * fill) and a separate leg, the "ray", which carries the gradient. Bounding box x 16..86, y 12..88.
 * Same geometry as the Android launcher icon and desktop/launcher/make_icon.py.
 */
object Mark {
    const val BODY = "M16 12H50A26 26 0 0 1 50 64H40V88H16Z"
    const val COUNTER = "M50 28.5A9.5 9.5 0 1 1 50 47.5A9.5 9.5 0 1 1 50 28.5Z"
    const val RAY = "M46 69H68L86 88H63Z"
    val INK = Color(0xFFF4F6FF)
    val RAY_FROM = Color(0xFF9AA6FF)
    val RAY_TO = Color(0xFF4FD6FF)
    val TILE_FROM = Color(0xFF232A52)
    val TILE_TO = Color(0xFF0B0E1A)

    fun body(): Path = PathParser().parsePathString(BODY + COUNTER).toPath().apply { fillType = PathFillType.EvenOdd }

    fun ray(): Path = PathParser().parsePathString(RAY).toPath()

    fun rayBrush(from: Color = RAY_FROM, to: Color = RAY_TO): Brush =
        Brush.linearGradient(listOf(from, to), start = Offset(46f, 69f), end = Offset(86f, 88f))
}

@Composable
fun Lettermark(modifier: Modifier = Modifier, ink: Color = Mark.INK, ray1: Color = Mark.RAY_FROM, ray2: Color = Mark.RAY_TO) {
    val body = remember { Mark.body() }
    val leg = remember { Mark.ray() }
    Canvas(modifier) {
        val k = min(size.width / 70f, size.height / 76f)
        translate(size.width / 2f - 51f * k, size.height / 2f - 50f * k) {
            scale(k, k, pivot = Offset.Zero) {
                drawPath(body, ink)
                drawPath(leg, Mark.rayBrush(ray1, ray2))
            }
        }
    }
}

/** App-icon tile: deep navy gradient with the lettermark, same as the launcher icon. */
@Composable
fun LogoTile(modifier: Modifier = Modifier, tile: Dp = 32.dp) {
    val shape = RoundedCornerShape(tile * 0.27f)
    Box(
        modifier.size(tile).clip(shape)
            .background(Brush.linearGradient(listOf(Mark.TILE_FROM, Mark.TILE_TO)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center
    ) {
        Lettermark(Modifier.size(tile * 0.6f))
    }
}
