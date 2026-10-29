package com.eagleslab.yingge.data

import android.content.Context
import com.eagleslab.yingge.BuildConfig
import com.eagleslab.yingge.data.contract.*
import com.eagleslab.yingge.model.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.io.IOException

/**
 * Production employee repository. Every operation is mapped to a named Yingge API;
 * there is no method/path/body proxy and organization identity is never accepted.
 */
class LiveYinggeRepository(
    context: Context,
    private val tokenVault: MobileTokenVault = MobileTokenVault.get(context),
) : YinggeRepository {
    private val appContext = context.applicationContext
    @Volatile private var gateway: MobileContractGateway? = null
    @Volatile private var api: EmployeeApi? = null
    @Volatile private var serverOrigin: String? = null
    private val consultationCache = mutableMapOf<String, ConsultationDto>()
    private val contentCache = mutableMapOf<String, ContentTaskItem>()
    @Volatile private var currentMobileRelease: MobileAppReleaseDto? = null
    @Volatile private var currentMembershipId: String? = null

    override suspend fun registrationOptions(serverUrl: String, registrationCode: String): RegistrationOptions {
        val contract = switchConnection(serverUrl)
        return contract.registrationOptions(registrationCode).let { result ->
            RegistrationOptions(result.teams.map { RegistrationTeam(it.id, it.name) })
        }
    }

    override suspend fun registerAndLogin(serverUrl: String, username: String, realName: String, password: String, registrationCode: String, teamId: String): SessionUser =
        switchConnection(serverUrl).registerAndLogin(username, realName, password, registrationCode, teamId)

    override suspend fun login(serverUrl: String, username: String, password: String): SessionUser =
        switchConnection(serverUrl).login(username, password).also { currentMembershipId = it.membershipId }

    override suspend fun restoreSession(serverUrl: String): SessionUser = switchConnection(serverUrl).bootstrap().actor.toModel().also { currentMembershipId = it.membershipId }

    override suspend fun bootstrap(): MobileAppBootstrap {
        val row = contract().bootstrap()
        currentMembershipId = row.actor.membershipId
        return MobileAppBootstrap(
            user = row.actor.toModel(),
            serverTime = Instant.parse(row.serverTime),
            organizationTimeZone = row.organizationTimeZone,
            availableModes = row.availableModes.mapNotNull { runCatching { AppMode.valueOf(it) }.getOrNull() }.toSet()
                .ifEmpty { setOf(AppMode.EMPLOYEE) },
            capabilities = row.capabilities,
            urgentReminders = row.unread.urgentReminders,
            unreadNotifications = row.unread.notifications,
        )
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) = contract().changePassword(currentPassword, newPassword)
    override suspend fun logout() {
        runCatching { contract().unregisterPush() }
        contract().logout()
    }

    override suspend fun dashboard(): DashboardSnapshot {
        val dashboard = authorizedRead { employee().dashboard() }
        val reminderCount = runCatching { authorizedRead { employee().reminders() }.count { it.status != "COMPLETED" } }.getOrDefault(0)
        val performance = runCatching { authorizedRead { employee().performanceToday() } }.getOrNull()
        return DashboardSnapshot(
            activeCustomers = dashboard.stats.totalActive,
            todayNewCustomers = dashboard.recentUpdated.count { it.createdAt.toInstantOrNull()?.atZone(BUSINESS_ZONE)?.toLocalDate() == LocalDate.now(BUSINESS_ZONE) },
            pendingReminders = reminderCount,
            staleAClassCustomers = dashboard.staleHighIntention.size,
            todayScore = performance?.score?.finalPoints ?: 0.0,
            baselineScore = performance?.score?.baseline ?: 0.0,
            focusCustomers = (dashboard.dueToday + dashboard.overdue + dashboard.staleHighIntention).distinctBy { it.id }.take(8).map(CustomerDto::toModel),
        )
    }

    override suspend fun dashboardGreeting(): DashboardGreeting = authorizedRead {
        employee().mobileGreeting()
    }.let { DashboardGreeting(it.salutation, it.subtitle, it.generatedByAi, Instant.parse(it.validUntil)) }

    override suspend fun customers(query: String): List<Customer> =
        authorizedRead { employee().customers(query.ifBlank { null }, size = 100) }.items.map(CustomerDto::toModel)

    override suspend fun customerPage(filter: CustomerFilter): CustomerPage {
        val page = authorizedRead {
            employee().customers(
                search = filter.search.ifBlank { null }, status = filter.status.ifBlank { null },
                intention = filter.intention.ifBlank { null }, grade = filter.grade.ifBlank { null },
                school = filter.school.ifBlank { null }, ownerMembershipId = filter.ownerMembershipId.ifBlank { null },
                includeArchived = filter.includeArchived, page = filter.page, size = filter.size, sort = filter.sort,
            )
        }
        val totalPages = if (page.size <= 0 || page.totalElements == 0L) 0 else ((page.totalElements + page.size - 1) / page.size).toInt()
        return CustomerPage(page.items.map(CustomerDto::toModel), page.page, page.size, page.totalElements, totalPages)
    }

    override suspend fun customerOwnerOptions(): List<CustomerTransferCandidate> =
        authorizedRead { employee().consultationCandidates() }.map { CustomerTransferCandidate(it.membershipId, it.displayName, it.teams.joinToString()) }

    override suspend fun customer(id: String): Customer {
        val row = authorizedRead { employee().customer(id) }
        val collaborators = runCatching { authorizedRead { employee().collaboration(id) }.collaborators.map { it.displayName } }.getOrDefault(emptyList())
        return row.toModel().copy(collaboratorNames = collaborators)
    }

    override suspend fun saveCustomer(draft: CustomerDraft): Customer {
        if (draft.name.isBlank()) throw RepositoryException("VALIDATION_FAILED", "姓名为必填项")
        if (!draft.hasContact) throw RepositoryException("VALIDATION_FAILED", "手机号、微信号、QQ 号至少填写一个")
        val body = if (draft.id == null) CustomerUpsertDto.from(draft) else {
            val previous = authorizedRead { employee().customer(draft.id) }
            CustomerUpsertDto.from(draft, previous)
        }
        return authorized {
            if (draft.id == null) employee().createCustomer(body) else employee().updateCustomer(draft.id, body)
        }.toModel()
    }

    override suspend fun customerCollaboration(customerId: String): CustomerCollaboration =
        authorizedRead { employee().collaboration(customerId) }.toModel()

    override suspend fun replaceCustomerCollaborators(customerId: String, membershipIds: Set<String>): CustomerCollaboration =
        authorized { employee().replaceCollaborators(customerId, UpdateCollaboratorsDto(membershipIds)) }.toModel()

    override suspend fun customerTransferCandidates(customerId: String): List<CustomerTransferCandidate> {
        val collaboration = authorizedRead { employee().collaboration(customerId) }
        if (!collaboration.canTransferOwnership) throw RepositoryException("CUSTOMER_TRANSFER_FORBIDDEN", "当前账号无权转接该客户")
        return collaboration.transferCandidates.map { CustomerTransferCandidate(it.membershipId, it.displayName, it.teamNames.orEmpty()) }
    }

    override suspend fun transferCustomer(customerId: String, targetMembershipId: String, reason: String): Customer {
        val current = authorizedRead { employee().customer(customerId) }
        return authorized { employee().transferCustomer(customerId, TransferOwnershipDto(targetMembershipId, current.version, reason.take(500).takeIf { it.isNotBlank() })) }.toModel()
    }

    override suspend fun archiveCustomer(customerId: String) {
        authorized { employee().archiveCustomer(customerId) }
    }

    override suspend fun customerClassificationState(customerId: String): CustomerClassificationState {
        val history = authorizedRead { employee().classificationHistory(customerId) }
        val deadline = runCatching { authorizedRead { employee().deadlineShadow(customerId) } }.getOrNull()
        return CustomerClassificationState(history.map(MarketClassificationDto::toCustomerClassification), deadline?.computedDeadline.toInstantOrNull(), deadline?.mode.orEmpty(), deadline?.noReplyExtensionUsed ?: false)
    }

    override suspend fun saveCustomerClassification(customerId: String, draft: CustomerClassificationDraft): CustomerClassificationState {
        if (draft.recognitionReason.isBlank() || draft.potentialDemand.isBlank() || draft.evidenceInteractionIds.isEmpty()) throw RepositoryException("VALIDATION_FAILED", "请填写认定说明、潜在需求并选择事实依据")
        if (draft.level == CustomerQuality.A && draft.signals.none(String::isNotBlank)) throw RepositoryException("A_CLASS_SIGNAL_REQUIRED", "A 类认定至少填写一项近期推进信号")
        authorized { employee().classifyCustomer(customerId, MarketClassificationRequestDto(
            draft.level.name, draft.recognitionReason.trim(), draft.potentialDemand.trim(), draft.signals.map(String::trim).filter(String::isNotBlank).toSet(), draft.evidenceInteractionIds, draft.correctionReason?.trim()?.takeIf(String::isNotBlank),
        )) }
        return customerClassificationState(customerId)
    }

    override suspend fun downloadCustomerTimeline(customerId: String): DownloadArtifact {
        val body = authorizedRead { employee().exportCustomerTimeline(customerId) }
        return body.use { DownloadArtifact("客户时间线-$customerId.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", it.bytes()) }
    }

    override suspend fun previewCustomerImport(file: EvidenceUpload): CustomerImportState {
        if (file.bytes.isEmpty()) throw RepositoryException("IMPORT_EMPTY", "导入文件为空")
        if (!file.fileName.endsWith(".csv", true) && !file.fileName.endsWith(".xlsx", true)) throw RepositoryException("IMPORT_FORMAT_UNSUPPORTED", "仅支持 CSV 或 XLSX")
        val part = MultipartBody.Part.createFormData("file", file.fileName.take(180), file.bytes.toRequestBody(file.contentType.toMediaType()))
        return authorized { employee().previewCustomerImport(part) }.toModel()
    }

    override suspend fun commitCustomerImport(jobId: String): CustomerImportState = authorized { employee().commitCustomerImport(jobId) }.toModel()

    override suspend fun downloadCustomers(format: String): DownloadArtifact {
        val normalized = if (format.lowercase() == "csv") "csv" else "xlsx"
        val body = authorizedRead { employee().exportCustomers(normalized) }
        val type = if (normalized == "csv") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        return body.use { DownloadArtifact("客户档案-${LocalDate.now(BUSINESS_ZONE)}.$normalized", type, it.bytes()) }
    }

    override suspend fun customerInsight(customerId: String): CustomerInsight {
        val row = authorizedRead { employee().mobileCustomerInsight(customerId) }
        return CustomerInsight(customerId, row.content, Instant.parse(row.generatedAt),
            row.timelineUpdatedAt?.let(Instant::parse), row.traceId)
    }

    private fun parseCustomerInsightStream(customerId: String, body: ResponseBody): CustomerInsight {
        val source = body.source()
        return parseCustomerInsightLines(customerId) { source.readUtf8Line() }
    }

    override suspend fun interactions(customerId: String): List<Interaction> =
        authorizedRead { employee().interactions(customerId) }.map(InteractionDto::toModel)

    override suspend fun addInteraction(customerId: String, draft: InteractionDraft): Interaction {
        if (draft.summary.isBlank()) throw RepositoryException("VALIDATION_FAILED", "沟通摘要为必填项")
        return authorized {
            employee().addInteraction(customerId, InteractionCreateDto(
                contactedAt = draft.occurredAt?.toString(), channel = draft.channel, summary = draft.summary,
                details = draft.details, interactionType = draft.type.name,
            ))
        }.toModel()
    }

    override suspend fun reminders(): List<Reminder> {
        val rows = authorizedRead { employee().reminders() }
        val names = runCatching { customers().associate { it.id to it.name } }.getOrDefault(emptyMap())
        return rows.map { it.toModel(names[it.customerId].orEmpty()) }
    }

    override suspend fun completeReminder(id: String) {
        authorized { employee().updateReminder(id, ReminderUpdateDto(status = "COMPLETED")) }
    }

    override suspend fun dailySummary(): DailySummary = authorizedRead { employee().dailyToday() }.toModel()

    override suspend fun saveDailySummary(draft: DailySummaryDraft): DailySummary {
        if (draft.issues.isBlank() || draft.tomorrowPlan.isBlank()) throw RepositoryException("VALIDATION_FAILED", "今日问题和明日计划必须填写")
        val existing = dailySummary()
        val deep = if (existing.deepConversationReadOnly) existing.deepConversationCount else draft.deepConversationCount.toIntOrNull() ?: 0
        return authorized { employee().saveDaily(existing.date.toString(), DailyUpsertDto(deep, draft.issues, draft.tomorrowPlan)) }.toModel()
    }

    override suspend fun requestDeal(customerId: String, idempotencyKey: String): DealRequest =
        contract().requestDeal(customerId, idempotencyKey)

    override suspend fun notifications(): List<NotificationItem> = authorizedRead { employee().notifications().items }.map(NotificationDto::toModel)
    override suspend fun markNotificationRead(id: String) { authorized { employee().markNotificationRead(id) } }
    override suspend fun markAllNotificationsRead(): Int = authorized { employee().markAllNotificationsRead().updatedCount }

    override suspend fun businessModule(key: String): BusinessModule = when (key) {
        "pool" -> authorizedRead { employee().poolEntries() }.let { rows -> BusinessModule(key, "客户公共池", "认领前仅展示服务端返回的脱敏摘要。", ModuleAvailability.AVAILABLE, actionItems = rows.map { BusinessModuleActionItem(it.id, it.customerName ?: "脱敏客户", "${it.school.orEmpty()} ${it.grade.orEmpty()} · ${it.demandSummary.orEmpty()}", "临时认领") }) }
        "second_line" -> authorizedRead { employee().secondLineRecruitments() }.let { rows -> BusinessModule(key, "A 类二线协作", "领取后按服务端权限开放客户档案。", ModuleAvailability.AVAILABLE, actionItems = rows.map { BusinessModuleActionItem(it.id, it.customerName ?: "脱敏客户", "${it.claimedCount}/${it.requiredCount} · ${it.demandSummary.orEmpty()}", "领取二线") }) }
        "team_data" -> authorizedRead { employee().teamDataOverview() }.let { row -> BusinessModule(
            key, "团队数据", "按服务端权限范围统计，不接收客户端伪造组织参数。", ModuleAvailability.READ_ONLY,
            listOf(
                "范围成员 ${row.stats.memberCount} 人 · 活跃客户 ${row.stats.activeCustomerCount}",
                "今日新增 ${row.stats.todayNewCustomerCount} · 26 级有效线索 ${row.stats.todayGrade26LeadCount}",
                "今日日报 ${row.stats.todayReportCount} · 待审核线索 ${row.stats.pendingLeadReviewCount}",
                "小红书有效内容 ${row.stats.xiaohongshuValidCount} · 抖音有效内容 ${row.stats.douyinValidCount}",
            ) + row.members.take(20).map { "${it.displayName} · ${it.teamNames}" },
        ) }
        "market_insights" -> authorizedRead { employee().teamDataOverview() }.let { row -> BusinessModule(
            key, "市场洞察", "展示团队成员、内容进度和待审核线索的原生摘要。", ModuleAvailability.READ_ONLY,
            listOf("待审核有效线索 ${row.leadReviewQueue.size}") + row.contentProgress.take(20).map { "${it.displayName} · 小红书 ${it.xiaohongshuValid} · 抖音 ${it.douyinValid}" },
        ) }
        "releases" -> run {
            // Product release history must remain usable when the optional APK
            // update channel has no body (204) or is temporarily unavailable.
            // Keeping the calls independent also prevents a transport/parser
            // failure in the APK manifest check from blanking the whole page.
            val mobileReleaseResult = runCatching { contract().currentRelease() }
            val row = mobileReleaseResult.getOrNull()
            currentMobileRelease = row
            val history = authorizedRead { employee().releases() }.items
            val pending = runCatching { authorizedRead { employee().unacknowledgedRelease() } }.getOrNull()
            val actions = buildList {
                if (row != null && row.versionCode > BuildConfig.VERSION_CODE) add(BusinessModuleActionItem("mobile-apk:${row.id}", "版本 ${row.versionName}", row.notes, "下载并校验"))
                if (pending != null) add(BusinessModuleActionItem("product-ack:${pending.id}", pending.title, pending.summary, "我已了解"))
            }
            BusinessModule(
                key,
                "更新日志",
                if (mobileReleaseResult.isFailure) {
                    "产品更新历史已加载；Android 安装包检查暂不可用，可稍后重试。"
                } else {
                    "当前 Android 发布通道；下载后会校验大小、SHA-256 和应用签名。"
                },
                if (actions.isNotEmpty()) ModuleAvailability.AVAILABLE else ModuleAvailability.READ_ONLY,
                listOfNotNull(row?.let { "Android ${it.versionName} · ${it.notes}" }) + history.map { "${it.version} · ${it.title} · ${it.summary}" },
                actionItems = actions,
            )
        }
        "assistant" -> authorizedRead { employee().assistantAvailability() }.let { row -> BusinessModule(key, "智能助手", "移动端实时读取组织 AI 可用性；客户洞察在客户详情中按权限请求。", ModuleAvailability.READ_ONLY, listOf("服务：${if (row.available) "可用" else "暂停"}", "Provider：${row.provider.ifBlank { "未配置" }}", "模型：${row.model.ifBlank { "未配置" }}")) }
        else -> throw RepositoryException("MODULE_USE_NATIVE_ROUTE", "请使用该功能的原生页面")
    }

    override suspend fun actOnBusinessModuleItem(key: String, id: String): BusinessModule {
        when (key) {
            "pool" -> authorized { employee().claimPool(id, PoolClaimDto(UUID.randomUUID().toString())) }
            "second_line" -> authorized { employee().claimSecondLine(id) }
            "releases" -> {
                if (id.startsWith("product-ack:")) {
                    authorized { employee().acknowledgeRelease(id.removePrefix("product-ack:")) }
                    return businessModule(key)
                }
                val releaseId = id.removePrefix("mobile-apk:")
                val release = currentMobileRelease?.takeIf { it.id == releaseId } ?: contract().currentRelease()?.also { currentMobileRelease = it }
                    ?: throw RepositoryException("UPDATE_NOT_FOUND", "暂无可用更新")
                // Retrofit returns a streaming body here. Consuming it on the ViewModel's
                // main dispatcher can trigger Android's network-on-main-thread guard after
                // the server has already sent the complete APK. Keep download, hashing and
                // package parsing on the IO dispatcher; only launch the system installer
                // after returning to the caller dispatcher.
                val verified = withContext(Dispatchers.IO) {
                    contract().releaseApk(release.id).use { body ->
                        AppUpdateVerifier(appContext).verify(release, body)
                    }
                }
                val result = AppUpdateInstaller(appContext).launch(verified.file)
                val next = when (result) {
                    AppUpdateInstaller.LaunchResult.INSTALLER_OPENED -> "已打开 Android 系统安装确认页。"
                    AppUpdateInstaller.LaunchResult.PERMISSION_REQUIRED -> "请允许英格云安装企业内部更新，返回后再次点击下载。"
                }
                return BusinessModule(key, "更新日志", "更新包已通过大小、SHA-256 与应用签名校验。", ModuleAvailability.READ_ONLY, listOf("${verified.versionName} · ${verified.sha256.take(16)}…", next))
            }
            else -> throw RepositoryException("MODULE_ACTION_NOT_ALLOWED", "该页面没有可执行的移动端写操作")
        }
        return businessModule(key)
    }

    override suspend fun tasks(): List<WorkTask> = authorizedRead { employee().tasks() }.map(TaskDto::toModel)
    override suspend fun claimTask(taskId: String): WorkTask {
        authorized { employee().claimTask(taskId) }
        return tasks().firstOrNull { it.id == taskId } ?: throw RepositoryException("TASK_NOT_FOUND", "任务不存在")
    }
    override suspend fun submitTask(taskId: String, result: String): WorkTask {
        if (result.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请填写任务成果")
        authorized { employee().updateTask(taskId, TaskUpdateDto("SUBMITTED", result, null)) }
        return tasks().firstOrNull { it.id == taskId } ?: throw RepositoryException("TASK_NOT_FOUND", "任务不存在")
    }

    override suspend fun performance(): PerformanceSnapshot {
        val to = LocalDate.now(BUSINESS_ZONE)
        val from = to.minusDays(29)
        val today = authorizedRead { employee().performanceToday() }
        val history = authorizedRead { employee().performanceHistory(from.toString(), to.toString()) }
        val leaderboard = authorizedRead { employee().performanceLeaderboard(from.toString(), to.toString()) }
        return today.toModel(history, leaderboard, today.activities)
    }
    override suspend fun submitPerformanceEvidence(interactionId: String, requestedType: String, evidence: EvidenceUpload): PerformanceSnapshot {
        if (requestedType !in setOf("NORMAL", "DEEP")) throw RepositoryException("PERFORMANCE_TYPE_INVALID", "请选择普通交流或深度交流")
        val normalizedId = runCatching { UUID.fromString(interactionId).toString() }.getOrElse {
            throw RepositoryException("INTERACTION_ID_REQUIRED", "请选择一条可申报的沟通记录")
        }
        if (evidence.bytes.isEmpty() || evidence.bytes.size > 5 * 1024 * 1024) throw RepositoryException("PERFORMANCE_EVIDENCE_INVALID", "证据文件必须在 5 MB 以内")
        if (evidence.contentType !in setOf("image/jpeg", "image/png", "image/webp")) throw RepositoryException("PERFORMANCE_EVIDENCE_INVALID", "证据只支持 JPEG、PNG 或 WebP")
        val claim = authorized { employee().createPerformanceClaim(PerformanceClaimCreateDto(normalizedId, requestedType)) }
        val body = evidence.bytes.toRequestBody(evidence.contentType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", evidence.fileName.take(180), body)
        authorized { employee().uploadPerformanceEvidence(claim.id, part) }
        authorized { employee().submitPerformanceClaim(claim.id) }
        return performance()
    }

    override suspend fun proposePerformanceActivity(eventDate: String, title: String, goal: String, expectedParticipants: Int, description: String): PerformanceSnapshot {
        val normalizedDate = runCatching { LocalDate.parse(eventDate).toString() }.getOrElse { throw RepositoryException("PERFORMANCE_DATE_INVALID", "活动日期必须为 YYYY-MM-DD") }
        if (title.isBlank() || goal.isBlank() || description.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请完整填写活动名称、目标和说明")
        if (expectedParticipants < 0) throw RepositoryException("VALIDATION_FAILED", "预计人数不能为负数")
        authorized { employee().createPerformanceActivity(PerformanceActivityCreateDto(normalizedDate, title.trim(), goal.trim(), expectedParticipants, description.trim())) }
        return performance()
    }

    override suspend fun completePerformanceActivity(activityId: String, actualParticipants: Int, expectedVersion: Long, evidence: EvidenceUpload): PerformanceSnapshot {
        if (actualParticipants < 0) throw RepositoryException("VALIDATION_FAILED", "实际参与人数不能为负数")
        if (evidence.bytes.isEmpty() || evidence.bytes.size > 5 * 1024 * 1024 || evidence.contentType !in setOf("image/jpeg", "image/png", "image/webp")) {
            throw RepositoryException("PERFORMANCE_EVIDENCE_INVALID", "活动证据仅支持 5 MB 以内的 JPEG、PNG 或 WebP")
        }
        val body = evidence.bytes.toRequestBody(evidence.contentType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", evidence.fileName.take(180), body)
        authorized { employee().uploadPerformanceActivityEvidence(activityId, part) }
        authorized { employee().completePerformanceActivity(activityId, PerformanceActivityCompleteDto(actualParticipants, expectedVersion)) }
        return performance()
    }

    override suspend fun consultations(): List<ConsultationItem> {
        val rows = authorizedRead { employee().consultations() }
        synchronized(consultationCache) { rows.forEach { consultationCache[it.id] = it } }
        return rows.map { it.toModel(currentMembershipId) }
    }

    override suspend fun consultationOptions(customerName: String): ConsultationOptions {
        if (customerName.isBlank()) throw RepositoryException("CUSTOMER_SELECTION_REQUIRED", "请输入客户姓名")
        val matches = customers(customerName).filter { it.name == customerName.trim() }
        if (matches.size != 1) throw RepositoryException("CUSTOMER_SELECTION_REQUIRED", "请填写唯一匹配的客户姓名")
        val customer = matches.single()
        val sources = interactions(customer.id).map { ConsultationSource(it.id, it.summary) }
        if (sources.isEmpty()) throw RepositoryException("SOURCE_INTERACTION_REQUIRED", "该客户暂无可引用沟通记录")
        val candidates = authorizedRead { employee().consultationCandidates() }.map { ConsultationCandidate(it.membershipId, it.displayName) }
        if (candidates.isEmpty()) throw RepositoryException("CONSULTANT_REQUIRED", "当前没有可预约的咨询顾问")
        val failureReasons = authorizedRead { employee().consultationFailureReasons() }.filter { it.active }.sortedBy { it.sortOrder }.map { ConsultationFailureReason(it.id, it.code, it.label) }
        return ConsultationOptions(customer.id, customer.name, sources, candidates, failureReasons)
    }

    override suspend fun submitConsultation(draft: ConsultationDraft): ConsultationItem {
        val requiredFacts = listOf(
            draft.customerId, draft.consultantMembershipId, draft.sourceInteractionId,
            draft.consultationReason, draft.customerQuotes, draft.goalsMotivation, draft.actionsTaken,
            draft.mainConcerns, draft.productFit, draft.suggestedFocus, draft.explicitTaboos,
            draft.expectedResult, draft.scheduledStart, draft.scheduledEnd,
        )
        if (requiredFacts.any { it.isBlank() }) throw RepositoryException("VALIDATION_FAILED", "请逐项填写事实、选择证据与顾问，不得由客户端自动补全")
        val options = consultationOptions(authorizedRead { employee().customer(draft.customerId) }.fullName)
        if (options.sources.none { it.interactionId == draft.sourceInteractionId }) throw RepositoryException("SOURCE_INTERACTION_REQUIRED", "请选择该客户的一条沟通记录")
        if (options.candidates.none { it.membershipId == draft.consultantMembershipId }) throw RepositoryException("CONSULTANT_REQUIRED", "请选择可用咨询顾问")
        val start = runCatching { Instant.parse(draft.scheduledStart) }.getOrElse { throw RepositoryException("SCHEDULE_INVALID", "预约开始时间格式无效") }
        val end = runCatching { Instant.parse(draft.scheduledEnd) }.getOrElse { throw RepositoryException("SCHEDULE_INVALID", "预约结束时间格式无效") }
        if (!end.isAfter(start)) throw RepositoryException("SCHEDULE_INVALID", "预约结束时间必须晚于开始时间")
        val row = authorized { employee().createConsultation(ConsultationCreateDto(
            customerId = draft.customerId, consultantMembershipId = draft.consultantMembershipId,
            sourceInteractionIds = listOf(draft.sourceInteractionId), consultationReason = draft.consultationReason,
            customerQuotes = draft.customerQuotes, goalsMotivation = draft.goalsMotivation,
            actionsTaken = draft.actionsTaken, mainConcerns = draft.mainConcerns,
            productFit = draft.productFit, suggestedFocus = draft.suggestedFocus,
            explicitTaboos = draft.explicitTaboos, expectedResult = draft.expectedResult,
            scheduledStart = start.toString(), scheduledEnd = end.toString(),
        )) }
        synchronized(consultationCache) { consultationCache[row.id] = row }
        return row.toModel(currentMembershipId)
    }

    override suspend fun acceptConsultation(consultationId: String, expectedVersion: Long): ConsultationItem {
        val row = authorized { employee().acceptConsultation(consultationId, ConsultationVersionDto(expectedVersion)) }
        synchronized(consultationCache) { consultationCache[row.id] = row }
        return row.toModel(currentMembershipId)
    }

    override suspend fun rejectConsultation(consultationId: String, expectedVersion: Long, reason: String): ConsultationItem {
        if (reason.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请填写拒绝原因")
        val row = authorized { employee().rejectConsultation(consultationId, ConsultationRejectDto(expectedVersion, reason.trim())) }
        synchronized(consultationCache) { consultationCache[row.id] = row }
        return row.toModel(currentMembershipId)
    }

    override suspend fun endConsultation(consultationId: String, expectedVersion: Long): ConsultationItem {
        val row = authorized { employee().endConsultation(consultationId, ConsultationEndDto(expectedVersion, Instant.now().toString())) }
        synchronized(consultationCache) { consultationCache[row.id] = row }
        return row.toModel(currentMembershipId)
    }

    override suspend fun completeConsultation(consultationId: String, result: ConsultationResultDraft): ConsultationItem {
        if (result.resultSummary.isBlank() || result.nextStep.isBlank() || result.nextOwnerMembershipId.isBlank()) throw RepositoryException("VALIDATION_FAILED", "请完整回填结果、下一步和下一负责人")
        if (!result.continueFollowUp && result.failureReasonId.isNullOrBlank()) throw RepositoryException("CONSULTATION_FAILURE_REASON_REQUIRED", "暂不推进时必须选择失败原因")
        val existing = synchronized(consultationCache) { consultationCache[consultationId] } ?: authorizedRead { employee().consultation(consultationId) }
        val row = authorized { employee().completeConsultation(consultationId, ConsultationCompleteDto(
            expectedVersion = existing.version, resultSummary = result.resultSummary.trim(), continueFollowUp = result.continueFollowUp,
            nextStep = result.nextStep.trim(), nextOwnerMembershipId = result.nextOwnerMembershipId,
            nextFollowUpAt = result.nextFollowUpAt?.trim()?.takeIf(String::isNotBlank),
            failureReasonId = result.failureReasonId, failureReasonOther = result.failureReasonOther?.trim()?.takeIf(String::isNotBlank),
        )) }
        synchronized(consultationCache) { consultationCache[row.id] = row }
        return row.toModel(currentMembershipId)
    }

    override suspend fun contentTasks(): List<ContentTaskItem> {
        val overview = authorizedRead { employee().contentOverview() }
        val rows = overview.myPosts.map(ContentPostDto::toModel) + overview.directions.map { ContentTaskItem(it.id, it.title, "小红书 / 抖音", false) }
        synchronized(contentCache) { rows.forEach { contentCache[it.id] = it } }
        return rows
    }

    override suspend fun submitContentTask(taskId: String, platform: String, content: String): ContentTaskItem {
        if (platform !in setOf("XIAOHONGSHU", "DOUYIN")) throw RepositoryException("CONTENT_PLATFORM_INVALID", "请选择小红书或抖音")
        if (!content.startsWith("http://") && !content.startsWith("https://")) throw RepositoryException("CONTENT_URL_REQUIRED", "请输入完整作品链接")
        val direction = synchronized(contentCache) { contentCache[taskId] } ?: throw RepositoryException("CONTENT_DIRECTION_REQUIRED", "请刷新后选择内容方向")
        val row = authorized { employee().submitContent(ContentPostCreateDto(platform, LocalDate.now().toString(), taskId, direction.title, content, 0, 0, 0, true)) }.toModel()
        synchronized(contentCache) { contentCache[row.id] = row }
        return row
    }

    override suspend fun updateContentMetrics(taskId: String, metrics: String): ContentTaskItem {
        val values = Regex("\\d+").findAll(metrics).map { it.value.toLong() }.toList()
        if (values.size < 3) throw RepositoryException("METRICS_REQUIRED", "请依次填写浏览、点赞、收藏三个数字")
        return authorized { employee().updateContentMetrics(taskId, ContentMetricsDto(values[0], values[1], values[2])) }.toModel()
    }

    override suspend fun circlePosts(): List<CirclePost> = authorizedRead { employee().circle() }.items.map(ContentPostDto::toCircle)
    override suspend fun circleFeed(search: String, senderMembershipId: String?, page: Int): CircleFeed {
        val result = authorizedRead { employee().circle(page = page.coerceAtLeast(0), size = 20, search = search.trim().ifBlank { null }, membershipId = senderMembershipId) }
        val senders = authorizedRead { employee().circleSenders() }.map { CircleSender(it.membershipId, it.displayName) }
        return CircleFeed(result.items.map(ContentPostDto::toCircle), senders, result.page, result.size, result.totalElements)
    }
    override suspend fun announcements(): List<AnnouncementItem> = authorizedRead { employee().announcements() }.map(AnnouncementDto::toModel)
    override suspend fun assistantWorkspace(): AssistantWorkspace {
        val row = authorizedRead { employee().assistantAvailability() }
        return AssistantWorkspace(AssistantAvailability(row.available, row.provider, row.model))
    }

    override suspend fun parseAssistantDraft(mode: String, text: String): AssistantWorkspace {
        if (mode !in setOf("CUSTOMER", "INTERACTION")) throw RepositoryException("ASSISTANT_MODE_INVALID", "请选择客户草稿或沟通草稿")
        if (text.isBlank() || text.length > 20_000) throw RepositoryException("VALIDATION_FAILED", "请输入 1–20000 字的待解析内容")
        val availability = authorizedRead { employee().assistantAvailability() }
        if (!availability.available) throw RepositoryException("AI_NOT_CONFIGURED", "组织尚未启用智能助手")
        val result = authorized { if (mode == "CUSTOMER") employee().assistantCustomerDraft(AssistantParseDto(text.trim())) else employee().assistantInteractionDraft(AssistantParseDto(text.trim())) }
        val fields = result.entries.map { (key, value) -> key to assistantDisplayValue(value) }
        return AssistantWorkspace(AssistantAvailability(true, availability.provider, availability.model), AssistantStructuredDraft(mode, fields))
    }

    override suspend fun runAssistantStream(kind: String, customerName: String?): AssistantWorkspace {
        val availability = authorizedRead { employee().assistantAvailability() }
        if (!availability.available) throw RepositoryException("AI_NOT_CONFIGURED", "组织尚未启用智能助手")
        val title: String
        val body = when (kind) {
            "DAILY_BRIEFING" -> { title = "今日智能简报"; authorizedRead { employee().assistantDailyBriefing() } }
            "SUMMARY", "NEXT_ACTIONS", "DATA_QUALITY" -> {
                if (customerName.isNullOrBlank()) throw RepositoryException("CUSTOMER_SELECTION_REQUIRED", "请输入客户准确姓名")
                val matches = customers(customerName).filter { it.name == customerName.trim() }
                if (matches.size != 1) throw RepositoryException("CUSTOMER_SELECTION_REQUIRED", "请输入唯一匹配的客户姓名")
                val customer = matches.single()
                title = when (kind) { "SUMMARY" -> "${customer.name}·时间线摘要"; "NEXT_ACTIONS" -> "${customer.name}·下一步建议"; else -> "${customer.name}·数据质量检测" }
                when (kind) {
                    "SUMMARY" -> authorizedRead { employee().assistantCustomerSummary(customer.id) }
                    "NEXT_ACTIONS" -> authorizedRead { employee().assistantCustomerNextActions(customer.id) }
                    else -> authorizedRead { employee().assistantCustomerDataQuality(customer.id) }
                }
            }
            else -> throw RepositoryException("ASSISTANT_MODE_INVALID", "无法识别智能助手任务")
        }
        val content = body.readAssistantSse()
        if (content.isBlank()) throw RepositoryException("AI_STREAM_EMPTY", "模型未返回有效内容")
        return AssistantWorkspace(AssistantAvailability(true, availability.provider, availability.model), streamedTitle = title, streamedContent = content)
    }
    override suspend fun agentAuthorizations(): List<AgentAuthorization> = authorizedRead { employee().agentAuthorizations() }.filter { it.status == "ACTIVE" }.map(AgentGrantDto::toModel)
    override suspend fun revokeAgentAuthorization(id: String) { authorized { employee().revokeAgentAuthorization(id) } }
    override suspend fun mobileDevices(): List<MobileDeviceItem> = contract().devices().map { MobileDeviceItem(it.id, it.deviceName, it.current, it.lastUsedAt.toInstantOrNull()) }
    override suspend fun revokeMobileDevice(id: String) { contract().revokeDevice(id) }

    override suspend fun registerPushToken(registrationId: String, vendor: String?) {
        if (registrationId.length !in 16..2048) throw RepositoryException("PUSH_TOKEN_INVALID", "推送标识无效")
        contract().registerPush(registrationId, vendor)
        contract().updateNotificationPreferences(NotificationPreferencesDto())
    }

    override suspend fun adminOverview(): AdminOverview = authorizedRead { employee().adminOverview() }.toModel()

    override suspend fun adminMembers(): List<AdminMember> =
        authorizedRead { employee().adminMembers() }.map(AdminMemberDto::toModel)

    override suspend fun createAdminMember(
        username: String,
        displayName: String,
        initialPassword: String,
        role: String,
        teamId: String?,
    ): AdminMember {
        if (username.isBlank() || displayName.isBlank() || initialPassword.isBlank()) {
            throw RepositoryException("VALIDATION_FAILED", "账号、真实姓名和初始密码均为必填项")
        }
        return authorized {
            employee().createAdminMember(AdminMemberCreateDto(username.trim(), displayName.trim(), initialPassword, role, teamId))
        }.toModel()
    }

    override suspend fun deleteAdminMember(membershipId: String, transferCustomerToMembershipId: String?) {
        authorized { employee().deleteAdminMember(membershipId, AdminMemberDeleteDto(transferCustomerToMembershipId)) }
    }

    override suspend fun adminTeams(): List<AdminTeam> =
        authorizedRead { employee().adminTeams() }.map(AdminTeamDto::toModel)

    override suspend fun createAdminTeam(name: String): AdminTeam {
        if (name.isBlank()) throw RepositoryException("VALIDATION_FAILED", "团队名称不能为空")
        return authorized { employee().createAdminTeam(AdminTeamCreateDto(name.trim())) }.toModel()
    }

    override suspend fun assignTeamLeader(teamId: String, membershipId: String): AdminTeam =
        authorized { employee().assignTeamLeader(teamId, AdminTeamLeaderDto(membershipId)) }.toModel()

    override suspend fun deleteAdminTeam(teamId: String) {
        authorized { employee().deleteAdminTeam(teamId) }
    }

    override suspend fun updateAdminMember(
        member: AdminMember,
        displayName: String,
        role: String,
        status: String,
        teamId: String?,
    ): AdminMember {
        if (displayName.isBlank()) throw RepositoryException("VALIDATION_FAILED", "成员姓名不能为空")
        return authorized {
            employee().updateAdminMember(member.membershipId, AdminMemberUpdateDto(displayName.trim(), role, status, teamId))
        }.toModel()
    }

    override suspend fun adminRegistrationCodes(): List<AdminRegistrationCode> =
        authorizedRead { employee().adminRegistrationCodes() }.map(AdminRegistrationCodeDto::toModel)

    override suspend fun createAdminRegistrationCode(maxUses: Int, expiresAt: String?): AdminRegistrationCode {
        if (maxUses !in 1..1000) throw RepositoryException("INVALID_MAX_USES", "最大使用次数必须在 1 到 1000 之间")
        return authorized { employee().createAdminRegistrationCode(AdminRegistrationCodeCreateDto(maxUses, expiresAt?.takeIf(String::isNotBlank))) }
            .toModel()
    }

    override suspend fun disableAdminRegistrationCode(codeId: String) {
        authorized { employee().disableAdminRegistrationCode(codeId) }
    }

    override suspend fun adminNativeModule(key: String): AdminNativeModule = when (key) {
        "market" -> {
            val reviews = authorizedRead { employee().classificationReviews(false) }
            val anomalies = authorizedRead { employee().classificationAnomalies() }
            AdminNativeModule(key, "A/B/C 认定与抽查", "30% 抽查与成员 A 类率异常均来自服务端治理口径。",
                metrics = listOf("待复核" to reviews.count { it.reviewStatus == "PENDING" }.toString(), "异常成员" to anomalies.count { it.abnormal }.toString()),
                items = reviews.map {
                    AdminNativeItem(
                        it.classificationId,
                        "${it.customerName} · ${it.level} 类",
                        "申报人 ${it.submittedByDisplayName} · ${it.createdAt}",
                        it.reviewStatus,
                        actions = if (it.reviewStatus == "PENDING") listOf("确认", "更正") else emptyList(),
                        metadata = mapOf("customerId" to it.customerId, "level" to it.level),
                    )
                } +
                    anomalies.filter { it.abnormal }.map { AdminNativeItem("anomaly:${it.membershipId}", it.displayName, "A 类率 ${"%.1f".format(it.aRate * 100)}% · 团队中位数 ${"%.1f".format(it.teamMedianRate * 100)}%", "异常") }
            )
        }
        "pool" -> {
            val pools = authorizedRead { employee().poolEntries() }
            val secondLines = authorizedRead { employee().secondLineRecruitments() }
            AdminNativeModule(key, "公共池与二线", "A/B 仅展示人工释放记录；C 类释放与二线招募继续执行。",
                listOf("池内客户" to pools.size.toString(), "二线待领取" to secondLines.size.toString()),
                pools.map { AdminNativeItem("pool:${it.id}", it.customerName ?: "脱敏客户", "${it.school.orEmpty()} ${it.grade.orEmpty()} · ${it.demandSummary.orEmpty()}", "公共池") } +
                    secondLines.map { AdminNativeItem("second:${it.id}", it.customerName ?: "脱敏客户", "已领取 ${it.claimedCount}/${it.requiredCount} · ${it.demandSummary.orEmpty()}", "二线招募") })
        }
        "cross_sales" -> {
            val page = authorizedRead { employee().crossSales(crossSale = true) }
            AdminNativeModule(
                key,
                "交叉单快速查询",
                "只要客户存在至少一名有效协作者，即统一认定为交叉单。",
                listOf("交叉客户" to page.totalElements.toString()),
                page.items.map { row ->
                    AdminNativeItem(
                        "cross:${row.customerId}",
                        row.customerName,
                        "${row.customerQuality.orEmpty()} 类 · ${row.teamName ?: "未分组"}\n主负责人 ${row.ownerDisplayName ?: "未分配"} · 协作者 ${row.collaboratorNames.ifBlank { "无" }}\n业务状态 ${row.businessCycleStatus ?: "无业务周期"} · 标记 ${row.statusMarkedAt.orEmpty()}",
                        "交叉单",
                    )
                } + AdminNativeItem("export:cross-sales", "导出交叉单", "导出当前组织全部交叉客户 XLSX。", "可导出", actions = listOf("导出")),
            )
        }
        "tasks_admin" -> {
            val rows = authorizedRead { employee().taskMatrix() }
            AdminNativeModule(key, "任务派发与验收", "任务状态与人数由正式任务接口返回。",
                listOf("任务总数" to rows.size.toString(), "进行中" to rows.count { it.status !in setOf("CLOSED", "DELETED") }.toString()),
                rows.flatMap { row ->
                    listOf(AdminNativeItem(row.task.id, row.task.title, "截止 ${row.task.dueAt.orEmpty()} · ${row.assignments.size} 份派发", row.task.status, row.task.version, if (row.task.status != "CLOSED") listOf("关闭", "删除") else listOf("删除"))) +
                        row.assignments.filter { it.status == "SUBMITTED" }.map { assignment ->
                            AdminNativeItem("assignment:${row.task.id}:${assignment.id}", "${row.task.title} · ${assignment.memberDisplayName.orEmpty()}", assignment.resultSummary.orEmpty(), assignment.status, assignment.version, listOf("验收", "退回"))
                        }
                })
        }
        "content_admin" -> {
            val overview = authorizedRead { employee().contentOverview() }
            val announcements = authorizedRead { employee().announcements(includeArchived = true) }
            AdminNativeModule(key, "内容与公告", "内容审核和公告归档直接作用于正式业务记录。",
                listOf("内容待审" to overview.reviewQueue.size.toString(), "公告" to announcements.size.toString()),
                overview.reviewQueue.map { AdminNativeItem("post:${it.id}", it.topic, "${it.memberDisplayName.orEmpty()} · ${it.platform}", it.reviewStatus, it.version, listOf("通过", "驳回")) } +
                    announcements.map {
                        AdminNativeItem(
                            "announcement:${it.id}", it.title, it.content.take(180), it.status, it.version,
                            if (it.status == "PUBLISHED") listOf("编辑", if (it.pinned) "取消置顶" else "置顶", "归档") else emptyList(),
                            mapOf(
                                "content" to it.content,
                                "priority" to it.priority,
                                "pinned" to it.pinned.toString(),
                                "showOnLeadershipScreen" to it.showOnLeadershipScreen.toString(),
                            ),
                        )
                    })
        }
        "daily_admin" -> {
            val calendar = authorizedRead { employee().dailyCalendar(YearMonth.now(BUSINESS_ZONE).toString()) }
            val rows = calendar.days.filter { it.reportingDay }
            AdminNativeModule(key, "日报日历", "明确展示每个计分日的已提交与未提交人员。",
                listOf("应提交成员" to calendar.expectedMemberCount.toString(), "本月计分日" to rows.size.toString()),
                rows.map { day -> AdminNativeItem(day.date, day.date, "已提交 ${day.submittedCount}/${day.expectedCount} · 未提交 ${day.missingMembers.joinToString { it.displayName }.ifBlank { "无" }}", if (day.submittedCount == day.expectedCount) "完整" else "待补") })
        }
        "performance_admin" -> {
            val claims = authorizedRead { employee().performanceClaims("SUBMITTED") }
            val activities = authorizedRead { employee().performanceActivities(null) }.filter { it.status !in setOf("ALLOCATED", "REJECTED") }
            val rules = authorizedRead { employee().performanceRules() }
            val participants = authorizedRead { employee().performanceParticipants() }
            val today = runCatching { authorizedRead { employee().performanceDay(LocalDate.now(BUSINESS_ZONE).toString()) } }.getOrNull()
            AdminNativeModule(key, "绩效管理", "交流、活动、规则和参与人员使用同一绩效流水口径。",
                listOf("交流待审" to claims.size.toString(), "活动待处理" to activities.size.toString(), "参与人员" to participants.count { it.activeToday }.toString(), "规则版本" to rules.size.toString()),
                claims.map { AdminNativeItem("claim:${it.id}", "${it.memberDisplayName} · ${it.customerName}", "申报 ${it.requestedType} · ${it.performanceDate}", it.status, it.version, listOf("通过", "驳回")) } +
                    activities.map {
                        AdminNativeItem(
                            "activity:${it.id}", it.title,
                            "${it.organizerDisplayName} · ${it.eventDate} · 预计 ${it.expectedParticipants} 人${it.actualParticipants?.let { count -> " · 实际 $count 人" }.orEmpty()}",
                            it.status, it.version,
                            when (it.status) { "PROPOSED" -> listOf("通过", "驳回"); "COMPLETION_SUBMITTED" -> listOf("分配"); else -> emptyList() },
                            mapOf("actualParticipants" to (it.actualParticipants?.toString() ?: "0")),
                        )
                    } +
                    rules.sortedByDescending { it.versionNo }.map { rule ->
                        AdminNativeItem("rule:${rule.id}", "规则 v${rule.versionNo} · 基线 ${rule.dailyBaseline}", "${rule.effectiveFrom} 生效 · ${rule.calendarMode} · ${rule.submissionOpensAt}–${rule.submissionClosesAt} · 普通 ${rule.ordinaryPoints}/深度 ${rule.deepPoints}", "规则")
                    } +
                    participants.map { participant ->
                        AdminNativeItem("participant:${participant.id}", participant.displayName, "${participant.startsOn} 开始${participant.endsOn?.let { " · $it 结束" }.orEmpty()}", if (participant.activeToday) "参与中" else "已停止", participant.version, if (participant.activeToday) listOf("停止未来计分") else emptyList())
                    } +
                    listOfNotNull(today?.let { day ->
                        AdminNativeItem("day:${day.date}", "${day.date} 日结", "待审交流 ${day.pendingClaims} · 待处理活动 ${day.pendingActivities}", day.status, day.version,
                            when (day.status) { "SETTLED" -> listOf("重开"); else -> listOf("结算") })
                    }) + listOf(AdminNativeItem("export:performance", "导出月度绩效明细", "导出当月全组织 XLSX，下载由 Android 系统文件选择器完成。", "可导出", actions = listOf("导出")))
            )
        }
        "followup_policy" -> {
            val policy = authorizedRead { employee().followUpPolicy() }
            AdminNativeModule(key, "A 类跟进提醒策略", "A/B 自动释放默认保持关闭；C 类、人工释放和离队治理不受影响。",
                listOf("重点提醒" to if (policy.aClassStaleEnabled) "开启" else "关闭", "阈值" to "${policy.staleAfterHours} 小时", "A/B 自动释放" to if (policy.abAutoReleaseEnabled) "开启" else "关闭"),
                listOf(AdminNativeItem("followup-policy", "A 类超时提醒", "每日 ${policy.notifyAt} 检查 · QQ ${if (policy.qqEnabled) "开启" else "关闭"}", "版本 ${policy.version}", policy.version)),
                mapOf("aClassStaleEnabled" to policy.aClassStaleEnabled.toString(), "staleAfterHours" to policy.staleAfterHours.toString(), "notifyAt" to policy.notifyAt, "qqEnabled" to policy.qqEnabled.toString(), "abAutoReleaseEnabled" to policy.abAutoReleaseEnabled.toString(), "version" to policy.version.toString()))
        }
        "consultation_settings" -> {
            val settings = authorizedRead { employee().consultationSettings() }
            val reasons = authorizedRead { employee().adminConsultationReasons(true) }
            AdminNativeModule(key, "咨询交接设置", "预约容量、反馈时限和失败原因均由管理员维护。",
                listOf("营业时间" to "${settings.businessStart}–${settings.businessEnd}", "每日容量" to settings.dailyCapacity.toString(), "反馈时限" to "${settings.feedbackDueHours} 小时"),
                listOf(AdminNativeItem("consultation-settings", "预约与反馈策略", "提前 ${settings.minimumLeadMinutes} 分钟 · 时长 ${settings.allowedDurations.joinToString()} 分钟", "版本 ${settings.version}", settings.version)) +
                    reasons.map { reason -> AdminNativeItem("reason:${reason.id}", reason.label, "${reason.code} · 排序 ${reason.sortOrder}", if (reason.active) "启用" else "停用") },
                mapOf("businessStart" to settings.businessStart, "businessEnd" to settings.businessEnd, "minimumLeadMinutes" to settings.minimumLeadMinutes.toString(), "allowedDurations" to settings.allowedDurations.joinToString(","), "dailyCapacity" to settings.dailyCapacity.toString(), "feedbackDueHours" to settings.feedbackDueHours.toString(), "version" to settings.version.toString()))
        }
        "dictionaries" -> {
            val tags = authorizedRead { employee().tags() }
            val dictionaries = authorizedRead { employee().dictionaries() }
            AdminNativeModule(key, "字典与标签", "学校、来源、方向和标签均为组织级选项。",
                listOf("标签" to tags.size.toString(), "字典项" to dictionaries.size.toString()),
                dictionaries.map { AdminNativeItem("dictionary:${it.id}", it.label, "${it.type} · ${it.code}", "启用") } + tags.map { AdminNativeItem("tag:${it.id}", it.name, "${it.category} · ${it.color}", if (it.system) "系统" else "自定义") })
        }
        "ai_settings" -> {
            val row = authorizedRead { employee().assistantSettings() }
            AdminNativeModule(key, "AI 服务与模型", "密钥仅显示是否已配置，服务端从不回传明文。",
                listOf("服务" to if (row.enabled) "启用" else "停用", "密钥" to if (row.apiKeyConfigured) "已配置" else "未配置"),
                listOf(AdminNativeItem("assistant-settings", row.model, row.baseUrl, if (row.enabled) "运行中" else "已停用", row.version)),
                mapOf("enabled" to row.enabled.toString(), "baseUrl" to row.baseUrl, "model" to row.model, "version" to row.version.toString()))
        }
        "intelligence" -> {
            val settings = authorizedRead { employee().intelligenceSettings() }
            val runtime = authorizedRead { employee().intelligenceRuntime() }
            AdminNativeModule(key, "客户智能分析", "实时展示服务器、JVM、数据库连接池和分析队列压力。",
                listOf("压力" to runtime.pressureLabel, "系统 CPU" to runtime.systemCpuPercent?.let { "${"%.1f".format(it)}%" }.orEmpty(), "内存" to runtime.systemMemoryPercent?.let { "${"%.1f".format(it)}%" }.orEmpty(), "待处理" to runtime.pendingJobs.toString()),
                listOf(AdminNativeItem("intelligence-settings", "自动分析与补算", "模型 ${settings.embeddingModel} · 失败 ${runtime.failedJobs} · 运行中 ${runtime.runningJobs}", if (settings.backfillPaused) "已暂停" else "运行中", settings.version, listOf(if (settings.backfillPaused) "恢复补算" else "暂停补算"))))
        }
        "enrichment" -> {
            val jobs = authorizedRead { employee().enrichmentJobs() }
            AdminNativeModule(key, "客户资料补全", "文件预览与提交均保留 SHA-256 和行级结果。",
                listOf("历史任务" to jobs.size.toString()),
                jobs.map { AdminNativeItem(it.id, it.filename, "总计 ${it.totalRows} · 可处理 ${it.readyRows} · 更新 ${it.updatedRows} · 跳过 ${it.skippedRows} · 冲突 ${it.conflictRows}", it.status,
                    actions = buildList { if (it.status == "PREVIEWED") add("确认提交"); if (it.status !in setOf("PREVIEWED", "PARSING")) add("下载报告") }) })
        }
        "agent_admin" -> {
            val grants = authorizedRead { employee().organizationAgentGrants() }
            AdminNativeModule(key, "Agent 连接管理", "管理员可查看组织授权状态并强制撤销，但永远看不到 Token。",
                listOf("活跃连接" to grants.count { it.status == "ACTIVE" }.toString()),
                grants.map { AdminNativeItem(it.id, it.clientName, "${it.displayName} · ${it.scopes.joinToString()}", it.status, actions = if (it.status == "ACTIVE") listOf("撤销") else emptyList()) })
        }
        "audit" -> {
            val page = authorizedRead { employee().auditRows() }
            AdminNativeModule(key, "审计查询", "只返回脱敏后的操作、结果、人员、时间和 traceId。",
                listOf("当前页" to page.items.size.toString(), "总记录" to page.totalElements.toString()),
                page.items.map { AdminNativeItem(it.traceId ?: it.entityId, it.action, "${it.actorDisplayName ?: "系统"} · ${it.entityType} · ${it.createdAt}", it.outcome) } +
                    AdminNativeItem("export:audit", "导出当月审计记录", "只导出服务端权限范围内的脱敏 CSV。", "可导出", actions = listOf("导出")))
        }
        "webhooks" -> {
            val hooks = authorizedRead { employee().webhooks() }
            val deliveries = authorizedRead { employee().webhookDeliveries() }
            AdminNativeModule(key, "企业微信 Webhook", "地址只允许单向录入；列表不返回密钥或完整 URL。",
                listOf("启用通道" to hooks.count { it.active }.toString(), "失败投递" to deliveries.count { it.status == "FAILED" }.toString()),
                hooks.map { AdminNativeItem("hook:${it.id}", it.name, it.eventTypes.joinToString(), if (it.active) "启用" else "停用", it.version, if (it.active) listOf("停用") else emptyList()) } +
                    deliveries.filter { it.status == "FAILED" }.map { AdminNativeItem("delivery:${it.id}", it.title, "${it.channelName} · 尝试 ${it.attemptCount} 次 · ${it.lastErrorCode.orEmpty()}", "失败", actions = listOf("重试")) })
        }
        "leadership_settings" -> {
            val row = authorizedRead { employee().leadershipSettings() }
            AdminNativeModule(key, "领导大屏参数", "手机端仅配置展示参数；经营看板采用独立竖屏视图。",
                listOf("待机" to if (row.standbyEnabled) "启用" else "停用", "动画" to if (row.animationsEnabled) "启用" else "停用"),
                listOf(AdminNativeItem("leadership-settings", "特色年级 ${row.featuredGrade}", "空闲 ${row.idleTimeoutMinutes} 分钟 · 场景 ${row.sceneDurationSeconds} 秒", row.motionIntensity, row.version)),
                mapOf("animationsEnabled" to row.animationsEnabled.toString(), "standbyEnabled" to row.standbyEnabled.toString(), "idleTimeoutMinutes" to row.idleTimeoutMinutes.toString(), "sceneDurationSeconds" to row.sceneDurationSeconds.toString(), "eventDurationSeconds" to row.eventDurationSeconds.toString(), "motionIntensity" to row.motionIntensity, "sceneOrder" to row.sceneOrder.joinToString(","), "enabledScenes" to row.enabledScenes.joinToString(","), "enabledEvents" to row.enabledEvents.joinToString(","), "featuredGrade" to row.featuredGrade, "version" to row.version.toString()))
        }
        else -> throw RepositoryException("ADMIN_MODULE_NOT_REGISTERED", "该管理模块尚未登记移动端能力")
    }

    override suspend fun performAdminNativeAction(key: String, itemId: String, action: String, version: Long): AdminNativeModule {
        when (key) {
            "market" -> authorized {
                employee().selectClassification(itemId)
                employee().reviewClassification(itemId, MarketReviewRequestDto("CONFIRMED"))
            }
            "tasks_admin" -> when {
                itemId.startsWith("assignment:") -> {
                    val parts = itemId.split(':')
                    if (parts.size != 3) throw RepositoryException("ADMIN_ACTION_INVALID", "无法识别任务验收对象")
                    authorized { employee().reviewTaskAssignment(parts[1], parts[2], TaskReviewDto(if (action == "验收") "COMPLETED" else "RETURNED", if (action == "验收") null else "移动端管理员退回")) }
                }
                action == "删除" -> authorized { employee().deleteTask(itemId) }
                else -> authorized { employee().closeTask(itemId) }
            }
            "content_admin" -> when {
                itemId.startsWith("post:") -> authorized { employee().reviewContent(itemId.removePrefix("post:"), ContentReviewDto(if (action == "通过") "APPROVED" else "REJECTED", if (action == "通过") null else "移动端管理员驳回")) }
                itemId.startsWith("announcement:") && action == "归档" -> authorized { employee().archiveAnnouncement(itemId.removePrefix("announcement:")) }
                itemId.startsWith("announcement:") && action in setOf("置顶", "取消置顶") -> {
                    val id = itemId.removePrefix("announcement:")
                    val row = authorizedRead { employee().announcements(includeArchived = true) }.firstOrNull { it.id == id }
                        ?: throw RepositoryException("ANNOUNCEMENT_NOT_FOUND", "公告不存在")
                    authorized {
                        employee().updateAdminAnnouncement(
                            id,
                            AdminAnnouncementUpsertDto(row.title, row.content, row.priority, action == "置顶", row.showOnLeadershipScreen),
                        )
                    }
                }
                else -> throw RepositoryException("ADMIN_ACTION_INVALID", "无法识别内容管理对象")
            }
            "performance_admin" -> when {
                itemId.startsWith("claim:") -> {
                    val id = itemId.removePrefix("claim:")
                    val claim = authorizedRead { employee().performanceClaims(null) }.firstOrNull { it.id == id } ?: throw RepositoryException("PERFORMANCE_CLAIM_NOT_FOUND", "绩效申报不存在")
                    authorized { employee().reviewPerformanceClaim(id, PerformanceClaimReviewDto(if (action == "通过") "APPROVED" else "REJECTED", if (action == "通过") claim.requestedType else null, if (action == "通过") null else "移动端管理员驳回", claim.version)) }
                }
                itemId.startsWith("activity:") -> authorized { employee().reviewPerformanceActivity(itemId.removePrefix("activity:"), PerformanceActivityReviewDto(if (action == "通过") "APPROVED" else "REJECTED", if (action == "通过") null else "移动端管理员驳回", version)) }
                itemId.startsWith("participant:") -> authorized {
                    employee().updatePerformanceParticipant(itemId.removePrefix("participant:"), PerformanceParticipantUpdateDto(LocalDate.now(BUSINESS_ZONE).toString(), version))
                }
                itemId.startsWith("day:") && action == "结算" -> authorized { employee().settlePerformanceDay(itemId.removePrefix("day:"), version) }
                itemId.startsWith("day:") && action == "重开" -> authorized { employee().reopenPerformanceDay(itemId.removePrefix("day:"), PerformanceReopenDayDto("移动端管理员重开", version)) }
                else -> throw RepositoryException("ADMIN_ACTION_INVALID", "无法识别绩效对象")
            }
            "intelligence" -> authorized { if (action == "恢复补算") employee().resumeIntelligenceBackfill() else employee().pauseIntelligenceBackfill() }
            "enrichment" -> authorized { employee().commitEnrichment(itemId) }
            "agent_admin" -> authorized { employee().revokeOrganizationAgentGrant(itemId) }
            "webhooks" -> when {
                itemId.startsWith("hook:") -> authorized { employee().disableWebhook(itemId.removePrefix("hook:")) }
                itemId.startsWith("delivery:") -> authorized { employee().retryWebhookDelivery(itemId.removePrefix("delivery:")) }
                else -> throw RepositoryException("ADMIN_ACTION_INVALID", "无法识别 Webhook 对象")
            }
            else -> throw RepositoryException("ADMIN_ACTION_NOT_AVAILABLE", "该管理模块没有一键操作")
        }
        return adminNativeModule(key)
    }

    override suspend fun executeAdminNativeCommand(command: AdminNativeCommand): AdminNativeModule {
        when (command) {
            is CreateAdminTaskCommand -> {
                if (command.title.isBlank() || command.description.isBlank()) throw RepositoryException("VALIDATION_FAILED", "任务标题和说明不能为空")
                authorized {
                    employee().createAdminTask(
                        AdminTaskCreateDto(
                            command.title.trim(), command.description.trim(), command.distributionType,
                            Instant.now().toString(), command.dueAt, null,
                            command.assigneeIds.takeIf { command.distributionType == "ASSIGNED" },
                        )
                    )
                }
            }
            is CreateAdminAnnouncementCommand -> {
                if (command.title.isBlank() || command.content.isBlank()) throw RepositoryException("VALIDATION_FAILED", "公告标题和内容不能为空")
                authorized { employee().createAdminAnnouncement(AdminAnnouncementUpsertDto(command.title.trim(), command.content.trim(), command.priority, command.pinned, false)) }
            }
            is UpdateAdminAnnouncementCommand -> {
                if (command.title.isBlank() || command.content.isBlank()) throw RepositoryException("VALIDATION_FAILED", "公告标题和内容不能为空")
                authorized {
                    employee().updateAdminAnnouncement(
                        command.id,
                        AdminAnnouncementUpsertDto(command.title.trim(), command.content.trim(), command.priority, command.pinned, command.showOnLeadershipScreen),
                    )
                }
            }
            is CorrectAdminClassificationCommand -> authorized {
                val current = employee().currentClassification(command.customerId)
                employee().selectClassification(command.classificationId)
                employee().reviewClassification(
                    command.classificationId,
                    MarketReviewRequestDto(
                        decision = "CORRECTED",
                        note = command.correctionNote.trim(),
                        correction = MarketClassificationRequestDto(
                            level = command.correctedLevel,
                            recognitionReason = current.recognitionReason,
                            potentialDemand = current.potentialDemand,
                            aSignals = current.aSignals,
                            evidenceInteractionIds = current.evidenceInteractionIds,
                            correctionReason = command.correctionNote.trim(),
                        ),
                    ),
                )
            }
            is CreateAdminTagCommand -> {
                if (!Regex("#[0-9a-fA-F]{6}").matches(command.color)) throw RepositoryException("VALIDATION_FAILED", "标签颜色必须为 #RRGGBB")
                authorized { employee().createAdminTag(AdminTagCreateDto(command.name.trim(), command.category.trim(), command.color)) }
            }
            is CreateAdminDictionaryCommand -> authorized {
                employee().createAdminDictionary(AdminDictionaryCreateDto(command.type, command.code.trim(), command.label.trim(), command.sortOrder))
            }
            is UpdateAdminAssistantCommand -> authorized {
                employee().updateAssistantSettings(AdminAssistantUpdateDto(command.enabled, command.baseUrl.trim(), command.model.trim(), command.apiKey?.trim()?.takeIf(String::isNotBlank), command.clearApiKey, command.expectedVersion))
            }
            is CreateAdminWebhookCommand -> authorized {
                employee().createWebhook(AdminWebhookCreateDto(command.name.trim(), command.webhookUrl.trim(), command.eventTypes, true, null))
            }
            is PreviewAdminEnrichmentCommand -> {
                if (command.file.bytes.size > 20 * 1024 * 1024) throw RepositoryException("FILE_TOO_LARGE", "XLSX 不得超过 20 MB")
                val body = command.file.bytes.toRequestBody(command.file.contentType.toMediaType())
                val part = MultipartBody.Part.createFormData("file", command.file.fileName.take(180), body)
                authorized { employee().previewEnrichment(part) }
            }
            is CreateAdminManualPerformanceCommand -> authorized {
                employee().createManualPerformance(
                    AdminManualPerformanceDto(command.membershipId, command.performanceDate, command.entryType, command.title.trim(), command.reason.trim(), command.points, null)
                )
            }
            is CreateAdminPerformanceRuleCommand -> authorized {
                employee().createPerformanceRule(
                    PerformanceRuleCreateDto(
                        command.effectiveFrom, command.dailyBaseline, command.calendarMode, command.weekdays,
                        submissionOpensAt = command.submissionOpensAt, submissionClosesAt = command.submissionClosesAt,
                        shortfallMode = command.shortfallMode, ordinaryPoints = command.ordinaryPoints,
                        deepPoints = command.deepPoints, ordinaryDailyCap = command.ordinaryDailyCap,
                        deepDailyCap = command.deepDailyCap,
                        eventTiers = command.eventTiers.map { PerformanceTierDto(it.label, it.minParticipants, it.maxParticipants, it.points) },
                    )
                )
            }
            is AddAdminPerformanceParticipantCommand -> authorized {
                employee().addPerformanceParticipant(PerformanceParticipantCreateDto(command.membershipId, command.startsOn, command.endsOn))
            }
            is AllocateAdminPerformanceActivityCommand -> authorized {
                if (command.allocations.isEmpty()) throw RepositoryException("VALIDATION_FAILED", "请至少选择一名参与绩效的成员")
                employee().allocatePerformanceActivity(
                    command.activityId,
                    PerformanceActivityAllocateDto(
                        command.verifiedParticipants,
                        command.allocations.map { PerformanceActivityAllocationDto(it.membershipId, it.points) },
                        command.expectedVersion,
                    ),
                )
            }
            is SettleAdminPerformanceDayCommand -> authorized { employee().settlePerformanceDay(command.date, command.expectedVersion) }
            is ReopenAdminPerformanceDayCommand -> authorized { employee().reopenPerformanceDay(command.date, PerformanceReopenDayDto(command.reason, command.expectedVersion)) }
            is UpdateAdminFollowUpPolicyCommand -> authorized {
                employee().updateFollowUpPolicy(FollowUpPolicyUpdateDto(command.aClassStaleEnabled, command.staleAfterHours, command.notifyAt, command.qqEnabled, command.abAutoReleaseEnabled, command.expectedVersion))
            }
            is UpdateAdminConsultationSettingsCommand -> authorized {
                employee().updateConsultationSettings(ConsultationAdminSettingsUpdateDto(command.businessStart, command.businessEnd, command.minimumLeadMinutes, command.allowedDurations, command.dailyCapacity, command.feedbackDueHours, command.expectedVersion))
            }
            is SaveAdminConsultationReasonCommand -> authorized {
                val body = ConsultationReasonSaveDto(command.code.trim(), command.label.trim(), command.active, command.sortOrder)
                if (command.id == null) employee().createConsultationReason(body) else employee().updateConsultationReason(command.id, body)
            }
            is UpdateAdminLeadershipSettingsCommand -> authorized {
                employee().updateLeadershipSettings(
                    LeadershipSettingsUpdateDto(
                        command.animationsEnabled, command.standbyEnabled, command.idleTimeoutMinutes,
                        command.sceneDurationSeconds, command.eventDurationSeconds, command.motionIntensity,
                        command.sceneOrder, command.enabledScenes, command.enabledEvents, command.featuredGrade,
                        command.expectedVersion,
                    )
                )
            }
        }
        return adminNativeModule(command.moduleKey)
    }

    override suspend fun downloadAdminArtifact(key: String, itemId: String): DownloadArtifact {
        val today = LocalDate.now(BUSINESS_ZONE)
        val body = when (key) {
            "performance_admin" -> authorizedRead { employee().exportPerformance(today.withDayOfMonth(1).toString(), today.toString()) }
            "audit" -> authorizedRead { employee().auditExport(today.withDayOfMonth(1).toString(), today.toString()) }
            "enrichment" -> authorizedRead { employee().enrichmentReport(itemId) }
            "cross_sales" -> authorizedRead { employee().exportCrossSales(crossSale = true) }
            else -> throw RepositoryException("ADMIN_EXPORT_NOT_AVAILABLE", "该管理模块没有登记导出能力")
        }
        val contentType = body.contentType()?.toString() ?: if (key in setOf("performance_admin", "cross_sales")) {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else "text/csv"
        val name = when (key) {
            "performance_admin" -> "绩效明细-${today.withDayOfMonth(1)}-$today.xlsx"
            "audit" -> "审计记录-${today.withDayOfMonth(1)}-$today.csv"
            "cross_sales" -> "交叉单查询-$today.xlsx"
            else -> "客户资料补全-$itemId.csv"
        }
        return body.use { DownloadArtifact(name, contentType, it.bytes()) }
    }

    override suspend fun adminDealRequests(): List<DealRequest> =
        authorizedRead { employee().adminDealRequests(status = "PENDING") }.content.map(DealRequestDto::toModel)

    override suspend fun reviewDealRequest(requestId: String, version: Long, approve: Boolean): DealRequest =
        authorized {
            if (approve) employee().approveDealRequest(requestId, version)
            else employee().rejectDealRequest(requestId, version)
        }.toModel()

    override suspend fun leadershipDashboard(teamId: String?, membershipId: String?): LeadershipDashboard =
        authorizedRead { employee().leadershipDashboard(teamId, membershipId, 30) }.toModel()

    private suspend fun switchConnection(serverUrl: String): MobileContractGateway {
        val origin = ServerOriginValidator.requireValid(serverUrl, serverUrl.startsWith("http://")).canonical
        if (origin == serverOrigin && gateway != null && api != null) return gateway!!
        val oldOrigin = serverOrigin
        val oldGateway = gateway
        if (oldOrigin != null && oldOrigin != origin) {
            runCatching { oldGateway?.logout() }
            tokenVault.clear(oldOrigin)
            gateway = null; api = null; serverOrigin = null
            synchronized(consultationCache) { consultationCache.clear() }
            synchronized(contentCache) { contentCache.clear() }
        }
        val contract = MobileContractGateway.create(appContext, origin, origin.startsWith("http://"), tokenVault)
        val deviceId = DeviceIdentity(appContext).id
        val expected = ServerOriginValidator.requireValid(origin, origin.startsWith("http://"))
        val client = MobileHttpClientFactory.create(
            origin = expected,
            deviceId = deviceId,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            accessToken = { tokenVault.accessToken(origin) },
        )
        api = Retrofit.Builder().baseUrl("$origin/").client(client).addConverterFactory(GsonConverterFactory.create()).build().create(EmployeeApi::class.java)
        gateway = contract
        serverOrigin = origin
        return contract
    }

    private fun contract(): MobileContractGateway = gateway ?: throw RepositoryException("MOBILE_SESSION_MISSING", "请重新登录")
    private fun employee(): EmployeeApi = api ?: throw RepositoryException("MOBILE_SESSION_MISSING", "请重新登录")

    private suspend fun <T> authorized(block: suspend () -> T): T {
        val origin = serverOrigin ?: throw RepositoryException("MOBILE_SESSION_MISSING", "请重新登录")
        if (tokenVault.accessToken(origin) == null) contract().refreshAfterUnauthorized(null)
        val used = tokenVault.accessToken(origin)
        return try { call(block) } catch (error: RepositoryException) {
            if (error.httpStatus != 401) throw error
            contract().refreshAfterUnauthorized(used)
            try { call(block) } catch (retry: RepositoryException) {
                if (retry.httpStatus == 401) tokenVault.clear(origin)
                throw retry
            }
        }
    }

    private suspend fun <T> authorizedRead(block: suspend () -> T): T =
        retryTransientRead { authorized(block) }

    private suspend fun <T> call(block: suspend () -> T): T = MobileNetworkCall.execute(block)

    companion object { private val BUSINESS_ZONE = ZoneId.of("Asia/Shanghai") }
}

internal suspend fun <T> retryTransientRead(
    maxAttempts: Int = 3,
    initialBackoffMillis: Long = 500,
    block: suspend () -> T,
): T {
    require(maxAttempts >= 1)
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (error: RepositoryException) {
            val retryable = error.code in setOf(
                "NETWORK_IO_FAILED",
                "NETWORK_CONNECT_FAILED",
                "NETWORK_TIMEOUT",
                "HTTP_502",
                "HTTP_503",
                "HTTP_504",
            )
            if (!retryable || attempt >= maxAttempts) throw error
            val backoff = initialBackoffMillis * attempt
            if (backoff > 0) delay(backoff)
            attempt += 1
        }
    }
}

internal fun parseCustomerInsightLines(customerId: String, readLine: () -> String?): CustomerInsight {
    var event = ""
    val deltas = StringBuilder()
    var finalContent: String? = null
    var interrupted: IOException? = null
    while (true) {
        val line = try {
            readLine() ?: break
        } catch (error: IOException) {
            interrupted = error
            break
        }
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                val data = line.removePrefix("data:").trim()
                if (event == "delta") {
                    deltas.append(runCatching { Gson().fromJson(data, String::class.java) }.getOrDefault(data.trim('"')))
                }
                if (event == "result") {
                    finalContent = runCatching { Gson().fromJson(data, Map::class.java)["content"]?.toString() }.getOrNull()
                }
                if (event == "error") throw RepositoryException("AI_STREAM_ERROR", "AI 客户洞察暂时不可用")
            }
        }
    }
    val content = finalContent?.takeIf { it.isNotBlank() }
        ?: deltas.toString().takeIf { it.isNotBlank() }
        ?: interrupted?.let { throw it }
        ?: throw RepositoryException("AI_EMPTY_RESULT", "AI 未返回可用客户洞察")
    return CustomerInsight(customerId, content)
}

private interface EmployeeApi {
    @GET("api/v1/mobile/admin/overview") suspend fun adminOverview(): AdminOverviewDto
    @GET("api/v1/admin/members") suspend fun adminMembers(): List<AdminMemberDto>
    @POST("api/v1/admin/members") suspend fun createAdminMember(@Body body: AdminMemberCreateDto): AdminMemberDto
    @PATCH("api/v1/admin/members/{id}") suspend fun updateAdminMember(@Path("id") id: String, @Body body: AdminMemberUpdateDto): AdminMemberDto
    @POST("api/v1/admin/members/{id}/delete") suspend fun deleteAdminMember(@Path("id") id: String, @Body body: AdminMemberDeleteDto)
    @GET("api/v1/admin/teams") suspend fun adminTeams(): List<AdminTeamDto>
    @POST("api/v1/admin/teams") suspend fun createAdminTeam(@Body body: AdminTeamCreateDto): AdminTeamDto
    @PUT("api/v1/admin/teams/{id}/leader") suspend fun assignTeamLeader(@Path("id") id: String, @Body body: AdminTeamLeaderDto): AdminTeamDto
    @DELETE("api/v1/admin/teams/{id}") suspend fun deleteAdminTeam(@Path("id") id: String)
    @GET("api/v1/admin/registration-codes") suspend fun adminRegistrationCodes(): List<AdminRegistrationCodeDto>
    @POST("api/v1/admin/registration-codes") suspend fun createAdminRegistrationCode(@Body body: AdminRegistrationCodeCreateDto): AdminRegistrationCodeDto
    @POST("api/v1/admin/registration-codes/{id}/disable") suspend fun disableAdminRegistrationCode(@Path("id") id: String)
    @GET("api/v1/admin/market/classification-reviews") suspend fun classificationReviews(@Query("recommendedOnly") recommendedOnly: Boolean): List<MarketReviewDto>
    @GET("api/v1/admin/market/classification-anomalies") suspend fun classificationAnomalies(): List<MarketAnomalyDto>
    @POST("api/v1/admin/market/classification-reviews/{id}/select") suspend fun selectClassification(@Path("id") id: String)
    @POST("api/v1/admin/market/classification-reviews/{id}/review") suspend fun reviewClassification(@Path("id") id: String, @Body body: MarketReviewRequestDto): Any
    @GET("api/v1/customers/{id}/classifications/current") suspend fun currentClassification(@Path("id") id: String): MarketClassificationDto
    @GET("api/v1/admin/cross-sales") suspend fun crossSales(
        @Query("crossSale") crossSale: Boolean? = null,
        @Query("ownerMembershipId") ownerMembershipId: String? = null,
        @Query("collaboratorMembershipId") collaboratorMembershipId: String? = null,
        @Query("status") status: String? = null,
        @Query("quality") quality: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): PageDto<CrossSaleDto>
    @Streaming @GET("api/v1/admin/cross-sales/export.xlsx") suspend fun exportCrossSales(
        @Query("crossSale") crossSale: Boolean? = null,
        @Query("ownerMembershipId") ownerMembershipId: String? = null,
        @Query("collaboratorMembershipId") collaboratorMembershipId: String? = null,
        @Query("status") status: String? = null,
        @Query("quality") quality: String? = null,
    ): ResponseBody
    @POST("api/v1/tasks/{id}/close") suspend fun closeTask(@Path("id") id: String)
    @DELETE("api/v1/tasks/{id}") suspend fun deleteTask(@Path("id") id: String)
    @GET("api/v1/tasks/matrix") suspend fun taskMatrix(): List<TaskMatrixRowDto>
    @PATCH("api/v1/tasks/{taskId}/assignments/{assignmentId}") suspend fun reviewTaskAssignment(@Path("taskId") taskId: String, @Path("assignmentId") assignmentId: String, @Body body: TaskReviewDto): AssignmentDto
    @POST("api/v1/tasks") suspend fun createAdminTask(@Body body: AdminTaskCreateDto): TaskDto
    @PATCH("api/v1/content-posts/{id}/review") suspend fun reviewContent(@Path("id") id: String, @Body body: ContentReviewDto): ContentPostDto
    @POST("api/v1/announcements/{id}/archive") suspend fun archiveAnnouncement(@Path("id") id: String)
    @POST("api/v1/announcements") suspend fun createAdminAnnouncement(@Body body: AdminAnnouncementUpsertDto): AnnouncementDto
    @PATCH("api/v1/announcements/{id}") suspend fun updateAdminAnnouncement(@Path("id") id: String, @Body body: AdminAnnouncementUpsertDto): AnnouncementDto
    @GET("api/v1/daily-reports/calendar") suspend fun dailyCalendar(@Query("month") month: String): DailyCalendarDto
    @GET("api/v1/performance/claims") suspend fun performanceClaims(@Query("status") status: String?): List<PerformanceAdminClaimDto>
    @POST("api/v1/performance/claims/{id}/review") suspend fun reviewPerformanceClaim(@Path("id") id: String, @Body body: PerformanceClaimReviewDto): PerformanceAdminClaimDto
    @GET("api/v1/performance/activities") suspend fun performanceActivities(@Query("status") status: String?): List<PerformanceAdminActivityDto>
    @POST("api/v1/performance/activities/{id}/review") suspend fun reviewPerformanceActivity(@Path("id") id: String, @Body body: PerformanceActivityReviewDto): PerformanceAdminActivityDto
    @GET("api/v1/performance/admin/rules") suspend fun performanceRules(): List<PerformanceRuleDto>
    @POST("api/v1/performance/admin/rules") suspend fun createPerformanceRule(@Body body: PerformanceRuleCreateDto): PerformanceRuleDto
    @GET("api/v1/performance/admin/participants") suspend fun performanceParticipants(): List<PerformanceParticipantDto>
    @POST("api/v1/performance/admin/participants") suspend fun addPerformanceParticipant(@Body body: PerformanceParticipantCreateDto): PerformanceParticipantDto
    @PATCH("api/v1/performance/admin/participants/{id}") suspend fun updatePerformanceParticipant(@Path("id") id: String, @Body body: PerformanceParticipantUpdateDto): PerformanceParticipantDto
    @GET("api/v1/performance/admin/days/{date}") suspend fun performanceDay(@Path("date") date: String): PerformanceDayDto?
    @POST("api/v1/performance/admin/days/{date}/settle") suspend fun settlePerformanceDay(@Path("date") date: String, @Query("expectedVersion") expectedVersion: Long): PerformanceDayDto
    @POST("api/v1/performance/admin/days/{date}/reopen") suspend fun reopenPerformanceDay(@Path("date") date: String, @Body body: PerformanceReopenDayDto): PerformanceDayDto
    @Streaming @GET("api/v1/performance/admin/export") suspend fun exportPerformance(@Query("from") from: String, @Query("to") to: String): ResponseBody
    @GET("api/v1/tags") suspend fun tags(): List<AdminTagDto>
    @POST("api/v1/tags") suspend fun createAdminTag(@Body body: AdminTagCreateDto): AdminTagDto
    @GET("api/v1/dictionaries") suspend fun dictionaries(): List<AdminDictionaryDto>
    @POST("api/v1/dictionaries") suspend fun createAdminDictionary(@Body body: AdminDictionaryCreateDto): AdminDictionaryDto
    @GET("api/v1/admin/assistant-settings") suspend fun assistantSettings(): AssistantSettingsDto
    @PUT("api/v1/admin/assistant-settings") suspend fun updateAssistantSettings(@Body body: AdminAssistantUpdateDto): AssistantSettingsDto
    @GET("api/v1/admin/customer-intelligence/settings") suspend fun intelligenceSettings(): IntelligenceSettingsDto
    @GET("api/v1/admin/customer-intelligence/runtime") suspend fun intelligenceRuntime(): IntelligenceRuntimeDto
    @POST("api/v1/admin/customer-intelligence/backfill/resume") suspend fun resumeIntelligenceBackfill(): IntelligenceSettingsDto
    @POST("api/v1/admin/customer-intelligence/backfill/pause") suspend fun pauseIntelligenceBackfill(): IntelligenceSettingsDto
    @GET("api/v1/admin/customer-enrichment-jobs") suspend fun enrichmentJobs(): List<EnrichmentJobDto>
    @Multipart @POST("api/v1/admin/customer-enrichment-jobs/preview") suspend fun previewEnrichment(@Part file: MultipartBody.Part): EnrichmentPreviewDto
    @POST("api/v1/admin/customer-enrichment-jobs/{id}/commit") suspend fun commitEnrichment(@Path("id") id: String): Any
    @Streaming @GET("api/v1/admin/customer-enrichment-jobs/{id}/report.csv") suspend fun enrichmentReport(@Path("id") id: String): ResponseBody
    @GET("api/v1/admin/agent/authorizations") suspend fun organizationAgentGrants(): List<AgentGrantDto>
    @DELETE("api/v1/admin/agent/authorizations/{id}") suspend fun revokeOrganizationAgentGrant(@Path("id") id: String)
    @GET("api/v1/admin/audit") suspend fun auditRows(@Query("page") page: Int = 0, @Query("size") size: Int = 50): PageDto<AuditRowDto>
    @Streaming @GET("api/v1/admin/audit/export") suspend fun auditExport(@Query("from") from: String?, @Query("to") to: String?): ResponseBody
    @GET("api/v1/admin/wecom-webhooks") suspend fun webhooks(): List<WebhookDto>
    @POST("api/v1/admin/wecom-webhooks") suspend fun createWebhook(@Body body: AdminWebhookCreateDto): WebhookDto
    @DELETE("api/v1/admin/wecom-webhooks/{id}") suspend fun disableWebhook(@Path("id") id: String)
    @GET("api/v1/admin/wecom-webhooks/deliveries") suspend fun webhookDeliveries(@Query("size") size: Int = 100): List<WebhookDeliveryDto>
    @POST("api/v1/admin/wecom-webhooks/deliveries/{id}/retry") suspend fun retryWebhookDelivery(@Path("id") id: String)
    @GET("api/v1/admin/leadership-screen-settings") suspend fun leadershipSettings(): LeadershipSettingsDto
    @PUT("api/v1/admin/leadership-screen-settings") suspend fun updateLeadershipSettings(@Body body: LeadershipSettingsUpdateDto): LeadershipSettingsDto
    @GET("api/v1/admin/follow-up-policy") suspend fun followUpPolicy(): FollowUpPolicyDto
    @PUT("api/v1/admin/follow-up-policy") suspend fun updateFollowUpPolicy(@Body body: FollowUpPolicyUpdateDto): FollowUpPolicyDto
    @GET("api/v1/admin/consultation-settings") suspend fun consultationSettings(): ConsultationAdminSettingsDto
    @PUT("api/v1/admin/consultation-settings") suspend fun updateConsultationSettings(@Body body: ConsultationAdminSettingsUpdateDto): ConsultationAdminSettingsDto
    @GET("api/v1/consultation-failure-reasons") suspend fun adminConsultationReasons(@Query("includeInactive") includeInactive: Boolean = true): List<ConsultationFailureReasonDto>
    @POST("api/v1/admin/consultation-reasons") suspend fun createConsultationReason(@Body body: ConsultationReasonSaveDto): ConsultationFailureReasonDto
    @PUT("api/v1/admin/consultation-reasons/{id}") suspend fun updateConsultationReason(@Path("id") id: String, @Body body: ConsultationReasonSaveDto): ConsultationFailureReasonDto
    @POST("api/v1/performance/admin/manual-entries") suspend fun createManualPerformance(@Body body: AdminManualPerformanceDto): Any
    @GET("api/v1/admin/deal-requests") suspend fun adminDealRequests(@Query("status") status: String?, @Query("page") page: Int = 0, @Query("size") size: Int = 50): DealRequestPageDto
    @POST("api/v1/admin/deal-requests/{id}/approve") suspend fun approveDealRequest(@Path("id") id: String, @Header("If-Match-Version") version: Long): DealRequestDto
    @POST("api/v1/admin/deal-requests/{id}/reject") suspend fun rejectDealRequest(@Path("id") id: String, @Header("If-Match-Version") version: Long): DealRequestDto
    @GET("api/v1/leadership-dashboard") suspend fun leadershipDashboard(@Query("teamId") teamId: String?, @Query("membershipId") membershipId: String?, @Query("days") days: Int): LeadershipDashboardDto
    @GET("api/v1/dashboard") suspend fun dashboard(): DashboardDto
    @POST("api/v1/mobile/dashboard/greeting") suspend fun mobileGreeting(): MobileGreetingDto
    @GET("api/v1/team-data/overview") suspend fun teamDataOverview(): TeamDataOverviewDto
    @GET("api/v1/customers") suspend fun customers(
        @Query("search") search: String?,
        @Query("status") status: String? = null,
        @Query("intention") intention: String? = null,
        @Query("grade") grade: String? = null,
        @Query("school") school: String? = null,
        @Query("ownerMembershipId") ownerMembershipId: String? = null,
        @Query("includeArchived") includeArchived: Boolean = false,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "updatedDesc",
    ): PageDto<CustomerDto>
    @GET("api/v1/customers/{id}") suspend fun customer(@Path("id") id: String): CustomerDto
    @GET("api/v1/customers/{id}/collaboration") suspend fun collaboration(@Path("id") id: String): CollaborationDto
    @PUT("api/v1/customers/{id}/collaborators") suspend fun replaceCollaborators(@Path("id") id: String, @Body body: UpdateCollaboratorsDto): CollaborationDto
    @POST("api/v1/customers/{id}/transfer") suspend fun transferCustomer(@Path("id") id: String, @Body body: TransferOwnershipDto): CustomerDto
    @POST("api/v1/customers/{id}/archive") suspend fun archiveCustomer(@Path("id") id: String)
    @GET("api/v1/customers/{id}/classifications") suspend fun classificationHistory(@Path("id") id: String): List<MarketClassificationDto>
    @POST("api/v1/customers/{id}/classifications") suspend fun classifyCustomer(@Path("id") id: String, @Body body: MarketClassificationRequestDto): MarketClassificationDto
    @GET("api/v1/customers/{id}/deadline-shadow") suspend fun deadlineShadow(@Path("id") id: String): DeadlineShadowDto
    @Streaming @GET("api/v1/exports/customers/{id}/timeline") suspend fun exportCustomerTimeline(@Path("id") id: String, @Query("format") format: String = "xlsx"): ResponseBody
    @Multipart @POST("api/v1/imports/customers/preview") suspend fun previewCustomerImport(@Part file: MultipartBody.Part): CustomerImportPreviewDto
    @POST("api/v1/imports/{id}/commit") suspend fun commitCustomerImport(@Path("id") id: String): CustomerImportReportDto
    @Streaming @GET("api/v1/exports/customers") suspend fun exportCustomers(@Query("format") format: String): ResponseBody
    @POST("api/v1/customers") suspend fun createCustomer(@Body body: CustomerUpsertDto): CustomerDto
    @PATCH("api/v1/customers/{id}") suspend fun updateCustomer(@Path("id") id: String, @Body body: CustomerUpsertDto): CustomerDto
    @GET("api/v1/customers/{id}/interactions") suspend fun interactions(@Path("id") id: String): List<InteractionDto>
    @POST("api/v1/mobile/customers/{id}/insight") suspend fun mobileCustomerInsight(@Path("id") id: String): MobileCustomerInsightDto
    @POST("api/v1/customers/{id}/interactions") suspend fun addInteraction(@Path("id") id: String, @Body body: InteractionCreateDto): InteractionDto
    @GET("api/v1/reminders") suspend fun reminders(): List<ReminderDto>
    @PATCH("api/v1/reminders/{id}") suspend fun updateReminder(@Path("id") id: String, @Body body: ReminderUpdateDto): ReminderDto
    @GET("api/v1/daily-reports/today") suspend fun dailyToday(): DailyDto
    @PUT("api/v1/daily-reports/{date}") suspend fun saveDaily(@Path("date") date: String, @Body body: DailyUpsertDto): DailyDto
    @GET("api/v1/notifications") suspend fun notifications(): NotificationInboxDto
    @POST("api/v1/notifications/{id}/read") suspend fun markNotificationRead(@Path("id") id: String)
    @POST("api/v1/notifications/read-all") suspend fun markAllNotificationsRead(): NotificationReadAllDto
    @GET("api/v1/tasks") suspend fun tasks(): List<TaskDto>
    @POST("api/v1/tasks/{id}/claim") suspend fun claimTask(@Path("id") id: String): AssignmentDto
    @PATCH("api/v1/tasks/{id}/my-assignment") suspend fun updateTask(@Path("id") id: String, @Body body: TaskUpdateDto): AssignmentDto
    @GET("api/v1/performance/today") suspend fun performanceToday(): PerformanceTodayDto
    @GET("api/v1/performance/history") suspend fun performanceHistory(@Query("from") from: String, @Query("to") to: String): List<PerformanceHistoryDto>
    @GET("api/v1/performance/leaderboard") suspend fun performanceLeaderboard(@Query("from") from: String, @Query("to") to: String): List<PerformanceLeaderboardDto>
    @POST("api/v1/performance/claims") suspend fun createPerformanceClaim(@Body body: PerformanceClaimCreateDto): PerformanceClaimDto
    @Multipart @POST("api/v1/performance/claims/{id}/evidence") suspend fun uploadPerformanceEvidence(@Path("id") id: String, @Part file: MultipartBody.Part): PerformanceEvidenceDto
    @POST("api/v1/performance/claims/{id}/submit") suspend fun submitPerformanceClaim(@Path("id") id: String): PerformanceClaimDto
    @POST("api/v1/performance/activities") suspend fun createPerformanceActivity(@Body body: PerformanceActivityCreateDto): PerformanceAdminActivityDto
    @Multipart @POST("api/v1/performance/activities/{id}/evidence") suspend fun uploadPerformanceActivityEvidence(@Path("id") id: String, @Part file: MultipartBody.Part): PerformanceEvidenceDto
    @POST("api/v1/performance/activities/{id}/complete") suspend fun completePerformanceActivity(@Path("id") id: String, @Body body: PerformanceActivityCompleteDto): PerformanceAdminActivityDto
    @POST("api/v1/performance/activities/{id}/allocate") suspend fun allocatePerformanceActivity(@Path("id") id: String, @Body body: PerformanceActivityAllocateDto): PerformanceAdminActivityDto
    @GET("api/v1/consultations") suspend fun consultations(): List<ConsultationDto>
    @GET("api/v1/consultations/{id}") suspend fun consultation(@Path("id") id: String): ConsultationDto
    @GET("api/v1/consultations/candidates") suspend fun consultationCandidates(): List<ConsultantDto>
    @GET("api/v1/consultation-failure-reasons") suspend fun consultationFailureReasons(): List<ConsultationFailureReasonDto>
    @POST("api/v1/consultations") suspend fun createConsultation(@Body body: ConsultationCreateDto): ConsultationDto
    @POST("api/v1/consultations/{id}/accept") suspend fun acceptConsultation(@Path("id") id: String, @Body body: ConsultationVersionDto): ConsultationDto
    @POST("api/v1/consultations/{id}/reject") suspend fun rejectConsultation(@Path("id") id: String, @Body body: ConsultationRejectDto): ConsultationDto
    @POST("api/v1/consultations/{id}/end") suspend fun endConsultation(@Path("id") id: String, @Body body: ConsultationEndDto): ConsultationDto
    @POST("api/v1/consultations/{id}/complete") suspend fun completeConsultation(@Path("id") id: String, @Body body: ConsultationCompleteDto): ConsultationDto
    @GET("api/v1/content-tasks/overview") suspend fun contentOverview(): ContentOverviewDto
    @POST("api/v1/content-posts") suspend fun submitContent(@Body body: ContentPostCreateDto): ContentPostDto
    @PATCH("api/v1/content-posts/{id}/metrics") suspend fun updateContentMetrics(@Path("id") id: String, @Body body: ContentMetricsDto): ContentPostDto
    @GET("api/v1/circle/posts") suspend fun circle(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
        @Query("search") search: String? = null,
        @Query("membershipId") membershipId: String? = null,
        @Query("sort") sort: String = "recommended",
    ): PageDto<ContentPostDto>
    @GET("api/v1/circle/senders") suspend fun circleSenders(): List<CircleSenderDto>
    @GET("api/v1/announcements") suspend fun announcements(@Query("includeArchived") includeArchived: Boolean = false): List<AnnouncementDto>
    @GET("api/v1/releases") suspend fun releases(@Query("page") page: Int = 0, @Query("size") size: Int = 20): PageDto<ProductReleaseDto>
    @GET("api/v1/releases/current/unacknowledged") suspend fun unacknowledgedRelease(): ProductReleaseDto?
    @POST("api/v1/releases/{id}/acknowledge") suspend fun acknowledgeRelease(@Path("id") id: String)
    @GET("api/v1/assistant/availability") suspend fun assistantAvailability(): AssistantAvailabilityDto
    @POST("api/v1/assistant/customer-draft") suspend fun assistantCustomerDraft(@Body body: AssistantParseDto): Map<String, Any?>
    @POST("api/v1/assistant/interaction-draft") suspend fun assistantInteractionDraft(@Body body: AssistantParseDto): Map<String, Any?>
    @Streaming @GET("api/v1/assistant/daily-briefing") suspend fun assistantDailyBriefing(): ResponseBody
    @Streaming @GET("api/v1/assistant/customers/{id}/summary") suspend fun assistantCustomerSummary(@Path("id") id: String): ResponseBody
    @Streaming @GET("api/v1/assistant/customers/{id}/next-actions") suspend fun assistantCustomerNextActions(@Path("id") id: String): ResponseBody
    @Streaming @GET("api/v1/assistant/customers/{id}/data-quality") suspend fun assistantCustomerDataQuality(@Path("id") id: String): ResponseBody
    @Streaming @GET("api/v1/assistant/customers/{id}/summary") suspend fun customerSummary(@Path("id") id: String): ResponseBody
    @GET("api/v1/agent/authorizations") suspend fun agentAuthorizations(): List<AgentGrantDto>
    @DELETE("api/v1/agent/authorizations/{id}") suspend fun revokeAgentAuthorization(@Path("id") id: String)
    @GET("api/v1/customer-pools") suspend fun poolEntries(): List<PoolEntryDto>
    @POST("api/v1/customer-pools/{id}/claims") suspend fun claimPool(@Path("id") id: String, @Body body: PoolClaimDto): PoolClaimResultDto
    @GET("api/v1/second-line-recruitments") suspend fun secondLineRecruitments(): List<SecondLineDto>
    @POST("api/v1/second-line-recruitments/{id}/claim") suspend fun claimSecondLine(@Path("id") id: String): SecondLineClaimResultDto
}

private data class PageDto<T>(val items: List<T> = emptyList(), val page: Int = 0, val size: Int = 0, val totalElements: Long = 0)
private data class DealRequestPageDto(val content: List<DealRequestDto> = emptyList())
private data class AdminMemberDto(
    val membershipId: String, val userId: String, val username: String, val displayName: String,
    val status: String, val roles: Set<String> = emptySet(), val teams: Set<String> = emptySet(),
    val teamIds: Set<String> = emptySet(), val ownedCustomerCount: Long = 0,
)
private data class AdminMemberCreateDto(val username: String, val displayName: String, val initialPassword: String, val role: String, val teamId: String?)
private data class AdminMemberUpdateDto(val displayName: String, val role: String, val status: String, val teamId: String?)
private data class AdminMemberDeleteDto(val transferCustomerToMembershipId: String?)
private data class AdminTeamDto(
    val id: String, val name: String, val active: Boolean = true, val leaderMembershipId: String? = null,
    val leaderDisplayName: String? = null, val memberCount: Int = 0,
)
private data class AdminTeamCreateDto(val name: String)
private data class AdminTeamLeaderDto(val membershipId: String)
private data class AdminRegistrationCodeDto(
    val id: String,
    val code: String? = null,
    val codeHint: String,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: String? = null,
    val status: String? = null,
    val active: Boolean? = null,
    val createdAt: String,
)
private data class AdminRegistrationCodeCreateDto(val maxUses: Int, val expiresAt: String?)
private data class LeadershipOptionDto(val id: String? = null, val membershipId: String? = null, val name: String? = null, val displayName: String? = null)
private data class LeadershipHeadlineDto(
    val totalCustomers: Long = 0, val addedFriendCustomers: Long = 0, val trustedFriendCustomers: Long = 0,
    val qualifiedFriendCustomers: Long = 0, val todayNewCustomers: Long = 0, val todayQualifiedCustomers: Long = 0,
)
private data class LeadershipRankingDto(
    val membershipId: String, val displayName: String, val teamNames: String = "", val totalCustomers: Long = 0,
    val qualityACustomers: Long = 0, val todayCustomers: Long = 0, val taskCompletionRate: Double = 0.0,
)
private data class LeadershipSourceDto(val source: String, val customerCount: Long = 0, val share: Double = 0.0)
private data class LeadershipQualityDto(val code: String, val label: String, val customerCount: Long = 0, val share: Double = 0.0)
private data class LeadershipQualitySummaryDto(val levels: List<LeadershipQualityDto> = emptyList())
private data class LeadershipTaskSummaryDto(val completionRate: Double = 0.0)
private data class LeadershipOwnerDto(val displayName: String, val customerCount: Long = 0)
private data class LeadershipAClassDto(val customerCount: Long = 0, val owners: List<LeadershipOwnerDto> = emptyList())
private data class LeadershipSchoolDto(val featuredGrade: String = "", val institutionA: Long = 0, val institutionB: Long = 0)
private data class LeadershipTickerDto(val id: String, val priority: String = "NORMAL", val text: String)
private data class LeadershipDashboardDto(
    val generatedAt: String, val teams: List<LeadershipOptionDto> = emptyList(), val members: List<LeadershipOptionDto> = emptyList(),
    val headline: LeadershipHeadlineDto = LeadershipHeadlineDto(), val rankings: List<LeadershipRankingDto> = emptyList(),
    val sources: List<LeadershipSourceDto> = emptyList(), val customerQuality: LeadershipQualitySummaryDto = LeadershipQualitySummaryDto(),
    val taskSummary: LeadershipTaskSummaryDto = LeadershipTaskSummaryDto(), val featuredGrade: String = "",
    val aClassStale: LeadershipAClassDto = LeadershipAClassDto(), val schoolComparison: LeadershipSchoolDto = LeadershipSchoolDto(),
    val tickerItems: List<LeadershipTickerDto> = emptyList(),
)
private data class CustomerDto(
    val id: String, val ownerMembershipId: String? = null, val ownerDisplayName: String? = null,
    val teamId: String? = null, val visibility: String? = null,
    val fullName: String, val nickname: String? = null, val phone: String? = null, val wechatId: String? = null,
    val qqId: String? = null, val school: String? = null, val major: String? = null, val grade: String? = null,
    val city: String? = null, val highSchool: String? = null, val sourceLocation: String? = null,
    val identityType: String? = null, val focusDirections: Set<String> = emptySet(), val demandSummary: String? = null,
    val intentionLevel: String? = null, val urgencyLevel: String? = null, val verificationStatus: String? = null,
    val sourceDictionaryId: String? = null, val sourceChannel: String? = null, val currentStatus: String? = null,
    val ownerNotes: String? = null, val lastContactAt: String? = null, val nextFollowUpAt: String? = null,
    val friendAddedAt: String? = null, val friendTrustedAt: String? = null, val friendQualifiedAt: String? = null,
    val archivedAt: String? = null, val createdAt: String? = null, val updatedAt: String? = null, val version: Long = 0,
    val dealState: String? = null, val canRequestDeal: Boolean = false, val dealRequestedAt: String? = null,
    val tags: List<TagDto> = emptyList(),
)
private data class TagDto(val id: String, val name: String, val category: String? = null)
private data class CustomerUpsertDto(
    val fullName: String, val nickname: String?, val phone: String?, val wechatId: String?, val qqId: String?, val school: String?, val major: String?, val grade: String?,
    val city: String? = null, val highSchool: String? = null, val sourceLocation: String? = null, val identityType: String? = null,
    val focusDirections: Set<String> = emptySet(), val demandSummary: String?, val intentionLevel: String? = null, val urgencyLevel: String? = null,
    val verificationStatus: String? = null, val sourceDictionaryId: String? = null, val sourceChannel: String?, val currentStatus: String? = null,
    val ownerNotes: String? = null, val nextFollowUpAt: String? = null, val friendAddedAt: String? = null, val friendTrustedAt: String? = null,
    val friendQualifiedAt: String? = null, val ownerMembershipId: String? = null, val teamId: String? = null, val visibility: String? = null,
    val tagIds: Set<String> = emptySet(), val duplicateOverrideReason: String? = null, val expectedVersion: Long? = null,
) {
    companion object {
        fun from(draft: CustomerDraft, old: CustomerDto? = null) = CustomerUpsertDto(
            draft.name.trim(), draft.nickname.trim().ifBlank { null }, draft.phone.trim().ifBlank { null }, draft.wechat.trim().ifBlank { null },
            draft.qq.trim().ifBlank { null }, draft.school.trim().ifBlank { null }, draft.major.trim().ifBlank { null }, draft.grade.trim().ifBlank { null },
            city = old?.city, highSchool = old?.highSchool, sourceLocation = old?.sourceLocation, identityType = old?.identityType,
            focusDirections = old?.focusDirections ?: emptySet(), demandSummary = draft.demandSummary.trim().ifBlank { null }, intentionLevel = old?.intentionLevel,
            urgencyLevel = old?.urgencyLevel, verificationStatus = old?.verificationStatus, sourceDictionaryId = old?.sourceDictionaryId,
            sourceChannel = draft.source.trim().takeIf { it.isNotBlank() } ?: old?.sourceChannel, currentStatus = old?.currentStatus, ownerNotes = old?.ownerNotes,
            nextFollowUpAt = old?.nextFollowUpAt, friendAddedAt = old?.friendAddedAt, friendTrustedAt = old?.friendTrustedAt,
            friendQualifiedAt = old?.friendQualifiedAt, ownerMembershipId = old?.ownerMembershipId, teamId = old?.teamId,
            visibility = old?.visibility, tagIds = old?.tags?.map { it.id }?.toSet() ?: emptySet(), expectedVersion = old?.version,
        )
    }
}
private data class CollaborationMemberDto(val membershipId: String, val displayName: String, val teamNames: String? = null)
private data class CollaborationDto(
    val ownerMembershipId: String? = null,
    val canManage: Boolean = false,
    val canTransferOwnership: Boolean = false,
    val canEditProfile: Boolean = false,
    val canArchive: Boolean = false,
    val canEditTimeline: Boolean = false,
    val collaborators: List<CollaborationMemberDto> = emptyList(),
    val candidates: List<CollaborationMemberDto> = emptyList(),
    val transferCandidates: List<CollaborationMemberDto> = emptyList(),
)
private data class UpdateCollaboratorsDto(val membershipIds: Set<String>)
private data class TransferOwnershipDto(val targetMembershipId: String, val expectedVersion: Long, val reason: String?)
private data class DeadlineShadowDto(val computedDeadline: String? = null, val mode: String? = null, val noReplyExtensionUsed: Boolean = false)
private data class CustomerImportRowDto(val rowNumber: Int, val status: String, val messages: List<String> = emptyList())
private data class CustomerImportPreviewDto(val jobId: String, val filename: String, val format: String, val totalRows: Int, val readyRows: Int, val failedRows: Int, val rows: List<CustomerImportRowDto> = emptyList())
private data class CustomerImportReportDto(val jobId: String, val filename: String, val format: String, val status: String, val totalRows: Int, val createdCount: Int, val skippedCount: Int, val failedCount: Int, val messages: List<CustomerImportRowDto> = emptyList())
private data class DashboardStatsDto(val totalActive: Int = 0)
private data class DashboardDto(val stats: DashboardStatsDto, val dueToday: List<CustomerDto> = emptyList(), val overdue: List<CustomerDto> = emptyList(), val staleHighIntention: List<CustomerDto> = emptyList(), val recentUpdated: List<CustomerDto> = emptyList())
private data class TeamDataStatsDto(val memberCount: Long = 0, val activeCustomerCount: Long = 0, val todayNewCustomerCount: Long = 0, val todayGrade26LeadCount: Long = 0, val pendingLeadReviewCount: Long = 0, val todayReportCount: Long = 0, val xiaohongshuValidCount: Long = 0, val douyinValidCount: Long = 0)
private data class TeamDataMemberDto(val membershipId: String, val displayName: String, val username: String = "", val teamNames: String = "")
private data class TeamDataContentDto(val membershipId: String, val displayName: String, val xiaohongshuValid: Long = 0, val douyinValid: Long = 0)
private data class TeamDataLeadDto(val customerId: String)
private data class TeamDataOverviewDto(val members: List<TeamDataMemberDto> = emptyList(), val stats: TeamDataStatsDto = TeamDataStatsDto(), val contentProgress: List<TeamDataContentDto> = emptyList(), val leadReviewQueue: List<TeamDataLeadDto> = emptyList())
private data class MobileGreetingDto(val salutation: String, val subtitle: String, val generatedByAi: Boolean, val validUntil: String, val traceId: String? = null)
private data class MobileCustomerInsightDto(val content: String, val generatedAt: String, val timelineUpdatedAt: String? = null, val traceId: String? = null)
internal data class InteractionCreateDto(val contactedAt: String?, val channel: String, val summary: String, val details: String?, val outcome: String? = null, val verificationTodo: String? = null, val nextFollowUpAt: String? = null, val currentStatus: String? = null, val intentionLevel: String? = null, val verificationStatus: String? = null, val tagIds: Set<String>? = null, val interactionType: String, val sourceMessageId: String? = null, val tagUpdateRequested: Boolean = false)
private data class InteractionDto(val id: String, val customerId: String, val contactedAt: String, val channel: String, val summary: String, val details: String? = null, val createdByDisplayName: String? = null, val createdAt: String, val interactionType: String? = null)
private data class ReminderDto(val id: String, val customerId: String, val remindAt: String, val title: String, val status: String, val overdue: Boolean)
private data class ReminderUpdateDto(val remindAt: String? = null, val title: String? = null, val status: String? = null)
private data class DailyDto(val reportDate: String, val grade26NewValidLeads: Int, val deepCommunicationCount: Int, val pendingDeepCommunicationCount: Int, val performanceManaged: Boolean, val todayIssues: String? = null, val tomorrowPlan: String? = null, val submitted: Boolean)
private data class DailyUpsertDto(val deepCommunicationCount: Int, val todayIssues: String, val tomorrowPlan: String)
private data class NotificationInboxDto(val unreadCount: Long, val items: List<NotificationDto> = emptyList())
private data class NotificationDto(val id: String, val title: String, val summary: String, val read: Boolean, val createdAt: String)
private data class NotificationReadAllDto(val updatedCount: Int = 0)
private data class AssignmentDto(val id: String, val status: String, val resultSummary: String? = null, val memberDisplayName: String? = null, val version: Long = 0)
private data class TaskDto(val id: String, val title: String, val dueAt: String? = null, val status: String, val claimable: Boolean = false, val myAssignment: AssignmentDto? = null, val version: Long = 0)
private data class TaskUpdateDto(val status: String, val resultSummary: String?, val evidenceUrl: String?)
private data class TaskMatrixRowDto(val task: TaskDto, val assignments: List<AssignmentDto> = emptyList()) {
    val status: String get() = task.status
}
private data class TaskReviewDto(val status: String, val feedback: String?)
private data class AdminTaskCreateDto(
    val title: String,
    val description: String,
    val distributionType: String,
    val startAt: String,
    val dueAt: String,
    val maxClaimants: Int?,
    val assigneeIds: Set<String>?,
)
private data class ScoreDto(val finalPoints: Double = 0.0, val baseline: Double = 0.0)
private data class InteractionOptionDto(val interactionId: String, val customerName: String, val summary: String)
private data class PerformanceTodayDto(
    val score: ScoreDto,
    val pendingClaims: Long = 0,
    val pendingActivities: Long = 0,
    val availableInteractions: List<InteractionOptionDto> = emptyList(),
    val activities: List<PerformanceAdminActivityDto> = emptyList(),
    val ledger: List<LedgerDto> = emptyList(),
)
private data class LedgerDto(val points: Double, val description: String)
private data class PerformanceClaimCreateDto(val interactionId: String, val requestedType: String)
private data class PerformanceClaimDto(val id: String)
private data class PerformanceEvidenceDto(val id: String? = null)
private data class ConsultantDto(val membershipId: String, val displayName: String, val roles: List<String> = emptyList(), val teams: List<String> = emptyList())
private data class ConsultationFailureReasonDto(val id: String, val code: String, val label: String, val active: Boolean, val sortOrder: Int)
private data class ProductReleaseDto(val id: String, val version: String, val title: String, val summary: String, val releasedAt: String, val current: Boolean, val acknowledged: Boolean)
private data class ConsultationCreateDto(val customerId: String, val consultantMembershipId: String, val sourceInteractionIds: List<String>, val consultationReason: String, val customerQuotes: String, val goalsMotivation: String, val actionsTaken: String, val mainConcerns: String, val decisionParticipants: String? = null, val decisionParticipantConsent: Boolean = false, val productFit: String, val suggestedFocus: String, val explicitTaboos: String, val expectedResult: String, val scheduledStart: String, val scheduledEnd: String)
private data class ConsultationCompleteDto(val expectedVersion: Long, val resultSummary: String, val continueFollowUp: Boolean, val nextStep: String, val nextOwnerMembershipId: String, val nextFollowUpAt: String? = null, val failureReasonId: String? = null, val failureReasonOther: String? = null)
private data class ConsultationVersionDto(val expectedVersion: Long)
private data class ConsultationRejectDto(val expectedVersion: Long, val reason: String)
private data class ConsultationEndDto(val expectedVersion: Long, val actualEndedAt: String)
private data class ConsultationDto(val id: String, val customerName: String, val createdByMembershipId: String, val consultantMembershipId: String, val status: String, val nextStep: String? = null, val version: Long)
private data class DirectionDto(val id: String, val title: String)
private data class ContentOverviewDto(val directions: List<DirectionDto> = emptyList(), val myPosts: List<ContentPostDto> = emptyList(), val reviewQueue: List<ContentPostDto> = emptyList())
private data class ContentPostCreateDto(val platform: String, val publishDate: String, val directionId: String, val topic: String, val contentUrl: String, val viewCount: Long, val likeCount: Long, val favoriteCount: Long, val originalDeclared: Boolean)
private data class ContentMetricsDto(val viewCount: Long, val likeCount: Long, val favoriteCount: Long)
private data class ContentPostDto(val id: String, val membershipId: String = "", val memberDisplayName: String? = null, val platform: String, val directionTitle: String? = null, val topic: String, val contentUrl: String? = null, val viewCount: Long = 0, val likeCount: Long = 0, val favoriteCount: Long = 0, val createdAt: String, val reviewStatus: String = "", val version: Long = 0)
private data class CircleSenderDto(val membershipId: String, val displayName: String)
private data class AnnouncementDto(
    val id: String,
    val title: String,
    val content: String,
    val priority: String = "NORMAL",
    val pinned: Boolean,
    val showOnLeadershipScreen: Boolean = false,
    val publishedAt: String,
    val status: String = "PUBLISHED",
    val version: Long = 0,
)
private data class AdminAnnouncementUpsertDto(val title: String, val content: String, val priority: String, val pinned: Boolean, val showOnLeadershipScreen: Boolean)
private data class AssistantAvailabilityDto(val available: Boolean, val provider: String = "", val model: String = "")
private data class AssistantParseDto(val text: String)
private data class AgentGrantDto(val id: String, val clientName: String, val scopes: List<String> = emptyList(), val status: String, val lastUsedAt: String? = null, val displayName: String = "")
private data class PoolEntryDto(val id: String, val customerName: String? = null, val school: String? = null, val grade: String? = null, val demandSummary: String? = null)
private data class PoolClaimDto(val idempotencyKey: String)
private data class PoolClaimResultDto(val claimId: String, val customerId: String, val status: String)
private data class SecondLineDto(val id: String, val customerName: String? = null, val school: String? = null, val grade: String? = null, val demandSummary: String? = null, val requiredCount: Int = 0, val claimedCount: Int = 0)
private data class SecondLineClaimResultDto(val recruitmentId: String, val customerId: String, val activeSecondLineCount: Int)

private data class MarketReviewDto(
    val classificationId: String,
    val customerId: String,
    val customerName: String,
    val level: String,
    val submittedByDisplayName: String,
    val reviewStatus: String,
    val createdAt: String,
)
private data class MarketAnomalyDto(
    val membershipId: String,
    val displayName: String,
    val aRate: Double = 0.0,
    val teamMedianRate: Double = 0.0,
    val abnormal: Boolean = false,
)
private data class CrossSaleDto(
    val customerId: String,
    val customerName: String,
    val customerQuality: String? = null,
    val teamName: String? = null,
    val ownerDisplayName: String? = null,
    val collaboratorCount: Int = 0,
    val collaboratorNames: String = "",
    val crossSale: Boolean = false,
    val businessCycleStatus: String? = null,
    val statusMarkedAt: String? = null,
)
private data class MarketClassificationDto(
    val id: String,
    val customerId: String,
    val level: String,
    val recognitionReason: String,
    val potentialDemand: String,
    val aSignals: Set<String> = emptySet(),
    val evidenceInteractionIds: Set<String> = emptySet(),
    val revision: Int = 1,
    val createdAt: String? = null,
)
private data class MarketClassificationRequestDto(
    val level: String,
    val recognitionReason: String,
    val potentialDemand: String,
    val aSignals: Set<String>,
    val evidenceInteractionIds: Set<String>,
    val correctionReason: String?,
)
private data class MarketReviewRequestDto(
    val decision: String,
    val note: String? = null,
    val correction: MarketClassificationRequestDto? = null,
)
private data class ContentReviewDto(val status: String, val reason: String? = null)
private data class DailyCalendarMemberDto(val membershipId: String, val displayName: String)
private data class DailyCalendarDayDto(
    val date: String,
    val reportingDay: Boolean = false,
    val expectedCount: Int = 0,
    val submittedCount: Int = 0,
    val submittedMembers: List<DailyCalendarMemberDto> = emptyList(),
    val missingMembers: List<DailyCalendarMemberDto> = emptyList(),
)
private data class DailyCalendarDto(
    val month: String,
    val today: String,
    val expectedMemberCount: Int = 0,
    val days: List<DailyCalendarDayDto> = emptyList(),
)
private data class PerformanceAdminClaimDto(
    val id: String,
    val memberDisplayName: String,
    val customerName: String,
    val performanceDate: String,
    val requestedType: String,
    val status: String,
    val version: Long = 0,
)
private data class PerformanceClaimReviewDto(
    val decision: String,
    val approvedType: String?,
    val reason: String?,
    val expectedVersion: Long,
)
private data class PerformanceAdminActivityDto(
    val id: String,
    val organizerMembershipId: String,
    val organizerDisplayName: String,
    val eventDate: String,
    val title: String,
    val goal: String = "",
    val expectedParticipants: Int = 0,
    val actualParticipants: Int? = null,
    val status: String,
    val version: Long = 0,
)
private data class PerformanceActivityReviewDto(val decision: String, val reason: String?, val expectedVersion: Long)
private data class PerformanceActivityCreateDto(val eventDate: String, val title: String, val goal: String, val expectedParticipants: Int, val description: String)
private data class PerformanceActivityCompleteDto(val actualParticipants: Int, val expectedVersion: Long)
private data class PerformanceActivityAllocationDto(val membershipId: String, val points: Double)
private data class PerformanceActivityAllocateDto(val verifiedParticipants: Int, val allocations: List<PerformanceActivityAllocationDto>, val expectedVersion: Long)
private data class PerformanceHistoryDto(val date: String, val earnedPoints: Double, val finalPoints: Double, val baseline: Double, val settled: Boolean)
private data class PerformanceLeaderboardDto(val rank: Int, val displayName: String, val teamName: String, val finalPoints: Double, val achievedDays: Int, val scoredDays: Int)
private data class PerformanceRuleDto(
    val id: String = "", val versionNo: Int = 0, val effectiveFrom: String = "", val dailyBaseline: Double = 0.0,
    val calendarMode: String = "", val submissionOpensAt: String = "", val submissionClosesAt: String = "",
    val shortfallMode: String = "", val ordinaryPoints: Double = 0.0, val deepPoints: Double = 0.0,
    val eventTiers: List<PerformanceTierDto> = emptyList(),
)
private data class PerformanceTierDto(val label: String, val minParticipants: Int, val maxParticipants: Int?, val points: Double)
private data class PerformanceRuleCreateDto(
    val effectiveFrom: String, val dailyBaseline: Double, val calendarMode: String, val weekdays: Set<Int>,
    val includedDates: Set<String> = emptySet(), val excludedDates: Set<String> = emptySet(),
    val submissionOpensAt: String, val submissionClosesAt: String, val shortfallMode: String,
    val ordinaryPoints: Double, val deepPoints: Double, val ordinaryDailyCap: Int?, val deepDailyCap: Int?,
    val eventTiers: List<PerformanceTierDto>,
)
private data class PerformanceParticipantDto(
    val id: String = "", val membershipId: String = "", val displayName: String, val startsOn: String = "",
    val endsOn: String? = null, val activeToday: Boolean = false, val version: Long = 0,
)
private data class PerformanceParticipantCreateDto(val membershipId: String, val startsOn: String, val endsOn: String?)
private data class PerformanceParticipantUpdateDto(val endsOn: String?, val expectedVersion: Long)
private data class PerformanceDayDto(val date: String, val status: String, val pendingClaims: Long = 0, val pendingActivities: Long = 0, val version: Long = 0)
private data class PerformanceReopenDayDto(val reason: String, val expectedVersion: Long)
private data class AdminTagDto(val id: String, val name: String, val category: String, val color: String, val system: Boolean = false)
private data class AdminTagCreateDto(val name: String, val category: String, val color: String)
private data class AdminDictionaryDto(val id: String, val type: String, val code: String, val label: String, val sortOrder: Int = 0)
private data class AdminDictionaryCreateDto(val type: String, val code: String, val label: String, val sortOrder: Int)
private data class AssistantSettingsDto(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val apiKeyConfigured: Boolean = false,
    val version: Long = 0,
)
private data class AdminAssistantUpdateDto(
    val enabled: Boolean,
    val baseUrl: String,
    val model: String,
    val apiKey: String?,
    val clearApiKey: Boolean,
    val expectedVersion: Long,
)
private data class IntelligenceSettingsDto(val backfillPaused: Boolean = false, val embeddingModel: String = "", val version: Long = 0)
private data class IntelligenceRuntimeDto(
    val pressureLabel: String = "UNKNOWN",
    val systemCpuPercent: Double? = null,
    val systemMemoryPercent: Double? = null,
    val pendingJobs: Long = 0,
    val runningJobs: Long = 0,
    val failedJobs: Long = 0,
)
private data class EnrichmentJobDto(
    val id: String,
    val filename: String,
    val status: String,
    val totalRows: Int = 0,
    val readyRows: Int = 0,
    val updatedRows: Int = 0,
    val skippedRows: Int = 0,
    val conflictRows: Int = 0,
)
private data class EnrichmentPreviewDto(val job: EnrichmentJobDto, val rows: List<Any> = emptyList())
private data class AuditRowDto(
    val action: String,
    val entityType: String,
    val entityId: String,
    val outcome: String,
    val traceId: String? = null,
    val actorDisplayName: String? = null,
    val createdAt: String,
)
private data class WebhookDto(
    val id: String,
    val name: String,
    val eventTypes: Set<String> = emptySet(),
    val active: Boolean = false,
    val secretConfigured: Boolean = false,
    val version: Long = 0,
)
private data class AdminWebhookCreateDto(
    val name: String,
    val webhookUrl: String,
    val eventTypes: Set<String>,
    val active: Boolean,
    val expectedVersion: Long?,
)
private data class WebhookDeliveryDto(
    val id: String,
    val channelName: String,
    val title: String,
    val status: String,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
)
private data class LeadershipSettingsDto(
    val animationsEnabled: Boolean = true,
    val standbyEnabled: Boolean = true,
    val idleTimeoutMinutes: Int = 5,
    val sceneDurationSeconds: Int = 15,
    val motionIntensity: String = "NORMAL",
    val featuredGrade: String = "2026",
    val eventDurationSeconds: Int = 8,
    val sceneOrder: List<String> = emptyList(),
    val enabledScenes: List<String> = emptyList(),
    val enabledEvents: List<String> = emptyList(),
    val version: Long = 0,
)
private data class LeadershipSettingsUpdateDto(
    val animationsEnabled: Boolean, val standbyEnabled: Boolean, val idleTimeoutMinutes: Int,
    val sceneDurationSeconds: Int, val eventDurationSeconds: Int, val motionIntensity: String,
    val sceneOrder: List<String>, val enabledScenes: List<String>, val enabledEvents: List<String>,
    val featuredGrade: String, val expectedVersion: Long,
)
private data class FollowUpPolicyDto(
    val aClassStaleEnabled: Boolean, val staleAfterHours: Int, val notifyAt: String, val qqEnabled: Boolean,
    val abAutoReleaseEnabled: Boolean, val version: Long,
)
private data class FollowUpPolicyUpdateDto(
    val aClassStaleEnabled: Boolean, val staleAfterHours: Int, val notifyAt: String, val qqEnabled: Boolean,
    val abAutoReleaseEnabled: Boolean, val expectedVersion: Long,
)
private data class ConsultationAdminSettingsDto(
    val businessStart: String, val businessEnd: String, val minimumLeadMinutes: Int,
    val allowedDurations: List<Int>, val dailyCapacity: Int, val feedbackDueHours: Int, val version: Long,
)
private data class ConsultationAdminSettingsUpdateDto(
    val businessStart: String, val businessEnd: String, val minimumLeadMinutes: Int,
    val allowedDurations: List<Int>, val dailyCapacity: Int, val feedbackDueHours: Int, val expectedVersion: Long,
)
private data class ConsultationReasonSaveDto(val code: String, val label: String, val active: Boolean, val sortOrder: Int)
private data class AdminManualPerformanceDto(
    val membershipId: String,
    val performanceDate: String,
    val entryType: String,
    val title: String,
    val reason: String,
    val points: Double,
    val relatedLedgerId: String?,
)

private fun AdminOverviewDto.toModel() = AdminOverview(
    pendingDealRequests, pendingClassificationReviews, dueOwnershipDeadlines, openPoolEntries,
    pendingSecondLineRecruitments, pendingPerformanceClaims, pendingPerformanceActivities,
    overdueConsultationFeedback, failedIntelligenceJobs, generatedAt.toInstantOrNull() ?: Instant.now(),
)
private fun AdminMemberDto.toModel() = AdminMember(membershipId, userId, username, displayName, status, roles, teams, teamIds, ownedCustomerCount)
private fun AdminTeamDto.toModel() = AdminTeam(id, name, active, leaderMembershipId, leaderDisplayName, memberCount)
private fun AdminRegistrationCodeDto.toModel() = AdminRegistrationCode(
    id = id,
    codeHint = codeHint,
    maxUses = maxUses,
    usedCount = usedCount,
    expiresAt = expiresAt.toInstantOrNull(),
    status = status ?: if (active == true) "ACTIVE" else "DISABLED",
    createdAt = createdAt.toInstantOrNull() ?: Instant.now(),
    plainCode = code,
)
private fun LeadershipDashboardDto.toModel() = LeadershipDashboard(
    generatedAt = generatedAt.toInstantOrNull() ?: Instant.now(),
    headline = LeadershipHeadline(
        headline.totalCustomers, headline.addedFriendCustomers, headline.trustedFriendCustomers,
        headline.qualifiedFriendCustomers, headline.todayNewCustomers, headline.todayQualifiedCustomers,
    ),
    teams = teams.mapNotNull { row -> row.id?.let { LeadershipFilterOption(it, row.name.orEmpty()) } },
    members = members.mapNotNull { row -> row.membershipId?.let { LeadershipFilterOption(it, row.displayName.orEmpty()) } },
    rankings = rankings.map { LeadershipRanking(it.membershipId, it.displayName, it.teamNames, it.totalCustomers, it.qualityACustomers, it.todayCustomers, it.taskCompletionRate) },
    sources = sources.map { LeadershipSource(it.source, it.customerCount, it.share) },
    quality = customerQuality.levels.map { LeadershipQuality(it.code, it.label, it.customerCount, it.share) },
    taskCompletionRate = taskSummary.completionRate,
    staleAClassCustomers = aClassStale.customerCount,
    staleAClassOwners = aClassStale.owners.map { it.displayName to it.customerCount },
    featuredGrade = schoolComparison.featuredGrade.ifBlank { featuredGrade },
    institutionACustomers = schoolComparison.institutionA,
    institutionBCustomers = schoolComparison.institutionB,
    tickerItems = tickerItems.map { LeadershipTicker(it.id, it.priority, it.text) },
)

private fun CustomerDto.toModel(): Customer {
    val quality = tags.firstOrNull { it.category == "客户质量" }?.name?.let { name ->
        when { name.startsWith("A") -> CustomerQuality.A; name.startsWith("B") -> CustomerQuality.B; else -> CustomerQuality.C }
    } ?: CustomerQuality.C
    return Customer(id, fullName, nickname.orEmpty(), school.orEmpty(), major.orEmpty(), grade.orEmpty(), phone.orEmpty(), wechatId.orEmpty(), qqId.orEmpty(), sourceChannel.orEmpty(), ownerDisplayName.orEmpty(), quality = quality, intention = intentionLevel.orEmpty(), status = currentStatus.orEmpty(), demandSummary = demandSummary.orEmpty(), lastContactAt = lastContactAt.toInstantOrNull(), nextFollowUpAt = nextFollowUpAt.toInstantOrNull(), dealState = runCatching { DealState.valueOf(dealState ?: "NONE") }.getOrDefault(DealState.NONE), canRequestDeal = canRequestDeal, dealRequestedAt = dealRequestedAt.toInstantOrNull(), archivedAt = archivedAt.toInstantOrNull(), version = version)
}
private fun CollaborationDto.toModel() = CustomerCollaboration(
    ownerMembershipId, canManage, canTransferOwnership, canEditProfile, canArchive, canEditTimeline,
    collaborators.map { CustomerCollaborationMember(it.membershipId, it.displayName, it.teamNames.orEmpty()) },
    candidates.map { CustomerCollaborationMember(it.membershipId, it.displayName, it.teamNames.orEmpty()) },
    transferCandidates.map { CustomerCollaborationMember(it.membershipId, it.displayName, it.teamNames.orEmpty()) },
)
private fun MarketClassificationDto.toCustomerClassification() = CustomerClassification(
    id, revision, runCatching { CustomerQuality.valueOf(level) }.getOrDefault(CustomerQuality.C), recognitionReason,
    potentialDemand, aSignals.toList(), evidenceInteractionIds.toList(), createdAt.toInstantOrNull() ?: Instant.now(),
)
private fun CustomerImportPreviewDto.toModel() = CustomerImportState(jobId, filename, format, "PREVIEWED", totalRows, readyRows, failedRows, messages = rows.flatMap { row -> row.messages.map { "第 ${row.rowNumber} 行：$it" } })
private fun CustomerImportReportDto.toModel() = CustomerImportState(jobId, filename, format, status, totalRows, failedRows = failedCount, createdRows = createdCount, skippedRows = skippedCount, messages = messages.flatMap { row -> row.messages.map { "第 ${row.rowNumber} 行：$it" } })
private fun InteractionDto.toModel() = Interaction(id, customerId, Instant.parse(contactedAt), Instant.parse(createdAt), runCatching { InteractionType.valueOf(interactionType ?: "INTERNAL_NOTE") }.getOrDefault(InteractionType.INTERNAL_NOTE), channel, summary, details.orEmpty(), createdByDisplayName.orEmpty())
private fun ReminderDto.toModel(customerName: String) = Reminder(id, customerId, customerName, title, Instant.parse(remindAt), overdue, status == "COMPLETED")
private fun DailyDto.toModel() = DailySummary(LocalDate.parse(reportDate), grade26NewValidLeads, deepCommunicationCount, performanceManaged, todayIssues.orEmpty(), tomorrowPlan.orEmpty(), submitted)
private fun NotificationDto.toModel() = NotificationItem(id, title, summary, Instant.parse(createdAt), !read)
private fun TaskDto.toModel() = WorkTask(id, title, dueAt.toInstantOrNull(), when { myAssignment?.status == "SUBMITTED" -> WorkTaskStatus.SUBMITTED; myAssignment?.status == "ACCEPTED" || myAssignment?.status == "COMPLETED" -> WorkTaskStatus.ACCEPTED; myAssignment != null -> WorkTaskStatus.CLAIMED; claimable -> WorkTaskStatus.OPEN; else -> WorkTaskStatus.ACCEPTED }, myAssignment?.resultSummary.orEmpty())
private fun PerformanceTodayDto.toModel(
    history: List<PerformanceHistoryDto> = emptyList(),
    leaderboard: List<PerformanceLeaderboardDto> = emptyList(),
    activities: List<PerformanceAdminActivityDto> = emptyList(),
) = PerformanceSnapshot(
    score.finalPoints, score.baseline, pendingClaims.toDouble(),
    ledger.map { "${it.description} ${it.points} 分" },
    availableInteractions.map { PerformanceInteractionOption(it.interactionId, it.customerName, it.summary) },
    history.map { PerformanceHistoryPoint(it.date, it.earnedPoints, it.finalPoints, it.baseline, it.settled) },
    leaderboard.map { PerformanceLeaderboardRow(it.rank, it.displayName, it.teamName, it.finalPoints, it.achievedDays, it.scoredDays) },
    activities.map { PerformanceActivityItem(it.id, it.title, it.eventDate, it.goal, it.expectedParticipants, it.actualParticipants, it.status, it.version, true) },
)

private fun assistantDisplayValue(value: Any?): String = when (value) {
    null -> "未确认"
    is String -> value.ifBlank { "未确认" }
    is Collection<*> -> value.joinToString("\n") { assistantDisplayValue(it) }.ifBlank { "未确认" }
    is Map<*, *> -> Gson().toJson(value)
    else -> value.toString()
}

private suspend fun ResponseBody.readAssistantSse(): String = withContext(Dispatchers.IO) {
    val output = StringBuilder()
    var event = ""
    charStream().buffered().useLines { lines ->
        lines.forEach { line ->
            when {
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:") && event == "delta" -> output.append(line.removePrefix("data:").trimStart())
            }
        }
    }
    output.toString()
}
private fun ConsultationDto.toModel(currentMembershipId: String? = null) = ConsultationItem(id, customerName, when (status) {
    "PENDING_ACCEPTANCE" -> ConsultationStatus.WAITING_ACCEPTANCE
    "SCHEDULED" -> ConsultationStatus.SCHEDULED
    "AWAITING_FEEDBACK" -> ConsultationStatus.WAITING_RESULT
    "COMPLETED" -> ConsultationStatus.COMPLETED
    else -> ConsultationStatus.DRAFT
}, nextStep.orEmpty(), version, canAccept = status == "PENDING_ACCEPTANCE" && consultantMembershipId == currentMembershipId, canEnd = status == "SCHEDULED" && consultantMembershipId == currentMembershipId)
private fun ContentPostDto.toModel() = ContentTaskItem(id, directionTitle ?: topic, platform, true, "浏览 $viewCount · 点赞 $likeCount · 收藏 $favoriteCount")
private fun ContentPostDto.toCircle() = CirclePost(id, memberDisplayName.orEmpty(), topic, Instant.parse(createdAt), membershipId, platform, contentUrl.orEmpty(), "浏览 $viewCount · 点赞 $likeCount · 收藏 $favoriteCount")
private fun AnnouncementDto.toModel() = AnnouncementItem(id, title, content, pinned, Instant.parse(publishedAt))
private fun AgentGrantDto.toModel() = AgentAuthorization(id, clientName, scopes, lastUsedAt.toInstantOrNull())
private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
