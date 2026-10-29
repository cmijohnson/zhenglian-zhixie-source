package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.NotificationItem
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val notificationTime = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))

@Composable
fun NotificationsScreen(rows: List<NotificationItem>, onRead: (String) -> Unit, onReadAll: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("NOTIFICATIONS", "通知中心", "提醒、审核和系统更新统一归档。") { if (rows.any(NotificationItem::unread)) TextButton(onClick = onReadAll) { Text("全部已读") } } }
        if (rows.isEmpty()) item { EmptyPane("暂无通知", "新消息会及时出现在这里。") }
        items(rows, key = NotificationItem::id) { item -> YinggeCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = if (item.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) { Text(item.title, fontWeight = if (item.unread) FontWeight.Bold else FontWeight.SemiBold); Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 5.dp)); Text(notificationTime.format(item.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)); if (item.unread) TextButton(onClick = { onRead(item.id) }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text("标记已读") } }
            }
        } }
    }
}
