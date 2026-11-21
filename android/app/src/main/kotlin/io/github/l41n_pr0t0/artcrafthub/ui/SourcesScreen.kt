package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l41n_pr0t0.artcrafthub.core.Source
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Faint
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Hairline
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Ink
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Mono
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.glass

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

    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()).wrapContentWidth().widthIn(max = MaxContentWidth + 36.dp),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "add") {
            Panel("Add a link") {
                Text(
                    "The hub only checks these GitHub links. Paste a link to an ArtCraft repo or to the " +
                        "artcraft-android organisation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Paper.copy(alpha = 0.86f),
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("github.com/artcraft-android/procraft", style = Mono, color = Faint) },
                    textStyle = Mono.copy(color = Paper),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Hairline,
                        focusedBorderColor = Paper.copy(alpha = 0.6f),
                        cursorColor = Paper,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SolidButton("Add", add, Modifier.weight(1f), enabled = link.isNotBlank(), icon = Glyphs.Plus)
                    GhostButton(
                        "Paste",
                        { clipboard.getText()?.text?.let { link = it.trim() } },
                        Modifier.weight(1f),
                        icon = Glyphs.Paste,
                    )
                }
            }
        }
        item(key = "label") {
            SectionLabel("Watched links", Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp), trailing = "${sources.size}")
        }
        items(sources, key = { it.id }) { source ->
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.Link, null, Modifier.size(18.dp), tint = Paper)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.id, style = MaterialTheme.typography.titleSmall, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (source.repo == null) "All ArtCraft apps" else source.url,
                        style = Mono.copy(fontSize = 11.sp),
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemove(source.id) }) {
                    Icon(Glyphs.Remove, "Remove ${source.id}", Modifier.size(18.dp), tint = Muted)
                }
            }
        }
        item(key = "import") {
            Spacer(Modifier.size(6.dp))
            Panel("Obtainium") {
                GhostButton(
                    "Import from Obtainium",
                    { picker.launch(arrayOf("application/json", "*/*")) },
                    Modifier.fillMaxWidth(),
                    icon = Glyphs.Import,
                )
                Text(
                    "In Obtainium: Settings → Export. Only ArtCraft apps in the file are added.",
                    style = Mono.copy(fontSize = 11.sp),
                    color = Muted,
                )
            }
        }
        item(key = "reset") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { confirmReset = true }) {
                    Icon(Glyphs.Reset, null, Modifier.size(16.dp), tint = Muted)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to default", color = Muted)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Color(0xFF151517),
            shape = PanelShape,
            title = { Text("Reset sources?", color = Paper) },
            text = { Text("Your links are removed and only the ArtCraft organisation is checked.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Reset", color = Paper) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Muted) } },
        )
    }
}
