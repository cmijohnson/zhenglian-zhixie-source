package com.eagleslab.yingge.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessModuleFailureTest {
    @Test
    fun `failed pool load becomes retryable offline state instead of endless spinner`() {
        val module = failedBusinessModule("pool", "网络连接失败")

        assertEquals("客户公共池", module.title)
        assertEquals(ModuleAvailability.OFFLINE, module.availability)
        assertTrue(module.errorMessage!!.contains("网络"))
    }
}
