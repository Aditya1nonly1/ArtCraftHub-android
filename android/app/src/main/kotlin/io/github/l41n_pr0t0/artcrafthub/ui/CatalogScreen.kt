package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The list of ArtCraft apps, pull down to check for new releases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: HubUiState,
    packageName: (AppUi) -> String,
    icons: IconLoader,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onCancel: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.apps.isEmpty() && !state.refreshing) {
                item { Text("No apps yet. Pull down to check GitHub.", Modifier.padding(16.dp)) }
            }
            items(state.apps, key = { it.entry.id }) { app ->
                AppCard(app, packageName(app), icons, onSelect, onPrimary, onCancel)
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppUi,
    packageName: String,
    icons: IconLoader,
    onSelect: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable { onSelect(app.entry.id) }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, packageName, icons)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stateLine(app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(app, onPrimary, onCancel)
            }
            val s = app.state
            if (s is AppState.Downloading) {
                LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } else if (s == AppState.Installing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
    }
}

/** One line under the app name: version and what's going on. */
fun stateLine(app: AppUi): String {
    val latest = app.entry.latestVersion.ifEmpty { "no release" }
    return when (val s = app.state) {
        AppState.NotInstalled -> "$latest · not installed"
        is AppState.Installed -> "${s.version} · installed"
        is AppState.UpdateAvailable -> "${s.installed} → $latest"
        is AppState.Downloading -> "Downloading ${(s.fraction * 100).toInt()}%"
        AppState.Installing -> "Installing…"
        AppState.NoApk -> "$latest · no APK for this device"
    }
}

@Composable
fun PrimaryButton(app: AppUi, onPrimary: (String) -> Unit, onCancel: (String) -> Unit) {
    val id = app.entry.id
    when (app.state) {
        AppState.NotInstalled -> Button(onClick = { onPrimary(id) }) { Text("Install") }
        is AppState.UpdateAvailable -> Button(onClick = { onPrimary(id) }) { Text("Update") }
        is AppState.Installed -> OutlinedButton(onClick = { onPrimary(id) }) { Text("Open") }
        is AppState.Downloading -> OutlinedButton(onClick = { onCancel(id) }) { Text("Cancel") }
        AppState.Installing -> OutlinedButton(onClick = {}, enabled = false) { Text("Installing") }
        AppState.NoApk -> OutlinedButton(onClick = {}, enabled = false) { Text("No APK") }
    }
}
