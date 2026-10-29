package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.ActorDto
import com.eagleslab.yingge.data.contract.DealRequestDto
import com.eagleslab.yingge.data.contract.toModel
import com.eagleslab.yingge.model.DealRequestStatus
import com.eagleslab.yingge.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMappingTest {
    @Test
    fun `actor roles are capability inputs not client selected values`() {
        val actor = ActorDto("u", "m", "o", "member", "员工", listOf("MEMBER", "UNKNOWN_FUTURE_ROLE"), false).toModel()
        assertEquals(setOf(UserRole.MEMBER), actor.roles)
        assertTrue(actor.canWrite)
    }

    @Test
    fun `deal request keeps contract status and version`() {
        val request = DealRequestDto("r", "c", "客户", null, "负责人", "m", "员工", "PENDING", "2026-08-22T00:00:00Z", null, null, 7).toModel()
        assertEquals(DealRequestStatus.PENDING, request.status)
        assertEquals(7, request.version)
    }
}

