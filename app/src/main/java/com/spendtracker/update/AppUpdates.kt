package com.spendtracker.update

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.spendtracker.notifications.UpdateCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Asks the Google Play Store app on the phone whether a newer SpendTracker is out, and runs Play's own
 * update screens. Copies not installed from Play (e.g. a sideloaded APK) simply never see an update.
 */
class AppUpdates(private val context: Context) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Play allows each [AppUpdateInfo] to start one update only, so it's dropped once used. */
    private var latestInfo: AppUpdateInfo? = null

    /** The version the user said "not now" to; hidden until the app next starts. */
    private var dismissedVersionCode: Int? = null

    private val installListener = InstallStateUpdatedListener { install ->
        when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> _state.value = UpdateState.Downloading(
                UpdateDecisions.progress(install.bytesDownloaded(), install.totalBytesToDownload()),
            )
            InstallStatus.DOWNLOADED -> _state.value = UpdateState.ReadyToInstall
            InstallStatus.FAILED, InstallStatus.CANCELED -> refresh()
            else -> Unit
        }
    }

    init {
        manager.registerListener(installListener)
    }

    /** Called whenever the app comes to the front. */
    fun refresh() {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestInfo = info
                _state.value = stateOf(info)
                // The banner takes over from the notification once the app is open.
                UpdateCheckWorker.cancelNotification(context)
            }
            .addOnFailureListener {
                latestInfo = null
                _state.value = UpdateState.None
            }
    }

    /** Opens Google Play's update screen; false when there is nothing to start. */
    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val info = latestInfo ?: return false
        val flow = flowOf(info) ?: return false
        latestInfo = null
        val type = if (flow == UpdateFlow.FLEXIBLE) AppUpdateType.FLEXIBLE else AppUpdateType.IMMEDIATE
        return manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
    }

    /** Google Play's update screen closed. Backing out of it counts as "not now". */
    fun onFlowResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_CANCELED) dismiss()
        refresh()
    }

    fun dismiss() {
        val available = _state.value as? UpdateState.Available ?: return
        dismissedVersionCode = available.versionCode
        _state.value = UpdateState.None
    }

    /** Installs a downloaded update; Google Play restarts the app. */
    fun install() {
        manager.completeUpdate()
    }

    private fun stateOf(info: AppUpdateInfo): UpdateState {
        val availability = info.updateAvailability()
        return UpdateDecisions.bannerState(
            // An immediate update the user left half-way is resumed from the same banner.
            updateAvailable = availability == UpdateAvailability.UPDATE_AVAILABLE ||
                availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            availableVersionCode = info.availableVersionCode(),
            flow = flowOf(info),
            phase = when (info.installStatus()) {
                InstallStatus.DOWNLOADED, InstallStatus.INSTALLING -> InstallPhase.DOWNLOADED
                InstallStatus.PENDING, InstallStatus.DOWNLOADING -> InstallPhase.DOWNLOADING
                else -> InstallPhase.IDLE
            },
            bytesDownloaded = info.bytesDownloaded(),
            totalBytes = info.totalBytesToDownload(),
            dismissedVersionCode = dismissedVersionCode,
        )
    }

    private fun flowOf(info: AppUpdateInfo): UpdateFlow? = UpdateDecisions.flowFor(
        flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
    )
}

/** Waits for a Play services [Task] without pulling in the coroutines-play-services library. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
