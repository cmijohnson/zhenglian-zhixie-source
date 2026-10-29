package com.eagleslab.yingge.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TransientReadRetryTest {
    @Test
    fun `transient read is retried exactly once`() = runTest {
        var attempts = 0

        val result = retryTransientRead(initialBackoffMillis = 0) {
            attempts += 1
            if (attempts == 1) throw RepositoryException("NETWORK_IO_FAILED", "stale connection")
            "loaded"
        }

        assertEquals("loaded", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `business and validation errors are never retried`() = runTest {
        var attempts = 0
        val expected = RepositoryException("FORBIDDEN", "not allowed", httpStatus = 403)

        val actual = runCatching {
            retryTransientRead(initialBackoffMillis = 0) {
                attempts += 1
                throw expected
            }
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals(1, attempts)
    }

    @Test
    fun `persistent transport failure stops after the configured limit`() = runTest {
        var attempts = 0

        val actual = runCatching {
            retryTransientRead(maxAttempts = 3, initialBackoffMillis = 0) {
                attempts += 1
                throw RepositoryException("NETWORK_CONNECT_FAILED", "offline")
            }
        }.exceptionOrNull()

        assertEquals("NETWORK_CONNECT_FAILED", (actual as RepositoryException).code)
        assertEquals(3, attempts)
    }
}
