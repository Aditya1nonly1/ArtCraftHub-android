package io.github.l41n_pr0t0.artcrafthub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.l41n_pr0t0.artcrafthub.core.RustCore
import io.github.l41n_pr0t0.artcrafthub.core.isNewer
import io.github.l41n_pr0t0.artcrafthub.system.AndroidPackageGateway
import io.github.l41n_pr0t0.artcrafthub.system.openInObtainium
import io.github.l41n_pr0t0.artcrafthub.ui.CatalogScreen
import io.github.l41n_pr0t0.artcrafthub.ui.DetailScreen
import io.github.l41n_pr0t0.artcrafthub.ui.HubViewModel
import io.github.l41n_pr0t0.artcrafthub.ui.IconLoader
import io.github.l41n_pr0t0.artcrafthub.ui.PrefsPackageNames
import io.github.l41n_pr0t0.artcrafthub.ui.SettingsScreen
import io.github.l41n_pr0t0.artcrafthub.ui.SourcesScreen
import io.github.l41n_pr0t0.artcrafthub.ui.theme.HubTheme
import kotlinx.coroutines.Dispatchers

private enum class Screen { Catalog, Sources, Settings }

class MainActivity : ComponentActivity() {
    private val core by lazy { RustCore(applicationContext) }
    private val packages by lazy { AndroidPackageGateway(applicationContext) }

    private val model: HubViewModel by viewModels {
        viewModelFactory {
            initializer {
                HubViewModel(core, packages, Dispatchers.IO, ::isNewer, PrefsPackageNames(applicationContext))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val icons = IconLoader(core, packages, Dispatchers.IO)
        setContent {
            HubTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val hasApps = state.apps.isNotEmpty()
                LaunchedEffect(hasApps) { if (hasApps) reportFullyDrawn() }
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.let {
                        snackbar.showSnackbar(it)
                        model.dismissMessage()
                    }
                }
                var screen by rememberSaveable { mutableStateOf(Screen.Catalog) }
                val selected = state.apps.firstOrNull { it.entry.id == state.selectedId }
                val goBack = {
                    if (selected != null) model.select(null) else screen = Screen.Catalog
                }
                BackHandler(enabled = selected != null || screen != Screen.Catalog, onBack = goBack)
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when {
                                        selected != null -> selected.entry.name
                                        screen == Screen.Sources -> "Sources"
                                        screen == Screen.Settings -> "Settings"
                                        else -> getString(R.string.app_name)
                                    },
                                )
                            },
                            navigationIcon = {
                                if (selected != null || screen != Screen.Catalog) {
                                    TextButton(onClick = goBack) { Text("Back") }
                                }
                            },
                            actions = {
                                if (selected == null && screen == Screen.Catalog) {
                                    TextButton(onClick = { screen = Screen.Sources }) { Text("Sources") }
                                    TextButton(onClick = { screen = Screen.Settings }) { Text("Settings") }
                                }
                            },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    when {
                        selected != null -> DetailScreen(
                            app = selected,
                            packageName = model.packageName(selected.entry),
                            icons = icons,
                            onPrimary = model::primaryAction,
                            onCancel = model::cancel,
                            onUninstall = model::uninstall,
                            extraActions = {
                                TextButton(onClick = { openInObtainium(this@MainActivity, selected.entry.repoUrl) }) {
                                    Text("Track in Obtainium")
                                }
                            },
                            contentPadding = padding,
                        )
                        screen == Screen.Sources -> SourcesScreen(
                            sources = state.sources,
                            onAdd = model::addSource,
                            onRemove = model::removeSource,
                            onReset = model::resetSources,
                            onImport = model::importObtainium,
                            onImportError = { model.showMessage("Couldn't read that file.") },
                            contentPadding = padding,
                        )
                        screen == Screen.Settings -> SettingsScreen(onClearCache = model::clearCache, contentPadding = padding)
                        else -> CatalogScreen(
                            state = state,
                            packageName = { model.packageName(it.entry) },
                            icons = icons,
                            onRefresh = model::refresh,
                            onSelect = model::select,
                            onPrimary = model::primaryAction,
                            onCancel = model::cancel,
                            contentPadding = padding,
                        )
                    }
                }
            }
        }
    }
}
