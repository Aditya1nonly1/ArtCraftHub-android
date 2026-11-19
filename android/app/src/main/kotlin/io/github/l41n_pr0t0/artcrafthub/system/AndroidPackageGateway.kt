package io.github.l41n_pr0t0.artcrafthub.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** [PackageGateway] backed by the real PackageManager and PackageInstaller. */
class AndroidPackageGateway(context: Context) : PackageGateway {
    private val app = context.applicationContext
    private val pm = app.packageManager

    override fun installedVersion(packageName: String): String? = try {
        pm.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    override fun icon(packageName: String): ImageBitmap? = try {
        pm.getApplicationIcon(packageName).toBitmap(width = 144, height = 144).asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    override fun archivePackageName(apkPath: String): String? = pm.getPackageArchiveInfo(apkPath, 0)?.packageName

    override fun canInstall(): Boolean = pm.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun install(apkPath: String): InstallResult = withContext(Dispatchers.IO) {
        val file = File(apkPath)
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            archivePackageName(apkPath)?.let { setAppPackageName(it) }
            setSize(file.length())
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (e: Exception) {
            return@withContext InstallResult.Failed(e.message ?: "Could not start the install")
        }
        val result = CompletableDeferred<InstallResult>()
        InstallResultReceiver.pending[sessionId] = result
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val callback = Intent(app, InstallResultReceiver::class.java).setPackage(app.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(app, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            InstallResultReceiver.pending.remove(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            return@withContext InstallResult.Failed(e.message ?: "Install failed")
        }
        result.await()
    }

    override fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun uninstall(packageName: String) {
        @Suppress("DEPRECATION") // ACTION_DELETE is the only user-facing uninstall prompt without extra privileges.
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun changes(): Flow<String> = packageChanges(app)
}
