/*
 * RayScope lettermark (1.2): an R drawn as one continuous line. Shared by Android and Windows.
 */
package com.rayscope.fluid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Minimise glyph for the frameless Windows window, drawn like the rest of [Glyphs]. */
val MinusGlyph: ImageVector by lazy { glyph("minus", "M6 12h12") }

/**
 * RayScope lettermark: an R drawn as one continuous line. Stem, bowl and leg flow into each other with no joint and
 * no sharp corner; the bowl curls back into the leg and leaves a gap at the stem, the same gap the interface keeps
 * between the connect tile and the panel. 14-unit line, 10 degree slant, 100-unit box, glyph centred on (50, 50),
 * 72 units tall. [PATH_SMALL] is the optical cut for 16-24 px: a heavier line and a wider gap.
 * Same geometry as the Android launcher icon and desktop/launcher/make_icon.py.
 */
object RayLogo {
    const val PATH = "M31.89 79L40 33C40.49 30.24 43.12 28 45.89 28L63.39 28C66.42 28 68.45 30.46 67.92 33.5C67.38 36.54 64.48 39 61.45 39C55.53 39 49.52 42.26 45.82 47.49C42.12 52.71 41.34 59.04 43.78 63.95L53.23 82.91C54.83 86.12 59.03 86.97 62.62 84.8C66.2 82.64 67.82 78.29 66.22 75.09L59.69 61.97C58.84 60.27 58.94 58.12 59.97 56.18C60.99 54.24 62.82 52.74 64.89 52.13C74.61 49.28 81.97 40.12 82.21 30.59C82.45 21.05 75.5 14 65.85 14L48.35 14C37.86 14 27.86 22.51 26 33L17.89 79C17.21 82.87 19.79 86 23.66 86C27.53 86 31.21 82.87 31.89 79Z"
    const val PATH_SMALL = "M30.52 78.25L38.37 33.75C38.78 31.4 41.02 29.5 43.37 29.5L65.37 29.5C67.99 29.5 69.74 31.63 69.28 34.25C68.82 36.87 66.32 39 63.69 39C57.5 39 51.21 42.42 47.34 47.89C43.47 53.35 42.64 59.98 45.2 65.12L53.9 82.58C55.67 86.13 60.32 87.07 64.29 84.68C68.26 82.28 70.05 77.46 68.28 73.92L62.91 63.13C62.08 61.46 62.16 59.36 63.13 57.45C64.1 55.54 65.85 54.02 67.87 53.36C77.76 50.09 85.04 40.58 85.09 30.85C85.15 21.12 77.97 14 68.1 14L46.1 14C35.19 14 24.79 22.84 22.87 33.75L15.02 78.25C14.27 82.53 17.13 86 21.41 86C25.69 86 29.77 82.53 30.52 78.25Z"
    val INK = Color(0xFFF4F5F7)
    val TILE = Color(0xFF0F1115)

    fun path(small: Boolean = false): Path = PathParser().parsePathString(if (small) PATH_SMALL else PATH).toPath()
}

/** The lettermark in one colour, fitted to the smaller side. Below 26 dp it switches to the small optical cut. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, ink: Color = RayLogo.INK) {
    val big = remember { RayLogo.path(false) }
    val small = remember { RayLogo.path(true) }
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val k = side / 72f
        val glyph = if (side < 26.dp.toPx()) small else big
        translate(size.width / 2f - 50f * k, size.height / 2f - 50f * k) {
            scale(k, k, pivot = Offset.Zero) {
                drawPath(glyph, ink)
            }
        }
    }
}
