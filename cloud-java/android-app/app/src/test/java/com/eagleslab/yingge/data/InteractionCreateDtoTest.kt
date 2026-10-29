package com.eagleslab.yingge.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionCreateDtoTest {
    @Test
    fun `default timeline request does not send tag ids`() {
        val payload = Gson().toJsonTree(
            InteractionCreateDto(
                contactedAt = null,
                channel = "微信",
                summary = "客户回复",
                details = null,
                interactionType = "CUSTOMER_REPLY",
            ),
        ).asJsonObject

        assertFalse(payload.has("tagIds"))
        assertTrue(payload.has("tagUpdateRequested"))
        assertFalse(payload["tagUpdateRequested"].asBoolean)
    }

    @Test
    fun `explicit ordinary tag update remains representable`() {
        val payload: JsonObject = Gson().toJsonTree(
            InteractionCreateDto(
                contactedAt = null,
                channel = "微信",
                summary = "更新普通标签",
                details = null,
                tagIds = emptySet(),
                interactionType = "CUSTOMER_REPLY",
                tagUpdateRequested = true,
            ),
        ).asJsonObject

        assertTrue(payload.has("tagIds"))
        assertTrue(payload["tagIds"].asJsonArray.isEmpty)
        assertTrue(payload["tagUpdateRequested"].asBoolean)
    }
}
