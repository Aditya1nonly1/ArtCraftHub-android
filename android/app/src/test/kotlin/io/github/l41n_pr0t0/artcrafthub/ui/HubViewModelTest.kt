package io.github.l41n_pr0t0.artcrafthub.ui

import io.github.l41n_pr0t0.artcrafthub.core.HubException
import io.github.l41n_pr0t0.artcrafthub.system.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val core = FakeCore(cached = listOf(entry("capcraft")), fresh = listOf(entry("capcraft"), entry("procraft")))
    private val packages = FakePackages()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HubViewModel(core, packages, dispatcher, fakeIsNewer, MemoryPackageNames())

    private fun HubViewModel.app(id: String) = state.value.apps.first { it.entry.id == id }

    @Test fun showsCachedThenRefreshed() = runTest(dispatcher) {
        val seen = mutableListOf<List<String>>()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { s -> seen += s.apps.map { it.entry.id } }
        }
        advanceUntilIdle()
        assertTrue(seen.toString(), listOf("capcraft") in seen)
        assertEquals(listOf("capcraft", "procraft"), seen.last())
        assertEquals(false, vm.state.value.refreshing)
    }

    @Test fun refreshErrorKeepsAppsAndShowsMessage() = runTest(dispatcher) {
        core.refreshError = HubException.Offline()
        val vm = vm()
        advanceUntilIdle()
        assertEquals(listOf("capcraft"), vm.state.value.apps.map { it.entry.id })
        assertEquals("No internet connection.", vm.state.value.message)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test fun installMovesThroughDownloadingAndInstalling() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val seen = mutableListOf<AppState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { s -> s.apps.firstOrNull { it.entry.id == "capcraft" }?.let { seen += it.state } }
        }
        vm.primaryAction("capcraft")
        advanceUntilIdle()
        assertEquals(listOf("capcraft"), core.downloads)
        assertTrue(seen.toString(), seen.any { it is AppState.Downloading })
        assertTrue(seen.toString(), AppState.Installing in seen)
        assertEquals(AppState.Installed("0.0.7-beta"), vm.app("capcraft").state)
        assertTrue(seen.indexOfFirst { it is AppState.Downloading } < seen.indexOf(AppState.Installing))
    }

    @Test fun withoutInstallPermissionOpensSettingsInsteadOfDownloading() = runTest(dispatcher) {
        packages.allowed = false
        val vm = vm()
        advanceUntilIdle()
        vm.primaryAction("capcraft")
        advanceUntilIdle()
        assertEquals(1, packages.openedSettings)
        assertTrue(core.downloads.isEmpty())
    }

    @Test fun installedAppOpens() = runTest(dispatcher) {
        packages.installed["io.github.artcraftandroid.capcraft"] = "0.0.7-beta"
        val vm = vm()
        advanceUntilIdle()
        vm.primaryAction("capcraft")
        advanceUntilIdle()
        assertEquals(listOf("io.github.artcraftandroid.capcraft"), packages.launched)
        assertTrue(core.downloads.isEmpty())
    }

    @Test fun signatureConflictShowsMessage() = runTest(dispatcher) {
        packages.installResult = InstallResult.SignatureConflict
        val vm = vm()
        advanceUntilIdle()
        vm.primaryAction("capcraft")
        advanceUntilIdle()
        assertEquals(installMessage(InstallResult.SignatureConflict), vm.state.value.message)
        assertEquals(AppState.NotInstalled, vm.app("capcraft").state)
    }

    @Test fun packageChangeRereadsInstalledVersion() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(AppState.NotInstalled, vm.app("procraft").state)
        packages.installed["io.github.artcraftandroid.procraft"] = "0.0.6-beta"
        packages.events.emit("io.github.artcraftandroid.procraft")
        runCurrent()
        assertEquals(AppState.UpdateAvailable("0.0.6-beta"), vm.app("procraft").state)
    }

    @Test fun rejectedSourceKeepsSourcesAndShowsMessage() = runTest(dispatcher) {
        core.addSourceError = HubException.NotArtcraft()
        val vm = vm()
        advanceUntilIdle()
        val before = vm.state.value.sources
        var added = false
        vm.addSource("https://github.com/someone/else") { added = true }
        advanceUntilIdle()
        assertEquals(false, added)
        assertEquals(before, vm.state.value.sources)
        assertEquals("Only links from github.com/artcraft-android can be added.", vm.state.value.message)
    }

    @Test fun acceptedSourceReportsSuccess() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        var added = false
        vm.addSource("https://github.com/artcraft-android/capcraft") { added = true }
        advanceUntilIdle()
        assertEquals(true, added)
    }

    @Test fun importObtainiumAddsSourcesAndRefreshes() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val refreshesBefore = core.refreshes
        core.importResult = listOf(repoSource("procraft"), repoSource("capcraft"))
        vm.importObtainium("{}")
        advanceUntilIdle()
        assertEquals(listOf("artcraft-android", "procraft", "capcraft"), vm.state.value.sources.map { it.id })
        assertEquals(refreshesBefore + 1, core.refreshes)
        assertEquals("Added 2 sources from Obtainium.", vm.state.value.message)
    }

    @Test fun importWithNothingNewSaysSo() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.importObtainium("{}")
        advanceUntilIdle()
        assertEquals("No ArtCraft apps found in that file.", vm.state.value.message)
    }

    @Test fun removingARepoSourceDropsItsApp() = runTest(dispatcher) {
        core.sourceList = listOf(repoSource("capcraft"), repoSource("procraft"))
        val vm = vm()
        advanceUntilIdle()
        assertEquals(listOf("capcraft", "procraft"), vm.state.value.apps.map { it.entry.id })
        vm.removeSource("procraft")
        advanceUntilIdle()
        assertEquals(listOf("capcraft"), vm.state.value.apps.map { it.entry.id })
    }

    @Test fun removingARepoStillCoveredByTheOrgKeepsItsApp() = runTest(dispatcher) {
        core.sourceList = core.sourceList + repoSource("procraft")
        val vm = vm()
        advanceUntilIdle()
        vm.removeSource("procraft")
        advanceUntilIdle()
        assertEquals(listOf("capcraft", "procraft"), vm.state.value.apps.map { it.entry.id })
    }
}
