package io.github.l41n_pr0t0.artcrafthub.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PackageInstaller.STATUS_* values from the Android docs (android.jar is only stubs in JVM tests). */
private const val SUCCESS = 0
private const val FAILURE = 1
private const val BLOCKED = 2
private const val ABORTED = 3
private const val CONFLICT = 5
private const val STORAGE = 6
private const val INCOMPATIBLE = 7

class InstallResultTest {
    @Test fun success() = assertEquals(InstallResult.Success, installResultFrom(SUCCESS, null))

    @Test fun abortedIsCancelled() = assertEquals(InstallResult.Cancelled, installResultFrom(ABORTED, "x"))

    @Test fun conflictIsSignatureConflict() =
        assertEquals(InstallResult.SignatureConflict, installResultFrom(CONFLICT, "x"))

    @Test fun incompatibleFails() = assertTrue(installResultFrom(INCOMPATIBLE, null) is InstallResult.Failed)

    @Test fun failureKeepsMessage() = assertEquals(InstallResult.Failed("x"), installResultFrom(FAILURE, "x"))

    @Test fun otherFailuresHaveAReadableMessage() {
        for (status in listOf(BLOCKED, STORAGE, INCOMPATIBLE)) {
            val r = installResultFrom(status, null)
            assertTrue("$status → $r", r is InstallResult.Failed && r.message.isNotBlank())
        }
    }
}
