package com.rayscope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ palette

val Good = Color(0xFF30D158)
val Mid = Color(0xFFFFB020)
val Bad = Color(0xFFFF453A)
val Idle = Color(0xFF8E8E93)
val Orange = Color(0xFFFF9F0A)
val Blue = Color(0xFF3E9BFF)

/** Space reserved at the bottom of tab screens for the floating glass tab bar. */
val BottomInset = 120.dp

@Immutable
data class RayColors(
    val dark: Boolean,
    val bg: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val rim: Color,
    val rimSoft: Color,
    val divider: Color,
    val field: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
)

private val DarkRay = RayColors(
    dark = true,
    bg = Color(0xFF07090E),
    text = Color(0xFFF5F7FA),
    hint = Color(0xFF8D93A0),
    accent = Color(0xFF3E9BFF),
    glassTop = Color.White.copy(alpha = 0.12f),
    glassBottom = Color.White.copy(alpha = 0.035f),
    rim = Color.White.copy(alpha = 0.45f),
    rimSoft = Color.White.copy(alpha = 0.05f),
    divider = Color.White.copy(alpha = 0.08f),
    field = Color.White.copy(alpha = 0.07f),
    blob1 = Color(0xFF2F6BFF),
    blob2 = Color(0xFF8A3FFC),
    blob3 = Color(0xFF00BFA5),
)

private val LightRay = RayColors(
    dark = false,
    bg = Color(0xFFEFF1F6),
    text = Color(0xFF0B0D12),
    hint = Color(0xFF6E7380),
    accent = Color(0xFF3390EC),
    glassTop = Color.White.copy(alpha = 0.80f),
    glassBottom = Color.White.copy(alpha = 0.40f),
    rim = Color.White,
    rimSoft = Color.White.copy(alpha = 0.20f),
    divider = Color.Black.copy(alpha = 0.07f),
    field = Color.Black.copy(alpha = 0.05f),
    blob1 = Color(0xFF6AB2FF),
    blob2 = Color(0xFFC79BFF),
    blob3 = Color(0xFF7BE6D3),
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
            background = r.bg, onBackground = r.text, surface = Color(0xFF15181F), onSurface = r.text,
            surfaceVariant = Color(0xFF1E222B), onSurfaceVariant = r.hint,
            surfaceContainerLowest = Color(0xFF0B0D12), surfaceContainerLow = Color(0xFF12151B),
            surfaceContainer = Color(0xFF181B22), surfaceContainerHigh = Color(0xFF1E222B),
            surfaceContainerHighest = Color(0xFF262A34), outline = Color(0xFF3A3F4B),
        )
    } else {
        lightColorScheme(
            primary = r.accent, onPrimary = Color.White, secondary = r.accent,
            background = r.bg, onBackground = r.text, surface = Color.White, onSurface = r.text,
            surfaceVariant = Color(0xFFE6E8EE), onSurfaceVariant = r.hint,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FB),
            surfaceContainer = Color(0xFFF6F7FA), surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFF0F1F5), outline = Color(0xFFC9CDD6),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalRay provides r, LocalContentColor provides r.text) {
            content()
        }
    }
}

// ------------------------------------------------------------------ liquid glass

/**
 * Clear "liquid glass" surface: almost transparent fill (the colorful backdrop shows through),
 * a diagonal specular sheen and a bright rim light that fades around the edge. No blur.
 */
fun Modifier.glass(c: RayColors, shape: Shape, tint: Color = Color.Unspecified): Modifier {
    val base = if (c.dark) this else this.shadow(
        elevation = 10.dp, shape = shape, clip = false,
        ambientColor = Color(0x18000000), spotColor = Color(0x26000000)
    )
    val hasTint = tint != Color.Unspecified
    val top = if (hasTint) tint.copy(alpha = if (c.dark) 0.30f else 0.22f) else c.glassTop
    val bottom = if (hasTint) tint.copy(alpha = if (c.dark) 0.08f else 0.06f) else c.glassBottom
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (c.dark) 0.08f else 0.30f),
        0.42f to Color.Transparent,
        1f to Color.White.copy(alpha = if (c.dark) 0.03f else 0.12f)
    )
    val rimBrush = Brush.linearGradient(
        listOf(c.rim, c.rimSoft, c.rimSoft, c.rim.copy(alpha = c.rim.alpha * 0.55f))
    )
    return base
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .background(sheen)
        .border(1.dp, rimBrush, shape)
}

@Composable
fun RayBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = ray
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                val a = if (c.dark) 0.50f else 0.42f
                val p1 = Offset(w * 0.08f, h * 0.06f)
                val p2 = Offset(w * 0.98f, h * 0.38f)
                val p3 = Offset(w * 0.18f, h * 0.88f)
                drawCircle(Brush.radialGradient(listOf(c.blob1.copy(alpha = a), Color.Transparent), center = p1, radius = w * 0.85f), radius = w * 0.85f, center = p1)
                drawCircle(Brush.radialGradient(listOf(c.blob2.copy(alpha = a * 0.8f), Color.Transparent), center = p2, radius = w * 0.75f), radius = w * 0.75f, center = p2)
                drawCircle(Brush.radialGradient(listOf(c.blob3.copy(alpha = a * 0.7f), Color.Transparent), center = p3, radius = w * 0.8f), radius = w * 0.8f, center = p3)
            },
        content = content
    )
}

// ------------------------------------------------------------------ building blocks

@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = ray
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = c.text, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp)) {
            Text(
                title, color = c.text, fontSize = if (onBack == null) 26.sp else 18.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = c.hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun GlassIconButton(onClick: () -> Unit, enabled: Boolean = true, size: Dp = 38.dp, content: @Composable () -> Unit) {
    val c = ray
    Box(
        Modifier.size(size).glass(c, CircleShape).alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun Section(
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ray
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), color = c.hint, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 14.dp, bottom = 5.dp)
            )
        }
        Column(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(20.dp)).padding(contentPadding), content = content)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f))
        Text(
            v.ifEmpty { "—" },
            color = if (valueColor == Color.Unspecified) c.hint else valueColor,
            fontSize = 14.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.hint, fontSize = 14.sp)
        Text("  ›", color = c.hint, fontSize = 18.sp)
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
            Text(title, color = c.text, fontSize = 15.sp)
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
                uncheckedTrackColor = if (c.dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.Transparent,
            )
        )
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = ray
    val shape = RoundedCornerShape(50)
    val m = if (selected) {
        Modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.85f), c.accent)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape)
    } else {
        Modifier.glass(c, shape)
    }
    Box(m.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = if (selected) Color.White else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val c = ray
    val shape = RoundedCornerShape(16.dp)
    val tint = if (color != Color.Unspecified) color else c.accent
    val base = if (primary) {
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.82f), tint)))
            .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.06f))), shape)
    } else {
        modifier.glass(c, shape)
    }
    Box(
        base.alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (primary) Color.White else tint, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = ray
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, color = c.hint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.field)
                .border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) Text(placeholder, color = c.hint, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
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
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.field),
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
    val shape = RoundedCornerShape(50)
    var m: Modifier = Modifier.clip(shape).background(col.copy(alpha = 0.16f))
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
                GlassField(value = url, onValueChange = { url = it }, placeholder = "https://…", label = "URL подписки")
                Spacer(Modifier.height(4.dp))
                GlassField(value = name, onValueChange = { name = it }, placeholder = "из подписки", label = "Название (необязательно)")
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
