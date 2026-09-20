package karacken.curl

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Pure Jetpack Compose wrapper for [PageSurfaceView].
 *
 * Provides lifecycle-aware GL surface management, gesture handling, and deck updates.
 */
@Composable
fun PlayLikeCurlLayout(
    deck: PageDeck<Bitmap>?,
    modifier: Modifier = Modifier,
    paperColorArgb: Int = 0xFFFFFFFF.toInt(),
    listener: PageSurfaceListener = remember { object : PageSurfaceListener {} },
    onSurfaceCreated: ((PageSurfaceView) -> Unit)? = null
) {
    val context = LocalContext.current
    val surfaceView = remember {
        PageSurfaceView(context, paperColorArgb).apply {
            pageSurfaceListener = listener
            onSurfaceCreated?.invoke(this)
        }
    }

    DisposableEffect(surfaceView) {
        surfaceView.attach()
        onDispose {
            surfaceView.detach()
            surfaceView.dispose()
        }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = modifier,
        update = { view ->
            view.setBackgroundColor(
                (paperColorArgb shr 16) and 0xFF,
                (paperColorArgb shr 8) and 0xFF,
                paperColorArgb and 0xFF
            )
            if (deck != null) {
                view.submitDeck(deck)
            }
        }
    )
}
