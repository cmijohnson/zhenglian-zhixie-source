package com.eagleslab.yingge.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.eagleslab.yingge.model.SessionUser

object Routes {
    const val DASHBOARD = "dashboard"
    const val CUSTOMERS = "customers"
    const val REMINDERS = "reminders"
    const val DAILY = "daily-summary"
    const val NOTIFICATIONS = "notifications"
    const val MORE = "more"
    const val CUSTOMER_NEW = "customers/new"
    const val CUSTOMER_DETAIL = "customers/{customerId}"
    const val CUSTOMER_EDIT = "customers/{customerId}/edit"
    const val TIMELINE = "customers/{customerId}/timeline"
    const val SETTINGS = "settings"
    const val QQ = "qq"
    const val REGISTER = "register"
    const val MODULE = "module/{moduleKey}"
    const val LEADERSHIP = "leadership"
    const val ADMIN_OVERVIEW = "admin/overview"
    const val ADMIN_PEOPLE = "admin/people"
    const val ADMIN_GOVERNANCE = "admin/governance"
    const val ADMIN_LEADERSHIP = "admin/leadership"
    const val ADMIN_MORE = "admin/more"
    const val ADMIN_MODULE = "admin/module/{moduleKey}"

    fun detail(id: String) = "customers/$id"
    fun edit(id: String) = "customers/$id/edit"
    fun timeline(id: String) = "customers/$id/timeline"
    fun module(key: String) = "module/$key"
    fun adminModule(key: String) = "admin/module/$key"
}

data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

fun bottomDestinations(user: SessionUser): List<BottomDestination> = listOf(
    BottomDestination(Routes.DASHBOARD, "工作台", Icons.Rounded.Dashboard),
    BottomDestination(Routes.CUSTOMERS, "客户", Icons.Rounded.People),
    BottomDestination(Routes.REMINDERS, "提醒", Icons.Rounded.Alarm),
    if (user.canWrite) BottomDestination(Routes.DAILY, "每日小结", Icons.Rounded.EditNote)
    else BottomDestination(Routes.NOTIFICATIONS, "通知", Icons.Rounded.Notifications),
    BottomDestination(Routes.MORE, "更多", Icons.Rounded.MoreHoriz),
)

val adminBottomDestinations: List<BottomDestination> = listOf(
    BottomDestination(Routes.ADMIN_OVERVIEW, "管理概览", Icons.Rounded.AdminPanelSettings),
    BottomDestination(Routes.ADMIN_PEOPLE, "成员团队", Icons.Rounded.Groups),
    BottomDestination(Routes.ADMIN_GOVERNANCE, "业务治理", Icons.Rounded.Gavel),
    BottomDestination(Routes.ADMIN_LEADERSHIP, "经营看板", Icons.Rounded.QueryStats),
    BottomDestination(Routes.ADMIN_MORE, "管理更多", Icons.Rounded.MoreHoriz),
)

fun String.isRootDestination(user: SessionUser): Boolean = bottomDestinations(user).any { it.route == this }
fun String.isAdminRootDestination(): Boolean = adminBottomDestinations.any { it.route == this }
