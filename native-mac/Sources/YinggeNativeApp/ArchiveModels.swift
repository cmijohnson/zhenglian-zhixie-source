import Foundation
import UserNotifications

enum AppSection: String, CaseIterable, Identifiable {
    case dashboard
    case profiles
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .dashboard:
            "首页"
        case .profiles:
            "档案库"
        case .settings:
            "设置"
        }
    }

    var systemImage: String {
        switch self {
        case .dashboard:
            "rectangle.grid.2x2"
        case .profiles:
            "person.2"
        case .settings:
            "gearshape"
        }
    }
}

enum ProfileSortBy: String, CaseIterable, Codable, Identifiable {
    case updatedDesc
    case createdDesc
    case lastContactDesc
    case nextFollowUpAsc

    var id: String { rawValue }

    var label: String {
        switch self {
        case .updatedDesc:
            "最近更新"
        case .createdDesc:
            "最近新增"
        case .lastContactDesc:
            "最近联系"
        case .nextFollowUpAsc:
            "最近待跟进"
        }
    }
}

enum ExportFormat: String, CaseIterable, Identifiable {
    case csv
    case xlsx

    var id: String { rawValue }

    var fileExtension: String { rawValue }

    var label: String {
        switch self {
        case .csv:
            "CSV"
        case .xlsx:
            "Excel"
        }
    }
}

struct SearchFilters: Codable, Hashable {
    var searchText: String?
    var grade: String?
    var focusDirection: String?
    var status: String?
    var intentionLevel: String?
    var verificationStatus: String?
    var tagIds: [Int64]
    var includeArchived: Bool
    var sortBy: ProfileSortBy?

    static let `default` = SearchFilters(
        searchText: nil,
        grade: nil,
        focusDirection: nil,
        status: nil,
        intentionLevel: nil,
        verificationStatus: nil,
        tagIds: [],
        includeArchived: false,
        sortBy: .updatedDesc
    )
}

struct TagRecord: Identifiable, Hashable {
    let id: Int64
    let name: String
    let category: String
    let color: String
    let isSystem: Bool
}

struct StudentProfileRecord: Identifiable, Hashable {
    let id: Int64
    let fullName: String
    let nickname: String?
    let phone: String?
    let wechatId: String?
    let qqId: String?
    let school: String?
    let major: String?
    let grade: String?
    let city: String?
    let identityType: String
    let focusDirections: [String]
    let demandSummary: String?
    let intentionLevel: String
    let urgencyLevel: String
    let verificationStatus: String
    let sourceChannel: String?
    let currentStatus: String
    let ownerNotes: String?
    let createdAt: String
    let updatedAt: String
    let lastContactAt: String?
    let nextFollowUpAt: String?
    let isArchived: Bool
    var tagIds: [Int64]
    var tags: [TagRecord]
}

struct InteractionLogRecord: Identifiable, Hashable {
    let id: Int64
    let studentProfileId: Int64
    let contactedAt: String
    let channel: String
    let summary: String
    let details: String?
    let outcome: String?
    let verificationTodo: String?
    let nextFollowUpAt: String?
    let createdAt: String
}

struct ReminderEventRecord: Identifiable, Hashable {
    let id: Int64
    let studentProfileId: Int64
    let interactionLogId: Int64?
    let remindAt: String
    let title: String
    let status: String
    let notificationSent: Bool
    let createdAt: String
    let updatedAt: String
}

struct ProfileDetailRecord: Hashable {
    let profile: StudentProfileRecord
    let interactions: [InteractionLogRecord]
    let reminders: [ReminderEventRecord]
}

struct AppSettingsRecord: Hashable {
    let defaultReminderTime: String
    let lastBackupAt: String?
    let databasePath: String
}

struct DashboardStats: Hashable {
    let totalActive: Int
    let archivedCount: Int
    let todayDueCount: Int
    let overdueCount: Int
    let highIntentionCount: Int
}

struct DashboardData: Hashable {
    let stats: DashboardStats
    let dueToday: [StudentProfileRecord]
    let overdue: [StudentProfileRecord]
    let recentCreated: [StudentProfileRecord]
    let recentUpdated: [StudentProfileRecord]
    let staleHighIntention: [StudentProfileRecord]
}

struct BootstrapSnapshot {
    let profiles: [StudentProfileRecord]
    let tags: [TagRecord]
    let settings: AppSettingsRecord
    let dashboard: DashboardData
}

struct ProfileInput: Hashable {
    var fullName: String
    var nickname: String?
    var phone: String?
    var wechatId: String?
    var qqId: String?
    var school: String?
    var major: String?
    var grade: String?
    var city: String?
    var identityType: String
    var focusDirections: [String]
    var demandSummary: String?
    var intentionLevel: String
    var urgencyLevel: String
    var verificationStatus: String
    var sourceChannel: String?
    var currentStatus: String
    var ownerNotes: String?
    var nextFollowUpAt: String?
    var tagIds: [Int64]
}

struct InteractionInput: Hashable {
    var studentProfileId: Int64
    var contactedAt: String
    var channel: String
    var summary: String
    var details: String?
    var outcome: String?
    var verificationTodo: String?
    var nextFollowUpAt: String?
    var currentStatus: String?
    var intentionLevel: String?
    var verificationStatus: String?
    var tagIds: [Int64]?
}

struct CreateTagInput: Hashable {
    var name: String
    var category: String
    var color: String
}

struct UpdateSettingsInput: Hashable {
    var defaultReminderTime: String
}

struct ImportProfilesResult: Hashable {
    let createdCount: Int
    let updatedCount: Int
    let skippedCount: Int
    let messages: [String]
}

struct DuplicateMatch: Hashable {
    let profile: StudentProfileRecord
    let reasons: [String]
}

struct ProfileFormModel: Hashable {
    var fullName = ""
    var nickname = ""
    var phone = ""
    var wechatId = ""
    var qqId = ""
    var school = ""
    var major = ""
    var grade = ""
    var city = ""
    var identityType = AppOptions.identityTypes[2]
    var focusDirections = ["暂不明确"]
    var demandSummary = ""
    var intentionLevel = AppOptions.intentionLevels[1]
    var urgencyLevel = AppOptions.urgencyLevels[1]
    var verificationStatus = AppOptions.verificationStatuses[0]
    var sourceChannel = ""
    var currentStatus = AppOptions.profileStatuses[0]
    var ownerNotes = ""
    var nextFollowUpAt = Date()
    var hasNextFollowUpAt = false
    var tagIds: Set<Int64> = []

    init() {}

    init(profile: StudentProfileRecord) {
        fullName = profile.fullName
        nickname = profile.nickname ?? ""
        phone = profile.phone ?? ""
        wechatId = profile.wechatId ?? ""
        qqId = profile.qqId ?? ""
        school = profile.school ?? ""
        major = profile.major ?? ""
        grade = profile.grade ?? ""
        city = profile.city ?? ""
        identityType = profile.identityType
        focusDirections = profile.focusDirections.isEmpty ? ["暂不明确"] : profile.focusDirections
        demandSummary = profile.demandSummary ?? ""
        intentionLevel = profile.intentionLevel
        urgencyLevel = profile.urgencyLevel
        verificationStatus = profile.verificationStatus
        sourceChannel = profile.sourceChannel ?? ""
        currentStatus = profile.currentStatus
        ownerNotes = profile.ownerNotes ?? ""
        if let nextFollowUpAt = ArchiveDate.parse(profile.nextFollowUpAt) {
            self.nextFollowUpAt = nextFollowUpAt
            hasNextFollowUpAt = true
        }
        tagIds = Set(profile.tagIds)
    }

    func toInput() -> ProfileInput {
        ProfileInput(
            fullName: fullName,
            nickname: ArchiveText.optional(nickname),
            phone: ArchiveText.optional(phone),
            wechatId: ArchiveText.optional(wechatId),
            qqId: ArchiveText.optional(qqId),
            school: ArchiveText.optional(school),
            major: ArchiveText.optional(major),
            grade: ArchiveText.optional(grade),
            city: ArchiveText.optional(city),
            identityType: identityType,
            focusDirections: focusDirections,
            demandSummary: ArchiveText.optional(demandSummary),
            intentionLevel: intentionLevel,
            urgencyLevel: urgencyLevel,
            verificationStatus: verificationStatus,
            sourceChannel: ArchiveText.optional(sourceChannel),
            currentStatus: currentStatus,
            ownerNotes: ArchiveText.optional(ownerNotes),
            nextFollowUpAt: hasNextFollowUpAt ? ArchiveDate.isoString(from: nextFollowUpAt) : nil,
            tagIds: Array(tagIds).sorted()
        )
    }
}

struct InteractionFormModel: Hashable {
    var contactedAt = Date()
    var channel = AppOptions.interactionChannels[0]
    var summary = ""
    var details = ""
    var outcome = ""
    var verificationTodo = ""
    var nextFollowUpAt = Date()
    var hasNextFollowUpAt = false
    var currentStatus = ""
    var intentionLevel = ""
    var verificationStatus = ""
    var tagIds: Set<Int64> = []

    init(defaultStatus: String, existingTagIDs: [Int64]) {
        currentStatus = defaultStatus
        tagIds = Set(existingTagIDs)
    }

    func toInput(profileID: Int64) -> InteractionInput {
        InteractionInput(
            studentProfileId: profileID,
            contactedAt: ArchiveDate.isoString(from: contactedAt),
            channel: channel,
            summary: summary,
            details: ArchiveText.optional(details),
            outcome: ArchiveText.optional(outcome),
            verificationTodo: ArchiveText.optional(verificationTodo),
            nextFollowUpAt: hasNextFollowUpAt ? ArchiveDate.isoString(from: nextFollowUpAt) : nil,
            currentStatus: ArchiveText.optional(currentStatus),
            intentionLevel: ArchiveText.optional(intentionLevel),
            verificationStatus: ArchiveText.optional(verificationStatus),
            tagIds: Array(tagIds).sorted()
        )
    }
}

struct TagFormModel: Hashable {
    var name = ""
    var category = AppOptions.tagCategories.last ?? "自定义"
    var color = AppOptions.tagColorOptions.first ?? "#2563eb"

    func toInput() -> CreateTagInput {
        CreateTagInput(name: name, category: category, color: color)
    }
}

enum AppOptions {
    static let identityTypes = ["新生", "转专业", "在校生", "毕业生", "家长代问", "其他"]
    static let focusDirectionOptions = ["就业", "考研", "考公", "升学", "转专业", "技能提升", "暂不明确"]
    static let intentionLevels = ["低", "中", "高"]
    static let urgencyLevels = ["低", "中", "高"]
    static let verificationStatuses = ["未核实", "已核实", "部分待确认"]
    static let profileStatuses = ["初次接触", "待核实", "已沟通", "跟进中", "高意向", "待决定", "已转化", "暂缓", "无效", "归档"]
    static let interactionChannels = ["微信", "电话", "面聊", "活动", "群聊", "语音", "其他"]
    static let tagCategories = ["年级", "需求方向", "状态补充", "风险", "自定义"]
    static let tagColorOptions = ["#2563eb", "#0f766e", "#ea580c", "#7c3aed", "#16a34a", "#dc2626", "#475569", "#d97706"]
}

enum ArchiveNotificationPermission: String {
    case notDetermined
    case denied
    case authorized
}

enum ArchiveError: LocalizedError {
    case databaseNotFound(String)
    case sqlite(String)
    case invalidDatabase(String)
    case validation(String)
    case unsupported(String)

    var errorDescription: String? {
        switch self {
        case let .databaseNotFound(path):
            "没有找到数据库文件：\(path)"
        case let .sqlite(message):
            "SQLite 错误：\(message)"
        case let .invalidDatabase(message):
            "数据库内容异常：\(message)"
        case let .validation(message):
            message
        case let .unsupported(message):
            message
        }
    }
}
