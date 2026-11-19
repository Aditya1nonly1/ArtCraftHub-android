package io.github.l41n_pr0t0.artcrafthub.ui

import io.github.l41n_pr0t0.artcrafthub.core.HubException
import io.github.l41n_pr0t0.artcrafthub.system.InstallResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** A sentence a user can act on, for any error the core or Android throws. */
fun errorText(e: Throwable): String = when (e) {
    is HubException.RateLimited -> {
        val at = clock.format(Instant.ofEpochSecond(e.resetAt.toLong()).atZone(ZoneId.systemDefault()))
        "GitHub limit reached. Try again after $at."
    }
    is HubException.Offline -> "No internet connection."
    is HubException.NotArtcraft -> "Only links from github.com/artcraft-android can be added."
    is HubException.InvalidLink -> "That doesn't look like a GitHub link."
    is HubException.ChecksumMismatch -> "The download was damaged. Please try again."
    is HubException.NotFound -> "Not found on GitHub."
    is HubException.Cancelled -> "Download cancelled."
    is HubException.Io -> "Something went wrong: ${e.detail}"
    is HubException.Parse -> "GitHub sent an answer the hub didn't understand."
    else -> e.message ?: "Something went wrong."
}

/** The message to show after an install, or null when there's nothing to say. */
fun installMessage(result: InstallResult): String? = when (result) {
    InstallResult.Success, InstallResult.Cancelled -> null
    InstallResult.SignatureConflict ->
        "This app was installed from another source. Uninstall it first, then install it here."
    is InstallResult.Failed -> result.message
}
