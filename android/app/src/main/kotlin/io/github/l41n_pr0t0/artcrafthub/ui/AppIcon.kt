package io.github.l41n_pr0t0.artcrafthub.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.l41n_pr0t0.artcrafthub.core.CoreGateway
import io.github.l41n_pr0t0.artcrafthub.system.PackageGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Finds an app's icon: the installed app's own icon, else the one from its repo. */
class IconLoader(
    private val core: CoreGateway,
    private val packages: PackageGateway,
    private val io: CoroutineDispatcher,
) {
    suspend fun load(appId: String, packageName: String, installed: Boolean): ImageBitmap? = withContext(io) {
        if (installed) packages.icon(packageName)?.let { return@withContext it }
        runCatching { core.icon(appId) }.getOrNull()
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?.asImageBitmap()
    }
}

@Composable
fun AppIcon(app: AppUi, packageName: String, loader: IconLoader, size: Dp = 48.dp) {
    val installed = app.state is AppState.Installed || app.state is AppState.UpdateAvailable
    val icon by produceState<ImageBitmap?>(null, app.entry.id, installed) {
        value = loader.load(app.entry.id, packageName, installed)
    }
    val shape = RoundedCornerShape(size / 4)
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.entry.name.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
