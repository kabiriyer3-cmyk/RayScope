package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ palette (same "Scope" look as Android)

val Good = Color(0xFF3DDC97)
val Mid = Color(0xFFFFB547)
val Bad = Color(0xFFFF5C6C)
val Idle = Color(0xFF7D8590)
val Orange = Color(0xFFFF8A3D)
val Blue = Color(0xFF4C8DFF)
val Purple = Color(0xFFA78BFA)
val Indigo = Color(0xFF7C83FF)
val Teal = Color(0xFF2DD4BF)
val Pink = Color(0xFFFF6B9A)
val Gray = Color(0xFF8B949E)
val Yellow = Color(0xFFFFD43B)
val Cyan = Color(0xFF38E1FF)
val InkOn = Color(0xFF051116)

val TNum = TextStyle(fontFeatureSettings = "tnum")

@Immutable
data class Pal(
    val dark: Boolean,
    val bg: Color,
    val side: Color,
    val surface: Color,
    val raised: Color,
    val stroke: Color,
    val text: Color,
    val hint: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    /** Liquid Glass level: 0 off, 1 soft, 2 strong */
    val glass: Int = 1,
    val glassFill: Color = Color.Unspecified,
    val glassFallback: Color = Color.Unspecified,
    val rim: Color = Color.Unspecified,
)

val ACCENT_NAMES = listOf("Циан", "Мята", "Лайм", "Синий", "Фиалка", "Розовый", "Янтарь", "Коралл")
private val ACCENTS_DARK = listOf(
    Color(0xFF38E1FF), Color(0xFF3DDC97), Color(0xFFC6F432), Color(0xFF5B9BFF),
    Color(0xFFA78BFA), Color(0xFFFF6B9A), Color(0xFFFFB547), Color(0xFFFF7A59)
)
private val ACCENTS_LIGHT = listOf(
    Color(0xFF0091BA), Color(0xFF0E9F6E), Color(0xFF5E8C00), Color(0xFF2563EB),
    Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFC27803), Color(0xFFE0482A)
)

fun accentColor(i: Int, dark: Boolean): Color {
    val l = if (dark) ACCENTS_DARK else ACCENTS_LIGHT
    return l.getOrElse(i) { l[0] }
}

fun palette(dark: Boolean, accentIndex: Int, glass: Int = 1): Pal {
    val a = accentColor(accentIndex, dark)
    val on = if (a.luminance() > 0.45f) InkOn else Color.White
    return if (dark) {
        Pal(
            dark = true, bg = Color(0xFF0A0C10), side = Color(0xFF0E1015), surface = Color(0xFF13161C),
            raised = Color(0xFF1C2028), stroke = Color.White.copy(alpha = 0.07f), text = Color(0xFFF1F3F7),
            hint = Color(0xFF8A93A2), faint = Color.White.copy(alpha = 0.10f), accent = a, onAccent = on,
            glass = glass.coerceIn(0, 2), glassFill = Color(0xFF14171D).copy(alpha = 0.58f),
            glassFallback = Color(0xF2171A21), rim = Color.White.copy(alpha = 0.42f)
        )
    } else {
        Pal(
            dark = false, bg = Color(0xFFF3F4F6), side = Color(0xFFEBEDF1), surface = Color.White,
            raised = Color(0xFFEDEFF3), stroke = Color.Black.copy(alpha = 0.08f), text = Color(0xFF0E1116),
            hint = Color(0xFF667080), faint = Color.Black.copy(alpha = 0.08f), accent = a, onAccent = on,
            glass = glass.coerceIn(0, 2), glassFill = Color.White.copy(alpha = 0.66f),
            glassFallback = Color(0xF5FFFFFF), rim = Color.White
        )
    }
}

val LocalPal = staticCompositionLocalOf { palette(true, 0) }

val pal: Pal
    @Composable get() = LocalPal.current

@Composable
fun RayTheme(theme: Int, accent: Int, glass: Int = 1, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = palette(dark, accent, glass)
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.hint,
            surfaceContainer = p.surface, surfaceContainerHigh = p.raised, outline = Color(0xFF2E3440)
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.hint,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, outline = Color(0xFFD0D4DB)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPal provides p, LocalContentColor provides p.text) {
            content()
        }
    }
}

// ------------------------------------------------------------------ icons (Lucide-like, drawn in code)

private val INK = SolidColor(Color.Black)

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply(block).build()

private fun ImageVector.Builder.line(width: Float = 2f, block: PathBuilder.() -> Unit) {
    path(fill = null, stroke = INK, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
}

private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) {
    path(fill = INK, pathBuilder = block)
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    lineTo(x + w, y)
    lineTo(x + w, y + h)
    lineTo(x, y + h)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

private fun appIcon(): ImageVector {
    val cyan = Color(0xFF38E1FF)
    val b = ImageVector.Builder(name = "app", defaultWidth = 32.dp, defaultHeight = 32.dp, viewportWidth = 32f, viewportHeight = 32f)
    b.path(fill = Brush.verticalGradient(listOf(Color(0xFF2A303A), Color(0xFF0A0D12)), startY = 1f, endY = 31f)) {
        roundRect(1f, 1f, 30f, 30f, 7.5f)
    }
    b.path(fill = Brush.radialGradient(listOf(cyan.copy(alpha = 0.40f), cyan.copy(alpha = 0f)), center = Offset(16f, 16f), radius = 11f)) {
        circle(16f, 16f, 11f)
    }
    for (i in 0 until 24) {
        val a = (i * 15.0 - 90.0) * PI / 180.0
        val long = i % 6 == 0
        val r2 = 12.8f
        val r1 = if (long) 10.3f else 11.3f
        val k = (1f - i / 17f).coerceAtLeast(0f)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        b.path(
            stroke = SolidColor(cyan.copy(alpha = 0.28f + 0.72f * k)),
            strokeLineWidth = if (long) 1.4f else 0.9f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(16f + ca * r1, 16f + sa * r1)
            lineTo(16f + ca * r2, 16f + sa * r2)
        }
    }
    b.path(fill = SolidColor(cyan.copy(alpha = 0.14f)), stroke = SolidColor(cyan.copy(alpha = 0.6f)), strokeLineWidth = 0.6f) {
        circle(16f, 16f, 7f)
    }
    b.path(stroke = SolidColor(cyan), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
        moveTo(16f, 11.3f)
        lineTo(16f, 15.6f)
    }
    b.path(stroke = SolidColor(cyan), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
        moveTo(18.97f, 13.53f)
        arcTo(4.2f, 4.2f, 0f, true, true, 13.03f, 13.53f)
    }
    b.path(stroke = SolidColor(Color.White.copy(alpha = 0.22f)), strokeLineWidth = 0.6f) {
        roundRect(1.3f, 1.3f, 29.4f, 29.4f, 7.2f)
    }
    return b.build()
}

object Ico {
    /** Same artwork as RayScope.exe: dark squircle, scope ring, power glyph. */
    val App: ImageVector = appIcon()

    val Power = icon("power") {
        line { moveTo(12f, 2.5f); lineTo(12f, 11.5f) }
        line { moveTo(18.36f, 6.64f); arcTo(9f, 9f, 0f, true, true, 5.64f, 6.64f) }
    }
    val Shield = icon("shield") {
        line {
            moveTo(12f, 2.5f); lineTo(4.5f, 5.5f); lineTo(4.5f, 11f)
            curveTo(4.5f, 16f, 7.7f, 19.9f, 12f, 21.5f)
            curveTo(16.3f, 19.9f, 19.5f, 16f, 19.5f, 11f)
            lineTo(19.5f, 5.5f); close()
        }
        line { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) }
    }
    val Bolt = icon("bolt") {
        solid { moveTo(13f, 2f); lineTo(3f, 14f); lineTo(12f, 14f); lineTo(11f, 22f); lineTo(21f, 10f); lineTo(12f, 10f); close() }
    }
    val Home = icon("home") {
        line { moveTo(3f, 11f); lineTo(12f, 3.5f); lineTo(21f, 11f) }
        line { moveTo(5.5f, 9.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.5f) }
    }
    val Servers = icon("servers") {
        line { rect(3f, 4f, 18f, 7f) }
        line { rect(3f, 13f, 18f, 7f) }
        line(2.6f) { moveTo(7f, 7.5f); lineTo(7.01f, 7.5f) }
        line(2.6f) { moveTo(7f, 16.5f); lineTo(7.01f, 16.5f) }
    }
    val Radar = icon("radar") {
        line { circle(12f, 12f, 9f) }
        line { circle(12f, 12f, 4.5f) }
        line { moveTo(12f, 12f); lineTo(18.4f, 5.6f) }
    }
    val Sliders = icon("sliders") {
        line { moveTo(4f, 7f); lineTo(20f, 7f) }
        line { moveTo(4f, 17f); lineTo(20f, 17f) }
        solid { circle(9f, 7f, 2.6f) }
        solid { circle(15f, 17f, 2.6f) }
    }
    val Logs = icon("logs") {
        line { rect(5f, 3f, 14f, 18f) }
        line { moveTo(8.5f, 8f); lineTo(15.5f, 8f) }
        line { moveTo(8.5f, 12f); lineTo(15.5f, 12f) }
        line { moveTo(8.5f, 16f); lineTo(13f, 16f) }
    }
    val Refresh = icon("refresh") {
        line { moveTo(20f, 12f); arcTo(8f, 8f, 0f, true, true, 17.66f, 6.34f) }
        line { moveTo(20f, 4f); lineTo(20f, 9f); lineTo(15f, 9f) }
    }
    val Plus = icon("plus") {
        line { moveTo(12f, 5f); lineTo(12f, 19f) }
        line { moveTo(5f, 12f); lineTo(19f, 12f) }
    }
    val Close = icon("close") {
        line { moveTo(6f, 6f); lineTo(18f, 18f) }
        line { moveTo(18f, 6f); lineTo(6f, 18f) }
    }
    val Check = icon("check") {
        line { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }
    val Chevron = icon("chevron") {
        line { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    }
    val Back = icon("back") {
        line { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    }
    val Search = icon("search") {
        line { circle(11f, 11f, 7f) }
        line { moveTo(16.5f, 16.5f); lineTo(21f, 21f) }
    }
    private fun PathBuilder.star() {
        moveTo(12f, 2.5f); lineTo(14.9f, 8.6f); lineTo(21.5f, 9.3f); lineTo(16.5f, 13.8f); lineTo(17.9f, 20.4f)
        lineTo(12f, 17f); lineTo(6.1f, 20.4f); lineTo(7.5f, 13.8f); lineTo(2.5f, 9.3f); lineTo(9.1f, 8.6f); close()
    }
    val Star = icon("star") { line { star() } }
    val StarFilled = icon("starFilled") { solid { star() } }
    val Trash = icon("trash") {
        line { moveTo(4f, 7f); lineTo(20f, 7f) }
        line { moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f) }
        line { moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f) }
    }
    val Copy = icon("copy") {
        line { rect(8f, 8f, 12f, 12f) }
        line { moveTo(16f, 8f); lineTo(16f, 4f); lineTo(4f, 4f); lineTo(4f, 16f); lineTo(8f, 16f) }
    }
    val Globe = icon("globe") {
        line { circle(12f, 12f, 9f) }
        line { moveTo(3f, 12f); lineTo(21f, 12f) }
        line { moveTo(12f, 3f); arcTo(4.5f, 9f, 0f, false, true, 12f, 21f); arcTo(4.5f, 9f, 0f, false, true, 12f, 3f) }
    }
    val Clipboard = icon("clipboard") {
        line { rect(5f, 4.5f, 14f, 16.5f) }
        line { rect(9f, 2.5f, 6f, 3.5f) }
    }
    val Download = icon("download") {
        line { moveTo(12f, 4f); lineTo(12f, 15f) }
        line { moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f) }
        line { moveTo(5f, 20f); lineTo(19f, 20f) }
    }
    val Upload = icon("upload") {
        line { moveTo(12f, 20f); lineTo(12f, 8.5f) }
        line { moveTo(7f, 13f); lineTo(12f, 8f); lineTo(17f, 13f) }
        line { moveTo(5f, 4f); lineTo(19f, 4f) }
    }
    val Alert = icon("alert") {
        line { moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close() }
        line { moveTo(12f, 9f); lineTo(12f, 13.5f) }
        line(2.6f) { moveTo(12f, 17f); lineTo(12.01f, 17f) }
    }
    val More = icon("more") {
        solid { circle(5f, 12f, 1.7f) }
        solid { circle(12f, 12f, 1.7f) }
        solid { circle(19f, 12f, 1.7f) }
    }
    val Sort = icon("sort") {
        line { moveTo(4f, 7f); lineTo(20f, 7f) }
        line { moveTo(7f, 12f); lineTo(17f, 12f) }
        line { moveTo(10f, 17f); lineTo(14f, 17f) }
    }
    val Layers = icon("layers") {
        line { moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close() }
        line { moveTo(3f, 13f); lineTo(12f, 18f); lineTo(21f, 13f) }
    }
    val Activity = icon("activity") {
        line { moveTo(3f, 12f); lineTo(7f, 12f); lineTo(10f, 4f); lineTo(14f, 20f); lineTo(17f, 12f); lineTo(21f, 12f) }
    }
    val Folder = icon("folder") {
        line { moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close() }
    }
    val Link = icon("link") {
        line { moveTo(10f, 14f); lineTo(14f, 10f) }
        line { moveTo(8.5f, 11.5f); lineTo(6.5f, 13.5f); arcTo(3.5f, 3.5f, 0f, false, false, 11.5f, 18.5f); lineTo(13.5f, 16.5f) }
        line { moveTo(15.5f, 12.5f); lineTo(17.5f, 10.5f); arcTo(3.5f, 3.5f, 0f, false, false, 12.5f, 5.5f); lineTo(10.5f, 7.5f) }
    }
    val Arrow = icon("arrow") {
        line { moveTo(4f, 12f); lineTo(20f, 12f) }
        line { moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) }
    }
}

// ------------------------------------------------------------------ helpers

fun Modifier.card(p: Pal, radius: Dp = 18.dp, stroke: Color = p.stroke, width: Dp = 1.dp): Modifier {
    val s = RoundedCornerShape(radius)
    return this.clip(s).background(p.surface).border(width, stroke, s)
}

fun Modifier.hand(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)

@Composable
fun spin(ms: Int = 900): State<Float> {
    val inf = rememberInfiniteTransition(label = "spin")
    return inf.animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "spinValue")
}

fun delayColor(d: Long): Color {
    if (d == -2L) return Idle
    if (d < 0) return Bad
    val p = Store.prefs.value
    return when {
        d < p.goodPing -> Good
        d < p.okPing -> Mid
        else -> Orange
    }
}

fun delayLevel(d: Long): Int {
    if (d < 0) return 0
    val p = Store.prefs.value
    return when {
        d < p.goodPing / 2 -> 4
        d < p.goodPing -> 3
        d < p.okPing -> 2
        else -> 1
    }
}

// ------------------------------------------------------------------ text blocks

@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text.uppercase(), color = if (color == Color.Unspecified) pal.hint else color,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier
    )
}

@Composable
fun Tag(text: String, color: Color = Color.Unspecified) {
    val col = if (color == Color.Unspecified) pal.hint else color
    Text(
        text, color = col, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        maxLines = 1, letterSpacing = 0.4.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun DevTag() {
    Tag("DEV", Purple)
}

@Composable
fun Header(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, color = p.hint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun Section(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = pal
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Caps(title, Modifier.weight(1f))
                if (trailing != null) trailing()
            }
        }
        Column(Modifier.fillMaxWidth().card(p, 16.dp), content = content)
        if (footer != null) {
            Text(footer, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp))
        }
    }
}

@Composable
fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.stroke))
}

@Composable
fun KV(k: String, v: String, valueColor: Color = Color.Unspecified, mono: Boolean = false) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.42f))
        Text(
            v.ifEmpty { "—" },
            color = if (valueColor == Color.Unspecified) p.text else valueColor,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TNum,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
fun KVIf(k: String, v: String, mono: Boolean = false) {
    if (v.isNotEmpty()) KV(k, v, mono = mono)
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 34.dp, iconSize: Dp = 18.dp, iconModifier: Modifier = Modifier) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = iconModifier.size(iconSize))
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    color: Color = Color.Unspecified,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconRotation: State<Float>? = null,
) {
    val p = pal
    val tint = if (color == Color.Unspecified) p.accent else color
    val fg = if (primary) (if (tint.luminance() > 0.45f) InkOn else Color.White) else tint
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(if (primary) tint else tint.copy(alpha = if (p.dark) 0.14f else 0.10f))
            .clickable(enabled = enabled, onClick = onClick).hand()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon, contentDescription = null, tint = fg,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = iconRotation?.value ?: 0f }
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = Color.Unspecified, enabled: Boolean = true, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).hand(),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = if (tint == Color.Unspecified) pal.hint else tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun RChip(text: String, selected: Boolean, count: Int? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = pal
    val bg by animateColorAsState(if (selected) p.accent else p.raised, tween(180))
    val fg by animateColorAsState(if (selected) p.onAccent else p.text, tween(180))
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(bg).border(1.dp, if (selected) Color.Transparent else p.stroke, shape)
            .clickable(onClick = onClick).hand().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) fg else Yellow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TNum)
        }
    }
}

@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = pal
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f))
    val track = lerp(p.raised, p.accent, t)
    val thumb = lerp(if (p.dark) Color(0xFF8E97A6) else Color.White, p.onAccent, t)
    Box(
        Modifier.size(width = 42.dp, height = 24.dp).alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape).background(track).border(1.dp, p.stroke, CircleShape)
            .clickable(enabled = enabled) { onChange(!checked) }.hand()
            .padding(3.dp)
    ) {
        Box(Modifier.offset(x = 18.dp * t).size(18.dp).clip(CircleShape).background(thumb))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().height(38.dp).clip(shape).background(p.raised).border(1.dp, p.stroke, shape).padding(3.dp)) {
        options.forEachIndexed { i, t ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) (if (p.dark) Color(0xFF2A303B) else Color.White) else Color.Transparent, tween(180))
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(bg).clickable { onSelect(i) }.hand(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t, color = if (sel) p.text else p.hint, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    leading: ImageVector? = null,
    onCard: Boolean = true,
) {
    val p = pal
    val ff = if (mono) FontFamily.Monospace else FontFamily.Default
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(if (onCard) p.raised else p.surface).border(1.dp, p.stroke, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = p.hint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = p.hint.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp, fontFamily = ff),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ------------------------------------------------------------------ setting rows

@Composable
fun SettingRow(title: String, subtitle: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = pal
    var m: Modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
    if (onClick != null && enabled) m = m.clickable(onClick = onClick).hand()
    Row(m.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, enabled, onClick = { onChange(!checked) }) {
        Toggle(checked, enabled, onChange)
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    subtitle: String? = null,
    enabled: Boolean = true,
    format: (Int) -> String = { "$it" },
    onChange: (Int) -> Unit,
) {
    val p = pal
    var raw by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val s = step.coerceAtLeast(1)
    fun snap(f: Float): Int = (((f - range.first) / s).roundToInt() * s + range.first).coerceIn(range.first, range.last)
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text(format(snap(raw)), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = TNum)
        }
        Slider(
            value = raw,
            onValueChange = { nv ->
                raw = nv
                val r = snap(nv)
                if (r != value) onChange(r)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.raised,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, subtitle: String? = null, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> RChip(o, i == selected) { if (enabled) onSelect(i) } }
        }
    }
}

@Composable
fun FieldRow(title: String, value: String, placeholder: String, subtitle: String? = null, mono: Boolean = true, onChange: (String) -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Field(value, onChange, placeholder, Modifier.fillMaxWidth(), mono = mono)
    }
}

// ------------------------------------------------------------------ flags and latency display

/** Country flag drawn in code (see Flags.kt); unknown codes fall back to letters, empty to a globe. */
@Composable
fun FlagBox(code: String, size: Dp = 40.dp) {
    val p = pal
    val shape = RoundedCornerShape(size * 0.28f)
    val known = code in FLAG_CODES
    Box(Modifier.size(size).clip(shape).background(p.raised), contentAlignment = Alignment.Center) {
        when {
            known -> Canvas(Modifier.fillMaxSize()) {
                drawFlag(code)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.14f))
                    )
                )
            }
            code.isEmpty() -> Icon(Ico.Globe, contentDescription = null, tint = p.hint, modifier = Modifier.size(size * 0.46f))
            else -> Text(code, color = p.text, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Box(
            Modifier.matchParentSize().border(
                1.dp,
                if (known) (if (p.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)) else p.stroke,
                shape
            )
        )
    }
}

@Composable
fun SignalBars(d: Long, modifier: Modifier = Modifier) {
    val p = pal
    val col = delayColor(d)
    val lvl = delayLevel(d)
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(Modifier.width(3.dp).height((3 * i).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= lvl) col else p.faint))
        }
    }
}

@Composable
fun LatencyText(d: Long, size: TextUnit = 17.sp) {
    val p = pal
    val col by animateColorAsState(delayColor(d), tween(300))
    if (d >= 0L || d == -2L) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (d == -2L) "—" else "$d", color = col, fontSize = size, fontWeight = FontWeight.Bold, style = TNum, maxLines = 1)
            if (d >= 0) Text(" ms", color = p.hint, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
        }
    } else {
        Icon(Ico.Close, contentDescription = "нет ответа", tint = col, modifier = Modifier.size((size.value * 0.95f).dp))
    }
}

@Composable
fun DelayBadge(d: Long) {
    val col = delayColor(d)
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "нет"
        else -> "$d ms"
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(5.dp))
        Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TNum)
    }
}

@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier) {
    val p = pal
    val v by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300))
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(p.raised)) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(50)).background(p.accent))
    }
}
