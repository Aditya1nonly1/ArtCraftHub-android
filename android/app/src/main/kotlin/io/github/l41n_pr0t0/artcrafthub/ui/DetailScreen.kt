package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Hairline
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Mono
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Spaced
import io.github.l41n_pr0t0.artcrafthub.ui.theme.glass
import java.util.Locale

val PanelShape = RoundedCornerShape(26.dp)

/** Everything about one app: description, release notes, and what you can do with it. */
@OptIn(ExperimentalLayoutApi::class)
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
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 28.dp)
            .contentWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // the app itself
        Column(
            Modifier.fillMaxWidth().glass(PanelShape).padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.glass(CutCornerShape(26.dp)).padding(14.dp)) {
                AppIcon(app, packageName, icons, size = 84.dp, shape = CutCornerShape(20.dp), tintByState = false)
            }
            Spacer(Modifier.height(18.dp))
            Text(entry.name.uppercase(), style = Spaced.copy(fontSize = 22.sp), color = Paper, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(stateLine(app), style = Mono, color = Muted, textAlign = TextAlign.Center)
            val facts = listOfNotNull(
                entry.latestVersion.takeIf { it.isNotEmpty() },
                "beta".takeIf { entry.prerelease },
                entry.apk?.let { megabytes(it.size.toLong()) },
            )
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    facts.forEach { Chip(it) }
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(app, onPrimary, onCancel, Modifier.fillMaxWidth())
            val s = app.state
            if (s is AppState.Downloading) {
                LinearProgressIndicator(
                    progress = { s.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(PillShape),
                    color = Paper,
                    trackColor = Hairline,
                )
            } else if (s == AppState.Installing) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(PillShape),
                    color = Paper,
                    trackColor = Hairline,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (app.state is AppState.Installed || app.state is AppState.UpdateAvailable) {
                    GhostButton("Uninstall", { onUninstall(entry.id) }, compact = true, icon = Glyphs.Remove)
                }
                extraActions()
            }
        }

        if (entry.description.isNotBlank()) {
            Panel("About") {
                Text(entry.description, style = MaterialTheme.typography.bodyLarge, color = Paper)
            }
        }
        Panel("What's new", entry.latestVersion.takeIf { it.isNotEmpty() }) {
            ReleaseNotes(entry.releaseNotes)
        }
    }
}

/** A titled glass panel. */
@Composable
fun Panel(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(PanelShape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(title, trailing = trailing)
        content()
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = Mono.copy(fontSize = 11.sp),
        color = Muted,
        modifier = Modifier.border(1.dp, Hairline, PillShape).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** GitHub release notes, with the bits of Markdown they use (headings, bullets, bold, links) tidied up. */
@Composable
private fun ReleaseNotes(markdown: String) {
    val lines = markdownLines(markdown)
    if (lines.isEmpty()) {
        Text("No release notes.", style = Mono, color = Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            when (line) {
                is NoteLine.Heading -> Text(
                    line.text,
                    style = MaterialTheme.typography.titleSmall,
                    color = Paper,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is NoteLine.Bullet -> Row {
                    Text("·", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.width(16.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Paper.copy(alpha = 0.86f))
                }
                is NoteLine.Para -> Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Paper.copy(alpha = 0.86f))
            }
        }
    }
}

sealed interface NoteLine {
    val text: String
    data class Heading(override val text: String) : NoteLine
    data class Bullet(override val text: String) : NoteLine
    data class Para(override val text: String) : NoteLine
}

private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
private val emphasis = Regex("""(\*\*|__|`)""")

/** Splits release notes into headings, bullets and plain lines; blank lines are dropped. */
fun markdownLines(markdown: String): List<NoteLine> = markdown.lines().mapNotNull { raw ->
    val line = raw.trim()
    if (line.isEmpty()) return@mapNotNull null
    fun clean(t: String) = t.replace(link, "$1").replace(emphasis, "").trim()
    when {
        line.startsWith("#") -> NoteLine.Heading(clean(line.trimStart('#')))
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> NoteLine.Bullet(clean(line.drop(2)))
        else -> NoteLine.Para(clean(line))
    }
}.filter { it.text.isNotEmpty() }

/** A size in MB with one decimal, e.g. "14.1 MB". */
fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
