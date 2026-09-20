package com.bunko.reader.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// In-app update state holder ported from mpvRx's UpdateViewModel, adapted to
// Bunko's no-ViewModel style: a plain class owned via remember, driving the
// global UpdateSheet and the About screen's update section.
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Loading : UpdateState
    data class Available(val release: BunkoRelease) : UpdateState
    data object NoUpdate : UpdateState
    data object Error : UpdateState
    data class ReadyToInstall(val release: BunkoRelease) : UpdateState
}

@Stable
class UpdateController internal constructor(
    private val manager: BunkoUpdateManager,
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set
    var downloadProgress: Float by mutableFloatStateOf(0f)
        private set
    var isDownloading: Boolean by mutableStateOf(false)
        private set
    var autoCheckEnabled: Boolean by mutableStateOf(manager.isAutoCheckEnabled())
        private set
    var updateChannel: UpdateChannel by mutableStateOf(manager.storedChannel())
        private set

    private var checkJob: Job? = null

    fun setAutoCheck(enabled: Boolean) {
        manager.setAutoCheckEnabled(enabled)
        autoCheckEnabled = enabled
        if (enabled) checkForUpdate(manual = false)
    }

    fun setChannel(next: UpdateChannel) {
        if (next == updateChannel) return
        manager.storeChannel(next)
        updateChannel = next
        manager.clearCache()
        state = UpdateState.Idle
        checkForUpdate(manual = true)
    }

    fun checkForUpdate(manual: Boolean = false) {
        checkJob?.cancel()
        checkJob = scope.launch {
            state = UpdateState.Loading
            try {
                val release = manager.checkForUpdate(channel = updateChannel, forceShow = manual)
                state = if (release != null) {
                    if (manager.getApkFile(release) != null) {
                        UpdateState.ReadyToInstall(release)
                    } else {
                        UpdateState.Available(release)
                    }
                } else if (manual) {
                    UpdateState.NoUpdate
                } else {
                    UpdateState.Idle
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                BunkoLog.w("Update check failed.", t)
                state = if (manual) UpdateState.Error else UpdateState.Idle
            }
        }
    }

    fun downloadUpdate(release: BunkoRelease) {
        scope.launch {
            isDownloading = true
            try {
                manager.downloadUpdate(release).collect { progress ->
                    downloadProgress = progress
                }
                isDownloading = false
                state = UpdateState.ReadyToInstall(release)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                BunkoLog.w("Update download failed.", t)
                isDownloading = false
                state = UpdateState.Error
            }
        }
    }

    fun installUpdate(release: BunkoRelease) {
        val file = manager.getApkFile(release) ?: return
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun dismiss() {
        manager.clearCache()
        state = UpdateState.Idle
    }

    fun dismissStatus() {
        if (state == UpdateState.NoUpdate || state == UpdateState.Error) {
            state = UpdateState.Idle
        }
    }

    fun ignoreVersion(version: String) {
        manager.ignoreVersion(version, updateChannel)
        state = UpdateState.Idle
    }
}

@Composable
fun rememberUpdateController(context: Context): UpdateController {
    val appContext = context.applicationContext
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    DisposableEffect(scope) {
        onDispose { scope.cancel() }
    }
    val controller = remember(appContext) {
        UpdateController(BunkoUpdateManager(appContext), appContext, scope)
    }
    // Deferred auto-check so the network call never races first-frame composition.
    DisposableEffect(controller) {
        val job = if (controller.autoCheckEnabled) {
            scope.launch {
                delay(1500L)
                controller.checkForUpdate(manual = false)
            }
        } else {
            null
        }
        onDispose { job?.cancel() }
    }
    return controller
}
