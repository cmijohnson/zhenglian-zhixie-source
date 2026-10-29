package com.eagleslab.yingge.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerInsightStreamTest {
    @Test
    fun `keeps received AI delta when chunked stream ends early`() {
        val lines = ArrayDeque(listOf("event: delta", "data: \"\u5df2\u786e\u8ba4\u5ba2\u6237\u6709\u540e\u7eed\u8ddf\u8fdb\u610f\u5411\"", ""))
        val insight = parseCustomerInsightLines("customer-1") {
            if (lines.isNotEmpty()) lines.removeFirst() else throw IOException("unexpected end of stream")
        }

        assertEquals("已确认客户有后续跟进意向", insight.content)
    }

    @Test
    fun `prefers structured result event over accumulated deltas`() {
        val lines = ArrayDeque(listOf(
            "event: delta", "data: \"\u4e2d\u95f4\u7ed3\u679c\"", "",
            "event: result", "data: {\"content\":\"\u6700\u7ec8\u6d1e\u5bdf\"}", "",
        ))
        val insight = parseCustomerInsightLines("customer-2") { lines.removeFirstOrNull() }

        assertEquals("最终洞察", insight.content)
    }
}
