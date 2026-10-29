import Foundation
import SQLite3

package enum ArchiveSmokeChecks {
    package static func run() throws -> [String] {
        let bootstrapURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("fresh-install.sqlite")
        let bootstrapRepository = ArchiveRepository(databasePath: bootstrapURL.path)
        let bootstrapSnapshot = try bootstrapRepository.loadSnapshot()
        try expect(FileManager.default.fileExists(atPath: bootstrapURL.path), "首次启动未自动创建数据库文件")
        try expect(bootstrapSnapshot.tags.count >= 10, "首次启动未自动注入默认标签")
        try expect(bootstrapSnapshot.settings.defaultReminderTime == "09:00", "首次启动未自动注入默认设置")

        let databaseURL = try makeDatabase()
        let repository = ArchiveRepository(databasePath: databaseURL.path)

        let created = try repository.createProfile(
            input: ProfileInput(
                fullName: "张同学",
                nickname: "小张",
                phone: "13800000000",
                wechatId: "zhang-study",
                qqId: "12345678",
                school: "院校甲",
                major: "计算机科学",
                grade: "2026",
                city: "镇江",
                identityType: "在校生",
                focusDirections: ["就业", "考研"],
                demandSummary: "想了解就业路线",
                intentionLevel: "中",
                urgencyLevel: "中",
                verificationStatus: "未核实",
                sourceChannel: "微信",
                currentStatus: "初次接触",
                ownerNotes: "测试数据",
                nextFollowUpAt: ArchiveDate.nowISO(),
                tagIds: [1]
            )
        )
        try expect(created.fullName == "张同学", "createProfile 未正确写入 fullName")
        try expect(created.tagIds == [1], "createProfile 未正确写入标签关联")
        try expect(created.qqId == "12345678", "createProfile 未正确写入 QQ 号")

        let snapshotAfterCreate = try repository.loadSnapshot()
        try expect(snapshotAfterCreate.profiles.count == 1, "loadSnapshot 未返回新建档案")

        _ = try repository.updateProfile(
            profileID: created.id,
            input: ProfileInput(
                fullName: "张同学",
                nickname: "阿张",
                phone: "13800000000",
                wechatId: "zhang-study",
                qqId: "87654321",
                school: "院校甲",
                major: "软件工程",
                grade: "2026",
                city: "镇江",
                identityType: "在校生",
                focusDirections: ["就业"],
                demandSummary: "需要更具体的方案",
                intentionLevel: "高",
                urgencyLevel: "高",
                verificationStatus: "已核实",
                sourceChannel: "活动",
                currentStatus: "跟进中",
                ownerNotes: "更新后备注",
                nextFollowUpAt: ArchiveDate.nowISO(),
                tagIds: [1, 2]
            )
        )

        guard let detailAfterUpdate = try repository.loadProfileDetail(profileID: created.id) else {
            throw ArchiveError.validation("updateProfile 后无法读取档案详情")
        }
        try expect(detailAfterUpdate.profile.major == "软件工程", "updateProfile 未更新 major")
        try expect(Set(detailAfterUpdate.profile.tagIds) == Set([1, 2]), "updateProfile 未更新标签")
        try expect(detailAfterUpdate.profile.qqId == "87654321", "updateProfile 未更新 QQ 号")

        let detailAfterInteraction = try repository.addInteraction(
            input: InteractionInput(
                studentProfileId: created.id,
                contactedAt: ArchiveDate.nowISO(),
                channel: "微信",
                summary: "沟通了课程安排",
                details: "对就业方向更感兴趣",
                outcome: "继续跟进",
                verificationTodo: "确认家长意见",
                nextFollowUpAt: ArchiveDate.nowISO(),
                currentStatus: "高意向",
                intentionLevel: "高",
                verificationStatus: "已核实",
                tagIds: [2]
            )
        )
        try expect(detailAfterInteraction.interactions.count == 1, "addInteraction 未写入时间线")
        try expect(detailAfterInteraction.profile.currentStatus == "高意向", "addInteraction 未同步状态")
        try expect(Set(detailAfterInteraction.profile.tagIds) == Set([2]), "addInteraction 未同步标签")
        try expect(detailAfterInteraction.profile.lastContactAt != nil, "addInteraction 未同步 last_contact_at")

        let dueReminders = try repository.listDueReminders()
        try expect(!dueReminders.isEmpty, "提醒事件未同步写入 reminder_events")

        let exportDirectory = databaseURL.deletingLastPathComponent()
        let csvURL = exportDirectory.appendingPathComponent("profiles.csv")
        let xlsxURL = exportDirectory.appendingPathComponent("profiles.xlsx")
        let timelineURL = exportDirectory.appendingPathComponent("timeline.xlsx")
        let backupURL = exportDirectory.appendingPathComponent("backup.sqlite")

        _ = try repository.exportProfiles(format: .csv, filters: .default, destination: csvURL)
        _ = try repository.exportProfiles(format: .xlsx, filters: .default, destination: xlsxURL)
        _ = try repository.exportSingleProfileTimeline(profileID: created.id, destination: timelineURL)
        _ = try repository.backupDatabase(destination: backupURL)

        try expect(FileManager.default.fileExists(atPath: csvURL.path), "CSV 导出失败")
        try expect(FileManager.default.fileExists(atPath: xlsxURL.path), "XLSX 导出失败")
        try expect(FileManager.default.fileExists(atPath: timelineURL.path), "单档案时间线导出失败")
        try expect(FileManager.default.fileExists(atPath: backupURL.path), "数据库备份失败")

        let importDatabaseURL = try makeDatabase()
        let importRepository = ArchiveRepository(databasePath: importDatabaseURL.path)
        let importResult = try importRepository.importProfiles(from: csvURL)
        try expect(importResult.createdCount == 1, "CSV 批量导入未创建档案")
        let importedProfiles = try importRepository.searchProfiles(filters: .default)
        try expect(importedProfiles.count == 1, "CSV 批量导入后档案数量异常")
        try expect(importedProfiles[0].qqId == "87654321", "CSV 批量导入未带入 QQ 号")

        try repository.archiveProfile(profileID: created.id)

        let activeProfiles = try repository.searchProfiles(filters: .default)
        try expect(activeProfiles.isEmpty, "归档后默认列表仍返回档案")

        let archivedProfiles = try repository.searchProfiles(
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
        try expect(archivedProfiles.count == 1 && archivedProfiles[0].isArchived, "包含归档查询未返回归档档案")

        return [
            "首次启动自动建库 / 默认种子通过",
            "createProfile / loadSnapshot 通过",
            "updateProfile / loadProfileDetail 通过",
            "addInteraction / reminder_events 同步通过",
            "CSV / XLSX / timeline / backup / import 输出通过",
            "archiveProfile / includeArchived 查询通过",
        ]
    }

    private static func expect(_ condition: @autoclosure () -> Bool, _ message: String) throws {
        if !condition() {
            throw ArchiveError.validation(message)
        }
    }

    private static func makeDatabase() throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let databaseURL = directory.appendingPathComponent("archive.sqlite")

        var handle: OpaquePointer?
        if sqlite3_open_v2(databaseURL.path, &handle, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE, nil) != SQLITE_OK {
            throw ArchiveError.sqlite("无法创建测试数据库")
        }
        defer { sqlite3_close(handle) }

        let schema = """
        PRAGMA foreign_keys = ON;
        CREATE TABLE student_profiles (
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
        CREATE TABLE interaction_logs (
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
        CREATE TABLE tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            category TEXT NOT NULL,
            color TEXT NOT NULL,
            is_system INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE profile_tags (
            student_profile_id INTEGER NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (student_profile_id, tag_id),
            FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
            FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );
        CREATE TABLE reminder_events (
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
        CREATE TABLE app_settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        INSERT INTO app_settings (key, value) VALUES ('defaultReminderTime', '09:00');
        INSERT INTO app_settings (key, value) VALUES ('lastBackupAt', '');
        INSERT INTO tags (id, name, category, color, is_system) VALUES
          (1, '高意向', '状态补充', '#16a34a', 1),
          (2, '待回访', '状态补充', '#ea580c', 1);
        """

        if sqlite3_exec(handle, schema, nil, nil, nil) != SQLITE_OK {
            throw ArchiveError.sqlite("初始化测试数据库失败")
        }

        return databaseURL
    }
}
