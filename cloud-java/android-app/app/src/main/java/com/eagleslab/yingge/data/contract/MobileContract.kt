package com.eagleslab.yingge.data.contract

import com.eagleslab.yingge.model.DealRequest
import com.eagleslab.yingge.model.DealRequestStatus
import com.eagleslab.yingge.model.SessionUser
import com.eagleslab.yingge.model.UserRole
import java.time.Instant

data class MobileLoginRequest(val username: String, val password: String, val deviceName: String)
data class RegistrationOptionsRequest(val registrationCode: String)
data class RegistrationTeamDto(val id: String, val name: String)
data class RegistrationOptionsDto(val teams: List<RegistrationTeamDto>)
data class MobileRegisterRequest(val username: String, val realName: String, val password: String, val registrationCode: String, val teamId: String)
data class RegistrationResultDto(val userId: String, val membershipId: String, val username: String, val realName: String, val role: String, val teamId: String, val teamName: String)
data class PasswordChangeRequest(val currentPassword: String, val newPassword: String)
data class MobileRefreshRequest(val refreshToken: String)
data class PushRegistrationRequest(val provider: String = "UNIPUSH", val registrationId: String, val vendor: String? = null)
data class NotificationPreferencesDto(
    val urgentReminders: Boolean = true,
    val tasks: Boolean = true,
    val consultations: Boolean = true,
    val announcements: Boolean = true,
    val releases: Boolean = true,
)
data class ActorDto(
    val userId: String,
    val membershipId: String,
    val organizationId: String,
    val username: String,
    val displayName: String,
    val roles: List<String>,
    val passwordChangeRequired: Boolean,
)
data class MobileTokenResponse(
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val actor: ActorDto,
)
data class UnreadDto(val urgentReminders: Int, val notifications: Int)
data class MobileDeviceDto(
    val id: String,
    val platform: String,
    val deviceName: String,
    val appVersion: String,
    val current: Boolean,
    val createdAt: String,
    val lastUsedAt: String,
    val revokedAt: String? = null,
)
data class MobileBootstrapDto(
    val actor: ActorDto,
    val serverTime: String,
    val organizationTimeZone: String,
    val availableModes: Set<String> = setOf("EMPLOYEE"),
    val capabilities: Set<String>,
    val unread: UnreadDto,
)
data class AdminOverviewDto(
    val pendingDealRequests: Long = 0,
    val pendingClassificationReviews: Long = 0,
    val dueOwnershipDeadlines: Long = 0,
    val openPoolEntries: Long = 0,
    val pendingSecondLineRecruitments: Long = 0,
    val pendingPerformanceClaims: Long = 0,
    val pendingPerformanceActivities: Long = 0,
    val overdueConsultationFeedback: Long = 0,
    val failedIntelligenceJobs: Long = 0,
    val generatedAt: String,
)
data class WebSessionTicketRequest(val target: String)
data class WebSessionTicketResponse(val ticket: String, val exchangeUrl: String, val expiresAt: String)
data class MobileAppReleaseDto(
    val id: String,
    val platform: String,
    val versionCode: Long,
    val versionName: String,
    val minimumSupportedVersionCode: Long,
    val mandatory: Boolean,
    val notes: String,
    val apkSize: Long? = null,
    val apkSha256: String? = null,
    val manifestSignature: String? = null,
)
data class ContractCustomerDto(
    val id: String,
    val fullName: String,
    val nickname: String? = null,
    val school: String? = null,
    val major: String? = null,
    val grade: String? = null,
    val currentStatus: String? = null,
    val dealState: String,
    val canRequestDeal: Boolean,
    val dealRequestedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
)
data class DealRequestDto(
    val id: String,
    val customerId: String,
    val customerName: String? = null,
    val teamName: String? = null,
    val ownerDisplayName: String? = null,
    val applicantMembershipId: String,
    val applicantDisplayName: String? = null,
    val status: String,
    val requestedAt: String,
    val reviewedAt: String? = null,
    val reviewerMembershipId: String? = null,
    val version: Long,
)
data class FieldErrorDto(val field: String, val message: String)
data class ApiErrorDto(val code: String, val message: String, val fieldErrors: List<FieldErrorDto>, val traceId: String)

fun ActorDto.toModel(): SessionUser = SessionUser(
    userId = userId,
    membershipId = membershipId,
    displayName = displayName,
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    passwordChangeRequired = passwordChangeRequired,
)

fun DealRequestDto.toModel(): DealRequest = DealRequest(
    id = id,
    customerId = customerId,
    customerName = customerName.orEmpty(),
    teamName = teamName,
    ownerDisplayName = ownerDisplayName,
    applicantMembershipId = applicantMembershipId,
    applicantDisplayName = applicantDisplayName,
    status = DealRequestStatus.valueOf(status),
    requestedAt = Instant.parse(requestedAt),
    reviewedAt = reviewedAt?.let(Instant::parse),
    version = version,
)
