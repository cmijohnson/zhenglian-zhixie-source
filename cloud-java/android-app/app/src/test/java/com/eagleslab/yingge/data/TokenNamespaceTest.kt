package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.TokenNamespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TokenNamespaceTest {
    @Test fun `refresh namespaces are stable and isolated by canonical origin`() {
        val first = TokenNamespace.key("https://cloud.example.com")
        assertEquals(first, TokenNamespace.key("https://cloud.example.com"))
        assertNotEquals(first, TokenNamespace.key("https://other.example.com"))
        assertFalse(first.contains("cloud.example.com"))
    }
}
