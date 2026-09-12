// Bunko fork of oleksandrbalan/pagecurl v1.5.1 (Apache-2.0).
// Modifications:
// - turnEndFractionX lets the fold stop before the far edge (0.5f = the spine), which
//   turns a full-page curl into a spread leaf turn.
// - next()/prev() while an animation is in flight now commit the running turn without
//   flattening and continue the new turn from the current fold, so rapid taps
//   accelerate through pages instead of visually resetting.
package eu.wewox.pagecurl.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.PageCurlConfig.DragInteraction.PointerBehavior
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remembers the [PageCurlState].
 *
 * @param initialCurrent The initial current page.
 * @return The remembered [PageCurlState].
 */
@ExperimentalPageCurlApi
@Composable
public fun rememberPageCurlState(
    initialCurrent: Int = 0,
    turnEndFractionX: Float = 0f,
): PageCurlState =
    rememberSaveable(
        initialCurrent,
        turnEndFractionX,
        saver = Saver(
            save = { it.current },
            restore = { PageCurlState(initialCurrent = it, turnEndFractionX = turnEndFractionX) }
        )
    ) {
        PageCurlState(
            initialCurrent = initialCurrent,
            turnEndFractionX = turnEndFractionX,
        )
    }

/**
 * Remembers the [PageCurlState].
 *
 * @param initialCurrent The initial current page.
 * @param config The configuration for PageCurl.
 * @return The remembered [PageCurlState].
 */
@ExperimentalPageCurlApi
@Composable
@Deprecated(
    message = "Specify 'config' as 'config' in PageCurl composable.",
    level = DeprecationLevel.ERROR,
)
@Suppress("UnusedPrivateMember")
public fun rememberPageCurlState(
    initialCurrent: Int = 0,
    config: PageCurlConfig,
): PageCurlState =
    rememberSaveable(
        initialCurrent,
        saver = Saver(
            save = { it.current },
            restore = { PageCurlState(initialCurrent = it) }
        )
    ) {
        PageCurlState(
            initialCurrent = initialCurrent,
        )
    }

/**
 * Remembers the [PageCurlState].
 *
 * @param max The max number of pages.
 * @param initialCurrent The initial current page.
 * @param config The configuration for PageCurl.
 * @return The remembered [PageCurlState].
 */
@ExperimentalPageCurlApi
@Composable
@Deprecated(
    message = "Specify 'max' as 'count' in PageCurl composable and 'config' as 'config' in PageCurl composable.",
    level = DeprecationLevel.ERROR,
)
@Suppress("UnusedPrivateMember")
public fun rememberPageCurlState(
    max: Int,
    initialCurrent: Int = 0,
    config: PageCurlConfig = rememberPageCurlConfig()
): PageCurlState =
    rememberSaveable(
        max, initialCurrent,
        saver = Saver(
            save = { it.current },
            restore = {
                PageCurlState(
                    initialCurrent = it,
                    initialMax = max,
                )
            }
        )
    ) {
        PageCurlState(
            initialCurrent = initialCurrent,
            initialMax = max,
        )
    }

/**
 * The state of the PageCurl.
 *
 * @param initialMax The initial max number of pages.
 * @param initialCurrent The initial current page.
 * @param turnEndFractionX Bunko fork: the horizontal fraction of the width where the
 * fold line stops when a turn completes. 0f keeps the original full-page behavior;
 * 0.5f makes the fold stop at the centre (spine), i.e. a spread leaf turn where the
 * flap lands exactly on the other half of the viewport.
 */
@ExperimentalPageCurlApi
public enum class PageCurlTurnDirection {
    Forward,
    Backward
}

@ExperimentalPageCurlApi
public class PageCurlState(
    initialMax: Int = 0,
    initialCurrent: Int = 0,
    private val turnEndFractionX: Float = 0f,
) {
    /**
     * The observable current page.
     */
    public var current: Int by mutableStateOf(initialCurrent)
        internal set

    /**
     * The observable progress as page is turned.
     * When going forward it changes from 0 to 1, when going backward it is going from 0 to -1.
     */
    public val progress: Float get() = internalState?.progress ?: 0f

    internal var max: Int = initialMax
        private set

    internal var internalState: InternalState? by mutableStateOf(null)
        private set

    private var interactiveTurn: InteractiveTurn? = null

    internal fun setup(count: Int, constraints: Constraints) {
        max = count
        if (current >= count) {
            current = (count - 1).coerceAtLeast(0)
        }

        if (internalState?.constraints == constraints) {
            return
        }

        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()

        val left = Edge(Offset(0f, 0f), Offset(0f, maxHeightPx))
        val right = Edge(Offset(maxWidthPx, 0f), Offset(maxWidthPx, maxHeightPx))
        // Where the fold settles when a turn commits. Vanilla: far edge. Leaf: the spine.
        val forwardEnd = Edge(
            Offset(maxWidthPx * turnEndFractionX, 0f),
            Offset(maxWidthPx * turnEndFractionX, maxHeightPx)
        )
        val backwardEnd = Edge(
            Offset(maxWidthPx * (1f - turnEndFractionX), 0f),
            Offset(maxWidthPx * (1f - turnEndFractionX), maxHeightPx)
        )

        val forward = Animatable(right, Edge.VectorConverter, Edge.VisibilityThreshold)
        val backward = Animatable(left, Edge.VectorConverter, Edge.VisibilityThreshold)

        internalState = InternalState(constraints, left, right, forwardEnd, backwardEnd, forward, backward)
    }

    /**
     * Instantly snaps the state to the given page.
     *
     * @param value The page to snap to.
     */
    public suspend fun snapTo(value: Int) {
        // Bunko fork: before setup() runs, max is 0 and coerceIn(0, -1) throws on an
        // empty range (an upstream latent bug). The host may snap while the composable is
        // not yet mounted (e.g. a slide transition still covers it), so bail out instead;
        // setup() clamps current when it eventually runs.
        if (max <= 0) {
            interactiveTurn = null
            return
        }
        current = value.coerceIn(0, max - 1)
        interactiveTurn = null
        internalState?.reset()
    }

    public suspend fun beginTurn(
        direction: PageCurlTurnDirection,
        pointer: Offset,
        pointerBehavior: PointerBehavior = PointerBehavior.Default,
    ): Boolean {
        val state = internalState ?: return false
        val targetIndex = current + direction.pageDelta
        if (targetIndex < 0 || targetIndex >= max) return false

        state.animateJob?.let { running ->
            running.cancel()
            running.join()
        }
        state.reset()
        interactiveTurn = InteractiveTurn(direction, pointer, pointerBehavior)
        return true
    }

    public suspend fun dragTurnTo(pointer: Offset) {
        val state = internalState ?: return
        val turn = interactiveTurn ?: return
        val size = IntSize(state.constraints.maxWidth, state.constraints.maxHeight)
        val edge = interactiveCurlEdgeForPointer(
            direction = turn.direction,
            pointerBehavior = turn.pointerBehavior,
            startOffset = turn.startPointer,
            currentOffset = pointer,
            size = size,
            leafTurn = state.leafTurn,
            forwardEndX = state.forwardEndEdge.top.x,
            backwardEndX = state.backwardEndEdge.top.x,
        )
        // Match the library's native drag (DragCommonGesture): every pointer event starts a
        // spring animateTo towards the new edge, each cancelling the previous one. The paper
        // pursues the finger with a springy lag — the tactile feel the spike validated (A8) —
        // instead of rigidly snapping to it.
        when (turn.direction) {
            PageCurlTurnDirection.Forward -> state.forward.animateTo(edge)
            PageCurlTurnDirection.Backward -> state.backward.animateTo(edge)
        }
    }

    public suspend fun settleTurn(commit: Boolean) {
        val state = internalState ?: return
        val turn = interactiveTurn ?: return
        interactiveTurn = null
        val targetIndex = current + turn.direction.pageDelta
        state.animateTo(
            target = { if (commit) targetIndex else current },
            continuing = true,
            animate = {
                when (turn.direction) {
                    PageCurlTurnDirection.Forward -> {
                        forward.animateTo(if (commit) forwardEndEdge else rightEdge, tween(InteractiveSettleAnimDuration))
                    }
                    PageCurlTurnDirection.Backward -> {
                        backward.animateTo(if (commit) backwardEndEdge else leftEdge, tween(InteractiveSettleAnimDuration))
                    }
                }
            }
        )
    }

    /**
     * Go forward with an animation.
     *
     * @param block The animation block to animate a change. When null the default keyframe
     * animation is used; a turn already in flight is committed and continued instead.
     * @param tapPosition Bunko fork: the tap that started the turn, if any. In leaf mode
     * the curl starts from the tapped corner with a restrained, near-vertical crease.
     */
    public suspend fun next(
        block: (suspend Animatable<Edge, AnimationVector4D>.(Size) -> Unit)? = null,
        tapPosition: Offset? = null,
    ) {
        val state = internalState ?: return
        // Bunko fork: a forward turn already mid-flight is committed as-is and the new
        // turn continues from the current fold position, so rapid taps read as an
        // accelerated flip instead of the page snapping flat and starting over.
        val continuing = state.animateJob?.isActive == true && state.forward.value != state.rightEdge
        state.animateTo(
            target = { current + 1 },
            continuing = continuing,
            animate = {
                when {
                    block != null -> forward.block(it)
                    continuing -> forward.animateTo(forwardEndEdge, tween(ContinuationAnimDuration))
                    else -> forward.animateTo(
                        targetValue = forwardEndEdge,
                        animationSpec = keyframes {
                            durationMillis = TapAnimDuration
                            rightEdge at 0
                            quietMiddle(it, tapPosition, mirrored = false) at TapMidPointDuration
                        }
                    )
                }
            }
        )
    }

    /**
     * Go backward with an animation.
     *
     * @param block The animation block to animate a change. When null the default keyframe
     * animation is used; a turn already in flight is committed and continued instead.
     * @param tapPosition Bunko fork: the tap that started the turn, if any. In leaf mode
     * the curl starts from the tapped corner with a restrained, near-vertical crease.
     */
    public suspend fun prev(
        block: (suspend Animatable<Edge, AnimationVector4D>.(Size) -> Unit)? = null,
        tapPosition: Offset? = null,
    ) {
        val state = internalState ?: return
        val continuing = state.animateJob?.isActive == true && state.backward.value != state.leftEdge
        state.animateTo(
            target = { current - 1 },
            continuing = continuing,
            animate = {
                when {
                    block != null -> backward.block(it)
                    continuing -> backward.animateTo(backwardEndEdge, tween(ContinuationAnimDuration))
                    // Backward sweeps from the left, so its mid pose is the mirrored one.
                    else -> backward.animateTo(
                        targetValue = backwardEndEdge,
                        animationSpec = keyframes {
                            durationMillis = TapAnimDuration
                            leftEdge at 0
                            quietMiddle(it, tapPosition, mirrored = true) at TapMidPointDuration
                        }
                    )
                }
            }
        )
    }

    internal inner class InternalState(
        val constraints: Constraints,
        val leftEdge: Edge,
        val rightEdge: Edge,
        val forwardEndEdge: Edge,
        val backwardEndEdge: Edge,
        val forward: Animatable<Edge, AnimationVector4D>,
        val backward: Animatable<Edge, AnimationVector4D>,
    ) {

        var animateJob: Job? = null

        // Bunko fork: set by a successor turn before it cancels the running one, so the
        // predecessor's commit keeps the fold in place for the successor to continue from.
        var skipResetOnCommit: Boolean = false

        // Bunko fork: true when the fold stops before the far edge (spread leaf turn).
        val leafTurn: Boolean get() = forwardEndEdge != leftEdge

        val progress: Float by derivedStateOf {
            if (forward.value != rightEdge) {
                val range = rightEdge.centerX - forwardEndEdge.centerX
                if (kotlin.math.abs(range) < 0.001f) {
                    0f
                } else {
                    ((rightEdge.centerX - forward.value.centerX) / range).coerceIn(0f, 1f)
                }
            } else if (backward.value != leftEdge) {
                val range = backwardEndEdge.centerX - leftEdge.centerX
                if (kotlin.math.abs(range) < 0.001f) {
                    0f
                } else {
                    (-(backward.value.centerX - leftEdge.centerX) / range).coerceIn(-1f, 0f)
                }
            } else {
                0f
            }
        }

        suspend fun reset() {
            forward.snapTo(rightEdge)
            backward.snapTo(leftEdge)
        }

        suspend fun animateTo(
            target: () -> Int,
            animate: suspend InternalState.(Size) -> Unit,
            continuing: Boolean = false,
        ) {
            animateJob?.let { running ->
                if (continuing) skipResetOnCommit = true
                running.cancel()
                // Wait for the predecessor's commit so target() sees the updated page.
                running.join()
                skipResetOnCommit = false
            }

            val targetIndex = target()
            if (targetIndex < 0 || targetIndex >= max) {
                if (continuing) reset()
                return
            }

            coroutineScope {
                animateJob = launch {
                    try {
                        if (!continuing) reset()
                        animate(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
                    } finally {
                        withContext(NonCancellable) {
                            current = target().coerceIn(0, max - 1)
                            if (!skipResetOnCommit) reset()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The wrapper to represent a line with 2 points: [top] and [bottom].
 */
public data class Edge(val top: Offset, val bottom: Offset) {

    internal val centerX: Float = (top.x + bottom.x) * 0.5f

    internal companion object {
        val VectorConverter: TwoWayConverter<Edge, AnimationVector4D> =
            TwoWayConverter(
                convertToVector = { AnimationVector4D(it.top.x, it.top.y, it.bottom.x, it.bottom.y) },
                convertFromVector = { Edge(Offset(it.v1, it.v2), Offset(it.v3, it.v4)) }
            )

        val VisibilityThreshold: Edge =
            Edge(Offset.VisibilityThreshold, Offset.VisibilityThreshold)
    }
}

// Bunko fork: duration used when a new turn continues from a fold already in flight.
private const val ContinuationAnimDuration: Int = 180
private const val InteractiveSettleAnimDuration: Int = 180

// Bunko fork: tap animation is shorter and calmer than the vanilla 450ms corner sweep
// (Stage 1.7 H2: reading rhythm beats spectacle). Applies to both single-page and leaf mode.
private const val TapAnimDuration: Int = 400
private const val TapMidPointDuration: Int = 140

// Bunko fork: mid pose for the tap turn. Near-vertical crease with a slight lean
// so the corner on the tapped half of the screen lifts first (Stage 1.7 H6); no tap
// position leads with the bottom corner, matching a thumb resting low on a tablet.
internal fun quietMiddle(size: Size, tapPosition: Offset?, mirrored: Boolean): Edge {
    val topLeads = tapPosition != null && tapPosition.y < size.height / 2f
    val nearSpineX = size.width * 0.70f
    val nearEdgeX = size.width * 0.80f
    val topX = if (topLeads) nearSpineX else nearEdgeX
    val bottomX = if (topLeads) nearEdgeX else nearSpineX
    return if (mirrored) {
        Edge(Offset(size.width - topX, 0f), Offset(size.width - bottomX, size.height))
    } else {
        Edge(Offset(topX, 0f), Offset(bottomX, size.height))
    }
}

@ExperimentalPageCurlApi
private data class InteractiveTurn(
    val direction: PageCurlTurnDirection,
    val startPointer: Offset,
    val pointerBehavior: PointerBehavior,
)

@ExperimentalPageCurlApi
private val PageCurlTurnDirection.pageDelta: Int
    get() = when (this) {
        PageCurlTurnDirection.Forward -> 1
        PageCurlTurnDirection.Backward -> -1
    }

@ExperimentalPageCurlApi
internal fun interactiveCurlEdgeForPointer(
    direction: PageCurlTurnDirection,
    pointerBehavior: PointerBehavior,
    startOffset: Offset,
    currentOffset: Offset,
    size: IntSize,
    leafTurn: Boolean,
    forwardEndX: Float,
    backwardEndX: Float,
): Edge {
    val creator = when (pointerBehavior) {
        PointerBehavior.Default -> NewEdgeCreator.Default()
        PointerBehavior.PageEdge -> NewEdgeCreator.PageEdge()
    }
    val mirror = direction == PageCurlTurnDirection.Backward && leafTurn
    val creatorStart = if (mirror) startOffset.mirrorX(size) else startOffset
    val creatorCurrent = if (mirror) currentOffset.mirrorX(size) else currentOffset
    var target = creator.createNew(size, creatorStart, creatorCurrent)
    if (mirror) target = target.mirrorX(size)
    if (leafTurn) {
        val maxWidthPx = size.width.toFloat()
        target = when (direction) {
            PageCurlTurnDirection.Forward -> target.clampLineX(forwardEndX..maxWidthPx, size)
            PageCurlTurnDirection.Backward -> target.clampLineX(0f..backwardEndX, size)
        }
    }
    return target
}
