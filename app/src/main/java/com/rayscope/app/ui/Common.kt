package com.rayscope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ palette (iOS / Telegram)

val Good = Color(0xFF30D158)
val Mid = Color(0xFFFF9F0A)
val Bad = Color(0xFFFF453A)
val Idle = Color(0xFF8E8E93)
val Orange = Color(0xFFFF9F0A)
val Blue = Color(0xFF0A84FF)

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
    glassFill = Color.White.copy(alpha = 0.04f),
    glassFallback = Color(0xEB1C1C1E),
    rim = Color.White.copy(alpha = 0.70f),
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
    glassFill = Color.White.copy(alpha = 0.14f),
    glassFallback = Color(0xEBFFFFFF),
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
    footer: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ray
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), color = c.hint, fontSize = 13.sp, letterSpacing = 0.2.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )
        }
        Column(Modifier.fillMaxWidth().card(c).padding(contentPadding), content = content)
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
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(ray.divider))
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

@Composable
fun NavRow(title: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.hint, fontSize = 15.sp)
        Text("  ›", color = c.hint, fontSize = 20.sp)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.hint, fontSize = 12.sp)
        }
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
}

@Composable
fun Chip(text: String, selected: Boolean, onCard: Boolean = false, onClick: () -> Unit) {
    val c = ray
    val bg = when {
        selected -> c.accent
        onCard -> c.field
        else -> c.card
    }
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
) {
    val c = ray
    val tint = if (color != Color.Unspecified) color else c.accent
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) tint else tint.copy(alpha = if (c.dark) 0.16f else 0.10f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (primary) Color.White else tint, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = ray
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (onCard) c.field else c.card)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
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

@Composable
fun FlagBox(flag: String, size: Dp = 40.dp) {
    val c = ray
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.field),
        contentAlignment = Alignment.Center
    ) {
        Text(flag.ifEmpty { "\uD83C\uDF10" }, fontSize = (size.value * 0.52f).sp)
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
    val col = delayColor(d)
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "нет"
        else -> "$d ms"
    }
    var m: Modifier = Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.15f))
    if (onClick != null) m = m.clickable { onClick() }
    Row(m.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
        title = { Text("Новая подписка") },
        text = {
            Column {
                InputField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "URL подписки")
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
