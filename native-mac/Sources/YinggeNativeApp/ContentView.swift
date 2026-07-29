import SwiftUI

package struct ContentView: View {
    @EnvironmentObject private var store: ArchiveStore

    package init() {}

    package var body: some View {
        NavigationSplitView {
            List(AppSection.allCases, selection: $store.selectedSection) { section in
                Label(section.title, systemImage: section.systemImage)
                    .tag(section)
            }
            .navigationTitle("英格学生档案")
            .listStyle(.sidebar)
        } detail: {
            ZStack(alignment: .bottomTrailing) {
                Group {
                    switch store.selectedSection {
                    case .dashboard:
                        DashboardWorkspaceView()
                    case .profiles:
                        ProfilesWorkspaceView()
                    case .settings:
                        SettingsWorkspaceView()
                    }
                }

                if let busyMessage = store.busyMessage {
                    ProgressOverlay(label: busyMessage)
                        .padding(24)
                }
            }
            .toolbar {
                ToolbarItemGroup {
                    Button("刷新") {
                        store.reload()
                    }
                    .keyboardShortcut("r", modifiers: [.command])

                    if store.selectedSection == .profiles {
                        Button("新建档案") {
                            store.presentCreateProfile()
                        }
                    }
                }
            }
        }
        .task {
            store.load()
        }
        .sheet(isPresented: $store.showingProfileSheet) {
            ProfileEditorSheet(
                form: $store.profileForm,
                tags: store.allTags,
                isEditing: store.isEditingProfileSheet,
                onSave: { store.saveProfileForm() }
            )
        }
        .sheet(isPresented: $store.showingInteractionSheet) {
            InteractionEditorSheet(
                form: $store.interactionForm,
                tags: store.allTags,
                onSave: { store.saveInteractionForm() }
            )
        }
        .alert("操作失败", isPresented: Binding(
            get: { store.errorMessage != nil },
            set: { newValue in
                if !newValue { store.clearError() }
            }
        )) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text(store.errorMessage ?? "")
        }
        .alert("操作完成", isPresented: Binding(
            get: { store.successMessage != nil },
            set: { newValue in
                if !newValue { store.clearSuccess() }
            }
        )) {
            Button("好的", role: .cancel) {}
        } message: {
            Text(store.successMessage ?? "")
        }
    }
}

struct DashboardWorkspaceView: View {
    @EnvironmentObject private var store: ArchiveStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("今日工作台")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                    Text("原生 SwiftUI 工作台，直接读取并维护现有 SQLite 数据。")
                        .foregroundStyle(.secondary)
                }

                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ], spacing: 14) {
                    StatCard(title: "活跃档案", value: "\(store.dashboard.stats.totalActive)")
                    StatCard(title: "今日待跟进", value: "\(store.dashboard.stats.todayDueCount)")
                    StatCard(title: "逾期未处理", value: "\(store.dashboard.stats.overdueCount)")
                    StatCard(title: "高意向", value: "\(store.dashboard.stats.highIntentionCount)")
                }

                DashboardProfileSection(
                    title: "今日待跟进",
                    subtitle: "优先处理今天需要联系的档案",
                    profiles: store.dashboard.dueToday
                )

                DashboardProfileSection(
                    title: "逾期未处理",
                    subtitle: "这些档案已经超过预定跟进时间",
                    profiles: store.dashboard.overdue
                )

                HStack(alignment: .top, spacing: 16) {
                    DashboardProfileSection(
                        title: "最近新增",
                        subtitle: "最近 7 天创建",
                        profiles: store.dashboard.recentCreated
                    )
                    DashboardProfileSection(
                        title: "高意向但 3 天未联系",
                        subtitle: "适合优先安排回访",
                        profiles: store.dashboard.staleHighIntention
                    )
                }

                GroupBox("提醒概览") {
                    VStack(alignment: .leading, spacing: 10) {
                        if store.dueReminders.isEmpty {
                            Text("当前没有待处理提醒。")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(store.dueReminders.prefix(6)) { reminder in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(reminder.title)
                                            .font(.headline)
                                        Text(ArchiveDate.display(reminder.remindAt))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(reminder.status)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                if reminder.id != store.dueReminders.prefix(6).last?.id {
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
            .padding(24)
        }
    }
}

struct DashboardProfileSection: View {
    @EnvironmentObject private var store: ArchiveStore

    let title: String
    let subtitle: String
    let profiles: [StudentProfileRecord]

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                if profiles.isEmpty {
                    ContentUnavailableView("暂无记录", systemImage: "tray")
                        .frame(maxWidth: .infinity)
                } else {
                    ForEach(profiles) { profile in
                        Button {
                            store.openProfile(profile.id)
                        } label: {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(profile.fullName)
                                        .font(.headline)
                                    Text("\(profile.school ?? "学校未填") · \(profile.focusDirections.joined(separator: " / "))")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 4) {
                                    Text(profile.currentStatus)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(ArchiveDate.display(profile.nextFollowUpAt ?? profile.lastContactAt))
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)

                        if profile.id != profiles.last?.id {
                            Divider()
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3)
                    .fontWeight(.semibold)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct ProfilesWorkspaceView: View {
    @EnvironmentObject private var store: ArchiveStore
    @State private var showArchiveConfirmation = false

    var body: some View {
        HSplitView {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text("档案库")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        Spacer()
                        Text("\(store.filteredProfiles.count) 条")
                            .foregroundStyle(.secondary)
                    }

                    HStack {
                        TextField("搜索姓名、学校、微信、QQ、标签", text: $store.searchText)
                            .textFieldStyle(.roundedBorder)
                        Picker("状态", selection: $store.statusFilter) {
                            ForEach(store.availableStatuses, id: \.self) { value in
                                Text(value).tag(value)
                            }
                        }
                        .pickerStyle(.menu)

                        Toggle("包含归档", isOn: $store.includeArchived)
                            .toggleStyle(.switch)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("标签筛选")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        SelectableTagFlowView(
                            tags: store.allTags,
                            selectedTagIDs: $store.selectedTagIDs
                        )
                    }

                    HStack {
                        Button("新建档案") {
                            store.presentCreateProfile()
                        }
                        Button("批量导入 CSV") {
                            store.importProfilesFromCSV()
                        }
                        Button("批量导出 CSV") {
                            store.exportProfiles(format: .csv)
                        }
                        Button("批量导出 Excel") {
                            store.exportProfiles(format: .xlsx)
                        }
                    }
                }
                .padding(20)

                Divider()

                List(selection: Binding(
                    get: { store.selectedProfileID },
                    set: { store.selectProfile($0) }
                )) {
                    ForEach(store.filteredProfiles) { profile in
                        ProfileRow(profile: profile)
                            .tag(profile.id)
                    }
                }
                .listStyle(.inset)
            }
            .frame(minWidth: 400, idealWidth: 460, maxWidth: 520)

            VStack(spacing: 0) {
                if let detail = store.selectedDetail {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(detail.profile.fullName)
                                        .font(.largeTitle)
                                        .fontWeight(.bold)
                                    Text("联系方式、提醒、标签和沟通记录都集中在这里。")
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 8) {
                                    HStack {
                                        Button("新增沟通") {
                                            store.presentInteractionSheet()
                                        }
                                        Button("编辑档案") {
                                            store.presentEditProfile()
                                        }
                                    }
                                    HStack {
                                        Button("导出时间线 CSV") {
                                            store.exportSelectedTimeline(format: .csv)
                                        }
                                        Button("导出时间线 Excel") {
                                            store.exportSelectedTimeline(format: .xlsx)
                                        }
                                    }
                                    Button("归档") {
                                        showArchiveConfirmation = true
                                    }
                                    .foregroundStyle(.red)
                                }
                            }

                            ProfileSummaryStrip(profile: detail.profile, reminders: detail.reminders, interactions: detail.interactions)

                            GroupBox("档案信息") {
                                DetailGridView(items: [
                                    ("学校", detail.profile.school ?? "未填写"),
                                    ("专业", detail.profile.major ?? "未填写"),
                                    ("年级", detail.profile.grade ?? "未填写"),
                                    ("城市", detail.profile.city ?? "未填写"),
                                    ("身份类型", detail.profile.identityType),
                                    ("关注方向", detail.profile.focusDirections.joined(separator: " / ")),
                                    ("报名意向", detail.profile.intentionLevel),
                                    ("核实状态", detail.profile.verificationStatus),
                                    ("最近联系", ArchiveDate.display(detail.profile.lastContactAt)),
                                    ("下次跟进", ArchiveDate.display(detail.profile.nextFollowUpAt)),
                                ])
                            }

                            GroupBox("联系方式与备注") {
                                VStack(alignment: .leading, spacing: 10) {
                                    DetailItem(label: "手机号", value: detail.profile.phone ?? "未填写")
                                    DetailItem(label: "微信号", value: detail.profile.wechatId ?? "未填写")
                                    DetailItem(label: "QQ号", value: detail.profile.qqId ?? "未填写")
                                    DetailItem(label: "来源渠道", value: detail.profile.sourceChannel ?? "未填写")
                                    DetailItem(label: "需求摘要", value: detail.profile.demandSummary ?? "暂无")
                                    DetailItem(label: "内部备注", value: detail.profile.ownerNotes ?? "暂无")
                                }
                            }

                            GroupBox("标签") {
                                if detail.profile.tags.isEmpty {
                                    Text("暂无标签")
                                        .foregroundStyle(.secondary)
                                } else {
                                    FlowTagView(tags: detail.profile.tags)
                                }
                            }

                            GroupBox("待处理提醒") {
                                if detail.reminders.isEmpty {
                                    Text("暂无提醒")
                                        .foregroundStyle(.secondary)
                                } else {
                                    VStack(alignment: .leading, spacing: 12) {
                                        ForEach(detail.reminders) { reminder in
                                            ReminderCard(reminder: reminder)
                                            if reminder.id != detail.reminders.last?.id {
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }

                            GroupBox("沟通时间线") {
                                if detail.interactions.isEmpty {
                                    Text("还没有沟通记录")
                                        .foregroundStyle(.secondary)
                                } else {
                                    VStack(alignment: .leading, spacing: 14) {
                                        ForEach(detail.interactions) { interaction in
                                            InteractionTimelineRow(interaction: interaction)
                                            if interaction.id != detail.interactions.last?.id {
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(24)
                    }
                } else {
                    ContentUnavailableView("选择一条档案", systemImage: "person.text.rectangle")
                }
            }
            .confirmationDialog("归档后该档案默认不出现在普通列表中。", isPresented: $showArchiveConfirmation) {
                Button("确认归档", role: .destructive) {
                    store.archiveSelectedProfile()
                }
                Button("取消", role: .cancel) {}
            }
        }
    }
}

package struct SettingsWorkspaceView: View {
    @EnvironmentObject private var store: ArchiveStore

    package init() {}

    package var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("设置")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        Text("数据库路径、提醒和标签都在这里维护。")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("备份数据库") {
                        store.backupDatabase()
                    }
                }

                GroupBox("提醒与通知") {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("默认提醒时间")
                            TextField("09:00", text: $store.reminderTimeDraft)
                                .frame(width: 100)
                            Button("保存提醒设置") {
                                store.saveReminderSettings()
                            }
                        }

                        HStack {
                            Text("通知权限")
                            Text(notificationLabel(store.notificationPermission))
                                .foregroundStyle(.secondary)
                            Button("请求通知权限") {
                                store.requestNotificationPermission()
                            }
                        }
                    }
                }

                GroupBox("数据库") {
                    VStack(alignment: .leading, spacing: 12) {
                        DetailItem(label: "数据库路径", value: store.settings.databasePath)
                        DetailItem(label: "最近备份", value: ArchiveDate.display(store.settings.lastBackupAt, fallback: "还没有备份记录"))

                        HStack {
                            Button("重新读取") {
                                store.reload()
                            }
                            #if canImport(AppKit)
                            Button("选择数据库") {
                                store.chooseDatabase()
                            }
                            #endif
                            Button("恢复默认路径") {
                                store.resetToDefaultDatabase()
                            }
                        }
                    }
                }

                GroupBox("标签管理") {
                    VStack(alignment: .leading, spacing: 16) {
                        if store.allTags.isEmpty {
                            Text("暂无标签")
                                .foregroundStyle(.secondary)
                        } else {
                            FlowTagView(tags: store.allTags)
                        }

                        Divider()

                        VStack(alignment: .leading, spacing: 12) {
                            Text("新建标签")
                                .font(.headline)
                            HStack {
                                TextField("标签名称", text: $store.tagForm.name)
                                Picker("类别", selection: $store.tagForm.category) {
                                    ForEach(AppOptions.tagCategories, id: \.self) { category in
                                        Text(category).tag(category)
                                    }
                                }
                                .pickerStyle(.menu)
                            }

                            ColorSelectionView(colors: AppOptions.tagColorOptions, selectedColor: $store.tagForm.color)

                            Button("创建标签") {
                                store.createTagFromSettings()
                            }
                            .disabled(store.tagForm.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    }
                }
            }
            .padding(24)
        }
    }

    private func notificationLabel(_ permission: ArchiveNotificationPermission) -> String {
        switch permission {
        case .notDetermined:
            "未设置"
        case .denied:
            "已拒绝"
        case .authorized:
            "已允许"
        }
    }
}

struct ProfileEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var form: ProfileFormModel

    let tags: [TagRecord]
    let isEditing: Bool
    let onSave: () -> Bool

    var body: some View {
        NavigationStack {
            Form {
                Section("基础信息") {
                    TextField("姓名 *", text: $form.fullName)
                    TextField("昵称", text: $form.nickname)
                    TextField("手机号", text: $form.phone)
                    TextField("微信号（选填）", text: $form.wechatId)
                    TextField("QQ号（选填）", text: $form.qqId)
                    TextField("学校", text: $form.school)
                    TextField("专业", text: $form.major)
                    TextField("年级 / 届别", text: $form.grade)
                    TextField("城市", text: $form.city)
                    TextField("来源渠道", text: $form.sourceChannel)
                }

                Section("状态信息") {
                    Picker("身份类型", selection: $form.identityType) {
                        ForEach(AppOptions.identityTypes, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("报名意向", selection: $form.intentionLevel) {
                        ForEach(AppOptions.intentionLevels, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("紧急程度", selection: $form.urgencyLevel) {
                        ForEach(AppOptions.urgencyLevels, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("核实状态", selection: $form.verificationStatus) {
                        ForEach(AppOptions.verificationStatuses, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("当前状态", selection: $form.currentStatus) {
                        ForEach(AppOptions.profileStatuses, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                }

                Section("关注方向") {
                    MultiSelectOptionView(options: AppOptions.focusDirectionOptions, selectedOptions: $form.focusDirections)
                }

                Section("标签") {
                    SelectableTagFlowView(tags: tags, selectedTagIDs: $form.tagIds)
                }

                Section("提醒与备注") {
                    Toggle("设置下次跟进时间", isOn: $form.hasNextFollowUpAt)
                    if form.hasNextFollowUpAt {
                        DatePicker("下次跟进", selection: $form.nextFollowUpAt)
                    }
                    TextField("需求摘要", text: $form.demandSummary, axis: .vertical)
                        .lineLimit(2...4)
                    TextField("内部备注", text: $form.ownerNotes, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .formStyle(.grouped)
            .navigationTitle(isEditing ? "编辑档案" : "新建档案")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isEditing ? "保存" : "创建") {
                        if onSave() {
                            dismiss()
                        }
                    }
                }
            }
        }
        .frame(minWidth: 760, minHeight: 760)
    }
}

struct InteractionEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var form: InteractionFormModel

    let tags: [TagRecord]
    let onSave: () -> Bool

    var body: some View {
        NavigationStack {
            Form {
                Section("沟通信息") {
                    DatePicker("沟通时间", selection: $form.contactedAt)
                    Picker("沟通方式", selection: $form.channel) {
                        ForEach(AppOptions.interactionChannels, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    TextField("本次摘要 *", text: $form.summary)
                    TextField("详细备注", text: $form.details, axis: .vertical)
                        .lineLimit(3...6)
                }

                Section("结果与提醒") {
                    TextField("结果判断", text: $form.outcome)
                    TextField("待核实信息", text: $form.verificationTodo)
                    Toggle("设置下次跟进", isOn: $form.hasNextFollowUpAt)
                    if form.hasNextFollowUpAt {
                        DatePicker("下次跟进", selection: $form.nextFollowUpAt)
                    }
                }

                Section("同步档案状态") {
                    Picker("当前状态", selection: $form.currentStatus) {
                        Text("不变").tag("")
                        ForEach(AppOptions.profileStatuses, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("报名意向", selection: $form.intentionLevel) {
                        Text("不变").tag("")
                        ForEach(AppOptions.intentionLevels, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    Picker("核实状态", selection: $form.verificationStatus) {
                        Text("不变").tag("")
                        ForEach(AppOptions.verificationStatuses, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                }

                Section("同步标签") {
                    SelectableTagFlowView(tags: tags, selectedTagIDs: $form.tagIds)
                }
            }
            .navigationTitle("记录沟通")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        if onSave() {
                            dismiss()
                        }
                    }
                }
            }
        }
        .frame(minWidth: 760, minHeight: 700)
    }
}

struct ProfileRow: View {
    let profile: StudentProfileRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(profile.fullName)
                    .font(.headline)
                Spacer()
                Text(profile.currentStatus)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Text("\(profile.school ?? "学校未填") · \(profile.major ?? "专业未填")")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text("意向：\(profile.intentionLevel) · 最近联系：\(ArchiveDate.display(profile.lastContactAt))")
                .font(.caption)
                .foregroundStyle(.tertiary)

            if !profile.tags.isEmpty {
                Text(profile.tags.map(\.name).joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 4)
    }
}

struct ProfileSummaryStrip: View {
    let profile: StudentProfileRecord
    let reminders: [ReminderEventRecord]
    let interactions: [InteractionLogRecord]

    var body: some View {
        HStack(spacing: 16) {
            SummaryTile(label: "当前状态", value: profile.currentStatus)
            SummaryTile(label: "最近联系", value: ArchiveDate.display(profile.lastContactAt))
            SummaryTile(label: "提醒数量", value: "\(reminders.count)")
            SummaryTile(label: "沟通记录", value: "\(interactions.count)")
        }
    }
}

struct SummaryTile: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3)
                .fontWeight(.semibold)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor))
        )
    }
}

struct DetailGridView: View {
    let items: [(String, String)]

    private let columns = [
        GridItem(.flexible()),
        GridItem(.flexible()),
    ]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                DetailItem(label: item.0, value: item.1)
            }
        }
    }
}

struct DetailItem: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ReminderCard: View {
    let reminder: ReminderEventRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(reminder.title)
                .font(.headline)
            Text("状态：\(reminder.status)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(ArchiveDate.display(reminder.remindAt))
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }
}

struct InteractionTimelineRow: View {
    let interaction: InteractionLogRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(interaction.summary)
                    .font(.headline)
                Spacer()
                Text(interaction.channel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(ArchiveDate.display(interaction.contactedAt))
                .font(.caption)
                .foregroundStyle(.tertiary)
            Text(interaction.details ?? "无详细备注")
                .foregroundStyle(.secondary)
            HStack {
                Text("结果：\(interaction.outcome ?? "未填写")")
                Text("待核实：\(interaction.verificationTodo ?? "无")")
                Text("下次跟进：\(ArchiveDate.display(interaction.nextFollowUpAt))")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }
}

struct FlowTagView: View {
    let tags: [TagRecord]

    var body: some View {
        LazyVGrid(columns: [
            GridItem(.adaptive(minimum: 120), alignment: .leading),
        ], alignment: .leading, spacing: 10) {
            ForEach(tags) { tag in
                VStack(alignment: .leading, spacing: 4) {
                    Text(tag.name)
                        .font(.headline)
                    Text(tag.category)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(nsColor: .controlBackgroundColor))
                )
            }
        }
    }
}

struct SelectableTagFlowView<Selection: SetAlgebra>: View where Selection.Element == Int64 {
    let tags: [TagRecord]
    @Binding var selectedTagIDs: Selection

    var body: some View {
        LazyVGrid(columns: [
            GridItem(.adaptive(minimum: 110), alignment: .leading),
        ], alignment: .leading, spacing: 8) {
            ForEach(tags) { tag in
                Button {
                    toggle(tag.id)
                } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(tag.name)
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Text(tag.category)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(selectedTagIDs.contains(tag.id) ? Color.accentColor.opacity(0.16) : Color(nsColor: .controlBackgroundColor))
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func toggle(_ tagID: Int64) {
        if selectedTagIDs.contains(tagID) {
            selectedTagIDs.remove(tagID)
        } else {
            selectedTagIDs.insert(tagID)
        }
    }
}

struct MultiSelectOptionView: View {
    let options: [String]
    @Binding var selectedOptions: [String]

    var body: some View {
        LazyVGrid(columns: [
            GridItem(.adaptive(minimum: 110), alignment: .leading),
        ], alignment: .leading, spacing: 8) {
            ForEach(options, id: \.self) { option in
                Button {
                    toggle(option)
                } label: {
                    Text(option)
                        .font(.subheadline)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(selectedOptions.contains(option) ? Color.accentColor.opacity(0.16) : Color(nsColor: .controlBackgroundColor))
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func toggle(_ option: String) {
        if let index = selectedOptions.firstIndex(of: option) {
            selectedOptions.remove(at: index)
        } else {
            selectedOptions.append(option)
        }

        if selectedOptions.isEmpty {
            selectedOptions = ["暂不明确"]
        } else if selectedOptions.count > 1, selectedOptions.contains("暂不明确"), option != "暂不明确" {
            selectedOptions.removeAll { $0 == "暂不明确" }
        }
    }
}

struct ColorSelectionView: View {
    let colors: [String]
    @Binding var selectedColor: String

    var body: some View {
        HStack {
            ForEach(colors, id: \.self) { colorHex in
                Button {
                    selectedColor = colorHex
                } label: {
                    Circle()
                        .fill(Color(hex: colorHex))
                        .frame(width: 24, height: 24)
                        .overlay(
                            Circle()
                                .stroke(selectedColor == colorHex ? Color.primary : Color.clear, lineWidth: 2)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct ProgressOverlay: View {
    let label: String

    var body: some View {
        HStack(spacing: 10) {
            ProgressView()
            Text(label)
                .font(.subheadline)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.thinMaterial, in: Capsule())
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.replacingOccurrences(of: "#", with: "")
        let scanner = Scanner(string: hex)
        var value: UInt64 = 0
        scanner.scanHexInt64(&value)

        let red = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue = Double(value & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}

struct StatCard: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(size: 28, weight: .bold))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor))
        )
    }
}
