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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.l41n_pr0t0.artcrafthub.core.RustCore
import io.github.l41n_pr0t0.artcrafthub.core.isNewer
import io.github.l41n_pr0t0.artcrafthub.system.AndroidPackageGateway
import io.github.l41n_pr0t0.artcrafthub.ui.CatalogScreen
import io.github.l41n_pr0t0.artcrafthub.ui.DetailScreen
import io.github.l41n_pr0t0.artcrafthub.ui.HubViewModel
import io.github.l41n_pr0t0.artcrafthub.ui.IconLoader
import io.github.l41n_pr0t0.artcrafthub.ui.PrefsPackageNames
import io.github.l41n_pr0t0.artcrafthub.ui.theme.HubTheme
import kotlinx.coroutines.Dispatchers

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
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.let {
                        snackbar.showSnackbar(it)
                        model.dismissMessage()
                    }
                }
                val selected = state.apps.firstOrNull { it.entry.id == state.selectedId }
                BackHandler(enabled = selected != null) { model.select(null) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(selected?.entry?.name ?: getString(R.string.app_name)) },
                            navigationIcon = {
                                if (selected != null) TextButton(onClick = { model.select(null) }) { Text("Back") }
                            },
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    if (selected != null) {
                        DetailScreen(
                            app = selected,
                            packageName = model.packageName(selected.entry),
                            icons = icons,
                            onPrimary = model::primaryAction,
                            onCancel = model::cancel,
                            onUninstall = model::uninstall,
                            extraActions = {},
                            contentPadding = padding,
                        )
                    } else {
                        CatalogScreen(
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
