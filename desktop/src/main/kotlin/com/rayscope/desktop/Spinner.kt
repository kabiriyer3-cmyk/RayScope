package com.rayscope.desktop

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Rotates the element by an animated angle (used for small "in progress" icons). */
fun Modifier.spinning(angle: State<Float>): Modifier = this.graphicsLayer { rotationZ = angle.value }
