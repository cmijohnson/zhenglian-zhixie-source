package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.ServerOriginValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerOriginTest {
    @Test fun `https root origin is canonicalized`() {
        assertEquals("https://cloud.example.com", ServerOriginValidator.requireValid("https://Cloud.Example.com/", false).canonical)
    }

    @Test(expected = RepositoryException::class)
    fun `http requires explicit confirmation`() { ServerOriginValidator.requireValid("http://192.0.2.10:8080", false) }

    @Test fun `confirmed enterprise http keeps exact port`() {
        assertEquals("http://192.0.2.10:8080", ServerOriginValidator.requireValid("http://192.0.2.10:8080", true).canonical)
    }

    @Test fun `confirmed public http origin is accepted without a host allowlist`() {
        assertEquals("http://203.0.113.10:38080", ServerOriginValidator.requireValid("http://203.0.113.10:38080", true).canonical)
        assertEquals("http://crm.example.test:31900", ServerOriginValidator.requireValid("http://crm.example.test:31900", true).canonical)
    }

    @Test(expected = RepositoryException::class)
    fun `path query and user info are rejected`() { ServerOriginValidator.requireValid("https://user@cloud.example.com/api?x=1", false) }
}
