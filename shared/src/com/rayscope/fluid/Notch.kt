/*
 * Notch layout: the connect tile sits in the top-left corner and the panel wraps around it with an even gap.
 * When the tunnel comes up, light runs around the tile. Also the pill tab bar.
 * Shared by Android and Windows.
 */
package com.rayscope.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 6.2831855f

/** Sizes of the notch layout. The panel's inner corner is concentric with the tile, so the gap stays even. */
@Immutable
data class NotchSpec(
    val tile: Dp = 112.dp,
    val gap: Dp = 8.dp,
    val tileRadius: Dp = 28.dp,
    val radius: Dp = 28.dp,
    /** radius of the two small corners where the panel meets the tile */
    val cusp: Dp = 14.dp,
)

/** Panel outline in px: rounded rectangle with the tile's corner cut out. */
fun notchPath(size: Size, s: Float, g: Float, rt: Float, rp: Float, rc: Float): Path {
    val n = s + g
    val w = size.width
    val h = maxOf(size.height, n + rc + rp)
    val big = rt + g
    val c = s - rt
    return Path().apply {
        moveTo(n + rc, 0f)
        lineTo(w - rp, 0f)
        arcTo(Rect(w - 2f * rp, 0f, w, 2f * rp), -90f, 90f, false)
        lineTo(w, h - rp)
        arcTo(Rect(w - 2f * rp, h - 2f * rp, w, h), 0f, 90f, false)
        lineTo(rp, h)
        arcTo(Rect(0f, h - 2f * rp, 2f * rp, h), 90f, 90f, false)
        lineTo(0f, n + rc)
        arcTo(Rect(0f, n, 2f * rc, n + 2f * rc), 180f, 90f, false)
        lineTo(c, n)
        arcTo(Rect(c - big, c - big, c + big, c + big), 90f, -90f, false)
        lineTo(n, rc)
        arcTo(Rect(n, 0f, n + 2f * rc, 2f * rc), 180f, 90f, false)
        close()
    }
}

/**
 * Centre line of the channel around the tile: the gap on the panel side, the window margin on the other two.
 * Starts in the middle of the inner (bottom-right) corner and runs clockwise.
 */
fun ringPath(s: Float, g: Float, rt: Float): Path {
    val m = g / 2f
    val r = rt + m
    val a = rt
    val b = s - rt
    val k = 0.70710677f
    fun box(cx: Float, cy: Float) = Rect(cx - r, cy - r, cx + r, cy + r)
    return Path().apply {
        moveTo(b + r * k, b + r * k)
        arcTo(box(b, b), 45f, 45f, false)
        lineTo(a, s + m)
        arcTo(box(a, b), 90f, 90f, false)
        lineTo(-m, a)
        arcTo(box(a, a), 180f, 90f, false)
        lineTo(b, -m)
        arcTo(box(b, a), 270f, 90f, false)
        lineTo(s + m, b)
        arcTo(box(b, b), 0f, 45f, false)
        close()
    }
}

class NotchShape(private val spec: NotchSpec) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        Outline.Generic(
            notchPath(size, spec.tile.toPx(), spec.gap.toPx(), spec.tileRadius.toPx(), spec.radius.toPx(), spec.cusp.toPx())
        )
    }
}

/**
 * Light around the connect tile. [level]: 0 off, 1 connecting (one short beam runs around the tile at an even
 * pace), 2 connected (the ring draws itself from the inner corner both ways and stays). Eased, never springy.
 */
@Composable
fun GrooveRay(spec: NotchSpec, level: Int, color: Color, modifier: Modifier = Modifier, width: Dp = 3.dp, motion: Boolean = true) {
    val ring by animateFloatAsState(
        if (level == 2) 1f else 0f,
        if (motion) tween(if (level == 2) 750 else 320, easing = FastOutSlowInEasing) else snap(),
        label = "grooveRing"
    )
    val beam by animateFloatAsState(
        if (level == 1) 1f else 0f,
        if (motion) tween(320, easing = FastOutSlowInEasing) else snap(),
        label = "grooveBeam"
    )
    var phase = 0f
    if (level == 1 && motion) {
        val inf = rememberInfiniteTransition(label = "groove")
        val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "grooveT")
        phase = t
    }
    if (ring > 0.003f || beam > 0.003f) {
        Canvas(modifier) {
            val path = ringPath(spec.tile.toPx(), spec.gap.toPx(), spec.tileRadius.toPx())
            val pm = PathMeasure()
            pm.setPath(path, false)
            val len = pm.length
            val stroke = Stroke(width = width.toPx(), cap = StrokeCap.Round)
            if (ring > 0.003f) {
                val half = len / 2f * ring
                val seg = Path()
                pm.getSegment(0f, half, seg, true)
                pm.getSegment(len - half, len, seg, true)
                drawPath(seg, color.copy(alpha = color.alpha * ring.coerceIn(0f, 1f)), style = stroke)
            }
            if (beam > 0.003f) {
                if (!motion) {
                    drawPath(path, color.copy(alpha = color.alpha * 0.35f * beam), style = stroke)
                } else {
                    val head = phase * len
                    val start = head - len * 0.22f
                    val seg = Path()
                    if (start >= 0f) {
                        pm.getSegment(start, head, seg, true)
                    } else {
                        pm.getSegment(len + start, len, seg, true)
                        pm.getSegment(0f, head, seg, true)
                    }
                    drawPath(seg, color.copy(alpha = color.alpha * 0.9f * beam), style = stroke)
                }
            }
        }
    }
}

/**
 * The connect button that lives in the notch: a rounded square that squashes when pressed, breathes while the
 * tunnel comes up and fills with [onFill] once it is up.
 */
@Composable
fun ConnectTile(
    on: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    idleFill: Color,
    idleInk: Color,
    onFill: Color,
    onInk: Color,
    label: String,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    enabled: Boolean = true,
    icon: ImageVector = Glyphs.Power,
    motion: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val k by animateFloatAsState(if (pressed && enabled) 0.93f else 1f, Fluid.jelly(), label = "tileSquash")
    var pulse = 0f
    if (busy && motion) {
        val inf = rememberInfiniteTransition(label = "tilePulse")
        val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "tilePulseT")
        pulse = 0.5f - 0.5f * cos(t * TWO_PI)
    }
    val heat by animateFloatAsState(if (on) 1f else if (busy) 0.4f else 0f, tween(420), label = "tileHeat")
    val h = (heat + if (busy) 0.22f * pulse else 0f).coerceIn(0f, 1f)
    val lift = if (hovered && enabled && !on) 0.07f else 0f
    val fill = lerp(lerp(idleFill, onFill, h), onFill, lift)
    val ink = lerp(idleInk, onInk, ((h - 0.35f) / 0.3f).coerceIn(0f, 1f))
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .graphicsLayer {
                scaleX = k
                scaleY = k
                alpha = if (enabled) 1f else 0.55f
            }
            .clip(shape)
            .background(fill)
            .hoverable(src, enabled)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(radius * 1.45f))
            Spacer(Modifier.height(6.dp))
            Text(label, color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1)
        }
    }
}

/**
 * Tile in the corner, panel around it, light in the gap. [shoulder] fills the strip to the right of the tile,
 * [body] everything under it. [fill] = true stretches the panel to the full height (desktop window).
 */
@Composable
fun NotchFrame(
    spec: NotchSpec,
    panel: Color,
    edge: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    ray: Int = 0,
    rayColor: Color = Color.White,
    motion: Boolean = true,
    tile: @Composable () -> Unit,
    shoulder: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(spec) { NotchShape(spec) }
    Box(modifier) {
        Column(
            (if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).clip(shape).background(panel).border(1.dp, edge, shape)
        ) {
            Row(Modifier.fillMaxWidth().height(spec.tile)) {
                Spacer(Modifier.width(spec.tile + spec.gap))
                Box(Modifier.weight(1f).fillMaxHeight()) { shoulder() }
            }
            Spacer(Modifier.height(spec.gap))
            body()
        }
        Box(Modifier.size(spec.tile)) { tile() }
        GrooveRay(spec, ray, rayColor, Modifier.matchParentSize(), motion = motion)
    }
}

/**
 * Pill tab bar. The active tab grows into a pill with its label, the others shrink to icons; widths ease from
 * one tab to the next without overshoot.
 */
@Composable
fun PillTabs(
    items: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    track: Color,
    pill: Color,
    onPill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    dotIndex: Int = -1,
    dot: Color = Color.Unspecified,
) {
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, item ->
            val sel = i == selected
            val w by animateFloatAsState(if (sel) 2.5f else 1f, tween(280, easing = FastOutSlowInEasing), label = "tabWidth")
            val bg by animateColorAsState(if (sel) pill else pill.copy(alpha = 0f), tween(220), label = "tabFill")
            val fg by animateColorAsState(if (sel) onPill else ink, tween(220), label = "tabInk")
            val src = remember { MutableInteractionSource() }
            Row(
                Modifier.weight(w.coerceAtLeast(0.4f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(bg)
                    .clickable(interactionSource = src, indication = null) { onSelect(i) }
                    .pointerHoverIcon(PointerIcon.Hand),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Icon(item.first, contentDescription = item.second, tint = fg, modifier = Modifier.size(20.dp))
                    if (i == dotIndex && dot != Color.Unspecified) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(CircleShape).background(dot))
                    }
                }
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.second, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
