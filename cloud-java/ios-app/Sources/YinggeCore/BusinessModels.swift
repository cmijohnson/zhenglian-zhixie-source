import Foundation

public struct BusinessNotification: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let eventType: String; public let title: String; public let summary: String
    public let targetPath: String?; public let read: Bool; public let createdAt: Date; public let expiresAt: Date?
}
public struct NotificationInbox: Codable, Sendable {
    public let unreadCount: Int64; public let items: [BusinessNotification]
}
public struct NotificationReadAllResponse: Codable, Sendable { public let updatedCount: Int }

public struct WorkAssignment: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let membershipId: String; public let memberDisplayName: String
    public let assignmentSource: String; public let status: String; public let resultSummary: String?
    public let evidenceUrl: String?; public let reviewFeedback: String?; public let claimedAt: Date?
    public let submittedAt: Date?; public let completedAt: Date?; public let updatedAt: Date?; public let version: Int64
}
public struct WorkTask: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let title: String; public let description: String; public let distributionType: String
    public let startAt: Date; public let dueAt: Date; public let maxClaimants: Int?; public let status: String
    public let assignmentCount: Int64; public let completedCount: Int64; public let overdue: Bool; public let claimable: Bool
    public let myAssignment: WorkAssignment?; public let createdAt: Date; public let version: Int64
}
public struct WorkAssignmentUpdate: Codable, Sendable {
    public let status: String; public let resultSummary: String?; public let evidenceUrl: String?
    public init(status: String, resultSummary: String? = nil, evidenceUrl: String? = nil) {
        self.status = status; self.resultSummary = resultSummary; self.evidenceUrl = evidenceUrl
    }
}

public struct PerformanceScore: Codable, Hashable, Sendable {
    public let businessPoints: Double; public let logisticsPoints: Double; public let correctionPoints: Double
    public let earnedPoints: Double; public let baseline: Double; public let shortfall: Double
    public let deduction: Double; public let finalPoints: Double
}
public struct PerformanceInteractionOption: Codable, Hashable, Identifiable, Sendable {
    public let interactionId: String; public let customerId: String; public let customerName: String
    public let contactedAt: Date; public let channel: String; public let summary: String
    public var id: String { interactionId }
}
public struct PerformanceClaim: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let membershipId: String; public let memberDisplayName: String
    public let interactionId: String; public let customerId: String; public let customerName: String
    public let performanceDate: String; public let requestedType: String; public let approvedType: String?
    public let status: String; public let reviewReason: String?; public let submittedAt: Date?; public let reviewedAt: Date?
    public let evidence: [PerformanceEvidence]; public let version: Int64
}
public struct PerformanceEvidence: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let ownerType: String; public let ownerId: String; public let fileName: String
    public let contentType: String; public let fileSize: Int64; public let createdAt: Date
}
public struct PerformanceEvidenceFile: Hashable, Identifiable, Sendable {
    public let id: UUID; public let fileName: String; public let contentType: String; public let data: Data
    public init(id: UUID = UUID(), fileName: String, contentType: String, data: Data) {
        self.id = id; self.fileName = fileName; self.contentType = contentType; self.data = data
    }
}
public enum PerformanceEvidenceValidationError: LocalizedError, Sendable {
    case empty, tooLarge, unsupportedType, tooMany
    public var errorDescription: String? {
        switch self {
        case .empty: return "证据图片不能为空"
        case .tooLarge: return "单张证据不能超过 5MB"
        case .unsupportedType: return "证据仅支持 JPEG、PNG 或 WebP 图片"
        case .tooMany: return "每次申报最多上传 3 张证据图片"
        }
    }
}
public enum PerformanceEvidenceValidator {
    public static let maximumBytes = 5 * 1024 * 1024
    public static func validate(data: Data, suggestedName: String = "evidence") throws -> PerformanceEvidenceFile {
        guard !data.isEmpty else { throw PerformanceEvidenceValidationError.empty }
        guard data.count <= maximumBytes else { throw PerformanceEvidenceValidationError.tooLarge }
        let bytes = [UInt8](data.prefix(12)); let type: String; let suffix: String
        if bytes.count >= 3 && bytes[0] == 0xff && bytes[1] == 0xd8 && bytes[2] == 0xff { type = "image/jpeg"; suffix = "jpg" }
        else if bytes.count >= 8 && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a { type = "image/png"; suffix = "png" }
        else if bytes.count >= 12 && String(bytes: bytes[0...3], encoding: .ascii) == "RIFF" && String(bytes: bytes[8...11], encoding: .ascii) == "WEBP" { type = "image/webp"; suffix = "webp" }
        else { throw PerformanceEvidenceValidationError.unsupportedType }
        let base = suggestedName.replacingOccurrences(of: "[^A-Za-z0-9._-]", with: "_", options: .regularExpression)
        let fileName = base.lowercased().hasSuffix(".\(suffix)") ? base : "\(base.prefix(220)).\(suffix)"
        return PerformanceEvidenceFile(fileName: fileName, contentType: type, data: data)
    }
}
public struct PerformanceToday: Codable, Sendable {
    public let date: String; public let participant: Bool; public let dayStatus: String; public let submissionOpen: Bool
    public let opensAt: String?; public let closesAt: String?; public let score: PerformanceScore
    public let pendingClaims: Int64; public let pendingActivities: Int64; public let approvedDeepCount: Int; public let pendingDeepCount: Int
    public let availableInteractions: [PerformanceInteractionOption]; public let claims: [PerformanceClaim]
}
public struct PerformanceClaimCreate: Codable, Sendable {
    public let interactionId: String; public let requestedType: String
}

public struct ConsultationCandidate: Codable, Hashable, Identifiable, Sendable {
    public let membershipId: String; public let displayName: String; public let roles: [String]; public let teams: [String]
    public var id: String { membershipId }
}
public struct ConsultationRecord: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public let customerName: String
    public let createdByMembershipId: String; public let createdByName: String
    public let consultantMembershipId: String; public let consultantName: String
    public let consultationReason: String; public let customerQuotes: String; public let goalsMotivation: String
    public let actionsTaken: String; public let mainConcerns: String; public let productFit: String
    public let suggestedFocus: String; public let explicitTaboos: String; public let expectedResult: String
    public let scheduledStart: Date; public let scheduledEnd: Date; public let status: String
    public let resultSummary: String?; public let nextStep: String?; public let nextOwnerMembershipId: String?
    public let nextFollowUpAt: Date?; public let version: Int64
}
public struct ConsultationCreate: Codable, Sendable {
    public let customerId: String; public let consultantMembershipId: String; public let sourceInteractionIds: [String]
    public let consultationReason: String; public let customerQuotes: String; public let goalsMotivation: String
    public let actionsTaken: String; public let mainConcerns: String; public let decisionParticipants: String?
    public let decisionParticipantConsent: Bool; public let productFit: String; public let suggestedFocus: String
    public let explicitTaboos: String; public let expectedResult: String; public let scheduledStart: Date; public let scheduledEnd: Date
}
public struct ConsultationComplete: Codable, Sendable {
    public let expectedVersion: Int64; public let resultSummary: String; public let continueFollowUp: Bool
    public let nextStep: String; public let nextOwnerMembershipId: String; public let nextFollowUpAt: Date?
    public let failureReasonId: String?; public let failureReasonOther: String?
}
public struct ConsultationFailureReason: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let code: String; public let label: String; public let active: Bool; public let sortOrder: Int
}

public struct ContentDirection: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let title: String; public let description: String?; public let active: Bool
    public let createdAt: Date; public let updatedAt: Date
}
public struct ContentPost: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let membershipId: String; public let memberDisplayName: String; public let platform: String
    public let publishDate: String; public let directionId: String; public let directionTitle: String; public let topic: String
    public let contentUrl: String; public let viewCount: Int64; public let likeCount: Int64; public let favoriteCount: Int64
    public let originalDeclared: Bool; public let reviewStatus: String; public let reviewReason: String?
    public let reviewedAt: Date?; public let createdAt: Date; public let updatedAt: Date; public let weightScore: Int64; public let version: Int64
}
public struct ContentProgress: Codable, Hashable, Sendable { public let xiaohongshuValid: Int64; public let douyinValid: Int64 }
public struct ContentCampaign: Codable, Hashable, Sendable {
    public let name: String; public let startDate: String; public let endDate: String
    public let xiaohongshuTarget: Int; public let douyinTarget: Int; public let today: String; public let active: Bool
}
public struct ContentOverview: Codable, Sendable {
    public let campaign: ContentCampaign; public let directions: [ContentDirection]; public let myProgress: ContentProgress
    public let myPosts: [ContentPost]; public let reviewQueue: [ContentPost]; public let administrator: Bool; public let reviewer: Bool
}
public struct ContentPostCreate: Codable, Sendable {
    public let platform: String; public let publishDate: String; public let directionId: String; public let topic: String
    public let contentUrl: String; public let viewCount: Int64; public let likeCount: Int64; public let favoriteCount: Int64
    public let originalDeclared: Bool
}
public struct ContentMetricsUpdate: Codable, Sendable {
    public let viewCount: Int64; public let likeCount: Int64; public let favoriteCount: Int64
}

public struct Announcement: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let authorMembershipId: String; public let authorDisplayName: String
    public let title: String; public let content: String; public let priority: String; public let pinned: Bool
    public let showOnLeadershipScreen: Bool; public let status: String; public let publishedAt: Date
    public let archivedAt: Date?; public let updatedAt: Date; public let version: Int64
}

public struct AgentAuthorization: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let clientId: String; public let clientName: String; public let displayName: String
    public let scopes: Set<String>; public let status: String; public let createdAt: Date; public let expiresAt: Date?
    public let lastUsedAt: Date?; public let revokedAt: Date?
}
public struct AssistantAvailability: Codable, Hashable, Sendable {
    public let available: Bool; public let provider: String?; public let model: String?
}
public struct AssistantCustomerSummary: Hashable, Sendable {
    public let content: String
    public init(content: String) { self.content = content }
}

public struct PoolTimeline: Codable, Hashable, Sendable { public let occurredAt: Date; public let channel: String; public let description: String }
public struct CustomerPoolEntry: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public let poolType: String; public let releaseReason: String
    public let customerName: String?; public let school: String?; public let grade: String?; public let source: String?
    public let demandSummary: String?; public let timeline: [PoolTimeline]; public let availableAt: Date
    public let priorityUntil: Date?; public let canClaim: Bool; public let status: String
}
public struct PoolClaimRequest: Codable, Sendable { public let idempotencyKey: String }
public struct PoolClaimResult: Codable, Sendable {
    public let claimId: String; public let poolEntryId: String; public let customerId: String; public let status: String
    public let claimedAt: Date; public let expiresAt: Date; public let idempotentReplay: Bool
}
public struct SecondLineRecruitment: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public let customerName: String?; public let school: String?
    public let grade: String?; public let demandSummary: String?; public let requiredCount: Int; public let claimedCount: Int
    public let volunteerUntil: Date; public let status: String
}
public struct SecondLineClaimResult: Codable, Sendable {
    public let recruitmentId: String; public let customerId: String; public let activeSecondLineCount: Int; public let recruitmentClosed: Bool
}

public struct CustomerCollaborationMember: Codable, Hashable, Identifiable, Sendable {
    public let membershipId: String; public let displayName: String; public let teamNames: String; public var id: String { membershipId }
}
public struct CustomerCollaboration: Codable, Sendable {
    public let ownerMembershipId: String?; public let canManage: Bool; public let canTransferOwnership: Bool
    public let canEditProfile: Bool; public let canArchive: Bool; public let canEditTimeline: Bool
    public let collaborators: [CustomerCollaborationMember]; public let candidates: [CustomerCollaborationMember]
    public let transferCandidates: [CustomerCollaborationMember]
}
public struct CustomerTransferRequest: Codable, Sendable {
    public let targetMembershipId: String; public let expectedVersion: Int64; public let reason: String?
}
