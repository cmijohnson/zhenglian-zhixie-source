package com.eagleslab.yingge.data

import com.eagleslab.yingge.model.DealRequestStatus
import com.eagleslab.yingge.model.DealState
import com.eagleslab.yingge.model.WorkTaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MockYinggeRepositoryTest {
    @Test
    fun `employee deal request is bodyless and remains pending for web review`() = runTest {
        val repository = MockYinggeRepository()
        val before = repository.customer("c-1")
        assertTrue(before.canRequestDeal)
        assertEquals(DealState.NONE, before.dealState)

        val request = repository.requestDeal(before.id, UUID.randomUUID().toString())
        assertEquals(DealRequestStatus.PENDING, request.status)
        val pending = repository.customer(before.id)
        assertEquals(DealState.PENDING, pending.dealState)
        assertFalse(pending.canRequestDeal)

        assertEquals(DealRequestStatus.PENDING, request.status)
        assertEquals(DealState.PENDING, repository.customer(before.id).dealState)
    }

    @Test(expected = RepositoryException::class)
    fun `duplicate deal request is rejected`() = runTest {
        val repository = MockYinggeRepository()
        repository.requestDeal("c-1", UUID.randomUUID().toString())
        repository.requestDeal("c-1", UUID.randomUUID().toString())
    }

    @Test
    fun `registration options precede registration and login`() = runTest {
        val repository = MockYinggeRepository()
        val options = repository.registrationOptions("https://cloud.example.com", "invite")
        assertTrue(options.teams.isNotEmpty())
        val user = repository.registerAndLogin("https://cloud.example.com", "new-member", "新成员", "password8", "invite", options.teams.first().id)
        assertEquals("新成员", user.displayName)
    }

    @Test
    fun `employee task supports claim and submit state flow`() = runTest {
        val repository = MockYinggeRepository()
        val open = repository.tasks().first { it.status == WorkTaskStatus.OPEN }
        assertEquals(WorkTaskStatus.CLAIMED, repository.claimTask(open.id).status)
        assertEquals(WorkTaskStatus.SUBMITTED, repository.submitTask(open.id, "完成客户回访").status)
    }
}
