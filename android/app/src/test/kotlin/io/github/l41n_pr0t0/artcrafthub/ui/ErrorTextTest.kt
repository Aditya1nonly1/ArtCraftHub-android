package io.github.l41n_pr0t0.artcrafthub.ui

import io.github.l41n_pr0t0.artcrafthub.core.HubException
import io.github.l41n_pr0t0.artcrafthub.system.InstallResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ErrorTextTest {
    @Test fun rateLimitedShowsLocalResetTime() {
        val reset = 1_800_000_000L
        val hhmm = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochSecond(reset).atZone(ZoneId.systemDefault()))
        assertEquals("GitHub limit reached. Try again after $hhmm.", errorText(HubException.RateLimited(reset.toULong())))
    }

    @Test fun offline() = assertEquals("No internet connection.", errorText(HubException.Offline()))

    @Test fun notArtcraft() =
        assertEquals("Only links from github.com/artcraft-android can be added.", errorText(HubException.NotArtcraft()))

    @Test fun invalidLink() = assertEquals("That doesn't look like a GitHub link.", errorText(HubException.InvalidLink("x")))

    @Test fun checksum() =
        assertEquals("The download was damaged. Please try again.", errorText(HubException.ChecksumMismatch()))

    @Test fun signatureConflict() = assertEquals(
        "This app was installed from another source. Uninstall it first, then install it here.",
        installMessage(InstallResult.SignatureConflict),
    )

    @Test fun successHasNoMessage() = assertEquals(null, installMessage(InstallResult.Success))
}
