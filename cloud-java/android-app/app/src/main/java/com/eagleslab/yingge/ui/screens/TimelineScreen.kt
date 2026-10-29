package com.eagleslab.yingge.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.Interaction
import com.eagleslab.yingge.model.InteractionDraft
import com.eagleslab.yingge.model.InteractionType
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.ExpandableText
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timelineTime = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(customerName: String, customerId: String, rows: List<Interaction>, canWrite: Boolean, busy: Boolean, serverNow: Instant, initialEditorOpen: Boolean = false, onBack: () -> Unit, onSave: (InteractionDraft) -> Unit) {
    var showEditor by rememberSaveable { mutableStateOf(initialEditorOpen) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回客户档案") } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageHeading("TIMELINE", "$customerName 的时间线", "实际发生时间和录入时间分别保存。") }
            if (rows.isEmpty()) item { EmptyPane("暂无跟进记录", "点击下方按钮记录一次真实沟通。") }
            items(rows, key = Interaction::id) { item ->
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("${item.channel} · ${item.type.label}", style = MaterialTheme.typography.titleMedium)
                    Text(timelineTime.format(item.occurredAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
                    ExpandableText(item.summary, modifier = Modifier.padding(top = 10.dp))
                    if (item.details.isNotBlank()) ExpandableText(item.details, collapsedLines = 2, modifier = Modifier.padding(top = 8.dp))
                    Text("${item.creatorName} · 录入 ${timelineTime.format(item.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        if (canWrite) Surface(shadowElevation = 8.dp) { Button(onClick = { showEditor = true }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Add, contentDescription = null); Text("记录沟通") } }
    }
    if (showEditor) ModalBottomSheet(onDismissRequest = { showEditor = false }) {
        InteractionEditor(busy = busy, serverNow = serverNow, onCancel = { showEditor = false }, onSave = { onSave(it); showEditor = false })
    }
}

@Composable
private fun InteractionEditor(busy: Boolean, serverNow: Instant, onCancel: () -> Unit, onSave: (InteractionDraft) -> Unit) {
    var draft by remember { mutableStateOf(InteractionDraft()) }
    var typeMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val businessZone = remember { ZoneId.of("Asia/Shanghai") }
    val selectedIsFuture = draft.occurredAt?.isAfter(serverNow) == true
    fun selectHistoricalTime() {
        val initial = (draft.occurredAt ?: serverNow).atZone(businessZone)
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                draft = draft.copy(occurredAt = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, businessZone).toInstant())
            }, initial.hour, initial.minute, true).show()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("记录真实沟通", style = MaterialTheme.typography.titleLarge)
        Box {
            OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(draft.type.label) }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) { InteractionType.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { draft = draft.copy(type = item); typeMenu = false }) } }
        }
        OutlinedTextField(value = draft.channel, onValueChange = { draft = draft.copy(channel = it) }, label = { Text("沟通渠道") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.summary, onValueChange = { draft = draft.copy(summary = it) }, label = { Text("沟通摘要 *") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = draft.details, onValueChange = { draft = draft.copy(details = it) }, label = { Text("详细记录") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
        YinggeCard(Modifier.fillMaxWidth()) {
            Text("实际发生时间", style = MaterialTheme.typography.labelLarge)
            Text(draft.occurredAt?.let { timelineTime.format(it) } ?: "现在（以服务器接收时间为准）", color = if (selectedIsFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Text("服务器校准时间：${timelineTime.format(serverNow)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::selectHistoricalTime, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("选择历史时间") }
                if (draft.occurredAt != null) TextButton(onClick = { draft = draft.copy(occurredAt = null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("恢复现在") }
            }
            if (selectedIsFuture) Text("实际发生时间不能晚于服务器当前时间。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Text("超过 48 小时补录仍会保存，但不会刷新客户归属期限；重复消息由服务端统一识别。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }; Spacer(Modifier.width(8.dp)); Button(onClick = { onSave(draft) }, enabled = !busy && draft.summary.isNotBlank() && !selectedIsFuture, modifier = Modifier.heightIn(min = 48.dp)) { Text("写入时间线") } }
    }
}
