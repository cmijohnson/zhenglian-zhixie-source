package com.eagleslab.yingge.data

import com.eagleslab.yingge.model.*

/**
 * Mobile REST boundary. The production implementation must be generated/bound from
 * contracts/mobile-api.yaml. The app never invents organizationId or privileges.
 */
interface YinggeRepository {
    suspend fun registrationOptions(serverUrl: String, registrationCode: String): RegistrationOptions
    suspend fun registerAndLogin(serverUrl: String, username: String, realName: String, password: String, registrationCode: String, teamId: String): SessionUser
    suspend fun login(serverUrl: String, username: String, password: String): SessionUser
    suspend fun restoreSession(serverUrl: String): SessionUser
    suspend fun bootstrap(): MobileAppBootstrap
    suspend fun changePassword(currentPassword: String, newPassword: String)
    suspend fun logout()
    suspend fun dashboard(): DashboardSnapshot
    suspend fun dashboardGreeting(): DashboardGreeting
    suspend fun customers(query: String = ""): List<Customer>
    suspend fun customerPage(filter: CustomerFilter): CustomerPage
    suspend fun customerOwnerOptions(): List<CustomerTransferCandidate>
    suspend fun customer(id: String): Customer
    suspend fun saveCustomer(draft: CustomerDraft): Customer
    suspend fun customerCollaboration(customerId: String): CustomerCollaboration
    suspend fun replaceCustomerCollaborators(customerId: String, membershipIds: Set<String>): CustomerCollaboration
    suspend fun customerTransferCandidates(customerId: String): List<CustomerTransferCandidate>
    suspend fun transferCustomer(customerId: String, targetMembershipId: String, reason: String): Customer
    suspend fun archiveCustomer(customerId: String)
    suspend fun customerClassificationState(customerId: String): CustomerClassificationState
    suspend fun saveCustomerClassification(customerId: String, draft: CustomerClassificationDraft): CustomerClassificationState
    suspend fun downloadCustomerTimeline(customerId: String): DownloadArtifact
    suspend fun previewCustomerImport(file: EvidenceUpload): CustomerImportState
    suspend fun commitCustomerImport(jobId: String): CustomerImportState
    suspend fun downloadCustomers(format: String): DownloadArtifact
    suspend fun customerInsight(customerId: String): CustomerInsight
    suspend fun interactions(customerId: String): List<Interaction>
    suspend fun addInteraction(customerId: String, draft: InteractionDraft): Interaction
    suspend fun reminders(): List<Reminder>
    suspend fun completeReminder(id: String)
    suspend fun dailySummary(): DailySummary
    suspend fun saveDailySummary(draft: DailySummaryDraft): DailySummary
    suspend fun requestDeal(customerId: String, idempotencyKey: String): DealRequest
    suspend fun notifications(): List<NotificationItem>
    suspend fun markNotificationRead(id: String)
    suspend fun markAllNotificationsRead(): Int
    suspend fun businessModule(key: String): BusinessModule
    suspend fun actOnBusinessModuleItem(key: String, id: String): BusinessModule
    suspend fun tasks(): List<WorkTask>
    suspend fun claimTask(taskId: String): WorkTask
    suspend fun submitTask(taskId: String, result: String): WorkTask
    suspend fun performance(): PerformanceSnapshot
    suspend fun submitPerformanceEvidence(interactionId: String, requestedType: String, evidence: EvidenceUpload): PerformanceSnapshot
    suspend fun proposePerformanceActivity(eventDate: String, title: String, goal: String, expectedParticipants: Int, description: String): PerformanceSnapshot
    suspend fun completePerformanceActivity(activityId: String, actualParticipants: Int, expectedVersion: Long, evidence: EvidenceUpload): PerformanceSnapshot
    suspend fun consultations(): List<ConsultationItem>
    suspend fun consultationOptions(customerName: String): ConsultationOptions
    suspend fun submitConsultation(draft: ConsultationDraft): ConsultationItem
    suspend fun acceptConsultation(consultationId: String, expectedVersion: Long): ConsultationItem
    suspend fun rejectConsultation(consultationId: String, expectedVersion: Long, reason: String): ConsultationItem
    suspend fun endConsultation(consultationId: String, expectedVersion: Long): ConsultationItem
    suspend fun completeConsultation(consultationId: String, result: ConsultationResultDraft): ConsultationItem
    suspend fun contentTasks(): List<ContentTaskItem>
    suspend fun submitContentTask(taskId: String, platform: String, content: String): ContentTaskItem
    suspend fun updateContentMetrics(taskId: String, metrics: String): ContentTaskItem
    suspend fun circlePosts(): List<CirclePost>
    suspend fun circleFeed(search: String, senderMembershipId: String?, page: Int): CircleFeed
    suspend fun announcements(): List<AnnouncementItem>
    suspend fun assistantWorkspace(): AssistantWorkspace
    suspend fun parseAssistantDraft(mode: String, text: String): AssistantWorkspace
    suspend fun runAssistantStream(kind: String, customerName: String?): AssistantWorkspace
    suspend fun agentAuthorizations(): List<AgentAuthorization>
    suspend fun revokeAgentAuthorization(id: String)
    suspend fun mobileDevices(): List<MobileDeviceItem>
    suspend fun revokeMobileDevice(id: String)
    suspend fun registerPushToken(registrationId: String, vendor: String? = null)
    suspend fun adminOverview(): AdminOverview
    suspend fun adminMembers(): List<AdminMember>
    suspend fun createAdminMember(username: String, displayName: String, initialPassword: String, role: String, teamId: String?): AdminMember
    suspend fun deleteAdminMember(membershipId: String, transferCustomerToMembershipId: String?)
    suspend fun adminTeams(): List<AdminTeam>
    suspend fun createAdminTeam(name: String): AdminTeam
    suspend fun assignTeamLeader(teamId: String, membershipId: String): AdminTeam
    suspend fun deleteAdminTeam(teamId: String)
    suspend fun updateAdminMember(member: AdminMember, displayName: String, role: String, status: String, teamId: String?): AdminMember
    suspend fun adminRegistrationCodes(): List<AdminRegistrationCode>
    suspend fun createAdminRegistrationCode(maxUses: Int, expiresAt: String?): AdminRegistrationCode
    suspend fun disableAdminRegistrationCode(codeId: String)
    suspend fun adminNativeModule(key: String): AdminNativeModule
    suspend fun performAdminNativeAction(key: String, itemId: String, action: String, version: Long): AdminNativeModule
    suspend fun executeAdminNativeCommand(command: AdminNativeCommand): AdminNativeModule
    suspend fun downloadAdminArtifact(key: String, itemId: String): DownloadArtifact
    suspend fun adminDealRequests(): List<DealRequest>
    suspend fun reviewDealRequest(requestId: String, version: Long, approve: Boolean): DealRequest
    suspend fun leadershipDashboard(teamId: String? = null, membershipId: String? = null): LeadershipDashboard
}

class RepositoryException(
    val code: String,
    override val message: String,
    val traceId: String? = null,
    val httpStatus: Int? = null,
) : RuntimeException(message)
