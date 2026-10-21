package com.rayscope.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Flags drawn in code. Windows has no flag emoji (it shows two letters instead),
// so every country we expect to meet in VPN subscriptions gets a tiny vector flag.

val FLAG_CODES: Set<String> = setOf(
    "RU", "UA", "BY", "KZ", "DE", "NL", "LU", "AT", "LV", "LT", "EE", "PL", "ID", "MC", "HU", "BG", "AM", "RS", "SK", "SI",
    "HR", "CO", "GE", "AZ", "UZ", "CZ", "FR", "IT", "IE", "BE", "RO", "MD", "MX", "ES", "PT", "GR", "CH", "DK", "SE", "NO",
    "FI", "IS", "GB", "US", "CA", "JP", "KR", "CN", "HK", "TW", "SG", "TR", "IL", "AE", "SA", "IN", "TH", "VN", "MY", "AU",
    "NZ", "BR", "AR", "CL", "ZA", "CY", "PH", "NG", "EG", "IR", "PK", "KG", "TJ", "QA", "EU",
)

private val W = Color.White
private val K = Color(0xFF141414)
private val UK_BLUE = Color(0xFF012169)
private val UK_RED = Color(0xFFC8102E)

private fun DrawScope.hs(vararg c: Color) {
    val h = size.height / c.size
    c.forEachIndexed { i, col -> drawRect(col, Offset(0f, i * h), Size(size.width, h + 1f)) }
}

private fun DrawScope.vs(vararg c: Color) {
    val w = size.width / c.size
    c.forEachIndexed { i, col -> drawRect(col, Offset(i * w, 0f), Size(w + 1f, size.height)) }
}

private fun DrawScope.hw(weights: List<Float>, colors: List<Color>) {
    val total = weights.sum()
    var y = 0f
    weights.forEachIndexed { i, wt ->
        val h = size.height * wt / total
        drawRect(colors[i], Offset(0f, y), Size(size.width, h + 1f))
        y += h
    }
}

private fun DrawScope.vw(weights: List<Float>, colors: List<Color>) {
    val total = weights.sum()
    var x = 0f
    weights.forEachIndexed { i, wt ->
        val w = size.width * wt / total
        drawRect(colors[i], Offset(x, 0f), Size(w + 1f, size.height))
        x += w
    }
}

private fun DrawScope.nordic(bg: Color, cross: Color, inner: Color? = null) {
    val w = size.width
    val h = size.height
    drawRect(bg)
    val t = h * 0.24f
    val cx = w * 0.38f
    drawRect(cross, Offset(cx - t / 2f, 0f), Size(t, h))
    drawRect(cross, Offset(0f, h / 2f - t / 2f), Size(w, t))
    if (inner != null) {
        val ti = t * 0.5f
        drawRect(inner, Offset(cx - ti / 2f, 0f), Size(ti, h))
        drawRect(inner, Offset(0f, h / 2f - ti / 2f), Size(w, ti))
    }
}

fun starPath(cx: Float, cy: Float, r: Float, points: Int = 5, inner: Float = 0.382f, rot: Float = -90f): Path {
    val path = Path()
    for (i in 0 until points * 2) {
        val rr = if (i % 2 == 0) r else r * inner
        val a = (rot + i * 180.0 / points) * PI / 180.0
        val x = cx + (cos(a) * rr).toFloat()
        val y = cy + (sin(a) * rr).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun poly(cx: Float, cy: Float, r: Float, pts: FloatArray): Path {
    val path = Path()
    var i = 0
    while (i + 1 < pts.size) {
        val x = cx + pts[i] * r
        val y = cy + pts[i + 1] * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        i += 2
    }
    path.close()
    return path
}

private val MAPLE = floatArrayOf(
    0f, -1f, 0.12f, -0.72f, 0.3f, -0.8f, 0.24f, -0.35f, 0.55f, -0.6f, 0.62f, -0.45f, 0.9f, -0.5f, 0.8f, -0.2f,
    0.95f, -0.1f, 0.5f, 0.25f, 0.56f, 0.42f, 0.08f, 0.36f, 0.08f, 0.8f, -0.08f, 0.8f, -0.08f, 0.36f, -0.56f, 0.42f,
    -0.5f, 0.25f, -0.95f, -0.1f, -0.8f, -0.2f, -0.9f, -0.5f, -0.62f, -0.45f, -0.55f, -0.6f, -0.24f, -0.35f,
    -0.3f, -0.8f, -0.12f, -0.72f,
)

private fun DrawScope.star(cx: Float, cy: Float, r: Float, col: Color, points: Int = 5, inner: Float = 0.382f) {
    drawPath(starPath(cx, cy, r, points, inner), col)
}

private fun DrawScope.crescent(cx: Float, cy: Float, r: Float, col: Color, bg: Color, shift: Float = 0.28f) {
    drawCircle(col, r, Offset(cx, cy))
    drawCircle(bg, r * 0.8f, Offset(cx + r * shift, cy))
}

private fun DrawScope.tri(col: Color, depth: Float) {
    val path = Path()
    path.moveTo(0f, 0f)
    path.lineTo(size.width * depth, size.height / 2f)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, col)
}

private fun DrawScope.shield(cx: Float, cy: Float, s: Float, fill: Color, mark: Color) {
    val l = cx - s / 2f
    val t = cy - s / 2f
    val path = Path()
    path.moveTo(l, t)
    path.lineTo(l + s, t)
    path.lineTo(l + s, t + s * 0.62f)
    path.lineTo(cx, t + s * 1.05f)
    path.lineTo(l, t + s * 0.62f)
    path.close()
    drawPath(path, fill)
    drawPath(path, mark, style = Stroke(width = s * 0.07f))
    drawRect(mark, Offset(cx - s * 0.07f, t + s * 0.18f), Size(s * 0.14f, s * 0.62f))
    drawRect(mark, Offset(l + s * 0.22f, t + s * 0.38f), Size(s * 0.56f, s * 0.13f))
}

private fun DrawScope.checker(cx: Float, cy: Float, s: Float) {
    val n = 5
    val c = s / n
    val l = cx - s / 2f
    val t = cy - s / 2f
    for (i in 0 until n) {
        for (j in 0 until n) {
            drawRect(if ((i + j) % 2 == 0) Color(0xFFFF0000) else W, Offset(l + i * c, t + j * c), Size(c + 0.5f, c + 0.5f))
        }
    }
    drawRect(Color(0xFF171796), Offset(l, t), Size(s, s), style = Stroke(width = s * 0.05f))
}

private fun DrawScope.unionJack(right: Float, bottom: Float) {
    clipRect(0f, 0f, right, bottom) {
        val w = right
        val h = bottom
        drawRect(UK_BLUE, Offset.Zero, Size(w, h))
        drawLine(W, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.2f)
        drawLine(W, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.2f)
        drawLine(UK_RED, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.08f)
        drawLine(UK_RED, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.08f)
        drawRect(W, Offset(w / 2f - h * 0.17f, 0f), Size(h * 0.34f, h))
        drawRect(W, Offset(0f, h / 2f - h * 0.17f), Size(w, h * 0.34f))
        drawRect(UK_RED, Offset(w / 2f - h * 0.1f, 0f), Size(h * 0.2f, h))
        drawRect(UK_RED, Offset(0f, h / 2f - h * 0.1f), Size(w, h * 0.2f))
    }
}

private fun DrawScope.rays(c: Offset, r1: Float, r2: Float, n: Int, col: Color, width: Float) {
    for (i in 0 until n) {
        val a = i * 2.0 * PI / n
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(col, Offset(c.x + ca * r1, c.y + sa * r1), Offset(c.x + ca * r2, c.y + sa * r2), strokeWidth = width)
    }
}

/** Draws the flag for [code] over the whole draw area. Returns false when we don't have that flag. */
fun DrawScope.drawFlag(code: String): Boolean {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    val mid = Offset(w / 2f, h / 2f)
    when (code) {
        "RU" -> hs(W, Color(0xFF0039A6), Color(0xFFD52B1E))
        "UA" -> hs(Color(0xFF0057B7), Color(0xFFFFD700))
        "BY" -> {
            val red = Color(0xFFC8313E)
            hw(listOf(2f, 1f), listOf(red, Color(0xFF4AA657)))
            drawRect(W, Offset.Zero, Size(w * 0.16f, h))
            val d = w * 0.045f
            val cx = w * 0.08f
            var y = h * 0.04f
            while (y < h) {
                val path = Path()
                path.moveTo(cx, y)
                path.lineTo(cx + d, y + d)
                path.lineTo(cx, y + 2 * d)
                path.lineTo(cx - d, y + d)
                path.close()
                drawPath(path, red)
                y += d * 2.7f
            }
        }
        "KZ" -> {
            val sun = Color(0xFFFEC50C)
            drawRect(Color(0xFF00AFCA))
            val c = Offset(w * 0.55f, h * 0.45f)
            rays(c, m * 0.19f, m * 0.28f, 16, sun, m * 0.03f)
            drawCircle(sun, m * 0.15f, c)
            drawRect(sun, Offset(w * 0.08f, 0f), Size(w * 0.05f, h))
        }
        "DE" -> hs(K, Color(0xFFDD0000), Color(0xFFFFCE00))
        "NL" -> hs(Color(0xFFAE1C28), W, Color(0xFF21468B))
        "LU" -> hs(Color(0xFFEF3340), W, Color(0xFF00A3E0))
        "AT" -> hs(Color(0xFFC8102E), W, Color(0xFFC8102E))
        "LV" -> hw(listOf(2f, 1f, 2f), listOf(Color(0xFF9E3039), W, Color(0xFF9E3039)))
        "LT" -> hs(Color(0xFFFDB913), Color(0xFF006A44), Color(0xFFC1272D))
        "EE" -> hs(Color(0xFF0072CE), K, W)
        "PL" -> hs(W, Color(0xFFDC143C))
        "ID", "MC" -> hs(Color(0xFFCE1126), W)
        "HU" -> hs(Color(0xFFCE2939), W, Color(0xFF477050))
        "BG" -> hs(W, Color(0xFF00966E), Color(0xFFD62612))
        "AM" -> hs(Color(0xFFD90012), Color(0xFF0033A0), Color(0xFFF2A800))
        "RS" -> {
            hs(Color(0xFFC6363C), Color(0xFF0C4076), W)
            shield(w * 0.32f, h * 0.48f, m * 0.3f, Color(0xFFC6363C), W)
        }
        "SK" -> {
            hs(W, Color(0xFF0B4EA2), Color(0xFFEE1C25))
            shield(w * 0.34f, h * 0.48f, m * 0.36f, Color(0xFFEE1C25), W)
        }
        "SI" -> {
            hs(W, Color(0xFF005DA4), Color(0xFFED1C24))
            shield(w * 0.3f, h * 0.36f, m * 0.26f, Color(0xFF005DA4), W)
        }
        "HR" -> {
            hs(Color(0xFFFF0000), W, Color(0xFF171796))
            checker(w / 2f, h / 2f, m * 0.36f)
        }
        "CO" -> hw(listOf(2f, 1f, 1f), listOf(Color(0xFFFCD116), Color(0xFF003893), Color(0xFFCE1126)))
        "GE" -> {
            val red = Color(0xFFFF0000)
            drawRect(W)
            val t = m * 0.16f
            drawRect(red, Offset(w / 2f - t / 2f, 0f), Size(t, h))
            drawRect(red, Offset(0f, h / 2f - t / 2f), Size(w, t))
            val a = m * 0.13f
            val b = m * 0.05f
            listOf(Offset(0.22f, 0.22f), Offset(0.78f, 0.22f), Offset(0.22f, 0.78f), Offset(0.78f, 0.78f)).forEach { o ->
                val cx = w * o.x
                val cy = h * o.y
                drawRect(red, Offset(cx - b / 2f, cy - a / 2f), Size(b, a))
                drawRect(red, Offset(cx - a / 2f, cy - b / 2f), Size(a, b))
            }
        }
        "AZ" -> {
            hs(Color(0xFF00B5E2), Color(0xFFEF3340), Color(0xFF509E2F))
            crescent(w * 0.46f, h / 2f, m * 0.12f, W, Color(0xFFEF3340))
            star(w * 0.6f, h / 2f, m * 0.065f, W, 8, 0.5f)
        }
        "UZ" -> {
            val blue = Color(0xFF0099B5)
            hw(listOf(10f, 1f, 10f, 1f, 10f), listOf(blue, Color(0xFFCE1126), W, Color(0xFFCE1126), Color(0xFF1EB53A)))
            crescent(w * 0.2f, h * 0.16f, m * 0.1f, W, blue)
            for (i in 0 until 3) star(w * (0.38f + i * 0.11f), h * 0.16f, m * 0.035f, W)
        }
        "CZ" -> {
            hs(W, Color(0xFFD7141A))
            tri(Color(0xFF11457E), 0.5f)
        }
        "FR" -> vs(Color(0xFF0055A4), W, Color(0xFFEF4135))
        "IT" -> vs(Color(0xFF009246), W, Color(0xFFCE2B37))
        "IE" -> vs(Color(0xFF169B62), W, Color(0xFFFF883E))
        "BE" -> vs(K, Color(0xFFFDDA24), Color(0xFFEF3340))
        "RO" -> vs(Color(0xFF002B7F), Color(0xFFFCD116), Color(0xFFCE1126))
        "MD" -> {
            vs(Color(0xFF0046AE), Color(0xFFFFD200), Color(0xFFCC092F))
            drawRect(Color(0xFF8C5A2B), Offset(w * 0.43f, h * 0.37f), Size(w * 0.14f, h * 0.26f))
        }
        "MX" -> {
            vs(Color(0xFF006847), W, Color(0xFFCE1126))
            drawCircle(Color(0xFF8C5A2B), m * 0.1f, mid)
            drawCircle(Color(0xFF006847), m * 0.11f, mid, style = Stroke(width = m * 0.025f))
        }
        "ES" -> {
            hw(listOf(1f, 2f, 1f), listOf(Color(0xFFAA151B), Color(0xFFF1BF00), Color(0xFFAA151B)))
            drawRect(Color(0xFFAA151B), Offset(w * 0.24f, h * 0.38f), Size(w * 0.12f, h * 0.24f))
        }
        "PT" -> {
            vw(listOf(2f, 3f), listOf(Color(0xFF006600), Color(0xFFFF0000)))
            val c = Offset(w * 0.4f, h / 2f)
            drawCircle(Color(0xFFFFE000), m * 0.17f, c)
            drawCircle(Color(0xFFFF0000), m * 0.11f, c)
            drawCircle(W, m * 0.065f, c)
        }
        "GR" -> {
            val blue = Color(0xFF0D5EAF)
            val sh = h / 9f
            for (i in 0 until 9) drawRect(if (i % 2 == 0) blue else W, Offset(0f, i * sh), Size(w, sh + 1f))
            val cs = sh * 5f
            drawRect(blue, Offset.Zero, Size(cs, cs))
            drawRect(W, Offset(cs / 2f - sh / 2f, 0f), Size(sh, cs))
            drawRect(W, Offset(0f, cs / 2f - sh / 2f), Size(cs, sh))
        }
        "CH" -> {
            drawRect(Color(0xFFDA291C))
            val a = m * 0.56f
            val b = m * 0.18f
            drawRect(W, Offset(w / 2f - b / 2f, h / 2f - a / 2f), Size(b, a))
            drawRect(W, Offset(w / 2f - a / 2f, h / 2f - b / 2f), Size(a, b))
        }
        "DK" -> nordic(Color(0xFFC8102E), W)
        "SE" -> nordic(Color(0xFF006AA7), Color(0xFFFECC00))
        "NO" -> nordic(Color(0xFFBA0C2F), W, Color(0xFF00205B))
        "FI" -> nordic(W, Color(0xFF002F6C))
        "IS" -> nordic(Color(0xFF02529C), W, Color(0xFFDC1E35))
        "GB" -> unionJack(w, h)
        "US" -> {
            val red = Color(0xFFB22234)
            val sh = h / 13f
            for (i in 0 until 13) drawRect(if (i % 2 == 0) red else W, Offset(0f, i * sh), Size(w, sh + 1f))
            val cw = w * 0.5f
            val ch = sh * 7f
            drawRect(Color(0xFF3C3B6E), Offset.Zero, Size(cw, ch))
            for (r in 0 until 4) {
                for (c in 0 until 5) {
                    drawCircle(W, m * 0.022f, Offset(cw * (c + 0.5f) / 5f, ch * (r + 0.5f) / 4f))
                }
            }
        }
        "CA" -> {
            val red = Color(0xFFD80621)
            vw(listOf(1f, 2f, 1f), listOf(red, W, red))
            drawPath(poly(w / 2f, h * 0.48f, m * 0.3f, MAPLE), red)
        }
        "JP" -> {
            drawRect(W)
            drawCircle(Color(0xFFBC002D), m * 0.3f, mid)
        }
        "KR" -> {
            drawRect(W)
            val r = m * 0.24f
            val red = Color(0xFFCD2E3A)
            val blue = Color(0xFF0047A0)
            drawArc(red, 180f, 180f, true, Offset(mid.x - r, mid.y - r), Size(2 * r, 2 * r))
            drawArc(blue, 0f, 180f, true, Offset(mid.x - r, mid.y - r), Size(2 * r, 2 * r))
            drawCircle(red, r / 2f, Offset(mid.x - r / 2f, mid.y))
            drawCircle(blue, r / 2f, Offset(mid.x + r / 2f, mid.y))
            val k = 0.7071f
            for (dx in listOf(-1f, 1f)) {
                for (dy in listOf(-1f, 1f)) {
                    val ox = mid.x + dx * m * 0.36f
                    val oy = mid.y + dy * m * 0.36f
                    for (j in -1..1) {
                        val qx = ox + dx * k * j * m * 0.05f
                        val qy = oy + dy * k * j * m * 0.05f
                        val px = -dy * k * m * 0.075f
                        val py = dx * k * m * 0.075f
                        drawLine(K, Offset(qx - px, qy - py), Offset(qx + px, qy + py), strokeWidth = m * 0.03f)
                    }
                }
            }
        }
        "CN" -> {
            val y = Color(0xFFFFDE00)
            drawRect(Color(0xFFEE1C25))
            star(w * 0.26f, h * 0.3f, m * 0.15f, y)
            star(w * 0.48f, h * 0.13f, m * 0.05f, y)
            star(w * 0.57f, h * 0.24f, m * 0.05f, y)
            star(w * 0.57f, h * 0.38f, m * 0.05f, y)
            star(w * 0.48f, h * 0.49f, m * 0.05f, y)
        }
        "HK" -> {
            val red = Color(0xFFDE2910)
            drawRect(red)
            for (i in 0 until 5) {
                val a = (-90.0 + i * 72.0) * PI / 180.0
                drawCircle(W, m * 0.11f, Offset(mid.x + (cos(a) * m * 0.13f).toFloat(), mid.y + (sin(a) * m * 0.13f).toFloat()))
            }
            drawCircle(red, m * 0.045f, mid)
        }
        "TW" -> {
            val blue = Color(0xFF000095)
            drawRect(Color(0xFFFE0000))
            drawRect(blue, Offset.Zero, Size(w * 0.5f, h * 0.5f))
            val c = Offset(w * 0.25f, h * 0.25f)
            star(c.x, c.y, m * 0.16f, W, 12, 0.6f)
            drawCircle(blue, m * 0.08f, c)
            drawCircle(W, m * 0.065f, c)
        }
        "SG" -> {
            val red = Color(0xFFEF3340)
            hs(red, W)
            crescent(w * 0.24f, h * 0.25f, m * 0.14f, W, red, 0.32f)
            for (i in 0 until 5) {
                val a = (-90.0 + i * 72.0) * PI / 180.0
                star(w * 0.37f + (cos(a) * m * 0.08f).toFloat(), h * 0.25f + (sin(a) * m * 0.08f).toFloat(), m * 0.03f, W)
            }
        }
        "TR" -> {
            val red = Color(0xFFE30A17)
            drawRect(red)
            crescent(w * 0.4f, h / 2f, m * 0.25f, W, red, 0.25f)
            star(w * 0.67f, h / 2f, m * 0.1f, W)
        }
        "IL" -> {
            val blue = Color(0xFF0038B8)
            drawRect(W)
            drawRect(blue, Offset(0f, h * 0.1f), Size(w, h * 0.13f))
            drawRect(blue, Offset(0f, h * 0.77f), Size(w, h * 0.13f))
            val r = m * 0.18f
            drawPath(poly(mid.x, mid.y, r, floatArrayOf(0f, -1f, 0.866f, 0.5f, -0.866f, 0.5f)), blue, style = Stroke(width = m * 0.035f))
            drawPath(poly(mid.x, mid.y, r, floatArrayOf(0f, 1f, 0.866f, -0.5f, -0.866f, -0.5f)), blue, style = Stroke(width = m * 0.035f))
        }
        "AE" -> {
            hs(Color(0xFF00732F), W, K)
            drawRect(Color(0xFFFF0000), Offset.Zero, Size(w * 0.27f, h))
        }
        "SA" -> {
            drawRect(Color(0xFF006C35))
            for (i in 0 until 4) drawRect(W, Offset(w * (0.2f + i * 0.16f), h * 0.3f), Size(w * 0.1f, h * 0.06f))
            drawRect(W, Offset(w * 0.2f, h * 0.42f), Size(w * 0.6f, h * 0.05f))
            drawLine(W, Offset(w * 0.2f, h * 0.66f), Offset(w * 0.78f, h * 0.66f), strokeWidth = m * 0.035f)
            drawRect(W, Offset(w * 0.74f, h * 0.6f), Size(w * 0.04f, h * 0.12f))
        }
        "IN" -> {
            val navy = Color(0xFF000080)
            hs(Color(0xFFFF9933), W, Color(0xFF138808))
            drawCircle(navy, m * 0.13f, mid, style = Stroke(width = m * 0.025f))
            rays(mid, 0f, m * 0.13f, 12, navy, m * 0.012f)
        }
        "TH" -> hw(listOf(1f, 1f, 2f, 1f, 1f), listOf(Color(0xFFA51931), W, Color(0xFF2D2A4A), W, Color(0xFFA51931)))
        "VN" -> {
            drawRect(Color(0xFFDA251D))
            star(w / 2f, h * 0.53f, m * 0.3f, Color(0xFFFFFF00))
        }
        "MY" -> {
            val red = Color(0xFFCC0001)
            val blue = Color(0xFF010066)
            val gold = Color(0xFFFFCC00)
            val sh = h / 14f
            for (i in 0 until 14) drawRect(if (i % 2 == 0) red else W, Offset(0f, i * sh), Size(w, sh + 1f))
            drawRect(blue, Offset.Zero, Size(w * 0.55f, sh * 8f))
            crescent(w * 0.2f, sh * 4f, m * 0.17f, gold, blue, 0.3f)
            star(w * 0.4f, sh * 4f, m * 0.11f, gold, 14, 0.45f)
        }
        "AU", "NZ" -> {
            drawRect(UK_BLUE)
            unionJack(w * 0.5f, h * 0.5f)
            if (code == "AU") {
                star(w * 0.25f, h * 0.76f, m * 0.11f, W, 7, 0.45f)
                star(w * 0.76f, h * 0.2f, m * 0.05f, W, 7, 0.45f)
                star(w * 0.63f, h * 0.48f, m * 0.05f, W, 7, 0.45f)
                star(w * 0.87f, h * 0.43f, m * 0.05f, W, 7, 0.45f)
                star(w * 0.76f, h * 0.82f, m * 0.05f, W, 7, 0.45f)
            } else {
                listOf(Offset(0.76f, 0.22f), Offset(0.63f, 0.5f), Offset(0.88f, 0.45f), Offset(0.76f, 0.8f)).forEach { o ->
                    star(w * o.x, h * o.y, m * 0.075f, W)
                    star(w * o.x, h * o.y, m * 0.05f, UK_RED)
                }
            }
        }
        "BR" -> {
            drawRect(Color(0xFF009C3B))
            val d = Path()
            d.moveTo(w * 0.5f, h * 0.12f)
            d.lineTo(w * 0.92f, h * 0.5f)
            d.lineTo(w * 0.5f, h * 0.88f)
            d.lineTo(w * 0.08f, h * 0.5f)
            d.close()
            drawPath(d, Color(0xFFFFDF00))
            val r = m * 0.22f
            drawCircle(Color(0xFF002776), r, mid)
            val circle = Path()
            circle.addOval(Rect(mid, r))
            clipPath(circle) {
                drawCircle(W, m * 0.42f, Offset(w * 0.38f, h * 0.9f), style = Stroke(width = m * 0.04f))
            }
        }
        "AR" -> {
            val sky = Color(0xFF74ACDF)
            val sun = Color(0xFFF6B40E)
            hs(sky, W, sky)
            rays(mid, m * 0.07f, m * 0.13f, 16, sun, m * 0.02f)
            drawCircle(sun, m * 0.08f, mid)
        }
        "CL" -> {
            hs(W, Color(0xFFD52B1E))
            drawRect(Color(0xFF0039A6), Offset.Zero, Size(w * 0.4f, h * 0.5f))
            star(w * 0.2f, h * 0.25f, m * 0.11f, W)
        }
        "ZA" -> {
            hs(Color(0xFFE03C31), Color(0xFF001489))
            val tip = Offset(w * 0.4f, h / 2f)
            listOf(W to h * 0.34f, Color(0xFF007749) to h * 0.2f).forEach { (col, t) ->
                drawLine(col, Offset(-w * 0.05f, -h * 0.05f), tip, strokeWidth = t)
                drawLine(col, Offset(-w * 0.05f, h * 1.05f), tip, strokeWidth = t)
                drawRect(col, Offset(tip.x, tip.y - t / 2f), Size(w - tip.x, t))
                drawCircle(col, t / 2f, tip)
            }
            val y = Path()
            y.moveTo(0f, h * 0.16f)
            y.lineTo(w * 0.31f, h / 2f)
            y.lineTo(0f, h * 0.84f)
            y.close()
            drawPath(y, Color(0xFFFFB612))
            val b = Path()
            b.moveTo(0f, h * 0.24f)
            b.lineTo(w * 0.23f, h / 2f)
            b.lineTo(0f, h * 0.76f)
            b.close()
            drawPath(b, K)
        }
        "CY" -> {
            drawRect(W)
            drawOval(Color(0xFFD57800), Offset(w * 0.22f, h * 0.3f), Size(w * 0.56f, h * 0.24f))
            drawOval(Color(0xFF4E5B31), Offset(w * 0.3f, h * 0.62f), Size(w * 0.18f, h * 0.08f))
            drawOval(Color(0xFF4E5B31), Offset(w * 0.52f, h * 0.62f), Size(w * 0.18f, h * 0.08f))
        }
        "PH" -> {
            hs(Color(0xFF0038A8), Color(0xFFCE1126))
            tri(W, 0.6f)
            drawCircle(Color(0xFFFCD116), m * 0.09f, Offset(w * 0.2f, h / 2f))
        }
        "NG" -> vs(Color(0xFF008751), W, Color(0xFF008751))
        "EG" -> {
            hs(Color(0xFFCE1126), W, K)
            drawRect(Color(0xFFC09300), Offset(w * 0.44f, h * 0.38f), Size(w * 0.12f, h * 0.24f))
        }
        "IR" -> {
            hs(Color(0xFF239F40), W, Color(0xFFDA0000))
            drawCircle(Color(0xFFDA0000), m * 0.08f, mid, style = Stroke(width = m * 0.03f))
        }
        "PK" -> {
            val g = Color(0xFF01411C)
            drawRect(g)
            drawRect(W, Offset.Zero, Size(w * 0.25f, h))
            crescent(w * 0.6f, h / 2f, m * 0.25f, W, g, 0.3f)
            star(w * 0.74f, h * 0.42f, m * 0.08f, W)
        }
        "KG" -> {
            val red = Color(0xFFE8112D)
            val sun = Color(0xFFFFEF00)
            drawRect(red)
            rays(mid, m * 0.12f, m * 0.3f, 40, sun, m * 0.02f)
            drawCircle(sun, m * 0.17f, mid)
            drawCircle(red, m * 0.12f, mid)
            drawCircle(sun, m * 0.1f, mid, style = Stroke(width = m * 0.02f))
        }
        "TJ" -> {
            hw(listOf(2f, 3f, 2f), listOf(Color(0xFFCC0000), W, Color(0xFF006600)))
            drawCircle(Color(0xFFF8C300), m * 0.07f, mid)
        }
        "QA" -> {
            drawRect(Color(0xFF8A1538))
            val path = Path()
            path.moveTo(0f, 0f)
            path.lineTo(w * 0.3f, 0f)
            val n = 9
            val sh = h / n
            for (i in 0 until n) {
                path.lineTo(w * 0.42f, sh * (i + 0.5f))
                path.lineTo(w * 0.3f, sh * (i + 1))
            }
            path.lineTo(0f, h)
            path.close()
            drawPath(path, W)
        }
        "EU" -> {
            drawRect(Color(0xFF003399))
            for (i in 0 until 12) {
                val a = i * PI / 6.0
                star(mid.x + (cos(a) * m * 0.3f).toFloat(), mid.y + (sin(a) * m * 0.3f).toFloat(), m * 0.055f, Color(0xFFFFCC00))
            }
        }
        else -> return false
    }
    return true
}

/** Guesses the country of a server from its name when there is no flag emoji in it. */
object Countries {
    private val NAMES: List<Pair<String, String>> = listOf(
        "индонез" to "ID", "indonesia" to "ID", "jakarta" to "ID",
        "росси" to "RU", "russia" to "RU", "москва" to "RU", "moscow" to "RU", "петербург" to "RU", "petersburg" to "RU",
        "украин" to "UA", "ukrain" to "UA", "киев" to "UA", "kyiv" to "UA",
        "беларус" to "BY", "belarus" to "BY", "минск" to "BY",
        "казах" to "KZ", "kazakh" to "KZ", "алмат" to "KZ", "almaty" to "KZ", "астана" to "KZ",
        "герман" to "DE", "german" to "DE", "frankfurt" to "DE", "франкфурт" to "DE", "berlin" to "DE", "берлин" to "DE",
        "нидерланд" to "NL", "netherlands" to "NL", "голланд" to "NL", "holland" to "NL", "amsterdam" to "NL", "амстердам" to "NL",
        "люксембург" to "LU", "luxembourg" to "LU",
        "австри" to "AT", "austria" to "AT", "vienna" to "AT",
        "латви" to "LV", "latvia" to "LV", "riga" to "LV", "рига" to "LV",
        "литв" to "LT", "lithuania" to "LT", "vilnius" to "LT",
        "эстони" to "EE", "estonia" to "EE", "tallinn" to "EE", "таллин" to "EE",
        "польш" to "PL", "poland" to "PL", "warsaw" to "PL", "варшав" to "PL",
        "монако" to "MC", "monaco" to "MC",
        "венгри" to "HU", "hungary" to "HU", "budapest" to "HU",
        "болгари" to "BG", "bulgaria" to "BG", "sofia" to "BG",
        "армени" to "AM", "armenia" to "AM", "ереван" to "AM", "yerevan" to "AM",
        "серби" to "RS", "serbia" to "RS", "belgrade" to "RS",
        "словаки" to "SK", "slovakia" to "SK",
        "словени" to "SI", "slovenia" to "SI",
        "хорвати" to "HR", "croatia" to "HR",
        "колумби" to "CO", "colombia" to "CO",
        "грузи" to "GE", "georgia" to "GE", "тбилиси" to "GE", "tbilisi" to "GE",
        "азербайджан" to "AZ", "azerbaijan" to "AZ", "баку" to "AZ", "baku" to "AZ",
        "узбек" to "UZ", "uzbek" to "UZ", "ташкент" to "UZ", "tashkent" to "UZ",
        "чехи" to "CZ", "czech" to "CZ", "prague" to "CZ", "прага" to "CZ",
        "франци" to "FR", "france" to "FR", "paris" to "FR", "париж" to "FR",
        "итали" to "IT", "italy" to "IT", "milan" to "IT", "милан" to "IT",
        "ирланди" to "IE", "ireland" to "IE", "dublin" to "IE",
        "бельги" to "BE", "belgium" to "BE", "brussels" to "BE",
        "румыни" to "RO", "romania" to "RO", "bucharest" to "RO",
        "молдов" to "MD", "moldova" to "MD", "кишин" to "MD",
        "мексик" to "MX", "mexico" to "MX",
        "испани" to "ES", "spain" to "ES", "madrid" to "ES", "мадрид" to "ES",
        "португали" to "PT", "portugal" to "PT", "lisbon" to "PT",
        "греци" to "GR", "greece" to "GR", "athens" to "GR",
        "швейцари" to "CH", "switzerland" to "CH", "zurich" to "CH",
        "дания" to "DK", "denmark" to "DK", "copenhagen" to "DK",
        "швеци" to "SE", "sweden" to "SE", "stockholm" to "SE", "стокгольм" to "SE",
        "норвеги" to "NO", "norway" to "NO", "oslo" to "NO",
        "финлянди" to "FI", "finland" to "FI", "helsinki" to "FI", "хельсинки" to "FI",
        "исланди" to "IS", "iceland" to "IS",
        "великобритани" to "GB", "britain" to "GB", "united kingdom" to "GB", "англи" to "GB", "england" to "GB", "london" to "GB", "лондон" to "GB",
        "сша" to "US", "америк" to "US", "united states" to "US", "new york" to "US", "нью-йорк" to "US", "los angeles" to "US",
        "chicago" to "US", "dallas" to "US", "miami" to "US", "seattle" to "US", "silicon valley" to "US",
        "канад" to "CA", "canada" to "CA", "toronto" to "CA",
        "япони" to "JP", "japan" to "JP", "tokyo" to "JP", "токио" to "JP",
        "корея" to "KR", "корей" to "KR", "korea" to "KR", "seoul" to "KR", "сеул" to "KR",
        "китай" to "CN", "china" to "CN",
        "гонконг" to "HK", "hong kong" to "HK", "hongkong" to "HK",
        "тайван" to "TW", "taiwan" to "TW",
        "сингапур" to "SG", "singapore" to "SG",
        "турци" to "TR", "turkey" to "TR", "türkiye" to "TR", "istanbul" to "TR", "стамбул" to "TR",
        "израил" to "IL", "israel" to "IL",
        "эмират" to "AE", "оаэ" to "AE", "emirates" to "AE", "dubai" to "AE", "дубай" to "AE",
        "саудов" to "SA", "saudi" to "SA",
        "индия" to "IN", "india" to "IN", "mumbai" to "IN",
        "таиланд" to "TH", "thailand" to "TH", "bangkok" to "TH",
        "вьетнам" to "VN", "vietnam" to "VN",
        "малайзи" to "MY", "malaysia" to "MY",
        "австрали" to "AU", "australia" to "AU", "sydney" to "AU",
        "новая зеланди" to "NZ", "new zealand" to "NZ",
        "бразили" to "BR", "brazil" to "BR",
        "аргентин" to "AR", "argentina" to "AR",
        "чили" to "CL", "chile" to "CL",
        "юар" to "ZA", "south africa" to "ZA",
        "кипр" to "CY", "cyprus" to "CY",
        "филиппин" to "PH", "philippines" to "PH",
        "нигери" to "NG", "nigeria" to "NG",
        "египет" to "EG", "egypt" to "EG",
        "иран" to "IR", "iran" to "IR",
        "пакистан" to "PK", "pakistan" to "PK",
        "кыргыз" to "KG", "киргиз" to "KG", "kyrgyz" to "KG", "бишкек" to "KG",
        "таджик" to "TJ", "tajik" to "TJ",
        "катар" to "QA", "qatar" to "QA",
        "европ" to "EU", "europe" to "EU",
    )

    fun guess(name: String): String {
        if (name.isBlank()) return ""
        val low = name.lowercase()
        for ((k, v) in NAMES) if (low.contains(k)) return v
        for (t in name.split(Regex("[^A-Za-z]+"))) {
            if (t == "USA") return "US"
            if (t == "UAE") return "AE"
            if (t.length == 2 && t == t.uppercase()) {
                val c = if (t == "UK") "GB" else t
                if (c in FLAG_CODES) return c
            }
        }
        return ""
    }
}
