package com.eagleslab.yingge.model

import java.time.Instant

enum class WorkTaskStatus { OPEN, CLAIMED, SUBMITTED, ACCEPTED }
data class WorkTask(val id: String, val title: String, val deadline: Instant?, val status: WorkTaskStatus, val result: String = "")

data class PerformanceInteractionOption(val interactionId: String, val customerName: String, val summary: String)
data class EvidenceUpload(val fileName: String, val contentType: String, val bytes: ByteArray)
data class PerformanceHistoryPoint(val date: String, val earnedPoints: Double, val finalPoints: Double, val baseline: Double, val settled: Boolean)
data class PerformanceLeaderboardRow(val rank: Int, val displayName: String, val teamName: String, val finalPoints: Double, val achievedDays: Int, val scoredDays: Int)
data class PerformanceActivityItem(
    val id: String,
    val title: String,
    val eventDate: String,
    val goal: String,
    val expectedParticipants: Int,
    val actualParticipants: Int?,
    val status: String,
    val version: Long,
    val organizerMine: Boolean,
)

/** A server generated file that is handed to Android's document picker. */
data class DownloadArtifact(val fileName: String, val contentType: String, val bytes: ByteArray)
data class CustomerImportState(
    val jobId: String,
    val fileName: String,
    val format: String,
    val status: String,
    val totalRows: Int,
    val readyRows: Int = 0,
    val failedRows: Int = 0,
    val createdRows: Int = 0,
    val skippedRows: Int = 0,
    val messages: List<String> = emptyList(),
)
data class PerformanceSnapshot(
    val score: Double,
    val baseline: Double,
    val pendingScore: Double,
    val lines: List<String>,
    val availableInteractions: List<PerformanceInteractionOption> = emptyList(),
    val history: List<PerformanceHistoryPoint> = emptyList(),
    val leaderboard: List<PerformanceLeaderboardRow> = emptyList(),
    val activities: List<PerformanceActivityItem> = emptyList(),
)

enum class ConsultationStatus { DRAFT, WAITING_ACCEPTANCE, SCHEDULED, WAITING_RESULT, COMPLETED }
data class ConsultationItem(
    val id: String,
    val customerName: String,
    val status: ConsultationStatus,
    val nextAction: String,
    val version: Long = 0,
    val canAccept: Boolean = false,
    val canEnd: Boolean = false,
)
data class ConsultationSource(val interactionId: String, val summary: String)
data class ConsultationCandidate(val membershipId: String, val displayName: String)
data class ConsultationFailureReason(val id: String, val code: String, val label: String)
data class ConsultationOptions(
    val customerId: String,
    val customerName: String,
    val sources: List<ConsultationSource>,
    val candidates: List<ConsultationCandidate>,
    val failureReasons: List<ConsultationFailureReason> = emptyList(),
)
data class ConsultationDraft(
    val customerId: String,
    val consultantMembershipId: String,
    val sourceInteractionId: String,
    val consultationReason: String,
    val customerQuotes: String,
    val goalsMotivation: String,
    val actionsTaken: String,
    val mainConcerns: String,
    val productFit: String,
    val suggestedFocus: String,
    val explicitTaboos: String,
    val expectedResult: String,
    val scheduledStart: String,
    val scheduledEnd: String,
)
data class ConsultationResultDraft(
    val resultSummary: String,
    val continueFollowUp: Boolean,
    val nextStep: String,
    val nextOwnerMembershipId: String,
    val nextFollowUpAt: String?,
    val failureReasonId: String?,
    val failureReasonOther: String?,
)

data class ContentTaskItem(val id: String, val title: String, val channel: String, val submitted: Boolean, val metrics: String = "")
data class CirclePost(
    val id: String,
    val senderName: String,
    val content: String,
    val createdAt: Instant,
    val membershipId: String = "",
    val platform: String = "",
    val contentUrl: String = "",
    val metrics: String = "",
)
data class CircleSender(val membershipId: String, val displayName: String)
data class CircleFeed(val items: List<CirclePost>, val senders: List<CircleSender>, val page: Int, val size: Int, val totalElements: Long)
data class AnnouncementItem(val id: String, val title: String, val body: String, val pinned: Boolean, val createdAt: Instant)
data class AgentAuthorization(val id: String, val clientName: String, val scopes: List<String>, val lastUsedAt: Instant?)
data class MobileDeviceItem(val id: String, val deviceName: String, val current: Boolean, val lastUsedAt: Instant?)
data class AssistantAvailability(val available: Boolean, val provider: String, val model: String)
data class AssistantStructuredDraft(val mode: String, val fields: List<Pair<String, String>>)
data class AssistantWorkspace(
    val availability: AssistantAvailability,
    val draft: AssistantStructuredDraft? = null,
    val streamedTitle: String = "",
    val streamedContent: String = "",
)
