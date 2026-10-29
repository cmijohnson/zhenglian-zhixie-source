package com.eagleslab.yingge.model

import java.time.Instant
import java.time.LocalDate

enum class UserRole { ORG_ADMIN, TEAM_MANAGER, MEMBER, VIEWER }

enum class AppMode { EMPLOYEE, ADMIN }

data class SessionUser(
    val userId: String,
    val membershipId: String,
    val displayName: String,
    val roles: Set<UserRole>,
    val passwordChangeRequired: Boolean = false,
) {
    val canWrite: Boolean get() = roles.any { it != UserRole.VIEWER }
    val isAdmin: Boolean get() = UserRole.ORG_ADMIN in roles
}

data class MobileAppBootstrap(
    val user: SessionUser,
    val serverTime: Instant,
    val organizationTimeZone: String,
    val availableModes: Set<AppMode>,
    val capabilities: Set<String>,
    val urgentReminders: Int,
    val unreadNotifications: Int,
) {
    fun can(capability: String): Boolean = capability in capabilities
}

data class CustomerFilter(
    val search: String = "",
    val status: String = "",
    val intention: String = "",
    val grade: String = "",
    val school: String = "",
    val ownerMembershipId: String = "",
    val includeArchived: Boolean = false,
    val page: Int = 0,
    val size: Int = 30,
    val sort: String = "updatedDesc",
)

data class CustomerPage(
    val items: List<Customer>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class AdminOverview(
    val pendingDealRequests: Long = 0,
    val pendingClassificationReviews: Long = 0,
    val dueOwnershipDeadlines: Long = 0,
    val openPoolEntries: Long = 0,
    val pendingSecondLineRecruitments: Long = 0,
    val pendingPerformanceClaims: Long = 0,
    val pendingPerformanceActivities: Long = 0,
    val overdueConsultationFeedback: Long = 0,
    val failedIntelligenceJobs: Long = 0,
    val generatedAt: Instant = Instant.now(),
)

data class AdminMember(
    val membershipId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val status: String,
    val roles: Set<String>,
    val teams: Set<String>,
    val teamIds: Set<String>,
    val ownedCustomerCount: Long,
)

data class AdminTeam(
    val id: String,
    val name: String,
    val active: Boolean,
    val leaderMembershipId: String? = null,
    val leaderDisplayName: String? = null,
    val memberCount: Int = 0,
)

data class AdminRegistrationCode(
    val id: String,
    val codeHint: String,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: Instant? = null,
    val status: String,
    val createdAt: Instant,
    /** Only populated once, directly after creation. Never persisted by the app. */
    val plainCode: String? = null,
)

data class AdminNativeItem(
    val id: String,
    val title: String,
    val details: String,
    val status: String = "",
    val version: Long = 0,
    val actions: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

data class AdminNativeModule(
    val key: String,
    val title: String,
    val summary: String,
    val metrics: List<Pair<String, String>> = emptyList(),
    val items: List<AdminNativeItem> = emptyList(),
    /** Non-secret form defaults returned by typed API adapters. */
    val configuration: Map<String, String> = emptyMap(),
)

sealed interface AdminNativeCommand { val moduleKey: String }
data class CreateAdminTaskCommand(
    val title: String,
    val description: String,
    val distributionType: String,
    val dueAt: String,
    val assigneeIds: Set<String> = emptySet(),
) : AdminNativeCommand { override val moduleKey = "tasks_admin" }
data class CreateAdminAnnouncementCommand(
    val title: String,
    val content: String,
    val priority: String,
    val pinned: Boolean,
) : AdminNativeCommand { override val moduleKey = "content_admin" }
data class UpdateAdminAnnouncementCommand(
    val id: String,
    val title: String,
    val content: String,
    val priority: String,
    val pinned: Boolean,
    val showOnLeadershipScreen: Boolean,
) : AdminNativeCommand { override val moduleKey = "content_admin" }
data class CorrectAdminClassificationCommand(
    val classificationId: String,
    val customerId: String,
    val correctedLevel: String,
    val correctionNote: String,
) : AdminNativeCommand { override val moduleKey = "market" }
data class CreateAdminTagCommand(val name: String, val category: String, val color: String) : AdminNativeCommand {
    override val moduleKey = "dictionaries"
}
data class CreateAdminDictionaryCommand(val type: String, val code: String, val label: String, val sortOrder: Int) : AdminNativeCommand {
    override val moduleKey = "dictionaries"
}
data class UpdateAdminAssistantCommand(
    val enabled: Boolean,
    val baseUrl: String,
    val model: String,
    val apiKey: String?,
    val clearApiKey: Boolean,
    val expectedVersion: Long,
) : AdminNativeCommand { override val moduleKey = "ai_settings" }
data class CreateAdminWebhookCommand(val name: String, val webhookUrl: String, val eventTypes: Set<String>) : AdminNativeCommand {
    override val moduleKey = "webhooks"
}
data class PreviewAdminEnrichmentCommand(val file: EvidenceUpload) : AdminNativeCommand { override val moduleKey = "enrichment" }
data class CreateAdminManualPerformanceCommand(
    val membershipId: String,
    val performanceDate: String,
    val entryType: String,
    val title: String,
    val reason: String,
    val points: Double,
) : AdminNativeCommand { override val moduleKey = "performance_admin" }

data class CreateAdminPerformanceRuleCommand(
    val effectiveFrom: String,
    val dailyBaseline: Double,
    val calendarMode: String,
    val weekdays: Set<Int>,
    val submissionOpensAt: String,
    val submissionClosesAt: String,
    val shortfallMode: String,
    val ordinaryPoints: Double,
    val deepPoints: Double,
    val ordinaryDailyCap: Int?,
    val deepDailyCap: Int?,
    val eventTiers: List<PerformanceTierDraft>,
) : AdminNativeCommand { override val moduleKey = "performance_admin" }

data class PerformanceTierDraft(val label: String, val minParticipants: Int, val maxParticipants: Int?, val points: Double)

data class AddAdminPerformanceParticipantCommand(
    val membershipId: String,
    val startsOn: String,
    val endsOn: String?,
) : AdminNativeCommand { override val moduleKey = "performance_admin" }

data class PerformanceAllocationDraft(val membershipId: String, val points: Double)
data class AllocateAdminPerformanceActivityCommand(
    val activityId: String,
    val verifiedParticipants: Int,
    val allocations: List<PerformanceAllocationDraft>,
    val expectedVersion: Long,
) : AdminNativeCommand { override val moduleKey = "performance_admin" }

data class SettleAdminPerformanceDayCommand(val date: String, val expectedVersion: Long) : AdminNativeCommand {
    override val moduleKey = "performance_admin"
}

data class ReopenAdminPerformanceDayCommand(val date: String, val reason: String, val expectedVersion: Long) : AdminNativeCommand {
    override val moduleKey = "performance_admin"
}

data class UpdateAdminFollowUpPolicyCommand(
    val aClassStaleEnabled: Boolean,
    val staleAfterHours: Int,
    val notifyAt: String,
    val qqEnabled: Boolean,
    val abAutoReleaseEnabled: Boolean,
    val expectedVersion: Long,
) : AdminNativeCommand { override val moduleKey = "followup_policy" }

data class UpdateAdminConsultationSettingsCommand(
    val businessStart: String,
    val businessEnd: String,
    val minimumLeadMinutes: Int,
    val allowedDurations: List<Int>,
    val dailyCapacity: Int,
    val feedbackDueHours: Int,
    val expectedVersion: Long,
) : AdminNativeCommand { override val moduleKey = "consultation_settings" }

data class SaveAdminConsultationReasonCommand(
    val id: String?,
    val code: String,
    val label: String,
    val active: Boolean,
    val sortOrder: Int,
) : AdminNativeCommand { override val moduleKey = "consultation_settings" }

data class UpdateAdminLeadershipSettingsCommand(
    val animationsEnabled: Boolean,
    val standbyEnabled: Boolean,
    val idleTimeoutMinutes: Int,
    val sceneDurationSeconds: Int,
    val eventDurationSeconds: Int,
    val motionIntensity: String,
    val sceneOrder: List<String>,
    val enabledScenes: List<String>,
    val enabledEvents: List<String>,
    val featuredGrade: String,
    val expectedVersion: Long,
) : AdminNativeCommand { override val moduleKey = "leadership_settings" }

data class LeadershipHeadline(
    val totalCustomers: Long = 0,
    val addedFriendCustomers: Long = 0,
    val trustedFriendCustomers: Long = 0,
    val qualifiedFriendCustomers: Long = 0,
    val todayNewCustomers: Long = 0,
    val todayQualifiedCustomers: Long = 0,
)

data class LeadershipRanking(
    val membershipId: String,
    val displayName: String,
    val teamNames: String,
    val totalCustomers: Long,
    val qualityACustomers: Long,
    val todayCustomers: Long,
    val taskCompletionRate: Double,
)

data class LeadershipSource(
    val source: String,
    val customerCount: Long,
    val share: Double,
)

data class LeadershipQuality(val code: String, val label: String, val customerCount: Long, val share: Double)
data class LeadershipTicker(val id: String, val priority: String, val text: String)
data class LeadershipFilterOption(val id: String, val label: String)

data class LeadershipDashboard(
    val generatedAt: Instant,
    val headline: LeadershipHeadline,
    val teams: List<LeadershipFilterOption>,
    val members: List<LeadershipFilterOption>,
    val rankings: List<LeadershipRanking>,
    val sources: List<LeadershipSource>,
    val quality: List<LeadershipQuality>,
    val taskCompletionRate: Double,
    val staleAClassCustomers: Long,
    val staleAClassOwners: List<Pair<String, Long>>,
    val featuredGrade: String,
    val institutionACustomers: Long,
    val institutionBCustomers: Long,
    val tickerItems: List<LeadershipTicker>,
)

data class RegistrationTeam(val id: String, val name: String)

data class RegistrationOptions(val teams: List<RegistrationTeam>)

/**
 * A mobile module may be fully usable, read-only, or unavailable until the
 * server advertises the corresponding employee API capability.
 */
enum class ModuleAvailability { AVAILABLE, READ_ONLY, OFFLINE }

data class BusinessModule(
    val key: String,
    val title: String,
    val description: String,
    val availability: ModuleAvailability,
    val items: List<String> = emptyList(),
    val actionItems: List<BusinessModuleActionItem> = emptyList(),
    val errorMessage: String? = null,
)

data class BusinessModuleActionItem(
    val id: String,
    val title: String,
    val details: String,
    val actionLabel: String,
)

fun failedBusinessModule(key: String, message: String): BusinessModule {
    val title = when (key) {
        "pool" -> "客户公共池"
        "second_line" -> "A 类二线协作"
        "assistant" -> "智能助手"
        "releases" -> "更新日志"
        else -> "功能模块"
    }
    return BusinessModule(
        key = key,
        title = title,
        description = "本次加载未完成，可以在网络恢复后重试。",
        availability = ModuleAvailability.OFFLINE,
        errorMessage = message,
    )
}

enum class CustomerQuality { A, B, C }

data class Customer(
    val id: String,
    val name: String,
    val nickname: String = "",
    val school: String = "",
    val major: String = "",
    val grade: String = "",
    val phone: String = "",
    val wechat: String = "",
    val qq: String = "",
    val source: String = "",
    val ownerName: String = "",
    val collaboratorNames: List<String> = emptyList(),
    val quality: CustomerQuality = CustomerQuality.C,
    val intention: String = "一般",
    val status: String = "跟进中",
    val demandSummary: String = "",
    val lastContactAt: Instant? = null,
    val nextFollowUpAt: Instant? = null,
    val dealState: DealState = DealState.NONE,
    val canRequestDeal: Boolean = false,
    val dealRequestedAt: Instant? = null,
    val archivedAt: Instant? = null,
    val version: Long = 0,
)

data class CustomerDraft(
    val id: String? = null,
    val name: String = "",
    val nickname: String = "",
    val school: String = "",
    val major: String = "",
    val grade: String = "",
    val phone: String = "",
    val wechat: String = "",
    val qq: String = "",
    val source: String = "",
    val demandSummary: String = "",
) {
    val hasContact: Boolean get() = phone.isNotBlank() || wechat.isNotBlank() || qq.isNotBlank()
    val canSubmit: Boolean get() = name.isNotBlank() && hasContact
}

data class CustomerTransferCandidate(val membershipId: String, val displayName: String, val teamNames: String = "")
data class CustomerCollaborationMember(val membershipId: String, val displayName: String, val teamNames: String = "")
data class CustomerCollaboration(
    val ownerMembershipId: String? = null,
    val canManage: Boolean = false,
    val canTransferOwnership: Boolean = false,
    val canEditProfile: Boolean = false,
    val canArchive: Boolean = false,
    val canEditTimeline: Boolean = false,
    val collaborators: List<CustomerCollaborationMember> = emptyList(),
    val candidates: List<CustomerCollaborationMember> = emptyList(),
    val transferCandidates: List<CustomerCollaborationMember> = emptyList(),
)
data class CustomerClassification(
    val id: String,
    val revision: Int,
    val level: CustomerQuality,
    val recognitionReason: String,
    val potentialDemand: String,
    val signals: List<String>,
    val evidenceInteractionIds: List<String>,
    val createdAt: Instant,
)
data class CustomerClassificationState(
    val history: List<CustomerClassification> = emptyList(),
    val deadlineAt: Instant? = null,
    val deadlineMode: String = "",
    val noReplyExtensionUsed: Boolean = false,
)
data class CustomerClassificationDraft(
    val level: CustomerQuality,
    val recognitionReason: String,
    val potentialDemand: String,
    val signals: List<String>,
    val evidenceInteractionIds: Set<String>,
    val correctionReason: String? = null,
)
data class CustomerInsight(
    val customerId: String,
    val content: String,
    val generatedAt: Instant = Instant.now(),
    val timelineUpdatedAt: Instant? = null,
    val traceId: String? = null,
)

data class DashboardGreeting(
    val salutation: String,
    val subtitle: String,
    val generatedByAi: Boolean,
    val validUntil: Instant,
)

enum class InteractionType(val label: String) {
    CUSTOMER_REPLY("客户回复"), OUTBOUND_NO_REPLY("真实触达未回复"),
    AGREED_LATER("客户约定以后联系"), INTERNAL_NOTE("内部备注"),
}

data class Interaction(
    val id: String,
    val customerId: String,
    val occurredAt: Instant,
    val createdAt: Instant,
    val type: InteractionType,
    val channel: String,
    val summary: String,
    val details: String = "",
    val creatorName: String,
)

data class InteractionDraft(
    val type: InteractionType = InteractionType.CUSTOMER_REPLY,
    val channel: String = "QQ",
    val summary: String = "",
    val details: String = "",
    val occurredAt: Instant? = null,
)

data class Reminder(
    val id: String,
    val customerId: String,
    val customerName: String,
    val title: String,
    val remindAt: Instant,
    val overdue: Boolean,
    val completed: Boolean = false,
)

data class DashboardSnapshot(
    val activeCustomers: Int,
    val todayNewCustomers: Int,
    val pendingReminders: Int,
    val staleAClassCustomers: Int,
    val todayScore: Double,
    val baselineScore: Double,
    val focusCustomers: List<Customer>,
)

data class DailySummary(
    val date: LocalDate,
    val verifiedGrade26Leads: Int,
    val deepConversationCount: Int,
    val deepConversationReadOnly: Boolean,
    val issues: String,
    val tomorrowPlan: String,
    val submitted: Boolean,
)

data class DailySummaryDraft(
    val deepConversationCount: String = "",
    val issues: String = "",
    val tomorrowPlan: String = "",
)

enum class DealState(val label: String) {
    NONE("未申请成单"), PENDING("成单申请待审核"), WON("已成单")
}

enum class DealRequestStatus(val label: String) {
    PENDING("待审核"), APPROVED("已确认成单"), REJECTED("已驳回")
}

data class DealRequest(
    val id: String,
    val customerId: String,
    val customerName: String,
    val teamName: String? = null,
    val ownerDisplayName: String? = null,
    val applicantMembershipId: String,
    val applicantDisplayName: String? = null,
    val status: DealRequestStatus,
    val requestedAt: Instant,
    val reviewedAt: Instant? = null,
    val version: Long = 0,
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val unread: Boolean,
)
