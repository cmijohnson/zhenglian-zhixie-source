package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.data.ThemePreference
import com.eagleslab.yingge.model.SessionUser
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard

@Composable
fun MoreScreen(
    user: SessionUser,
    theme: ThemePreference,
    capabilities: Set<String>,
    adminModeAvailable: Boolean,
    onTheme: (ThemePreference) -> Unit,
    onNotifications: () -> Unit,
    onLeadership: () -> Unit,
    onAdminMode: () -> Unit,
    onQq: () -> Unit,
    onModule: (String) -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("MORE", "更多", "常用入口按当前角色自动裁剪。") }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                Text(user.roles.joinToString(" · ") { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                if (adminModeAvailable) Button(onClick = onAdminMode, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("进入管理模式")
                }
            }
        }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Text("效率工具", style = MaterialTheme.typography.titleMedium)
                MoreRow(Icons.Rounded.Notifications, "通知中心", onNotifications)
                if ("LEADERSHIP_DASHBOARD_READ" in capabilities) MoreRow(Icons.Rounded.QueryStats, "经营看板", onLeadership)
                if ("TEAM_DATA" in capabilities) MoreRow(Icons.Rounded.Groups, "团队数据", { onModule("team_data") })
                if ("MARKET_INSIGHTS_READ" in capabilities) MoreRow(Icons.Rounded.Insights, "市场洞察", { onModule("market_insights") })
                if ("QQ_WEB_WORKBENCH" in capabilities) MoreRow(Icons.Rounded.Chat, "QQ 对话工作台", onQq)
                if ("POOL_CLAIM" in capabilities) MoreRow(Icons.Rounded.Hub, "客户公共池", { onModule("pool") })
                if ("SECOND_LINE_CLAIM" in capabilities) MoreRow(Icons.Rounded.GroupAdd, "A 类二线协作", { onModule("second_line") })
                if ("TASKS_READ" in capabilities) MoreRow(Icons.Rounded.TaskAlt, "任务中心", { onModule("tasks") })
                if ("PERFORMANCE_WRITE" in capabilities) MoreRow(Icons.Rounded.QueryStats, "绩效中心", { onModule("performance") })
                if ("CONSULTATION_WRITE" in capabilities) MoreRow(Icons.Rounded.Handshake, "咨询交接", { onModule("consultations") })
                if ("CONTENT_WRITE" in capabilities) MoreRow(Icons.Rounded.EditNote, "内容任务", { onModule("content_tasks") })
                if ("CONTENT_READ" in capabilities) MoreRow(Icons.Rounded.DynamicFeed, "内容圈子", { onModule("circle") })
                if ("ANNOUNCEMENTS_READ" in capabilities) MoreRow(Icons.Rounded.Campaign, "公告中心", { onModule("announcements") })
                if ("ASSISTANT_READ" in capabilities) MoreRow(Icons.Rounded.AutoAwesome, "智能助手与客户洞察", { onModule("assistant") })
                if ("BULK_TRANSFER" in capabilities) MoreRow(Icons.Rounded.ImportExport, "客户导入导出", { onModule("data_transfer") })
                if ("RELEASES_READ" in capabilities) MoreRow(Icons.Rounded.NewReleases, "更新日志", { onModule("releases") })
                if ("AGENT_SELF_MANAGE" in capabilities) MoreRow(Icons.Rounded.Extension, "我的 Agent", { onModule("agent") })
                if ("DEVICE_SELF_MANAGE" in capabilities) MoreRow(Icons.Rounded.Devices, "设备管理", { onModule("devices") })
                MoreRow(Icons.Rounded.Settings, "服务器与设备", onSettings)
            }
        }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Text("显示模式", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { item -> FilterChip(selected = theme == item, onClick = { onTheme(item) }, label = { Text(when (item) { ThemePreference.SYSTEM -> "跟随系统"; ThemePreference.LIGHT -> "浅色"; ThemePreference.DARK -> "深色" }) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) }
                }
            }
        }
        item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.Logout, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("安全退出") } }
    }
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
