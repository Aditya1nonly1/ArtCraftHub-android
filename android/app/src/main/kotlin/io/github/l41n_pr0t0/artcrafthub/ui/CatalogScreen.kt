package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Faint
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Hairline
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Ink
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Mono
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Spaced
import io.github.l41n_pr0t0.artcrafthub.ui.theme.glass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class Filter(val label: String, val keep: (AppState) -> Boolean) {
    All("All", { true }),
    Installed("Installed", { it is AppState.Installed || it is AppState.UpdateAvailable }),
    Updates("Updates", { it is AppState.UpdateAvailable }),
    Available("Available", { it !is AppState.Installed && it !is AppState.UpdateAvailable }),
}

private val TileShape = CutCornerShape(16.dp)

/** The ArtCraft apps: a ring of every app around the hub's summary, then a filterable grid. */
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
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    val shown = state.apps.filter { filter.keep(it.state) }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        LazyVerticalGrid(
            // wider tiles when the system font is large, so names and buttons still fit
            columns = GridCells.Adaptive(minSize = 98.dp * (1 + (LocalDensity.current.fontScale - 1).coerceIn(0f, 1f) * 0.6f)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "ring") {
                HubRing(state.apps, state.refreshing || !state.loaded, packageName, icons, onSelect)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
                FilterTabs(filter, { filter = it }) { f -> state.apps.count { f.keep(it.state) } }
            }
            if (shown.isEmpty() && state.loaded) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text(
                        when {
                            state.apps.isEmpty() && state.refreshing -> "Checking GitHub…"
                            state.apps.isEmpty() -> "No apps yet. Pull down to check GitHub."
                            filter == Filter.Updates -> "Everything is up to date."
                            filter == Filter.Installed -> "No ArtCraft apps installed yet."
                            else -> "Every app is installed."
                        },
                        style = Mono,
                        color = Muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(shown, key = { it.entry.id }) { app ->
                AppTile(app, packageName(app), icons, onSelect, onPrimary, onCancel, Modifier.animateItem())
            }
        }
    }
}

/** Every app on an orbit around a glass disc counting what's installed. */
@Composable
private fun HubRing(
    apps: List<AppUi>,
    refreshing: Boolean,
    packageName: (AppUi) -> String,
    icons: IconLoader,
    onSelect: (String) -> Unit,
) {
    val spin by rememberInfiniteTransition(label = "ring").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "spin",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        // never taller than about half the screen (landscape, split screen), never huge on tablets
        val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
        val side = minOf(maxWidth.value, screenHeight * 0.52f, 400f).coerceAtLeast(200f).dp
        val orbit = side * 0.355f
        val ringApps = apps.take(12)
        val n = ringApps.size.coerceAtLeast(1)
        val disc: Dp = min(side.value * 0.17f, (2 * PI * orbit.value / n * 0.72).toFloat()).dp
        val core = side * 0.40f
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRing(orbit.toPx(), core.toPx() / 2, spin) }
            ringApps.forEachIndexed { i, app ->
                val angle = -PI / 2 + i * 2 * PI / n
                val hasUpdate = app.state is AppState.UpdateAvailable
                val description = if (hasUpdate) "${app.entry.name}, update available" else app.entry.name
                Box(
                    Modifier
                        .offset(orbit * cos(angle).toFloat(), orbit * sin(angle).toFloat())
                        .size(disc),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .glass(CircleShape)
                            .clickable(onClickLabel = "Open details") { onSelect(app.entry.id) }
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(app, packageName(app), icons, size = disc * 0.64f, shape = CircleShape)
                    }
                    if (hasUpdate) UpdateDot(Modifier.align(Alignment.TopEnd))
                }
            }
            CoreDisc(apps, refreshing, core)
        }
    }
}

@Composable
private fun CoreDisc(apps: List<AppUi>, refreshing: Boolean, size: Dp) {
    // the disc is a drawing: its text scales with the disc, not with the system font size
    val density = LocalDensity.current
    fun Dp.fixed() = with(density) { this@fixed.toSp() }
    val unit = size / 150
    val installed = apps.count { Filter.Installed.keep(it.state) }
    val updates = apps.count { it.state is AppState.UpdateAvailable }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.02f)),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (apps.isEmpty()) {
                Icon(Glyphs.Sparkle, null, Modifier.size(unit * 34), tint = Paper)
                Spacer(Modifier.height(unit * 8))
                Text(if (refreshing) "SYNCING" else "PULL TO SYNC", style = Spaced.copy(fontSize = (unit * 10).fixed()), color = Muted)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$installed",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = (unit * 40).fixed(), lineHeight = (unit * 44).fixed()),
                        color = Paper,
                    )
                    Text(
                        "/${apps.size}",
                        style = Mono.copy(fontSize = (unit * 14).fixed()),
                        color = Faint,
                        modifier = Modifier.padding(bottom = unit * 6, start = 2.dp),
                    )
                }
                Text("INSTALLED", style = Spaced.copy(fontSize = (unit * 9).fixed()), color = Muted)
                Spacer(Modifier.height(unit * 6))
                Text(
                    when (updates) {
                        0 -> "up to date"
                        1 -> "1 update"
                        else -> "$updates updates"
                    },
                    style = Mono.copy(fontSize = (unit * 11).fixed()),
                    color = if (updates > 0) Paper else Faint,
                )
            }
        }
    }
}

/** Hairline circles, tick marks and two diamonds; a few points of light drift slowly round. */
private fun DrawScope.drawRing(orbit: Float, coreRadius: Float, spin: Float) {
    val c = center
    val outer = size.minDimension / 2 - 8.dp.toPx()
    val hair = 1.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.16f), outer, c, style = Stroke(hair))
    drawCircle(Color.White.copy(alpha = 0.05f), outer - 10.dp.toPx(), c, style = Stroke(hair))
    drawCircle(
        Color.White.copy(alpha = 0.07f),
        orbit,
        c,
        style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))),
    )
    drawCircle(Color.White.copy(alpha = 0.10f), coreRadius + 9.dp.toPx(), c, style = Stroke(hair))
    // ticks just inside the outer circle
    val tickIn = outer - 16.dp.toPx()
    val tickOut = outer - 12.dp.toPx()
    for (i in 0 until 72) {
        val a = i * 2 * PI / 72
        val major = i % 6 == 0
        drawLine(
            Color.White.copy(alpha = if (major) 0.16f else 0.06f),
            Offset(c.x + cos(a).toFloat() * tickIn, c.y + sin(a).toFloat() * tickIn),
            Offset(c.x + cos(a).toFloat() * tickOut, c.y + sin(a).toFloat() * tickOut),
            strokeWidth = hair,
        )
    }
    // diamonds at top and bottom
    listOf(c.y - outer, c.y + outer).forEach { y -> drawDiamond(Offset(c.x, y), 4.dp.toPx()) }
    // drifting lights
    rotate(spin, c) {
        for (i in 0 until 4) {
            val a = PI / 4 + i * PI / 2
            val p = Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), p, 9.dp.toPx()),
                9.dp.toPx(),
                p,
            )
            drawCircle(Color.White, 1.6.dp.toPx(), p)
        }
    }
}

private fun DrawScope.drawDiamond(at: Offset, r: Float) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        lineTo(at.x + r, at.y)
        lineTo(at.x, at.y + r)
        lineTo(at.x - r, at.y)
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.9f))
}

@Composable
private fun UpdateDot(modifier: Modifier = Modifier) {
    Box(modifier.size(11.dp).clip(CircleShape).background(Ink).padding(2.dp).clip(CircleShape).background(Paper))
}

/** The sheet's grab handle and the filter tabs, each with a count. */
@Composable
private fun FilterTabs(selected: Filter, onSelect: (Filter) -> Unit, count: (Filter) -> Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(36.dp).height(3.dp).clip(PillShape).background(Paper.copy(alpha = 0.35f)))
        Spacer(Modifier.height(14.dp))
        // spread across the width, and scroll sideways when they don't fit (big fonts, narrow phones)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Filter.entries.forEach { f ->
                val on = f == selected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier
                            .clip(PillShape)
                            .then(
                                if (on) {
                                    Modifier
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, Paper.copy(alpha = 0.55f), PillShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(f) }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.label, style = MaterialTheme.typography.bodyMedium, color = if (on) Paper else Muted)
                        Spacer(Modifier.width(5.dp))
                        Text("${count(f)}", style = Mono.copy(fontSize = 11.sp), color = if (on) Muted else Faint)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.width(18.dp).height(2.dp)
                            .background(if (on) Paper else Color.Transparent),
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(4.dp))
    }
}

/** One app in the grid: a cut-corner glass tile with its icon, name, version and main action. */
@Composable
private fun AppTile(
    app: AppUi,
    packageName: String,
    icons: IconLoader,
    onSelect: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(TileShape)
            .clickable { onSelect(app.entry.id) }
            .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, packageName, icons, size = 50.dp, shape = CutCornerShape(11.dp))
            if (app.state is AppState.UpdateAvailable) UpdateDot(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            app.entry.name,
            style = MaterialTheme.typography.titleSmall,
            color = Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            tileLine(app),
            style = Mono.copy(fontSize = 10.sp),
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(app, onPrimary, onCancel, compact = true)
        when (val s = app.state) {
            is AppState.Downloading -> LinearProgressIndicator(
                progress = { s.fraction },
                modifier = Modifier.padding(top = 8.dp).width(64.dp).clip(PillShape),
                color = Paper,
                trackColor = Hairline,
            )
            AppState.Installing -> LinearProgressIndicator(
                Modifier.padding(top = 8.dp).width(64.dp).clip(PillShape),
                color = Paper,
                trackColor = Hairline,
            )
            else -> Unit
        }
    }
}

/** The tile's short caption: the version that matters, or the progress. */
private fun tileLine(app: AppUi): String {
    val latest = app.entry.latestVersion.ifEmpty { "no release" }
    return when (val s = app.state) {
        AppState.NotInstalled -> latest
        is AppState.Installed -> s.version
        is AppState.UpdateAvailable -> "→ $latest"
        is AppState.Downloading -> "${(s.fraction * 100).toInt()}%"
        AppState.Installing -> "installing"
        AppState.NoApk -> "no apk"
    }
}
