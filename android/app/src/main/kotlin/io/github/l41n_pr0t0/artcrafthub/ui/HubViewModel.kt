package io.github.l41n_pr0t0.artcrafthub.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.l41n_pr0t0.artcrafthub.core.AppEntry
import io.github.l41n_pr0t0.artcrafthub.core.CoreGateway
import io.github.l41n_pr0t0.artcrafthub.core.HubException
import io.github.l41n_pr0t0.artcrafthub.core.Source
import io.github.l41n_pr0t0.artcrafthub.system.PackageGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUi(val entry: AppEntry, val state: AppState)

data class HubUiState(
    val apps: List<AppUi> = emptyList(),
    val sources: List<Source> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
    val selectedId: String? = null,
)

/**
 * Remembers the real package name of an app when its APK doesn't use the one the core guessed
 * (`io.github.artcraftandroid.<name>`), so installed state is found next time.
 */
interface PackageNames {
    operator fun get(appId: String): String?
    operator fun set(appId: String, packageName: String)
}

class MemoryPackageNames : PackageNames {
    private val map = mutableMapOf<String, String>()
    override fun get(appId: String) = map[appId]
    override fun set(appId: String, packageName: String) {
        map[appId] = packageName
    }
}

class PrefsPackageNames(context: Context) : PackageNames {
    private val prefs = context.getSharedPreferences("package_overrides", Context.MODE_PRIVATE)
    override fun get(appId: String): String? = prefs.getString(appId, null)
    override fun set(appId: String, packageName: String) = prefs.edit().putString(appId, packageName).apply()
}

/**
 * The hub's screen state. Core calls run on [io]; everything else happens on the main thread,
 * so the private fields below need no locking.
 */
class HubViewModel(
    private val core: CoreGateway,
    private val packages: PackageGateway,
    private val io: CoroutineDispatcher,
    private val isNewer: (String, String) -> Boolean,
    private val names: PackageNames,
) : ViewModel() {
    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    private var entries: List<AppEntry> = emptyList()

    /** Installed versionName per package; null = not installed. Filled lazily. */
    private val installed = mutableMapOf<String, String?>()

    /** Downloading/Installing states, by app id. */
    private val busy = mutableMapOf<String, AppState>()

    init {
        viewModelScope.launch {
            val (cached, sources) = runCatching { withContext(io) { core.cachedCatalog() to core.sources() } }
                .getOrDefault(emptyList<AppEntry>() to emptyList())
            entries = cached
            _state.update { it.copy(sources = sources) }
            publish()
            refresh()
        }
        viewModelScope.launch {
            packages.changes().collect { pkg ->
                installed[pkg] = packages.installedVersion(pkg)
                publish()
            }
        }
    }

    fun packageName(entry: AppEntry): String = names[entry.id] ?: entry.packageName

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                entries = withContext(io) { core.refreshCatalog() }
                installed.clear() // apps may have changed while we were away
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(errorText(e))
            } finally {
                _state.update { it.copy(refreshing = false) }
                publish()
            }
        }
    }

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Install, update or open, depending on the app's state. */
    fun primaryAction(id: String) {
        val app = _state.value.apps.firstOrNull { it.entry.id == id } ?: return
        when (app.state) {
            is AppState.Installed -> if (!packages.launch(packageName(app.entry))) showMessage("This app can't be opened.")
            AppState.NotInstalled, is AppState.UpdateAvailable -> install(app.entry)
            is AppState.Downloading, AppState.Installing, AppState.NoApk -> Unit
        }
    }

    private fun install(entry: AppEntry) {
        if (!packages.canInstall()) {
            packages.openInstallPermissionSettings()
            return
        }
        val id = entry.id
        setBusy(id, AppState.Downloading(0f))
        viewModelScope.launch {
            try {
                var shown = -1
                val apk = withContext(io) {
                    core.download(id) { done, total ->
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != shown) {
                            shown = percent
                            viewModelScope.launch { if (busy[id] is AppState.Downloading) setBusy(id, AppState.Downloading(percent / 100f)) }
                        }
                    }
                }
                setBusy(id, AppState.Installing)
                val real = withContext(io) { packages.archivePackageName(apk.path) }
                if (real != null && real != entry.packageName) names[id] = real
                val result = packages.install(apk.path)
                val pkg = packageName(entry)
                installed[pkg] = packages.installedVersion(pkg)
                installMessage(result)?.let(::showMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (_: HubException.Cancelled) {
                // The user cancelled; nothing to report.
            } catch (e: Exception) {
                showMessage(errorText(e))
            } finally {
                setBusy(id, null)
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch { withContext(io) { core.cancelDownload(id) } }
    }

    fun uninstall(id: String) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        packages.uninstall(packageName(entry))
    }

    fun addSource(link: String) = editSources { core.addSource(link) }

    fun removeSource(id: String) = editSources { core.removeSource(id) }

    fun resetSources() = editSources { core.resetSources() }

    fun importObtainium(json: String) = editSources { core.importObtainium(json) }

    /** Runs a source change on [io], then reloads the source list and the catalogue. */
    private fun editSources(change: () -> Unit) {
        viewModelScope.launch {
            try {
                val sources = withContext(io) {
                    change()
                    core.sources()
                }
                _state.update { it.copy(sources = sources) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(errorText(e))
            }
        }
    }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun setBusy(id: String, state: AppState?) {
        if (state == null) busy.remove(id) else busy[id] = state
        publish()
    }

    private fun publish() {
        val apps = entries.map { entry ->
            val pkg = packageName(entry)
            val version = installed.getOrPut(pkg) { packages.installedVersion(pkg) }
            AppUi(entry, busy[entry.id] ?: appState(entry, version, isNewer))
        }
        _state.update { it.copy(apps = apps) }
    }
}
