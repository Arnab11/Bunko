package com.bunko.reader.reader.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode
import com.bunko.reader.reader.ReaderTurnDirection
import com.bunko.reader.reader.readerTurnDirectionForDrag
import com.bunko.reader.reader.readerTurnForDrag
import com.bunko.reader.reader.readerTurnProgressForDrag

/** Internal to reader, not for external use. */
@Composable
internal fun ReaderTapLayer(
    rightToLeft: Boolean,
    // Tap callbacks carry the tap position so the Curl path can start the fold
    // from the tapped corner (quietMiddle in the vendored fork).
    onNextSpread: (Offset) -> Unit,
    onPreviousSpread: (Offset) -> Unit,
    onNextSingle: () -> Unit,
    onPreviousSingle: () -> Unit,
    onCenterTap: () -> Unit,
    turnVisualDistancePx: Float,
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    zoomPanEnabled: Boolean = false,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    panMaxX: Float = 0f,
    panMaxY: Float = 0f,
    onPan: (Float, Float) -> Unit = { _, _ -> },
    onTurnDragStart: (ReaderTurnDirection, Offset) -> Unit = { _, _ -> },
    onTurnDrag: (ReaderTurnDirection, Float, Offset) -> Unit = { _, _, _ -> },
    onTurnDragEnd: (Float) -> Unit = {},
    onTurnDragCancel: () -> Unit = {},
    directionLockEnabled: Boolean = false,
    closeSwipeEnabled: Boolean = false,
    closeVisualDistancePx: Float = 1f,
    onCloseDrag: (Float) -> Unit = {},
    onCloseDragEnd: (Boolean) -> Unit = {},
    onCloseDragCancel: () -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    onTransform: (Float, Offset, Offset) -> Unit = { _, _, _ -> },
    onTransformEnd: (velocityScale: Float) -> Unit = {}
) {
    val latestOnNextSpread by rememberUpdatedState(onNextSpread)
    val latestOnPreviousSpread by rememberUpdatedState(onPreviousSpread)
    val latestOnNextSingle by rememberUpdatedState(onNextSingle)
    val latestOnPreviousSingle by rememberUpdatedState(onPreviousSingle)
    val latestOnCenterTap by rememberUpdatedState(onCenterTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        Modifier
            .fillMaxSize()
            .readerPinchZoom(
                onTransform = onTransform,
                onTransformEnd = onTransformEnd
            )
            .readerGestures(
                rightToLeft = rightToLeft,
                turnVisualDistancePx = turnVisualDistancePx,
                navigationMode = navigationMode,
                tappingInvertMode = tappingInvertMode,
                zoomPanEnabled = zoomPanEnabled,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                panMaxX = panMaxX,
                panMaxY = panMaxY,
                onPan = onPan,
                onTurnDragStart = onTurnDragStart,
                onTurnDrag = onTurnDrag,
                onTurnDragEnd = onTurnDragEnd,
                onTurnDragCancel = onTurnDragCancel,
                directionLockEnabled = directionLockEnabled,
                closeSwipeEnabled = closeSwipeEnabled,
                closeVisualDistancePx = closeVisualDistancePx,
                onCloseDrag = onCloseDrag,
                onCloseDragEnd = onCloseDragEnd,
                onCloseDragCancel = onCloseDragCancel,
                onMoveLeftTap = { position ->
                    if (!zoomPanEnabled) {
                        if (rightToLeft) latestOnNextSpread(position) else latestOnPreviousSpread(position)
                    }
                },
                onMoveRightTap = { position ->
                    if (!zoomPanEnabled) {
                        if (rightToLeft) latestOnPreviousSpread(position) else latestOnNextSpread(position)
                    }
                },
                onPrevTap = { position ->
                    if (!zoomPanEnabled) latestOnPreviousSpread(position)
                },
                onNextTap = { position ->
                    if (!zoomPanEnabled) latestOnNextSpread(position)
                },
                onCenterTap = latestOnCenterTap,
                onMoveLeftLongPress = {
                    if (!zoomPanEnabled) {
                        if (rightToLeft) latestOnNextSingle() else latestOnPreviousSingle()
                    }
                },
                onMoveRightLongPress = {
                    if (!zoomPanEnabled) {
                        if (rightToLeft) latestOnPreviousSingle() else latestOnNextSingle()
                    }
                },
                onPrevLongPress = {
                    if (!zoomPanEnabled) latestOnPreviousSingle()
                },
                onNextLongPress = {
                    if (!zoomPanEnabled) latestOnNextSingle()
                },
                onCenterDoubleTap = latestOnDoubleTap
            )
    )
}

@Composable
private fun Modifier.readerPinchZoom(
    onTransform: (Float, Offset, Offset) -> Unit,
    onTransformEnd: (velocityScale: Float) -> Unit = {}
): Modifier {
    val latestOnTransform by rememberUpdatedState(onTransform)
    val latestOnTransformEnd by rememberUpdatedState(onTransformEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            var lastCentroid: Offset? = null
            var lastSpan = 0f

            while (true) {
                val event = awaitPointerEvent()
                val pressedChanges = event.changes.filter { it.pressed }
                if (pressedChanges.isEmpty()) {
                    // All fingers lifted — fire end with the last scale velocity.
                    // We approximate the scale velocity as the last span ratio;
                    // callers that don't need it can ignore the parameter.
                    latestOnTransformEnd(if (lastSpan > 0f) lastSpan else 1f)
                    break
                }
                if (pressedChanges.size < 2) {
                    lastCentroid = null
                    lastSpan = 0f
                    continue
                }

                var centroidX = 0f
                var centroidY = 0f
                pressedChanges.forEach { change ->
                    centroidX += change.position.x
                    centroidY += change.position.y
                }
                val centroid = Offset(
                    x = centroidX / pressedChanges.size,
                    y = centroidY / pressedChanges.size
                )
                val span = pressedChanges
                    .map { (it.position - centroid).getDistance() }
                    .average()
                    .toFloat()

                val previousCentroid = lastCentroid
                if (previousCentroid != null && lastSpan > 0f && span > 0f) {
                    latestOnTransform(span / lastSpan, centroid - previousCentroid, centroid)
                    pressedChanges.forEach { it.consume() }
                }

                lastCentroid = centroid
                lastSpan = span
            }
        }
    }
}

@Composable
private fun Modifier.readerGestures(
    rightToLeft: Boolean,
    turnVisualDistancePx: Float,
    zoomPanEnabled: Boolean = false,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    panMaxX: Float = 0f,
    panMaxY: Float = 0f,
    onPan: (Float, Float) -> Unit = { _, _ -> },
    onTurnDragStart: (ReaderTurnDirection, Offset) -> Unit,
    onTurnDrag: (ReaderTurnDirection, Float, Offset) -> Unit,
    onTurnDragEnd: (Float) -> Unit,
    onTurnDragCancel: () -> Unit,
    directionLockEnabled: Boolean = false,
    closeSwipeEnabled: Boolean = false,
    closeVisualDistancePx: Float = 1f,
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    onCloseDrag: (Float) -> Unit = {},
    onCloseDragEnd: (Boolean) -> Unit = {},
    onCloseDragCancel: () -> Unit = {},
    onMoveLeftTap: (Offset) -> Unit = {},
    onCenterTap: () -> Unit = {},
    onMoveRightTap: (Offset) -> Unit = {},
    onPrevTap: (Offset) -> Unit = {},
    onNextTap: (Offset) -> Unit = {},
    onMoveLeftLongPress: () -> Unit = {},
    onMoveRightLongPress: () -> Unit = {},
    onPrevLongPress: () -> Unit = {},
    onNextLongPress: () -> Unit = {},
    onCenterDoubleTap: (Offset) -> Unit = {}
): Modifier {
    val latestRightToLeft by rememberUpdatedState(rightToLeft)
    val latestTurnVisualDistancePx by rememberUpdatedState(turnVisualDistancePx)
    val latestNavigationMode by rememberUpdatedState(navigationMode)
    val latestTappingInvertMode by rememberUpdatedState(tappingInvertMode)
    val latestZoomPanEnabled by rememberUpdatedState(zoomPanEnabled)
    val latestPanOffsetX by rememberUpdatedState(panOffsetX)
    val latestPanOffsetY by rememberUpdatedState(panOffsetY)
    val latestPanMaxX by rememberUpdatedState(panMaxX)
    val latestPanMaxY by rememberUpdatedState(panMaxY)
    val latestCloseSwipeEnabled by rememberUpdatedState(closeSwipeEnabled)
    val latestCloseVisualDistancePx by rememberUpdatedState(closeVisualDistancePx)
    val latestOnPan by rememberUpdatedState(onPan)
    val latestOnTurnDragStart by rememberUpdatedState(onTurnDragStart)
    val latestOnTurnDrag by rememberUpdatedState(onTurnDrag)
    val latestOnTurnDragEnd by rememberUpdatedState(onTurnDragEnd)
    val latestOnTurnDragCancel by rememberUpdatedState(onTurnDragCancel)
    val latestDirectionLockEnabled by rememberUpdatedState(directionLockEnabled)
    val latestOnCloseDrag by rememberUpdatedState(onCloseDrag)
    val latestOnCloseDragEnd by rememberUpdatedState(onCloseDragEnd)
    val latestOnCloseDragCancel by rememberUpdatedState(onCloseDragCancel)
    val latestOnMoveLeftTap by rememberUpdatedState(onMoveLeftTap)
    val latestOnCenterTap by rememberUpdatedState(onCenterTap)
    val latestOnMoveRightTap by rememberUpdatedState(onMoveRightTap)
    val latestOnPrevTap by rememberUpdatedState(onPrevTap)
    val latestOnNextTap by rememberUpdatedState(onNextTap)
    val latestOnMoveLeftLongPress by rememberUpdatedState(onMoveLeftLongPress)
    val latestOnMoveRightLongPress by rememberUpdatedState(onMoveRightLongPress)
    val latestOnPrevLongPress by rememberUpdatedState(onPrevLongPress)
    val latestOnNextLongPress by rememberUpdatedState(onNextLongPress)
    val latestOnCenterDoubleTap by rememberUpdatedState(onCenterDoubleTap)
    // Keep the pointerInput key stable; volatile gesture state is read through rememberUpdatedState to avoid restarting mid-swipe.
    return pointerInput(Unit) {
        var totalDragX = 0f
        var gestureDragX = 0f
        var gestureDragY = 0f
        var dragMode = ReaderDragMode.Pending
        var closeDragOffsetY = 0f
        var activePanX = 0f
        var activePanY = 0f
        var dragStartedAtNegativePanEdge = false
        var dragStartedAtPositivePanEdge = false
        var gestureStartPosition = Offset.Zero
        var velocityTracker = VelocityTracker()
        var lockedTurnDirection: ReaderTurnDirection? = null
        var pendingCenterTap: ReaderPendingCenterTap? = null
        val panEdgeTolerancePx = 1f
        val horizontalIntentSlopPx = 8f
        val directionLockSlopPx = 4f
        val verticalCloseIntentSlopPx = 12f
        val tapMoveSlopPx = directionLockSlopPx
        val doubleTapSlopPx = 64f
        val longPressTimeoutMillis = 500L
        val doubleTapTimeoutMillis = 300L

        fun closeMaxDragPx(): Float = latestCloseVisualDistancePx.coerceAtLeast(1f)

        fun closeCommitDistancePx(): Float = closeMaxDragPx() * 0.18f

        fun resetDragState() {
            totalDragX = 0f
            gestureDragX = 0f
            gestureDragY = 0f
            dragMode = ReaderDragMode.Pending
            closeDragOffsetY = 0f
            activePanX = latestPanOffsetX
            activePanY = latestPanOffsetY
            dragStartedAtNegativePanEdge = activePanX <= -latestPanMaxX + panEdgeTolerancePx
            dragStartedAtPositivePanEdge = activePanX >= latestPanMaxX - panEdgeTolerancePx
            gestureStartPosition = Offset.Zero
            velocityTracker = VelocityTracker()
            lockedTurnDirection = null
        }

        fun finishDrag() {
            when (dragMode) {
                ReaderDragMode.VerticalClose -> {
                    latestOnCloseDragEnd(closeDragOffsetY >= closeCommitDistancePx())
                }
                ReaderDragMode.HorizontalTurn,
                ReaderDragMode.ZoomEdgeTurn -> latestOnTurnDragEnd(velocityTracker.calculateVelocity().x)
                ReaderDragMode.Pending -> Unit
            }
        }

        fun cancelDrag() {
            when (dragMode) {
                ReaderDragMode.VerticalClose -> latestOnCloseDragCancel()
                ReaderDragMode.HorizontalTurn,
                ReaderDragMode.ZoomEdgeTurn -> latestOnTurnDragCancel()
                ReaderDragMode.Pending -> Unit
            }
        }

        fun flushPendingCenterTap() {
            if (pendingCenterTap != null) {
                pendingCenterTap = null
                latestOnCenterTap()
            }
        }

        fun dropPendingCenterTap() {
            pendingCenterTap = null
        }

        fun handleTap(position: Offset, uptimeMillis: Long) {
            when (resolveTapAction(position, size, latestNavigationMode, latestTappingInvertMode)) {
                ReaderTapAction.MoveLeft -> {
                    flushPendingCenterTap()
                    latestOnMoveLeftTap(position)
                }
                ReaderTapAction.MoveRight -> {
                    flushPendingCenterTap()
                    latestOnMoveRightTap(position)
                }
                ReaderTapAction.Prev -> {
                    flushPendingCenterTap()
                    latestOnPrevTap(position)
                }
                ReaderTapAction.Next -> {
                    flushPendingCenterTap()
                    latestOnNextTap(position)
                }
                ReaderTapAction.Menu -> {
                    val pending = pendingCenterTap
                    if (
                        pending != null &&
                        uptimeMillis - pending.uptimeMillis <= doubleTapTimeoutMillis &&
                        (position - pending.position).getDistance() <= doubleTapSlopPx
                    ) {
                        pendingCenterTap = null
                        latestOnCenterDoubleTap(position)
                    } else {
                        pendingCenterTap = ReaderPendingCenterTap(position, uptimeMillis)
                    }
                }
            }
        }

        fun handleLongPress(position: Offset) {
            flushPendingCenterTap()
            when (resolveTapAction(position, size, latestNavigationMode, latestTappingInvertMode)) {
                ReaderTapAction.MoveLeft -> latestOnMoveLeftLongPress()
                ReaderTapAction.MoveRight -> latestOnMoveRightLongPress()
                ReaderTapAction.Prev -> latestOnPrevLongPress()
                ReaderTapAction.Next -> latestOnNextLongPress()
                ReaderTapAction.Menu -> Unit
            }
        }

        fun turnDirectionForDrag(): ReaderTurnDirection {
            val direction = readerTurnDirectionForDrag(
                dragX = totalDragX,
                rightToLeft = latestRightToLeft,
                lockedDirection = lockedTurnDirection,
                directionLockEnabled = latestDirectionLockEnabled
            )
            if (latestDirectionLockEnabled) {
                lockedTurnDirection = direction
            }
            return direction
        }

        fun turnProgressForDrag(direction: ReaderTurnDirection): Float =
            readerTurnProgressForDrag(
                dragX = totalDragX,
                rightToLeft = latestRightToLeft,
                direction = direction,
                visualDistancePx = latestTurnVisualDistancePx,
                directionLockEnabled = latestDirectionLockEnabled
            )

        fun handleDrag(change: PointerInputChange, dragAmount: Offset) {
            gestureDragX += dragAmount.x
            gestureDragY += dragAmount.y
            if (latestZoomPanEnabled) {
                if (dragMode == ReaderDragMode.ZoomEdgeTurn) {
                    totalDragX += dragAmount.x
                    val direction = turnDirectionForDrag()
                    latestOnTurnDrag(
                        direction,
                        turnProgressForDrag(direction),
                        change.position
                    )
                    change.consume()
                    return
                }

                val stillAtStartedNegativeEdge =
                    latestPanMaxX > 0f &&
                        dragStartedAtNegativePanEdge &&
                        activePanX <= -latestPanMaxX + panEdgeTolerancePx
                val stillAtStartedPositiveEdge =
                    latestPanMaxX > 0f &&
                        dragStartedAtPositivePanEdge &&
                        activePanX >= latestPanMaxX - panEdgeTolerancePx
                val draggingOutFromStartEdge =
                    (stillAtStartedNegativeEdge && dragAmount.x < 0f) ||
                        (stillAtStartedPositiveEdge && dragAmount.x > 0f)
                val horizontalIntent =
                    abs(gestureDragX) >= horizontalIntentSlopPx &&
                        abs(gestureDragX) > abs(gestureDragY) * 1.2f
                if (!draggingOutFromStartEdge || !horizontalIntent) {
                    activePanX = (activePanX + dragAmount.x).coerceIn(-latestPanMaxX, latestPanMaxX)
                    activePanY = (activePanY + dragAmount.y).coerceIn(-latestPanMaxY, latestPanMaxY)
                    latestOnPan(activePanX, activePanY)
                    change.consume()
                    return
                }

                totalDragX = gestureDragX
                dragMode = ReaderDragMode.ZoomEdgeTurn
                val direction = readerTurnForDrag(totalDragX, latestRightToLeft)
                lockedTurnDirection = if (latestDirectionLockEnabled) direction else null
                latestOnTurnDragStart(direction, gestureStartPosition)
                latestOnTurnDrag(
                    direction,
                    turnProgressForDrag(direction),
                    change.position
                )
                change.consume()
                return
            }

            if (dragMode == ReaderDragMode.VerticalClose) {
                closeDragOffsetY = gestureDragY.coerceIn(0f, closeMaxDragPx())
                latestOnCloseDrag(closeDragOffsetY)
                change.consume()
                return
            }

            if (dragMode == ReaderDragMode.HorizontalTurn) {
                totalDragX += dragAmount.x
                val direction = turnDirectionForDrag()
                latestOnTurnDrag(
                    direction,
                    turnProgressForDrag(direction),
                    change.position
                )
                change.consume()
                return
            }

            val absGestureX = abs(gestureDragX)
            val hasDirectionIntent = hypot(gestureDragX, gestureDragY) >= directionLockSlopPx
            val downwardCloseIntent =
                latestCloseSwipeEnabled &&
                    gestureDragY >= verticalCloseIntentSlopPx &&
                    gestureDragY > absGestureX * 1.4f
            // A downward-leaning drag that hasn't yet reached the close slop: the turn lock
            // fires at only 4px, well below the 12px close slop, so a slow downward swipe
            // would otherwise be captured as a page turn before it could ever register as a
            // close. Stay Pending while it still leans vertical so the close gesture has a
            // chance to engage; it falls through to a turn once it leans horizontal.
            val downwardLeaning =
                latestCloseSwipeEnabled &&
                    gestureDragY > 0f &&
                    gestureDragY > absGestureX * 1.4f
            if (!hasDirectionIntent || (downwardLeaning && !downwardCloseIntent)) {
                change.consume()
                return
            }

            when {
                downwardCloseIntent -> {
                    dragMode = ReaderDragMode.VerticalClose
                    closeDragOffsetY = gestureDragY.coerceIn(0f, closeMaxDragPx())
                    latestOnCloseDrag(closeDragOffsetY)
                }
                else -> {
                    totalDragX = gestureDragX
                    dragMode = ReaderDragMode.HorizontalTurn
                    val direction = readerTurnForDrag(totalDragX, latestRightToLeft)
                    lockedTurnDirection = if (latestDirectionLockEnabled) direction else null
                    latestOnTurnDragStart(direction, gestureStartPosition)
                    latestOnTurnDrag(
                        direction,
                        turnProgressForDrag(direction),
                        change.position
                    )
                }
            }
            change.consume()
        }

        awaitEachGesture {
            val down = if (pendingCenterTap != null) {
                withTimeoutOrNull(doubleTapTimeoutMillis) {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                } ?: run {
                    flushPendingCenterTap()
                    return@awaitEachGesture
                }
            } else {
                awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
            }
            pendingCenterTap?.let { pending ->
                if ((down.position - pending.position).getDistance() > doubleTapSlopPx) {
                    flushPendingCenterTap()
                }
            }
            resetDragState()
            gestureStartPosition = down.position
            velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var previousPosition = down.position
            val pointerId = down.id
            var lastEventUptimeMillis = down.uptimeMillis
            var tapCandidate = true
            var longPressFired = false

            while (true) {
                val timeUntilLongPressMillis = longPressTimeoutMillis -
                    (lastEventUptimeMillis - down.uptimeMillis)
                val event = if (tapCandidate && !longPressFired && timeUntilLongPressMillis > 0L) {
                    withTimeoutOrNull(timeUntilLongPressMillis) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                } else {
                    awaitPointerEvent(PointerEventPass.Initial)
                }
                if (event == null) {
                    if (tapCandidate && !longPressFired) {
                        handleLongPress(down.position)
                        longPressFired = true
                        tapCandidate = false
                        continue
                    } else {
                        finishDrag()
                        break
                    }
                }
                val pressedChanges = event.changes.filter { it.pressed }
                if (pressedChanges.size > 1) {
                    dropPendingCenterTap()
                    cancelDrag()
                    break
                }

                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed) {
                    val release = event.changes.firstOrNull { it.id == pointerId }
                    if (tapCandidate && !longPressFired) {
                        handleTap(
                            position = release?.position ?: previousPosition,
                            uptimeMillis = release?.uptimeMillis ?: lastEventUptimeMillis
                        )
                    } else {
                        finishDrag()
                    }
                    break
                }
                lastEventUptimeMillis = change.uptimeMillis
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                val dragAmount = change.position - previousPosition
                previousPosition = change.position
                if (longPressFired) {
                    change.consume()
                    continue
                }
                if (dragAmount != Offset.Zero) {
                    if (tapCandidate && (change.position - down.position).getDistance() >= tapMoveSlopPx) {
                        tapCandidate = false
                        dropPendingCenterTap()
                    }
                    if (tapCandidate) {
                        change.consume()
                    }
                    handleDrag(change, dragAmount)
                }
            }
        }
    }
}

internal enum class ReaderTapAction {
    Menu,
    Prev,
    Next,
    MoveLeft,
    MoveRight
}

internal fun resolveTapAction(
    position: Offset,
    size: androidx.compose.ui.unit.IntSize,
    navigationMode: ReaderNavigationMode,
    tappingInvertMode: ReaderTappingInvertMode
): ReaderTapAction {
    if (size.width <= 0 || size.height <= 0 || navigationMode == ReaderNavigationMode.Disabled) {
        return ReaderTapAction.Menu
    }

    var nx = (position.x / size.width.toFloat()).coerceIn(0f, 1f)
    var ny = (position.y / size.height.toFloat()).coerceIn(0f, 1f)

    if (tappingInvertMode == ReaderTappingInvertMode.Horizontal || tappingInvertMode == ReaderTappingInvertMode.Both) {
        nx = 1f - nx
    }
    if (tappingInvertMode == ReaderTappingInvertMode.Vertical || tappingInvertMode == ReaderTappingInvertMode.Both) {
        ny = 1f - ny
    }

    return when (navigationMode) {
        ReaderNavigationMode.Disabled -> ReaderTapAction.Menu
        ReaderNavigationMode.Default,
        ReaderNavigationMode.RightAndLeft -> {
            when {
                nx < 0.33f -> ReaderTapAction.MoveLeft
                nx >= 0.66f -> ReaderTapAction.MoveRight
                else -> ReaderTapAction.Menu
            }
        }
        ReaderNavigationMode.LShaped -> {
            when {
                ny < 0.33f -> ReaderTapAction.Prev
                ny >= 0.66f -> ReaderTapAction.Next
                nx < 0.33f -> ReaderTapAction.Prev
                nx >= 0.66f -> ReaderTapAction.Next
                else -> ReaderTapAction.Menu
            }
        }
        ReaderNavigationMode.Kindlish -> {
            when {
                ny < 0.33f -> ReaderTapAction.Menu
                nx < 0.33f -> ReaderTapAction.Prev
                else -> ReaderTapAction.Next
            }
        }
        ReaderNavigationMode.Edge -> {
            when {
                nx < 0.33f -> ReaderTapAction.Next
                nx >= 0.66f -> ReaderTapAction.Next
                ny >= 0.66f -> ReaderTapAction.Prev
                else -> ReaderTapAction.Menu
            }
        }
    }
}


