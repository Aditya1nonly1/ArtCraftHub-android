package io.github.l41n_pr0t0.artcrafthub.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.l41n_pr0t0.artcrafthub.core.CoreGateway
import io.github.l41n_pr0t0.artcrafthub.system.PackageGateway
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Muted
import io.github.l41n_pr0t0.artcrafthub.ui.theme.Paper
import io.github.l41n_pr0t0.artcrafthub.ui.theme.glass
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

/** Not-installed apps are shown in grey; an app takes on its colours once it's on the device. */
private val Grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AppIcon(
    app: AppUi,
    packageName: String,
    loader: IconLoader,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(size / 4),
    tintByState: Boolean = true,
) {
    val installed = app.state is AppState.Installed || app.state is AppState.UpdateAvailable
    val icon by produceState<ImageBitmap?>(null, app.entry.id, installed) {
        value = loader.load(app.entry.id, packageName, installed)
    }
    val grey = tintByState && !installed
    val current = icon
    if (current != null) {
        Image(
            current,
            contentDescription = null,
            colorFilter = if (grey) Grey else null,
            alpha = if (grey) 0.78f else 1f,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).glass(shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.entry.name.take(1),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                fontSize = (size.value * 0.42f).sp,
                color = if (grey) Muted else Paper,
            )
        }
    }
}
