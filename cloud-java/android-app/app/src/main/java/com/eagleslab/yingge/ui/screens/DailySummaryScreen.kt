package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.DailySummary
import com.eagleslab.yingge.model.DailySummaryDraft
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard
import java.time.format.DateTimeFormatter

@Composable
fun DailySummaryScreen(summary: DailySummary?, persistedDraft: DailySummaryDraft, busy: Boolean, onDraft: (DailySummaryDraft) -> Unit, onSubmit: (DailySummaryDraft, () -> Unit) -> Unit) {
    var draft by remember(summary, persistedDraft) { mutableStateOf(if (summary?.submitted == true) DailySummaryDraft(summary.deepConversationCount.toString(), summary.issues, summary.tomorrowPlan) else persistedDraft) }
    var completed by rememberSaveable(summary?.submitted) { mutableStateOf(false) }
    fun update(value: DailySummaryDraft) { draft = value; onDraft(value) }
    if (completed) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Text("今日小结已提交", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
                TextButton(onClick = { completed = false }, modifier = Modifier.padding(top = 10.dp).heightIn(min = 48.dp)) { Text("修改今日小结") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading("DAILY SUMMARY", "每日小结", summary?.date?.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) ?: "正在读取今日数据")
            YinggeCard(Modifier.fillMaxWidth()) {
                Text("系统统计", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryMetric("26级有效线索", summary?.verifiedGrade26Leads?.toString() ?: "—", Modifier.weight(1f))
                    SummaryMetric("深度沟通", summary?.deepConversationCount?.toString() ?: "—", Modifier.weight(1f))
                }
            }
            if (summary?.deepConversationReadOnly != true) OutlinedTextField(value = draft.deepConversationCount, onValueChange = { update(draft.copy(deepConversationCount = it.filter(Char::isDigit))) }, label = { Text("深度沟通人数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(15.dp))
            else Text("深度沟通由绩效审核结果自动统计，无需重复填写。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = draft.issues, onValueChange = { if (it.length <= 5000) update(draft.copy(issues = it)) }, label = { Text("今天遇到的问题 *") }, minLines = 5, maxLines = 10, supportingText = { Text("${draft.issues.length}/5000") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
            OutlinedTextField(value = draft.tomorrowPlan, onValueChange = { if (it.length <= 5000) update(draft.copy(tomorrowPlan = it)) }, label = { Text("明日计划 *") }, minLines = 5, maxLines = 10, supportingText = { Text("${draft.tomorrowPlan.length}/5000") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
            Text("未提交内容自动保存在本设备；提交成功后清除草稿。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(90.dp))
        }
        Surface(shadowElevation = 8.dp) {
            Button(onClick = { onSubmit(draft) { completed = true } }, enabled = !busy && draft.issues.isNotBlank() && draft.tomorrowPlan.isNotBlank(), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Send, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (summary?.submitted == true) "保存修改" else "提交小结") }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.padding(14.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 5.dp)) } }
}
