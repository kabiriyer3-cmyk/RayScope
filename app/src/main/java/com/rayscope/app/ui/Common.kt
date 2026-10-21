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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ palette (iOS / Telegram)

val Good = Color(0xFF30D158)
val Mid = Color(0xFFFF9F0A)
val Bad = Color(0xFFFF453A)
val Idle = Color(0xFF8E8E93)
val Orange = Color(0xFFFF9F0A)
val Blue = Color(0xFF0A84FF)
val Purple = Color(0xFFAF52DE)
val Indigo = Color(0xFF5E5CE6)
val Teal = Color(0xFF30B0C7)
val Pink = Color(0xFFFF375F)
val Gray = Color(0xFF8E8E93)
val Yellow = Color(0xFFFFCC00)

@Immutable
data class RayColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val divider: Color,
    val field: Color,
    val glassFill: Color,
    val glassFallback: Color,
    val rim: Color,
)

private val DarkRay = RayColors(
    dark = true,
    bg = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    text = Color.White,
    hint = Color(0xFF8E8E93),
    accent = Color(0xFF3E88F7),
    divider = Color(0xFF2C2C2E),
    field = Color(0xFF2C2C2E),
    glassFill = Color(0xFF1C1C1E).copy(alpha = 0.38f),
    glassFallback = Color(0xF01C1C1E),
    rim = Color.White.copy(alpha = 0.60f),
)

private val LightRay = RayColors(
    dark = false,
    bg = Color(0xFFF2F2F7),
    card = Color.White,
    text = Color.Black,
    hint = Color(0xFF8E8E93),
    accent = Color(0xFF007AFF),
    divider = Color(0xFFE5E5EA),
    field = Color(0xFFF2F2F7),
    glassFill = Color.White.copy(alpha = 0.50f),
    glassFallback = Color(0xF2FFFFFF),
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
            primary = r.accent, onPrimary = Color.White, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.card, onSurface = r.text,
            surfaceVariant = r.field, onSurfaceVariant = r.hint,
            surfaceContainerLowest = r.card, surfaceContainerLow = r.card, surfaceContainer = r.card,
            surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = Color(0xFF3A3A3C),
        )
    } else {
        lightColorScheme(
            primary = r.accent, onPrimary = Color.White, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = r.card, onSurface = r.text,
            surfaceVariant = r.field, onSurfaceVariant = r.hint,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF2F2F7),
            outline = Color(0xFFC6C6C8),
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

fun Modifier.card(c: RayColors, radius: Dp = 14.dp): Modifier =
    this.clip(RoundedCornerShape(radius)).background(c.card)

@Composable
fun TabBottomSpacer() {
    Spacer(Modifier.navigationBarsPadding().height(100.dp))
}

// ------------------------------------------------------------------ motion helpers

/** Press feedback: the element shrinks a little and springs back (no ripple). */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
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
        delay(index * 45L)
        a.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
    }
    return this.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 22.dp.toPx()
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
        Text(t, color = color, fontSize = fontSize, fontWeight = weight, maxLines = 1)
    }
}

// ------------------------------------------------------------------ content components (flat)

@Composable
fun LargeTitle(title: String, subtitle: String? = null, big: Boolean = true) {
    val c = ray
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)) {
        Text(
            title, color = c.text, fontSize = if (big) 32.sp else 24.sp, fontWeight = FontWeight.Bold,
            maxLines = if (big) 1 else 2, overflow = TextOverflow.Ellipsis, lineHeight = if (big) 38.sp else 29.sp
        )
        if (subtitle != null) {
            Text(subtitle, color = c.hint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    padded: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ray
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), color = c.hint, fontSize = 13.sp, letterSpacing = 0.2.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth().card(c).padding(if (padded) contentPadding else PaddingValues(0.dp)),
            content = content
        )
        if (footer != null) {
            Text(footer, color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Section(title = title, content = content)
}

@Composable
fun RowDivider(inset: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(ray.divider))
}

@Composable
fun KV(k: String, v: String, valueColor: Color = Color.Unspecified) {
    val c = ray
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f))
        Text(
            v.ifEmpty { "—" },
            color = if (valueColor == Color.Unspecified) c.hint else valueColor,
            fontSize = 15.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
fun KVIf(k: String, v: String) {
    if (v.isNotEmpty()) KV(k, v)
}

/** iOS settings style coloured square with a white glyph. */
@Composable
fun IconBadge(
    icon: ImageVector,
    bg: Color,
    size: Dp = 30.dp,
    iconSize: Dp = 18.dp,
    iconModifier: Modifier = Modifier,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = iconModifier.size(iconSize))
    }
}

/** Telegram / iOS grouped row: icon badge, title, optional value, trailing control and chevron. */
@Composable
fun SettingRow(
    icon: ImageVector,
    iconBg: Color,
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
    val dividerColor = c.divider
    var m: Modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)
    if (onClick != null) m = m.clickable(enabled = enabled, onClick = onClick)
    Row(
        m
            .drawBehind {
                if (divider) {
                    val y = size.height - 0.25.dp.toPx()
                    drawLine(dividerColor, Offset(58.dp.toPx(), y), Offset(size.width, y), 0.5.dp.toPx())
                }
            }
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, iconBg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.hint, fontSize = 12.sp, maxLines = 2)
        }
        if (value != null) {
            Text(value, color = c.hint, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        if (trailing != null) trailing()
        if (chevron) {
            Icon(RayIcons.Chevron, contentDescription = null, tint = c.hint.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ray
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.scale(0.85f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Good,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (c.dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
            uncheckedBorderColor = Color.Transparent,
        )
    )
}

@Composable
fun SettingSwitch(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    divider: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(
        icon = icon, iconBg = iconBg, title = title, subtitle = subtitle, divider = divider,
        onClick = { onChange(!checked) },
        trailing = { IosSwitch(checked, onChange) }
    )
}

/** iOS segmented control with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ray
    BoxWithConstraints(
        modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(c.field).padding(2.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = w * selected,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
            label = "segment"
        )
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(if (c.dark) Color(0xFF636366) else Color.White)
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
                        t, color = c.text, fontSize = 13.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onCard: Boolean = false, onClick: () -> Unit) {
    val c = ray
    val bg by animateColorAsState(
        when {
            selected -> c.accent
            onCard -> c.field
            else -> c.card
        },
        tween(200), label = "chipBg"
    )
    val fg by animateColorAsState(if (selected) Color.White else c.text, tween(200), label = "chipFg")
    Box(
        Modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
    val fg = if (primary) Color.White else tint
    Row(
        modifier
            .bounceClick(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) tint else tint.copy(alpha = if (c.dark) 0.16f else 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = iconModifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = ray
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (onCard) c.field else c.card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = c.hint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = c.hint, fontSize = 15.sp, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FlagBox(flag: String, size: Dp = 40.dp) {
    val c = ray
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(c.field),
        contentAlignment = Alignment.Center
    ) {
        if (flag.isEmpty()) {
            Icon(RayIcons.Globe, contentDescription = null, tint = c.hint, modifier = Modifier.size(size * 0.5f))
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
            RayIcons.Check, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(14.dp).graphicsLayer {
                scaleX = s
                scaleY = s
            }
        )
    }
}

@Composable
fun SlimProgress(progress: Float, modifier: Modifier = Modifier) {
    val c = ray
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(c.divider)) {
        Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.accent))
    }
}

fun delayColor(d: Long): Color = when {
    d == -2L -> Idle
    d < 0 -> Bad
    d < 300 -> Good
    d < 800 -> Mid
    else -> Orange
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
        Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun AddSubDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(RayIcons.Package, Indigo, 44.dp, 24.dp) },
        title = { Text("Новая подписка") },
        text = {
            Column {
                InputField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "URL подписки", leading = RayIcons.Link)
                InputField(value = name, onValueChange = { name = it }, placeholder = "из подписки", label = "Название (необязательно)")
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onAdd(url.trim(), name.trim()) }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

fun maskSecret(v: String): String = if (v.length <= 8) "••••" else v.take(4) + "••••" + v.takeLast(4)
