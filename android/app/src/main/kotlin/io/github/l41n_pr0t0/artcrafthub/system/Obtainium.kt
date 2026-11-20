package io.github.l41n_pr0t0.artcrafthub.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.l41n_pr0t0.artcrafthub.core.obtainiumLink

private const val OBTAINIUM_RELEASES = "https://github.com/ImranR98/Obtainium/releases"

/**
 * Asks Obtainium to track the app (with pre-releases included); opens Obtainium's download page
 * when it isn't installed.
 */
fun openInObtainium(context: Context, repoUrl: String, packageName: String, name: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(obtainiumLink(repoUrl, packageName, name))))
    } catch (_: ActivityNotFoundException) {
        openUrl(context, OBTAINIUM_RELEASES)
    }
}

/** Opens a web page in the browser; does nothing if there is no browser. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing sensible to do.
    }
}
