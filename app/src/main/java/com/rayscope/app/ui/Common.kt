package com.rayscope.app.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.data.Repo
import com.rayscope.app.data.UiPrefs
import com.rayscope.fluid.LiveIcon
import com.rayscope.fluid.rememberIconFx
import kotlin.math.roundToInt

// ------------------------------------------------------------------ palette: neutral greys, one accent

val Good = Color(0xFF4CB782)
val Mid = Color(0xFFE2A93B)
val Bad = Color(0xFFEB5757)
val Idle = Color(0xFF6E7380)
val Orange = Color(0xFFEF8A47)
val Blue = Color(0xFF4E8EF7)
val Purple = Color(0xFF9C88F0)
val Indigo = Color(0xFF7C84FF)
val Teal = Color(0xFF3FB8A9)
val Pink = Color(0xFFE8688F)
val Gray = Color(0xFF8B8D98)
val Yellow = Color(0xFFE8C547)
val Cyan = Color(0xFF4FB7E0)

/** Dark ink used on top of light fills (graphite accent, amber). */
val InkOn = Color(0xFF0D0E10)

/** Tabular figures, so changing numbers don't jitter. */
val TNum = TextStyle(fontFeatureSettings = "tnum")

@Immutable
data class RayColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val stroke: Color,
    val text: Color,
    val hint: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    /** corner radius multiplier from the look settings */
    val corner: Float = 1f,
) {
    val card: Color get() = surface
    val field: Color get() = raised
    val divider: Color get() = stroke
}

private val DarkRay = RayColors(
    dark = true,
    bg = Color(0xFF0D0E10),
    surface = Color(0xFF141518),
    raised = Color(0xFF1C1D21),
    stroke = Color.White.copy(alpha = 0.075f),
    text = Color(0xFFEDEEF0),
    hint = Color(0xFF8B8D98),
    faint = Color.White.copy(alpha = 0.08f),
    accent = Indigo,
    onAccent = Color.White,
)

private val LightRay = RayColors(
    dark = false,
    bg = Color(0xFFF7F7F8),
    surface = Color.White,
    raised = Color(0xFFF0F0F2),
    stroke = Color.Black.copy(alpha = 0.08f),
    text = Color(0xFF16171A),
    hint = Color(0xFF6B6F7A),
    faint = Color.Black.copy(alpha = 0.07f),
    accent = Color(0xFF5864D8),
    onAccent = Color.White,
)

val LocalRay = staticCompositionLocalOf { DarkRay }
val LocalUi = staticCompositionLocalOf { UiPrefs() }

val ray: RayColors
    @Composable get() = LocalRay.current

/** Current look & feel preferences. */
val look: UiPrefs
    @Composable get() = LocalUi.current

fun accentFor(index: Int, dark: Boolean): Color {
    val arr = if (dark) UiPrefs.ACCENTS_DARK else UiPrefs.ACCENTS_LIGHT
    return Color(arr.getOrElse(index) { arr[0] })
}

@Composable
fun RayTheme(ui: UiPrefs, content: @Composable () -> Unit) {
    val dark = when (ui.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    var r = if (dark) DarkRay else LightRay
    if (dark && ui.amoled) {
        r = r.copy(bg = Color.Black, surface = Color(0xFF0B0B0D), raised = Color(0xFF151518))
    }
    val accent: Color = if (ui.accent < 0 && Build.VERSION.SDK_INT >= 31) {
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
    } else {
        accentFor(ui.accent, dark)
    }
    r = r.copy(
        accent = accent,
        onAccent = if (accent.luminance() > 0.45f) InkOn else Color.White,
        corner = ui.cornerScale,
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = r.accent, onPrimary = r.onAccent, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.surface, onSurface = r.text,
            surfaceVariant = r.raised, onSurfaceVariant = r.hint,
            surfaceContainerLowest = r.surface, surfaceContainerLow = r.surface, surfaceContainer = r.surface,
            surfaceContainerHigh = r.raised, surfaceContainerHighest = Color(0xFF24252A),
            outline = Color(0xFF2C2D33),
        )
    } else {
        lightColorScheme(
            primary = r.accent, onPrimary = r.onAccent, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.surface, onSurface = r.text,
            surfaceVariant = r.raised, onSurfaceVariant = r.hint,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White, surfaceContainerHighest = r.raised,
            outline = Color(0xFFDADBE0),
        )
    }
    val d = LocalDensity.current
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalRay provides r,
            LocalUi provides ui,
            LocalContentColor provides r.text,
            LocalDensity provides Density(d.density, d.fontScale * ui.fontScale),
        ) {
            content()
        }
    }
}

@Composable
fun RayBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(ray.bg), content = content)
}

/**
 * Rounded shape scaled by the corner setting. Screens ask for radii from the old 16-24 dp scale;
 * they are halved here so cards sit at 8-12 dp, controls at 6-8 dp.
 */
fun RayColors.shape(radius: Dp): RoundedCornerShape = RoundedCornerShape((radius * 0.5f).coerceIn(3.dp, 12.dp) * corner)

fun Modifier.card(c: RayColors, radius: Dp = 20.dp, stroke: Color = c.stroke, width: Dp = 1.dp): Modifier {
    val shape = c.shape(radius)
    return this.clip(shape).background(c.surface).border(width, stroke, shape)
}

fun Modifier.bottomLine(color: Color, inset: Dp = 48.dp, show: Boolean = true): Modifier =
    if (!show) this else this.drawBehind {
        drawLine(color, Offset(inset.toPx(), size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
    }

/** Room under the last item so the bottom bar never covers it. */
@Composable
fun TabBottomSpacer() {
    Spacer(Modifier.navigationBarsPadding().height(84.dp))
}

/** Icon colour in rows and tiles: status colours stay, decorative colours fold into grey. */
@Composable
private fun rowTint(tint: Color): Color {
    val c = ray
    return if (tint == Bad || tint == Good || tint == Mid || tint == c.accent) tint else c.hint
}

// ------------------------------------------------------------------ motion helpers

/** Press feedback: the element dims while pressed (no scale, no ripple). */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.6f else 1f, tween(if (pressed) 60 else 160), label = "press")
    return this
        .graphicsLayer { alpha = a }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Short fade when a screen opens. No stagger and no movement; off when motion is reduced. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val motion = LocalUi.current.motion
    val a = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) a.animateTo(1f, tween(160)) else a.snapTo(1f)
    }
    return this.graphicsLayer { alpha = a.value }
}

@Composable
fun spinAngle(durationMs: Int = 900): State<Float> {
    val inf = rememberInfiniteTransition(label = "spin")
    return inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "spinAngle"
    )
}

@Composable
fun breathe(durationMs: Int = 1800): State<Float> {
    val inf = rememberInfiniteTransition(label = "breathe")
    return inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheValue"
    )
}

/** Tabular number text for counters and timers. */
@Composable
fun TickerText(text: String, color: Color, fontSize: TextUnit, weight: FontWeight = FontWeight.SemiBold) {
    Text(text, color = color, fontSize = fontSize, fontWeight = weight, maxLines = 1, style = TNum)
}

// ------------------------------------------------------------------ typography blocks

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, big: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, color = c.text, fontSize = if (big) 26.sp else 22.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp, lineHeight = if (big) 32.sp else 28.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.hint, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Section label above a group. Sentence case, muted. */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val c = ray
    Text(
        text, color = if (color == Color.Unspecified) c.hint else color,
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier
    )
}

/** Small outlined label: protocol, transport, feature flags. */
@Composable
fun Tag(text: String, color: Color = Color.Unspecified) {
    val c = ray
    val col = if (color == Color.Unspecified) c.hint else color
    val shape = c.shape(8.dp)
    Text(
        text, color = col, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier.clip(shape).border(1.dp, c.stroke, shape).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Marks developer-only blocks. */
@Composable
fun DevTag() {
    Tag("dev")
}

// ------------------------------------------------------------------ containers

/** Titled group: muted label, bordered surface, optional footnote. Rows inside handle their own padding. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ray
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapsLabel(title, Modifier.weight(1f))
                if (trailing != null) trailing()
            }
        }
        Column(Modifier.fillMaxWidth().card(c), content = content)
        if (footer != null) {
            Text(
                footer, color = c.hint, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )
        }
    }
}

@Composable
fun Hairline(inset: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(ray.stroke))
}

@Composable
fun KV(k: String, v: String, valueColor: Color = Color.Unspecified, mono: Boolean = false) {
    val c = ray
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = c.hint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.42f))
        Text(
            v.ifEmpty { "—" },
            color = if (valueColor == Color.Unspecified) c.text else valueColor,
            fontSize = if (mono) 13.sp else 14.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TNum,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
fun KVIf(k: String, v: String, mono: Boolean = false) {
    if (v.isNotEmpty()) KV(k, v, mono = mono)
}

/** Neutral bordered square with a glyph. Used for empty states and dialogs. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    iconModifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val c = ray
    val shape = c.shape(size * 0.5f)
    val fg = rowTint(tint)
    Box(
        Modifier.size(size).clip(shape).background(if (solid) tint else c.raised).border(1.dp, c.stroke, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (solid) (if (tint.luminance() > 0.5f) InkOn else Color.White) else fg,
            modifier = iconModifier.size(iconSize)
        )
    }
}

// ------------------------------------------------------------------ preference rows

@Composable
fun PrefRow(
    icon: ImageVector?,
    tint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = ray
    var m: Modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)
    if (onClick != null) m = m.clickable(enabled = enabled, onClick = onClick)
    Row(
        m.bottomLine(c.stroke, if (icon != null) 48.dp else 16.dp, divider)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 11.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = rowTint(tint), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = c.hint, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(
                value, color = c.hint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TNum,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 170.dp)
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Icon(
                RayIcons.Chevron, contentDescription = null, tint = c.hint.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp).size(16.dp)
            )
        }
    }
}

/** Compact switch: neutral track off, accent track on, white thumb. */
@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = ray
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(140), label = "toggle")
    val track = lerp(if (c.dark) Color(0xFF2A2B31) else Color(0xFFD9DADF), c.accent, t)
    Box(
        Modifier.size(width = 38.dp, height = 22.dp).alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape).background(track)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(Modifier.offset(x = 16.dp * t).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun PrefSwitch(
    icon: ImageVector?,
    tint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    divider: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    PrefRow(
        icon = icon, tint = tint, title = title, subtitle = subtitle, enabled = enabled, divider = divider,
        onClick = { onChange(!checked) },
        trailing = { Toggle(checked, enabled) { onChange(it) } }
    )
}

@Composable
fun PrefSlider(
    icon: ImageVector?,
    tint: Color,
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    format: (Int) -> String = { "$it" },
    onChange: (Int) -> Unit,
) {
    val c = ray
    var raw by remember { mutableFloatStateOf(value.toFloat()) }
    val s = step.coerceAtLeast(1)
    fun snap(f: Float): Int {
        val r = ((f - range.first) / s).roundToInt() * s + range.first
        return r.coerceIn(range.first, range.last)
    }
    val inset = if (icon != null) 48.dp else 16.dp
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).bottomLine(c.stroke, inset, divider)) {
        PrefRow(icon, tint, title, subtitle, value = format(snap(raw)), divider = false)
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
                thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.raised,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.padding(start = inset - 4.dp, end = 12.dp).height(32.dp)
        )
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrefChoice(
    icon: ImageVector?,
    tint: Color,
    title: String,
    options: List<String>,
    selected: Int,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val c = ray
    val inset = if (icon != null) 48.dp else 16.dp
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).bottomLine(c.stroke, inset, divider)) {
        PrefRow(icon, tint, title, subtitle, divider = false)
        FlowRow(
            Modifier.fillMaxWidth().padding(start = inset, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { i, o ->
                Chip(o, selected = i == selected, small = true, enabled = enabled) { onSelect(i) }
            }
        }
    }
}

@Composable
fun PrefText(
    icon: ImageVector?,
    tint: Color,
    title: String,
    value: String,
    placeholder: String,
    subtitle: String? = null,
    mono: Boolean = true,
    divider: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onChange: (String) -> Unit,
) {
    val c = ray
    val inset = if (icon != null) 48.dp else 16.dp
    Column(Modifier.fillMaxWidth().bottomLine(c.stroke, inset, divider)) {
        PrefRow(icon, tint, title, subtitle, divider = false)
        InputField(
            value = value, onValueChange = onChange, placeholder = placeholder, mono = mono,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.padding(start = inset, end = 16.dp, bottom = 12.dp)
        )
    }
}

// ------------------------------------------------------------------ controls

/** Segmented control: the selected option is a raised plate with a hairline. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ray
    val outer = c.shape(16.dp)
    val inner = c.shape(12.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(34.dp).clip(outer).background(c.raised).padding(2.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, tween(if (LocalUi.current.motion) 160 else 0), label = "segment")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .clip(inner)
                .background(if (c.dark) Color(0xFF2A2B31) else Color.White)
                .border(1.dp, c.stroke, inner)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t, color = if (i == selected) c.text else c.hint, fontSize = 13.sp,
                        fontWeight = if (i == selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    small: Boolean = false,
    enabled: Boolean = true,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val c = ray
    val bg by animateColorAsState(if (selected) c.accent.copy(alpha = if (c.dark) 0.18f else 0.12f) else Color.Transparent, tween(140), label = "chipBg")
    val line by animateColorAsState(if (selected) c.accent.copy(alpha = 0.55f) else c.stroke, tween(140), label = "chipLine")
    val shape = c.shape(14.dp)
    Row(
        Modifier.bounceClick(enabled = enabled, onClick = onClick).clip(shape).background(bg)
            .border(1.dp, line, shape)
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, color = if (selected) c.text else c.hint, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = c.hint, fontSize = 12.sp, style = TNum)
        }
    }
}

/** Small text button with a glyph, used in group headers and cards. */
@Composable
fun MiniAction(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    val c = ray
    val shape = c.shape(14.dp)
    val fx = rememberIconFx()
    Row(
        Modifier.bounceClick {
            onClick()
            fx.fire(icon)
        }.clip(shape).border(1.dp, c.stroke, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveIcon(
            icon, if (color == Bad) Bad else c.hint, Modifier.size(14.dp),
            play = fx.plays, done = fx.done, doneTint = Good, motion = LocalUi.current.motion
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (color == Bad) Bad else c.text, fontSize = 13.sp, maxLines = 1)
    }
}

/** Primary: filled accent. Secondary: neutral plate with a hairline, text in [color] if given. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    color: Color = Color.Unspecified,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
) {
    val c = ray
    val fill = if (color != Color.Unspecified) color else c.accent
    val fg = when {
        primary -> if (fill.luminance() > 0.45f) InkOn else Color.White
        color == Bad -> Bad
        else -> c.text
    }
    val shape = c.shape(16.dp)
    val fx = rememberIconFx()
    var m = modifier
        .bounceClick(enabled = enabled) {
            onClick()
            fx.fire(icon)
        }
        .alpha(if (enabled) 1f else 0.45f)
        .clip(shape)
    m = if (primary) m.background(fill) else m.background(c.raised).border(1.dp, c.stroke, shape)
    Row(
        m.padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            LiveIcon(
                icon, if (primary || color == Bad) fg else c.hint, iconModifier.size(16.dp),
                play = fx.plays, done = fx.done, doneTint = if (primary) fg else Good, motion = LocalUi.current.motion
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    onCard: Boolean = true,
    leading: ImageVector? = null,
    mono: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = ray
    val ff = if (mono) FontFamily.Monospace else FontFamily.Default
    val size = if (mono) 14.sp else 15.sp
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            CapsLabel(label, Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp))
        }
        val shape = c.shape(16.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(if (onCard) c.raised else c.surface).border(1.dp, c.stroke, shape)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = c.hint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.hint.copy(alpha = 0.7f), fontSize = size, fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = size, fontFamily = ff),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
fun FlagBox(flag: String, size: Dp = 40.dp) {
    val c = ray
    val showFlag = flag.isNotEmpty() && LocalUi.current.flags
    val shape = c.shape(size * 0.4f)
    Box(
        Modifier.size(size).clip(shape).background(c.raised).border(1.dp, c.stroke, shape),
        contentAlignment = Alignment.Center
    ) {
        if (!showFlag) {
            Icon(RayIcons.Globe, contentDescription = null, tint = c.hint, modifier = Modifier.size(size * 0.46f))
        } else {
            Text(flag, fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
fun CheckCircle(checked: Boolean) {
    val c = ray
    val bg by animateColorAsState(if (checked) c.accent else Color.Transparent, tween(140), label = "ccBg")
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(bg)
            .border(1.5.dp, if (checked) c.accent else c.hint.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(RayIcons.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val c = ray
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(c.faint)) {
        Box(
            Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(if (color == Color.Unspecified) c.accent else color)
        )
    }
}

// ------------------------------------------------------------------ latency

/** Latency colour using the thresholds from the ping settings. */
fun delayColor(d: Long): Color {
    if (d == -2L) return Idle
    if (d < 0) return Bad
    val st = Repo.settings
    return when {
        d < st.goodPing -> Good
        d < st.okPing -> Mid
        else -> Orange
    }
}

/** 0..4 bars for a latency value. */
fun delayLevel(d: Long): Int {
    if (d < 0) return 0
    val st = Repo.settings
    return when {
        d < st.goodPing / 2 -> 4
        d < st.goodPing -> 3
        d < st.okPing -> 2
        else -> 1
    }
}

@Composable
fun SignalBars(d: Long, modifier: Modifier = Modifier) {
    val c = ray
    val col = delayColor(d)
    val lvl = delayLevel(d)
    Row(modifier.height(11.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier.width(2.5.dp).height((2 + 2.25f * i).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i <= lvl) col else c.faint)
            )
        }
    }
}

/** Tabular latency number with a small unit. */
@Composable
fun LatencyText(d: Long, size: TextUnit = 17.sp) {
    val c = ray
    val col by animateColorAsState(delayColor(d), tween(300), label = "latency")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            when {
                d == -2L -> "—"
                d < 0 -> "✕"
                else -> "$d"
            },
            color = if (d >= 0) c.text else col, fontSize = size, fontWeight = FontWeight.Medium, style = TNum, maxLines = 1
        )
        if (d >= 0) {
            Text(" ms", color = c.hint, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun DelayBadge(d: Long, onClick: (() -> Unit)? = null) {
    val c = ray
    val col by animateColorAsState(delayColor(d), tween(300), label = "delay")
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "нет"
        else -> "$d ms"
    }
    var m: Modifier = Modifier
    if (onClick != null) m = m.bounceClick(onClick = onClick)
    val shape = c.shape(12.dp)
    Row(
        m.clip(shape).border(1.dp, c.stroke, shape).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(5.dp))
        Text(txt, color = c.text, fontSize = 12.sp, maxLines = 1, style = TNum)
    }
}

// ------------------------------------------------------------------ dialogs / misc

@Composable
fun AddSubDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val c = ray
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = c.shape(24.dp),
        title = { Text("Новая подписка", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                InputField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "URL подписки", leading = RayIcons.Link, mono = true)
                InputField(value = name, onValueChange = { name = it }, placeholder = "из подписки", label = "Название, если нужно своё")
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onAdd(url.trim(), name.trim()) }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = c.hint) }
        }
    )
}

fun maskSecret(v: String): String = if (v.length <= 8) "••••" else v.take(4) + "••••" + v.takeLast(4)
