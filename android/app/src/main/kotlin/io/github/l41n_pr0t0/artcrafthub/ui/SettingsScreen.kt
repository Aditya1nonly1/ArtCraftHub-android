package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l41n_pr0t0.artcrafthub.R
import io.github.l41n_pr0t0.artcrafthub.system.openUrl
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Mono
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Spaced
import io.github.l41n_pr0t0.artcrafthub.ui.theme.glass

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
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().glass(PanelShape).padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Glyphs.Sparkle, null, Modifier.size(34.dp), tint = Paper)
            Spacer(Modifier.height(14.dp))
            Text("ARTCRAFT HUB", style = Spaced.copy(fontSize = 20.sp), color = Paper)
            Text("v$version", style = Mono, color = Muted)
            Spacer(Modifier.height(14.dp))
            Text(
                "An independent companion for the ArtCraft Android apps. Free software under the MIT licence.",
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GhostButton("Source code on GitHub", { openUrl(context, HUB_REPO) }, icon = Glyphs.OpenExternal)
        }
        Panel("Cache") {
            Text(
                "Deletes downloaded APKs, icons and saved GitHub answers. Your sources are kept.",
                style = MaterialTheme.typography.bodyMedium,
                color = Paper.copy(alpha = 0.86f),
            )
            GhostButton("Clear cache", onClearCache, icon = Glyphs.Broom)
        }
        Panel("Open-source licences") {
            GhostButton(if (showLicenses) "Hide licences" else "Show licences", { showLicenses = !showLicenses })
            if (showLicenses) {
                val text = remember(context) {
                    context.resources.openRawResource(R.raw.licenses).use { it.readBytes().decodeToString() }
                }
                Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Muted)
            }
        }
    }
}
