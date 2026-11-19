package io.github.l41n_pr0t0.artcrafthub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStateTest {
    @Test fun noApkAndNotInstalled() = assertEquals(AppState.NoApk, appState(entry("a", apk = false), null, fakeIsNewer))

    @Test fun notInstalled() = assertEquals(AppState.NotInstalled, appState(entry("a"), null, fakeIsNewer))

    @Test fun olderInstalledHasUpdate() =
        assertEquals(AppState.UpdateAvailable("0.0.6-beta"), appState(entry("a", "v0.0.7-beta"), "0.0.6-beta", fakeIsNewer))

    @Test fun sameVersionIsInstalled() =
        assertEquals(AppState.Installed("0.0.7-beta"), appState(entry("a", "v0.0.7-beta"), "0.0.7-beta", fakeIsNewer))

    @Test fun installedWithoutApkCanStillOpen() =
        assertEquals(AppState.Installed("0.0.6-beta"), appState(entry("a", "v0.0.7-beta", apk = false), "0.0.6-beta", fakeIsNewer))
}
