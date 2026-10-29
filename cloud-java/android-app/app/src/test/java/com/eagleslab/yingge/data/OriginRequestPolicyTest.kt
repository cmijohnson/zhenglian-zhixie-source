package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.OriginRequestPolicy
import com.eagleslab.yingge.data.contract.ServerOrigin
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginRequestPolicyTest {
    @Test fun `public login register and refresh never receive an old bearer`() {
        listOf(
            "/api/v1/mobile/auth/registration-options",
            "/api/v1/mobile/auth/register",
            "/api/v1/mobile/auth/login",
            "/api/v1/mobile/auth/refresh",
        ).forEach { assertFalse(OriginRequestPolicy.shouldAttachAuthorization(it)) }
        assertTrue(OriginRequestPolicy.shouldAttachAuthorization("/api/v1/mobile/bootstrap"))
    }

    @Test fun `origin policy rejects host port and scheme changes`() {
        val origin = ServerOrigin("https", "cloud.example.com", 443)
        assertTrue(OriginRequestPolicy.isExactOrigin("https://cloud.example.com/api/v1/dashboard".toHttpUrl(), origin))
        assertFalse(OriginRequestPolicy.isExactOrigin("https://evil.example.com/api/v1/dashboard".toHttpUrl(), origin))
        assertFalse(OriginRequestPolicy.isExactOrigin("https://cloud.example.com:8443/api/v1/dashboard".toHttpUrl(), origin))
        assertFalse(OriginRequestPolicy.isExactOrigin("http://cloud.example.com/api/v1/dashboard".toHttpUrl(), origin))
    }
}
