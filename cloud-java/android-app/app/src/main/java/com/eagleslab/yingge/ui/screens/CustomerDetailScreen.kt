package com.eagleslab.yingge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.Customer
import com.eagleslab.yingge.model.CustomerClassificationDraft
import com.eagleslab.yingge.model.CustomerClassificationState
import com.eagleslab.yingge.model.CustomerCollaboration
import com.eagleslab.yingge.model.CustomerTransferCandidate
import com.eagleslab.yingge.model.CustomerInsight
import com.eagleslab.yingge.model.DealState
import com.eagleslab.yingge.model.Interaction
import com.eagleslab.yingge.model.DownloadArtifact
import com.eagleslab.yingge.model.CustomerQuality
import com.eagleslab.yingge.ui.components.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailTime = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))

@Composable
fun CustomerDetailScreen(
    customer: Customer?,
    interactions: List<Interaction>,
    transferCandidates: List<CustomerTransferCandidate>,
    collaboration: CustomerCollaboration?,
    classification: CustomerClassificationState?,
    downloadArtifact: DownloadArtifact?,
    insight: CustomerInsight?,
    canWrite: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddInteraction: () -> Unit,
    onTimeline: () -> Unit,
    onRequestDeal: () -> Unit,
    onLoadTransferCandidates: () -> Unit,
    onTransfer: (String, String, () -> Unit) -> Unit,
    onReplaceCollaborators: (Set<String>, () -> Unit) -> Unit,
    onArchive: (() -> Unit) -> Unit,
    onClassify: (CustomerClassificationDraft, () -> Unit) -> Unit,
    onDownloadTimeline: () -> Unit,
    onDownloadHandled: (Boolean) -> Unit,
    onLoadInsight: () -> Unit,
) {
    var confirmDeal by rememberSaveable { mutableStateOf(false) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    var transferTarget by rememberSaveable { mutableStateOf("") }
    var transferReason by rememberSaveable { mutableStateOf("") }
    var sharingOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCollaborators by remember { mutableStateOf(setOf<String>()) }
    var archiveConfirm by rememberSaveable { mutableStateOf(false) }
    var classifyOpen by rememberSaveable { mutableStateOf(false) }
    var classLevel by rememberSaveable { mutableStateOf(CustomerQuality.B) }
    var classReason by rememberSaveable { mutableStateOf("") }
    var classDemand by rememberSaveable { mutableStateOf("") }
    var classSignals by rememberSaveable { mutableStateOf("") }
    var classCorrection by rememberSaveable { mutableStateOf("") }
    var classEvidence by remember { mutableStateOf(setOf<String>()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(downloadArtifact?.contentType ?: "application/octet-stream")) { uri ->
        val saved = uri != null && downloadArtifact != null && runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(downloadArtifact.bytes) } ?: error("无法写入文件") }.isSuccess
        onDownloadHandled(saved)
    }
    LaunchedEffect(downloadArtifact) { downloadArtifact?.let { exportLauncher.launch(it.fileName) } }
    if (customer == null) { LoadingPane("正在加载客户档案"); return }
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回客户列表") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDownloadTimeline, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Download, contentDescription = null); Text("导出") }
                if (collaboration?.canManage == true && customer.archivedAt == null) TextButton(onClick = { selectedCollaborators = collaboration.collaborators.map { it.membershipId }.toSet(); sharingOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("共享") }
                if (collaboration?.canTransferOwnership == true && customer.archivedAt == null) TextButton(onClick = { transferOpen = true; onLoadTransferCandidates() }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.SwapHoriz, contentDescription = null); Text("转接") }
                if (collaboration?.canEditProfile == true && customer.archivedAt == null) TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Edit, contentDescription = null); Text("编辑") }
            }
        }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(58.dp)) { Box(contentAlignment = Alignment.Center) { Text(customer.name.take(1), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge) } }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(customer.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }); QualityBadge(customer.quality) }
                        Text(listOf(customer.school, customer.major, customer.grade).filter(String::isNotBlank).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledValue("负责人", customer.ownerName, Modifier.weight(1f))
                    LabeledValue("协作者", customer.collaboratorNames.joinToString("、"), Modifier.weight(1f))
                }
            }
        }
        item { ContactCard(customer) }
        item { IntelligenceCard(customer, insight, busy, onLoadInsight) }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                val current = classification?.history?.firstOrNull()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("客户分级与归属期限", style = MaterialTheme.typography.titleMedium)
                        Text(if (current == null) "尚未建立结构化 A/B/C 认定" else "${current.level} 类 · 第 ${current.revision} 版", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (collaboration?.canEditTimeline == true && customer.archivedAt == null) TextButton({
                        classLevel = current?.level ?: customer.quality; classReason = current?.recognitionReason.orEmpty(); classDemand = current?.potentialDemand.orEmpty(); classSignals = current?.signals?.joinToString("\n").orEmpty(); classCorrection = ""; classEvidence = emptySet(); classifyOpen = true
                    }) { Text(if (current == null) "建立" else "变更") }
                }
                current?.let { ExpandableText(it.recognitionReason, collapsedLines = 3, modifier = Modifier.padding(top = 8.dp)); Text("潜在需求：${it.potentialDemand}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("当前期限：${classification?.deadlineAt?.let(detailTime::format) ?: "待规则计算"}${classification?.deadlineMode?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("成单状态", style = MaterialTheme.typography.titleMedium); Text(customer.dealState.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                    AssistChip(onClick = {}, enabled = false, label = { Text(customer.dealState.label) }, leadingIcon = { Icon(if (customer.dealState == DealState.WON) Icons.Rounded.Verified else Icons.Rounded.HourglassTop, contentDescription = null) })
                }
                when {
                    customer.canRequestDeal && canWrite -> Button(onClick = { confirmDeal = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Text("提交成单申请") }
                    customer.dealState == DealState.PENDING -> Text("管理员审核前不会改变客户的正式成单状态。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    customer.dealState == DealState.WON -> Text("该客户已由组织管理员确认成单。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近时间线", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onTimeline, modifier = Modifier.heightIn(min = 48.dp)) { Text("查看全部") }
            }
        }
        if (interactions.isEmpty()) item { EmptyPane("暂无跟进记录", "记录客户回复或真实触达后，时间线会显示在这里。") }
        items(interactions.take(3), key = Interaction::id) { TimelinePreview(it) }
        if (collaboration?.canEditTimeline == true && customer.archivedAt == null) item { Button(onClick = onAddInteraction, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.AddComment, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("记录沟通") } }
        if (collaboration?.canArchive == true && customer.archivedAt == null) item { OutlinedButton({ archiveConfirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("归档客户") } }
    }
    if (confirmDeal) AlertDialog(
        onDismissRequest = { confirmDeal = false },
        icon = { Icon(Icons.Rounded.Verified, contentDescription = null) },
        title = { Text("提交成单申请？") },
        text = { Text("系统只会创建一条待管理员审核的申请，不填写金额，也不会直接标记成单。") },
        confirmButton = { Button(onClick = { confirmDeal = false; onRequestDeal() }, enabled = !busy) { Text("提交管理员审核") } },
        dismissButton = { TextButton(onClick = { confirmDeal = false }) { Text("取消") } },
    )
    if (transferOpen) AlertDialog(
        onDismissRequest = { if (!busy) transferOpen = false },
        title = { Text("转接客户所有权") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (transferCandidates.isEmpty()) Text("暂无符合当前权限范围的启用接收人。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                transferCandidates.forEach { candidate -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(transferTarget == candidate.membershipId, { transferTarget = candidate.membershipId })
                    Column { Text(candidate.displayName); if (candidate.teamNames.isNotBlank()) Text(candidate.teamNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
                OutlinedTextField(transferReason, { transferReason = it.take(500) }, label = { Text("转接说明（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                Text("主负责人和待办提醒将交给接收人；历史时间线与录入人保持不变。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { Button({ onTransfer(transferTarget, transferReason) { transferOpen = false; transferTarget = ""; transferReason = "" } }, enabled = transferTarget.isNotBlank() && !busy) { Text("确认转接") } },
        dismissButton = { TextButton({ transferOpen = false }, enabled = !busy) { Text("取消") } },
    )
    if (sharingOpen) AlertDialog(
        onDismissRequest = { if (!busy) sharingOpen = false },
        title = { Text("共享客户跟进") },
        text = { Column(Modifier.fillMaxWidth()) {
            Text("协作者与主负责人共同编辑同一份时间线；客户档案不会复制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            collaboration?.candidates.orEmpty().forEach { candidate -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(candidate.membershipId in selectedCollaborators, { checked -> selectedCollaborators = if (checked) selectedCollaborators + candidate.membershipId else selectedCollaborators - candidate.membershipId })
                Column { Text(candidate.displayName); if (candidate.teamNames.isNotBlank()) Text(candidate.teamNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            if (collaboration?.candidates.isNullOrEmpty()) Text("暂无可共享的启用成员。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { Button({ onReplaceCollaborators(selectedCollaborators) { sharingOpen = false } }, enabled = !busy) { Text("保存共享") } },
        dismissButton = { TextButton({ sharingOpen = false }, enabled = !busy) { Text("取消") } },
    )
    if (archiveConfirm) AlertDialog(
        onDismissRequest = { archiveConfirm = false },
        title = { Text("归档客户？") },
        text = { Text("归档后保留客户、时间线与审计历史；客户将从默认活跃列表移除。") },
        confirmButton = { Button({ onArchive { archiveConfirm = false } }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认归档") } },
        dismissButton = { TextButton({ archiveConfirm = false }) { Text("取消") } },
    )
    if (classifyOpen) AlertDialog(
        onDismissRequest = { if (!busy) classifyOpen = false },
        title = { Text("客户分级认定") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            val signals = classSignals.lines().map(String::trim).filter(String::isNotBlank)
            val missing = buildList {
                if (classReason.isBlank()) add("认定说明")
                if (classDemand.isBlank()) add("潜在需求")
                if (classEvidence.isEmpty()) add("事实沟通依据")
                if (classLevel == CustomerQuality.A && signals.isEmpty()) add("至少一项 A 类近期推进信号")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CustomerQuality.entries.forEach { level -> FilterChip(classLevel == level, { classLevel = level }, { Text(level.name) }) } }
            OutlinedTextField(classReason, { classReason = it.take(4_000) }, label = { Text("认定说明") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(classDemand, { classDemand = it.take(4_000) }, label = { Text("潜在需求") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(classSignals, { classSignals = it.take(10_000) }, label = { Text("A 类信号（每行一项，可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("选择事实沟通依据", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            interactions.forEach { interaction -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(interaction.id in classEvidence, { checked -> classEvidence = if (checked) classEvidence + interaction.id else classEvidence - interaction.id })
                ExpandableText(interaction.summary, collapsedLines = 2, modifier = Modifier.weight(1f))
            } }
            if (interactions.isEmpty()) {
                Text("当前没有可作为依据的真实沟通记录。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = { classifyOpen = false; onAddInteraction() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) { Text("先记录沟通") }
            }
            if (missing.isNotEmpty()) Text("还需补充：${missing.joinToString("、")}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            if (!classification?.history.isNullOrEmpty()) OutlinedTextField(classCorrection, { classCorrection = it.take(1_000) }, label = { Text("变更原因（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } },
        confirmButton = { Button({
            onClassify(CustomerClassificationDraft(classLevel, classReason, classDemand, classSignals.lines().map(String::trim).filter(String::isNotBlank), classEvidence, classCorrection.ifBlank { null })) { classifyOpen = false }
        }, enabled = classReason.isNotBlank() && classDemand.isNotBlank() && classEvidence.isNotEmpty() && (classLevel != CustomerQuality.A || classSignals.lines().any { it.isNotBlank() }) && !busy) { Text("确认认定") } },
        dismissButton = { TextButton({ classifyOpen = false }, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun ContactCard(customer: Customer) {
    YinggeCard(Modifier.fillMaxWidth()) {
        Text("联系与画像", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { LabeledValue("手机", customer.phone, Modifier.weight(1f)); LabeledValue("微信", customer.wechat, Modifier.weight(1f)) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { LabeledValue("QQ", customer.qq, Modifier.weight(1f)); LabeledValue("来源", customer.source, Modifier.weight(1f)) }
    }
}

@Composable
private fun IntelligenceCard(customer: Customer, insight: CustomerInsight?, busy: Boolean, onLoad: () -> Unit) {
    YinggeCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text("AI 客户洞察", style = MaterialTheme.typography.titleMedium) }
        Text("仅基于已确认画像和时间线，不会自动修改正式档案。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        if (insight == null) {
            ExpandableText(customer.demandSummary.ifBlank { "信息不足，暂时无法形成可靠洞察。" }, collapsedLines = 3, modifier = Modifier.padding(top = 14.dp))
            OutlinedButton(onClick = onLoad, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("生成 AI 客户洞察") }
        } else {
            ExpandableText(insight.content, collapsedLines = 5, modifier = Modifier.padding(top = 14.dp))
            TextButton(onClick = onLoad, enabled = !busy, modifier = Modifier.padding(top = 6.dp)) { Text("根据最新时间线重新分析") }
        }
    }
}

@Composable
private fun TimelinePreview(item: Interaction) {
    YinggeCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${item.channel} · ${item.type.label}", fontWeight = FontWeight.Bold); Text(detailTime.format(item.occurredAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        ExpandableText(item.summary, collapsedLines = 2, modifier = Modifier.padding(top = 8.dp))
        Text("${item.creatorName} 录入", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    }
}
