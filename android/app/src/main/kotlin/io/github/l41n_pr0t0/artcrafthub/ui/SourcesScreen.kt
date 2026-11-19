package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.l41n_pr0t0.artcrafthub.core.Source

/** Which GitHub links the hub checks: the whole ArtCraft org, or single repos. */
@Composable
fun SourcesScreen(
    sources: List<Source>,
    onAdd: (link: String, onAdded: () -> Unit) -> Unit,
    onRemove: (String) -> Unit,
    onReset: () -> Unit,
    onImport: (String) -> Unit,
    onImportError: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    @Suppress("DEPRECATION") // LocalClipboard is suspend-only; this is a one-shot read on click.
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        if (text == null) onImportError() else onImport(text)
    }
    val add = {
        if (link.isNotBlank()) {
            onAdd(link.trim()) { link = "" }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(contentPadding), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The hub only checks these GitHub links. Paste a link to an ArtCraft repo or to the " +
                        "artcraft-android organisation.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("GitHub link") },
                    placeholder = { Text("github.com/artcraft-android/procraft") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = add, enabled = link.isNotBlank()) { Text("Add") }
                    OutlinedButton(onClick = { clipboard.getText()?.text?.let { link = it.trim() } }) { Text("Paste") }
                }
            }
            HorizontalDivider()
        }
        items(sources, key = { it.id }) { source ->
            ListItem(
                headlineContent = { Text(source.id) },
                supportingContent = { Text(if (source.repo == null) "All ArtCraft apps" else source.url) },
                trailingContent = { TextButton(onClick = { onRemove(source.id) }) { Text("Remove") } },
            )
        }
        item {
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "*/*")) }) {
                    Text("Import from Obtainium")
                }
                Text(
                    "In Obtainium: Settings → Export. Only ArtCraft apps in the file are added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmReset = true }) { Text("Reset to default") }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset sources?") },
            text = { Text("Your links are removed and only the ArtCraft organisation is checked.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
