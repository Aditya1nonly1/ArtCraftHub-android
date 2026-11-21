package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Faint
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Hairline
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Mono
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Spaced

/**
 * The bar at the top of every screen: a mark (or a back arrow), a wide-tracked title with a
 * monospace subtitle, and any actions on the right, each set off by a thin rule.
 */
@Composable
fun HubHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    actions: List<Pair<ImageVector, Pair<String, () -> Unit>>> = emptyList(),
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Glyphs.Back, "Back", Modifier.size(22.dp), tint = Paper) }
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Glyphs.Sparkle, null, Modifier.size(24.dp), tint = Paper)
            }
        }
        VRule()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = Spaced.copy(fontSize = 19.sp),
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = Mono, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions.forEachIndexed { i, (icon, action) ->
            if (i > 0) VRule()
            IconButton(onClick = action.second) { Icon(icon, action.first, Modifier.size(23.dp), tint = Paper) }
        }
    }
}

/** A short vertical hairline between header items. */
@Composable
fun VRule() {
    Box(Modifier.width(1.dp).height(26.dp).background(Hairline))
}

/** A small wide-tracked caption above a group: "WHAT'S NEW · v0.0.7". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text.uppercase(), style = Spaced.copy(fontSize = 11.sp), color = Muted)
        if (trailing != null) Text(trailing, style = Mono.copy(fontSize = 11.sp), color = Faint)
    }
}

val PillShape = RoundedCornerShape(50)

/** A light, filled pill: the one thing to do. */
@Composable
fun SolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        contentPadding = if (compact) CompactPadding else ButtonDefaults.ContentPadding,
        modifier = if (compact) modifier.height(30.dp) else modifier.height(46.dp),
    ) {
        ButtonContent(text, icon, compact)
    }
}

/** A hairline pill for everything else. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, if (enabled) Paper.copy(alpha = 0.28f) else Hairline),
        contentPadding = if (compact) CompactPadding else ButtonDefaults.ContentPadding,
        modifier = if (compact) modifier.height(30.dp) else modifier.height(46.dp),
    ) {
        ButtonContent(text, icon, compact)
    }
}

private val CompactPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, compact: Boolean) {
    if (icon != null) {
        Icon(icon, null, Modifier.size(if (compact) 14.dp else 18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(
        text,
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

/** Install, Update, Open or Cancel, depending on the app's state. */
@Composable
fun PrimaryButton(
    app: AppUi,
    onPrimary: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val id = app.entry.id
    when (app.state) {
        AppState.NotInstalled -> SolidButton("Install", { onPrimary(id) }, modifier, compact = compact)
        is AppState.UpdateAvailable -> SolidButton("Update", { onPrimary(id) }, modifier, compact = compact)
        is AppState.Installed -> GhostButton("Open", { onPrimary(id) }, modifier, compact = compact)
        is AppState.Downloading -> GhostButton("Cancel", { onCancel(id) }, modifier, compact = compact)
        AppState.Installing -> GhostButton("Installing", {}, modifier, enabled = false, compact = compact)
        AppState.NoApk -> GhostButton("No APK", {}, modifier, enabled = false, compact = compact)
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
