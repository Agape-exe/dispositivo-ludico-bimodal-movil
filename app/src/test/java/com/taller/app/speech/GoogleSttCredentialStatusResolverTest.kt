package com.taller.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSttCredentialStatusResolverTest {
    @Test
    fun importedCredential_hasPriority() {
        assertEquals(
            GoogleSttCredentialStatus.IMPORTED_DEBUG,
            GoogleSttCredentialStatusResolver.resolve(true, true, true, true)
        )
    }

    @Test
    fun desktopPath_isNotRuntimeCredential() {
        assertEquals(
            GoogleSttCredentialStatus.DESKTOP_PATH_EXISTS,
            GoogleSttCredentialStatusResolver.resolve(false, false, true, true)
        )
    }

    @Test
    fun absentConfiguration_isReported() {
        assertEquals(
            GoogleSttCredentialStatus.NOT_CONFIGURED,
            GoogleSttCredentialStatusResolver.resolve(false, false, false, false)
        )
    }
}
