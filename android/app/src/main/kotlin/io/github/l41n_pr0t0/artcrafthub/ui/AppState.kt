package io.github.l41n_pr0t0.artcrafthub.ui

import io.github.l41n_pr0t0.artcrafthub.core.AppEntry

/** What the hub can do with one app right now. */
sealed interface AppState {
    data object NotInstalled : AppState
    data class Installed(val version: String) : AppState
    data class UpdateAvailable(val installed: String) : AppState
    data class Downloading(val fraction: Float) : AppState
    data object Installing : AppState
    data object NoApk : AppState
}

/** The resting state of [entry] given what's installed. Downloading/Installing are set by the view model. */
fun appState(entry: AppEntry, installedVersion: String?, isNewer: (String, String) -> Boolean): AppState = when {
    installedVersion == null -> if (entry.apk == null) AppState.NoApk else AppState.NotInstalled
    entry.apk != null && isNewer(entry.latestVersion, installedVersion) -> AppState.UpdateAvailable(installedVersion)
    else -> AppState.Installed(installedVersion)
}
