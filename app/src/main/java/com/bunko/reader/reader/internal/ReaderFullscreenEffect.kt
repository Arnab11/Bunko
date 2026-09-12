package com.bunko.reader.reader.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
/** Internal to reader, not for external use. */
@Composable
internal fun ReaderFullscreenEffect(showStatusBar: Boolean) {
    val view = LocalView.current
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
                WindowInsetsControllerCompat(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            }
        }
    }

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        WindowInsetsControllerCompat(activity.window, view).apply {
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
