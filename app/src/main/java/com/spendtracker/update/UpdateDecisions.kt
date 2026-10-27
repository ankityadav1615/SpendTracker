package com.spendtracker.update

/** What the Home banner shows about a newer version on Google Play. */
sealed interface UpdateState {
    data object None : UpdateState

    /** A newer version can be downloaded. */
    data class Available(val versionCode: Int) : UpdateState

    /** Google Play is downloading it in the background; [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : UpdateState

    /** Downloaded; restarting the app installs it. */
    data object ReadyToInstall : UpdateState
}

/** How Google Play installs an update: in the background while the app is used, or full screen. */
enum class UpdateFlow { FLEXIBLE, IMMEDIATE }

/** Google Play's install progress, reduced to what the banner needs. */
enum class InstallPhase { IDLE, DOWNLOADING, DOWNLOADED }

/** The rules behind the update banner and notification, free of Play types so they can be unit-tested. */
object UpdateDecisions {

    /** Prefers the background download, so people can keep using the app while it updates. */
    fun flowFor(flexibleAllowed: Boolean, immediateAllowed: Boolean): UpdateFlow? = when {
        flexibleAllowed -> UpdateFlow.FLEXIBLE
        immediateAllowed -> UpdateFlow.IMMEDIATE
        else -> null
    }

    /** A download already under way always shows, even for a version the user said "not now" to. */
    fun bannerState(
        updateAvailable: Boolean,
        availableVersionCode: Int,
        flow: UpdateFlow?,
        phase: InstallPhase,
        bytesDownloaded: Long,
        totalBytes: Long,
        dismissedVersionCode: Int?,
    ): UpdateState = when {
        phase == InstallPhase.DOWNLOADED -> UpdateState.ReadyToInstall
        phase == InstallPhase.DOWNLOADING -> UpdateState.Downloading(progress(bytesDownloaded, totalBytes))
        updateAvailable && flow != null && availableVersionCode != dismissedVersionCode ->
            UpdateState.Available(availableVersionCode)
        else -> UpdateState.None
    }

    fun progress(bytesDownloaded: Long, totalBytes: Long): Float? =
        if (totalBytes <= 0) null else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** One notification per new version, never repeated for a version already announced. */
    fun shouldNotify(updateAvailable: Boolean, availableVersionCode: Int, lastNotifiedVersionCode: Int): Boolean =
        updateAvailable && availableVersionCode > lastNotifiedVersionCode
}
