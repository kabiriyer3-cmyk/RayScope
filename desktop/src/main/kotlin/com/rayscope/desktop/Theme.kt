package com.rayscope.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rayscope.fluid.Fluid
import com.rayscope.fluid.FluidSlider
import com.rayscope.fluid.Glyphs
import com.rayscope.fluid.GooeyToggle
import com.rayscope.fluid.LiveIcon
import com.rayscope.fluid.LocalIconHover
import com.rayscope.fluid.LogoMark
import com.rayscope.fluid.MinusGlyph
import com.rayscope.fluid.RayLogo
import com.rayscope.fluid.fluidSpan
import com.rayscope.fluid.playOnRise
import com.rayscope.fluid.rememberIconFx
import kotlin.math.roundToInt

// ------------------------------------------------------------------ palette: graphite, one flat accent (same as Android)

val Good = Color(0xFF3DDC97)
val Mid = Color(0xFFF5B83D)
val Bad = Color(0xFFF26D6D)
val Idle = Color(0xFF6B7385)
val Orange = Color(0xFFF59E5B)
val Blue = Color(0xFF3D8BFF)
val Purple = Color(0xFFA78BFA)
val Indigo = Color(0xFF7C8CFF)
val Teal = Color(0xFF2DD4BF)
val Pink = Color(0xFFF472B6)
val Gray = Color(0xFF8C94A8)
val Yellow = Color(0xFFFBBF24)
val Cyan = Color(0xFF4FD6FF)
val InkOn = Color(0xFF0F1115)

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
    /** row highlight under the mouse */
    val hover: Color,
    /** same as accent: kept so older call sites compile */
    val accent2: Color = accent,
    /** connect tile while disconnected */
    val tileIdle: Color = raised,
) {
    /** Primary fill. Flat on purpose: no gradients anywhere. */
    val accentBrush: Brush get() = SolidColor(accent)
}

val ACCENT_NAMES = listOf("Графит", "Индиго", "Синий", "Зелёный", "Янтарь", "Красный", "Фиалка", "Бирюза")
private val ACCENTS_DARK = listOf(0xFFE9EAED, 0xFF7C8CFF, 0xFF3D8BFF, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0xFFA78BFA, 0xFF2DD4BF).map { Color(it) }
private val ACCENTS_LIGHT = listOf(0xFF15171C, 0xFF4F5BD5, 0xFF1F6FEB, 0xFF059669, 0xFFB45309, 0xFFDC2626, 0xFF7C3AED, 0xFF0D9488).map { Color(it) }

fun accentColor(i: Int, dark: Boolean): Color {
    val l = if (dark) ACCENTS_DARK else ACCENTS_LIGHT
    return l.getOrElse(i) { l[0] }
}

/** Kept for callers of the old two-hue accent: there are no gradients any more, so it is the accent itself. */
fun accentColor2(i: Int, dark: Boolean): Color = accentColor(i, dark)

/**
 * Graphite surfaces. side: the window panel; surface: cards on it; raised: controls on cards.
 * tileIdle: the connect tile while the tunnel is down.
 */
fun palette(dark: Boolean, accentIndex: Int): Pal {
    val a = accentColor(accentIndex, dark)
    val on = if (a.luminance() > 0.5f) InkOn else Color.White
    return if (dark) {
        Pal(
            dark = true, bg = Color(0xFF0A0B0D), side = Color(0xFF111216), surface = Color(0xFF191B20),
            raised = Color(0xFF23252B), stroke = Color.White.copy(alpha = 0.07f), text = Color(0xFFF2F3F5),
            hint = Color(0xFF8B909A), faint = Color.White.copy(alpha = 0.08f), accent = a, onAccent = on,
            hover = Color.White.copy(alpha = 0.05f), accent2 = a, tileIdle = Color(0xFF25272D)
        )
    } else {
        Pal(
            dark = false, bg = Color(0xFFF1F1EF), side = Color(0xFFE9E9E6), surface = Color.White,
            raised = Color(0xFFF1F1EF), stroke = Color.Black.copy(alpha = 0.08f), text = Color(0xFF111215),
            hint = Color(0xFF5F646D), faint = Color.Black.copy(alpha = 0.07f), accent = a, onAccent = on,
            hover = Color.Black.copy(alpha = 0.045f), accent2 = a, tileIdle = Color(0xFFD9D9D5)
        )
    }
}

val LocalPal = staticCompositionLocalOf { palette(true, 0) }

val pal: Pal
    @Composable get() = LocalPal.current

@Composable
fun RayTheme(theme: Int, accent: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = palette(dark, accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.hint,
            surfaceContainer = p.surface, surfaceContainerHigh = p.raised, outline = Color(0xFF2A3040)
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.hint,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, outline = Color(0xFFD5D9E2)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        // M3 body text carries 0.5 sp tracking and a fixed 24 sp line: both made rows look loose and uneven
        val text = LocalTextStyle.current.merge(TextStyle(letterSpacing = 0.sp, lineHeight = 1.4.em))
        CompositionLocalProvider(LocalPal provides p, LocalContentColor provides p.text, LocalTextStyle provides text) {
            content()
        }
    }
}

// ------------------------------------------------------------------ the lettermark

/** The R lettermark, one continuous line in one colour. */
@Composable
fun RayMark(modifier: Modifier = Modifier, ink: Color = pal.text) {
    LogoMark(modifier, ink)
}

/** Window, taskbar and tray icon: graphite tile with the white lettermark (small optical cut), same as RayScope.exe. */
private fun appIcon(): ImageVector {
    val b = ImageVector.Builder(name = "app", defaultWidth = 32.dp, defaultHeight = 32.dp, viewportWidth = 100f, viewportHeight = 100f)
    b.addPath(
        PathParser().parsePathString("M25 3H75A22 22 0 0 1 97 25V75A22 22 0 0 1 75 97H25A22 22 0 0 1 3 75V25A22 22 0 0 1 25 3Z").toNodes(),
        fill = SolidColor(RayLogo.TILE)
    )
    b.addGroup(name = "mark", pivotX = 50f, pivotY = 50f, scaleX = 0.7f, scaleY = 0.7f)
    b.addPath(PathParser().parsePathString(RayLogo.PATH_SMALL).toNodes(), fill = SolidColor(RayLogo.INK))
    b.clearGroup()
    return b.build()
}

// ------------------------------------------------------------------ icons: RayScope glyphs (shared/src/.../Glyphs.kt)

object Ico {
    val App: ImageVector = appIcon()

    val Home get() = Glyphs.Home
    val Servers get() = Glyphs.Servers
    val Globe get() = Glyphs.Globe
    val Settings get() = Glyphs.Settings
    val Sliders get() = Glyphs.Sliders
    val Radar get() = Glyphs.Radar
    val Search get() = Glyphs.Search
    val Refresh get() = Glyphs.Refresh
    val Repeat get() = Glyphs.Repeat
    val Sort get() = Glyphs.Sort
    val More get() = Glyphs.More
    val Star get() = Glyphs.Star
    val StarFilled get() = Glyphs.StarFilled
    val Info get() = Glyphs.Info
    val Back get() = Glyphs.Back
    val Chevron get() = Glyphs.Chevron
    val Plus get() = Glyphs.Plus
    val Trash get() = Glyphs.Trash
    val Close get() = Glyphs.Close
    val Power get() = Glyphs.Power
    val Play get() = Glyphs.Play
    val Bolt get() = Glyphs.Bolt
    val Copy get() = Glyphs.Copy
    val Share get() = Glyphs.Share
    val Shield get() = Glyphs.Shield
    val ShieldCheck get() = Glyphs.ShieldCheck
    val Gauge get() = Glyphs.Gauge
    val Activity get() = Glyphs.Activity
    val ArrowDown get() = Glyphs.ArrowDown
    val ArrowUp get() = Glyphs.ArrowUp
    val Clock get() = Glyphs.Clock
    val Timer get() = Glyphs.Timer
    val Link get() = Glyphs.Link
    val Apps get() = Glyphs.Apps
    val Logs get() = Glyphs.Logs
    val Check get() = Glyphs.Check
    val Clipboard get() = Glyphs.Clipboard
    val Package get() = Glyphs.Package
    val Pin get() = Glyphs.Pin
    val Alert get() = Glyphs.Alert
    val Lock get() = Glyphs.Lock
    val Key get() = Glyphs.Key
    val Wifi get() = Glyphs.Wifi
    val Code get() = Glyphs.Code
    val Sun get() = Glyphs.Sun
    val Moon get() = Glyphs.Moon
    val Signal get() = Glyphs.Signal
    val Eye get() = Glyphs.Eye
    val Plug get() = Glyphs.Plug
    val Route get() = Glyphs.Route
    val Fork get() = Glyphs.Fork
    val Layers get() = Glyphs.Layers
    val Scissors get() = Glyphs.Scissors
    val Hash get() = Glyphs.Hash
    val Network get() = Glyphs.Network
    val Ban get() = Glyphs.Ban
    val Filter get() = Glyphs.Filter
    val Download get() = Glyphs.Download
    val Upload get() = Glyphs.Upload
    val Vibrate get() = Glyphs.Vibrate
    val User get() = Glyphs.User
    val Target get() = Glyphs.Target
    val Folder get() = Glyphs.Folder
    val Arrow get() = Glyphs.Arrow
    val Minus get() = MinusGlyph
}

// ------------------------------------------------------------------ helpers

/** Side and top padding of every page inside the panel. */
val PAGE_PAD = 14.dp
val PAGE_TOP = 6.dp

/** Screens ask for radii on the 12-20 dp scale; the fluid look keeps them generous. */
fun soft(radius: Dp): RoundedCornerShape = RoundedCornerShape((radius * 0.8f).coerceIn(8.dp, 18.dp))

/** Surface card with a flat hairline. */
fun Modifier.card(p: Pal, radius: Dp = 18.dp, stroke: Color = p.stroke, width: Dp = 1.dp): Modifier {
    val s = soft(radius)
    return this.clip(s).background(p.surface).border(width, stroke, s)
}

fun Modifier.hand(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)

/** Clickable with a fading hover tint instead of a ripple. */
@Composable
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    pressWith(remember { MutableInteractionSource() }, enabled, onClick)

@Composable
fun Modifier.pressWith(src: MutableInteractionSource, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val hovered by src.collectIsHoveredAsState()
    val p = pal
    val bg by animateColorAsState(if (hovered && enabled) p.hover else p.hover.copy(alpha = 0f), tween(140), label = "hover")
    return this
        .background(bg)
        .hoverable(src, enabled)
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
        .hand()
}

/** Jelly squash while pressed. */
@Composable
fun Modifier.squish(src: MutableInteractionSource, enabled: Boolean = true, depth: Float = 0.96f): Modifier {
    val pressed by src.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed && enabled) depth else 1f, Fluid.jelly(), label = "squish")
    return this.graphicsLayer {
        scaleX = k
        scaleY = k
    }
}

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

/** Icon colour in rows and tiles: status colours stay, decorative colours fold into grey. */
@Composable
private fun rowTint(tint: Color): Color {
    val p = pal
    return if (tint == Bad || tint == Good || tint == Mid || tint == p.accent || tint == Yellow) tint else p.hint
}

// ------------------------------------------------------------------ text blocks

/** Section label. Sentence case, muted. */
@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text, color = if (color == Color.Unspecified) pal.hint else color,
        fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier
    )
}

/** Small tinted pill: protocol, transport, security. */
@Composable
fun Tag(text: String, color: Color = Color.Unspecified) {
    val p = pal
    val col = if (color == Color.Unspecified) p.hint else color
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(20.dp).clip(shape).background(if (color == Color.Unspecified) p.faint else col.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = col, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
    }
}

@Composable
fun DevTag() {
    Tag("dev", Purple)
}

@Composable
fun Header(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, color = p.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Caps(title, Modifier.weight(1f))
                if (trailing != null) trailing()
            }
        }
        Column(Modifier.fillMaxWidth().card(p, 18.dp), content = content)
        if (footer != null) {
            Text(footer, color = p.hint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = p.hint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.42f))
        Text(
            v.ifEmpty { "—" },
            color = if (valueColor == Color.Unspecified) p.text else valueColor,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = TNum,
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
    val p = pal
    val shape = RoundedCornerShape(size * 0.3f)
    val col = rowTint(tint)
    Box(
        Modifier.size(size).clip(shape).background(if (col == p.hint) p.raised else col.copy(alpha = 0.13f)).border(1.dp, p.stroke, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = col, modifier = iconModifier.size(iconSize))
    }
}

/** Tinted square badge for stat cards and action tiles. The glyph moves with the row: hover, click, busy. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    box: Dp = 32.dp,
    rotation: State<Float>? = null,
    hover: Boolean = false,
    play: Int = 0,
    busy: Boolean = false,
) {
    val shape = RoundedCornerShape(box * 0.32f)
    Box(modifier.size(box).clip(shape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        LiveIcon(
            icon, tint, Modifier.size(box * 0.56f).graphicsLayer { rotationZ = rotation?.value ?: 0f },
            hover = hover, play = play, busy = busy
        )
    }
}

/** Keyboard hint, same font as the label next to it so both sit on one line. */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    val p = pal
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.height(20.dp).clip(shape).background(p.raised).border(1.dp, p.stroke, shape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.hint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
    }
}

// ------------------------------------------------------------------ controls

/**
 * Pill button. Primary: accent gradient. Secondary: raised plate with a hairline. color = hint: ghost.
 * color = Bad: red tint for destructive actions. Squashes like jelly when pressed.
 */
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
    busy: Boolean = false,
) {
    val p = pal
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val fx = rememberIconFx()
    val solid = primary && color != Color.Unspecified && color != p.accent
    val ghost = !primary && color == p.hint
    val danger = !primary && color == Bad
    val fg = when {
        primary -> if ((if (solid) color else p.accent).luminance() > 0.5f) InkOn else Color.White
        danger -> Bad
        else -> p.text
    }
    val shape = RoundedCornerShape(50)
    val fill: Brush = when {
        solid -> SolidColor(color)
        primary -> p.accentBrush
        ghost -> SolidColor(if (hovered && enabled) p.hover else Color.Transparent)
        danger -> SolidColor(Bad.copy(alpha = if (hovered && enabled) 0.18f else 0.11f))
        else -> SolidColor(if (hovered && enabled) lerp(p.raised, p.text, 0.06f) else p.raised)
    }
    var m = modifier.squish(src, enabled).alpha(if (enabled) 1f else 0.45f).heightIn(min = 34.dp).clip(shape).background(fill)
    if (!primary && !ghost && !danger) m = m.border(1.dp, p.stroke, shape)
    if (primary && hovered && enabled) m = m.background(Color.White.copy(alpha = 0.1f))
    Row(
        m.hoverable(src, enabled)
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                onClick()
                fx.fire(icon)
            }.hand()
            .padding(horizontal = 15.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            LiveIcon(
                icon, if (primary || danger) fg else p.hint,
                Modifier.size(16.dp).graphicsLayer { rotationZ = iconRotation?.value ?: 0f },
                hover = hovered && enabled, play = fx.plays, busy = busy, done = fx.done, doneTint = if (primary) fg else Good
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Round icon button. The glyph answers the pointer: its hover pose or a short motion, the motion again on click,
 * a loop while [busy]; copy turns into a green check for a moment after the click.
 */
@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    busy: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val fx = rememberIconFx()
    Box(
        Modifier.size(size).squish(src, enabled, 0.88f).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).pressWith(src, enabled) {
            onClick()
            fx.fire(icon)
        },
        contentAlignment = Alignment.Center
    ) {
        LiveIcon(
            icon, if (tint == Color.Unspecified) pal.hint else tint, Modifier.size((size * 0.56f).coerceIn(14.dp, 22.dp)),
            contentDescription = desc, hover = hovered && enabled, play = fx.plays, busy = busy, done = fx.done, doneTint = Good
        )
    }
}

@Composable
fun RChip(text: String, selected: Boolean, count: Int? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = pal
    val bg by animateColorAsState(
        if (selected) p.accent.copy(alpha = if (p.dark) 0.16f else 0.11f) else p.accent.copy(alpha = 0f), Fluid.soft(), label = "chipBg"
    )
    val line by animateColorAsState(if (selected) p.accent.copy(alpha = 0.5f) else p.stroke, Fluid.soft(), label = "chipLine")
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val picked = playOnRise(selected)
    Row(
        Modifier.squish(src).height(30.dp).clip(shape).background(bg).border(1.dp, line, shape)
            .pressWith(src, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            LiveIcon(icon, if (selected) p.accent else Yellow, Modifier.size(14.dp), hover = hovered, play = picked)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) p.text else p.hint, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = if (selected) p.accent else p.hint.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium, style = TNum)
        }
    }
}

/** Gooey switch (see shared Fluid.kt). */
@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = pal
    GooeyToggle(
        checked = checked, onChange = onChange, on = p.accent, on2 = p.accent,
        off = if (p.dark) Color(0xFF2C2E35) else Color(0xFFD6D6D2), enabled = enabled,
        // a light accent (graphite in the dark theme) needs a dark thumb to stay visible
        thumb = if (checked && p.accent.luminance() > 0.6f) p.onAccent else Color.White
    )
}

/**
 * Segmented control with a fluid indicator: the pill stretches toward the new option and catches up.
 * accent = true fills the indicator with the accent gradient.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    val p = pal
    val n = options.size.coerceAtLeast(1)
    val span = fluidSpan(selected.toFloat(), selected + 1f)
    val shape = RoundedCornerShape(50)
    val pill = if (p.dark) Color(0xFF30333A) else Color.White
    val brush = p.accentBrush
    val shadow = Color.Black.copy(alpha = if (p.dark) 0.28f else 0.08f)
    Row(
        modifier.fillMaxWidth().height(36.dp).clip(shape).background(p.raised).border(1.dp, p.stroke, shape)
            .drawBehind {
                val inset = 3.dp.toPx()
                val w = (size.width - inset * 2f) / n
                val x0 = inset + span.start.value * w
                val x1 = inset + span.end.value * w
                val h = size.height - inset * 2f
                val cr = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(shadow, Offset(x0, inset + 1.dp.toPx()), Size(x1 - x0, h), cr)
                if (accent) drawRoundRect(brush, Offset(x0, inset), Size(x1 - x0, h), cr) else drawRoundRect(pill, Offset(x0, inset), Size(x1 - x0, h), cr)
            }
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, t ->
            val sel = i == selected
            val fg by animateColorAsState(if (sel) (if (accent) p.onAccent else p.text) else p.hint, tween(180), label = "segment")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }.hand(),
                contentAlignment = Alignment.Center
            ) {
                Text(t, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val line by animateColorAsState(if (focused) p.accent.copy(alpha = 0.7f) else p.stroke, tween(160), label = "field")
    // placeholder and input share one style, so the caret line matches the hint exactly
    val ts = TextStyle(color = p.text, fontSize = 13.sp, fontFamily = ff)
    Row(
        modifier.heightIn(min = 38.dp).clip(shape).background(if (onCard) p.raised else p.surface).border(1.dp, line, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            LiveIcon(leading, if (focused) p.accent else p.hint, Modifier.size(16.dp), hover = focused)
            Spacer(Modifier.width(9.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = ts.copy(color = p.hint.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = ts,
                interactionSource = src,
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
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var m: Modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (enabled) 1f else 0.45f)
    if (onClick != null && enabled) m = m.pressWith(src, onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 13.5.sp)
            if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        // chevrons and folders in the slot nudge when the row is hovered
        CompositionLocalProvider(LocalIconHover provides (hovered && onClick != null && enabled)) {
            trailing()
        }
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
    val span = (range.last - range.first).coerceAtLeast(1).toFloat()
    fun snap(f: Float): Int = (((f - range.first) / s).roundToInt() * s + range.first).coerceIn(range.first, range.last)
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 13.5.sp)
                if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text(format(snap(raw)), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = TNum)
        }
        Spacer(Modifier.height(4.dp))
        FluidSlider(
            value = (raw - range.first) / span,
            onChange = { f ->
                raw = range.first + f * span
                val r = snap(raw)
                if (r != value) onChange(r)
            },
            track = p.faint, fill = p.accent, fill2 = p.accent2, enabled = enabled
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, subtitle: String? = null, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = p.text, fontSize = 13.5.sp)
        if (subtitle != null) Text(subtitle, color = p.hint, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> RChip(o, i == selected) { if (enabled) onSelect(i) } }
        }
    }
}

@Composable
fun FieldRow(title: String, value: String, placeholder: String, subtitle: String? = null, mono: Boolean = true, onChange: (String) -> Unit) {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = p.text, fontSize = 13.5.sp)
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
            known -> Canvas(Modifier.fillMaxSize()) { drawFlag(code) }
            code.isEmpty() -> Icon(Ico.Globe, contentDescription = null, tint = p.hint, modifier = Modifier.size(size * 0.52f))
            else -> Text(code, color = p.text, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.SemiBold, style = TNum)
        }
        Box(Modifier.matchParentSize().border(1.dp, if (p.dark) Color.White.copy(alpha = 0.1f) else p.stroke, shape))
    }
}

@Composable
fun SignalBars(d: Long, modifier: Modifier = Modifier) {
    val p = pal
    val col = delayColor(d)
    val lvl = delayLevel(d)
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(Modifier.width(3.dp).height((3 + 2.25f * i).dp).clip(RoundedCornerShape(50)).background(if (i <= lvl) col else p.faint))
        }
    }
}

@Composable
fun LatencyText(d: Long, size: TextUnit = 17.sp) {
    val p = pal
    val col by animateColorAsState(delayColor(d), tween(300), label = "latency")
    if (d >= 0L || d == -2L) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (d == -2L) "—" else "$d", color = if (d >= 0) p.text else col, fontSize = size, fontWeight = FontWeight.SemiBold, style = TNum, maxLines = 1)
            if (d >= 0) Text(" ms", color = p.hint, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    } else {
        Icon(Ico.Close, contentDescription = "нет ответа", tint = col, modifier = Modifier.size((size.value * 0.9f).dp))
    }
}

@Composable
fun DelayBadge(d: Long) {
    val p = pal
    val col = delayColor(d)
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "нет"
        else -> "$d ms"
    }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(24.dp).clip(shape).background(col.copy(alpha = 0.12f)).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(6.dp))
        Text(txt, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = TNum)
    }
}

@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier) {
    val p = pal
    val v by animateFloatAsState(progress.coerceIn(0f, 1f), Fluid.soft(), label = "progress")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(p.faint)) {
        Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(50)).background(p.accentBrush))
    }
}
