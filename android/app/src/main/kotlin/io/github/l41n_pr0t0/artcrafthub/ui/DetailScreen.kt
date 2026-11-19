package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Everything about one app: description, release notes, and what you can do with it. */
@Composable
fun DetailScreen(
    app: AppUi,
    packageName: String,
    icons: IconLoader,
    onPrimary: (String) -> Unit,
    onCancel: (String) -> Unit,
    onUninstall: (String) -> Unit,
    extraActions: @Composable () -> Unit,
    contentPadding: PaddingValues,
) {
    val entry = app.entry
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, packageName, icons, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall)
                Text(stateLine(app), style = MaterialTheme.typography.bodyMedium)
                val facts = listOfNotNull(
                    entry.latestVersion.takeIf { it.isNotEmpty() },
                    "beta".takeIf { entry.prerelease },
                    entry.apk?.let { megabytes(it.size.toLong()) },
                )
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(app, onPrimary, onCancel)
            if (app.state is AppState.Installed || app.state is AppState.UpdateAvailable) {
                TextButton(onClick = { onUninstall(entry.id) }) { Text("Uninstall") }
            }
            extraActions()
        }
        val s = app.state
        if (s is AppState.Downloading) {
            LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
        }
        if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text("What's new in ${entry.latestVersion}", style = MaterialTheme.typography.titleMedium)
        Text(entry.releaseNotes.ifBlank { "No release notes." }, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A size in MB with one decimal, e.g. "14.1 MB". */
fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
