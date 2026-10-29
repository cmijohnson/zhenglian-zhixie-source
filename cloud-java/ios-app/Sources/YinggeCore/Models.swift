import Foundation

public struct EmptyResponse: Codable, Sendable { public init() {} }
public struct APIFieldError: Codable, Hashable, Sendable { public let field: String?; public let message: String }
public struct APIErrorEnvelope: Codable, Sendable {
    public let code: String; public let message: String; public let fieldErrors: [APIFieldError]?; public let traceId: String?
    public init(code: String, message: String, fieldErrors: [APIFieldError]? = nil, traceId: String? = nil) {
        self.code = code; self.message = message; self.fieldErrors = fieldErrors; self.traceId = traceId
    }
}

public struct Me: Codable, Hashable, Sendable {
    public let userId: String; public let membershipId: String; public let organizationId: String
    public let username: String; public let displayName: String; public let roles: Set<String>
    public let passwordChangeRequired: Bool
    public init(userId: String, membershipId: String, organizationId: String, username: String,
                displayName: String, roles: Set<String>, passwordChangeRequired: Bool = false) {
        self.userId = userId; self.membershipId = membershipId; self.organizationId = organizationId
        self.username = username; self.displayName = displayName; self.roles = roles
        self.passwordChangeRequired = passwordChangeRequired
    }
    public var canWriteCustomers: Bool { !roles.isDisjoint(with: ["ORG_ADMIN", "TEAM_MANAGER", "MEMBER"]) }
    public var canSubmitDailySummary: Bool { canWriteCustomers }
    public var isAdministrator: Bool { roles.contains("ORG_ADMIN") }
}

public enum MobilePlatform: String, Codable, Sendable { case android = "ANDROID"; case ios = "IOS" }
public struct MobileLoginRequest: Codable, Sendable {
    public let username: String; public let password: String; public let deviceName: String
    public init(username: String, password: String, deviceName: String) {
        self.username = username; self.password = password; self.deviceName = deviceName
    }
}
public struct RegistrationOptionsRequest: Codable, Sendable { public let registrationCode: String }
public struct RegistrationTeam: Codable, Hashable, Identifiable, Sendable { public let id: String; public let name: String }
public struct RegistrationOptions: Codable, Sendable { public let teams: [RegistrationTeam] }
public struct MobileRegisterRequest: Codable, Sendable {
    public let username: String; public let realName: String; public let password: String
    public let registrationCode: String; public let teamId: String
}
public struct RegistrationResult: Codable, Sendable {
    public let userId: String; public let membershipId: String; public let username: String; public let realName: String
    public let role: String; public let teamId: String; public let teamName: String
}
public struct PasswordChangeRequest: Codable, Sendable { public let currentPassword: String; public let newPassword: String }
public struct MobileRefreshRequest: Codable, Sendable { public let refreshToken: String }
public struct MobileTokenResponse: Codable, Sendable {
    public let accessToken: String; public let accessExpiresAt: Date; public let refreshToken: String
    public let refreshExpiresAt: Date; public let actor: Me
}
public struct MobileDevice: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let platform: MobilePlatform; public let deviceName: String
    public let appVersion: String; public let current: Bool; public let createdAt: Date
    public let lastUsedAt: Date; public let revokedAt: Date?
}
public struct MobileUnread: Codable, Hashable, Sendable { public let urgentReminders: Int; public let notifications: Int }
public struct MobileBootstrap: Codable, Sendable {
    public let actor: Me; public let serverTime: Date; public let organizationTimeZone: String
    public let capabilities: Set<String>; public let unread: MobileUnread
}
public struct PushRegistrationRequest: Codable, Sendable {
    public let provider: String; public let registrationId: String; public let vendor: String?
    public init(registrationId: String, vendor: String? = nil) { provider = "UNIPUSH"; self.registrationId = registrationId; self.vendor = vendor }
}
public struct NotificationPreferences: Codable, Hashable, Sendable {
    public var urgentReminders: Bool; public var tasks: Bool; public var consultations: Bool
    public var announcements: Bool; public var releases: Bool
    public init(urgentReminders: Bool = true, tasks: Bool = true, consultations: Bool = true,
                announcements: Bool = true, releases: Bool = true) {
        self.urgentReminders = urgentReminders; self.tasks = tasks; self.consultations = consultations
        self.announcements = announcements; self.releases = releases
    }
}
public enum WebSessionTarget: String, Codable, Sendable {
    case qqWorkbench = "QQ_WORKBENCH", oauthApproval = "OAUTH_APPROVAL", agentApproval = "AGENT_APPROVAL"
}
public struct WebSessionTicketRequest: Codable, Sendable { public let target: WebSessionTarget }
public struct WebSessionTicket: Codable, Sendable { public let ticket: String; public let exchangeUrl: String; public let expiresAt: Date }
public struct MobileAppRelease: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let platform: MobilePlatform; public let versionCode: Int64; public let versionName: String
    public let minimumSupportedVersionCode: Int64; public let mandatory: Bool; public let notes: String
    public let apkSize: Int64?; public let apkSha256: String?; public let manifestSignature: String?
}
public struct ProductRelease: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let version: String; public let title: String; public let summary: String
    public let sections: [String: [String]]; public let releasedAt: Date; public let current: Bool; public let acknowledged: Bool
}

public struct PageResponse<Item: Codable & Sendable>: Codable, Sendable {
    public let items: [Item]; public let page: Int; public let totalPages: Int; public let totalElements: Int
    public init(items: [Item], page: Int = 0, totalPages: Int = 1, totalElements: Int? = nil) {
        self.items = items; self.page = page; self.totalPages = totalPages; self.totalElements = totalElements ?? items.count
    }
    private enum CodingKeys: String, CodingKey { case items, content, page, totalPages, totalElements }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = try c.decodeIfPresent([Item].self, forKey: .items) ?? c.decode([Item].self, forKey: .content)
        page = try c.decodeIfPresent(Int.self, forKey: .page) ?? 0
        totalPages = try c.decodeIfPresent(Int.self, forKey: .totalPages) ?? 1
        totalElements = try c.decodeIfPresent(Int.self, forKey: .totalElements) ?? items.count
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(items, forKey: .items); try c.encode(page, forKey: .page)
        try c.encode(totalPages, forKey: .totalPages); try c.encode(totalElements, forKey: .totalElements)
    }
}

public struct TagSummary: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let name: String; public let category: String; public let color: String?
    public init(id: String, name: String, category: String, color: String? = nil) {
        self.id = id; self.name = name; self.category = category; self.color = color
    }
}
public enum DealState: String, Codable, Sendable { case none = "NONE", pending = "PENDING", won = "WON" }
public enum DealRequestStatus: String, Codable, Sendable { case pending = "PENDING", approved = "APPROVED", rejected = "REJECTED" }

public struct Customer: Codable, Hashable, Identifiable, Sendable {
    public let id: String
    public var ownerMembershipId: String?; public var ownerDisplayName: String?; public var teamId: String?
    public var visibility: String?; public var fullName: String; public var nickname: String?
    public var phone: String?; public var wechatId: String?; public var qqId: String?
    public var school: String?; public var major: String?; public var grade: String?; public var city: String?
    public var highSchool: String?; public var sourceLocation: String?; public var identityType: String?
    public var focusDirections: Set<String>; public var demandSummary: String?; public var intentionLevel: String
    public var urgencyLevel: String?; public var verificationStatus: String?; public var sourceChannel: String?
    public var currentStatus: String; public var ownerNotes: String?; public var lastContactAt: Date?
    public var nextFollowUpAt: Date?; public var archivedAt: Date?; public var dealState: DealState
    public var canRequestDeal: Bool; public var dealRequestedAt: Date?; public var createdAt: Date?
    public var updatedAt: Date?; public var version: Int64; public var tags: [TagSummary]

    public init(id: String, ownerMembershipId: String? = nil, ownerDisplayName: String? = nil, teamId: String? = nil,
                visibility: String? = "PRIVATE", fullName: String, nickname: String? = nil, phone: String? = nil,
                wechatId: String? = nil, qqId: String? = nil, school: String? = nil, major: String? = nil,
                grade: String? = nil, city: String? = nil, highSchool: String? = nil, sourceLocation: String? = nil,
                identityType: String? = "在校生", focusDirections: Set<String> = ["暂不明确"], demandSummary: String? = nil,
                intentionLevel: String = "中", urgencyLevel: String? = "中", verificationStatus: String? = "未核实",
                sourceChannel: String? = nil, currentStatus: String = "初次接触", ownerNotes: String? = nil,
                lastContactAt: Date? = nil, nextFollowUpAt: Date? = nil, archivedAt: Date? = nil,
                dealState: DealState = .none, canRequestDeal: Bool = true, dealRequestedAt: Date? = nil,
                createdAt: Date? = nil, updatedAt: Date? = nil, version: Int64 = 0, tags: [TagSummary] = []) {
        self.id = id; self.ownerMembershipId = ownerMembershipId; self.ownerDisplayName = ownerDisplayName; self.teamId = teamId
        self.visibility = visibility; self.fullName = fullName; self.nickname = nickname; self.phone = phone
        self.wechatId = wechatId; self.qqId = qqId; self.school = school; self.major = major; self.grade = grade; self.city = city
        self.highSchool = highSchool; self.sourceLocation = sourceLocation; self.identityType = identityType
        self.focusDirections = focusDirections; self.demandSummary = demandSummary; self.intentionLevel = intentionLevel
        self.urgencyLevel = urgencyLevel; self.verificationStatus = verificationStatus; self.sourceChannel = sourceChannel
        self.currentStatus = currentStatus; self.ownerNotes = ownerNotes; self.lastContactAt = lastContactAt
        self.nextFollowUpAt = nextFollowUpAt; self.archivedAt = archivedAt; self.dealState = dealState
        self.canRequestDeal = canRequestDeal; self.dealRequestedAt = dealRequestedAt; self.createdAt = createdAt
        self.updatedAt = updatedAt; self.version = version; self.tags = tags
    }
    private enum CodingKeys: String, CodingKey {
        case id, ownerMembershipId, ownerDisplayName, teamId, visibility, fullName, nickname, phone, wechatId, qqId
        case school, major, grade, city, highSchool, sourceLocation, identityType, focusDirections, demandSummary
        case intentionLevel, urgencyLevel, verificationStatus, sourceChannel, currentStatus, ownerNotes, lastContactAt
        case nextFollowUpAt, archivedAt, dealState, canRequestDeal, dealRequestedAt, createdAt, updatedAt, version, tags
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); fullName = try c.decode(String.self, forKey: .fullName)
        ownerMembershipId = try c.decodeIfPresent(String.self, forKey: .ownerMembershipId)
        ownerDisplayName = try c.decodeIfPresent(String.self, forKey: .ownerDisplayName); teamId = try c.decodeIfPresent(String.self, forKey: .teamId)
        visibility = try c.decodeIfPresent(String.self, forKey: .visibility); nickname = try c.decodeIfPresent(String.self, forKey: .nickname)
        phone = try c.decodeIfPresent(String.self, forKey: .phone); wechatId = try c.decodeIfPresent(String.self, forKey: .wechatId)
        qqId = try c.decodeIfPresent(String.self, forKey: .qqId); school = try c.decodeIfPresent(String.self, forKey: .school)
        major = try c.decodeIfPresent(String.self, forKey: .major); grade = try c.decodeIfPresent(String.self, forKey: .grade)
        city = try c.decodeIfPresent(String.self, forKey: .city); highSchool = try c.decodeIfPresent(String.self, forKey: .highSchool)
        sourceLocation = try c.decodeIfPresent(String.self, forKey: .sourceLocation); identityType = try c.decodeIfPresent(String.self, forKey: .identityType)
        focusDirections = try c.decodeIfPresent(Set<String>.self, forKey: .focusDirections) ?? ["暂不明确"]
        demandSummary = try c.decodeIfPresent(String.self, forKey: .demandSummary)
        intentionLevel = try c.decodeIfPresent(String.self, forKey: .intentionLevel) ?? "中"
        urgencyLevel = try c.decodeIfPresent(String.self, forKey: .urgencyLevel); verificationStatus = try c.decodeIfPresent(String.self, forKey: .verificationStatus)
        sourceChannel = try c.decodeIfPresent(String.self, forKey: .sourceChannel)
        currentStatus = try c.decodeIfPresent(String.self, forKey: .currentStatus) ?? "未设置"
        ownerNotes = try c.decodeIfPresent(String.self, forKey: .ownerNotes); lastContactAt = try c.decodeIfPresent(Date.self, forKey: .lastContactAt)
        nextFollowUpAt = try c.decodeIfPresent(Date.self, forKey: .nextFollowUpAt); archivedAt = try c.decodeIfPresent(Date.self, forKey: .archivedAt)
        dealState = try c.decodeIfPresent(DealState.self, forKey: .dealState) ?? .none
        canRequestDeal = try c.decodeIfPresent(Bool.self, forKey: .canRequestDeal) ?? false
        dealRequestedAt = try c.decodeIfPresent(Date.self, forKey: .dealRequestedAt)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt); updatedAt = try c.decodeIfPresent(Date.self, forKey: .updatedAt)
        version = try c.decodeIfPresent(Int64.self, forKey: .version) ?? 0; tags = try c.decodeIfPresent([TagSummary].self, forKey: .tags) ?? []
    }
    public var qualityCode: String? { tags.first(where: { $0.category == "客户质量" }).map { String($0.name.prefix(1)).uppercased() } }
    public var primaryContact: String {
        if let phone, !phone.isEmpty { return "手机 \(phone)" }; if let wechatId, !wechatId.isEmpty { return "微信 \(wechatId)" }
        if let qqId, !qqId.isEmpty { return "QQ \(qqId)" }; return "未填写联系方式"
    }
}

public struct DealRequest: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public let customerName: String?; public let teamName: String?
    public let ownerDisplayName: String?; public let applicantMembershipId: String; public let applicantDisplayName: String?
    public let status: DealRequestStatus; public let requestedAt: Date; public let reviewedAt: Date?
    public let reviewerMembershipId: String?; public let version: Int64
}
public struct DealRequestPage: Codable, Sendable {
    public let content: [DealRequest]; public let page: Int; public let size: Int; public let totalElements: Int64; public let totalPages: Int
}

public struct CustomerWriteRequest: Codable, Sendable {
    public var fullName: String; public var nickname: String?; public var phone: String?; public var wechatId: String?; public var qqId: String?
    public var school: String?; public var major: String?; public var grade: String?; public var city: String?
    public var highSchool: String?; public var sourceLocation: String?; public var identityType: String
    public var focusDirections: Set<String>; public var demandSummary: String?; public var intentionLevel: String
    public var urgencyLevel: String; public var verificationStatus: String; public var sourceChannel: String?
    public var currentStatus: String; public var ownerNotes: String?; public var nextFollowUpAt: Date?
    public var ownerMembershipId: String?; public var teamId: String?; public var visibility: String
    public var tagIds: Set<String>; public var duplicateOverrideReason: String?; public var expectedVersion: Int64?
    public init(customer: Customer? = nil) {
        fullName = customer?.fullName ?? ""; nickname = customer?.nickname; phone = customer?.phone; wechatId = customer?.wechatId
        qqId = customer?.qqId; school = customer?.school; major = customer?.major; grade = customer?.grade; city = customer?.city
        highSchool = customer?.highSchool; sourceLocation = customer?.sourceLocation; identityType = customer?.identityType ?? "在校生"
        focusDirections = customer?.focusDirections ?? ["暂不明确"]; demandSummary = customer?.demandSummary
        intentionLevel = customer?.intentionLevel ?? "中"; urgencyLevel = customer?.urgencyLevel ?? "中"
        verificationStatus = customer?.verificationStatus ?? "未核实"; sourceChannel = customer?.sourceChannel
        currentStatus = customer?.currentStatus == "已转化" ? "跟进中" : (customer?.currentStatus ?? "初次接触")
        ownerNotes = customer?.ownerNotes; nextFollowUpAt = customer?.nextFollowUpAt; ownerMembershipId = customer?.ownerMembershipId
        teamId = customer?.teamId; visibility = customer?.visibility ?? "PRIVATE"; tagIds = Set(customer?.tags.map(\.id) ?? [])
        expectedVersion = customer?.version
    }
}

public struct Interaction: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public let contactedAt: Date; public let channel: String
    public let summary: String; public let details: String?; public let outcome: String?; public let verificationTodo: String?
    public let nextFollowUpAt: Date?; public let createdByDisplayName: String?; public let interactionType: String?; public let createdAt: Date?
    public init(id: String, customerId: String, contactedAt: Date, channel: String, summary: String, details: String? = nil,
                outcome: String? = nil, verificationTodo: String? = nil, nextFollowUpAt: Date? = nil,
                createdByDisplayName: String? = nil, interactionType: String? = nil, createdAt: Date? = nil) {
        self.id = id; self.customerId = customerId; self.contactedAt = contactedAt; self.channel = channel; self.summary = summary
        self.details = details; self.outcome = outcome; self.verificationTodo = verificationTodo; self.nextFollowUpAt = nextFollowUpAt
        self.createdByDisplayName = createdByDisplayName; self.interactionType = interactionType; self.createdAt = createdAt
    }
}
public struct InteractionCreateRequest: Codable, Sendable {
    public var contactedAt: Date; public var channel: String; public var interactionType: String; public var summary: String
    public var details: String?; public var outcome: String?; public var verificationTodo: String?; public var nextFollowUpAt: Date?
    public init(contactedAt: Date = Date(), channel: String = "QQ", interactionType: String = "CUSTOMER_REPLIED", summary: String = "") {
        self.contactedAt = contactedAt; self.channel = channel; self.interactionType = interactionType; self.summary = summary
    }
}
public struct Reminder: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let customerId: String; public var remindAt: Date; public var title: String
    public var status: String; public let reminderType: String?; public let overdue: Bool; public let version: Int64
}
public struct ReminderUpdateRequest: Codable, Sendable {
    public let remindAt: Date?; public let title: String?; public let status: String?
    public init(remindAt: Date? = nil, title: String? = nil, status: String? = nil) {
        self.remindAt = remindAt; self.title = title; self.status = status
    }
}
public struct DailySummary: Codable, Hashable, Sendable {
    public let id: String?; public let membershipId: String; public let memberDisplayName: String; public let reportDate: String
    public let grade26NewValidLeads: Int; public var deepCommunicationCount: Int; public let pendingDeepCommunicationCount: Int
    public let performanceManaged: Bool; public var todayIssues: String; public var tomorrowPlan: String
    public let submittedAt: Date?; public let version: Int64; public let submitted: Bool
}
public struct DailySummaryWriteRequest: Codable, Sendable {
    public let deepCommunicationCount: Int; public let todayIssues: String; public let tomorrowPlan: String
}
public struct DashboardStats: Codable, Sendable {
    public let totalActive: Int; public let archivedCount: Int; public let todayDueCount: Int
    public let overdueCount: Int; public let highIntentionCount: Int
}
public struct Dashboard: Codable, Sendable {
    public let stats: DashboardStats; public let dueToday: [Customer]; public let overdue: [Customer]
    public let staleHighIntention: [Customer]; public let recentUpdated: [Customer]
}

public enum JSONValue: Codable, Hashable, Sendable {
    case string(String), number(Double), bool(Bool), object([String: JSONValue]), array([JSONValue]), null
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let value = try? c.decode(Bool.self) { self = .bool(value) }
        else if let value = try? c.decode(Double.self) { self = .number(value) }
        else if let value = try? c.decode(String.self) { self = .string(value) }
        else if let value = try? c.decode([String: JSONValue].self) { self = .object(value) }
        else { self = .array(try c.decode([JSONValue].self)) }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case let .string(v): try c.encode(v); case let .number(v): try c.encode(v); case let .bool(v): try c.encode(v)
        case let .object(v): try c.encode(v); case let .array(v): try c.encode(v); case .null: try c.encodeNil()
        }
    }
}
public struct IntelligenceEvidence: Codable, Hashable, Sendable {
    public let sourceType: String?; public let sourceKey: String?; public let sourceAt: Date?; public let excerpt: String?
}
public struct IntelligenceVersion: Codable, Hashable, Identifiable, Sendable {
    public let id: String; public let versionNumber: Int; public let triggerType: String; public let status: String
    public let result: JSONValue?; public let narrative: String?; public let evidence: [IntelligenceEvidence]
    public let provider: String?; public let model: String?; public let sourceWatermark: Date?; public let createdAt: Date?
}
public struct CustomerIntelligence: Codable, Hashable, Sendable {
    public let customerId: String; public let enabled: Bool; public let processing: Bool
    public let processingStatus: String?; public let latest: IntelligenceVersion?
}
