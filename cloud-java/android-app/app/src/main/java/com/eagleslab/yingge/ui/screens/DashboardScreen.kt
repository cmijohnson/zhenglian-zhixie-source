package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eagleslab.yingge.model.Customer
import com.eagleslab.yingge.model.DashboardSnapshot
import com.eagleslab.yingge.model.DashboardGreeting
import com.eagleslab.yingge.model.BusinessModuleActionItem
import com.eagleslab.yingge.ui.components.*

@Composable
fun DashboardScreen(snapshot: DashboardSnapshot?, displayName: String, greeting: DashboardGreeting?, availableUpdate: BusinessModuleActionItem?, onUpdate: () -> Unit, onCustomer: (String) -> Unit, onRefresh: () -> Unit) {
    val update = availableUpdate
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("TODAY", "${greeting?.salutation ?: "你好"}，$displayName", greeting?.subtitle ?: "今天先把最重要的客户推进一步。", action = { TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("刷新") } }) }
        if (update != null) item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                    Column(Modifier.weight(1f)) { Text("新版本 ${update.title} 可用", fontWeight = FontWeight.Bold); Text(update.details, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = onUpdate) { Text("更新") }
                }
            }
        }
        if (snapshot == null) item { LoadingPane() } else {
            if (snapshot.staleAClassCustomers > 0) item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null)
                        Column { Text("${snapshot.staleAClassCustomers} 位 A 类客户超过 3 天未跟进", fontWeight = FontWeight.Bold); Text("这是登录后的最高优先级事项，请先核对时间线。", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { ScoreCard(snapshot.todayScore, snapshot.baselineScore) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("活跃客户", snapshot.activeCustomers.toString(), "当前可见", Modifier.weight(1f))
                        MetricCard("今日新增", snapshot.todayNewCustomers.toString(), "北京时间", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("待办提醒", snapshot.pendingReminders.toString(), "含逾期", Modifier.weight(1f))
                        MetricCard("A 类超时", snapshot.staleAClassCustomers.toString(), "72 小时", Modifier.weight(1f))
                    }
                }
            }
            item { Text("重点客户", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
            items(snapshot.focusCustomers, key = Customer::id) { customer -> FocusCustomer(customer, onCustomer) }
        }
    }
}

@Composable
private fun ScoreCard(score: Double, baseline: Double) {
    val progress = if (baseline <= 0) 0f else (score / baseline).toFloat().coerceIn(0f, 1f)
    YinggeCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("今日绩效", fontWeight = FontWeight.Bold)
        }
        Text("${score.toDisplay()} / ${baseline.toDisplay()}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        Text(if (score >= baseline) "已达到今日 baseline" else "还差 ${(baseline - score).toDisplay()} 分", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun Double.toDisplay() = if (this % 1.0 == 0.0) toInt().toString() else toString()

@Composable
private fun MetricCard(label: String, value: String, hint: String, modifier: Modifier) {
    YinggeCard(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun FocusCustomer(customer: Customer, onCustomer: (String) -> Unit) {
    YinggeCard(Modifier.fillMaxWidth().clickable { onCustomer(customer.id) }) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text(customer.name, style = MaterialTheme.typography.titleMedium); Text(listOf(customer.school, customer.grade).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            QualityBadge(customer.quality)
        }
        ExpandableText(customer.demandSummary.ifBlank { "需求摘要待补充" }, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { Text("查看档案", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    }
}
