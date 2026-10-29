import Foundation
import SQLite3
import ZIPFoundation

enum ArchiveText {
    static func required(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func optional(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = required(value)
        return trimmed.isEmpty ? nil : trimmed
    }
}

enum ArchiveDate {
    private static func inputWithFractionalSecondsFormatter() -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }

    private static func inputStandardFormatter() -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }

    private static func outputFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        formatter.locale = Locale(identifier: "zh_CN")
        return formatter
    }

    static func parse(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        if let date = inputWithFractionalSecondsFormatter().date(from: value) {
            return date
        }
        return inputStandardFormatter().date(from: value)
    }

    static func display(_ value: String?, fallback: String = "未记录") -> String {
        guard let date = parse(value) else { return fallback }
        return outputFormatter().string(from: date)
    }

    static func isoString(from date: Date) -> String {
        inputWithFractionalSecondsFormatter().string(from: date)
    }

    static func nowISO() -> String {
        isoString(from: Date())
    }
}

enum ArchivePaths {
    static let databaseFileName = "yingge-student-archive.sqlite"
    static let savedPathKey = "yingge.native.database.path"

    static func defaultDatabasePath() -> String {
        if let savedPath = UserDefaults.standard.string(forKey: savedPathKey),
           FileManager.default.fileExists(atPath: savedPath) {
            return savedPath
        }

        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first

        let preferred = appSupport?
            .appendingPathComponent("com.yingge.studentarchive", isDirectory: true)
            .appendingPathComponent(databaseFileName)

        if let preferred, FileManager.default.fileExists(atPath: preferred.path) {
            return preferred.path
        }

        if let appSupport {
            let enumerator = FileManager.default.enumerator(
                at: appSupport,
                includingPropertiesForKeys: [.isRegularFileKey],
                options: [.skipsHiddenFiles]
            )

            while let url = enumerator?.nextObject() as? URL {
                if url.lastPathComponent == databaseFileName {
                    return url.path
                }
            }
        }

        return preferred?.path ?? databaseFileName
    }
}

enum SQLiteValue {
    case int64(Int64)
    case text(String)
    case bool(Bool)
    case null
}

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

final class SQLiteDatabase {
    private var handle: OpaquePointer?
    let path: String

    init(path: String, readOnly: Bool = true) throws {
        self.path = path

        if readOnly, !FileManager.default.fileExists(atPath: path) {
            throw ArchiveError.databaseNotFound(path)
        }

        if !readOnly {
            let directoryURL = URL(fileURLWithPath: path).deletingLastPathComponent()
            try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        }

        let flags = readOnly ? SQLITE_OPEN_READONLY : (SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE)
        if sqlite3_open_v2(path, &handle, flags, nil) != SQLITE_OK {
            throw ArchiveError.sqlite(Self.errorMessage(from: handle))
        }

        try execute("PRAGMA foreign_keys = ON")
    }

    deinit {
        sqlite3_close(handle)
    }

    func prepare(_ sql: String) throws -> OpaquePointer? {
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(handle, sql, -1, &statement, nil) != SQLITE_OK {
            throw ArchiveError.sqlite(Self.errorMessage(from: handle))
        }
        return statement
    }

    func finalize(_ statement: OpaquePointer?) {
        sqlite3_finalize(statement)
    }

    func bind(_ statement: OpaquePointer?, values: [SQLiteValue]) throws {
        for (index, value) in values.enumerated() {
            let parameterIndex = Int32(index + 1)
            let result: Int32

            switch value {
            case let .int64(number):
                result = sqlite3_bind_int64(statement, parameterIndex, number)
            case let .text(string):
                result = sqlite3_bind_text(statement, parameterIndex, string, -1, SQLITE_TRANSIENT)
            case let .bool(boolean):
                result = sqlite3_bind_int(statement, parameterIndex, boolean ? 1 : 0)
            case .null:
                result = sqlite3_bind_null(statement, parameterIndex)
            }

            if result != SQLITE_OK {
                throw ArchiveError.sqlite(Self.errorMessage(from: handle))
            }
        }
    }

    func step(_ statement: OpaquePointer?) throws -> Bool {
        let result = sqlite3_step(statement)
        switch result {
        case SQLITE_ROW:
            return true
        case SQLITE_DONE:
            return false
        default:
            throw ArchiveError.sqlite(Self.errorMessage(from: handle))
        }
    }

    func execute(_ sql: String, values: [SQLiteValue] = []) throws {
        let statement = try prepare(sql)
        defer { finalize(statement) }
        try bind(statement, values: values)
        while try step(statement) {}
    }

    func executeBatch(_ sql: String) throws {
        if sqlite3_exec(handle, sql, nil, nil, nil) != SQLITE_OK {
            throw ArchiveError.sqlite(Self.errorMessage(from: handle))
        }
    }

    func withTransaction<T>(_ block: (SQLiteDatabase) throws -> T) throws -> T {
        try execute("BEGIN IMMEDIATE TRANSACTION")
        do {
            let result = try block(self)
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    func lastInsertedRowID() -> Int64 {
        sqlite3_last_insert_rowid(handle)
    }

    func int64(from statement: OpaquePointer?, at index: Int32) -> Int64 {
        sqlite3_column_int64(statement, index)
    }

    func bool(from statement: OpaquePointer?, at index: Int32) -> Bool {
        sqlite3_column_int(statement, index) == 1
    }

    func string(from statement: OpaquePointer?, at index: Int32) -> String? {
        guard !isNull(statement, at: index), let pointer = sqlite3_column_text(statement, index) else {
            return nil
        }
        return String(cString: pointer)
    }

    func isNull(_ statement: OpaquePointer?, at index: Int32) -> Bool {
        sqlite3_column_type(statement, index) == SQLITE_NULL
    }

    static func errorMessage(from handle: OpaquePointer?) -> String {
        guard let pointer = sqlite3_errmsg(handle) else {
            return "unknown sqlite error"
        }
        return String(cString: pointer)
    }
}

final class ArchiveRepository {
    private let databasePath: String
    private let defaultTags: [(name: String, category: String, color: String, isSystem: Bool)] = [
        ("2025", "年级", "#2563eb", true),
        ("2026", "年级", "#0f766e", true),
        ("新生", "状态补充", "#f59e0b", true),
        ("转专业", "状态补充", "#dc2626", true),
        ("已进群", "状态补充", "#7c3aed", true),
        ("高意向", "状态补充", "#16a34a", true),
        ("待回访", "状态补充", "#ea580c", true),
        ("家长参与", "风险", "#7c2d12", true),
        ("信息不完整", "风险", "#b91c1c", true),
        ("重复咨询", "风险", "#475569", true),
    ]

    init(databasePath: String) {
        self.databasePath = databasePath
    }

    func loadSnapshot() throws -> BootstrapSnapshot {
        let database = try readOnlyDatabase()
        let tags = try loadAllTags(from: database)
        let settings = try loadSettings(from: database)
        let profiles = try loadProfiles(from: database, includeArchived: true)
        return BootstrapSnapshot(
            profiles: profiles,
            tags: tags,
            settings: settings,
            dashboard: makeDashboard(from: profiles)
        )
    }

    func searchProfiles(filters: SearchFilters) throws -> [StudentProfileRecord] {
        let database = try readOnlyDatabase()
        let profiles = try loadProfiles(from: database, includeArchived: true)
        return filterAndSortProfiles(profiles, filters: filters)
    }

    func loadProfileDetail(profileID: Int64) throws -> ProfileDetailRecord? {
        let database = try readOnlyDatabase()
        guard let profile = try loadProfile(from: database, profileID: profileID) else {
            return nil
        }
        let interactions = try loadInteractions(from: database, profileID: profileID)
        let reminders = try loadReminders(from: database, profileID: profileID)
        return ProfileDetailRecord(profile: profile, interactions: interactions, reminders: reminders)
    }

    func createProfile(input: ProfileInput) throws -> StudentProfileRecord {
        try validateProfileInput(input)
        let database = try writableDatabase()

        return try database.withTransaction { db in
            let now = ArchiveDate.nowISO()
            let fullName = ArchiveText.required(input.fullName)

            try db.execute(
                """
                INSERT INTO student_profiles (
                    full_name, nickname, phone, wechat_id, qq_id, school, major, grade, city,
                    identity_type, focus_directions, demand_summary, intention_level,
                    urgency_level, verification_status, source_channel, current_status,
                    owner_notes, created_at, updated_at, last_contact_at, next_follow_up_at, is_archived
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, 0)
                """,
                values: [
                    .text(fullName),
                    sqlText(input.nickname),
                    sqlText(input.phone),
                    sqlText(input.wechatId),
                    sqlText(input.qqId),
                    sqlText(input.school),
                    sqlText(input.major),
                    sqlText(input.grade),
                    sqlText(input.city),
                    .text(ArchiveText.required(input.identityType)),
                    .text(try encodeStringArray(input.focusDirections)),
                    sqlText(input.demandSummary),
                    .text(ArchiveText.required(input.intentionLevel)),
                    .text(ArchiveText.required(input.urgencyLevel)),
                    .text(ArchiveText.required(input.verificationStatus)),
                    sqlText(input.sourceChannel),
                    .text(ArchiveText.required(input.currentStatus)),
                    sqlText(input.ownerNotes),
                    .text(now),
                    .text(now),
                    sqlText(input.nextFollowUpAt),
                ]
            )

            let profileID = db.lastInsertedRowID()
            try syncProfileTags(db: db, profileID: profileID, tagIDs: input.tagIds)
            try replacePendingReminders(
                db: db,
                studentProfileID: profileID,
                interactionLogID: nil,
                nextFollowUpAt: input.nextFollowUpAt,
                title: "跟进 \(fullName)"
            )

            guard let created = try loadProfile(from: db, profileID: profileID) else {
                throw ArchiveError.invalidDatabase("新建档案后读取失败")
            }
            return created
        }
    }

    func updateProfile(profileID: Int64, input: ProfileInput) throws -> StudentProfileRecord {
        try validateProfileInput(input)
        let database = try writableDatabase()

        return try database.withTransaction { db in
            try db.execute(
                """
                UPDATE student_profiles SET
                    full_name = ?, nickname = ?, phone = ?, wechat_id = ?, qq_id = ?, school = ?, major = ?,
                    grade = ?, city = ?, identity_type = ?, focus_directions = ?, demand_summary = ?,
                    intention_level = ?, urgency_level = ?, verification_status = ?, source_channel = ?,
                    current_status = ?, owner_notes = ?, updated_at = ?, next_follow_up_at = ?
                WHERE id = ?
                """,
                values: [
                    .text(ArchiveText.required(input.fullName)),
                    sqlText(input.nickname),
                    sqlText(input.phone),
                    sqlText(input.wechatId),
                    sqlText(input.qqId),
                    sqlText(input.school),
                    sqlText(input.major),
                    sqlText(input.grade),
                    sqlText(input.city),
                    .text(ArchiveText.required(input.identityType)),
                    .text(try encodeStringArray(input.focusDirections)),
                    sqlText(input.demandSummary),
                    .text(ArchiveText.required(input.intentionLevel)),
                    .text(ArchiveText.required(input.urgencyLevel)),
                    .text(ArchiveText.required(input.verificationStatus)),
                    sqlText(input.sourceChannel),
                    .text(ArchiveText.required(input.currentStatus)),
                    sqlText(input.ownerNotes),
                    .text(ArchiveDate.nowISO()),
                    sqlText(input.nextFollowUpAt),
                    .int64(profileID),
                ]
            )

            try syncProfileTags(db: db, profileID: profileID, tagIDs: input.tagIds)
            try replacePendingReminders(
                db: db,
                studentProfileID: profileID,
                interactionLogID: nil,
                nextFollowUpAt: input.nextFollowUpAt,
                title: "跟进 \(ArchiveText.required(input.fullName))"
            )

            guard let updated = try loadProfile(from: db, profileID: profileID) else {
                throw ArchiveError.invalidDatabase("更新档案后读取失败")
            }
            return updated
        }
    }

    func archiveProfile(profileID: Int64) throws {
        let database = try writableDatabase()
        try database.withTransaction { db in
            let now = ArchiveDate.nowISO()
            try db.execute(
                """
                UPDATE student_profiles
                SET is_archived = 1, current_status = '归档', next_follow_up_at = NULL, updated_at = ?
                WHERE id = ?
                """,
                values: [.text(now), .int64(profileID)]
            )
            try db.execute(
                """
                UPDATE reminder_events
                SET status = 'cancelled', updated_at = ?
                WHERE student_profile_id = ? AND status IN ('pending', 'sent', 'overdue')
                """,
                values: [.text(now), .int64(profileID)]
            )
        }
    }

    func addInteraction(input: InteractionInput) throws -> ProfileDetailRecord {
        try validateInteractionInput(input)
        let database = try writableDatabase()

        return try database.withTransaction { db in
            guard let currentProfile = try loadProfile(from: db, profileID: input.studentProfileId) else {
                throw ArchiveError.validation("未找到对应档案")
            }

            let now = ArchiveDate.nowISO()
            try db.execute(
                """
                INSERT INTO interaction_logs (
                    student_profile_id, contacted_at, channel, summary, details, outcome,
                    verification_todo, next_follow_up_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                values: [
                    .int64(input.studentProfileId),
                    .text(ArchiveText.required(input.contactedAt)),
                    .text(ArchiveText.required(input.channel)),
                    .text(ArchiveText.required(input.summary)),
                    sqlText(input.details),
                    sqlText(input.outcome),
                    sqlText(input.verificationTodo),
                    sqlText(input.nextFollowUpAt),
                    .text(now),
                ]
            )

            let interactionID = db.lastInsertedRowID()
            try db.execute(
                """
                UPDATE student_profiles SET
                    last_contact_at = ?,
                    next_follow_up_at = ?,
                    current_status = COALESCE(?, current_status),
                    intention_level = COALESCE(?, intention_level),
                    verification_status = COALESCE(?, verification_status),
                    updated_at = ?
                WHERE id = ?
                """,
                values: [
                    .text(ArchiveText.required(input.contactedAt)),
                    sqlText(input.nextFollowUpAt),
                    sqlText(input.currentStatus),
                    sqlText(input.intentionLevel),
                    sqlText(input.verificationStatus),
                    .text(now),
                    .int64(input.studentProfileId),
                ]
            )

            if let tagIDs = input.tagIds {
                try syncProfileTags(db: db, profileID: input.studentProfileId, tagIDs: tagIDs)
            }

            try replacePendingReminders(
                db: db,
                studentProfileID: input.studentProfileId,
                interactionLogID: interactionID,
                nextFollowUpAt: input.nextFollowUpAt,
                title: "跟进 \(currentProfile.fullName)"
            )

            guard let detail = try loadProfileDetail(from: db, profileID: input.studentProfileId) else {
                throw ArchiveError.invalidDatabase("新增沟通后读取档案失败")
            }
            return detail
        }
    }

    func createTag(input: CreateTagInput) throws -> TagRecord {
        let database = try writableDatabase()

        return try database.withTransaction { db in
            try db.execute(
                "INSERT INTO tags (name, category, color, is_system) VALUES (?, ?, ?, 0)",
                values: [
                    .text(ArchiveText.required(input.name)),
                    .text(ArchiveText.required(input.category)),
                    .text(ArchiveText.required(input.color)),
                ]
            )

            let tagID = db.lastInsertedRowID()
            guard let tag = try loadTag(from: db, tagID: tagID) else {
                throw ArchiveError.invalidDatabase("标签创建后读取失败")
            }
            return tag
        }
    }

    func updateSettings(input: UpdateSettingsInput) throws -> AppSettingsRecord {
        let database = try writableDatabase()
        try database.execute(
            """
            INSERT INTO app_settings (key, value) VALUES ('defaultReminderTime', ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """,
            values: [.text(ArchiveText.required(input.defaultReminderTime))]
        )
        return try loadSettings(from: database)
    }

    func listDueReminders() throws -> [ReminderEventRecord] {
        let database = try readOnlyDatabase()
        let sql = """
        SELECT id, student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
        FROM reminder_events
        WHERE status IN ('pending', 'sent', 'overdue')
        ORDER BY remind_at ASC
        """
        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }

        let now = Date()
        var reminders: [ReminderEventRecord] = []
        while try database.step(statement) {
            let remindAt = database.string(from: statement, at: 3) ?? ""
            let rawStatus = database.string(from: statement, at: 5) ?? "pending"
            let normalizedStatus: String
            if rawStatus == "pending", let date = ArchiveDate.parse(remindAt), date < now {
                normalizedStatus = "overdue"
            } else {
                normalizedStatus = rawStatus
            }

            reminders.append(
                ReminderEventRecord(
                    id: database.int64(from: statement, at: 0),
                    studentProfileId: database.int64(from: statement, at: 1),
                    interactionLogId: database.isNull(statement, at: 2) ? nil : database.int64(from: statement, at: 2),
                    remindAt: remindAt,
                    title: database.string(from: statement, at: 4) ?? "",
                    status: normalizedStatus,
                    notificationSent: database.bool(from: statement, at: 6),
                    createdAt: database.string(from: statement, at: 7) ?? "",
                    updatedAt: database.string(from: statement, at: 8) ?? ""
                )
            )
        }
        return reminders
    }

    func backupDatabase(destination: URL) throws -> URL {
        if let parent = destination.deletingLastPathComponent().path(percentEncoded: false) as String? {
            try FileManager.default.createDirectory(atPath: parent, withIntermediateDirectories: true)
        }
        try FileManager.default.copyItem(atPath: databasePath, toPath: destination.path)
        let database = try writableDatabase()
        try database.execute(
            """
            INSERT INTO app_settings (key, value) VALUES ('lastBackupAt', ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """,
            values: [.text(ArchiveDate.nowISO())]
        )
        return destination
    }

    @discardableResult
    func exportProfiles(format: ExportFormat, filters: SearchFilters, destination: URL) throws -> URL {
        let profiles = try searchProfiles(filters: filters)
        switch format {
        case .csv:
            try writeProfilesCSV(profiles: profiles, destination: destination)
        case .xlsx:
            try writeProfilesWorkbook(profiles: profiles, destination: destination)
        }
        return destination
    }

    @discardableResult
    func exportSingleProfileTimeline(profileID: Int64, destination: URL) throws -> URL {
        guard let detail = try loadProfileDetail(profileID: profileID) else {
            throw ArchiveError.validation("未找到对应档案")
        }

        if destination.pathExtension.lowercased() == ExportFormat.csv.fileExtension {
            try writeSingleProfileTimelineCSV(detail: detail, destination: destination)
        } else {
            try writeSingleProfileWorkbook(detail: detail, destination: destination)
        }
        return destination
    }

    func findPotentialDuplicates(queryFullName: String, phone: String?, wechatID: String?, qqID: String?) throws -> [DuplicateMatch] {
        let database = try readOnlyDatabase()
        let profiles = try loadProfiles(from: database, includeArchived: true)
        let normalizedName = ArchiveText.required(queryFullName).lowercased()
        let normalizedPhone = ArchiveText.optional(phone)
        let normalizedWechat = ArchiveText.optional(wechatID)
        let normalizedQQ = ArchiveText.optional(qqID)
        var matches: [DuplicateMatch] = []

        for profile in profiles {
            var reasons: [String] = []
            if let normalizedPhone, profile.phone == normalizedPhone {
                reasons.append("手机号一致")
            }
            if let normalizedWechat, profile.wechatId == normalizedWechat {
                reasons.append("微信号一致")
            }
            if let normalizedQQ, profile.qqId == normalizedQQ {
                reasons.append("QQ号一致")
            }

            let currentName = profile.fullName.lowercased()
            if !normalizedName.isEmpty {
                if currentName == normalizedName {
                    reasons.append("姓名一致")
                } else if currentName.contains(normalizedName) || normalizedName.contains(currentName) {
                    reasons.append("姓名相近")
                }
            }

            if !reasons.isEmpty {
                matches.append(DuplicateMatch(profile: profile, reasons: reasons))
            }
        }

        return matches
    }

    func importProfiles(from source: URL) throws -> ImportProfilesResult {
        let drafts = try loadImportDrafts(from: source)
        var profiles = try searchProfiles(
            filters: SearchFilters(
                searchText: nil,
                grade: nil,
                focusDirection: nil,
                status: nil,
                intentionLevel: nil,
                verificationStatus: nil,
                tagIds: [],
                includeArchived: true,
                sortBy: .updatedDesc
            )
        )
        let existingTags = try loadAllTags(from: readOnlyDatabase())
        var tagLookup = Dictionary(uniqueKeysWithValues: existingTags.map { ($0.name, $0) })
        var createdCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var messages: [String] = []

        for (index, draft) in drafts.enumerated() {
            do {
                let tagIDs = try ensureTagIDs(names: draft.tagNames, tagLookup: &tagLookup)

                switch matchImportedProfile(draft, existingProfiles: profiles) {
                case .skip(let reason):
                    skippedCount += 1
                    messages.append("第 \(index + 2) 行：\(reason)")
                case .create:
                    let created = try createProfile(input: buildCreateInput(from: draft, tagIDs: tagIDs))
                    var finalProfile = created
                    if draft.isArchived {
                        try archiveProfile(profileID: created.id)
                        if let archived = try loadProfileDetail(profileID: created.id)?.profile {
                            finalProfile = archived
                        }
                    }
                    profiles.append(finalProfile)
                    createdCount += 1
                case .update(let existing):
                    let updated = try updateProfile(
                        profileID: existing.id,
                        input: mergeImportedDraft(draft, into: existing, tagIDs: tagIDs)
                    )
                    var finalProfile = updated
                    if draft.isArchived {
                        try archiveProfile(profileID: updated.id)
                        if let archived = try loadProfileDetail(profileID: updated.id)?.profile {
                            finalProfile = archived
                        }
                    }
                    if let matchIndex = profiles.firstIndex(where: { $0.id == existing.id }) {
                        profiles[matchIndex] = finalProfile
                    }
                    updatedCount += 1
                }
            } catch {
                skippedCount += 1
                messages.append("第 \(index + 2) 行：\((error as? LocalizedError)?.errorDescription ?? error.localizedDescription)")
            }
        }

        return ImportProfilesResult(
            createdCount: createdCount,
            updatedCount: updatedCount,
            skippedCount: skippedCount,
            messages: messages
        )
    }

    func makeDashboard(from allProfiles: [StudentProfileRecord]) -> DashboardData {
        let activeProfiles = allProfiles.filter { !$0.isArchived }
        let now = Date()
        let calendar = Calendar.current
        let sevenDaysAgo = calendar.date(byAdding: .day, value: -7, to: now) ?? now
        let threeDaysAgo = calendar.date(byAdding: .day, value: -3, to: now) ?? now

        var dueToday: [StudentProfileRecord] = []
        var overdue: [StudentProfileRecord] = []
        var recentCreated: [StudentProfileRecord] = []
        var recentUpdated: [StudentProfileRecord] = []
        var staleHighIntention: [StudentProfileRecord] = []
        var highIntentionCount = 0

        for profile in activeProfiles {
            if profile.intentionLevel == "高" {
                highIntentionCount += 1
            }

            if let nextFollowUp = ArchiveDate.parse(profile.nextFollowUpAt) {
                if calendar.isDate(nextFollowUp, inSameDayAs: now) {
                    dueToday.append(profile)
                }
                if nextFollowUp < now {
                    overdue.append(profile)
                }
            }

            if let createdAt = ArchiveDate.parse(profile.createdAt), createdAt >= sevenDaysAgo {
                recentCreated.append(profile)
            }

            if let updatedAt = ArchiveDate.parse(profile.updatedAt), updatedAt >= sevenDaysAgo {
                recentUpdated.append(profile)
            }

            if profile.intentionLevel == "高" {
                let lastContact = ArchiveDate.parse(profile.lastContactAt)
                if lastContact == nil || lastContact! <= threeDaysAgo {
                    staleHighIntention.append(profile)
                }
            }
        }

        dueToday.sort { ($0.nextFollowUpAt ?? "") < ($1.nextFollowUpAt ?? "") }
        overdue.sort { ($0.nextFollowUpAt ?? "") < ($1.nextFollowUpAt ?? "") }
        recentCreated.sort { $0.createdAt > $1.createdAt }
        recentUpdated.sort { $0.updatedAt > $1.updatedAt }
        staleHighIntention.sort { ($0.lastContactAt ?? "") > ($1.lastContactAt ?? "") }

        return DashboardData(
            stats: DashboardStats(
                totalActive: activeProfiles.count,
                archivedCount: allProfiles.filter(\.isArchived).count,
                todayDueCount: dueToday.count,
                overdueCount: overdue.count,
                highIntentionCount: highIntentionCount
            ),
            dueToday: Array(dueToday.prefix(8)),
            overdue: Array(overdue.prefix(8)),
            recentCreated: Array(recentCreated.prefix(8)),
            recentUpdated: Array(recentUpdated.prefix(8)),
            staleHighIntention: Array(staleHighIntention.prefix(8))
        )
    }

    private func readOnlyDatabase() throws -> SQLiteDatabase {
        try ensureDatabaseReady()
        return try SQLiteDatabase(path: databasePath, readOnly: true)
    }

    private func writableDatabase() throws -> SQLiteDatabase {
        try ensureDatabaseReady()
        return try SQLiteDatabase(path: databasePath, readOnly: false)
    }

    private func ensureDatabaseReady() throws {
        let database = try SQLiteDatabase(path: databasePath, readOnly: false)
        try database.executeBatch(
            """
            CREATE TABLE IF NOT EXISTS student_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                nickname TEXT,
                phone TEXT,
                wechat_id TEXT,
                qq_id TEXT,
                school TEXT,
                major TEXT,
                grade TEXT,
                city TEXT,
                identity_type TEXT NOT NULL,
                focus_directions TEXT NOT NULL,
                demand_summary TEXT,
                intention_level TEXT NOT NULL,
                urgency_level TEXT NOT NULL,
                verification_status TEXT NOT NULL,
                source_channel TEXT,
                current_status TEXT NOT NULL,
                owner_notes TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                last_contact_at TEXT,
                next_follow_up_at TEXT,
                is_archived INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS interaction_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_profile_id INTEGER NOT NULL,
                contacted_at TEXT NOT NULL,
                channel TEXT NOT NULL,
                summary TEXT NOT NULL,
                details TEXT,
                outcome TEXT,
                verification_todo TEXT,
                next_follow_up_at TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                category TEXT NOT NULL,
                color TEXT NOT NULL,
                is_system INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS profile_tags (
                student_profile_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY (student_profile_id, tag_id),
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS saved_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                filters_json TEXT NOT NULL,
                sort_by TEXT NOT NULL,
                show_on_homepage INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS reminder_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_profile_id INTEGER NOT NULL,
                interaction_log_id INTEGER,
                remind_at TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                notification_sent INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(interaction_log_id) REFERENCES interaction_logs(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """
        )

        for tag in defaultTags {
            try database.execute(
                """
                INSERT OR IGNORE INTO tags (name, category, color, is_system)
                VALUES (?, ?, ?, ?)
                """,
                values: [
                    .text(tag.name),
                    .text(tag.category),
                    .text(tag.color),
                    .bool(tag.isSystem),
                ]
            )
        }

        try database.execute(
            """
            INSERT OR IGNORE INTO app_settings (key, value) VALUES ('defaultReminderTime', '09:00')
            """
        )
        try database.execute(
            """
            INSERT OR IGNORE INTO app_settings (key, value) VALUES ('lastBackupAt', '')
            """
        )

        try ensureStudentProfileColumns(in: database)
    }

    private func ensureStudentProfileColumns(in database: SQLiteDatabase) throws {
        if !(try table("student_profiles", hasColumn: "qq_id", in: database)) {
            try database.execute("ALTER TABLE student_profiles ADD COLUMN qq_id TEXT")
        }
    }

    private func table(_ tableName: String, hasColumn columnName: String, in database: SQLiteDatabase) throws -> Bool {
        let statement = try database.prepare("PRAGMA table_info(\(tableName))")
        defer { database.finalize(statement) }

        while try database.step(statement) {
            if database.string(from: statement, at: 1) == columnName {
                return true
            }
        }
        return false
    }

    private func loadProfiles(from database: SQLiteDatabase, includeArchived: Bool) throws -> [StudentProfileRecord] {
        let sql = """
        SELECT id, full_name, nickname, phone, wechat_id, qq_id, school, major, grade, city,
               identity_type, focus_directions, demand_summary, intention_level, urgency_level,
               verification_status, source_channel, current_status, owner_notes, created_at,
               updated_at, last_contact_at, next_follow_up_at, is_archived
        FROM student_profiles
        \(includeArchived ? "" : "WHERE is_archived = 0")
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }

        var profiles: [StudentProfileRecord] = []
        while try database.step(statement) {
            var profile = mapProfile(from: database, statement: statement)
            let tags = try loadTags(for: profile.id, from: database)
            profile.tags = tags
            profile.tagIds = tags.map(\.id)
            profiles.append(profile)
        }
        return profiles
    }

    private func loadProfile(from database: SQLiteDatabase, profileID: Int64) throws -> StudentProfileRecord? {
        let sql = """
        SELECT id, full_name, nickname, phone, wechat_id, qq_id, school, major, grade, city,
               identity_type, focus_directions, demand_summary, intention_level, urgency_level,
               verification_status, source_channel, current_status, owner_notes, created_at,
               updated_at, last_contact_at, next_follow_up_at, is_archived
        FROM student_profiles
        WHERE id = ?
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.int64(profileID)])

        guard try database.step(statement) else {
            return nil
        }

        var profile = mapProfile(from: database, statement: statement)
        let tags = try loadTags(for: profile.id, from: database)
        profile.tags = tags
        profile.tagIds = tags.map(\.id)
        return profile
    }

    private func loadProfileDetail(from database: SQLiteDatabase, profileID: Int64) throws -> ProfileDetailRecord? {
        guard let profile = try loadProfile(from: database, profileID: profileID) else {
            return nil
        }
        let interactions = try loadInteractions(from: database, profileID: profileID)
        let reminders = try loadReminders(from: database, profileID: profileID)
        return ProfileDetailRecord(profile: profile, interactions: interactions, reminders: reminders)
    }

    private func loadTags(for profileID: Int64, from database: SQLiteDatabase) throws -> [TagRecord] {
        let sql = """
        SELECT t.id, t.name, t.category, t.color, t.is_system
        FROM tags t
        INNER JOIN profile_tags pt ON pt.tag_id = t.id
        WHERE pt.student_profile_id = ?
        ORDER BY t.category ASC, t.name ASC
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.int64(profileID)])

        var tags: [TagRecord] = []
        while try database.step(statement) {
            tags.append(
                TagRecord(
                    id: database.int64(from: statement, at: 0),
                    name: database.string(from: statement, at: 1) ?? "",
                    category: database.string(from: statement, at: 2) ?? "",
                    color: database.string(from: statement, at: 3) ?? "#94a3b8",
                    isSystem: database.bool(from: statement, at: 4)
                )
            )
        }
        return tags
    }

    private func loadAllTags(from database: SQLiteDatabase) throws -> [TagRecord] {
        let sql = """
        SELECT id, name, category, color, is_system
        FROM tags
        ORDER BY is_system DESC, category ASC, name ASC
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }

        var tags: [TagRecord] = []
        while try database.step(statement) {
            tags.append(
                TagRecord(
                    id: database.int64(from: statement, at: 0),
                    name: database.string(from: statement, at: 1) ?? "",
                    category: database.string(from: statement, at: 2) ?? "",
                    color: database.string(from: statement, at: 3) ?? "#94a3b8",
                    isSystem: database.bool(from: statement, at: 4)
                )
            )
        }
        return tags
    }

    private func loadTag(from database: SQLiteDatabase, tagID: Int64) throws -> TagRecord? {
        let statement = try database.prepare(
            "SELECT id, name, category, color, is_system FROM tags WHERE id = ?"
        )
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.int64(tagID)])
        guard try database.step(statement) else { return nil }
        return TagRecord(
            id: database.int64(from: statement, at: 0),
            name: database.string(from: statement, at: 1) ?? "",
            category: database.string(from: statement, at: 2) ?? "",
            color: database.string(from: statement, at: 3) ?? "#94a3b8",
            isSystem: database.bool(from: statement, at: 4)
        )
    }

    private func loadInteractions(from database: SQLiteDatabase, profileID: Int64) throws -> [InteractionLogRecord] {
        let sql = """
        SELECT id, student_profile_id, contacted_at, channel, summary, details, outcome, verification_todo, next_follow_up_at, created_at
        FROM interaction_logs
        WHERE student_profile_id = ?
        ORDER BY contacted_at DESC, created_at DESC
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.int64(profileID)])

        var interactions: [InteractionLogRecord] = []
        while try database.step(statement) {
            interactions.append(
                InteractionLogRecord(
                    id: database.int64(from: statement, at: 0),
                    studentProfileId: database.int64(from: statement, at: 1),
                    contactedAt: database.string(from: statement, at: 2) ?? "",
                    channel: database.string(from: statement, at: 3) ?? "",
                    summary: database.string(from: statement, at: 4) ?? "",
                    details: database.string(from: statement, at: 5),
                    outcome: database.string(from: statement, at: 6),
                    verificationTodo: database.string(from: statement, at: 7),
                    nextFollowUpAt: database.string(from: statement, at: 8),
                    createdAt: database.string(from: statement, at: 9) ?? ""
                )
            )
        }
        return interactions
    }

    private func loadReminders(from database: SQLiteDatabase, profileID: Int64) throws -> [ReminderEventRecord] {
        let sql = """
        SELECT id, student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
        FROM reminder_events
        WHERE student_profile_id = ?
        ORDER BY remind_at DESC
        """

        let statement = try database.prepare(sql)
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.int64(profileID)])

        var reminders: [ReminderEventRecord] = []
        while try database.step(statement) {
            reminders.append(
                ReminderEventRecord(
                    id: database.int64(from: statement, at: 0),
                    studentProfileId: database.int64(from: statement, at: 1),
                    interactionLogId: database.isNull(statement, at: 2) ? nil : database.int64(from: statement, at: 2),
                    remindAt: database.string(from: statement, at: 3) ?? "",
                    title: database.string(from: statement, at: 4) ?? "",
                    status: database.string(from: statement, at: 5) ?? "",
                    notificationSent: database.bool(from: statement, at: 6),
                    createdAt: database.string(from: statement, at: 7) ?? "",
                    updatedAt: database.string(from: statement, at: 8) ?? ""
                )
            )
        }
        return reminders
    }

    private func loadSettings(from database: SQLiteDatabase) throws -> AppSettingsRecord {
        let defaultReminderTime = try loadSetting(key: "defaultReminderTime", from: database) ?? "09:00"
        let lastBackupAt = try loadSetting(key: "lastBackupAt", from: database)?
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return AppSettingsRecord(
            defaultReminderTime: defaultReminderTime,
            lastBackupAt: (lastBackupAt?.isEmpty == true) ? nil : lastBackupAt,
            databasePath: database.path
        )
    }

    private func loadSetting(key: String, from database: SQLiteDatabase) throws -> String? {
        let statement = try database.prepare("SELECT value FROM app_settings WHERE key = ?")
        defer { database.finalize(statement) }
        try database.bind(statement, values: [.text(key)])
        guard try database.step(statement) else { return nil }
        return database.string(from: statement, at: 0)
    }

    private func syncProfileTags(db: SQLiteDatabase, profileID: Int64, tagIDs: [Int64]) throws {
        try db.execute(
            "DELETE FROM profile_tags WHERE student_profile_id = ?",
            values: [.int64(profileID)]
        )

        for tagID in tagIDs {
            try db.execute(
                "INSERT INTO profile_tags (student_profile_id, tag_id) VALUES (?, ?)",
                values: [.int64(profileID), .int64(tagID)]
            )
        }
    }

    private func replacePendingReminders(
        db: SQLiteDatabase,
        studentProfileID: Int64,
        interactionLogID: Int64?,
        nextFollowUpAt: String?,
        title: String
    ) throws {
        try db.execute(
            """
            UPDATE reminder_events
            SET status = 'completed', updated_at = ?
            WHERE student_profile_id = ? AND status IN ('pending', 'sent', 'overdue')
            """,
            values: [.text(ArchiveDate.nowISO()), .int64(studentProfileID)]
        )

        if let remindAt = ArchiveText.optional(nextFollowUpAt) {
            let createdAt = ArchiveDate.nowISO()
            try db.execute(
                """
                INSERT INTO reminder_events (
                    student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'pending', 0, ?, ?)
                """,
                values: [
                    .int64(studentProfileID),
                    interactionLogID.map(SQLiteValue.int64) ?? .null,
                    .text(remindAt),
                    .text(title),
                    .text(createdAt),
                    .text(createdAt),
                ]
            )
        }
    }

    private func validateProfileInput(_ input: ProfileInput) throws {
        if ArchiveText.required(input.fullName).isEmpty {
            throw ArchiveError.validation("姓名不能为空")
        }
        if ArchiveText.optional(input.phone) == nil,
           ArchiveText.optional(input.wechatId) == nil,
           ArchiveText.optional(input.qqId) == nil {
            throw ArchiveError.validation("手机号、微信号、QQ号至少填写一个")
        }
    }

    private func validateInteractionInput(_ input: InteractionInput) throws {
        if ArchiveText.required(input.summary).isEmpty {
            throw ArchiveError.validation("沟通摘要不能为空")
        }
        if ArchiveText.required(input.channel).isEmpty {
            throw ArchiveError.validation("沟通方式不能为空")
        }
        if ArchiveText.required(input.contactedAt).isEmpty {
            throw ArchiveError.validation("沟通时间不能为空")
        }
    }

    private func mapProfile(from database: SQLiteDatabase, statement: OpaquePointer?) -> StudentProfileRecord {
        StudentProfileRecord(
            id: database.int64(from: statement, at: 0),
            fullName: database.string(from: statement, at: 1) ?? "",
            nickname: database.string(from: statement, at: 2),
            phone: database.string(from: statement, at: 3),
            wechatId: database.string(from: statement, at: 4),
            qqId: database.string(from: statement, at: 5),
            school: database.string(from: statement, at: 6),
            major: database.string(from: statement, at: 7),
            grade: database.string(from: statement, at: 8),
            city: database.string(from: statement, at: 9),
            identityType: database.string(from: statement, at: 10) ?? "",
            focusDirections: decodeStringArray(database.string(from: statement, at: 11)),
            demandSummary: database.string(from: statement, at: 12),
            intentionLevel: database.string(from: statement, at: 13) ?? "",
            urgencyLevel: database.string(from: statement, at: 14) ?? "",
            verificationStatus: database.string(from: statement, at: 15) ?? "",
            sourceChannel: database.string(from: statement, at: 16),
            currentStatus: database.string(from: statement, at: 17) ?? "",
            ownerNotes: database.string(from: statement, at: 18),
            createdAt: database.string(from: statement, at: 19) ?? "",
            updatedAt: database.string(from: statement, at: 20) ?? "",
            lastContactAt: database.string(from: statement, at: 21),
            nextFollowUpAt: database.string(from: statement, at: 22),
            isArchived: database.bool(from: statement, at: 23),
            tagIds: [],
            tags: []
        )
    }

    private func decodeStringArray(_ value: String?) -> [String] {
        guard let value, let data = value.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([String].self, from: data)) ?? []
    }

    private func encodeStringArray(_ values: [String]) throws -> String {
        let normalized = values.map(ArchiveText.required).filter { !$0.isEmpty }
        let array = normalized.isEmpty ? ["暂不明确"] : normalized
        let data = try JSONEncoder().encode(array)
        guard let string = String(data: data, encoding: .utf8) else {
            throw ArchiveError.invalidDatabase("无法编码关注方向")
        }
        return string
    }

    private func filterAndSortProfiles(_ profiles: [StudentProfileRecord], filters: SearchFilters) -> [StudentProfileRecord] {
        let filtered = profiles.filter { profile in
            profileMatches(profile: profile, filters: filters)
        }
        return sortProfiles(filtered, sortBy: filters.sortBy ?? .updatedDesc)
    }

    private func profileMatches(profile: StudentProfileRecord, filters: SearchFilters) -> Bool {
        if !filters.includeArchived, profile.isArchived {
            return false
        }

        if let keyword = ArchiveText.optional(filters.searchText)?.lowercased() {
            var haystacks: [String] = []
            haystacks.append(profile.fullName)
            haystacks.append(profile.nickname ?? "")
            haystacks.append(profile.phone ?? "")
            haystacks.append(profile.wechatId ?? "")
            haystacks.append(profile.qqId ?? "")
            haystacks.append(profile.school ?? "")
            haystacks.append(profile.major ?? "")
            haystacks.append(profile.city ?? "")
            haystacks.append(profile.sourceChannel ?? "")
            haystacks.append(profile.ownerNotes ?? "")
            haystacks.append(profile.demandSummary ?? "")
            haystacks.append(profile.tags.map(\.name).joined(separator: " "))

            let matched = haystacks.contains { value in
                value.lowercased().contains(keyword)
            }
            if !matched {
                return false
            }
        }

        if let grade = ArchiveText.optional(filters.grade), profile.grade != grade {
            return false
        }

        if let focusDirection = ArchiveText.optional(filters.focusDirection),
           !profile.focusDirections.contains(focusDirection) {
            return false
        }

        if let status = ArchiveText.optional(filters.status), profile.currentStatus != status {
            return false
        }

        if let level = ArchiveText.optional(filters.intentionLevel), profile.intentionLevel != level {
            return false
        }

        if let verificationStatus = ArchiveText.optional(filters.verificationStatus),
           profile.verificationStatus != verificationStatus {
            return false
        }

        if !filters.tagIds.isEmpty && !filters.tagIds.allSatisfy(profile.tagIds.contains) {
            return false
        }

        return true
    }

    private func sortProfiles(_ profiles: [StudentProfileRecord], sortBy: ProfileSortBy) -> [StudentProfileRecord] {
        switch sortBy {
        case .updatedDesc:
            profiles.sorted { $0.updatedAt > $1.updatedAt }
        case .createdDesc:
            profiles.sorted { $0.createdAt > $1.createdAt }
        case .lastContactDesc:
            profiles.sorted { ($0.lastContactAt ?? "") > ($1.lastContactAt ?? "") }
        case .nextFollowUpAsc:
            profiles.sorted { left, right in
                switch (left.nextFollowUpAt, right.nextFollowUpAt) {
                case let (.some(leftValue), .some(rightValue)):
                    return leftValue < rightValue
                case (.some, .none):
                    return true
                case (.none, .some):
                    return false
                case (.none, .none):
                    return left.updatedAt > right.updatedAt
                }
            }
        }
    }

    private func writeProfilesCSV(profiles: [StudentProfileRecord], destination: URL) throws {
        let rows = profileRows(profiles: profiles)
        try writeCSV(rows: rows, destination: destination)
    }

    private func writeSingleProfileTimelineCSV(detail: ProfileDetailRecord, destination: URL) throws {
        var rows: [[String]] = [
            ["姓名", "沟通时间", "沟通方式", "摘要", "详细备注", "结果", "待核实", "下次跟进"],
        ]
        for interaction in detail.interactions {
            rows.append([
                detail.profile.fullName,
                interaction.contactedAt,
                interaction.channel,
                interaction.summary,
                interaction.details ?? "",
                interaction.outcome ?? "",
                interaction.verificationTodo ?? "",
                interaction.nextFollowUpAt ?? "",
            ])
        }
        try writeCSV(rows: rows, destination: destination)
    }

    private func writeCSV(rows: [[String]], destination: URL) throws {
        let csv = rows.map { row in
            row.map(csvEscape).joined(separator: ",")
        }.joined(separator: "\n")

        try csv.write(to: destination, atomically: true, encoding: .utf8)
    }

    private func csvEscape(_ value: String) -> String {
        let escaped = value.replacingOccurrences(of: "\"", with: "\"\"")
        if escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"") {
            return "\"\(escaped)\""
        }
        return escaped
    }

    private func profileRows(profiles: [StudentProfileRecord]) -> [[String]] {
        var rows: [[String]] = [
            [
                "姓名", "昵称", "手机号", "微信号", "QQ号", "学校", "专业", "年级", "城市", "身份类型",
                "关注方向", "需求摘要", "报名意向", "紧急程度", "核实状态", "来源渠道", "当前状态",
                "内部备注", "创建时间", "更新时间", "最近联系", "下次跟进", "是否归档", "标签"
            ],
        ]

        for profile in profiles {
            rows.append([
                profile.fullName,
                profile.nickname ?? "",
                profile.phone ?? "",
                profile.wechatId ?? "",
                profile.qqId ?? "",
                profile.school ?? "",
                profile.major ?? "",
                profile.grade ?? "",
                profile.city ?? "",
                profile.identityType,
                profile.focusDirections.joined(separator: " / "),
                profile.demandSummary ?? "",
                profile.intentionLevel,
                profile.urgencyLevel,
                profile.verificationStatus,
                profile.sourceChannel ?? "",
                profile.currentStatus,
                profile.ownerNotes ?? "",
                profile.createdAt,
                profile.updatedAt,
                profile.lastContactAt ?? "",
                profile.nextFollowUpAt ?? "",
                profile.isArchived ? "是" : "否",
                profile.tags.map(\.name).joined(separator: " / "),
            ])
        }
        return rows
    }

    private func writeProfilesWorkbook(profiles: [StudentProfileRecord], destination: URL) throws {
        let profileSheet = XLSXSheet(name: "档案", rows: profileRows(profiles: profiles))
        var interactionRows: [[String]] = [
            ["姓名", "沟通时间", "沟通方式", "摘要", "详细备注", "结果", "待核实", "下次跟进", "创建时间"],
        ]
        for profile in profiles {
            if let detail = try loadProfileDetail(profileID: profile.id) {
                for interaction in detail.interactions {
                    interactionRows.append([
                        profile.fullName,
                        interaction.contactedAt,
                        interaction.channel,
                        interaction.summary,
                        interaction.details ?? "",
                        interaction.outcome ?? "",
                        interaction.verificationTodo ?? "",
                        interaction.nextFollowUpAt ?? "",
                        interaction.createdAt,
                    ])
                }
            }
        }
        let interactionSheet = XLSXSheet(name: "跟进记录", rows: interactionRows)
        try XLSXWorkbookWriter(sheets: [profileSheet, interactionSheet]).write(to: destination)
    }

    private func writeSingleProfileWorkbook(detail: ProfileDetailRecord, destination: URL) throws {
        let profileSheet = XLSXSheet(name: "档案", rows: profileRows(profiles: [detail.profile]))
        var timelineRows: [[String]] = [
            ["沟通时间", "沟通方式", "摘要", "详细备注", "结果", "待核实", "下次跟进", "创建时间"],
        ]
        for interaction in detail.interactions {
            timelineRows.append([
                interaction.contactedAt,
                interaction.channel,
                interaction.summary,
                interaction.details ?? "",
                interaction.outcome ?? "",
                interaction.verificationTodo ?? "",
                interaction.nextFollowUpAt ?? "",
                interaction.createdAt,
            ])
        }
        let timelineSheet = XLSXSheet(name: "时间线", rows: timelineRows)
        try XLSXWorkbookWriter(sheets: [profileSheet, timelineSheet]).write(to: destination)
    }

    private func loadImportDrafts(from source: URL) throws -> [ImportedProfileDraft] {
        let text = try loadCSVText(from: source)
        let rows = parseCSV(text)
        guard let headerRow = rows.first else {
            throw ArchiveError.validation("导入文件为空")
        }

        let headers = headerRow.map(normalizeHeader)
        var drafts: [ImportedProfileDraft] = []

        for row in rows.dropFirst() {
            if row.allSatisfy({ ArchiveText.required($0).isEmpty }) {
                continue
            }

            func value(_ aliases: [String]) -> String? {
                for alias in aliases {
                    if let index = headers.firstIndex(of: alias), index < row.count {
                        let raw = row[index].replacingOccurrences(of: "\u{FEFF}", with: "")
                        if let normalized = ArchiveText.optional(raw) {
                            return normalized
                        }
                    }
                }
                return nil
            }

            guard let fullName = value(["姓名", "fullname", "name"]) else {
                throw ArchiveError.validation("导入文件缺少姓名列，或存在空姓名记录")
            }

            drafts.append(
                ImportedProfileDraft(
                    fullName: fullName,
                    nickname: value(["昵称", "nickname"]),
                    phone: value(["手机号", "phone", "mobile"]),
                    wechatId: value(["微信号", "微信", "wechatid", "wechat"]),
                    qqId: value(["qq号", "qq", "qqid"]),
                    school: value(["学校", "school"]),
                    major: value(["专业", "major"]),
                    grade: value(["年级", "年级/届别", "grade"]),
                    city: value(["城市", "city"]),
                    identityType: value(["身份类型", "identitytype"]),
                    focusDirections: splitMultiValue(value(["关注方向", "focusdirections"])),
                    demandSummary: value(["需求摘要", "demandsummary"]),
                    intentionLevel: value(["报名意向", "意向", "intentionlevel"]),
                    urgencyLevel: value(["紧急程度", "urgencylevel"]),
                    verificationStatus: value(["核实状态", "verificationstatus"]),
                    sourceChannel: value(["来源渠道", "sourcechannel"]),
                    currentStatus: value(["当前状态", "currentstatus"]),
                    ownerNotes: value(["内部备注", "备注", "ownernotes"]),
                    nextFollowUpAt: normalizeImportedDate(value(["下次跟进", "nextfollowupat"])),
                    isArchived: parseArchivedFlag(value(["是否归档", "isarchived"])),
                    tagNames: splitMultiValue(value(["标签", "tags"]))
                )
            )
        }

        return drafts
    }

    private func loadCSVText(from source: URL) throws -> String {
        let data = try Data(contentsOf: source)
        if var text = String(data: data, encoding: .utf8) {
            text = text.replacingOccurrences(of: "\r\n", with: "\n")
            text = text.replacingOccurrences(of: "\r", with: "\n")
            return text
        }
        if var text = String(data: data, encoding: .unicode) {
            text = text.replacingOccurrences(of: "\r\n", with: "\n")
            text = text.replacingOccurrences(of: "\r", with: "\n")
            return text
        }
        throw ArchiveError.validation("暂不支持该编码的 CSV 文件，请使用 UTF-8 格式")
    }

    private func parseCSV(_ text: String) -> [[String]] {
        let characters = Array(text)
        var rows: [[String]] = []
        var currentRow: [String] = []
        var currentField = ""
        var isInsideQuotes = false
        var index = 0

        while index < characters.count {
            let character = characters[index]
            if isInsideQuotes {
                if character == "\"" {
                    if index + 1 < characters.count, characters[index + 1] == "\"" {
                        currentField.append("\"")
                        index += 1
                    } else {
                        isInsideQuotes = false
                    }
                } else {
                    currentField.append(character)
                }
            } else {
                switch character {
                case "\"":
                    isInsideQuotes = true
                case ",":
                    currentRow.append(currentField)
                    currentField = ""
                case "\n":
                    currentRow.append(currentField)
                    rows.append(currentRow)
                    currentRow = []
                    currentField = ""
                default:
                    currentField.append(character)
                }
            }
            index += 1
        }

        if !currentField.isEmpty || !currentRow.isEmpty {
            currentRow.append(currentField)
            rows.append(currentRow)
        }

        if !rows.isEmpty, !rows[0].isEmpty {
            rows[0][0] = rows[0][0].replacingOccurrences(of: "\u{FEFF}", with: "")
        }
        return rows
    }

    private func normalizeHeader(_ value: String) -> String {
        ArchiveText.required(value)
            .lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "/", with: "")
    }

    private func splitMultiValue(_ value: String?) -> [String] {
        guard let value = ArchiveText.optional(value) else { return [] }
        let normalized = value
            .replacingOccurrences(of: " / ", with: "|")
            .replacingOccurrences(of: "/", with: "|")
            .replacingOccurrences(of: "／", with: "|")
            .replacingOccurrences(of: "、", with: "|")
            .replacingOccurrences(of: "，", with: "|")
            .replacingOccurrences(of: ",", with: "|")
            .replacingOccurrences(of: "；", with: "|")
            .replacingOccurrences(of: ";", with: "|")

        return normalized
            .split(separator: "|")
            .map { ArchiveText.required(String($0)) }
            .filter { !$0.isEmpty }
    }

    private func normalizeImportedDate(_ value: String?) -> String? {
        guard let value = ArchiveText.optional(value) else { return nil }
        if let date = ArchiveDate.parse(value) {
            return ArchiveDate.isoString(from: date)
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        if let date = formatter.date(from: value) {
            return ArchiveDate.isoString(from: date)
        }
        return nil
    }

    private func parseArchivedFlag(_ value: String?) -> Bool {
        guard let value = ArchiveText.optional(value)?.lowercased() else { return false }
        return ["是", "true", "1", "yes", "archived", "归档"].contains(value)
    }

    private func ensureTagIDs(names: [String], tagLookup: inout [String: TagRecord]) throws -> [Int64] {
        guard !names.isEmpty else { return [] }
        var tagIDs: [Int64] = []

        for name in names {
            if let tag = tagLookup[name] {
                tagIDs.append(tag.id)
            } else {
                let created = try createTag(
                    input: CreateTagInput(
                        name: name,
                        category: "自定义",
                        color: AppOptions.tagColorOptions.first ?? "#2563eb"
                    )
                )
                tagLookup[name] = created
                tagIDs.append(created.id)
            }
        }

        return Array(Set(tagIDs)).sorted()
    }

    private func buildCreateInput(from draft: ImportedProfileDraft, tagIDs: [Int64]) -> ProfileInput {
        ProfileInput(
            fullName: draft.fullName,
            nickname: draft.nickname,
            phone: draft.phone,
            wechatId: draft.wechatId,
            qqId: draft.qqId,
            school: draft.school,
            major: draft.major,
            grade: draft.grade,
            city: draft.city,
            identityType: draft.identityType ?? AppOptions.identityTypes[2],
            focusDirections: draft.focusDirections.isEmpty ? ["暂不明确"] : draft.focusDirections,
            demandSummary: draft.demandSummary,
            intentionLevel: draft.intentionLevel ?? AppOptions.intentionLevels[1],
            urgencyLevel: draft.urgencyLevel ?? AppOptions.urgencyLevels[1],
            verificationStatus: draft.verificationStatus ?? AppOptions.verificationStatuses[0],
            sourceChannel: draft.sourceChannel,
            currentStatus: draft.currentStatus ?? (draft.isArchived ? "归档" : AppOptions.profileStatuses[0]),
            ownerNotes: draft.ownerNotes,
            nextFollowUpAt: draft.nextFollowUpAt,
            tagIds: tagIDs
        )
    }

    private func mergeImportedDraft(_ draft: ImportedProfileDraft, into profile: StudentProfileRecord, tagIDs: [Int64]) -> ProfileInput {
        ProfileInput(
            fullName: draft.fullName,
            nickname: draft.nickname ?? profile.nickname,
            phone: draft.phone ?? profile.phone,
            wechatId: draft.wechatId ?? profile.wechatId,
            qqId: draft.qqId ?? profile.qqId,
            school: draft.school ?? profile.school,
            major: draft.major ?? profile.major,
            grade: draft.grade ?? profile.grade,
            city: draft.city ?? profile.city,
            identityType: draft.identityType ?? profile.identityType,
            focusDirections: draft.focusDirections.isEmpty ? profile.focusDirections : draft.focusDirections,
            demandSummary: draft.demandSummary ?? profile.demandSummary,
            intentionLevel: draft.intentionLevel ?? profile.intentionLevel,
            urgencyLevel: draft.urgencyLevel ?? profile.urgencyLevel,
            verificationStatus: draft.verificationStatus ?? profile.verificationStatus,
            sourceChannel: draft.sourceChannel ?? profile.sourceChannel,
            currentStatus: draft.currentStatus ?? (draft.isArchived ? "归档" : profile.currentStatus),
            ownerNotes: draft.ownerNotes ?? profile.ownerNotes,
            nextFollowUpAt: draft.nextFollowUpAt ?? profile.nextFollowUpAt,
            tagIds: tagIDs.isEmpty ? profile.tagIds : tagIDs
        )
    }

    private func matchImportedProfile(_ draft: ImportedProfileDraft, existingProfiles: [StudentProfileRecord]) -> ImportProfileMatch {
        var matches: [StudentProfileRecord] = []

        if let phone = draft.phone {
            matches.append(contentsOf: existingProfiles.filter { $0.phone == phone })
        }
        if let wechatId = draft.wechatId {
            matches.append(contentsOf: existingProfiles.filter { $0.wechatId == wechatId })
        }
        if let qqId = draft.qqId {
            matches.append(contentsOf: existingProfiles.filter { $0.qqId == qqId })
        }

        var uniqueMatches: [StudentProfileRecord] = []
        var seenIDs = Set<Int64>()
        for profile in matches where !seenIDs.contains(profile.id) {
            seenIDs.insert(profile.id)
            uniqueMatches.append(profile)
        }

        if uniqueMatches.count > 1 {
            let names = uniqueMatches.map(\.fullName).joined(separator: "、")
            return .skip("匹配到多个现有档案：\(names)")
        }
        if let profile = uniqueMatches.first {
            return .update(profile)
        }
        return .create
    }

    private func sqlText(_ value: String?) -> SQLiteValue {
        if let normalized = ArchiveText.optional(value) {
            return .text(normalized)
        }
        return .null
    }
}

private struct ImportedProfileDraft {
    let fullName: String
    let nickname: String?
    let phone: String?
    let wechatId: String?
    let qqId: String?
    let school: String?
    let major: String?
    let grade: String?
    let city: String?
    let identityType: String?
    let focusDirections: [String]
    let demandSummary: String?
    let intentionLevel: String?
    let urgencyLevel: String?
    let verificationStatus: String?
    let sourceChannel: String?
    let currentStatus: String?
    let ownerNotes: String?
    let nextFollowUpAt: String?
    let isArchived: Bool
    let tagNames: [String]
}

private enum ImportProfileMatch {
    case create
    case update(StudentProfileRecord)
    case skip(String)
}

private struct XLSXSheet {
    let name: String
    let rows: [[String]]
}

private struct XLSXWorkbookWriter {
    let sheets: [XLSXSheet]

    func write(to destination: URL) throws {
        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDirectory) }

        let archive = try Archive(url: destination, accessMode: .create)
        let files = try generateWorkbookFiles(in: tempDirectory)

        for (relativePath, fileURL) in files {
            try archive.addEntry(with: relativePath, fileURL: fileURL, compressionMethod: .deflate)
        }
    }

    private func generateWorkbookFiles(in directory: URL) throws -> [(String, URL)] {
        let definitions: [(String, String)] = [
            ("[Content_Types].xml", contentTypesXML()),
            ("_rels/.rels", rootRelationshipsXML()),
            ("docProps/app.xml", appXML()),
            ("docProps/core.xml", coreXML()),
            ("xl/workbook.xml", workbookXML()),
            ("xl/_rels/workbook.xml.rels", workbookRelationshipsXML()),
        ] + sheetDefinitions()

        var files: [(String, URL)] = []
        for (relativePath, contents) in definitions {
            let fileURL = directory.appendingPathComponent(relativePath)
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try contents.data(using: .utf8)?.write(to: fileURL)
            files.append((relativePath, fileURL))
        }
        return files
    }

    private func sheetDefinitions() -> [(String, String)] {
        sheets.enumerated().map { index, sheet in
            ("xl/worksheets/sheet\(index + 1).xml", sheetXML(sheet))
        }
    }

    private func contentTypesXML() -> String {
        var overrides = """
        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
        <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
        <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
        """
        for index in sheets.indices {
            overrides += "\n<Override PartName=\"/xl/worksheets/sheet\(index + 1).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          \(overrides)
        </Types>
        """
    }

    private func rootRelationshipsXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
        </Relationships>
        """
    }

    private func appXML() -> String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
          <Application>YinggeNativeApp</Application>
        </Properties>
        """
    }

    private func coreXML() -> String {
        let timestamp = ISO8601DateFormatter().string(from: Date())
        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <dc:creator>YinggeNativeApp</dc:creator>
          <cp:lastModifiedBy>YinggeNativeApp</cp:lastModifiedBy>
          <dcterms:created xsi:type="dcterms:W3CDTF">\(timestamp)</dcterms:created>
          <dcterms:modified xsi:type="dcterms:W3CDTF">\(timestamp)</dcterms:modified>
        </cp:coreProperties>
        """
    }

    private func workbookXML() -> String {
        let sheetXML = sheets.enumerated().map { index, sheet in
            "<sheet name=\"\(xmlEscape(sheet.name))\" sheetId=\"\(index + 1)\" r:id=\"rId\(index + 1)\"/>"
        }.joined()

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>\(sheetXML)</sheets>
        </workbook>
        """
    }

    private func workbookRelationshipsXML() -> String {
        let relationships = sheets.enumerated().map { index, _ in
            "<Relationship Id=\"rId\(index + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\(index + 1).xml\"/>"
        }.joined()

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          \(relationships)
        </Relationships>
        """
    }

    private func sheetXML(_ sheet: XLSXSheet) -> String {
        let rowsXML = sheet.rows.enumerated().map { rowIndex, row in
            let cellsXML = row.enumerated().map { columnIndex, value in
                let reference = "\(columnName(for: columnIndex))\(rowIndex + 1)"
                return """
                <c r="\(reference)" t="inlineStr"><is><t>\(xmlEscape(value))</t></is></c>
                """
            }.joined()
            return "<row r=\"\(rowIndex + 1)\">\(cellsXML)</row>"
        }.joined()

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>\(rowsXML)</sheetData>
        </worksheet>
        """
    }

    private func columnName(for index: Int) -> String {
        var value = index
        var result = ""
        repeat {
            result = String(UnicodeScalar(65 + (value % 26))!) + result
            value = value / 26 - 1
        } while value >= 0
        return result
    }

    private func xmlEscape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }
}
