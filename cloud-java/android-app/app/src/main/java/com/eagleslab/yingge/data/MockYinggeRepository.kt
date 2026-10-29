package com.eagleslab.yingge.data

import com.eagleslab.yingge.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Preview-only repository used while contracts/mobile-api.yaml is absent. */
class MockYinggeRepository : YinggeRepository {
    private val lock = Mutex()
    private val now = Instant.now()
    private val customerRows = mutableListOf(
        Customer("c-1", "周欣妍", "小周", "院校甲", "计算机科学与技术", "2026级", qq = "32****273", source = "新生群", ownerName = "王莹", collaboratorNames = listOf("刘悦扬"), quality = CustomerQuality.A, intention = "高", demandSummary = "计划转专业，重点关注课程衔接与时间安排。", lastContactAt = now.minus(76, ChronoUnit.HOURS), nextFollowUpAt = now.plus(3, ChronoUnit.HOURS), canRequestDeal = true),
        Customer("c-2", "陈慕蕊", school = "院校乙", major = "信息安全", grade = "2026级", wechat = "cmr***", source = "小红书", ownerName = "刘悦扬", quality = CustomerQuality.B, demandSummary = "需要进一步确认报名时间。", lastContactAt = now.minus(22, ChronoUnit.HOURS)),
        Customer("c-3", "林弘轩", school = "院校甲", major = "智能科学与技术", grade = "2025级", phone = "138****1203", source = "抖音", ownerName = "王莹", quality = CustomerQuality.C, intention = "一般", demandSummary = "目前处于了解阶段。", lastContactAt = now.minus(8, ChronoUnit.DAYS)),
    )
    private val interactionRows = mutableListOf(
        Interaction("i-1", "c-1", now.minus(76, ChronoUnit.HOURS), now.minus(75, ChronoUnit.HOURS), InteractionType.CUSTOMER_REPLY, "QQ", "明确希望了解转专业流程", "已确认主要顾虑为课程难度和时间成本。", "王莹"),
        Interaction("i-2", "c-1", now.minus(5, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS), InteractionType.INTERNAL_NOTE, "系统", "客户画像完成初步整理", creatorName = "系统管理员"),
    )
    private val reminderRows = mutableListOf(
        Reminder("r-1", "c-1", "周欣妍", "确认咨询时间", now.minus(30, ChronoUnit.MINUTES), overdue = true),
        Reminder("r-2", "c-2", "陈慕蕊", "补充发送课程方案", now.plus(4, ChronoUnit.HOURS), overdue = false),
    )
    private val dealRows = mutableListOf<DealRequest>()
    private val taskRows = mutableListOf(
        WorkTask("task-1", "完成重点客户回访", now.plus(6, ChronoUnit.HOURS), WorkTaskStatus.OPEN),
        WorkTask("task-2", "提交本周内容素材", now.plus(1, ChronoUnit.DAYS), WorkTaskStatus.CLAIMED),
    )
    private val consultationRows = mutableListOf(ConsultationItem("consult-1", "周欣妍", ConsultationStatus.WAITING_RESULT, "48 小时内回填结果"))
    private val contentRows = mutableListOf(ContentTaskItem("content-1", "新生转专业经验", "小红书", false))
    private val agentRows = mutableListOf(AgentAuthorization("agent-1", "个人学习 Agent", listOf("yingge.read", "yingge.write"), now.minus(1, ChronoUnit.DAYS)))
    private val deviceRows = mutableListOf(MobileDeviceItem("device-current", "当前 Android 设备", true, now), MobileDeviceItem("device-old", "旧 Android 设备", false, now.minus(8, ChronoUnit.DAYS)))
    private var submittedSummary = DailySummary(LocalDate.now(), 3, 2, false, "", "", false)

    private suspend fun networkBeat() = delay(120)

    override suspend fun registrationOptions(serverUrl: String, registrationCode: String): RegistrationOptions {
        networkBeat()
        if (serverUrl.isBlank()) throw RepositoryException("SERVER_REQUIRED", "请先填写服务器地址")
        if (registrationCode.isBlank()) throw RepositoryException("REGISTRATION_CODE_REQUIRED", "请输入邀请码")
        return RegistrationOptions(listOf(
            RegistrationTeam("team-jsu", "院校甲团队"),
            RegistrationTeam("team-just", "院校乙团队"),
        ))
    }

    override suspend fun registerAndLogin(serverUrl: String, username: String, realName: String, password: String, registrationCode: String, teamId: String): SessionUser {
        registrationOptions(serverUrl, registrationCode)
        if (username.isBlank() || realName.isBlank() || password.isBlank() || teamId.isBlank()) {
            throw RepositoryException("VALIDATION_FAILED", "请完整填写账号、真实姓名、密码和团队")
        }
        networkBeat()
        return SessionUser("preview-user", "preview-member", realName.trim(), setOf(UserRole.MEMBER))
    }

    override suspend fun login(serverUrl: String, username: String, password: String): SessionUser {
        networkBeat()
        if (serverUrl.isBlank()) throw RepositoryException("SERVER_REQUIRED", "请先填写服务器地址")
        if (username.isBlank() || password.isBlank()) throw RepositoryException("INVALID_CREDENTIALS", "请输入账号和密码")
        return SessionUser("preview-user", "preview-member", "移动端预览账号", setOf(UserRole.ORG_ADMIN))
    }
    override suspend fun restoreSession(serverUrl: String): SessionUser = SessionUser("preview-user", "preview-member", "移动端预览账号", setOf(UserRole.MEMBER))
    override suspend fun bootstrap(): MobileAppBootstrap = MobileAppBootstrap(
        SessionUser("preview-user", "preview-member", "移动端预览账号", setOf(UserRole.ORG_ADMIN)),
        Instant.now(),
        "Asia/Shanghai",
        setOf(AppMode.EMPLOYEE, AppMode.ADMIN),
        setOf("CUSTOMER_READ", "CUSTOMER_WRITE", "ADMIN_OVERVIEW_READ", "ADMIN_MANAGEMENT", "LEADERSHIP_DASHBOARD_READ"),
        2,
        3,
    )

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        networkBeat()
        if (currentPassword.isBlank() || newPassword.length < 8) throw RepositoryException("PASSWORD_INVALID", "新密码至少 8 位")
    }

    override suspend fun logout() { networkBeat() }

    override suspend fun dashboard(): DashboardSnapshot = lock.withLock {
        networkBeat()
        DashboardSnapshot(customerRows.size, 1, reminderRows.count { !it.completed }, customerRows.count { it.quality == CustomerQuality.A && (it.lastContactAt?.isBefore(now.minus(72, ChronoUnit.HOURS)) == true) }, 11.5, 14.0, customerRows.take(3))
    }

    override suspend fun dashboardGreeting(): DashboardGreeting = DashboardGreeting(
        "下午好", "先确认事实，再推进下一步。", false, Instant.now().plus(1, ChronoUnit.HOURS)
    )

    override suspend fun customers(query: String): List<Customer> = lock.withLock {
        networkBeat()
        val keyword = query.trim()
        customerRows.filter { keyword.isBlank() || listOf(it.name, it.nickname, it.school, it.major, it.phone, it.wechat, it.qq).any { value -> value.contains(keyword, true) } }
    }

    override suspend fun customerPage(filter: CustomerFilter): CustomerPage = lock.withLock {
        networkBeat()
        val filtered = customerRows.filter { customer ->
            (filter.search.isBlank() || listOf(customer.name, customer.nickname, customer.school, customer.major, customer.phone, customer.wechat, customer.qq).any { it.contains(filter.search, true) }) &&
                (filter.status.isBlank() || customer.status == filter.status) &&
                (filter.intention.isBlank() || customer.intention == filter.intention) &&
                (filter.grade.isBlank() || customer.grade == filter.grade) &&
                (filter.school.isBlank() || customer.school.contains(filter.school, true))
        }
        val start = (filter.page * filter.size).coerceAtMost(filtered.size)
        val end = (start + filter.size).coerceAtMost(filtered.size)
        CustomerPage(filtered.subList(start, end), filter.page, filter.size, filtered.size.toLong(), (filtered.size + filter.size - 1) / filter.size)
    }

    override suspend fun customerOwnerOptions(): List<CustomerTransferCandidate> = listOf(CustomerTransferCandidate("preview-member", "预览员工", "测试团队"))

    override suspend fun customer(id: String): Customer = lock.withLock {
        customerRows.firstOrNull { it.id == id } ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
    }

    override suspend fun saveCustomer(draft: CustomerDraft): Customer = lock.withLock {
        if (draft.name.isBlank()) throw RepositoryException("VALIDATION_FAILED", "姓名为必填项")
        if (!draft.hasContact) throw RepositoryException("VALIDATION_FAILED", "手机号、微信号、QQ 号至少填写一个")
        val old = draft.id?.let { id -> customerRows.firstOrNull { it.id == id } }
        val row = Customer(
            id = old?.id ?: UUID.randomUUID().toString(), name = draft.name.trim(), nickname = draft.nickname.trim(),
            school = draft.school.trim(), major = draft.major.trim(), grade = draft.grade.trim(), phone = draft.phone.trim(),
            wechat = draft.wechat.trim(), qq = draft.qq.trim(), source = draft.source.trim(), demandSummary = draft.demandSummary.trim(),
            ownerName = old?.ownerName ?: "移动端预览账号", collaboratorNames = old?.collaboratorNames ?: emptyList(),
            quality = old?.quality ?: CustomerQuality.C, intention = old?.intention ?: "一般", status = old?.status ?: "跟进中",
            lastContactAt = old?.lastContactAt, nextFollowUpAt = old?.nextFollowUpAt,
            dealState = old?.dealState ?: DealState.NONE, canRequestDeal = old?.canRequestDeal ?: false,
            dealRequestedAt = old?.dealRequestedAt, version = (old?.version ?: -1) + 1,
        )
        if (old == null) customerRows.add(0, row) else customerRows[customerRows.indexOf(old)] = row
        row
    }

    override suspend fun customerCollaboration(customerId: String): CustomerCollaboration {
        val customer = customer(customerId)
        val members = listOf(CustomerCollaborationMember("preview-transfer", "刘悦扬", "院校甲团队"), CustomerCollaborationMember("preview-member", "预览员工", "测试团队"))
        return CustomerCollaboration(
            canManage = true, canTransferOwnership = true, canEditProfile = true, canArchive = true, canEditTimeline = true,
            collaborators = members.filter { it.displayName in customer.collaboratorNames }, candidates = members, transferCandidates = members,
        )
    }

    override suspend fun replaceCustomerCollaborators(customerId: String, membershipIds: Set<String>): CustomerCollaboration = lock.withLock {
        val index = customerRows.indexOfFirst { it.id == customerId }
        val row = customerRows.getOrNull(index) ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
        val names = buildList { if ("preview-transfer" in membershipIds) add("刘悦扬"); if ("preview-member" in membershipIds) add("预览员工") }
        customerRows[index] = row.copy(collaboratorNames = names, version = row.version + 1)
        CustomerCollaboration(canManage = true, canTransferOwnership = true, canEditProfile = true, canArchive = true, canEditTimeline = true,
            collaborators = listOf(CustomerCollaborationMember("preview-transfer", "刘悦扬", "院校甲团队"), CustomerCollaborationMember("preview-member", "预览员工", "测试团队")).filter { it.membershipId in membershipIds },
            candidates = listOf(CustomerCollaborationMember("preview-transfer", "刘悦扬", "院校甲团队"), CustomerCollaborationMember("preview-member", "预览员工", "测试团队")))
    }

    override suspend fun customerTransferCandidates(customerId: String): List<CustomerTransferCandidate> =
        listOf(CustomerTransferCandidate("preview-transfer", "刘悦扬", "院校甲团队"))

    override suspend fun transferCustomer(customerId: String, targetMembershipId: String, reason: String): Customer = lock.withLock {
        val index = customerRows.indexOfFirst { it.id == customerId }
        val row = customerRows.getOrNull(index) ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
        row.copy(ownerName = "刘悦扬", version = row.version + 1).also { customerRows[index] = it }
    }

    override suspend fun archiveCustomer(customerId: String) = lock.withLock {
        val index = customerRows.indexOfFirst { it.id == customerId }
        val row = customerRows.getOrNull(index) ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
        customerRows[index] = row.copy(archivedAt = Instant.now(), version = row.version + 1)
    }

    override suspend fun customerClassificationState(customerId: String): CustomerClassificationState {
        val row = customer(customerId)
        return CustomerClassificationState(listOf(CustomerClassification("classification-$customerId", 1, row.quality, "预览认定", row.demandSummary.ifBlank { "待补充" }, emptyList(), interactions(customerId).take(1).map { it.id }, now.minus(1, ChronoUnit.DAYS))), now.plus(48, ChronoUnit.HOURS), "旺季规则", false)
    }

    override suspend fun saveCustomerClassification(customerId: String, draft: CustomerClassificationDraft): CustomerClassificationState {
        if (draft.recognitionReason.isBlank() || draft.potentialDemand.isBlank() || draft.evidenceInteractionIds.isEmpty()) throw RepositoryException("VALIDATION_FAILED", "请填写认定说明、潜在需求并选择事实依据")
        if (draft.level == CustomerQuality.A && draft.signals.none(String::isNotBlank)) throw RepositoryException("A_CLASS_SIGNAL_REQUIRED", "A 类认定至少填写一项近期推进信号")
        return CustomerClassificationState(listOf(CustomerClassification(UUID.randomUUID().toString(), 2, draft.level, draft.recognitionReason.trim(), draft.potentialDemand.trim(), draft.signals, draft.evidenceInteractionIds.toList(), Instant.now())), now.plus(72, ChronoUnit.HOURS), "预览规则", false)
    }

    override suspend fun downloadCustomerTimeline(customerId: String): DownloadArtifact = DownloadArtifact("客户时间线-$customerId.csv", "text/csv", "时间,内容\n2026-08-26,预览导出\n".toByteArray())

    override suspend fun previewCustomerImport(file: EvidenceUpload): CustomerImportState {
        if (file.bytes.isEmpty()) throw RepositoryException("IMPORT_EMPTY", "导入文件为空")
        return CustomerImportState("preview-import", file.fileName, if (file.fileName.endsWith(".xlsx", true)) "XLSX" else "CSV", "PREVIEWED", 3, 2, 1, messages = listOf("第 4 行：联系方式缺失"))
    }

    override suspend fun commitCustomerImport(jobId: String): CustomerImportState = CustomerImportState(jobId, "preview.xlsx", "XLSX", "COMPLETED", 3, createdRows = 2, skippedRows = 1)

    override suspend fun downloadCustomers(format: String): DownloadArtifact = DownloadArtifact("客户档案.${if (format == "csv") "csv" else "xlsx"}", if (format == "csv") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "预览导出".toByteArray())

    override suspend fun customerInsight(customerId: String): CustomerInsight = CustomerInsight(customerId, "当前处于目标核实阶段，建议先确认时间节点与已采取行动。")

    override suspend fun interactions(customerId: String): List<Interaction> = lock.withLock {
        interactionRows.filter { it.customerId == customerId }.sortedByDescending { it.occurredAt }
    }

    override suspend fun addInteraction(customerId: String, draft: InteractionDraft): Interaction = lock.withLock {
        if (draft.summary.isBlank()) throw RepositoryException("VALIDATION_FAILED", "沟通摘要为必填项")
        val row = Interaction(UUID.randomUUID().toString(), customerId, draft.occurredAt ?: Instant.now(), Instant.now(), draft.type, draft.channel, draft.summary.trim(), draft.details.trim(), "移动端预览账号")
        interactionRows.add(row)
        val index = customerRows.indexOfFirst { it.id == customerId }
        if (index >= 0) customerRows[index] = customerRows[index].copy(lastContactAt = row.occurredAt)
        row
    }

    override suspend fun reminders(): List<Reminder> = lock.withLock { reminderRows.sortedBy { it.remindAt } }
    override suspend fun completeReminder(id: String) = lock.withLock {
        val index = reminderRows.indexOfFirst { it.id == id }
        if (index >= 0) reminderRows[index] = reminderRows[index].copy(completed = true)
    }

    override suspend fun dailySummary(): DailySummary = submittedSummary
    override suspend fun saveDailySummary(draft: DailySummaryDraft): DailySummary {
        if (draft.issues.isBlank() || draft.tomorrowPlan.isBlank()) throw RepositoryException("VALIDATION_FAILED", "今日问题和明日计划必须填写")
        submittedSummary = submittedSummary.copy(deepConversationCount = draft.deepConversationCount.toIntOrNull() ?: 0, issues = draft.issues.trim(), tomorrowPlan = draft.tomorrowPlan.trim(), submitted = true)
        return submittedSummary
    }

    override suspend fun requestDeal(customerId: String, idempotencyKey: String): DealRequest = lock.withLock {
        if (idempotencyKey.length < 16) throw RepositoryException("IDEMPOTENCY_KEY_INVALID", "幂等键无效")
        val customerIndex = customerRows.indexOfFirst { it.id == customerId }
        val customer = customerRows.getOrNull(customerIndex) ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
        if (!customer.canRequestDeal || customer.dealState != DealState.NONE) throw RepositoryException("DEAL_REQUEST_CONFLICT", "当前客户不能重复提交成单申请")
        val requestedAt = Instant.now()
        val row = DealRequest(UUID.randomUUID().toString(), customer.id, customer.name, ownerDisplayName = customer.ownerName, applicantMembershipId = "preview-member", applicantDisplayName = "移动端预览账号", status = DealRequestStatus.PENDING, requestedAt = requestedAt)
        dealRows.add(row)
        customerRows[customerIndex] = customer.copy(dealState = DealState.PENDING, canRequestDeal = false, dealRequestedAt = requestedAt)
        row
    }

    override suspend fun notifications(): List<NotificationItem> = listOf(
        NotificationItem("n-1", "A 类客户需重点跟进", "周欣妍超过 72 小时未产生有效时间线，请尽快处理。", now.minus(20, ChronoUnit.MINUTES), true),
        NotificationItem("n-2", "成单申报待确认", "一个业务周期已进入管理员审核。", now.minus(2, ChronoUnit.HOURS), false),
    )

    override suspend fun markNotificationRead(id: String) { networkBeat() }
    override suspend fun markAllNotificationsRead(): Int = 1

    override suspend fun businessModule(key: String): BusinessModule {
        networkBeat()
        val definitions = mapOf(
            "pool" to ("客户公共池" to listOf("A 类二线招募 3 项", "C 类释放池 1 项", "认领前仅显示脱敏摘要")),
            "second_line" to ("A 类二线协作" to listOf("待领取 3 项", "待主负责人授权 1 项", "最多 2 名二线")),
            "transfer" to ("客户转接" to listOf("转接候选人按团队与有效状态裁剪", "转接前展示负责人和提醒影响")),
            "tasks" to ("任务中心" to listOf("今日待完成 2 项", "待验收 1 项")),
            "performance" to ("绩效中心" to listOf("今日 11.5 / 14 分", "待审核交流 1 条")),
            "consultations" to ("咨询交接" to listOf("待顾问接受 1 项", "48 小时内待回填 1 项")),
            "content_tasks" to ("内容任务" to listOf("小红书方向 2 项", "抖音方向 1 项")),
            "circle" to ("内容圈子" to listOf("最新内容 8 条", "支持发送人筛选与搜索")),
            "announcements" to ("公告中心" to listOf("置顶公告 1 条", "未读公告 2 条")),
            "assistant" to ("智能助手" to listOf("客户洞察只基于已确认档案与时间线", "AI 草稿须人工确认后写入")),
            "releases" to ("更新日志" to listOf("2026.08.22 Android 原生客户端", "历史版本可在 Web 更新中心查看")),
            "agent" to ("我的 Agent" to listOf("移动端仅展示授权状态", "新授权与危险操作确认请前往 Web")),
            "devices" to ("设备管理" to listOf("当前 Android 设备", "撤销其他设备请前往 Web")),
        )
        val definition = definitions[key] ?: throw RepositoryException("MODULE_UNKNOWN", "未知功能模块")
        return BusinessModule(key, definition.first, "当前为契约预览数据；未获得服务端能力前不执行写操作。", ModuleAvailability.READ_ONLY, definition.second)
    }

    override suspend fun actOnBusinessModuleItem(key: String, id: String): BusinessModule =
        throw RepositoryException("PREVIEW_READ_ONLY", "预览数据不执行服务端写操作")

    override suspend fun tasks(): List<WorkTask> = lock.withLock { networkBeat(); taskRows.toList() }
    override suspend fun claimTask(taskId: String): WorkTask = lock.withLock {
        val index = taskRows.indexOfFirst { it.id == taskId }
        val task = taskRows.getOrNull(index) ?: throw RepositoryException("TASK_NOT_FOUND", "任务不存在")
        if (task.status != WorkTaskStatus.OPEN) throw RepositoryException("TASK_ALREADY_CLAIMED", "任务已被领取")
        task.copy(status = WorkTaskStatus.CLAIMED).also { taskRows[index] = it }
    }
    override suspend fun submitTask(taskId: String, result: String): WorkTask = lock.withLock {
        if (result.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请填写任务成果")
        val index = taskRows.indexOfFirst { it.id == taskId }
        val task = taskRows.getOrNull(index) ?: throw RepositoryException("TASK_NOT_FOUND", "任务不存在")
        if (task.status != WorkTaskStatus.CLAIMED) throw RepositoryException("TASK_STATE_INVALID", "当前任务不能提交")
        task.copy(status = WorkTaskStatus.SUBMITTED, result = result.trim()).also { taskRows[index] = it }
    }

    override suspend fun performance(): PerformanceSnapshot = PerformanceSnapshot(
        11.5, 14.0, 2.0,
        listOf("普通交流 3 × 1 分", "深度交流 4 × 2 分", "待审核 2 分"),
        listOf(PerformanceInteractionOption("i-1", "周欣妍", "明确希望了解转专业流程")),
        listOf(PerformanceHistoryPoint("2026-08-25", 15.0, 15.0, 14.0, true)),
        listOf(PerformanceLeaderboardRow(1, "成员甲", "第一团队", 92.0, 6, 6)),
        listOf(PerformanceActivityItem("activity-preview", "新生咨询会", "2026-08-27", "解答新生问题", 50, null, "APPROVED", 1, true)),
    )
    override suspend fun submitPerformanceEvidence(interactionId: String, requestedType: String, evidence: EvidenceUpload): PerformanceSnapshot {
        if (interactionId.isBlank() || requestedType !in setOf("NORMAL", "DEEP") || evidence.bytes.isEmpty()) throw RepositoryException("VALIDATION_FAILED", "请选择沟通记录、申报类型和证据")
        return performance().copy(pendingScore = 3.0)
    }
    override suspend fun proposePerformanceActivity(eventDate: String, title: String, goal: String, expectedParticipants: Int, description: String): PerformanceSnapshot {
        if (listOf(eventDate, title, goal, description).any { it.isBlank() } || expectedParticipants < 0) throw RepositoryException("VALIDATION_FAILED", "请完整填写活动提案")
        return performance().copy(activities = listOf(PerformanceActivityItem("activity-new", title, eventDate, goal, expectedParticipants, null, "PROPOSED", 0, true)))
    }
    override suspend fun completePerformanceActivity(activityId: String, actualParticipants: Int, expectedVersion: Long, evidence: EvidenceUpload): PerformanceSnapshot {
        if (actualParticipants < 0 || evidence.bytes.isEmpty()) throw RepositoryException("VALIDATION_FAILED", "请填写人数并上传证据")
        return performance().copy(activities = performance().activities.map { if (it.id == activityId) it.copy(actualParticipants = actualParticipants, status = "COMPLETION_SUBMITTED") else it })
    }

    override suspend fun consultations(): List<ConsultationItem> = lock.withLock { consultationRows.toList() }
    override suspend fun consultationOptions(customerName: String): ConsultationOptions {
        val customer = customerRows.singleOrNull { it.name == customerName.trim() } ?: throw RepositoryException("CUSTOMER_SELECTION_REQUIRED", "请输入唯一匹配的客户姓名")
        return ConsultationOptions(customer.id, customer.name, interactionRows.filter { it.customerId == customer.id }.map { ConsultationSource(it.id, it.summary) }, listOf(ConsultationCandidate("preview-consultant", "预览顾问")), listOf(ConsultationFailureReason("reason-1", "PAUSED", "客户暂缓"), ConsultationFailureReason("reason-2", "OTHER", "其他")))
    }
    override suspend fun submitConsultation(draft: ConsultationDraft): ConsultationItem = lock.withLock {
        if (listOf(draft.customerId, draft.consultantMembershipId, draft.sourceInteractionId, draft.consultationReason, draft.customerQuotes, draft.goalsMotivation, draft.actionsTaken, draft.mainConcerns, draft.productFit, draft.suggestedFocus, draft.explicitTaboos, draft.expectedResult, draft.scheduledStart, draft.scheduledEnd).any { it.isBlank() }) throw RepositoryException("VALIDATION_FAILED", "请完整填写并明确选择咨询事实")
        val name = customerRows.firstOrNull { it.id == draft.customerId }?.name ?: throw RepositoryException("CUSTOMER_NOT_FOUND", "客户不存在")
        ConsultationItem(UUID.randomUUID().toString(), name, ConsultationStatus.WAITING_ACCEPTANCE, "等待顾问接受").also { consultationRows.add(0, it) }
    }
    override suspend fun acceptConsultation(consultationId: String, expectedVersion: Long): ConsultationItem = updateConsultationPreview(consultationId, ConsultationStatus.SCHEDULED, "已排期")
    override suspend fun rejectConsultation(consultationId: String, expectedVersion: Long, reason: String): ConsultationItem {
        if (reason.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请填写拒绝原因")
        return updateConsultationPreview(consultationId, ConsultationStatus.DRAFT, "已拒绝：${reason.trim()}")
    }
    override suspend fun endConsultation(consultationId: String, expectedVersion: Long): ConsultationItem = updateConsultationPreview(consultationId, ConsultationStatus.WAITING_RESULT, "待回填结果")
    override suspend fun completeConsultation(consultationId: String, result: ConsultationResultDraft): ConsultationItem = lock.withLock {
        if (result.resultSummary.isBlank() || result.nextStep.isBlank() || result.nextOwnerMembershipId.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请完整回填结果、下一步和下一负责人")
        val index = consultationRows.indexOfFirst { it.id == consultationId }
        val item = consultationRows.getOrNull(index) ?: throw RepositoryException("CONSULTATION_NOT_FOUND", "咨询记录不存在")
        item.copy(status = ConsultationStatus.COMPLETED, nextAction = result.nextStep.trim()).also { consultationRows[index] = it }
    }

    private suspend fun updateConsultationPreview(id: String, status: ConsultationStatus, nextAction: String): ConsultationItem = lock.withLock {
        val index = consultationRows.indexOfFirst { it.id == id }
        val item = consultationRows.getOrNull(index) ?: throw RepositoryException("CONSULTATION_NOT_FOUND", "咨询记录不存在")
        item.copy(status = status, nextAction = nextAction, version = item.version + 1).also { consultationRows[index] = it }
    }

    override suspend fun contentTasks(): List<ContentTaskItem> = lock.withLock { contentRows.toList() }
    override suspend fun submitContentTask(taskId: String, platform: String, content: String): ContentTaskItem = lock.withLock {
        if (platform !in setOf("XIAOHONGSHU", "DOUYIN")) throw RepositoryException("CONTENT_PLATFORM_INVALID", "请选择小红书或抖音")
        if (content.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请输入内容或作品链接")
        val index = contentRows.indexOfFirst { it.id == taskId }
        val item = contentRows.getOrNull(index) ?: throw RepositoryException("CONTENT_TASK_NOT_FOUND", "内容任务不存在")
        item.copy(channel = if (platform == "DOUYIN") "抖音" else "小红书", submitted = true).also { contentRows[index] = it }
    }
    override suspend fun updateContentMetrics(taskId: String, metrics: String): ContentTaskItem = lock.withLock {
        if (metrics.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请输入指标")
        val index = contentRows.indexOfFirst { it.id == taskId }
        val item = contentRows.getOrNull(index) ?: throw RepositoryException("CONTENT_TASK_NOT_FOUND", "内容任务不存在")
        item.copy(metrics = metrics.trim()).also { contentRows[index] = it }
    }

    override suspend fun circlePosts(): List<CirclePost> = listOf(CirclePost("post-1", "王莹", "院校甲转专业经验整理", now.minus(2, ChronoUnit.HOURS)))
    override suspend fun circleFeed(search: String, senderMembershipId: String?, page: Int): CircleFeed {
        val senders = listOf(CircleSender("preview-member", "王莹"))
        val rows = circlePosts().filter { (search.isBlank() || it.content.contains(search, true) || it.senderName.contains(search, true)) && (senderMembershipId == null || senderMembershipId == "preview-member") }
        return CircleFeed(rows, senders, page, 20, rows.size.toLong())
    }
    override suspend fun announcements(): List<AnnouncementItem> = listOf(AnnouncementItem("announcement-1", "本周校园活动安排", "请各位成员在周五前完成活动准备。", true, now.minus(1, ChronoUnit.DAYS)))
    override suspend fun assistantWorkspace() = AssistantWorkspace(AssistantAvailability(true, "Preview Provider", "preview-model"))
    override suspend fun parseAssistantDraft(mode: String, text: String): AssistantWorkspace {
        if (text.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请输入待解析内容")
        return AssistantWorkspace(AssistantAvailability(true, "Preview Provider", "preview-model"), AssistantStructuredDraft(mode, listOf("摘要" to text, "待核实" to "预览结果不会自动入库")))
    }
    override suspend fun runAssistantStream(kind: String, customerName: String?): AssistantWorkspace = AssistantWorkspace(
        AssistantAvailability(true, "Preview Provider", "preview-model"), streamedTitle = kind,
        streamedContent = customerName?.let { "${it}的预览分析结果。" } ?: "今日简报预览。",
    )
    override suspend fun agentAuthorizations(): List<AgentAuthorization> = lock.withLock { agentRows.toList() }
    override suspend fun revokeAgentAuthorization(id: String) { lock.withLock { if (!agentRows.removeAll { it.id == id }) throw RepositoryException("AUTHORIZATION_NOT_FOUND", "授权不存在") } }
    override suspend fun mobileDevices(): List<MobileDeviceItem> = lock.withLock { deviceRows.toList() }
    override suspend fun revokeMobileDevice(id: String) { lock.withLock { val item = deviceRows.firstOrNull { it.id == id } ?: throw RepositoryException("DEVICE_NOT_FOUND", "设备不存在"); if (item.current) throw RepositoryException("CURRENT_DEVICE", "请通过安全退出撤销当前设备"); deviceRows.remove(item) } }
    override suspend fun registerPushToken(registrationId: String, vendor: String?) { if (registrationId.length < 16) throw RepositoryException("PUSH_TOKEN_INVALID", "推送标识无效") }
    override suspend fun adminOverview(): AdminOverview = AdminOverview(
        pendingDealRequests = dealRows.count { it.status == DealRequestStatus.PENDING }.toLong(),
        pendingClassificationReviews = 2,
        dueOwnershipDeadlines = 4,
        openPoolEntries = 3,
        pendingSecondLineRecruitments = 2,
        pendingPerformanceClaims = 1,
        overdueConsultationFeedback = 1,
    )
    override suspend fun adminMembers(): List<AdminMember> = listOf(
        AdminMember("preview-member", "preview-user", "admin", "移动端预览账号", "ACTIVE", setOf("ORG_ADMIN"), setOf("院校甲团队"), setOf("team-jsu"), 2),
        AdminMember("preview-transfer", "preview-user-2", "liuyueyang", "刘悦扬", "ACTIVE", setOf("MEMBER"), setOf("院校甲团队"), setOf("team-jsu"), 1),
    )
    override suspend fun createAdminMember(username: String, displayName: String, initialPassword: String, role: String, teamId: String?): AdminMember =
        AdminMember(UUID.randomUUID().toString(), UUID.randomUUID().toString(), username.trim(), displayName.trim(), "ACTIVE", setOf(role), emptySet(), teamId?.let(::setOf) ?: emptySet(), 0)
    override suspend fun deleteAdminMember(membershipId: String, transferCustomerToMembershipId: String?) = Unit
    override suspend fun adminTeams(): List<AdminTeam> = listOf(
        AdminTeam("team-jsu", "院校甲团队", true, "preview-member", "移动端预览账号", 2),
        AdminTeam("team-just", "院校乙团队", true, memberCount = 1),
    )
    override suspend fun createAdminTeam(name: String): AdminTeam = AdminTeam(UUID.randomUUID().toString(), name.trim(), true)
    override suspend fun assignTeamLeader(teamId: String, membershipId: String): AdminTeam =
        adminTeams().firstOrNull { it.id == teamId }?.copy(leaderMembershipId = membershipId, leaderDisplayName = adminMembers().firstOrNull { it.membershipId == membershipId }?.displayName)
            ?: throw RepositoryException("TEAM_NOT_FOUND", "团队不存在")
    override suspend fun deleteAdminTeam(teamId: String) = Unit
    override suspend fun updateAdminMember(member: AdminMember, displayName: String, role: String, status: String, teamId: String?): AdminMember =
        member.copy(displayName = displayName.trim(), roles = setOf(role), status = status, teamIds = teamId?.let(::setOf) ?: emptySet())
    override suspend fun adminRegistrationCodes(): List<AdminRegistrationCode> = listOf(
        AdminRegistrationCode("code-preview", "A1B2", 10, 2, null, "ACTIVE", now),
    )
    override suspend fun createAdminRegistrationCode(maxUses: Int, expiresAt: String?): AdminRegistrationCode =
        AdminRegistrationCode(UUID.randomUUID().toString(), "NEW1", maxUses, 0, expiresAt?.let(Instant::parse), "ACTIVE", Instant.now(), "YG-PREVIEW-CODE")
    override suspend fun disableAdminRegistrationCode(codeId: String) = Unit
    override suspend fun adminNativeModule(key: String): AdminNativeModule = AdminNativeModule(
        key = key,
        title = key,
        summary = "预览数据",
        metrics = listOf("待处理" to "2"),
        items = listOf(AdminNativeItem("preview-$key", "预览管理事项", "来自本地预览仓库，不会写入服务器。", "PENDING", 0, listOf("确认", "驳回"))),
    )
    override suspend fun performAdminNativeAction(key: String, itemId: String, action: String, version: Long): AdminNativeModule = adminNativeModule(key)
    override suspend fun executeAdminNativeCommand(command: AdminNativeCommand): AdminNativeModule = adminNativeModule(command.moduleKey)
    override suspend fun downloadAdminArtifact(key: String, itemId: String): DownloadArtifact =
        DownloadArtifact("yingge-$key.csv", "text/csv", "mock-only\n".toByteArray())
    override suspend fun adminDealRequests(): List<DealRequest> = dealRows.filter { it.status == DealRequestStatus.PENDING }
    override suspend fun reviewDealRequest(requestId: String, version: Long, approve: Boolean): DealRequest = lock.withLock {
        val index = dealRows.indexOfFirst { it.id == requestId }
        val row = dealRows.getOrNull(index) ?: throw RepositoryException("DEAL_REQUEST_NOT_FOUND", "成单申请不存在")
        row.copy(status = if (approve) DealRequestStatus.APPROVED else DealRequestStatus.REJECTED, reviewedAt = Instant.now(), version = row.version + 1)
            .also { dealRows[index] = it }
    }
    override suspend fun leadershipDashboard(teamId: String?, membershipId: String?): LeadershipDashboard = LeadershipDashboard(
        generatedAt = Instant.now(),
        headline = LeadershipHeadline(customerRows.size.toLong(), 2, 1, 1, 1, 1),
        teams = adminTeams().map { LeadershipFilterOption(it.id, it.name) },
        members = adminMembers().map { LeadershipFilterOption(it.membershipId, it.displayName) },
        rankings = adminMembers().mapIndexed { index, it -> LeadershipRanking(it.membershipId, it.displayName, it.teams.joinToString(), 2L - index, 1, 1, 75.0) },
        sources = listOf(LeadershipSource("新生群", 1, 33.3), LeadershipSource("小红书", 1, 33.3), LeadershipSource("抖音", 1, 33.3)),
        quality = listOf(LeadershipQuality("A", "A 类", 1, 33.3), LeadershipQuality("B", "B 类", 1, 33.3), LeadershipQuality("C", "C 类", 1, 33.3)),
        taskCompletionRate = 75.0,
        staleAClassCustomers = 1,
        staleAClassOwners = listOf("移动端预览账号" to 1),
        featuredGrade = "2026",
        institutionACustomers = 2,
        institutionBCustomers = 1,
        tickerItems = listOf(LeadershipTicker("stale", "HIGH", "A 类客户 1 位超过 72 小时未跟进")),
    )
}
