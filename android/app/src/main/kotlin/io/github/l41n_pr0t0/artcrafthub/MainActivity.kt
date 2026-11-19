package io.github.l41n_pr0t0.artcrafthub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.l41n_pr0t0.artcrafthub.core.AppEntry
import io.github.l41n_pr0t0.artcrafthub.core.RustCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Temporary screen: lists the catalogue to prove the Rust core works on a device. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val core = RustCore(applicationContext)
        setContent {
            var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            var status by remember { mutableStateOf("Loading…") }
            LaunchedEffect(Unit) {
                apps = withContext(Dispatchers.IO) { core.cachedCatalog() }
                status = try {
                    apps = withContext(Dispatchers.IO) { core.refreshCatalog() }
                    "${apps.size} apps"
                } catch (e: Exception) {
                    "Refresh failed: $e"
                }
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.safeDrawingPadding().padding(16.dp)) {
                        item { Text(status, style = MaterialTheme.typography.titleMedium) }
                        items(apps, key = { it.id }) { app ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(app.name, style = MaterialTheme.typography.titleLarge)
                                Text("${app.latestVersion} · ${app.apk?.fileName ?: "no APK for this phone"}")
                            }
                        }
                    }
                }
            }
        }
    }
}
