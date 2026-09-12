package com.bunko.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

private enum class SideSheetDragValue {
    Open,
    Dismissed
}

@Composable
internal fun ModalSideSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    paneTitleText: String,
    content: @Composable () -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    var dismissPending by remember { mutableStateOf(false) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val dragState = remember { AnchoredDraggableState(SideSheetDragValue.Open) }

    if (!visible && !visibility.currentState && visibility.isIdle) return

    fun requestDismiss() {
        if (!dismissPending) {
            dismissPending = true
            visibility.targetState = false
        }
    }

    BackHandler(enabled = visible && !dismissPending, onBack = ::requestDismiss)

    LaunchedEffect(visible) {
        if (visible) {
            visibility.targetState = true
        } else {
            visibility.targetState = false
        }
    }
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.settledValue }
            .distinctUntilChanged()
            .filter { it == SideSheetDragValue.Dismissed }
            .first()
        requestDismiss()
    }
    LaunchedEffect(dismissPending, visibility.currentState, visibility.isIdle) {
        if (dismissPending && !visibility.currentState && visibility.isIdle) {
            dragState.snapTo(SideSheetDragValue.Open)
            dismissPending = false
            currentOnDismissRequest()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        val sheetWidth = if (maxWidth > 520.dp) 480.dp else maxWidth * 0.92f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = visible && !dismissPending
                )
                .pointerInput(Unit) {
                    detectTapGestures { requestDismiss() }
                }
        )

        AnimatedVisibility(
            visibleState = visibility,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .width(sheetWidth)
                    .fillMaxHeight()
                    .onSizeChanged { size ->
                        dragState.updateAnchors(
                            DraggableAnchors {
                                SideSheetDragValue.Open at 0f
                                SideSheetDragValue.Dismissed at size.width.toFloat()
                            }
                        )
                    }
                    .offset {
                        IntOffset(
                            x = dragState.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0,
                            y = 0
                        )
                    }
                    .anchoredDraggable(
                        state = dragState,
                        orientation = Orientation.Horizontal
                    )
                    .semantics {
                        dialog()
                        paneTitle = paneTitleText
                    },
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                content()
            }
        }
    }
}
