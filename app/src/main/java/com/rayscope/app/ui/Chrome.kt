package com.rayscope.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Top offset for page content: status bar + top bar. */
@Composable
fun pageTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp

@Composable
fun navBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Screen scaffold. Content scrolls under a flat top bar that fades in a solid background + hairline
 * once [scrolled]; the large title lives in the content. Glass is reserved for [overlay] elements
 * (floating buttons), which refract the page content under them.
 */
@Composable
fun Page(
    title: String? = null,
    scrolled: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = ray
    val bd = rememberBackdrop()
    val bar by animateFloatAsState(if (scrolled) 1f else 0f, tween(220), label = "topBar")
    val bg = c.bg
    val stroke = c.stroke
    Box(Modifier.fillMaxSize()) {
        BackdropSource(bd, Modifier.fillMaxSize()) { content() }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .drawBehind {
                        drawRect(bg.copy(alpha = 0.97f * bar))
                        drawLine(
                            stroke.copy(alpha = stroke.alpha * bar),
                            Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f
                        )
                    }
                    .padding(horizontal = 12.dp)
            ) {
                if (onBack != null) {
                    ActionIcon(RayIcons.Back, "Назад", modifier = Modifier.align(Alignment.CenterStart), onClick = onBack)
                }
                if (title != null) {
                    Text(
                        title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 200.dp)
                            .graphicsLayer {
                                alpha = bar
                                translationY = (1f - bar) * 10.dp.toPx()
                            }
                    )
                }
                if (actions != null) {
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides bd) { overlay() }
    }
}

/** Round flat icon button used in top bars. */
@Composable
fun ActionIcon(
    icon: ImageVector,
    desc: String,
    tint: Color = ray.text,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = ray
    Box(
        modifier.size(40.dp).clip(CircleShape).background(c.raised).border(1.dp, c.stroke, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = iconModifier.size(19.dp))
    }
}

@Composable
fun ActionText(text: String, color: Color = ray.accent, onClick: () -> Unit) {
    val c = ray
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.height(40.dp).clip(shape).background(c.raised).border(1.dp, c.stroke, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
