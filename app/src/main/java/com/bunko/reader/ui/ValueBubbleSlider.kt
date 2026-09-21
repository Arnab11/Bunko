package com.bunko.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

private val SliderThumbWidth = 4.dp
private val ValueIndicatorMinWidth = 40.dp
private val ValueIndicatorHeight = 32.dp
private val ValueIndicatorMaxWidth = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ValueBubbleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    reverseTrackColors: Boolean = false,
    showStopIndicator: Boolean = true,
    roundThumb: Boolean = false,
    wavy: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null
) {
    var showBubble by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val bubbleVisible = enabled && (showBubble || isPressed || isDragged)
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val valueSpan = valueRange.endInclusive - valueRange.start
    val fraction = if (valueSpan <= 0f) {
        0f
    } else {
        ((coercedValue - valueRange.start) / valueSpan).coerceIn(0f, 1f)
    }
    val colorScheme = MaterialTheme.colorScheme
    val sliderColors = if (reverseTrackColors) {
        SliderDefaults.colors(
            activeTrackColor = colorScheme.secondaryContainer,
            activeTickColor = colorScheme.onSecondaryContainer,
            inactiveTrackColor = colorScheme.primary,
            inactiveTickColor = colorScheme.onPrimary
        )
    } else {
        SliderDefaults.colors()
    }

    BoxWithConstraints(modifier = modifier) {
        val effectiveThumbWidth = if (roundThumb) 16.dp else SliderThumbWidth
        val sliderModifier = Modifier
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    showBubble = true
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    showBubble = false
                }
            }

        AnimatedVisibility(
            visible = bubbleVisible,
            modifier = Modifier
                .offset {
                    val maxWidthPx = maxWidth.toPx()
                    val thumbWidthPx = effectiveThumbWidth.toPx()
                    val indicatorWidthPx = ValueIndicatorMinWidth.toPx()
                    val thumbCenterPx = thumbWidthPx / 2f + (maxWidthPx - thumbWidthPx).coerceAtLeast(0f) * fraction
                    val xOffset = (thumbCenterPx - indicatorWidthPx / 2f).coerceIn(0f, (maxWidthPx - indicatorWidthPx).coerceAtLeast(0f))
                    IntOffset(xOffset.roundToInt(), (-34.dp).roundToPx())
                }
                .align(Alignment.TopStart),
            enter = fadeIn(animationSpec = tween(durationMillis = 110)) +
                scaleIn(
                    animationSpec = tween(durationMillis = 110),
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)) +
                scaleOut(
                    animationSpec = tween(durationMillis = 90),
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .height(ValueIndicatorHeight)
                        .defaultMinSize(minWidth = ValueIndicatorMinWidth)
                        .widthIn(max = ValueIndicatorMaxWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = valueLabel(coercedValue),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        val sliderOnValueChange: (Float) -> Unit = {
            showBubble = true
            onValueChange(it)
        }
        val sliderOnValueChangeFinished: () -> Unit = {
            showBubble = false
            onValueChangeFinished?.invoke()
        }

        if (wavy || roundThumb) {
            val roundThumbComposable: @Composable (SliderState) -> Unit = {
                Box(
                    modifier = Modifier
                        .size(if (isDragged || isPressed) 18.dp else 16.dp)
                        .shadow(elevation = 3.dp, shape = CircleShape)
                        .background(
                            color = if (reverseTrackColors) colorScheme.secondaryContainer else colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
            val wavyTrackComposable: @Composable (SliderState) -> Unit = { sliderState ->
                val rangeSpan = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val progress = if (rangeSpan > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / rangeSpan).coerceIn(0f, 1f)
                } else 0f
                val activeColor = if (reverseTrackColors) colorScheme.secondaryContainer else colorScheme.primary
                val inactiveColor = Color(0x33FFFFFF)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val strokeWidthPx = 4.5.dp.toPx()
                    val amplitudePx = 3.2.dp.toPx()
                    val waveLengthPx = 22.dp.toPx()

                    val activeWidth = (width * progress).coerceIn(0f, width)

                    // 1. Inactive straight track
                    if (reverseTrackColors) {
                        val inactiveEnd = width - activeWidth
                        if (inactiveEnd > 0f) {
                            drawLine(
                                color = inactiveColor,
                                start = Offset(0f, centerY),
                                end = Offset(inactiveEnd, centerY),
                                strokeWidth = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        val inactiveStart = activeWidth
                        if (inactiveStart < width) {
                            drawLine(
                                color = inactiveColor,
                                start = Offset(inactiveStart, centerY),
                                end = Offset(width, centerY),
                                strokeWidth = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // 2. Active Material Expressive wavy track (GPU-accelerated cubic Bézier curves)
                    if (activeWidth > 2f) {
                        val startX = if (reverseTrackColors) width - activeWidth else 0f
                        val endX = if (reverseTrackColors) width else activeWidth
                        val halfWave = waveLengthPx / 2f
                        val path = Path()
                        path.moveTo(startX, centerY)

                        var currentX = startX
                        var waveIndex = 0
                        while (currentX < endX) {
                            val nextX = minOf(currentX + halfWave, endX)
                            val actualHalfWave = nextX - currentX
                            val actualCtrlOffset = actualHalfWave * 0.3642f

                            val midPoint = (currentX + nextX) / 2f
                            val relX = if (reverseTrackColors) (endX - midPoint) else (midPoint - startX)
                            val edgeTaper = minOf(
                                relX / (waveLengthPx * 0.5f),
                                (activeWidth - relX) / (waveLengthPx * 0.5f)
                            ).coerceIn(0f, 1f)

                            val sign = if (waveIndex % 2 == 0) 1f else -1f
                            val currentAmp = amplitudePx * edgeTaper * sign

                            path.cubicTo(
                                currentX + actualCtrlOffset,
                                centerY + currentAmp,
                                nextX - actualCtrlOffset,
                                centerY + currentAmp,
                                nextX,
                                centerY
                            )
                            currentX = nextX
                            waveIndex++
                        }

                        drawPath(
                            path = path,
                            color = activeColor,
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Slider(
                value = value,
                onValueChange = sliderOnValueChange,
                onValueChangeFinished = sliderOnValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                interactionSource = interactionSource,
                colors = sliderColors,
                thumb = roundThumbComposable,
                track = wavyTrackComposable,
                modifier = sliderModifier
            )
        } else if (showStopIndicator) {
            Slider(
                value = value,
                onValueChange = sliderOnValueChange,
                onValueChangeFinished = sliderOnValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                interactionSource = interactionSource,
                colors = sliderColors,
                modifier = sliderModifier
            )
        } else {
            Slider(
                value = value,
                onValueChange = sliderOnValueChange,
                onValueChangeFinished = sliderOnValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                interactionSource = interactionSource,
                colors = sliderColors,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        colors = sliderColors,
                        drawStopIndicator = null
                    )
                },
                modifier = sliderModifier
            )
        }
    }
}

