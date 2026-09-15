package com.bunko.reader.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.delay
import kotlin.math.hypot

class ThemeTransitionState {
    var isAnimating by mutableStateOf(false)
        private set
    var clickPosition by mutableStateOf(Offset.Zero)
        private set
    var screenshotBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var animationProgress = Animatable(0f)
        private set

    private var captureView: View? = null

    var onTransitionComplete: (() -> Unit)? = null

    fun setView(view: View?) {
        captureView = view
    }

    fun startTransition(position: Offset) {
        if (isAnimating) return
        captureView?.let { view ->
            try {
                val bitmap = view.drawToBitmap()
                animationProgress = Animatable(0f)
                screenshotBitmap = bitmap
                clickPosition = position
                isAnimating = true
            } catch (e: Exception) {
                BunkoLog.w("Theme screenshot capture failed; falling back to instant switch.", e)
                screenshotBitmap = null
                isAnimating = false
            }
        }
    }

    fun finishTransition() {
        val oldBitmap = screenshotBitmap
        screenshotBitmap = null
        clickPosition = Offset.Zero
        isAnimating = false
        onTransitionComplete?.invoke()
        onTransitionComplete = null
        captureView?.postDelayed(
            { oldBitmap?.takeUnless { it.isRecycled }?.recycle() },
            96L,
        )
    }

    suspend fun resetProgress() {
        animationProgress.snapTo(0f)
    }
}

val LocalThemeTransitionState = staticCompositionLocalOf<ThemeTransitionState?> { null }

@Composable
fun rememberThemeTransitionState(): ThemeTransitionState = remember { ThemeTransitionState() }

private const val THEME_REVEAL_DURATION_MS = 650
private const val THEME_CONTENT_SETTLE_DELAY_MS = 100L
private val THEME_REVEAL_FEATHER = 30.dp

@Composable
fun ThemeTransitionOverlay(
    state: ThemeTransitionState,
    themeKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val featherPx = with(density) { THEME_REVEAL_FEATHER.toPx() }
    val bitmap = state.screenshotBitmap
    val progress = state.animationProgress.value

    DisposableEffect(view, state) {
        state.setView(view)
        onDispose { state.setView(null) }
    }

    LaunchedEffect(state.isAnimating, bitmap) {
        if (!state.isAnimating || bitmap == null) return@LaunchedEffect

        state.resetProgress()
        withFrameNanos { }
        delay(THEME_CONTENT_SETTLE_DELAY_MS)
        state.animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = THEME_REVEAL_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
        state.finishTransition()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (bitmap != null && state.isAnimating) {
            val frozenFrame = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = frozenFrame,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val center = state.clickPosition.takeUnless { it == Offset.Zero }
                            ?: Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = maxOf(
                            hypot(center.x, center.y),
                            hypot(size.width - center.x, center.y),
                            hypot(center.x, size.height - center.y),
                            hypot(size.width - center.x, size.height - center.y),
                        )
                        val revealRadius = (maxRadius + featherPx) * progress
                        val maskRadius = (revealRadius + featherPx).coerceAtLeast(1f)
                        val clearStop = ((revealRadius - featherPx) / maskRadius).coerceIn(0f, 1f)
                        val softStop = (revealRadius / maskRadius).coerceIn(clearStop, 1f)
                        val mask = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                clearStop to Color.Transparent,
                                softStop to Color.Black.copy(alpha = 0.28f),
                                1f to Color.Black,
                            ),
                            center = center,
                            radius = maskRadius,
                        )

                        onDrawWithContent {
                            drawContent()
                            if (progress > 0f) {
                                drawRect(brush = mask, blendMode = BlendMode.DstIn)
                            }
                        }
                    }.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}
