package com.bunko.reader.library.internal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

/**
 * Expressive floating pill navigation bar styled after mpvRx's pill navbar.
 * Features an animated sliding indicator, expanding active tabs, swipe/drag gestures across tabs, and haptic feedback.
 */
@Composable
internal fun FloatingPillNavigationBar(
    destinations: List<HomeDestination>,
    selected: HomeDestination,
    onSelect: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    if (destinations.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val animatedPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pill_nav_position"
    )

    fun activeTabWidth(destination: HomeDestination) = when (destination) {
        HomeDestination.Home -> 92.dp
        HomeDestination.History -> 100.dp
        HomeDestination.Libraries -> 108.dp
        HomeDestination.WantToRead -> 92.dp
        HomeDestination.Browse -> 96.dp
        HomeDestination.Search -> 96.dp
    }

    val inactiveTabWidth = 44.dp
    val spacing = 4.dp
    val startPadding = 6.dp

    val tabWidths = destinations.mapIndexed { index, destination ->
        val fraction = (1f - kotlin.math.abs(animatedPosition - index)).coerceIn(0f, 1f)
        lerp(inactiveTabWidth, activeTabWidth(destination), fraction)
    }

    val tabOffsets = buildList {
        var offset = startPadding
        tabWidths.forEach { width ->
            add(offset)
            offset += width + spacing
        }
    }

    val pageFloor = animatedPosition.toInt().coerceIn(destinations.indices)
    val pageCeil = (pageFloor + 1).coerceIn(destinations.indices)
    val pageFraction = (animatedPosition - pageFloor).coerceIn(0f, 1f)
    val indicatorLeft = lerp(tabOffsets[pageFloor], tabOffsets[pageCeil], pageFraction)
    val indicatorWidth = lerp(tabWidths[pageFloor], tabWidths[pageCeil], pageFraction)

    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .pointerInput(destinations, selectedIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragDistance = 0f },
                    onDragEnd = {
                        if (totalDragDistance < -40f && selectedIndex < destinations.lastIndex) {
                            val next = destinations[selectedIndex + 1]
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(next)
                        } else if (totalDragDistance > 40f && selectedIndex > 0) {
                            val prev = destinations[selectedIndex - 1]
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(prev)
                        }
                        totalDragDistance = 0f
                    },
                    onDragCancel = { totalDragDistance = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragDistance += dragAmount
                        val touchX = change.position.x
                        val touchDp = with(density) { touchX.toDp() }
                        val targetIndex = tabOffsets.indexOfLast { offset -> touchDp >= offset }
                            .coerceIn(destinations.indices)
                        if (targetIndex != selectedIndex && targetIndex in destinations.indices) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(destinations[targetIndex])
                        }
                    }
                )
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = startPadding, vertical = 6.dp)
        ) {
            // Animated sliding capsule pill indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorLeft - startPadding)
                    .width(indicatorWidth)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, destination ->
                    key(destination) {
                        val activeFraction = (1f - kotlin.math.abs(animatedPosition - index)).coerceIn(0f, 1f)
                        val isSelected = destination == selected
                        val contentColor = androidx.compose.ui.graphics.lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            activeFraction
                        )

                        Box(
                            modifier = Modifier
                                .width(tabWidths[index])
                                .height(44.dp)
                                .clip(CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true)
                                ) {
                                    if (destination != selected) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                    onSelect(destination)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (activeFraction > 0.05f) {
                                    Spacer(modifier = Modifier.width(lerp(0.dp, 6.dp, activeFraction)))
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = ((activeFraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
