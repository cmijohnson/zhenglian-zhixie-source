package com.eagleslab.yingge.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.*
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.ExpandableText
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard

@Composable
private fun ModuleBack(onBack: () -> Unit) { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回") } }

@Composable
private fun ConsultationFactField(label: String, value: String, update: (String) -> Unit) {
    OutlinedTextField(value, update, label = { Text(label) }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
fun TasksScreen(rows: List<WorkTask>, busy: Boolean, onBack: () -> Unit, onClaim: (String) -> Unit, onSubmit: (String, String) -> Unit) {
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var result by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("TASKS", "任务中心", "领取、执行并提交自己的任务成果。") }
        if (rows.isEmpty()) item { EmptyPane("暂无任务", "领取型任务开放后会显示在这里。") }
        items(rows, key = { it.id }) { task -> YinggeCard(Modifier.fillMaxWidth()) { Text(task.title, style = MaterialTheme.typography.titleMedium); Text(task.status.name, color = MaterialTheme.colorScheme.onSurfaceVariant); when (task.status) { WorkTaskStatus.OPEN -> Button({ onClaim(task.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("领取任务") }; WorkTaskStatus.CLAIMED -> Button({ editing = task }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("提交成果") }; else -> Text(task.result.ifBlank { "等待后续处理" }, modifier = Modifier.padding(top = 8.dp)) } } }
    }
    editing?.let { task -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("提交任务成果") }, text = { OutlinedTextField(result, { result = it }, label = { Text("成果说明") }, minLines = 3) }, confirmButton = { Button({ onSubmit(task.id, result); editing = null; result = "" }, enabled = result.isNotBlank()) { Text("确认提交") } }, dismissButton = { TextButton({ editing = null }) { Text("取消") } }) }
}

@Composable
fun PerformanceScreen(
    snapshot: PerformanceSnapshot?,
    busy: Boolean,
    onBack: () -> Unit,
    onSubmit: (String, String, EvidenceUpload) -> Unit,
    onProposeActivity: (String, String, String, Int, String) -> Unit,
    onCompleteActivity: (String, Int, Long, EvidenceUpload) -> Unit,
) {
    var selectedInteractionId by rememberSaveable { mutableStateOf("") }
    var requestedType by rememberSaveable { mutableStateOf("NORMAL") }
    var evidence by remember { mutableStateOf<EvidenceUpload?>(null) }
    var evidenceError by remember { mutableStateOf<String?>(null) }
    var proposingActivity by remember { mutableStateOf(false) }
    var completingActivity by remember { mutableStateOf<PerformanceActivityItem?>(null) }
    var activityDate by rememberSaveable { mutableStateOf("") }
    var activityTitle by rememberSaveable { mutableStateOf("") }
    var activityGoal by rememberSaveable { mutableStateOf("") }
    var activityExpected by rememberSaveable { mutableStateOf("") }
    var activityDescription by rememberSaveable { mutableStateOf("") }
    var activityActual by rememberSaveable { mutableStateOf("") }
    var activityEvidence by remember { mutableStateOf<EvidenceUpload?>(null) }
    val context = LocalContext.current
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        evidenceError = null
        evidence = uri?.let {
            runCatching {
                val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
                val name = context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "evidence"
                val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } ?: error("无法读取文件")
                require(bytes.size <= 5 * 1024 * 1024) { "证据文件不能超过 5 MB" }
                require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "只支持 JPEG、PNG 或 WebP" }
                EvidenceUpload(name, mime, bytes)
            }.getOrElse { error -> evidenceError = error.message ?: "无法读取证据"; null }
        }
    }
    val activityEvidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        evidenceError = null
        activityEvidence = uri?.let {
            runCatching {
                val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
                val name = context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "activity-evidence"
                val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } ?: error("无法读取文件")
                require(bytes.size <= 5 * 1024 * 1024) { "证据文件不能超过 5 MB" }
                require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "只支持 JPEG、PNG 或 WebP" }
                EvidenceUpload(name, mime, bytes)
            }.getOrElse { error -> evidenceError = error.message ?: "无法读取证据"; null }
        }
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("PERFORMANCE", "绩效中心", "今日明细与员工申报；图片证据需使用服务端文件能力。") }
        snapshot?.let { data -> item { YinggeCard(Modifier.fillMaxWidth()) { Text("${data.score} / ${data.baseline} 分", style = MaterialTheme.typography.headlineMedium); Text("审核中 ${data.pendingScore} 分", color = MaterialTheme.colorScheme.onSurfaceVariant); data.lines.forEach { Text("• $it", modifier = Modifier.padding(top = 6.dp)) } } } }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("申报交流绩效", style = MaterialTheme.typography.titleMedium)
            if (snapshot?.availableInteractions.isNullOrEmpty()) {
                Text("当前没有可申报的沟通记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            } else snapshot!!.availableInteractions.forEach { option ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    RadioButton(selected = selectedInteractionId == option.interactionId, onClick = { selectedInteractionId = option.interactionId })
                    Column(Modifier.padding(start = 6.dp)) { Text(option.customerName); Text(option.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                FilterChip(requestedType == "NORMAL", { requestedType = "NORMAL" }, { Text("普通交流") })
                Spacer(Modifier.width(8.dp))
                FilterChip(requestedType == "DEEP", { requestedType = "DEEP" }, { Text("深度交流") })
            }
            OutlinedButton({ evidencePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(evidence?.let { "已选择：${it.fileName}" } ?: "选择证据截图") }
            evidenceError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button({ evidence?.let { onSubmit(selectedInteractionId, requestedType, it) }; selectedInteractionId = ""; evidence = null }, enabled = selectedInteractionId.isNotBlank() && evidence != null && !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("上传证据并提交审核") }
            Text("证据仅支持 JPEG、PNG、WebP，最大 5 MB；使用 multipart 上传，不会嵌入 JSON。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        } }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("活动绩效", style = MaterialTheme.typography.titleMedium)
                TextButton({ proposingActivity = true }, enabled = !busy) { Text("提交提案") }
            }
            if (snapshot?.activities.isNullOrEmpty()) Text("暂无活动提案。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            snapshot?.activities.orEmpty().forEach { activity ->
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(activity.title, style = MaterialTheme.typography.titleSmall)
                Text("${activity.eventDate} · 预计 ${activity.expectedParticipants} 人 · ${activity.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(activity.goal, modifier = Modifier.padding(top = 4.dp))
                if (activity.organizerMine && activity.status == "APPROVED") {
                    Button({ completingActivity = activity; activityActual = ""; activityEvidence = null }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("上传成果并提交验收") }
                }
            }
        } }
        if (!snapshot?.leaderboard.isNullOrEmpty()) item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("近 30 天团队排行", style = MaterialTheme.typography.titleMedium)
            snapshot!!.leaderboard.take(10).forEach { row ->
                Text("${row.rank}. ${row.displayName} · ${row.teamName} · ${row.finalPoints} 分（达标 ${row.achievedDays}/${row.scoredDays} 天）", modifier = Modifier.padding(top = 8.dp))
            }
        } }
        if (!snapshot?.history.isNullOrEmpty()) item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("近 30 天历史", style = MaterialTheme.typography.titleMedium)
            snapshot!!.history.takeLast(14).reversed().forEach { point ->
                Text("${point.date} · ${point.finalPoints}/${point.baseline} 分 · ${if (point.settled) "已结算" else "未结算"}", modifier = Modifier.padding(top = 8.dp))
            }
        } }
    }
    if (proposingActivity) AlertDialog(
        onDismissRequest = { proposingActivity = false },
        title = { Text("提交活动提案") },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            OutlinedTextField(activityDate, { activityDate = it }, label = { Text("活动日期 YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(activityTitle, { activityTitle = it }, label = { Text("活动名称") }, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(activityGoal, { activityGoal = it }, label = { Text("活动目标") }, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(activityExpected, { activityExpected = it.filter(Char::isDigit) }, label = { Text("预计参与人数") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(activityDescription, { activityDescription = it }, label = { Text("活动说明") }, minLines = 3, modifier = Modifier.padding(top = 8.dp))
        } },
        confirmButton = { Button({ onProposeActivity(activityDate, activityTitle, activityGoal, activityExpected.toIntOrNull() ?: 0, activityDescription); proposingActivity = false }, enabled = activityDate.isNotBlank() && activityTitle.isNotBlank() && activityGoal.isNotBlank() && activityExpected.isNotBlank() && activityDescription.isNotBlank() && !busy) { Text("提交") } },
        dismissButton = { TextButton({ proposingActivity = false }) { Text("取消") } },
    )
    completingActivity?.let { activity -> AlertDialog(
        onDismissRequest = { completingActivity = null },
        title = { Text("提交活动成果") },
        text = { Column {
            Text(activity.title)
            OutlinedTextField(activityActual, { activityActual = it.filter(Char::isDigit) }, label = { Text("实际参与人数") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedButton({ activityEvidencePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(activityEvidence?.let { "已选择：${it.fileName}" } ?: "选择成果证据") }
            evidenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button({ activityEvidence?.let { onCompleteActivity(activity.id, activityActual.toIntOrNull() ?: 0, activity.version, it) }; completingActivity = null }, enabled = activityActual.isNotBlank() && activityEvidence != null && !busy) { Text("确认提交") } },
        dismissButton = { TextButton({ completingActivity = null }) { Text("取消") } },
    ) }
}

@Composable
fun AssistantScreen(
    workspace: AssistantWorkspace?,
    busy: Boolean,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onParse: (String, String) -> Unit,
    onStream: (String, String?) -> Unit,
) {
    var draftMode by rememberSaveable { mutableStateOf("CUSTOMER") }
    var input by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onLoad() }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("ASSISTANT", "智能助手", "草稿只用于人工核对，不会自动写入客户档案。") }
        workspace?.let { data -> item { YinggeCard(Modifier.fillMaxWidth()) {
            Text(if (data.availability.available) "${data.availability.provider} · ${data.availability.model}" else "AI 服务未启用", style = MaterialTheme.typography.titleMedium)
            Text(if (data.availability.available) "服务可用" else "基础业务不受影响，请由管理员配置。", color = if (data.availability.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } } }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("结构化草稿", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(draftMode == "CUSTOMER", { draftMode = "CUSTOMER" }, { Text("客户草稿") })
                FilterChip(draftMode == "INTERACTION", { draftMode = "INTERACTION" }, { Text("沟通草稿") })
            }
            OutlinedTextField(input, { input = it.take(20_000) }, label = { Text("粘贴自然语言记录") }, minLines = 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("结果中未有证据的字段必须保持“未确认”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ onParse(draftMode, input) }, enabled = input.isNotBlank() && workspace?.availability?.available == true && !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (busy) "生成中…" else "生成可编辑草稿") }
        } }
        workspace?.draft?.let { draft -> item { YinggeCard(Modifier.fillMaxWidth()) {
            Text(if (draft.mode == "CUSTOMER") "客户草稿结果" else "沟通草稿结果", style = MaterialTheme.typography.titleMedium)
            draft.fields.forEach { (label, value) ->
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                ExpandableText(value, collapsedLines = 4)
            }
        } } }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("摘要、下一步与质量检测", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(customerName, { customerName = it }, label = { Text("客户准确姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ onStream("SUMMARY", customerName) }, enabled = customerName.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Text("摘要") }
                OutlinedButton({ onStream("NEXT_ACTIONS", customerName) }, enabled = customerName.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Text("下一步") }
                OutlinedButton({ onStream("DATA_QUALITY", customerName) }, enabled = customerName.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Text("质量检测") }
            }
            Button({ onStream("DAILY_BRIEFING", null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("生成今日智能简报") }
        } }
        workspace?.streamedContent?.takeIf(String::isNotBlank)?.let { result -> item { YinggeCard(Modifier.fillMaxWidth()) {
            Text(workspace.streamedTitle, style = MaterialTheme.typography.titleMedium)
            ExpandableText(result, collapsedLines = 10, modifier = Modifier.padding(top = 8.dp))
        } } }
    }
}

@Composable
fun CircleScreen(feed: CircleFeed, busy: Boolean, onBack: () -> Unit, onLoad: (String, String?, Int) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var senderId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onLoad("", null, 0) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("CONTENT CIRCLE", "内容圈子", "支持关键词、发送人和服务端权重排序分页。") }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(search, { search = it }, label = { Text("搜索内容或发送人") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(senderId == null, { senderId = null }, { Text("全部发送人") }) }
                items(feed.senders, key = { it.membershipId }) { sender -> FilterChip(senderId == sender.membershipId, { senderId = sender.membershipId }, { Text(sender.displayName) }) }
            }
            Button({ onLoad(search, senderId, 0) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("查询") }
        } }
        if (feed.items.isEmpty()) item { EmptyPane("暂无内容", "调整搜索条件后重试。") }
        items(feed.items, key = { it.id }) { post -> YinggeCard(Modifier.fillMaxWidth()) {
            Text(post.senderName.ifBlank { "未知发送人" }, style = MaterialTheme.typography.titleMedium)
            Text("${post.platform} · ${post.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpandableText(post.content, collapsedLines = 5, modifier = Modifier.padding(top = 8.dp))
            Text(post.metrics, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            OutlinedButton({ onLoad(search, senderId, feed.page - 1) }, enabled = feed.page > 0 && !busy) { Text("上一页") }
            Text("${feed.page + 1} / ${kotlin.math.max(1, ((feed.totalElements + feed.size - 1) / feed.size).toInt())}", modifier = Modifier.padding(top = 12.dp))
            OutlinedButton({ onLoad(search, senderId, feed.page + 1) }, enabled = (feed.page + 1L) * feed.size < feed.totalElements && !busy) { Text("下一页") }
        } }
    }
}

@Composable
fun ConsultationsScreen(
    rows: List<ConsultationItem>,
    options: ConsultationOptions?,
    busy: Boolean,
    onBack: () -> Unit,
    onLoadOptions: (String) -> Unit,
    onCreate: (ConsultationDraft) -> Unit,
    onAccept: (String, Long) -> Unit,
    onReject: (String, Long, String) -> Unit,
    onEnd: (String, Long) -> Unit,
    onComplete: (String, ConsultationResultDraft) -> Unit,
) {
    var customer by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf("") }
    var candidateId by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var quotes by rememberSaveable { mutableStateOf("") }
    var goals by rememberSaveable { mutableStateOf("") }
    var actions by rememberSaveable { mutableStateOf("") }
    var concerns by rememberSaveable { mutableStateOf("") }
    var productFit by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf("") }
    var taboos by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var completing by remember { mutableStateOf<ConsultationItem?>(null) }
    var outcome by rememberSaveable { mutableStateOf("") }
    var continueFollowUp by rememberSaveable { mutableStateOf(true) }
    var resultNextStep by rememberSaveable { mutableStateOf("") }
    var resultOwnerId by rememberSaveable { mutableStateOf("") }
    var resultNextAt by rememberSaveable { mutableStateOf("") }
    var failureReasonId by rememberSaveable { mutableStateOf("") }
    var failureOther by rememberSaveable { mutableStateOf("") }
    var rejecting by remember { mutableStateOf<ConsultationItem?>(null) }
    var rejectionReason by rememberSaveable { mutableStateOf("") }
    val factsComplete = listOf(reason, quotes, goals, actions, concerns, productFit, focus, taboos, expected, start, end).all { it.isNotBlank() }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("CONSULTATION", "咨询交接", "带着客户画像提交交接，并在咨询后回填结果。") }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("创建交接卡", style = MaterialTheme.typography.titleMedium)
            Text("所有事实必须由你明确选择或填写，客户端不会自动复制、推断或构造内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(customer, { customer = it }, label = { Text("客户准确姓名") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
            OutlinedButton({ onLoadOptions(customer); sourceId = ""; candidateId = "" }, enabled = customer.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("核对客户并加载证据/顾问") }
            options?.takeIf { it.customerName == customer.trim() }?.let { loaded ->
                Text("选择来源沟通", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                loaded.sources.forEach { source -> Row(Modifier.fillMaxWidth()) { RadioButton(sourceId == source.interactionId, { sourceId = source.interactionId }); Text(source.summary, modifier = Modifier.padding(top = 12.dp)) } }
                Text("选择咨询顾问", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                loaded.candidates.forEach { candidate -> Row(Modifier.fillMaxWidth()) { RadioButton(candidateId == candidate.membershipId, { candidateId = candidate.membershipId }); Text(candidate.displayName, modifier = Modifier.padding(top = 12.dp)) } }
                ConsultationFactField("咨询原因", reason) { reason = it }
                ConsultationFactField("客户原话", quotes) { quotes = it }
                ConsultationFactField("目标与动机", goals) { goals = it }
                ConsultationFactField("已做行动", actions) { actions = it }
                ConsultationFactField("主要顾虑", concerns) { concerns = it }
                ConsultationFactField("产品适配", productFit) { productFit = it }
                ConsultationFactField("建议重点", focus) { focus = it }
                ConsultationFactField("明确禁忌（无则填“无”）", taboos) { taboos = it }
                ConsultationFactField("预期咨询结果", expected) { expected = it }
                OutlinedTextField(start, { start = it }, label = { Text("预约开始（ISO 8601，例如 2026-08-22T10:00:00Z）") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("预约结束（ISO 8601）") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                Button({
                    onCreate(ConsultationDraft(loaded.customerId, candidateId, sourceId, reason, quotes, goals, actions, concerns, productFit, focus, taboos, expected, start, end))
                }, enabled = sourceId.isNotBlank() && candidateId.isNotBlank() && factsComplete && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("确认并提交交接") }
            }
        } }
        items(rows, key = { it.id }) { row -> YinggeCard(Modifier.fillMaxWidth()) {
            Text(row.customerName, style = MaterialTheme.typography.titleMedium)
            Text("${row.status} · ${row.nextAction}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.canAccept) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onAccept(row.id, row.version) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("接受交接") }
                OutlinedButton({ rejecting = row; rejectionReason = "" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("拒绝") }
            }
            if (row.canEnd) Button({ onEnd(row.id, row.version) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("标记咨询结束") }
            if (row.status == ConsultationStatus.WAITING_RESULT) Button({
                completing = row; outcome = ""; resultNextStep = ""; resultOwnerId = ""; resultNextAt = ""; failureReasonId = ""; failureOther = ""; continueFollowUp = true
                onLoadOptions(row.customerName)
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("回填结果") }
        } }
    }
    rejecting?.let { row -> AlertDialog(
        onDismissRequest = { rejecting = null },
        title = { Text("拒绝咨询交接") },
        text = { OutlinedTextField(rejectionReason, { rejectionReason = it.take(2_000) }, label = { Text("拒绝原因") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button({ onReject(row.id, row.version, rejectionReason); rejecting = null }, enabled = rejectionReason.isNotBlank() && !busy) { Text("确认拒绝") } },
        dismissButton = { TextButton({ rejecting = null }) { Text("取消") } },
    ) }
    completing?.let { row ->
        val loaded = options?.takeIf { it.customerName == row.customerName }
        val selectedFailure = loaded?.failureReasons?.firstOrNull { it.id == failureReasonId }
        val valid = outcome.isNotBlank() && resultNextStep.isNotBlank() && resultOwnerId.isNotBlank() &&
            (continueFollowUp || (failureReasonId.isNotBlank() && (selectedFailure?.code != "OTHER" || failureOther.isNotBlank())))
        AlertDialog(
            onDismissRequest = { completing = null },
            title = { Text("回填咨询结果") },
            text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                OutlinedTextField(outcome, { outcome = it.take(10_000) }, label = { Text("沟通结果") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(continueFollowUp, { continueFollowUp = true; failureReasonId = "" }, { Text("继续推进") })
                    FilterChip(!continueFollowUp, { continueFollowUp = false }, { Text("暂不推进") })
                }
                OutlinedTextField(resultNextStep, { resultNextStep = it.take(6_000) }, label = { Text("下一步") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("下一负责人", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                loaded?.candidates.orEmpty().forEach { candidate -> Row(Modifier.fillMaxWidth()) { RadioButton(resultOwnerId == candidate.membershipId, { resultOwnerId = candidate.membershipId }); Text(candidate.displayName, modifier = Modifier.padding(top = 12.dp)) } }
                OutlinedTextField(resultNextAt, { resultNextAt = it }, label = { Text("下次时间（ISO 8601，可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (!continueFollowUp) {
                    Text("未推进原因", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    loaded?.failureReasons.orEmpty().forEach { reason -> Row(Modifier.fillMaxWidth()) { RadioButton(failureReasonId == reason.id, { failureReasonId = reason.id }); Text(reason.label, modifier = Modifier.padding(top = 12.dp)) } }
                    if (selectedFailure?.code == "OTHER") OutlinedTextField(failureOther, { failureOther = it.take(2_000) }, label = { Text("其他原因说明") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            } },
            confirmButton = { Button({
                onComplete(row.id, ConsultationResultDraft(outcome, continueFollowUp, resultNextStep, resultOwnerId, resultNextAt.ifBlank { null }, failureReasonId.ifBlank { null }, failureOther.ifBlank { null }))
                completing = null
            }, enabled = valid && !busy) { Text("确认回填") } },
            dismissButton = { TextButton({ completing = null }) { Text("取消") } },
        )
    }
}

@Composable
fun ContentTasksScreen(rows: List<ContentTaskItem>, busy: Boolean, onBack: () -> Unit, onSubmit: (String, String, String) -> Unit, onMetrics: (String, String) -> Unit) {
    var editing by remember { mutableStateOf<Pair<ContentTaskItem, Boolean>?>(null) }; var text by rememberSaveable { mutableStateOf("") }; var platform by rememberSaveable { mutableStateOf("XIAOHONGSHU") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ModuleBack(onBack); PageHeading("CONTENT", "内容任务", "提交作品并更新发布后的业务指标。") }
        items(rows, key = { it.id }) { row -> YinggeCard(Modifier.fillMaxWidth()) { Text(row.title, style = MaterialTheme.typography.titleMedium); Text("${row.channel} · ${if (row.submitted) "已提交" else "待提交"}"); if (!row.submitted) Button({ editing = row to false }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("提交内容") } else OutlinedButton({ editing = row to true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("更新指标") } } }
    }
    editing?.let { (row, metrics) -> AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (metrics) "更新指标" else "提交内容") }, text = { Column {
        if (!metrics) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(platform == "XIAOHONGSHU", { platform = "XIAOHONGSHU" }, { Text("小红书") })
            FilterChip(platform == "DOUYIN", { platform = "DOUYIN" }, { Text("抖音") })
        }
        OutlinedTextField(text, { text = it }, label = { Text(if (metrics) "浏览、点赞、收藏三个数字" else "完整作品链接") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    } }, confirmButton = { Button({ if (metrics) onMetrics(row.id, text) else onSubmit(row.id, platform, text); editing = null; text = "" }, enabled = text.isNotBlank()) { Text("确认") } }, dismissButton = { TextButton({ editing = null }) { Text("取消") } }) }
}

@Composable
fun FeedScreen(title: String, subtitle: String, rows: List<Pair<String, String>>, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { ModuleBack(onBack); PageHeading("READ", title, subtitle) }; if (rows.isEmpty()) item { EmptyPane("暂无内容", "请稍后刷新。") } else items(rows) { row -> YinggeCard(Modifier.fillMaxWidth()) { Text(row.first, style = MaterialTheme.typography.titleMedium); Text(row.second, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) } } }
}

@Composable
fun AccessListScreen(title: String, rows: List<Triple<String, String, Boolean>>, busy: Boolean, onBack: () -> Unit, onRevoke: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { ModuleBack(onBack); PageHeading("SECURITY", title, "只显示当前账号授权；撤销操作需要再次确认。") }; items(rows) { row -> YinggeCard(Modifier.fillMaxWidth()) { Text(row.second, style = MaterialTheme.typography.titleMedium); Text(if (row.third) "当前设备不可在此撤销" else "可撤销", color = MaterialTheme.colorScheme.onSurfaceVariant); if (!row.third) OutlinedButton({ onRevoke(row.first) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("撤销") } } } }
}
