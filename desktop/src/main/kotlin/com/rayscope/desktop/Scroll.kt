package com.rayscope.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Scrolling for the mouse. Compose on desktop scrolls a sideways row only with Shift + wheel, never by dragging,
 * and draws no scrollbars on its own: these helpers add both, in the window palette.
 */

/** Width of every scrollbar: a pill that fits the 14 dp page margin with room on both sides. */
private val BAR = 6.dp

@Composable
fun rayScrollbarStyle(): ScrollbarStyle {
    val p = pal
    return ScrollbarStyle(
        minimalHeight = 36.dp,
        thickness = BAR,
        shape = RoundedCornerShape(50),
        hoverDurationMillis = 160,
        unhoverColor = p.text.copy(alpha = if (p.dark) 0.16f else 0.20f),
        hoverColor = p.text.copy(alpha = if (p.dark) 0.36f else 0.42f),
    )
}

/** Vertical scrollbar in the right page margin, for pages that scroll as a whole. */
@Composable
fun BoxScope.PageScrollbar(state: ScrollState) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state),
        Modifier.align(Alignment.TopEnd).fillMaxHeight()
            .padding(top = PAGE_TOP + 4.dp, bottom = 10.dp, end = (PAGE_PAD - BAR) / 2),
        style = rayScrollbarStyle(),
    )
}

/** A page that scrolls as one column, with its scrollbar in the margin. */
@Composable
fun ScrollColumn(
    padding: PaddingValues,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(state).padding(padding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        PageScrollbar(state)
    }
}

/** The same for a page laid out in a box (settings keep their column centred in it). */
@Composable
fun ScrollBox(
    contentAlignment: Alignment = Alignment.TopStart,
    state: ScrollState = rememberScrollState(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(state), contentAlignment = contentAlignment, content = content)
        PageScrollbar(state)
    }
}

/** The server list's scrollbar, moved out into the page margin so the rows keep the width of the search field. */
@Composable
fun BoxScope.ListScrollbar(state: LazyListState) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state),
        Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(bottom = 10.dp).offset(x = (PAGE_PAD + BAR) / 2),
        style = rayScrollbarStyle(),
    )
}

/** Scrollbar inside a card, in the card's own padding. */
@Composable
fun BoxScope.CardScrollbar(state: LazyListState) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state),
        Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 10.dp, horizontal = 3.dp),
        style = rayScrollbarStyle(),
    )
}

/** Scrollbar under a sideways row; it stays invisible while everything fits. */
@Composable
fun RowScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    HorizontalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxWidth(), style = rayScrollbarStyle())
}

/**
 * Lets the mouse move a sideways row: the wheel scrolls it smoothly (Shift + wheel and touchpads already work and
 * are left to the row itself), and the row can be dragged with the button held, the way a finger drags it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.mouseSideways(state: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 120.dp.toPx() }
    // where the running wheel animation is heading: quick turns add up instead of starting over from the middle
    val target = remember(state) { floatArrayOf(0f) }
    val wheel = remember(state) { arrayOfNulls<Job>(1) }
    val drag = rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    return this
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type != PointerEventType.Scroll) continue
                    val ch = e.changes.firstOrNull() ?: continue
                    val d = ch.scrollDelta
                    if (ch.isConsumed || d.y == 0f || d.x != 0f || state.maxValue == 0) continue
                    val from = if (wheel[0]?.isActive == true) target[0] else state.value.toFloat()
                    val to = (from + d.y * step).coerceIn(0f, state.maxValue.toFloat())
                    target[0] = to
                    wheel[0] = scope.launch { state.animateScrollTo(to.roundToInt(), tween(220, easing = FastOutSlowInEasing)) }
                    ch.consume()
                }
            }
        }
        .draggable(drag, Orientation.Horizontal)
}
