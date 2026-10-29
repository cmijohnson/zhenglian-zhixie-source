package com.eagleslab.yingge.data.contract

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ensures concurrent 401 responses rotate one refresh token at most once. */
class SingleFlightRefreshGate {
    private val mutex = Mutex()

    suspend fun force(refresh: suspend () -> Unit) = mutex.withLock { refresh() }

    suspend fun refreshIfUnchanged(usedAccessToken: String?, currentAccessToken: () -> String?, refresh: suspend () -> Unit) {
        mutex.withLock {
            if (currentAccessToken() == usedAccessToken) refresh()
        }
    }
}
