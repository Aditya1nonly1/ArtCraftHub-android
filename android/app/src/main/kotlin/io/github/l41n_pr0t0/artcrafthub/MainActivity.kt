package io.github.l41n_pr0t0.artcrafthub

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.l41n_pr0t0.artcrafthub.core.RustCore
import io.github.l41n_pr0t0.artcrafthub.core.isNewer
import io.github.l41n_pr0t0.artcrafthub.system.AndroidPackageGateway
import io.github.l41n_pr0t0.artcrafthub.system.openInObtainium
import io.github.l41n_pr0t0.artcrafthub.ui.CatalogScreen
import io.github.l41n_pr0t0.artcrafthub.ui.DetailScreen
import io.github.l41n_pr0t0.artcrafthub.ui.GhostButton
import io.github.l41n_pr0t0.artcrafthub.ui.Glyphs
import io.github.l41n_pr0t0.artcrafthub.ui.HubHeader
import io.github.l41n_pr0t0.artcrafthub.ui.HubViewModel
import io.github.l41n_pr0t0.artcrafthub.ui.IconLoader
import io.github.l41n_pr0t0.artcrafthub.ui.PrefsPackageNames
import io.github.l41n_pr0t0.artcrafthub.ui.SettingsScreen
import io.github.l41n_pr0t0.artcrafthub.ui.SourcesScreen
import io.github.l41n_pr0t0.artcrafthub.ui.theme.HubTheme
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.hubBackground
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
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
                    // landscape: keep clear of the camera cutout and a side navigation bar
                    modifier = Modifier.hubBackground()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    containerColor = Color.Transparent,
                    topBar = {
                        when {
                            selected != null -> HubHeader(
                                "Details",
                                model.packageName(selected.entry),
                                onBack = goBack,
                            )
                            screen == Screen.Sources -> HubHeader("Sources", "GitHub links to check", onBack = goBack)
                            screen == Screen.Settings -> HubHeader("Settings", "About · cache · licences", onBack = goBack)
                            else -> HubHeader(
                                "ArtCraft",
                                "Unified Hub",
                                onBack = null,
                                actions = listOf(
                                    Glyphs.Link to ("Sources" to { screen = Screen.Sources }),
                                    Glyphs.Settings to ("Settings" to { screen = Screen.Settings }),
                                ),
                            )
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(snackbar) { data ->
                            Snackbar(
                                data,
                                shape = RoundedCornerShape(18.dp),
                                containerColor = Color(0xFF1C1C1F),
                                contentColor = Paper,
                            )
                        }
                    },
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
                                GhostButton("Track in Obtainium", {
                                    val app = selected.entry
                                    openInObtainium(this@MainActivity, app.repoUrl, app.packageName, app.name)
                                }, compact = true, icon = Glyphs.OpenExternal)
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
