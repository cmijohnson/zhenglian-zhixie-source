package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.EndpointCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointCatalogTest {
    @Test
    fun `catalog keeps the seventeen non admin mobile contract paths`() {
        assertEquals(17, EndpointCatalog.contractPaths.size)
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/mobile/auth/registration-options"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/mobile/auth/register"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/auth/password"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/mobile/auth/login"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/mobile/devices/push-registration"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/mobile/web-sessions"))
        assertTrue(EndpointCatalog.contractPaths.contains("/api/v1/customers/{customerId}/deal-requests"))
        assertFalse(EndpointCatalog.contractPaths.any { it.startsWith("/api/v1/admin") })
    }

    @Test
    fun `role bounded paths never accept forged organization or QQ long lived tokens`() {
        val normalized = EndpointCatalog.reachableEndpoints.joinToString("\n") { "${it.method} ${it.path}" }.lowercase()
        assertEquals(166, EndpointCatalog.contractOperations.size)
        assertEquals(166, EndpointCatalog.reachableEndpoints.size)
        assertTrue(EndpointCatalog.contractOperations.contains("POST|/api/v1/performance/claims/{claimId}/evidence"))
        assertTrue(EndpointCatalog.contractOperations.contains("GET|/api/v1/mobile/app-releases/{releaseId}/apk"))
        assertTrue(EndpointCatalog.contractOperations.contains("POST|/api/v1/mobile/customers/{customerId}/insight"))
        assertTrue(normalized.contains("/api/v1/admin/deal-requests"))
        assertTrue(normalized.contains("/api/v1/mobile/admin/overview"))
        assertFalse(normalized.contains("organizationid"))
        assertFalse(normalized.contains("qce-token"))
        assertFalse(normalized.contains("bridge-token"))
        assertFalse(normalized.contains("qq-token"))
        assertFalse(normalized.contains("access_token"))
    }
}
