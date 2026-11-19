package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.l41n_pr0t0.artcrafthub.R
import io.github.l41n_pr0t0.artcrafthub.system.openUrl

const val HUB_REPO = "https://github.com/l41n-pr0t0/ArtCraftHub-android"

/** Version, source link, licences and cache. */
@Composable
fun SettingsScreen(onClearCache: () -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ArtCraft Hub $version", style = MaterialTheme.typography.titleLarge)
        Text("An independent companion for the ArtCraft Android apps. Free software under the MIT licence.")
        TextButton(onClick = { openUrl(context, HUB_REPO) }) { Text("Source code on GitHub") }
        HorizontalDivider()
        Text("Cache", style = MaterialTheme.typography.titleMedium)
        Text(
            "Deletes downloaded APKs, icons and saved GitHub answers. Your sources are kept.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onClearCache) { Text("Clear cache") }
        HorizontalDivider()
        TextButton(onClick = { showLicenses = !showLicenses }) {
            Text(if (showLicenses) "Hide licences" else "Open-source licences")
        }
        if (showLicenses) {
            val text = remember(context) {
                context.resources.openRawResource(R.raw.licenses).use { it.readBytes().decodeToString() }
            }
            Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
