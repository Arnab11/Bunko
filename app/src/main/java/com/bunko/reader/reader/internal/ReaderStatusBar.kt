package com.bunko.reader.reader.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false
)

@Composable
internal fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false
        mutableStateOf(BatteryState(level = if (cap in 0..100) cap else 100, isCharging = isCharging))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val lvl = if (rawLevel >= 0 && scale > 0) {
                        ((rawLevel.toFloat() / scale.toFloat()) * 100).roundToInt()
                    } else state.level
                    state = BatteryState(level = lvl.coerceIn(0, 100), isCharging = charging)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { intent ->
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val lvl = if (rawLevel >= 0 && scale > 0) {
                ((rawLevel.toFloat() / scale.toFloat()) * 100).roundToInt()
            } else state.level
            state = BatteryState(level = lvl.coerceIn(0, 100), isCharging = charging)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {}
        }
    }

    return state
}

@Composable
internal fun BatteryIcon(
    batteryState: BatteryState,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val icon = when {
        batteryState.isCharging -> Icons.Default.BatteryChargingFull
        batteryState.level >= 95 -> Icons.Default.BatteryFull
        batteryState.level >= 80 -> Icons.Default.Battery6Bar
        batteryState.level >= 65 -> Icons.Default.Battery5Bar
        batteryState.level >= 50 -> Icons.Default.Battery4Bar
        batteryState.level >= 35 -> Icons.Default.Battery3Bar
        batteryState.level >= 20 -> Icons.Default.Battery2Bar
        batteryState.level >= 10 -> Icons.Default.Battery1Bar
        batteryState.level >= 5 -> Icons.Default.Battery0Bar
        else -> Icons.Default.BatteryAlert
    }
    Icon(
        imageVector = icon,
        contentDescription = "Battery ${batteryState.level}%",
        tint = tint,
        modifier = modifier
    )
}

private val MintPaperColor = Color(0xFFE5F3EA)

@Composable
private fun StatusPill(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (enabled) {
        Box(
            modifier = modifier
                .background(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderBottomStatusBar(
    currentPage: Int,
    totalPages: Int,
    pageBackground: Color,
    visible: Boolean,
    isEpub: Boolean = false,
    modifier: Modifier = Modifier
) {
    val batteryState = rememberBatteryState()
    val safePages = totalPages.coerceAtLeast(1)
    val displayPage = (currentPage + 1).coerceIn(1, safePages)
    val progressPercent = ((displayPage.toFloat() / safePages) * 100).roundToInt()

    val isLightBg = pageBackground.luminance() > 0.5f
    val isMint = pageBackground == MintPaperColor
    val isSepia = isLightBg && !isMint && pageBackground != Color.White
    val textColor = if (isEpub) {
        when {
            isSepia -> Color(0x99423224)
            isMint -> Color(0x9914251D)
            isLightBg -> Color(0x99000000)
            else -> Color(0x99FFFFFF)
        }
    } else {
        Color(0xF2FFFFFF)
    }
    val shadow = if (isEpub) {
        val shadowColor = if (isLightBg) Color(0x33FFFFFF) else Color(0x55000000)
        Shadow(
            color = shadowColor,
            offset = Offset(0f, 1f),
            blurRadius = 2f
        )
    } else {
        null
    }

    val style = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        shadow = shadow
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val navInsets = WindowInsets.navigationBarsIgnoringVisibility
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val startInset = (navInsets.calculateStartPadding(layoutDirection) + 24.dp).coerceAtLeast(24.dp)
        val endInset = (navInsets.calculateEndPadding(layoutDirection) + 24.dp).coerceAtLeast(24.dp)
        val bottomInset = (navInsets.calculateBottomPadding() + 8.dp).coerceAtLeast(10.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInset, start = startInset, end = endInset)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Page in book / chapter
                StatusPill(enabled = !isEpub) {
                    Text(
                        text = "Page $displayPage of $safePages",
                        color = textColor,
                        style = style
                    )
                }

                // 2. Percentage completed
                StatusPill(enabled = !isEpub) {
                    Text(
                        text = "$progressPercent%",
                        color = textColor,
                        style = style
                    )
                }

                // 3. Battery percentage
                StatusPill(enabled = !isEpub) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BatteryIcon(
                            batteryState = batteryState,
                            tint = textColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${batteryState.level}%",
                            color = textColor,
                            style = style
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberCurrentTime(): String {
    val context = LocalContext.current
    fun formatTime(): String {
        return android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
    }

    var timeStr by remember { mutableStateOf(formatTime()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeStr = formatTime()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {}
        }
    }

    return timeStr
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderTopHeaderBar(
    bookTitle: String,
    pageBackground: Color,
    visible: Boolean,
    isEpub: Boolean = false,
    headerHeight: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    val currentTime = rememberCurrentTime()

    val isLightBg = pageBackground.luminance() > 0.5f
    val isMint = pageBackground == MintPaperColor
    val isSepia = isLightBg && !isMint && pageBackground != Color.White
    val textColor = if (isEpub) {
        when {
            isSepia -> Color(0x99423224)
            isMint -> Color(0x9914251D)
            isLightBg -> Color(0x99000000)
            else -> Color(0x99FFFFFF)
        }
    } else {
        Color(0xF2FFFFFF)
    }
    val shadow = if (isEpub) {
        val shadowColor = if (isLightBg) Color(0x33FFFFFF) else Color(0x55000000)
        Shadow(
            color = shadowColor,
            offset = Offset(0f, 1f),
            blurRadius = 2f
        )
    } else {
        null
    }

    val timeStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        shadow = shadow
    )

    val titleStyle = TextStyle(
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
        textAlign = TextAlign.Center,
        shadow = shadow
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val statusBarTop = WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
            .calculateTopPadding()
        val layoutDirection = LocalLayoutDirection.current
        val sideInsets = WindowInsets.navigationBarsIgnoringVisibility
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
        val startPadding = (sideInsets.calculateStartPadding(layoutDirection) + 24.dp).coerceAtLeast(24.dp)
        val endPadding = (sideInsets.calculateEndPadding(layoutDirection) + 24.dp).coerceAtLeast(24.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop, start = startPadding, end = endPadding)
                .height(headerHeight)
        ) {
            // 1. Time on top-left
            StatusPill(
                enabled = !isEpub,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = currentTime,
                    color = textColor,
                    style = timeStyle
                )
            }

            // 2. Book Title in the center (when present, e.g. text/epub formats)
            if (bookTitle.isNotBlank()) {
                Text(
                    text = bookTitle,
                    color = textColor,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 64.dp)
                )
            }
        }
    }
}


