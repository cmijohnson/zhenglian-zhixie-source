package com.eagleslab.yingge.navigation

import com.eagleslab.yingge.model.SessionUser
import com.eagleslab.yingge.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationPolicyTest {
    @Test
    fun `writable roles receive daily summary and exactly five tabs`() {
        val user = SessionUser("u", "m", "员工", setOf(UserRole.MEMBER))
        val tabs = bottomDestinations(user)
        assertEquals(5, tabs.size)
        assertTrue(tabs.any { it.route == Routes.DAILY })
        assertFalse(tabs.any { it.route == Routes.NOTIFICATIONS })
    }

    @Test
    fun `viewer receives notifications instead of daily summary`() {
        val user = SessionUser("u", "m", "只读", setOf(UserRole.VIEWER))
        val tabs = bottomDestinations(user)
        assertEquals(listOf(Routes.DASHBOARD, Routes.CUSTOMERS, Routes.REMINDERS, Routes.NOTIFICATIONS, Routes.MORE), tabs.map { it.route })
    }

    @Test
    fun `admin mode exposes exactly the five approved native destinations`() {
        assertEquals(
            listOf(
                Routes.ADMIN_OVERVIEW,
                Routes.ADMIN_PEOPLE,
                Routes.ADMIN_GOVERNANCE,
                Routes.ADMIN_LEADERSHIP,
                Routes.ADMIN_MORE,
            ),
            adminBottomDestinations.map { it.route },
        )
    }
}
