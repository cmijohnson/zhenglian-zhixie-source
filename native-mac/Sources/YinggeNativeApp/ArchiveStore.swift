import Foundation
import SwiftUI

#if canImport(AppKit)
import AppKit
import UniformTypeIdentifiers
#endif

@MainActor
package final class ArchiveStore: ObservableObject {
    @Published var selectedSection: AppSection = .dashboard
    @Published var searchText = ""
    @Published var includeArchived = false
    @Published var statusFilter = "全部"
    @Published var selectedTagIDs: Set<Int64> = []

    @Published private(set) var profiles: [StudentProfileRecord] = []
    @Published private(set) var allTags: [TagRecord] = []
    @Published private(set) var dashboard = DashboardData(
        stats: DashboardStats(totalActive: 0, archivedCount: 0, todayDueCount: 0, overdueCount: 0, highIntentionCount: 0),
        dueToday: [],
        overdue: [],
        recentCreated: [],
        recentUpdated: [],
        staleHighIntention: []
    )
    @Published private(set) var settings = AppSettingsRecord(defaultReminderTime: "09:00", lastBackupAt: nil, databasePath: ArchivePaths.defaultDatabasePath())
    @Published private(set) var dueReminders: [ReminderEventRecord] = []
    @Published private(set) var selectedDetail: ProfileDetailRecord?
    @Published private(set) var notificationPermission: ArchiveNotificationPermission = .notDetermined
    @Published var selectedProfileID: Int64?

    @Published var isLoading = false
    @Published var busyMessage: String?
    @Published var errorMessage: String?
    @Published var successMessage: String?

    @Published var showingProfileSheet = false
    @Published var showingInteractionSheet = false
    @Published var showingSettingsTagCreator = false
    @Published var profileForm = ProfileFormModel()
    @Published var interactionForm = InteractionFormModel(defaultStatus: "跟进中", existingTagIDs: [])
    @Published var tagForm = TagFormModel()
    @Published var reminderTimeDraft = "09:00"

    private var editingProfileID: Int64?
    private var repository: ArchiveRepository
    private let notificationCoordinator = ArchiveNotificationCoordinator()

    package init() {
        let path = ArchivePaths.defaultDatabasePath()
        repository = ArchiveRepository(databasePath: path)
    }

    var filteredProfiles: [StudentProfileRecord] {
        profiles
            .filter { includeArchived || !$0.isArchived }
            .filter { statusFilter == "全部" || $0.currentStatus == statusFilter }
            .filter { selectedTagIDs.isEmpty || selectedTagIDs.allSatisfy($0.tagIds.contains) }
            .filter { profile in
                let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                guard !keyword.isEmpty else { return true }
                return matchesSearch(profile: profile, keyword: keyword)
            }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    var availableStatuses: [String] {
        ["全部"] + Set(profiles.map(\.currentStatus)).sorted()
    }

    var selectedProfile: StudentProfileRecord? {
        selectedDetail?.profile ?? profiles.first(where: { $0.id == selectedProfileID })
    }

    var isEditingProfileSheet: Bool {
        editingProfileID != nil
    }

    var currentFilters: SearchFilters {
        SearchFilters(
            searchText: ArchiveText.optional(searchText),
            grade: nil,
            focusDirection: nil,
            status: statusFilter == "全部" ? nil : statusFilter,
            intentionLevel: nil,
            verificationStatus: nil,
            tagIds: Array(selectedTagIDs).sorted(),
            includeArchived: includeArchived,
            sortBy: .updatedDesc
        )
    }

    func load() {
        runBusy("正在读取数据库") {
            let snapshot = try repository.loadSnapshot()
            profiles = snapshot.profiles
            allTags = snapshot.tags
            settings = snapshot.settings
            reminderTimeDraft = snapshot.settings.defaultReminderTime
            dashboard = snapshot.dashboard
            dueReminders = try repository.listDueReminders()

            if selectedProfileID == nil {
                selectedProfileID = filteredProfiles.first?.id
            } else if profiles.contains(where: { $0.id == selectedProfileID }) == false {
                selectedProfileID = filteredProfiles.first?.id
            }

            try refreshSelectedDetail()
            syncNotifications()
            updateNotificationPermission()
        }
    }

    func reload() {
        load()
    }

    func selectProfile(_ profileID: Int64?) {
        selectedProfileID = profileID
        runBusy("正在读取档案详情") {
            try refreshSelectedDetail()
        }
    }

    func openProfile(_ profileID: Int64) {
        selectedSection = .profiles
        selectProfile(profileID)
    }

    func presentCreateProfile() {
        editingProfileID = nil
        profileForm = ProfileFormModel()
        showingProfileSheet = true
    }

    func presentEditProfile() {
        guard let profile = selectedProfile else { return }
        editingProfileID = profile.id
        profileForm = ProfileFormModel(profile: profile)
        showingProfileSheet = true
    }

    @discardableResult
    func saveProfileForm() -> Bool {
        return runBusy(editingProfileID == nil ? "正在创建档案" : "正在保存档案") {
            let input = profileForm.toInput()
            if editingProfileID == nil {
                let duplicates = try repository.findPotentialDuplicates(
                    queryFullName: input.fullName,
                    phone: input.phone,
                    wechatID: input.wechatId,
                    qqID: input.qqId
                )
                if !duplicates.isEmpty {
                    throw ArchiveError.validation("检测到可能重复档案，请先搜索确认后再创建。")
                }
                let profile = try repository.createProfile(input: input)
                selectedProfileID = profile.id
                successMessage = "已创建档案：\(profile.fullName)"
            } else if let editingProfileID {
                let profile = try repository.updateProfile(profileID: editingProfileID, input: input)
                selectedProfileID = profile.id
                successMessage = "已更新档案：\(profile.fullName)"
            }

            try reloadDataKeepingSelection()
            showingProfileSheet = false
        }
    }

    func archiveSelectedProfile() {
        guard let profile = selectedProfile else { return }
        runBusy("正在归档档案") {
            try repository.archiveProfile(profileID: profile.id)
            successMessage = "已归档：\(profile.fullName)"
            selectedProfileID = nil
            try reloadDataKeepingSelection()
        }
    }

    func presentInteractionSheet() {
        guard let profile = selectedProfile else { return }
        interactionForm = InteractionFormModel(defaultStatus: profile.currentStatus, existingTagIDs: profile.tagIds)
        showingInteractionSheet = true
    }

    @discardableResult
    func saveInteractionForm() -> Bool {
        guard let profile = selectedProfile else { return false }
        return runBusy("正在记录沟通") {
            let detail = try repository.addInteraction(input: interactionForm.toInput(profileID: profile.id))
            selectedProfileID = detail.profile.id
            successMessage = "已记录与 \(detail.profile.fullName) 的沟通"
            try reloadDataKeepingSelection()
            showingInteractionSheet = false
        }
    }

    func createTagFromSettings() {
        runBusy("正在创建标签") {
            let tag = try repository.createTag(input: tagForm.toInput())
            tagForm = TagFormModel()
            successMessage = "已创建标签：\(tag.name)"
            try reloadDataKeepingSelection()
        }
    }

    func saveReminderSettings() {
        runBusy("正在保存设置") {
            settings = try repository.updateSettings(input: UpdateSettingsInput(defaultReminderTime: reminderTimeDraft))
            successMessage = "已保存默认提醒时间"
        }
    }

    func exportProfiles(format: ExportFormat) {
        guard let destination = saveURL(
            title: "导出档案",
            defaultName: "学生档案导出-\(timestamp())",
            fileExtension: format.fileExtension
        ) else {
            return
        }

        runBusy("正在导出\(format.label)") {
            let exportedURL = try repository.exportProfiles(format: format, filters: currentFilters, destination: destination)
            successMessage = "已导出到：\(exportedURL.path)"
        }
    }

    func importProfilesFromCSV() {
        #if canImport(AppKit)
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.commaSeparatedText, .plainText]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.message = "选择要导入的客户 CSV 文件"

        guard panel.runModal() == .OK, let url = panel.url else {
            return
        }

        runBusy("正在批量导入客户") {
            let result = try repository.importProfiles(from: url)
            try reloadDataKeepingSelection()

            var summary = "导入完成：新增 \(result.createdCount) 条，更新 \(result.updatedCount) 条，跳过 \(result.skippedCount) 条。"
            if !result.messages.isEmpty {
                summary += "\n\n" + result.messages.prefix(6).joined(separator: "\n")
                if result.messages.count > 6 {
                    summary += "\n还有 \(result.messages.count - 6) 条提示未展开。"
                }
            }
            successMessage = summary
        }
        #endif
    }

    func exportSelectedTimeline(format: ExportFormat) {
        guard let profile = selectedProfile else { return }
        guard let destination = saveURL(
            title: "导出单个档案",
            defaultName: "\(profile.fullName)-时间线-\(timestamp())",
            fileExtension: format.fileExtension
        ) else {
            return
        }

        runBusy("正在导出档案时间线") {
            let exportedURL = try repository.exportSingleProfileTimeline(profileID: profile.id, destination: destination)
            successMessage = "已导出到：\(exportedURL.path)"
        }
    }

    func backupDatabase() {
        guard let destination = saveURL(
            title: "备份数据库",
            defaultName: "yingge-student-archive-backup-\(timestamp())",
            fileExtension: "sqlite"
        ) else {
            return
        }

        runBusy("正在备份数据库") {
            let backupURL = try repository.backupDatabase(destination: destination)
            settings = try repository.loadSnapshot().settings
            successMessage = "数据库备份已保存到：\(backupURL.path)"
        }
    }

    func requestNotificationPermission() {
        Task {
            let permission = await notificationCoordinator.requestAuthorization()
            await MainActor.run {
                notificationPermission = permission
                if permission == .authorized {
                    syncNotifications()
                }
            }
        }
    }

    func clearError() {
        errorMessage = nil
    }

    func clearSuccess() {
        successMessage = nil
    }

    func useDatabase(path: String) {
        repository = ArchiveRepository(databasePath: path)
        UserDefaults.standard.set(path, forKey: ArchivePaths.savedPathKey)
        load()
    }

    func resetToDefaultDatabase() {
        UserDefaults.standard.removeObject(forKey: ArchivePaths.savedPathKey)
        useDatabase(path: ArchivePaths.defaultDatabasePath())
    }

    #if canImport(AppKit)
    func chooseDatabase() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.database, .data]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.message = "选择英格学生档案数据库文件"

        if panel.runModal() == .OK, let url = panel.url {
            useDatabase(path: url.path)
        }
    }
    #endif

    @discardableResult
    private func runBusy(_ message: String, operation: () throws -> Void) -> Bool {
        busyMessage = message
        isLoading = true
        defer {
            busyMessage = nil
            isLoading = false
        }

        do {
            try operation()
            return true
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            return false
        }
    }

    private func reloadDataKeepingSelection() throws {
        let snapshot = try repository.loadSnapshot()
        profiles = snapshot.profiles
        allTags = snapshot.tags
        settings = snapshot.settings
        reminderTimeDraft = snapshot.settings.defaultReminderTime
        dashboard = snapshot.dashboard
        dueReminders = try repository.listDueReminders()

        if selectedProfileID == nil || !profiles.contains(where: { $0.id == selectedProfileID }) {
            selectedProfileID = filteredProfiles.first?.id
        }

        try refreshSelectedDetail()
        syncNotifications()
        updateNotificationPermission()
    }

    private func refreshSelectedDetail() throws {
        guard let selectedProfileID else {
            selectedDetail = nil
            return
        }
        selectedDetail = try repository.loadProfileDetail(profileID: selectedProfileID)
    }

    private func matchesSearch(profile: StudentProfileRecord, keyword: String) -> Bool {
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
        return haystacks.contains { $0.lowercased().contains(keyword) }
    }

    private func updateNotificationPermission() {
        Task {
            let permission = await notificationCoordinator.currentPermission()
            await MainActor.run {
                notificationPermission = permission
            }
        }
    }

    private func syncNotifications() {
        Task {
            await notificationCoordinator.sync(reminders: dueReminders)
        }
    }

    private func timestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd-HHmm"
        return formatter.string(from: Date())
    }

    #if canImport(AppKit)
    private func saveURL(title: String, defaultName: String, fileExtension: String) -> URL? {
        let panel = NSSavePanel()
        panel.title = title
        panel.canCreateDirectories = true
        panel.nameFieldStringValue = "\(defaultName).\(fileExtension)"
        panel.allowedContentTypes = [.init(filenameExtension: fileExtension) ?? .data]
        return panel.runModal() == .OK ? panel.url : nil
    }
    #else
    private func saveURL(title: String, defaultName: String, fileExtension: String) -> URL? {
        nil
    }
    #endif
}
