package com.rayscope.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayscope.app.data.Repo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ------------------------------------------------------------------ palette ("Scope": ink + signal cyan)

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

/** Dark ink used on top of bright fills (green / cyan / amber). */
val InkOn = Color(0xFF051116)

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
    val glassFill: Color,
    val glassFallback: Color,
    val rim: Color,
) {
    val card: Color get() = surface
    val field: Color get() = raised
    val divider: Color get() = stroke
}

private val DarkRay = RayColors(
    dark = true,
    bg = Color(0xFF0A0C10),
    surface = Color(0xFF13161C),
    raised = Color(0xFF1C2028),
    stroke = Color.White.copy(alpha = 0.07f),
    text = Color(0xFFF1F3F7),
    hint = Color(0xFF8A93A2),
    faint = Color.White.copy(alpha = 0.10f),
    accent = Cyan,
    onAccent = InkOn,
    glassFill = Color(0xFF14171D).copy(alpha = 0.58f),
    glassFallback = Color(0xF2171A21),
    rim = Color.White.copy(alpha = 0.42f),
)

private val LightRay = RayColors(
    dark = false,
    bg = Color(0xFFF3F4F6),
    surface = Color.White,
    raised = Color(0xFFEDEFF3),
    stroke = Color.Black.copy(alpha = 0.07f),
    text = Color(0xFF0E1116),
    hint = Color(0xFF667080),
    faint = Color.Black.copy(alpha = 0.08f),
    accent = Color(0xFF0091BA),
    onAccent = Color.White,
    glassFill = Color.White.copy(alpha = 0.66f),
    glassFallback = Color(0xF5FFFFFF),
    rim = Color.White,
)

val LocalRay = staticCompositionLocalOf { DarkRay }

val ray: RayColors
    @Composable get() = LocalRay.current

@Composable
fun RayTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val r = if (dark) DarkRay else LightRay
    val scheme = if (dark) {
        darkColorScheme(
            primary = r.accent, onPrimary = r.onAccent, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.surface, onSurface = r.text,
            surfaceVariant = r.raised, onSurfaceVariant = r.hint,
            surfaceContainerLowest = r.surface, surfaceContainerLow = r.surface, surfaceContainer = r.surface,
            surfaceContainerHigh = r.raised, surfaceContainerHighest = Color(0xFF262B35),
            outline = Color(0xFF2E3440),
        )
    } else {
        lightColorScheme(
            primary = r.accent, onPrimary = r.onAccent, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.surface, onSurface = r.text,
            surfaceVariant = r.raised, onSurfaceVariant = r.hint,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White, surfaceContainerHighest = r.raised,
            outline = Color(0xFFD0D4DB),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalRay provides r, LocalContentColor provides r.text) {
            content()
        }
    }
}

@Composable
fun RayBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(ray.bg), content = content)
}

fun Modifier.card(c: RayColors, radius: Dp = 20.dp, stroke: Color = c.stroke, width: Dp = 1.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(c.surface).border(width, stroke, shape)
}

fun Modifier.bottomLine(color: Color, inset: Dp = 62.dp, show: Boolean = true): Modifier =
    if (!show) this else this.drawBehind {
        drawLine(color, Offset(inset.toPx(), size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
    }

@Composable
fun TabBottomSpacer() {
    Spacer(Modifier.navigationBarsPadding().height(112.dp))
}

// ------------------------------------------------------------------ motion helpers

/** Press feedback: the element shrinks a little and springs back (no ripple). */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "bounce"
    )
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Staggered entrance: fade + rise, used on cards when a screen opens. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        a.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 280f))
    }
    return this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 18.dp.toPx()
    }
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

/** Text that rolls vertically when its value changes (speed counters, timers). */
@Composable
fun TickerText(text: String, color: Color, fontSize: TextUnit, weight: FontWeight = FontWeight.SemiBold) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(180)))
        },
        label = "ticker"
    ) { t ->
        Text(t, color = color, fontSize = fontSize, fontWeight = weight, maxLines = 1, style = TNum)
    }
}

// ------------------------------------------------------------------ typography blocks

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, big: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, color = c.text, fontSize = if (big) 34.sp else 26.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp, lineHeight = if (big) 40.sp else 31.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = c.hint, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val c = ray
    Text(
        text.uppercase(), color = if (color == Color.Unspecified) c.hint else color,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier
    )
}

/** Small static tag: protocol, transport, feature flags. */
@Composable
fun Tag(text: String, color: Color = Color.Unspecified) {
    val c = ray
    val col = if (color == Color.Unspecified) c.hint else color
    Text(
        text, color = col, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        maxLines = 1, letterSpacing = 0.4.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ------------------------------------------------------------------ containers

/** Titled card group. Rows inside handle their own padding. */
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
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CapsLabel(title, Modifier.weight(1f))
                if (trailing != null) trailing()
            }
        }
        Column(Modifier.fillMaxWidth().card(c), content = content)
        if (footer != null) {
            Text(
                footer, color = c.hint, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)
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
            fontWeight = FontWeight.Medium,
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

/** Tinted rounded square with a coloured glyph. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    iconModifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(if (solid) tint else tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (solid) (if (tint.luminance() > 0.5f) InkOn else Color.White) else tint,
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
        m.bottomLine(c.stroke, if (icon != null) 62.dp else 16.dp, divider)
            .padding(horizontal = 14.dp, vertical = if (subtitle != null) 11.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconTile(icon, tint)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = c.hint, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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

/** Custom switch: accent track, soft thumb. */
@Composable
fun Toggle(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = ray
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "toggle")
    val track = lerp(c.raised, c.accent, t)
    val thumb = lerp(if (c.dark) Color(0xFF8E97A6) else Color.White, if (c.dark) InkOn else Color.White, t)
    Box(
        Modifier.size(width = 46.dp, height = 28.dp).alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape).background(track).border(1.dp, c.stroke, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = 18.dp * t).size(22.dp)
                .shadow(if (c.dark) 0.dp else 2.dp, CircleShape)
                .clip(CircleShape).background(thumb)
        )
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
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).bottomLine(c.stroke, if (icon != null) 62.dp else 16.dp, divider)) {
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
            modifier = Modifier.padding(start = if (icon != null) 58.dp else 12.dp, end = 12.dp).height(34.dp)
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
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).bottomLine(c.stroke, if (icon != null) 62.dp else 16.dp, divider)) {
        PrefRow(icon, tint, title, subtitle, divider = false)
        FlowRow(
            Modifier.fillMaxWidth().padding(start = if (icon != null) 62.dp else 16.dp, end = 14.dp, bottom = 12.dp),
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
    Column(Modifier.fillMaxWidth().bottomLine(c.stroke, if (icon != null) 62.dp else 16.dp, divider)) {
        PrefRow(icon, tint, title, subtitle, divider = false)
        InputField(
            value = value, onValueChange = onChange, placeholder = placeholder, mono = mono,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.padding(start = if (icon != null) 62.dp else 16.dp, end = 14.dp, bottom = 12.dp)
        )
    }
}

// ------------------------------------------------------------------ controls

/** Segmented control with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ray
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(38.dp).clip(shape).background(c.raised).border(1.dp, c.stroke, shape).padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = w * selected,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
            label = "segment"
        )
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(if (c.dark) 0.dp else 2.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(if (c.dark) Color(0xFF2A303B) else Color.White)
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
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1
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
    val bg by animateColorAsState(if (selected) c.accent else c.raised, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, tween(200), label = "chipFg")
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.bounceClick(enabled = enabled, onClick = onClick).clip(shape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.stroke, shape)
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = fg, fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = TNum)
        }
    }
}

@Composable
fun MiniAction(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

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
    val tint = if (color != Color.Unspecified) color else c.accent
    val fg = if (primary) (if (tint.luminance() > 0.45f) InkOn else Color.White) else tint
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .bounceClick(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (primary) tint else tint.copy(alpha = if (c.dark) 0.14f else 0.10f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = iconModifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            CapsLabel(label, Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp))
        }
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(if (onCard) c.raised else c.surface).border(1.dp, c.stroke, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = c.hint, modifier = Modifier.size(17.dp))
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
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size).clip(shape).background(c.raised).border(1.dp, c.stroke, shape),
        contentAlignment = Alignment.Center
    ) {
        if (flag.isEmpty()) {
            Icon(RayIcons.Globe, contentDescription = null, tint = c.hint, modifier = Modifier.size(size * 0.48f))
        } else {
            Text(flag, fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
fun CheckCircle(checked: Boolean) {
    val c = ray
    val bg by animateColorAsState(if (checked) c.accent else Color.Transparent, tween(180), label = "ccBg")
    val s by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "ccScale"
    )
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(bg)
            .border(1.5.dp, if (checked) c.accent else c.hint.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            RayIcons.Check, contentDescription = null, tint = c.onAccent,
            modifier = Modifier.size(14.dp).graphicsLayer {
                scaleX = s
                scaleY = s
            }
        )
    }
}

@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val c = ray
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(c.raised)) {
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
    Row(modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier.width(3.dp).height((3 * i).dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i <= lvl) col else c.faint)
            )
        }
    }
}

/** Big tabular latency number with a small unit. */
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
            color = col, fontSize = size, fontWeight = FontWeight.Bold, style = TNum, maxLines = 1
        )
        if (d >= 0) {
            Text(" ms", color = c.hint, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun DelayBadge(d: Long, onClick: (() -> Unit)? = null) {
    val col by animateColorAsState(delayColor(d), tween(300), label = "delay")
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "нет"
        else -> "$d ms"
    }
    var m: Modifier = Modifier
    if (onClick != null) m = m.bounceClick(onClick = onClick)
    Row(
        m.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(5.dp))
        Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TNum)
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
        icon = { IconTile(RayIcons.Package, Indigo, 46.dp, 24.dp) },
        title = { Text("Новая подписка", color = c.text) },
        text = {
            Column {
                InputField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "URL подписки", leading = RayIcons.Link, mono = true)
                InputField(value = name, onValueChange = { name = it }, placeholder = "из подписки", label = "Название (необязательно)")
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
