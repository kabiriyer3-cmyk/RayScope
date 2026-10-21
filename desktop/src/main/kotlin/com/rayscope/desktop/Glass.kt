package com.rayscope.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.ColorFilter as SkColorFilter
import org.jetbrains.skia.ColorMatrix as SkColorMatrix

// Liquid Glass for desktop, same idea as the phone version (Kyant0/AndroidLiquidGlass, Apache-2.0):
// the page is recorded into a graphics layer (the backdrop), glass elements redraw the part of the
// backdrop under them through a lens shader (edge refraction + slight dispersion), saturation and blur.

class Backdrop {
    var layer: GraphicsLayer? = null
    var coords: LayoutCoordinates? = null
    internal var frames = 0

    /** bumped after every recording so glass elements know they must redraw */
    val version = mutableIntStateOf(0)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Records its content into [backdrop] and draws it as usual. Glass must be placed outside of it (as a sibling). */
@Composable
fun BackdropSource(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    backdrop.layer = layer
    Box(
        modifier
            .onGloballyPositioned { backdrop.coords = it }
            .graphicsLayer { }
            .drawWithContent {
                layer.record {
                    if (background.alpha > 0f) drawRect(background)
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
                backdrop.frames += 1
                backdrop.version.intValue = backdrop.frames
            },
        content = content
    )
}

private class GlassSpot {
    var coords: LayoutCoordinates? = null
}

/** A glass panel. Level and colors come from the theme (Настройки → Оформление → Liquid Glass). */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    backdrop: Backdrop? = LocalBackdrop.current,
    tint: Color = Color.Unspecified,
    shadow: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = pal
    val level = p.glass
    val shape = RoundedCornerShape(radius)
    val spot = remember { GlassSpot() }
    val rimBrush = remember(p.dark, p.rim) {
        if (p.dark) {
            Brush.linearGradient(listOf(p.rim, p.rim.copy(alpha = 0.05f), p.rim.copy(alpha = 0.2f)))
        } else {
            Brush.linearGradient(listOf(Color.White, Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.12f)))
        }
    }
    val sheen = remember(p.dark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.08f else 0.30f), Color.Transparent))
    }
    val live = level > 0 && backdrop != null
    val fill = when {
        tint != Color.Unspecified -> tint
        !live -> p.glassFallback
        level == 2 -> p.glassFill.copy(alpha = p.glassFill.alpha * 0.7f)
        else -> p.glassFill
    }
    val base = if (shadow) {
        modifier.shadow(
            if (live) 18.dp else 10.dp, shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.5f)
        )
    } else {
        modifier
    }
    Box(base.clip(shape).onGloballyPositioned { spot.coords = it }, contentAlignment = contentAlignment) {
        if (live && backdrop != null) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        renderEffect = Lens.effect(size.width, size.height, radius.toPx(), level, density)
                        clip = true
                        this.shape = shape
                    }
                    .drawBehind {
                        val v = backdrop.version.intValue
                        val src = backdrop.layer
                        val sc = backdrop.coords
                        val me = spot.coords
                        if (v >= 0 && src != null && sc != null && me != null && sc.isAttached && me.isAttached) {
                            val o = sc.localPositionOf(me, Offset.Zero)
                            translate(-o.x, -o.y) { drawLayer(src) }
                        }
                    }
            )
        }
        Box(Modifier.matchParentSize().background(fill).background(sheen).border(1.dp, rimBrush, shape))
        content()
    }
}

/** Pressable glass: grows a little on hover and press, like the phone version. */
@Composable
fun GlassSurface(
    radius: Dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val press by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (press) 1.05f else if (hover) 1.02f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "glassScale"
    )
    var m = modifier.graphicsLayer {
        scaleX = sc
        scaleY = sc
    }
    if (onClick != null) {
        m = m.hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).hand()
    }
    GlassBox(m, radius, backdrop, tint, contentAlignment = Alignment.Center, content = content)
}

/** Skia lens: refraction near the rounded edges, slight color dispersion, saturation and blur. */
object Lens {
    private const val SKSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float amount;

half4 main(float2 p) {
    float2 hs = size * 0.5;
    float2 q = abs(p - hs) - (hs - float2(radius));
    float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float e = clamp(1.0 + d / depth, 0.0, 1.0);
    float2 mq = max(q, float2(0.0));
    float2 n = float2(0.0, 1.0);
    if (length(mq) > 0.001) {
        n = normalize(mq);
    } else if (q.x > q.y) {
        n = float2(1.0, 0.0);
    }
    n = n * sign(p - hs);
    float2 off = n * (e * e) * amount;
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 c = content.eval(clamp(p - off, lo, hi));
    half4 r = content.eval(clamp(p - off * 1.12, lo, hi));
    half4 b = content.eval(clamp(p - off * 0.88, lo, hi));
    return half4(r.r, c.g, b.b, c.a);
}
"""

    private val runtime: RuntimeEffect? by lazy {
        try {
            RuntimeEffect.makeForShader(SKSL)
        } catch (t: Throwable) {
            Log.add("Liquid Glass: шейдер недоступен (" + t.message + "), будет простое размытие")
            null
        }
    }
    private val cache = HashMap<String, RenderEffect>()

    fun effect(w: Float, h: Float, radius: Float, level: Int, density: Float): RenderEffect? {
        if (w < 2f || h < 2f || level <= 0) return null
        val key = "" + w.toInt() + "x" + h.toInt() + ":" + radius.toInt() + ":" + level + ":" + density
        cache[key]?.let { return it }
        val sigma = (if (level == 2) 1.5f else 7f) * density
        val fx = try {
            build(w, h, radius, level, density, sigma)
        } catch (t: Throwable) {
            null
        } ?: BlurEffect(sigma * 1.6f, sigma * 1.6f, TileMode.Clamp)
        if (cache.size > 48) cache.clear()
        cache[key] = fx
        return fx
    }

    private fun saturation(s: Float): FloatArray {
        val r = 0.2126f * (1f - s)
        val g = 0.7152f * (1f - s)
        val b = 0.0722f * (1f - s)
        return floatArrayOf(
            r + s, g, b, 0f, 0f,
            r, g + s, b, 0f, 0f,
            r, g, b + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun build(w: Float, h: Float, radius: Float, level: Int, density: Float, sigma: Float): RenderEffect? {
        val e = runtime ?: return null
        val blur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null, null)
        val sat = SkColorFilter.makeMatrix(SkColorMatrix(*saturation(if (level == 2) 1.35f else 1.25f)))
        val colored = ImageFilter.makeColorFilter(sat, blur, null)
        val b = RuntimeShaderBuilder(e)
        val m = minOf(w, h)
        b.uniform("size", w, h)
        b.uniform("radius", minOf(radius, m / 2f))
        b.uniform("depth", m * (if (level == 2) 0.5f else 0.38f))
        b.uniform("amount", (if (level == 2) 16f else 9f) * density)
        return ImageFilter.makeRuntimeShader(b, "content", colored).asComposeRenderEffect()
    }
}
