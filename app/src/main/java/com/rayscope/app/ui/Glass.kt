/*
 * Liquid Glass for Jetpack Compose (used sparingly: floating tab bar and floating connect button only).
 *
 * The refraction (lens) and specular-rim shaders are adapted from Kyant's
 * AndroidLiquidGlass / Backdrop library (https://github.com/Kyant0/AndroidLiquidGlass),
 * Copyright 2025 Kyant, licensed under the Apache License, Version 2.0.
 *
 * The screen content is recorded into a GraphicsLayer (the "backdrop"). Each glass element
 * redraws the part of the backdrop under it through an AGSL RuntimeShader that bends pixels near
 * the rounded edge like a lens, then adds a light-angle dependent specular rim. No blur.
 * Real refraction needs Android 13+; older devices (or glass = off) get a solid frosted fallback.
 */
package com.rayscope.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Liquid Glass with an intensity from the look settings: 0 = frosted solid, 1 = soft lens, 2 = strong lens.
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Unspecified,
): Modifier {
    val c = ray
    val level = LocalUi.current.glass
    val backdrop = LocalBackdrop.current
    val layer = rememberGraphicsLayer()
    val pos = remember { GlassPos() }
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) Lens() else null }
    val refractionHeight = if (level >= 2) 16.dp else 10.dp
    val refractionAmount = if (level >= 2) 26.dp else 14.dp
    // same radius as the shadow shape, so the lens, fill and shadow line up for every corner setting
    val radius = cornerRadius * c.corner
    val shape = c.shape(cornerRadius)
    return this
        .shadow(
            elevation = if (c.dark) 6.dp else 10.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = if (c.dark) 0.40f else 0.14f)
        )
        .onGloballyPositioned { pos.origin = it.positionInRoot() }
        .drawBehind {
            val r = radius.toPx().coerceAtMost(size.minDimension / 2f)
            val cr = CornerRadius(r, r)
            val bd = backdrop
            var refracted = false
            if (level > 0 && bd != null && lens != null && Build.VERSION.SDK_INT >= 33) {
                val off = pos.origin - bd.origin
                layer.record {
                    translate(-off.x, -off.y) { drawLayer(bd.layer) }
                }
                layer.renderEffect = lens.effect(
                    size.width, size.height, r.coerceAtLeast(1f),
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

            val sw = 1.dp.toPx()
            val inset = Offset(sw / 2f, sw / 2f)
            val inner = Size(size.width - sw, size.height - sw)
            val icr = CornerRadius((r - sw / 2f).coerceAtLeast(0f))
            if (refracted && lens != null && Build.VERSION.SDK_INT >= 33) {
                drawRoundRect(
                    brush = ShaderBrush(lens.highlight(size.width, size.height, r.coerceAtLeast(1f), c.rim)),
                    topLeft = inset, size = inner, cornerRadius = icr, style = Stroke(sw)
                )
            } else {
                drawRoundRect(c.rim.copy(alpha = c.rim.alpha * 0.30f), topLeft = inset, size = inner, cornerRadius = icr, style = Stroke(sw))
            }
            drawRoundRect(c.stroke, cornerRadius = cr, style = Stroke(0.6.dp.toPx()))
        }
}

/** A glass surface that swells slightly while pressed. */
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
        targetValue = if (pressed && LocalUi.current.motion) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
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

/** True when the device can render real refraction. */
val glassSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

// ------------------------------------------------------------------ shaders

@RequiresApi(33)
private class Lens {
    private val refraction = RuntimeShader(REFRACTION_AGSL)
    private val rim = RuntimeShader(HIGHLIGHT_AGSL)
    private val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.25f) })

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
