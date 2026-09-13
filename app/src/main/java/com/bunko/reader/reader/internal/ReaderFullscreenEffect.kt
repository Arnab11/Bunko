package com.bunko.reader.reader.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance

/** Internal to reader, not for external use. */
@Composable
internal fun ReaderFullscreenEffect(
    showStatusBar: Boolean,
    brightness: Float = -1f
) {
    val view = LocalView.current
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        if (activity == null) {
            onDispose {}
        } else {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                val act = view.context.findActivity()
                if (act != null) {
                    val lp = act.window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    act.window.attributes = lp
                }
                WindowInsetsControllerCompat(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }
    }

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val targetBrightness = if (brightness in 0f..1f) {
            if (brightness <= 0.20f) {
                0.01f
            } else {
                val t = (brightness - 0.20f) / 0.80f
                (0.01f + t * t * 0.99f).coerceIn(0.01f, 1f)
            }
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        val lp = activity.window.attributes
        if (lp.screenBrightness != targetBrightness) {
            lp.screenBrightness = targetBrightness
            activity.window.attributes = lp
        }

        WindowInsetsControllerCompat(activity.window, view).apply {
            isAppearanceLightStatusBars = isLightMode
            isAppearanceLightNavigationBars = isLightMode
            hide(WindowInsetsCompat.Type.navigationBars())
            if (showStatusBar) {
                show(WindowInsetsCompat.Type.statusBars())
            } else {
                hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
