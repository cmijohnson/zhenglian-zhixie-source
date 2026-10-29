package com.eagleslab.yingge.data.contract

import okhttp3.HttpUrl

internal object OriginRequestPolicy {
    private val publicAuthPaths = setOf(
        "/api/v1/mobile/auth/registration-options",
        "/api/v1/mobile/auth/register",
        "/api/v1/mobile/auth/login",
        "/api/v1/mobile/auth/refresh",
    )

    fun isExactOrigin(url: HttpUrl, origin: ServerOrigin): Boolean =
        url.scheme == origin.scheme && url.host.lowercase() == origin.host && url.port == origin.port

    fun shouldAttachAuthorization(encodedPath: String): Boolean = encodedPath !in publicAuthPaths
}
