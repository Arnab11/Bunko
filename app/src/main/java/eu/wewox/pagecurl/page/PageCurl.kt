// Bunko fork of oleksandrbalan/pagecurl v1.5.1 (Apache-2.0).
// Modifications: optional backContent lambda renders a real page on the back face of
// the flap (via drawCurlFront/drawCurlBack) instead of a mirror of the front page.
package eu.wewox.pagecurl.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param interactionsEnabled Bunko fork: when false, PageCurl draws only and attaches no native tap/drag handlers.
 * @param backContent Bunko fork: when provided, the flap shows this composable instead
 * of a mirrored copy of the front page. Receives (current, forward): the current page index
 * and the turn direction. The composable must lay out the arriving page at the position it
 * occupies once the turn has landed (upright, reader-facing); the fold transforms are
 * applied internally. For a forward turn the flap delivers page current + 1's incoming half,
 * for a backward turn page current - 1's returning half.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
public fun PageCurl(
    count: Int,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    interactionsEnabled: Boolean = true,
    backContent: (@Composable (current: Int, forward: Boolean) -> Unit)? = null,
    content: @Composable (Int) -> Unit
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier) {
        state.setup(count, constraints)

        val updatedCurrent by rememberUpdatedState(state.current)
        val internalState by rememberUpdatedState(state.internalState ?: return@BoxWithConstraints)

        val updatedConfig by rememberUpdatedState(config)

        val dragGestureModifier = if (interactionsEnabled) {
            when (val interaction = updatedConfig.dragInteraction) {
                is PageCurlConfig.GestureDragInteraction ->
                    Modifier
                        .dragGesture(
                            dragInteraction = interaction,
                            state = internalState,
                            enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                            enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                            scope = scope,
                            onChange = { state.current = updatedCurrent + it }
                        )

                is PageCurlConfig.StartEndDragInteraction ->
                    Modifier
                        .dragStartEnd(
                            dragInteraction = interaction,
                            state = internalState,
                            enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                            enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                            scope = scope,
                            onChange = { state.current = updatedCurrent + it }
                        )
            }
        } else {
            Modifier
        }

        Box(
            Modifier
                .then(dragGestureModifier)
                .then(
                    if (interactionsEnabled) {
                        Modifier.tapGesture(
                            config = updatedConfig,
                            scope = scope,
                            onTapForward = { position -> state.next(tapPosition = position) },
                            onTapBackward = { position -> state.prev(tapPosition = position) },
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            // Wrap in key to synchronize state updates
            key(updatedCurrent, internalState.forward.value, internalState.backward.value) {
                if (updatedCurrent + 1 < state.max) {
                    content(updatedCurrent + 1)
                }

                if (updatedCurrent < state.max) {
                    val forward = internalState.forward.value
                    if (backContent == null) {
                        Box(Modifier.drawCurl(updatedConfig, forward.top, forward.bottom)) {
                            content(updatedCurrent)
                        }
                    } else {
                        Box(Modifier.drawCurlFront(updatedConfig, forward.top, forward.bottom)) {
                            content(updatedCurrent)
                        }
                        Box(Modifier.drawCurlBack(updatedConfig, forward.top, forward.bottom)) {
                            backContent(updatedCurrent, true)
                        }
                    }
                }

                if (updatedCurrent > 0) {
                    val backward = internalState.backward.value
                    if (backContent == null || !internalState.leafTurn) {
                        // The mirrored-forward treatment below models a spine-bound leaf;
                        // full-page turns keep the vanilla backward (custom back faces are
                        // currently only supported in leaf mode).
                        Box(Modifier.drawCurl(updatedConfig, backward.top, backward.bottom)) {
                            content(updatedCurrent - 1)
                        }
                    } else if (backward != internalState.leftEdge) {
                        // A spine-bound leaf cannot un-turn the way an edge-bound page does:
                        // going backward is a forward turn seen in a horizontally mirrored
                        // space (the LTR/RTL flip). Mirror the edge and the drawing, and let
                        // the inner mirrors restore the content orientation.
                        val widthPx = internalState.constraints.maxWidth.toFloat()
                        val mirroredTop = Offset(widthPx - backward.top.x, backward.top.y)
                        val mirroredBottom = Offset(widthPx - backward.bottom.x, backward.bottom.y)
                        // The revealed area and the landing base both belong to the previous
                        // page; it fully covers the idle forward pair beneath.
                        content(updatedCurrent - 1)
                        Box(Modifier.graphicsLayer { scaleX = -1f }) {
                            Box(Modifier.drawCurlFront(updatedConfig, mirroredTop, mirroredBottom)) {
                                Box(Modifier.graphicsLayer { scaleX = -1f }) {
                                    content(updatedCurrent)
                                }
                            }
                        }
                        Box(Modifier.graphicsLayer { scaleX = -1f }) {
                            Box(Modifier.drawCurlBack(updatedConfig, mirroredTop, mirroredBottom)) {
                                Box(Modifier.graphicsLayer { scaleX = -1f }) {
                                    backContent(updatedCurrent, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param key The lambda to provide stable key for each item. Useful when adding and removing items before current page.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
public fun PageCurl(
    count: Int,
    key: (Int) -> Any,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    interactionsEnabled: Boolean = true,
    content: @Composable (Int) -> Unit
) {
    var lastKey by remember(state.current) { mutableStateOf(if (count > 0) key(state.current) else null) }

    remember(count) {
        val newKey = if (count > 0) key(state.current) else null
        if (newKey != lastKey) {
            val index = List(count, key).indexOf(lastKey).coerceIn(0, count - 1)
            lastKey = newKey
            state.current = index
        }
        count
    }

    PageCurl(
        count = count,
        state = state,
        config = config,
        interactionsEnabled = interactionsEnabled,
        content = content,
        modifier = modifier,
    )
}

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param modifier The modifier for this composable.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
@Deprecated("Specify 'max' as 'count' in PageCurl composable.")
public fun PageCurl(
    state: PageCurlState,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    PageCurl(
        count = state.max,
        state = state,
        modifier = modifier,
        content = content,
    )
}
