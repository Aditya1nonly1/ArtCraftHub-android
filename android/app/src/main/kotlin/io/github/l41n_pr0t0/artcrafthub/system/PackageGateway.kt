package io.github.l41n_pr0t0.artcrafthub.system

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow

/** How an install ended. */
sealed interface InstallResult {
    data object Success : InstallResult
    data object Cancelled : InstallResult

    /** An app with this package name is installed but signed with a different key. */
    data object SignatureConflict : InstallResult
    data class Failed(val message: String) : InstallResult
}

/** Maps a `PackageInstaller.STATUS_*` code (and its message) to an [InstallResult]. */
fun installResultFrom(status: Int, message: String?): InstallResult = when (status) {
    0 -> InstallResult.Success
    3 -> InstallResult.Cancelled
    5 -> InstallResult.SignatureConflict
    else -> InstallResult.Failed(
        message?.takeIf { it.isNotBlank() } ?: when (status) {
            2 -> "Install blocked by the system"
            4 -> "The APK is invalid"
            6 -> "Not enough storage"
            7 -> "This app doesn't support your device"
            else -> "Install failed (status $status)"
        },
    )
}

/** What the UI needs from Android's package manager. An interface so view models can use a fake. */
interface PackageGateway {
    /** The installed `versionName`, or null when the app isn't installed. */
    fun installedVersion(packageName: String): String?
    fun icon(packageName: String): ImageBitmap?

    /** The package name inside a downloaded APK, or null if it isn't a readable APK. */
    fun archivePackageName(apkPath: String): String?

    /** Whether the user has let this app install other apps. */
    fun canInstall(): Boolean
    fun openInstallPermissionSettings()
    suspend fun install(apkPath: String): InstallResult
    fun launch(packageName: String): Boolean
    fun uninstall(packageName: String)

    /** Package names as apps are installed, updated or removed. */
    fun changes(): Flow<String>
}
