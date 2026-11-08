package com.rayscope.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rayscope.fluid.LiveIcon
import com.rayscope.fluid.rememberIconFx

/** Top offset for page content: status bar + top bar. */
@Composable
fun pageTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp

@Composable
fun navBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Screen scaffold. Content scrolls under a flat top bar; once [scrolled], the bar gets a solid
 * background, a hairline and the small title. [overlay] holds floating elements above the content.
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
    val bar by animateFloatAsState(if (scrolled) 1f else 0f, tween(160), label = "topBar")
    val bg = c.bg
    val stroke = c.stroke
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) { content() }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .drawBehind {
                        drawRect(bg.copy(alpha = bar))
                        drawLine(
                            stroke.copy(alpha = stroke.alpha * bar),
                            Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f
                        )
                    }
                    .padding(horizontal = 8.dp)
            ) {
                if (onBack != null) {
                    ActionIcon(RayIcons.Back, "Назад", modifier = Modifier.align(Alignment.CenterStart), onClick = onBack)
                }
                if (title != null) {
                    Text(
                        title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 220.dp)
                            .graphicsLayer { alpha = bar }
                    )
                }
                if (actions != null) {
                    Row(
                        Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
        overlay()
    }
}

/** Borderless icon button used in top bars. */
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
    val fx = rememberIconFx()
    Box(
        modifier.size(40.dp).clip(c.shape(16.dp)).bounceClick(enabled = enabled) {
            onClick()
            fx.fire(icon)
        },
        contentAlignment = Alignment.Center
    ) {
        LiveIcon(
            icon, if (enabled) tint else tint.copy(alpha = 0.35f), iconModifier.size(20.dp), contentDescription = desc,
            play = fx.plays, done = fx.done, doneTint = Good, motion = LocalUi.current.motion
        )
    }
}

@Composable
fun ActionText(text: String, color: Color = ray.accent, onClick: () -> Unit) {
    val c = ray
    val shape = c.shape(16.dp)
    Box(
        Modifier.height(34.dp).clip(shape).border(1.dp, c.stroke, shape)
            .bounceClick(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (color == Bad) Bad else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
