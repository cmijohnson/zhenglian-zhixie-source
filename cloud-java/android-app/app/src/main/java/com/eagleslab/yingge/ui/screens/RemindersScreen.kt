package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.Reminder
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val reminderTime = DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.of("Asia/Shanghai"))

@Composable
fun RemindersScreen(rows: List<Reminder>, onComplete: (String) -> Unit, onCustomer: (String) -> Unit) {
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val visible = rows.filter { showCompleted || !it.completed }
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("REMINDERS", "跟进提醒", "逾期事项优先显示，完成后仍保留历史。") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted }, label = { Text("包含已完成") }, modifier = Modifier.heightIn(min = 44.dp)) } }
        if (visible.isEmpty()) item { EmptyPane("没有待办提醒", "新的客户跟进计划会出现在这里。") }
        items(visible, key = Reminder::id) { item ->
            YinggeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text(item.customerName, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
                    AssistChip(onClick = {}, enabled = false, label = { Text(if (item.completed) "已完成" else if (item.overdue) "已逾期" else "待处理") }, leadingIcon = { Icon(if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule, contentDescription = null) })
                }
                Text(reminderTime.format(item.remindAt), color = if (item.overdue && !item.completed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onCustomer(item.customerId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("查看客户") }
                    if (!item.completed) Button(onClick = { onComplete(item.id) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(13.dp)) { Text("完成") }
                }
            }
        }
    }
}
