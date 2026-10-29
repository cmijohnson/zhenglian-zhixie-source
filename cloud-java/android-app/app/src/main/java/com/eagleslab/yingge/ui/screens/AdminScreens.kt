package com.eagleslab.yingge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.*
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.ExpandableText
import com.eagleslab.yingge.ui.components.LoadingPane
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AdminOverviewScreen(
    overview: AdminOverview?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenGovernance: () -> Unit,
    onOpenPeople: () -> Unit,
    onEmployeeMode: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading("ADMIN COMMAND", "管理概览", "只展示当前组织、当前权限范围内的实时待办。") {
                TextButton(onClick = onEmployeeMode) { Text("员工模式") }
            }
        }
        if (overview == null && busy) item { LoadingPane("正在汇总管理待办") }
        overview?.let { value ->
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { AdminMetricCard("成单审核", value.pendingDealRequests, Icons.Rounded.TaskAlt, onOpenGovernance) }
                    item { AdminMetricCard("A 类抽查", value.pendingClassificationReviews, Icons.Rounded.FactCheck, onOpenGovernance) }
                    item { AdminMetricCard("归属到期", value.dueOwnershipDeadlines, Icons.Rounded.Timer, onOpenGovernance) }
                    item { AdminMetricCard("公共池", value.openPoolEntries, Icons.Rounded.Hub, onOpenGovernance) }
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("今日需要处理", style = MaterialTheme.typography.titleLarge)
                    AdminTodoRow(Icons.Rounded.GroupAdd, "A 类二线待补充", value.pendingSecondLineRecruitments, onOpenGovernance)
                    AdminTodoRow(Icons.Rounded.QueryStats, "绩效交流待审核", value.pendingPerformanceClaims, onOpenGovernance)
                    AdminTodoRow(Icons.Rounded.Event, "绩效活动待处理", value.pendingPerformanceActivities, onOpenGovernance)
                    AdminTodoRow(Icons.Rounded.Handshake, "咨询反馈已超时", value.overdueConsultationFeedback, onOpenGovernance)
                    AdminTodoRow(Icons.Rounded.Psychology, "客户智能分析失败", value.failedIntelligenceJobs, onOpenGovernance)
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth().clickable(onClick = onOpenPeople)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("成员与团队", fontWeight = FontWeight.Bold)
                            Text("成员状态、角色、团队和唯一团队长", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: Long, icon: ImageVector, onClick: () -> Unit) {
    YinggeCard(Modifier.width(148.dp).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdminTodoRow(icon: ImageVector, title: String, count: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
        Badge { Text(if (count > 99) "99+" else count.toString()) }
    }
}

@Composable
fun AdminPeopleScreen(
    members: List<AdminMember>,
    teams: List<AdminTeam>,
    registrationCodes: List<AdminRegistrationCode>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onCreateMember: (String, String, String, String, String?) -> Unit,
    onDeleteMember: (AdminMember, String?) -> Unit,
    onCreateTeam: (String) -> Unit,
    onAssignLeader: (String, String) -> Unit,
    onDeleteTeam: (String) -> Unit,
    onUpdateMember: (AdminMember, String, String, String, String?) -> Unit,
    onCreateRegistrationCode: (Int, String?) -> Unit,
    onDisableRegistrationCode: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var createMember by remember { mutableStateOf(false) }
    var createTeam by remember { mutableStateOf(false) }
    var createCode by remember { mutableStateOf(false) }
    var editMember by remember { mutableStateOf<AdminMember?>(null) }
    var deleteMember by remember { mutableStateOf<AdminMember?>(null) }
    var deleteTeam by remember { mutableStateOf<AdminTeam?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("PEOPLE & TEAMS", "成员团队", "角色、状态与团队变更均由服务端重新校验并写入审计。") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("成员 ${members.size}", "团队 ${teams.size}", "邀请码 ${registrationCodes.size}").forEachIndexed { index, label ->
                    SegmentedButton(selected = tab == index, onClick = { tab = index }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                }
            }
        }
        if (busy && members.isEmpty() && teams.isEmpty()) item { LoadingPane() }
        if (tab == 0) {
            item { Button(onClick = { createMember = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Rounded.PersonAdd, null); Text("新增成员") } }
            if (members.isEmpty() && !busy) item { EmptyPane("暂无成员", "请检查服务端能力和成员状态。") }
            items(members, key = { it.membershipId }) { member ->
                YinggeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(member.displayName.take(1), fontWeight = FontWeight.Bold) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("${member.username} · ${member.roles.joinToString()} · ${member.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${member.teams.joinToString().ifBlank { "未分组" }} · 客户 ${member.ownedCustomerCount}", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { editMember = member }) { Icon(Icons.Rounded.Edit, contentDescription = "编辑成员") }
                    }
                    TextButton(onClick = { deleteMember = member }, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Rounded.PersonRemove, null); Text("删除 / 转移")
                    }
                }
            }
        } else if (tab == 1) {
            item { Button(onClick = { createTeam = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Rounded.Add, null); Text("新建团队") } }
            items(teams, key = { it.id }) { team ->
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium)
                    Text("${if (team.active) "启用" else "停用"} · ${team.memberCount} 名成员", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("团队长：${team.leaderDisplayName ?: "未设置"}", modifier = Modifier.padding(top = 6.dp))
                    var expanded by remember(team.id) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) { Text("设置团队长") }
                    if (expanded) {
                        members.filter { team.id in it.teamIds && it.status == "ACTIVE" }.forEach { member ->
                            TextButton(onClick = { onAssignLeader(team.id, member.membershipId); expanded = false }, modifier = Modifier.fillMaxWidth()) {
                                Text(member.displayName, modifier = Modifier.weight(1f)); if (team.leaderMembershipId == member.membershipId) Icon(Icons.Rounded.Check, null)
                            }
                        }
                    }
                    OutlinedButton(onClick = { deleteTeam = team }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.DeleteOutline, null); Text("删除团队")
                    }
                }
            }
        } else {
            item { Button(onClick = { createCode = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Rounded.VpnKey, null); Text("生成邀请码") } }
            if (registrationCodes.isEmpty() && !busy) item { EmptyPane("暂无邀请码", "新邀请码明文只显示一次。") }
            items(registrationCodes, key = { it.id }) { code ->
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text(code.plainCode ?: "尾号 ${code.codeHint}", style = MaterialTheme.typography.titleMedium)
                    Text("已使用 ${code.usedCount}/${code.maxUses} · ${code.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    code.expiresAt?.let { Text("到期：${DateTimeFormatter.ISO_INSTANT.format(it)}", style = MaterialTheme.typography.labelMedium) }
                    if (code.status == "ACTIVE") OutlinedButton(onClick = { onDisableRegistrationCode(code.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("停用邀请码") }
                    if (code.plainCode != null) Text("请立即通过安全渠道交付；离开本页后不再显示完整邀请码。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (createMember) AdminMemberCreateDialog(teams, busy, { createMember = false }) { username, name, password, role, teamId ->
        onCreateMember(username, name, password, role, teamId); createMember = false
    }
    if (createTeam) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createTeam = false },
            title = { Text("新建团队") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("团队名称") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { onCreateTeam(name); createTeam = false }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { createTeam = false }) { Text("取消") } },
        )
    }
    editMember?.let { member ->
        MemberEditDialog(member, teams, busy, { editMember = null }) { name, role, status, teamId ->
            onUpdateMember(member, name, role, status, teamId); editMember = null
        }
    }
    deleteMember?.let { member ->
        AdminMemberDeleteDialog(member, members, busy, { deleteMember = null }) { transferTo ->
            onDeleteMember(member, transferTo); deleteMember = null
        }
    }
    deleteTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { deleteTeam = null },
            icon = { Icon(Icons.Rounded.WarningAmber, null) },
            title = { Text("确认删除团队") },
            text = { Text("将删除“${team.name}”的团队关系，但不会删除成员、客户或历史记录。请确认团队长和成员归属已妥善处理。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onDeleteTeam(team.id); deleteTeam = null }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { deleteTeam = null }) { Text("取消") } },
        )
    }
    if (createCode) {
        var maxUses by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { createCode = false },
            title = { Text("生成邀请码") },
            text = { Column { OutlinedTextField(maxUses, { maxUses = it.filter(Char::isDigit).take(4) }, label = { Text("最大使用次数（1–1000）") }, singleLine = true); Text("完整邀请码只显示一次，App 不会缓存。", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            confirmButton = { TextButton(enabled = maxUses.toIntOrNull() in 1..1000 && !busy, onClick = { onCreateRegistrationCode(maxUses.toInt(), null); createCode = false }) { Text("生成") } },
            dismissButton = { TextButton(onClick = { createCode = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AdminMemberCreateDialog(teams: List<AdminTeam>, busy: Boolean, onDismiss: () -> Unit, onCreate: (String, String, String, String, String?) -> Unit) {
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("MEMBER") }
    var teamId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增成员") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(username, { username = it }, label = { Text("登录账号") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("真实姓名") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("初始密码") }, singleLine = true)
            Text("角色", fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("MEMBER", "TEAM_MANAGER", "VIEWER", "ORG_ADMIN")) { value -> FilterChip(role == value, { role = value }, { Text(value) }) } }
            Text("团队（可选）", fontWeight = FontWeight.SemiBold)
            teams.forEach { team -> RadioButtonRow(team.name, teamId == team.id) { teamId = team.id } }
        } },
        confirmButton = { TextButton(enabled = username.isNotBlank() && name.isNotBlank() && password.isNotBlank() && !busy, onClick = { onCreate(username, name, password, role, teamId) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AdminMemberDeleteDialog(member: AdminMember, members: List<AdminMember>, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val candidates = members.filter { it.membershipId != member.membershipId && it.status == "ACTIVE" }
    var transferTo by remember(member.membershipId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, null) },
        title = { Text("删除成员 ${member.displayName}") },
        text = { Column {
            Text("该操作会停用账号并移除当前成员关系；历史沟通和审计记录保留。当前名下客户 ${member.ownedCustomerCount} 位。")
            if (member.ownedCustomerCount > 0) {
                Text("客户及提醒接收人（必选）", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                candidates.forEach { candidate -> RadioButtonRow(candidate.displayName, transferTo == candidate.membershipId) { transferTo = candidate.membershipId } }
            }
        } },
        confirmButton = { TextButton(enabled = !busy && (member.ownedCustomerCount == 0L || transferTo != null), onClick = { onConfirm(transferTo) }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MemberEditDialog(member: AdminMember, teams: List<AdminTeam>, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String?) -> Unit) {
    var name by remember(member.membershipId) { mutableStateOf(member.displayName) }
    var role by remember(member.membershipId) { mutableStateOf(member.roles.firstOrNull() ?: "MEMBER") }
    var status by remember(member.membershipId) { mutableStateOf(member.status) }
    var teamId by remember(member.membershipId) { mutableStateOf(member.teamIds.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑成员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("真实姓名") }, singleLine = true)
                Text("角色", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("MEMBER", "TEAM_MANAGER", "VIEWER", "ORG_ADMIN")) { item -> FilterChip(role == item, { role = item }, { Text(item) }) }
                }
                Text("状态", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACTIVE", "DISABLED").forEach { item -> FilterChip(status == item, { status = item }, { Text(item) }) }
                }
                Text("团队", fontWeight = FontWeight.SemiBold)
                teams.forEach { team -> RadioButtonRow(team.name, teamId == team.id) { teamId = team.id } }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { onSave(name, role, status, teamId) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RadioButtonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}

@Composable
fun AdminGovernanceScreen(
    overview: AdminOverview?,
    dealRequests: List<DealRequest>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onReview: (DealRequest, Boolean) -> Unit,
    onModule: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("BUSINESS GOVERNANCE", "业务治理", "成单采用极简审核；其他治理能力按权限进入原生模块。") }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                Text("成单审核", style = MaterialTheme.typography.titleLarge)
                Text("员工只提交申请，管理员确认后客户主状态变为“已成单”。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (dealRequests.isEmpty()) EmptyPane("暂无待审核成单", "新的申请会实时出现在这里。")
                dealRequests.forEach { request ->
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(request.customerName, fontWeight = FontWeight.Bold)
                    Text("${request.teamName ?: "未分组"} · ${request.ownerDisplayName ?: "无负责人"} · 申请人 ${request.applicantDisplayName ?: "未知"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onReview(request, false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("驳回") }
                        Button(onClick = { onReview(request, true) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("确认成单") }
                    }
                }
            }
        }
        item { AdminSectionLinks(overview, onModule) }
    }
}

@Composable
private fun AdminSectionLinks(overview: AdminOverview?, onModule: (String) -> Unit) {
    YinggeCard(Modifier.fillMaxWidth()) {
        Text("治理工作区", style = MaterialTheme.typography.titleLarge)
        val rows = listOf(
            Triple("market", "A/B/C 认定与抽查", overview?.pendingClassificationReviews ?: 0),
            Triple("pool", "公共池、二线与归属期限", (overview?.openPoolEntries ?: 0) + (overview?.pendingSecondLineRecruitments ?: 0)),
            Triple("cross_sales", "交叉单快速查询", 0L),
            Triple("tasks_admin", "任务派发与验收", 0L),
            Triple("content_admin", "内容与公告审核", 0L),
            Triple("daily_admin", "日报日历与提交情况", 0L),
            Triple("performance_admin", "绩效管理", (overview?.pendingPerformanceClaims ?: 0) + (overview?.pendingPerformanceActivities ?: 0)),
        )
        rows.forEach { (key, label, count) ->
            Row(Modifier.fillMaxWidth().clickable { onModule(key) }.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (count > 0) Badge { Text(count.toString()) }
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun LeadershipDashboardScreen(
    value: LeadershipDashboard?,
    busy: Boolean,
    onRefresh: (String?, String?) -> Unit,
) {
    var teamId by rememberSaveable { mutableStateOf<String?>(null) }
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(teamId, memberId) {
        while (isActive) {
            onRefresh(teamId, memberId)
            delay(10_000)
        }
    }
    LazyColumn(contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("MOBILE INTELLIGENCE", "经营看板", "竖屏实时视图；后台时 Compose 页面停止刷新。") }
        if (value == null && busy) item { LoadingPane("正在读取经营数据") }
        value?.let { board ->
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(teamId == null, { teamId = null; memberId = null; onRefresh(null, null) }, { Text("全部团队") }) }
                    items(board.teams) { option -> FilterChip(teamId == option.id, { teamId = option.id; memberId = null; onRefresh(option.id, null) }, { Text(option.label) }) }
                }
                if (teamId != null) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    item { FilterChip(memberId == null, { memberId = null; onRefresh(teamId, null) }, { Text("全部成员") }) }
                    items(board.members) { option -> FilterChip(memberId == option.id, { memberId = option.id; onRefresh(teamId, option.id) }, { Text(option.label) }) }
                }
            }
            if (board.tickerItems.isNotEmpty()) item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("重要参数", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    board.tickerItems.take(5).forEach { ExpandableText(it.text, collapsedLines = 2, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("总客户", board.headline.totalCustomers, Modifier.weight(1f))
                    KpiCard("今日新增", board.headline.todayNewCustomers, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("有效线索", board.headline.qualifiedFriendCustomers, Modifier.weight(1f))
                    KpiCard("今日有效", board.headline.todayQualifiedCustomers, Modifier.weight(1f))
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("A 类三日未跟进", style = MaterialTheme.typography.titleMedium)
                    Text(board.staleAClassCustomers.toString(), style = MaterialTheme.typography.displaySmall, color = if (board.staleAClassCustomers > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    board.staleAClassOwners.take(5).forEach { (name, count) -> Text("$name · $count 位", modifier = Modifier.padding(top = 5.dp)) }
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("${board.featuredGrade.ifBlank { "当前" }} 级院校对比", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard("院校甲", board.institutionACustomers, Modifier.weight(1f), flat = true)
                        KpiCard("院校乙", board.institutionBCustomers, Modifier.weight(1f), flat = true)
                    }
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("客户等级", style = MaterialTheme.typography.titleMedium)
                    board.quality.forEach { Text("${it.label}  ${it.customerCount} · ${"%.1f".format(it.share)}%", modifier = Modifier.padding(top = 8.dp)) }
                    Text("来源分布", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
                    board.sources.take(5).forEach { Text("${it.source}  ${it.customerCount} · ${"%.1f".format(it.share)}%", modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item {
                YinggeCard(Modifier.fillMaxWidth()) {
                    Text("成员排行", style = MaterialTheme.typography.titleMedium)
                    board.rankings.take(5).forEachIndexed { index, row ->
                        Text("${index + 1}. ${row.displayName} · 客户 ${row.totalCustomers} · A 类 ${row.qualityACustomers}", modifier = Modifier.padding(top = 9.dp))
                    }
                    if (board.rankings.size > 5) ExpandableText(board.rankings.drop(5).joinToString("\n") { "${it.displayName} · 客户 ${it.totalCustomers}" }, 3)
                }
            }
            item {
                Text(
                    "最后更新 ${DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(board.generatedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: Long, modifier: Modifier, flat: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = if (flat) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f) else MaterialTheme.colorScheme.surface, tonalElevation = if (flat) 0.dp else 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun AdminMoreScreen(
    capabilities: Set<String>,
    onModule: (String) -> Unit,
    onEmployeeMode: () -> Unit,
) {
    val entries = listOf(
        Triple("dictionaries", "字典与标签", "ADMIN_DICTIONARIES_WRITE"),
        Triple("ai_settings", "AI 服务与模型", "ADMIN_ASSISTANT_SETTINGS_WRITE"),
        Triple("intelligence", "客户智能分析运行状态", "ADMIN_CUSTOMER_INTELLIGENCE_WRITE"),
        Triple("enrichment", "客户资料补全", "ADMIN_ENRICHMENT_WRITE"),
        Triple("agent_admin", "Agent 连接管理", "ADMIN_AGENT_MANAGEMENT"),
        Triple("audit", "审计查询与导出", "ADMIN_AUDIT_READ"),
        Triple("webhooks", "企业微信 Webhook", "ADMIN_WEBHOOKS_WRITE"),
        Triple("followup_policy", "A 类提醒与释放策略", "ADMIN_FOLLOWUP_POLICY_WRITE"),
        Triple("consultation_settings", "咨询预约与失败原因", "ADMIN_CONSULTATION_SETTINGS_WRITE"),
        Triple("leadership_settings", "领导大屏参数", "ADMIN_LEADERSHIP_SETTINGS_WRITE"),
    )
    LazyColumn(contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("ADMIN TOOLS", "管理更多", "仅展示服务端明确授予的管理能力。") { TextButton(onClick = onEmployeeMode) { Text("员工模式") } } }
        item {
            YinggeCard(Modifier.fillMaxWidth()) {
                entries.filter { it.third in capabilities }.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onModule(key) }.heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
fun NativeAdminModuleScreen(
    key: String,
    module: AdminNativeModule?,
    busy: Boolean,
    members: List<AdminMember>,
    onLoad: (String) -> Unit,
    onAction: (AdminNativeItem, String) -> Unit,
    onCommand: (AdminNativeCommand) -> Unit,
    downloadArtifact: DownloadArtifact?,
    onDownload: (AdminNativeItem) -> Unit,
    onDownloadHandled: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val copy = when (key) {
        "market" -> Triple("MARKET GOVERNANCE", "A/B/C 认定与抽查", "查看待复核、异常 A 类率、归属期限和提醒策略。")
        "pool" -> Triple("OWNERSHIP", "公共池与二线", "处理人工释放池、临时认领、二线招募和联系授权。")
        "cross_sales" -> Triple("CROSS-SALE DIRECTORY", "交叉单快速查询", "有任一有效协作者即认定为交叉单，支持原生查看与导出。")
        "tasks_admin" -> Triple("TASK ADMIN", "任务派发与验收", "创建、派发、验收、退回、关闭和删除任务。")
        "content_admin" -> Triple("CONTENT ADMIN", "内容与公告", "审核内容，发布、置顶、编辑或归档公告。")
        "daily_admin" -> Triple("DAILY REPORT", "日报管理", "按日历核对已提交和未提交人员。")
        "performance_admin" -> Triple("PERFORMANCE ADMIN", "绩效管理", "规则、参与人员、审核、人工分、日结与导出。")
        "dictionaries" -> Triple("DICTIONARIES", "字典与标签", "管理学校、来源和客户标签。")
        "ai_settings" -> Triple("AI SETTINGS", "AI 服务与模型", "密钥只允许单向录入，不回显、不缓存。")
        "intelligence" -> Triple("AI RUNTIME", "客户智能分析", "查看运行状态与服务器压力，暂停或恢复补算。")
        "enrichment" -> Triple("ENRICHMENT", "客户资料补全", "选择 XLSX、预览、确认并下载结果报告。")
        "agent_admin" -> Triple("AGENT ADMIN", "Agent 连接管理", "查看连接状态与审计并强制撤销。")
        "audit" -> Triple("AUDIT", "审计查询", "按客户、成员、操作类型和日期筛选并导出。")
        "webhooks" -> Triple("WEBHOOK", "企业微信 Webhook", "管理通道、查看投递账本并重试失败项。")
        "followup_policy" -> Triple("FOLLOW-UP POLICY", "A 类提醒与释放策略", "A/B 自动释放与超时提醒分开控制。")
        "consultation_settings" -> Triple("CONSULTATION ADMIN", "咨询交接设置", "配置营业时段、每日容量、反馈时限和失败原因。")
        else -> Triple("LEADERSHIP SETTINGS", "领导大屏参数", "配置重要字幕、特色年级和展示策略。")
    }
    var confirmation by remember { mutableStateOf<Pair<AdminNativeItem, String>?>(null) }
    var commandOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AdminNativeItem?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(downloadArtifact?.contentType ?: "application/octet-stream")
    ) { uri ->
        val artifact = downloadArtifact
        val saved = uri != null && artifact != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(artifact.bytes) } ?: error("无法写入文件")
        }.isSuccess
        onDownloadHandled(saved)
    }
    LaunchedEffect(downloadArtifact) {
        downloadArtifact?.let { exportLauncher.launch(it.fileName) }
    }
    val enrichmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取文件")
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "customer-enrichment.xlsx"
            onCommand(PreviewAdminEnrichmentCommand(EvidenceUpload(name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes)))
        }
    }
    LaunchedEffect(key) { onLoad(key) }
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading(copy.first, copy.second, copy.third) { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "返回") } } }
        adminCommandLabel(key)?.let { label ->
            item {
                Button(
                    onClick = { if (key == "enrichment") enrichmentPicker.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") else commandOpen = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(label) }
            }
        }
        if (module == null && busy) item { LoadingPane("正在读取实时管理数据") }
        module?.let { value ->
            if (value.metrics.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(value.metrics, key = { it.first }) { metric ->
                        YinggeCard(Modifier.widthIn(min = 132.dp)) {
                            Text(metric.first, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(metric.second.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            item { Text(value.summary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (value.items.isEmpty()) item { EmptyPane("暂无待处理数据", "下拉刷新或稍后再试。") }
            items(value.items, key = { it.id }) { row ->
                YinggeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            ExpandableText(row.details, collapsedLines = 4, modifier = Modifier.padding(top = 6.dp))
                        }
                        AssistChip(onClick = {}, enabled = false, label = { Text(row.status.ifBlank { "—" }) })
                    }
                    if (row.actions.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            row.actions.forEach { action ->
                                if (action in setOf("导出", "下载报告")) {
                                    Button(onClick = { onDownload(row) }, enabled = !busy) { Text(action) }
                                    return@forEach
                                }
                                if (action in setOf("编辑", "更正", "分配")) {
                                    OutlinedButton(onClick = { editingItem = row }, enabled = !busy) { Text(action) }
                                    return@forEach
                                }
                                val destructive = action in setOf("删除", "驳回", "归档", "撤销", "停用", "暂停补算")
                                if (destructive) OutlinedButton(onClick = { confirmation = row to action }, enabled = !busy) { Text(action) }
                                else Button(onClick = { confirmation = row to action }, enabled = !busy) { Text(action) }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmation?.let { (row, action) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            icon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
            title = { Text("确认执行“$action”") },
            text = { Text("对象：${row.title}\n\n该操作将由服务端重新校验权限、版本和业务状态，并写入审计记录。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onAction(row, action); confirmation = null }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
    if (commandOpen) AdminNativeCommandDialog(key, module, members, busy, null, { commandOpen = false }) { command ->
        onCommand(command)
        commandOpen = false
    }
    editingItem?.let { item ->
        AdminNativeCommandDialog(key, module, members, busy, item, { editingItem = null }) { command ->
            onCommand(command)
            editingItem = null
        }
    }
}

private fun adminCommandLabel(key: String): String? = when (key) {
    "tasks_admin" -> "创建并派发任务"
    "content_admin" -> "发布公告"
    "performance_admin" -> "绩效设置与人工分"
    "dictionaries" -> "新增字典或标签"
    "ai_settings" -> "配置 AI 服务"
    "enrichment" -> "选择 XLSX 并预览"
    "webhooks" -> "新增企业微信 Webhook"
    "followup_policy" -> "修改跟进策略"
    "consultation_settings" -> "修改咨询设置"
    "leadership_settings" -> "修改展示参数"
    else -> null
}

@Composable
private fun AdminNativeCommandDialog(
    key: String,
    module: AdminNativeModule?,
    members: List<AdminMember>,
    busy: Boolean,
    editingItem: AdminNativeItem?,
    onDismiss: () -> Unit,
    onSubmit: (AdminNativeCommand) -> Unit,
) {
    when (key) {
        "market" -> {
            var correctedLevel by remember(editingItem) { mutableStateOf(editingItem?.metadata?.get("level") ?: "B") }
            var note by remember(editingItem) { mutableStateOf("") }
            AdminFormDialog(
                "更正客户分级",
                busy,
                onDismiss,
                editingItem != null && note.isNotBlank(),
                {
                    val item = requireNotNull(editingItem)
                    onSubmit(
                        CorrectAdminClassificationCommand(
                            item.id,
                            item.metadata["customerId"].orEmpty(),
                            correctedLevel,
                            note,
                        )
                    )
                },
            ) {
                Text("仅更正等级；原认定说明、需求与事实证据将原样保留并形成新版本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("A", "B", "C")) { value -> FilterChip(correctedLevel == value, { correctedLevel = value }, { Text("$value 类") }) }
                }
                OutlinedTextField(note, { note = it.take(2000) }, label = { Text("更正说明") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        }
        "tasks_admin" -> {
            var title by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            var distribution by remember { mutableStateOf("MANDATORY") }
            var dueAt by remember { mutableStateOf(LocalDate.now().plusDays(1).atTime(18, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString()) }
            var assignees by remember { mutableStateOf(setOf<String>()) }
            AdminFormDialog("创建任务", busy, onDismiss, title.isNotBlank() && description.isNotBlank() && dueAt.isNotBlank() && (distribution != "ASSIGNED" || assignees.isNotEmpty()), {
                onSubmit(CreateAdminTaskCommand(title, description, distribution, dueAt, assignees))
            }) {
                OutlinedTextField(title, { title = it }, label = { Text("任务标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("任务说明") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text("派发方式", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("MANDATORY", "ASSIGNED", "CLAIMABLE")) { value -> FilterChip(distribution == value, { distribution = value }, { Text(when (value) { "MANDATORY" -> "强制性"; "ASSIGNED" -> "派发性"; else -> "自领取" }) }) } }
                if (distribution == "ASSIGNED") {
                    Text("选择成员", fontWeight = FontWeight.SemiBold)
                    members.filter { it.status == "ACTIVE" }.forEach { member ->
                        CheckboxRow(member.displayName, member.membershipId in assignees) { checked -> assignees = if (checked) assignees + member.membershipId else assignees - member.membershipId }
                    }
                }
                OutlinedTextField(dueAt, { dueAt = it }, label = { Text("截止时间（ISO 8601）") }, modifier = Modifier.fillMaxWidth())
            }
        }
        "content_admin" -> {
            var title by remember(editingItem) { mutableStateOf(editingItem?.title.orEmpty()) }
            var content by remember(editingItem) { mutableStateOf(editingItem?.metadata?.get("content").orEmpty()) }
            var priority by remember(editingItem) { mutableStateOf(editingItem?.metadata?.get("priority") ?: "NORMAL") }
            var pinned by remember(editingItem) { mutableStateOf(editingItem?.metadata?.get("pinned")?.toBooleanStrictOrNull() ?: false) }
            var leadership by remember(editingItem) { mutableStateOf(editingItem?.metadata?.get("showOnLeadershipScreen")?.toBooleanStrictOrNull() ?: false) }
            AdminFormDialog(if (editingItem == null) "发布公告" else "编辑公告", busy, onDismiss, title.isNotBlank() && content.isNotBlank(), {
                if (editingItem == null) onSubmit(CreateAdminAnnouncementCommand(title, content, priority, pinned))
                else onSubmit(UpdateAdminAnnouncementCommand(editingItem.id.removePrefix("announcement:"), title, content, priority, pinned, leadership))
            }) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("公告内容") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("LOW", "NORMAL", "HIGH", "URGENT")) { value -> FilterChip(priority == value, { priority = value }, { Text(value) }) } }
                CheckboxRow("置顶公告", pinned) { pinned = it }
                CheckboxRow("领导大屏展示", leadership) { leadership = it }
            }
        }
        "performance_admin" -> {
            if (editingItem?.id?.startsWith("activity:") == true) {
                var verified by remember(editingItem) { mutableStateOf(editingItem.metadata["actualParticipants"] ?: "0") }
                var selected by remember(editingItem) { mutableStateOf(setOf<String>()) }
                var pointValues by remember(editingItem) { mutableStateOf<Map<String, String>>(emptyMap()) }
                val allocations = selected.mapNotNull { membershipId -> pointValues[membershipId]?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { PerformanceAllocationDraft(membershipId, it) } }
                AdminFormDialog("分配活动绩效", busy, onDismiss, verified.toIntOrNull() != null && allocations.size == selected.size && allocations.isNotEmpty(), {
                    onSubmit(AllocateAdminPerformanceActivityCommand(editingItem.id.removePrefix("activity:"), verified.toInt(), allocations, editingItem.version))
                }) {
                    Text("核定人数决定活动档次；分配合计必须与服务端规则档次总分一致，每份为 0.5 分的倍数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(verified, { verified = it.filter(Char::isDigit) }, label = { Text("核定实际人数") }, modifier = Modifier.fillMaxWidth())
                    members.filter { it.status == "ACTIVE" }.forEach { member ->
                        CheckboxRow(member.displayName, member.membershipId in selected) { checked ->
                            selected = if (checked) selected + member.membershipId else selected - member.membershipId
                            if (checked && member.membershipId !in pointValues) pointValues = pointValues + (member.membershipId to "0.5")
                        }
                        if (member.membershipId in selected) OutlinedTextField(
                            pointValues[member.membershipId].orEmpty(),
                            { value -> pointValues = pointValues + (member.membershipId to value.filter { ch -> ch.isDigit() || ch == '.' }) },
                            label = { Text("${member.displayName} 分数") },
                            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 6.dp),
                        )
                    }
                    Text("当前分配合计：${allocations.sumOf { it.points }} 分", fontWeight = FontWeight.SemiBold)
                }
                return
            }
            var mode by remember { mutableStateOf("MANUAL") }
            var memberId by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }
            var type by remember { mutableStateOf("LOGISTICS") }; var title by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }; var points by remember { mutableStateOf("") }
            var baseline by remember { mutableStateOf("14") }; var opensAt by remember { mutableStateOf("09:00") }; var closesAt by remember { mutableStateOf("22:00") }
            var ordinary by remember { mutableStateOf("1") }; var deep by remember { mutableStateOf("2") }; var shortfall by remember { mutableStateOf("MARK_ONLY") }
            var tierText by remember { mutableStateOf("Pro,50,,8") }
            val tiers = remember(tierText) { parsePerformanceTiers(tierText) }
            val valid = when (mode) {
                "MANUAL" -> memberId.isNotBlank() && title.isNotBlank() && reason.isNotBlank() && points.toDoubleOrNull() != null
                "PARTICIPANT" -> memberId.isNotBlank()
                else -> baseline.toDoubleOrNull() != null && ordinary.toDoubleOrNull() != null && deep.toDoubleOrNull() != null && tiers != null
            }
            AdminFormDialog("绩效设置与人工分", busy, onDismiss, valid, {
                when (mode) {
                    "MANUAL" -> onSubmit(CreateAdminManualPerformanceCommand(memberId, date, type, title, reason, points.toDouble()))
                    "PARTICIPANT" -> onSubmit(AddAdminPerformanceParticipantCommand(memberId, date, null))
                    else -> onSubmit(CreateAdminPerformanceRuleCommand(date, baseline.toDouble(), "WEEKDAYS", setOf(1,2,3,4,5), opensAt, closesAt, shortfall, ordinary.toDouble(), deep.toDouble(), null, null, tiers!!))
                }
            }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("MANUAL", "PARTICIPANT", "RULE")) { value -> FilterChip(mode == value, { mode = value }, { Text(when(value){"MANUAL"->"人工分";"PARTICIPANT"->"参与人员";else->"新规则"}) }) }
                }
                if (mode in setOf("MANUAL", "PARTICIPANT")) {
                    Text("成员", fontWeight = FontWeight.SemiBold)
                    members.filter { it.status == "ACTIVE" }.forEach { member -> RadioButtonRow(member.displayName, memberId == member.membershipId) { memberId = member.membershipId } }
                }
                OutlinedTextField(date, { date = it }, label = { Text(if (mode == "RULE") "生效日期 YYYY-MM-DD" else "计分/开始日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                if (mode == "MANUAL") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("LOGISTICS", "CORRECTION")) { value -> FilterChip(type == value, { type = value }, { Text(if (value == "LOGISTICS") "基础后勤" else "纠错") }) } }
                    OutlinedTextField(title, { title = it }, label = { Text("事项") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(reason, { reason = it }, label = { Text("原因") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(points, { points = it.filter { ch -> ch.isDigit() || ch in ".-" } }, label = { Text("分数（0.5 为最小单位）") }, modifier = Modifier.fillMaxWidth())
                } else if (mode == "RULE") {
                    OutlinedTextField(baseline, { baseline = it }, label = { Text("每日 baseline") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(opensAt, { opensAt = it }, label = { Text("开放") }, modifier = Modifier.weight(1f)); OutlinedTextField(closesAt, { closesAt = it }, label = { Text("截止") }, modifier = Modifier.weight(1f)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(ordinary, { ordinary = it }, label = { Text("普通分") }, modifier = Modifier.weight(1f)); OutlinedTextField(deep, { deep = it }, label = { Text("深度分") }, modifier = Modifier.weight(1f)) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("MARK_ONLY", "DEDUCT_SHORTFALL")) { value -> FilterChip(shortfall == value, { shortfall = value }, { Text(if (value == "MARK_ONLY") "仅标记差额" else "扣除差额") }) } }
                    OutlinedTextField(tierText, { tierText = it }, label = { Text("活动档位：名称,最小,最大,分数；每行一档") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    if (tiers == null) Text("档位格式错误或人数区间不连续。", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        "dictionaries" -> DictionaryCommandDialog(busy, onDismiss, onSubmit)
        "ai_settings" -> {
            var enabled by remember { mutableStateOf(module?.configuration?.get("enabled")?.toBooleanStrictOrNull() ?: (module?.items?.firstOrNull()?.status != "已停用")) }; var baseUrl by remember { mutableStateOf(module?.configuration?.get("baseUrl").orEmpty()) }; var model by remember { mutableStateOf(module?.configuration?.get("model") ?: module?.items?.firstOrNull()?.title.orEmpty()) }; var apiKey by remember { mutableStateOf("") }; var clearKey by remember { mutableStateOf(false) }
            AdminFormDialog("配置 AI 服务", busy, onDismiss, baseUrl.isNotBlank() && model.isNotBlank(), { onSubmit(UpdateAdminAssistantCommand(enabled, baseUrl, model, apiKey.ifBlank { null }, clearKey, module?.items?.firstOrNull()?.version ?: 0)) }) {
                CheckboxRow("启用智能助手", enabled) { enabled = it }
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Provider Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("新 API Key（留空保持原密钥）") }, modifier = Modifier.fillMaxWidth())
                Text("密钥只单向发送，不回显、不缓存。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                CheckboxRow("清除已配置密钥", clearKey) { clearKey = it }
            }
        }
        "webhooks" -> {
            var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }; var events by remember { mutableStateOf(setOf("A_CLASS_STALE")) }
            val eventOptions = listOf("A_CLASS_STALE", "CUSTOMER_DEAL_REQUEST_PENDING", "CUSTOMER_DEAL_REQUEST_APPROVED", "CUSTOMER_DEAL_REQUEST_REJECTED")
            AdminFormDialog("新增企业微信 Webhook", busy, onDismiss, name.isNotBlank() && url.startsWith("https://qyapi.weixin.qq.com/") && events.isNotEmpty(), { onSubmit(CreateAdminWebhookCommand(name, url, events)) }) {
                OutlinedTextField(name, { name = it }, label = { Text("通道名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("Webhook URL（单向录入）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("订阅事件", fontWeight = FontWeight.SemiBold)
                eventOptions.forEach { event -> CheckboxRow(event, event in events) { checked -> events = if (checked) events + event else events - event } }
                Text("保存后 URL 不再回显，也不会进入 App 缓存。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
        "followup_policy" -> FollowUpPolicyDialog(module, busy, onDismiss, onSubmit)
        "consultation_settings" -> ConsultationSettingsDialog(module, busy, onDismiss, onSubmit)
        "leadership_settings" -> LeadershipSettingsDialog(module, busy, onDismiss, onSubmit)
    }
}

private fun parsePerformanceTiers(raw: String): List<PerformanceTierDraft>? = runCatching {
    val rows = raw.lines().map(String::trim).filter(String::isNotBlank).map { line ->
        val cells = line.split(',').map(String::trim)
        require(cells.size == 4)
        PerformanceTierDraft(cells[0], cells[1].toInt(), cells[2].takeIf(String::isNotBlank)?.toInt(), cells[3].toDouble())
    }.sortedBy { it.minParticipants }
    require(rows.isNotEmpty())
    rows.forEachIndexed { index, row ->
        require(row.label.isNotBlank() && row.points >= 0 && (row.maxParticipants == null || row.maxParticipants >= row.minParticipants))
        if (index < rows.lastIndex) require(row.maxParticipants != null && row.maxParticipants + 1 == rows[index + 1].minParticipants)
    }
    rows
}.getOrNull()

@Composable
private fun FollowUpPolicyDialog(
    module: AdminNativeModule?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AdminNativeCommand) -> Unit,
) {
    val config = module?.configuration.orEmpty()
    var reminderEnabled by remember(module) { mutableStateOf(config["aClassStaleEnabled"]?.toBooleanStrictOrNull() ?: true) }
    var staleHours by remember(module) { mutableStateOf(config["staleAfterHours"] ?: "72") }
    var notifyAt by remember(module) { mutableStateOf(config["notifyAt"] ?: "09:00") }
    var qqEnabled by remember(module) { mutableStateOf(config["qqEnabled"]?.toBooleanStrictOrNull() ?: false) }
    var autoRelease by remember(module) { mutableStateOf(config["abAutoReleaseEnabled"]?.toBooleanStrictOrNull() ?: false) }
    val hours = staleHours.toIntOrNull()
    val validTime = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?$").matches(notifyAt)
    AdminFormDialog(
        title = "修改跟进策略",
        busy = busy,
        onDismiss = onDismiss,
        valid = hours in 24..720 && validTime,
        onConfirm = {
            onSubmit(
                UpdateAdminFollowUpPolicyCommand(
                    reminderEnabled,
                    hours!!,
                    notifyAt,
                    qqEnabled,
                    autoRelease,
                    config["version"]?.toLongOrNull() ?: 0,
                )
            )
        },
    ) {
        CheckboxRow("启用 A 类长时间未跟进重点提醒", reminderEnabled) { reminderEnabled = it }
        OutlinedTextField(
            staleHours,
            { staleHours = it.filter(Char::isDigit).take(3) },
            label = { Text("未有效跟进阈值（小时）") },
            supportingText = { Text("允许 24–720 小时，默认 72 小时。") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(notifyAt, { notifyAt = it.take(8) }, label = { Text("每日通知时间") }, modifier = Modifier.fillMaxWidth())
        CheckboxRow("QQ 提醒通道", qqEnabled) { qqEnabled = it }
        CheckboxRow("启用 A/B 超时自动释放", autoRelease) { autoRelease = it }
        if (autoRelease) {
            Text(
                "开启后仅处理新生效时间之后到期的 A/B 客户；保持关闭可继续提醒而不自动改变归属。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ConsultationSettingsDialog(
    module: AdminNativeModule?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AdminNativeCommand) -> Unit,
) {
    val config = module?.configuration.orEmpty()
    var mode by remember { mutableStateOf("SETTINGS") }
    var businessStart by remember(module) { mutableStateOf(config["businessStart"] ?: "09:00") }
    var businessEnd by remember(module) { mutableStateOf(config["businessEnd"] ?: "21:00") }
    var leadMinutes by remember(module) { mutableStateOf(config["minimumLeadMinutes"] ?: "30") }
    var durations by remember(module) { mutableStateOf(config["allowedDurations"] ?: "30,45,60,90") }
    var dailyCapacity by remember(module) { mutableStateOf(config["dailyCapacity"] ?: "6") }
    var feedbackHours by remember(module) { mutableStateOf(config["feedbackDueHours"] ?: "48") }
    var reasonCode by remember { mutableStateOf("") }
    var reasonLabel by remember { mutableStateOf("") }
    var reasonOrder by remember { mutableStateOf("0") }
    val timeRegex = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?$")
    val parsedDurations = durations.split(',').map(String::trim).filter(String::isNotBlank).mapNotNull(String::toIntOrNull)
    val settingsValid = timeRegex.matches(businessStart) && timeRegex.matches(businessEnd) && businessStart < businessEnd &&
        leadMinutes.toIntOrNull() in 0..10080 && dailyCapacity.toIntOrNull() in 1..100 &&
        feedbackHours.toIntOrNull() in 1..720 && parsedDurations.isNotEmpty() && parsedDurations.size <= 10 &&
        parsedDurations.distinct().size == parsedDurations.size && parsedDurations.all { it in 15..480 }
    val reasonValid = reasonCode.isNotBlank() && reasonLabel.isNotBlank() && reasonOrder.toIntOrNull() != null
    AdminFormDialog(
        title = "修改咨询设置",
        busy = busy,
        onDismiss = onDismiss,
        valid = if (mode == "SETTINGS") settingsValid else reasonValid,
        onConfirm = {
            if (mode == "SETTINGS") {
                onSubmit(
                    UpdateAdminConsultationSettingsCommand(
                        businessStart,
                        businessEnd,
                        leadMinutes.toInt(),
                        parsedDurations,
                        dailyCapacity.toInt(),
                        feedbackHours.toInt(),
                        config["version"]?.toLongOrNull() ?: 0,
                    )
                )
            } else {
                onSubmit(SaveAdminConsultationReasonCommand(null, reasonCode, reasonLabel, true, reasonOrder.toInt()))
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == "SETTINGS", { mode = "SETTINGS" }, { Text("预约规则") })
            FilterChip(mode == "REASON", { mode = "REASON" }, { Text("新增失败原因") })
        }
        if (mode == "SETTINGS") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(businessStart, { businessStart = it.take(8) }, label = { Text("营业开始") }, modifier = Modifier.weight(1f))
                OutlinedTextField(businessEnd, { businessEnd = it.take(8) }, label = { Text("营业结束") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(leadMinutes, { leadMinutes = it.filter(Char::isDigit) }, label = { Text("最少提前分钟") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(durations, { durations = it.filter { ch -> ch.isDigit() || ch == ',' } }, label = { Text("可选时长（分钟，逗号分隔）") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dailyCapacity, { dailyCapacity = it.filter(Char::isDigit) }, label = { Text("每日容量") }, modifier = Modifier.weight(1f))
                OutlinedTextField(feedbackHours, { feedbackHours = it.filter(Char::isDigit) }, label = { Text("反馈时限小时") }, modifier = Modifier.weight(1f))
            }
        } else {
            OutlinedTextField(reasonCode, { reasonCode = it.uppercase().replace(' ', '_').take(80) }, label = { Text("原因编码") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reasonLabel, { reasonLabel = it.take(160) }, label = { Text("显示名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reasonOrder, { reasonOrder = it.filter { ch -> ch.isDigit() || ch == '-' } }, label = { Text("排序") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LeadershipSettingsDialog(
    module: AdminNativeModule?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AdminNativeCommand) -> Unit,
) {
    val config = module?.configuration.orEmpty()
    val allScenes = listOf("time", "culture", "briefing", "trend", "champions", "funnel", "mission", "followUpRisk", "schoolComparison")
    val allEvents = listOf("zeroBreakthrough", "customerAdded", "customerUpdated", "milestone", "trendSurge", "conversionStage", "taskPublished", "taskMilestone", "rankingChange", "teamMomentum")
    val sceneOrder = config["sceneOrder"]?.split(',')?.filter(String::isNotBlank)?.takeIf { it.toSet() == allScenes.toSet() } ?: allScenes
    var animations by remember(module) { mutableStateOf(config["animationsEnabled"]?.toBooleanStrictOrNull() ?: true) }
    var standby by remember(module) { mutableStateOf(config["standbyEnabled"]?.toBooleanStrictOrNull() ?: true) }
    var idleMinutes by remember(module) { mutableStateOf(config["idleTimeoutMinutes"] ?: "20") }
    var sceneSeconds by remember(module) { mutableStateOf(config["sceneDurationSeconds"] ?: "12") }
    var eventSeconds by remember(module) { mutableStateOf(config["eventDurationSeconds"] ?: "7") }
    var intensity by remember(module) { mutableStateOf(config["motionIntensity"] ?: "CINEMATIC") }
    var enabledScenes by remember(module) { mutableStateOf(config["enabledScenes"]?.split(',')?.filter(String::isNotBlank)?.toSet()?.ifEmpty { allScenes.toSet() } ?: allScenes.toSet()) }
    var enabledEvents by remember(module) { mutableStateOf(config["enabledEvents"]?.split(',')?.filter(String::isNotBlank)?.toSet() ?: allEvents.toSet()) }
    var grade by remember(module) { mutableStateOf(config["featuredGrade"] ?: "2026") }
    val valid = idleMinutes.toIntOrNull() in 5..120 && sceneSeconds.toIntOrNull() in 5..60 &&
        eventSeconds.toIntOrNull() in 3..15 && Regex("^(19|20)\\d{2}$").matches(grade) && enabledScenes.isNotEmpty()
    AdminFormDialog(
        title = "修改领导大屏参数",
        busy = busy,
        onDismiss = onDismiss,
        valid = valid,
        onConfirm = {
            onSubmit(
                UpdateAdminLeadershipSettingsCommand(
                    animations,
                    standby,
                    idleMinutes.toInt(),
                    sceneSeconds.toInt(),
                    eventSeconds.toInt(),
                    intensity,
                    sceneOrder,
                    sceneOrder.filter(enabledScenes::contains),
                    allEvents.filter(enabledEvents::contains),
                    grade,
                    config["version"]?.toLongOrNull() ?: 0,
                )
            )
        },
    ) {
        CheckboxRow("启用动画", animations) { animations = it }
        CheckboxRow("启用待机场景", standby) { standby = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(idleMinutes, { idleMinutes = it.filter(Char::isDigit) }, label = { Text("空闲分钟") }, modifier = Modifier.weight(1f))
            OutlinedTextField(sceneSeconds, { sceneSeconds = it.filter(Char::isDigit) }, label = { Text("场景秒数") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(eventSeconds, { eventSeconds = it.filter(Char::isDigit) }, label = { Text("事件动画秒数") }, modifier = Modifier.fillMaxWidth())
        Text("动画强度", fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("SUBTLE", "CINEMATIC", "STRONG")) { value -> FilterChip(intensity == value, { intensity = value }, { Text(value) }) }
        }
        OutlinedTextField(grade, { grade = it.filter(Char::isDigit).take(4) }, label = { Text("重点年级") }, modifier = Modifier.fillMaxWidth())
        Text("待机场景", fontWeight = FontWeight.SemiBold)
        allScenes.forEach { scene -> CheckboxRow(scene, scene in enabledScenes) { checked -> enabledScenes = if (checked) enabledScenes + scene else enabledScenes - scene } }
        Text("事件动画", fontWeight = FontWeight.SemiBold)
        allEvents.forEach { event -> CheckboxRow(event, event in enabledEvents) { checked -> enabledEvents = if (checked) enabledEvents + event else enabledEvents - event } }
    }
}

@Composable
private fun DictionaryCommandDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (AdminNativeCommand) -> Unit) {
    var tagMode by remember { mutableStateOf(false) }; var label by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var type by remember { mutableStateOf("SCHOOL") }; var category by remember { mutableStateOf("客户标签") }; var color by remember { mutableStateOf("#3159D8") }
    AdminFormDialog("新增字典或标签", busy, onDismiss, label.isNotBlank() && (if (tagMode) category.isNotBlank() && Regex("#[0-9a-fA-F]{6}").matches(color) else code.isNotBlank()), {
        onSubmit(if (tagMode) CreateAdminTagCommand(label, category, color) else CreateAdminDictionaryCommand(type, code, label, 0))
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!tagMode, { tagMode = false }, { Text("字典") }); FilterChip(tagMode, { tagMode = true }, { Text("标签") }) }
        if (!tagMode) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("SCHOOL", "SOURCE_CHANNEL", "FOCUS_DIRECTION", "INTERACTION_CHANNEL")) { value -> FilterChip(type == value, { type = value }, { Text(value) }) } }
            OutlinedTextField(code, { code = it.uppercase().replace(' ', '_') }, label = { Text("唯一编码") }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(category, { category = it }, label = { Text("标签分类") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(color, { color = it }, label = { Text("颜色 #RRGGBB") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(label, { label = it }, label = { Text(if (tagMode) "标签名称" else "显示名称") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AdminFormDialog(title: String, busy: Boolean, onDismiss: () -> Unit, valid: Boolean, onConfirm: () -> Unit, fields: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = fields,
            )
        },
        confirmButton = { TextButton(enabled = valid && !busy, onClick = onConfirm) { Text("确认提交") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked); Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}
