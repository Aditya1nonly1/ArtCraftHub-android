package io.github.l41n_pr0t0.artcrafthub.core

import android.content.Context
import android.os.Build

/**
 * Everything the UI needs from the Rust core. An interface so view models can be tested with a
 * fake. All calls block: call them from `Dispatchers.IO`.
 */
interface CoreGateway {
    fun sources(): List<Source>
    fun addSource(link: String): Source
    fun removeSource(id: String)
    fun resetSources(): List<Source>
    fun importObtainium(json: String): List<Source>
    fun cachedCatalog(): List<AppEntry>
    fun refreshCatalog(): List<AppEntry>
    fun download(appId: String, onProgress: (downloaded: Long, total: Long) -> Unit): DownloadedApk
    fun cancelDownload(appId: String)
    fun icon(appId: String): ByteArray?
    fun clearCache()
}

/** The real core: the Rust `Hub`, keeping its files in the app's private storage. */
class RustCore(context: Context) : CoreGateway {
    // Lazy so the native library loads and the files are read on the first (background) call,
    // not on the main thread while the first frame is being drawn.
    private val hub by lazy {
        Hub(context.filesDir.absolutePath, context.cacheDir.absolutePath, Build.SUPPORTED_ABIS.toList())
    }

    override fun sources() = hub.sources()
    override fun addSource(link: String) = hub.addSource(link)
    override fun removeSource(id: String) = hub.removeSource(id)
    override fun resetSources() = hub.resetSources()
    override fun importObtainium(json: String) = hub.importObtainium(json)
    override fun cachedCatalog() = hub.cachedCatalog()
    override fun refreshCatalog() = hub.refreshCatalog()

    override fun download(appId: String, onProgress: (downloaded: Long, total: Long) -> Unit): DownloadedApk =
        hub.download(
            appId,
            object : DownloadProgress {
                override fun onProgress(downloaded: ULong, total: ULong) = onProgress(downloaded.toLong(), total.toLong())
            },
        )

    override fun cancelDownload(appId: String) = hub.cancelDownload(appId)
    override fun icon(appId: String) = hub.icon(appId)
    override fun clearCache() = hub.clearCache()
}
