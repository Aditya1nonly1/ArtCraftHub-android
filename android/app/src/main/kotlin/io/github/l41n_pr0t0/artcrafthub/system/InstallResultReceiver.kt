package io.github.l41n_pr0t0.artcrafthub.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/** Receives PackageInstaller session results and hands them to the waiting [AndroidPackageGateway.install]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        pending.remove(sessionId)?.complete(installResultFrom(status, message))
    }

    companion object {
        /** Installs waiting for a final status, by session id. */
        val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()
    }
}
