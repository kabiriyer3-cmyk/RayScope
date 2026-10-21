/*
 * Liquid Glass for Jetpack Compose.
 *
 * The refraction (lens) and specular-rim shaders are adapted from Kyant's
 * AndroidLiquidGlass / Backdrop library (https://github.com/Kyant0/AndroidLiquidGlass),
 * Copyright 2025 Kyant, licensed under the Apache License, Version 2.0.
 *
 * The screen content is recorded into a GraphicsLayer (the "backdrop"). Each glass element
 * redraws the part of the backdrop under it through an AGSL RuntimeShader that bends pixels near
 * the rounded edge like a lens, then adds a light-angle dependent specular rim. No blur.
 * Glass is only used for the floating navigation layer (tab bar, toolbar, FAB), like on iOS.
 * Real refraction needs Android 13+; older devices get a solid fallback.
 */
package com.rayscope.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ backdrop

class Backdrop internal constructor(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Backdrop available to glass elements. Content inside a [BackdropSource] never sees its own backdrop. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun BackdropSource(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val bg = ray.bg
    CompositionLocalProvider(LocalBackdrop provides null) {
        Box(
            modifier
                .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
                .drawWithContent {
                    backdrop.layer.record {
                        drawRect(bg)
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(backdrop.layer)
                },
            content = content
        )
    }
}

// ------------------------------------------------------------------ glass

private class GlassPos {
    var origin by mutableStateOf(Offset.Zero)
}

/**
 * Clear Liquid Glass: refracts what is under the element (lens bending at the edges),
 * boosts saturation slightly, adds a thin surface tint and a specular rim lit from the top-left.
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Unspecified,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 20.dp,
): Modifier {
    val c = ray
    val backdrop = LocalBackdrop.current
    val layer = rememberGraphicsLayer()
    val pos = remember { GlassPos() }
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) Lens() else null }
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = if (c.dark) 8.dp else 14.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = if (c.dark) 0.35f else 0.16f)
        )
        .onGloballyPositioned { pos.origin = it.positionInRoot() }
        .drawBehind {
            val r = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
            val cr = CornerRadius(r, r)
            val bd = backdrop
            var refracted = false
            if (bd != null && lens != null && Build.VERSION.SDK_INT >= 33) {
                val off = pos.origin - bd.origin
                layer.record {
                    translate(-off.x, -off.y) { drawLayer(bd.layer) }
                }
                layer.renderEffect = lens.effect(
                    size.width, size.height, r,
                    refractionHeight.toPx().coerceAtMost(size.minDimension / 2f),
                    refractionAmount.toPx()
                )
                val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, cr)) }
                clipPath(path) { drawLayer(layer) }
                refracted = true
            }

            val fill = when {
                tint != Color.Unspecified -> tint
                refracted -> c.glassFill
                else -> c.glassFallback
            }
            drawRoundRect(fill, cornerRadius = cr)

            val sw = 1.2.dp.toPx()
            val inset = Offset(sw / 2f, sw / 2f)
            val inner = Size(size.width - sw, size.height - sw)
            val icr = CornerRadius((r - sw / 2f).coerceAtLeast(0f))
            if (refracted && lens != null && Build.VERSION.SDK_INT >= 33) {
                drawRoundRect(
                    brush = ShaderBrush(lens.highlight(size.width, size.height, r, c.rim)),
                    topLeft = inset, size = inner, cornerRadius = icr, style = Stroke(sw)
                )
            } else {
                drawRoundRect(c.rim.copy(alpha = c.rim.alpha * 0.30f), topLeft = inset, size = inner, cornerRadius = icr, style = Stroke(sw))
            }
            if (!c.dark) {
                drawRoundRect(Color.Black.copy(alpha = 0.06f), cornerRadius = cr, style = Stroke(0.5.dp.toPx()))
            }
        }
}

/** A glass surface that swells while pressed, like iOS 26 controls. */
@Composable
fun GlassSurface(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "glassPress"
    )
    var m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .liquidGlass(cornerRadius, tint)
    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    }
    Box(m, contentAlignment = contentAlignment, content = content)
}

// ------------------------------------------------------------------ page chrome

/** Top offset for page content: status bar + floating glass toolbar. */
@Composable
fun pageTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp

@Composable
fun navBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * iOS 26 style screen: content scrolls under floating Liquid Glass controls. The large title
 * lives in the content; once it scrolls away a compact glass title capsule pops in.
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
    Box(Modifier.fillMaxSize()) {
        BackdropSource(bd, Modifier.fillMaxSize()) { content() }
        CompositionLocalProvider(LocalBackdrop provides bd) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.bg))
            Box(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).height(44.dp)
            ) {
                if (onBack != null) {
                    GlassSurface(22.dp, Modifier.align(Alignment.CenterStart).size(44.dp), onClick = onBack) {
                        Icon(RayIcons.Back, contentDescription = "Назад", tint = c.text, modifier = Modifier.size(22.dp))
                    }
                }
                if (title != null) {
                    AnimatedVisibility(
                        visible = scrolled,
                        modifier = Modifier.align(Alignment.Center),
                        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.8f),
                        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.9f)
                    ) {
                        GlassSurface(19.dp, Modifier.height(38.dp).widthIn(max = 190.dp)) {
                            Text(
                                title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
                if (actions != null) {
                    GlassSurface(22.dp, Modifier.align(Alignment.CenterEnd).height(44.dp)) {
                        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                    }
                }
            }
            overlay()
        }
    }
}

@Composable
fun ActionIcon(
    icon: ImageVector,
    desc: String,
    tint: Color = ray.text,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = iconModifier.size(21.dp))
    }
}

@Composable
fun ActionText(text: String, color: Color = ray.accent, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------ shaders

@RequiresApi(33)
private class Lens {
    private val refraction = RuntimeShader(REFRACTION_AGSL)
    private val rim = RuntimeShader(HIGHLIGHT_AGSL)
    private val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.4f) })

    fun effect(w: Float, h: Float, r: Float, height: Float, amount: Float): androidx.compose.ui.graphics.RenderEffect {
        refraction.setFloatUniform("size", w, h)
        refraction.setFloatUniform("cornerRadius", r)
        refraction.setFloatUniform("refractionHeight", height.coerceAtLeast(1f))
        refraction.setFloatUniform("refractionAmount", -amount)
        val lensFx = android.graphics.RenderEffect.createRuntimeShaderEffect(refraction, "content")
        val colorFx = android.graphics.RenderEffect.createColorFilterEffect(saturate)
        return android.graphics.RenderEffect.createChainEffect(colorFx, lensFx).asComposeRenderEffect()
    }

    fun highlight(w: Float, h: Float, r: Float, color: Color): RuntimeShader {
        rim.setFloatUniform("size", w, h)
        rim.setFloatUniform("cornerRadius", r)
        rim.setFloatUniform("lightDir", -0.7071f, -0.7071f)
        rim.setColorUniform("color", color.toArgb())
        return rim
    }
}

private const val REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float cornerRadius;
uniform float refractionHeight;
uniform float refractionAmount;

float sdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, 0.0)) - r + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, 0.0) + 0.0001);
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float sd = sdRoundedRect(p, halfSize, cornerRadius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - (-sd) / refractionHeight) * refractionAmount;
    float gr = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(p, halfSize, gr) + 0.0001);
    return content.eval(coord + d * grad);
}
"""

private const val HIGHLIGHT_AGSL = """
uniform float2 size;
uniform float cornerRadius;
uniform float2 lightDir;
layout(color) uniform half4 color;

float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, 0.0) + 0.0001);
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float gr = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y));
    float2 g = gradSdRoundedRect(p, halfSize, gr);
    float k = pow(abs(dot(g, lightDir)), 1.6);
    return color * half(0.15 + 0.85 * k);
}
"""
