package com.rayscope.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Good = Color(0xFF2ECC71)
val Mid = Color(0xFFF1C40F)
val Bad = Color(0xFFE74C3C)
val Idle = Color(0xFF6B7A90)
val Orange = Color(0xFFE67E22)
val Blue = Color(0xFF8AB4FF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CF5C8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F4D3B),
    onPrimaryContainer = Color(0xFFB8FFE4),
    secondary = Blue,
    onSecondary = Color(0xFF0A2350),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFFA9B9D0),
    surfaceContainerLowest = Color(0xFF080E19),
    surfaceContainerLow = Color(0xFF0F1A2B),
    surfaceContainer = Color(0xFF111D31),
    surfaceContainerHigh = Color(0xFF162338),
    surfaceContainerHighest = Color(0xFF1E2D47),
    outline = Color(0xFF3A4C6B),
)

@Composable
fun RayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

fun delayColor(d: Long): Color = when {
    d == -2L -> Idle
    d < 0 -> Bad
    d < 300 -> Good
    d < 800 -> Mid
    else -> Orange
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun KV(k: String, v: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            k,
            Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            v.ifEmpty { "—" },
            Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
fun KVIf(k: String, v: String) {
    if (v.isNotEmpty()) KV(k, v)
}

@Composable
fun DelayBadge(d: Long, onClick: (() -> Unit)? = null) {
    val c = delayColor(d)
    val txt = when {
        d == -2L -> "—"
        d < 0 -> "✕ нет"
        else -> "$d ms"
    }
    val m = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(color = c.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp), modifier = m) {
        Text(
            txt,
            color = c,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
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
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL подписки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
