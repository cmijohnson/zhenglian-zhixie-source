package com.eagleslab.yingge.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDraftValidationTest {
    @Test
    fun `customer requires name and at least one contact before submit`() {
        assertFalse(CustomerDraft(name = "测试客户").canSubmit)
        assertFalse(CustomerDraft(qq = "123456").canSubmit)
        assertTrue(CustomerDraft(name = "测试客户", qq = "123456").canSubmit)
        assertTrue(CustomerDraft(name = "测试客户", wechat = "test_wechat").canSubmit)
    }
}
