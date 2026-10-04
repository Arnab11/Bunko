package com.bunko.reader.reader.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunko.reader.BunkoLog
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Play Books-style Bubble Zoom popup overlay.
 * Magnifies the tapped speech bubble balloon smoothly with expressive styling,
 * rounded corners, elevation, and optional quick actions (Copy, TTS, Bubble Navigation).
 */
@Composable
internal fun BubbleZoomOverlay(
    activeBubble: ComicSpeechBubble?,
    allBubblesOnPage: List<ComicSpeechBubble>,
    sourceBitmap: Bitmap?,
    zoomScale: Float = 1.65f,
    // Pixel bounds of the page image on screen [left, top, right, bottom]
    pageDisplayBounds: Rect,
    onDismiss: () -> Unit,
    onSelectBubble: (ComicSpeechBubble) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val isVisible = activeBubble != null && sourceBitmap != null && !sourceBitmap.isRecycled

    // Crop the speech bubble sub-bitmap safely
    val bubbleBitmap = remember(activeBubble, sourceBitmap) {
        if (activeBubble == null || sourceBitmap == null || sourceBitmap.isRecycled) {
            null
        } else {
            val r = activeBubble.bubbleRect
            val cropX = r.left.coerceIn(0, sourceBitmap.width - 1)
            val cropY = r.top.coerceIn(0, sourceBitmap.height - 1)
            val cropW = r.width().coerceIn(1, sourceBitmap.width - cropX)
            val cropH = r.height().coerceIn(1, sourceBitmap.height - cropY)
            try {
                Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropW, cropH)
            } catch (t: Throwable) {
                BunkoLog.w("Failed to crop bubble bitmap", t)
                null
            }
        }
    }

    // Simple one-shot TTS engine for bubble read aloud
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.getDefault()
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(180)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(140)) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(140)
        ),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            val viewportW = constraints.maxWidth.toFloat()
            val viewportH = constraints.maxHeight.toFloat()

            if (activeBubble != null && bubbleBitmap != null) {
                val bitmapW = bubbleBitmap.width.toFloat()
                val bitmapH = bubbleBitmap.height.toFloat()

                // Calculate actual rendered page bounds within pageDisplayBounds
                val srcW = (sourceBitmap?.width ?: 1).toFloat().coerceAtLeast(1f)
                val srcH = (sourceBitmap?.height ?: 1).toFloat().coerceAtLeast(1f)
                val fitFactor = minOf(pageDisplayBounds.width / srcW, pageDisplayBounds.height / srcH)
                val actualPageW = srcW * fitFactor
                val actualPageH = srcH * fitFactor
                val actualPageX = pageDisplayBounds.left + (pageDisplayBounds.width - actualPageW) / 2f
                val actualPageY = pageDisplayBounds.top + (pageDisplayBounds.height - actualPageH) / 2f

                // Unmagnified bubble dimensions on screen
                val unmagnifiedW = (bitmapW / srcW) * actualPageW
                val unmagnifiedH = (bitmapH / srcH) * actualPageH
                val bubbleCenterScreenX = actualPageX + (activeBubble.normalizedRect.centerX() * actualPageW)
                val bubbleCenterScreenY = actualPageY + (activeBubble.normalizedRect.centerY() * actualPageH)

                // Magnify while strictly preserving aspect ratio
                var targetW = unmagnifiedW * zoomScale
                var targetH = unmagnifiedH * zoomScale

                val maxAllowedW = viewportW * 0.78f
                val maxAllowedH = viewportH * 0.52f

                // Scale down if overflowing screen
                if (targetW > maxAllowedW || targetH > maxAllowedH) {
                    val downScale = minOf(maxAllowedW / targetW, maxAllowedH / targetH)
                    targetW *= downScale
                    targetH *= downScale
                }

                // Ensure readable minimum width if the bubble is tiny
                val minAllowedW = with(density) { 130.dp.toPx() }
                if (targetW < minAllowedW && minAllowedW <= maxAllowedW) {
                    val upScale = minOf(minAllowedW / targetW, maxAllowedH / targetH)
                    targetW *= upScale
                    targetH *= upScale
                }

                // Account for quick action bar width (~210dp) so buttons are never cramped
                val toolbarWidthPx = with(density) { 210.dp.toPx() }
                val containerW = maxOf(targetW, toolbarWidthPx)

                // Account for quick action bar height below bubble (~48dp)
                val actionsBarHeightPx = with(density) { 48.dp.toPx() }
                val totalContentHeight = targetH + actionsBarHeightPx + with(density) { 8.dp.toPx() }

                // Clamp popup position inside viewport with safety margins
                val marginPx = with(density) { 16.dp.toPx() }
                val targetLeft = (bubbleCenterScreenX - containerW / 2f)
                    .coerceIn(marginPx, (viewportW - containerW - marginPx).coerceAtLeast(marginPx))
                val targetTop = (bubbleCenterScreenY - totalContentHeight / 2f)
                    .coerceIn(marginPx + 20f, (viewportH - totalContentHeight - marginPx - 20f).coerceAtLeast(marginPx))

                val currentBubbleIndex = allBubblesOnPage.indexOf(activeBubble)
                val hasPrev = currentBubbleIndex > 0
                val hasNext = currentBubbleIndex >= 0 && currentBubbleIndex < allBubblesOnPage.lastIndex

                Box(
                    modifier = Modifier
                        .offset { IntOffset(targetLeft.roundToInt(), targetTop.roundToInt()) }
                        .width(with(density) { containerW.toDp() })
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* keep open on bubble tap or cycle */ }
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        // Magnified Speech Balloon Surface (Exact Aspect Ratio)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 6.dp,
                            shadowElevation = 12.dp,
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .size(
                                    width = with(density) { targetW.toDp() },
                                    height = with(density) { targetH.toDp() }
                                )
                                .clip(RoundedCornerShape(18.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bubbleBitmap.asImageBitmap(),
                                    contentDescription = "Zoomed Speech Bubble",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Quick Actions Bar (TTS, Copy, Next/Prev, Dismiss)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (hasPrev) {
                                    FilledIconButton(
                                        onClick = { onSelectBubble(allBubblesOnPage[currentBubbleIndex - 1]) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                            contentDescription = "Previous Bubble",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // TTS Speak
                                FilledIconButton(
                                    onClick = {
                                        if (activeBubble.text.isNotBlank()) {
                                            ttsEngine?.speak(
                                                activeBubble.text,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "bubble_${activeBubble.id}"
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read Aloud",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Copy Text
                                FilledIconButton(
                                    onClick = {
                                        if (activeBubble.text.isNotBlank()) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Comic Dialogue", activeBubble.text)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied dialogue text", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Text",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (hasNext) {
                                    FilledIconButton(
                                        onClick = { onSelectBubble(allBubblesOnPage[currentBubbleIndex + 1]) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                            contentDescription = "Next Bubble",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Close Button
                                FilledIconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(16.dp)
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
