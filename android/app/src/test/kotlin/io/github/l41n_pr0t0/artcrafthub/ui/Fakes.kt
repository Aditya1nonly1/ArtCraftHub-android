package io.github.l41n_pr0t0.artcrafthub.ui

import androidx.compose.ui.graphics.ImageBitmap
import io.github.l41n_pr0t0.artcrafthub.core.ApkInfo
import io.github.l41n_pr0t0.artcrafthub.core.AppEntry
import io.github.l41n_pr0t0.artcrafthub.core.CoreGateway
import io.github.l41n_pr0t0.artcrafthub.core.DownloadedApk
import io.github.l41n_pr0t0.artcrafthub.core.Source
import io.github.l41n_pr0t0.artcrafthub.system.InstallResult
import io.github.l41n_pr0t0.artcrafthub.system.PackageGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
fun repoSource(id: String) = Source(id, id, "https://github.com/artcraft-android/$id")

fun entry(id: String, version: String = "v0.0.7-beta", apk: Boolean = true) = AppEntry(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    description = "",
    packageName = "io.github.artcraftandroid.$id",
    repoUrl = "https://github.com/artcraft-android/$id",
    latestVersion = version,
    prerelease = true,
    releaseNotes = "",
    apk = if (apk) ApkInfo("$id.apk", "https://example.invalid/$id.apk", 1_000u, null) else null,
    iconUrl = "",
)

/** Compares versions as plain strings after dropping a leading "v"; enough for tests. */
val fakeIsNewer: (String, String) -> Boolean = { latest, installed -> latest.removePrefix("v") != installed.removePrefix("v") }

class FakeCore(
    var cached: List<AppEntry> = emptyList(),
    var fresh: List<AppEntry> = emptyList(),
) : CoreGateway {
    var refreshError: Exception? = null
    var addSourceError: Exception? = null
    var sourceList = listOf(Source("artcraft-android", null, "https://github.com/artcraft-android"))
    val downloads = mutableListOf<String>()

    override fun sources() = sourceList
    override fun addSource(link: String): Source {
        addSourceError?.let { throw it }
        val repo = link.substringAfterLast('/')
        return Source(repo, repo, link).also { sourceList = sourceList + it }
    }
    override fun removeSource(id: String) { sourceList = sourceList.filterNot { it.id == id } }
    override fun resetSources() = sourceList
    override fun importObtainium(json: String): List<Source> {
        sourceList = sourceList + importResult
        return importResult
    }
    override fun cachedCatalog() = cached
    var importResult: List<Source> = emptyList()
    var refreshes = 0

    /** Like the real core: the org source covers every app, a repo source only its own. */
    override fun refreshCatalog(): List<AppEntry> {
        refreshError?.let { throw it }
        refreshes++
        if (sourceList.any { it.repo == null }) return fresh
        return fresh.filter { app -> sourceList.any { it.repo == app.id } }
    }
    override fun download(appId: String, onProgress: (Long, Long) -> Unit): DownloadedApk {
        downloads += appId
        onProgress(500, 1_000)
        onProgress(1_000, 1_000)
        return DownloadedApk("/apks/$appId.apk", true)
    }
    override fun cancelDownload(appId: String) = Unit
    override fun icon(appId: String): ByteArray? = null
    override fun clearCache() = Unit
}

class FakePackages : PackageGateway {
    val installed = mutableMapOf<String, String>()
    var allowed = true
    var installResult: InstallResult = InstallResult.Success
    var openedSettings = 0
    val launched = mutableListOf<String>()
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override fun installedVersion(packageName: String) = installed[packageName]
    override fun icon(packageName: String): ImageBitmap? = null
    override fun archivePackageName(apkPath: String) =
        "io.github.artcraftandroid." + apkPath.substringAfterLast('/').removeSuffix(".apk")
    override fun canInstall() = allowed
    override fun openInstallPermissionSettings() { openedSettings++ }
    override suspend fun install(apkPath: String): InstallResult {
        if (installResult == InstallResult.Success) installed[archivePackageName(apkPath)] = "0.0.7-beta"
        return installResult
    }
    override fun launch(packageName: String): Boolean { launched += packageName; return true }
    override fun uninstall(packageName: String) = Unit
    override fun changes(): Flow<String> = events
}
