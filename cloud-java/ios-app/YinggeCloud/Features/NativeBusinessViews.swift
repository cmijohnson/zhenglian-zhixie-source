import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct NativeBusinessDestination: View {
    let feature: NativeFeatureSpec
    @ViewBuilder var body: some View {
        switch feature.title {
        case "公共池与二线协作": OwnershipCenterView()
        case "任务中心": TaskCenterView()
        case "绩效中心": PerformanceCenterView()
        case "咨询交接": ConsultationCenterView()
        case "内容任务": ContentTaskCenterView()
        case "内容圈子": ContentCircleView()
        case "公告中心": AnnouncementCenterView()
        case "AI 助手": AssistantCenterView()
        case "更新日志": ReleaseHistoryView()
        case "我的 Agent": AgentAuthorizationView()
        case "设备管理": DeviceManagementView()
        default: VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle").font(.largeTitle)
            Text("功能不可用").font(.headline)
            Text("当前版本没有登记该原生页面。").foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

private struct OwnershipCenterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var pools: [CustomerPoolEntry] = []
    @State private var recruitments: [SecondLineRecruitment] = []
    var body: some View {
        List {
            Section("公共池") {
                if pools.isEmpty { Text("暂无可领取客户").foregroundStyle(.secondary) }
                ForEach(pools) { item in
                    VStack(alignment: .leading, spacing: 7) {
                        Text(item.customerName ?? "脱敏客户").font(.headline).privacySensitive()
                        Text([item.school, item.grade, item.source].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                        Text(item.demandSummary ?? "暂无需求摘要").lineLimit(3)
                        if item.canClaim { Button("领取客户") { Task { if await model.claimPool(item.id) { await load() } } }.buttonStyle(.borderedProminent) }
                    }.padding(.vertical, 5)
                }
            }
            Section("A 类二线招募") {
                if recruitments.isEmpty { Text("暂无二线招募").foregroundStyle(.secondary) }
                ForEach(recruitments) { item in
                    VStack(alignment: .leading, spacing: 7) {
                        Text(item.customerName ?? "脱敏客户").font(.headline).privacySensitive()
                        Text(item.demandSummary ?? "暂无摘要").lineLimit(3)
                        Text("已领取 \(item.claimedCount)/\(item.requiredCount)").font(.caption).foregroundStyle(.secondary)
                        Button("领取二线") { Task { if await model.claimSecondLine(item.id) { await load() } } }.buttonStyle(.borderedProminent)
                    }.padding(.vertical, 5)
                }
            }
        }.navigationTitle("公共池与二线").task { await load() }.refreshable { await load() }
    }
    private func load() async {
        async let nextPools = model.customerPool(); async let nextRecruitments = model.secondLineRecruitments()
        (pools, recruitments) = await (nextPools, nextRecruitments)
    }
}

private struct TaskCenterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var tasks: [WorkTask] = []
    @State private var submitting: WorkTask?
    var body: some View {
        List(tasks) { task in
            VStack(alignment: .leading, spacing: 8) {
                HStack { Text(task.title).font(.headline); Spacer(); Pill(text: task.status, color: task.overdue ? .red : YinggeTheme.blue) }
                Text(task.description).lineLimit(4)
                Text("完成 \(task.completedCount)/\(task.assignmentCount) · 截止 \(task.dueAt.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                if task.claimable { Button("领取任务") { Task { if await model.claimTask(task.id) { await load() } } }.buttonStyle(.borderedProminent) }
                if task.myAssignment != nil { Button("提交成果") { submitting = task }.buttonStyle(.bordered) }
            }.padding(.vertical, 6)
        }.overlay { if tasks.isEmpty { Text("暂无任务").foregroundStyle(.secondary) } }
            .navigationTitle("任务中心").task { await load() }.refreshable { await load() }
            .sheet(item: $submitting) { task in TaskSubmissionSheet(task: task) { await load() } }
    }
    private func load() async { tasks = await model.workTasks() }
}

private struct TaskSubmissionSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let task: WorkTask; let completed: () async -> Void
    @State private var summary = ""; @State private var evidence = ""
    var body: some View {
        NavigationStack {
            Form { TextEditor(text: $summary).frame(minHeight: 150); TextField("证据链接（可选）", text: $evidence).textInputAutocapitalization(.never) }
                .navigationTitle("提交任务")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("提交") { Task { if await model.submitTask(task.id, summary: summary, evidenceURL: evidence.isEmpty ? nil : evidence) { await completed(); dismiss() } } }.disabled(summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
                }
        }
    }
}

private struct PerformanceCenterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var today: PerformanceToday?
    @State private var selected = ""; @State private var type = "ORDINARY"
    @State private var evidenceClaim: PerformanceClaim?
    var body: some View {
        Form {
            if let today {
                Section("今日 \(today.date)") {
                    LabeledContent("得分", value: today.score.finalPoints.formatted())
                    LabeledContent("Baseline", value: today.score.baseline.formatted())
                    LabeledContent("差额", value: today.score.shortfall.formatted())
                    LabeledContent("审核中", value: today.pendingClaims.formatted())
                }
                Section("申报沟通") {
                    Picker("沟通记录", selection: $selected) { Text("请选择").tag(""); ForEach(today.availableInteractions) { Text("\($0.customerName) · \($0.summary)").tag($0.id) } }
                    Picker("类型", selection: $type) { Text("普通交流").tag("ORDINARY"); Text("深度交流").tag("DEEP") }.pickerStyle(.segmented)
                    Button("创建申报草稿") { Task { if let claim = await model.createPerformanceClaim(interactionID: selected, type: type) { self.today = await model.performanceToday(); evidenceClaim = claim } } }.disabled(selected.isEmpty || !today.submissionOpen)
                    Label("草稿创建后需上传 1–3 张 JPEG、PNG 或 WebP 证据并提交审核。", systemImage: "photo.on.rectangle.angled").font(.footnote).foregroundStyle(.secondary)
                }
                Section("今日明细") { ForEach(today.claims) { claim in
                    VStack(alignment: .leading, spacing: 7) {
                        LabeledContent("\(claim.customerName) · \(claim.requestedType)", value: claim.status)
                        if claim.status == "DRAFT" { Button("添加证据并提交") { evidenceClaim = claim }.buttonStyle(.bordered) }
                        if !claim.evidence.isEmpty { Text("已上传 \(claim.evidence.count) 张证据").font(.caption).foregroundStyle(.secondary) }
                    }
                } }
            } else { ProgressView("正在加载绩效…") }
        }.navigationTitle("绩效中心").task { today = await model.performanceToday(); selected = today?.availableInteractions.first?.id ?? "" }.refreshable { today = await model.performanceToday() }
            .sheet(item: $evidenceClaim) { claim in PerformanceEvidenceSheet(claim: claim) { today = await model.performanceToday() } }
    }
}

private struct PerformanceEvidenceSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let claim: PerformanceClaim; let completed: () async -> Void
    @State private var files: [PerformanceEvidenceFile] = []
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var showImporter = false
    @State private var validationError: String?
    @State private var submitting = false
    private var remaining: Int { max(0, 3 - claim.evidence.count - files.count) }
    var body: some View {
        NavigationStack {
            Form {
                Section("申报") { LabeledContent("客户", value: claim.customerName); LabeledContent("类型", value: claim.requestedType); LabeledContent("已上传", value: "\(claim.evidence.count) 张") }
                Section("新增证据") {
                    ForEach(files) { file in HStack { Image(systemName: "photo"); Text(file.fileName).lineLimit(1); Spacer(); Text(ByteCountFormatter.string(fromByteCount: Int64(file.data.count), countStyle: .file)).font(.caption).foregroundStyle(.secondary) } }
                    PhotosPicker(selection: $photoItems, maxSelectionCount: remaining, matching: .images) { Label("从照片选择", systemImage: "photo.on.rectangle") }.disabled(remaining == 0)
                    Button { showImporter = true } label: { Label("从文件选择", systemImage: "folder") }.disabled(remaining == 0)
                    if let validationError { Text(validationError).font(.footnote).foregroundStyle(.red) }
                    Text("仅 JPEG、PNG、WebP；每张不超过 5MB；每次申报合计最多 3 张。").font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("绩效证据")
            .onChange(of: photoItems) { items in Task { await importPhotos(items) } }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.jpeg, .png, .webP], allowsMultipleSelection: true) { result in importFiles(result) }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("上传并提交") { submit() }.disabled(submitting || (files.isEmpty && claim.evidence.isEmpty)) }
            }
        }
    }
    private func append(_ data: Data, name: String) {
        guard remaining > 0 else { validationError = PerformanceEvidenceValidationError.tooMany.localizedDescription; return }
        do { files.append(try PerformanceEvidenceValidator.validate(data: data, suggestedName: name)); validationError = nil }
        catch { validationError = error.localizedDescription }
    }
    private func importPhotos(_ items: [PhotosPickerItem]) async {
        for (index, item) in items.prefix(remaining).enumerated() {
            do { if let data = try await item.loadTransferable(type: Data.self) { append(data, name: "photo-\(index + 1)") } }
            catch { validationError = "无法读取照片：\(error.localizedDescription)" }
        }
        photoItems = []
    }
    private func importFiles(_ result: Result<[URL], Error>) {
        do {
            let urls = try result.get()
            guard urls.count <= remaining else { validationError = PerformanceEvidenceValidationError.tooMany.localizedDescription; return }
            for url in urls {
                let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                append(try Data(contentsOf: url, options: .mappedIfSafe), name: url.lastPathComponent)
            }
        } catch { validationError = "无法读取文件：\(error.localizedDescription)" }
    }
    private func submit() {
        submitting = true
        Task {
            let success = await model.uploadAndSubmitPerformanceClaim(claim, files: files)
            await completed(); submitting = false
            if success || model.errorMessage != nil { dismiss() }
        }
    }
}

private struct ConsultationCenterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var records: [ConsultationRecord] = []
    @State private var showNew = false
    @State private var completing: ConsultationRecord?
    @State private var detail: ConsultationRecord?
    var body: some View {
        List(records) { record in
            VStack(alignment: .leading, spacing: 8) {
                HStack { Text(record.customerName).font(.headline); Spacer(); Pill(text: record.status) }
                Text(record.consultationReason).lineLimit(3)
                Text("顾问 \(record.consultantName) · \(record.scheduledStart.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.secondary)
                HStack {
                    Button("查看详情") { Task { detail = await model.consultation(record.id) } }
                    if ["WAITING_FEEDBACK", "ENDED"].contains(record.status) { Button("回填结果") { completing = record } }
                }
            }.padding(.vertical, 5)
        }.overlay { if records.isEmpty { Text("暂无咨询交接").foregroundStyle(.secondary) } }
            .navigationTitle("咨询交接").toolbar { Button { showNew = true } label: { Image(systemName: "plus") } }
            .task { await load() }.refreshable { await load() }
            .sheet(isPresented: $showNew) { ConsultationCreateSheet { await load() } }
            .sheet(item: $completing) { ConsultationCompleteSheet(record: $0) { await load() } }
            .sheet(item: $detail) { ConsultationDetailSheet(record: $0) }
    }
    private func load() async { records = await model.consultations() }
}

private struct ConsultationDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let record: ConsultationRecord
    var body: some View {
        NavigationStack {
            Form {
                Section("交接对象") {
                    LabeledContent("客户", value: record.customerName)
                    LabeledContent("顾问", value: record.consultantName)
                    LabeledContent("预约", value: record.scheduledStart.formatted(date: .abbreviated, time: .shortened))
                    LabeledContent("状态", value: record.status)
                }
                Section("带画像交接") {
                    DetailText(title: "咨询原因", content: record.consultationReason)
                    DetailText(title: "客户原话", content: record.customerQuotes)
                    DetailText(title: "目标与动机", content: record.goalsMotivation)
                    DetailText(title: "已做行动", content: record.actionsTaken)
                    DetailText(title: "主要顾虑", content: record.mainConcerns)
                    DetailText(title: "产品适配", content: record.productFit)
                    DetailText(title: "建议重点", content: record.suggestedFocus)
                    DetailText(title: "明确禁忌", content: record.explicitTaboos)
                    DetailText(title: "预期结果", content: record.expectedResult)
                }
                if let result = record.resultSummary, !result.isEmpty {
                    Section("咨询结果") {
                        DetailText(title: "结果", content: result)
                        DetailText(title: "下一步", content: record.nextStep ?? "未填写")
                    }
                }
            }
            .navigationTitle("咨询交接详情")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}

private struct DetailText: View {
    let title: String; let content: String
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(content).font(.subheadline).textSelection(.enabled)
        }.padding(.vertical, 3)
    }
}

private struct ConsultationCreateSheet: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    let completed: () async -> Void
    @State private var customerID = ""
    @State private var consultantID = ""
    @State private var interactionID = ""
    @State private var interactions: [Interaction] = []; @State private var candidates: [ConsultationCandidate] = []
    @State private var reason = ""
    @State private var quotes = ""
    @State private var goals = ""
    @State private var actions = ""
    @State private var concerns = ""
    @State private var fit = ""
    @State private var focus = ""
    @State private var taboos = ""
    @State private var expected = ""
    @State private var start = Date().addingTimeInterval(3600); @State private var duration = 60
    var body: some View {
        NavigationStack {
            Form {
                Picker("客户", selection: $customerID) { Text("请选择").tag(""); ForEach(model.customers) { Text($0.fullName).tag($0.id) } }
                    .onChange(of: customerID) { value in Task { interactions = await model.interactions(for: value); interactionID = interactions.first?.id ?? "" } }
                Picker("顾问", selection: $consultantID) { Text("请选择").tag(""); ForEach(candidates) { Text($0.displayName).tag($0.id) } }
                Picker("依据时间线", selection: $interactionID) { Text("请选择").tag(""); ForEach(interactions) { Text($0.summary).tag($0.id) } }
                TextField("咨询原因", text: $reason); TextField("客户原话", text: $quotes, axis: .vertical)
                TextField("目标与动机", text: $goals, axis: .vertical); TextField("已做行动", text: $actions, axis: .vertical)
                TextField("主要顾虑", text: $concerns, axis: .vertical); TextField("产品适配", text: $fit, axis: .vertical)
                TextField("建议重点", text: $focus, axis: .vertical); TextField("明确禁忌", text: $taboos, axis: .vertical)
                TextField("预期结果", text: $expected, axis: .vertical); DatePicker("开始时间", selection: $start)
                Picker("时长", selection: $duration) { ForEach([30,45,60,90], id: \.self) { Text("\($0) 分钟").tag($0) } }
            }.navigationTitle("新建交接卡").task { candidates = await model.consultationCandidates() }
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("提交") { submit() }.disabled(!valid) } }
        }
    }
    private var valid: Bool { ![customerID, consultantID, interactionID, reason, quotes, goals, actions, concerns, fit, focus, taboos, expected].contains { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }
    private func submit() {
        let body = ConsultationCreate(customerId: customerID, consultantMembershipId: consultantID, sourceInteractionIds: [interactionID], consultationReason: reason, customerQuotes: quotes, goalsMotivation: goals, actionsTaken: actions, mainConcerns: concerns, decisionParticipants: nil, decisionParticipantConsent: false, productFit: fit, suggestedFocus: focus, explicitTaboos: taboos, expectedResult: expected, scheduledStart: start, scheduledEnd: start.addingTimeInterval(Double(duration * 60)))
        Task { if await model.createConsultation(body) { await completed(); dismiss() } }
    }
}

private struct ConsultationCompleteSheet: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    let record: ConsultationRecord; let completed: () async -> Void
    @State private var summary = ""
    @State private var next = ""
    @State private var owner = ""
    @State private var candidates: [ConsultationCandidate] = []
    @State private var reasons: [ConsultationFailureReason] = []
    @State private var continueFollowUp = true
    @State private var nextFollowUpAt = Date().addingTimeInterval(24 * 3600)
    @State private var reasonID = ""
    @State private var reasonOther = ""
    var body: some View {
        NavigationStack {
            Form {
                TextField("咨询结果", text: $summary, axis: .vertical)
                Toggle("继续推进", isOn: $continueFollowUp)
                TextField("下一步", text: $next, axis: .vertical)
                Picker("下一负责人", selection: $owner) { Text("请选择").tag(""); ForEach(candidates) { Text($0.displayName).tag($0.id) } }
                if continueFollowUp { DatePicker("下次跟进", selection: $nextFollowUpAt) }
                else {
                    Picker("失败原因", selection: $reasonID) { Text("请选择").tag(""); ForEach(reasons) { Text($0.label).tag($0.id) } }
                    if selectedReason?.code == "OTHER" { TextField("其他原因说明", text: $reasonOther, axis: .vertical) }
                }
            }
                .navigationTitle("回填咨询")
                .task { async let nextCandidates = model.consultationCandidates(); async let nextReasons = model.consultationFailureReasons(); (candidates, reasons) = await (nextCandidates, nextReasons); owner = record.consultantMembershipId }
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("确认回填") { submit() }.disabled(!valid) } }
        }
    }
    private var selectedReason: ConsultationFailureReason? { reasons.first { $0.id == reasonID } }
    private var valid: Bool {
        guard !summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !next.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !owner.isEmpty else { return false }
        if continueFollowUp { return true }
        guard let selectedReason else { return false }
        return selectedReason.code != "OTHER" || !reasonOther.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    private func submit() {
        Task {
            if await model.completeConsultation(record, summary: summary, nextStep: next, continueFollowUp: continueFollowUp,
                                                nextOwner: owner, nextFollowUpAt: continueFollowUp ? nextFollowUpAt : nil,
                                                failureReason: selectedReason,
                                                failureOther: selectedReason?.code == "OTHER" ? reasonOther : nil) {
                await completed(); dismiss()
            }
        }
    }
}

private struct ContentTaskCenterView: View {
    @EnvironmentObject private var model: AppModel
    @State private var overview: ContentOverview?; @State private var showNew = false; @State private var metrics: ContentPost?
    var body: some View {
        List {
            if let overview {
                Section(overview.campaign.name) { LabeledContent("小红书有效", value: overview.myProgress.xiaohongshuValid.formatted()); LabeledContent("抖音有效", value: overview.myProgress.douyinValid.formatted()) }
                Section("我的内容") { ForEach(overview.myPosts) { post in Button { metrics = post } label: { VStack(alignment: .leading) { Text(post.topic).foregroundStyle(.primary); Text("\(post.platform) · \(post.reviewStatus) · 浏览 \(post.viewCount)").font(.caption).foregroundStyle(.secondary) } } } }
            }
        }.navigationTitle("内容任务").toolbar { Button { showNew = true } label: { Image(systemName: "plus") }.disabled(overview?.directions.isEmpty != false) }
            .task { overview = await model.contentOverview() }.refreshable { overview = await model.contentOverview() }
            .sheet(isPresented: $showNew) { if let overview { ContentCreateSheet(directions: overview.directions) { self.overview = await model.contentOverview() } } }
            .sheet(item: $metrics) { ContentMetricsSheet(post: $0) { overview = await model.contentOverview() } }
    }
}

private struct ContentCreateSheet: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    let directions: [ContentDirection]; let completed: () async -> Void
    @State private var platform = "XIAOHONGSHU"
    @State private var direction = ""
    @State private var topic = ""
    @State private var url = ""
    @State private var original = true
    var body: some View {
        NavigationStack { Form { Picker("平台", selection: $platform) { Text("小红书").tag("XIAOHONGSHU"); Text("抖音").tag("DOUYIN") }.pickerStyle(.segmented); Picker("内容方向", selection: $direction) { Text("请选择").tag(""); ForEach(directions) { Text($0.title).tag($0.id) } }; TextField("主题", text: $topic); TextField("内容链接", text: $url).textInputAutocapitalization(.never); Toggle("原创声明", isOn: $original) }
            .navigationTitle("提交内容").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("提交") { submit() }.disabled(direction.isEmpty || topic.isEmpty || url.isEmpty) } } }
    }
    private func submit() { let date = Date.now.formatted(.iso8601.year().month().day()); let body = ContentPostCreate(platform: platform, publishDate: date, directionId: direction, topic: topic, contentUrl: url, viewCount: 0, likeCount: 0, favoriteCount: 0, originalDeclared: original); Task { if await model.submitContent(body) { await completed(); dismiss() } } }
}

private struct ContentMetricsSheet: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    let post: ContentPost; let completed: () async -> Void
    @State private var views = 0
    @State private var likes = 0
    @State private var favorites = 0
    var body: some View {
        NavigationStack {
            Form { Stepper("浏览 \(views)", value: $views); Stepper("点赞 \(likes)", value: $likes); Stepper("收藏 \(favorites)", value: $favorites) }
                .navigationTitle("更新指标")
                .onAppear { views = Int(post.viewCount); likes = Int(post.likeCount); favorites = Int(post.favoriteCount) }
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { Task { if await model.updateContentMetrics(post, views: Int64(views), likes: Int64(likes), favorites: Int64(favorites)) { await completed(); dismiss() } } } } }
        }
    }
}

private struct ContentCircleView: View {
    @EnvironmentObject private var model: AppModel; @State private var posts: [ContentPost] = []
    var body: some View {
        List(posts) { post in
            VStack(alignment: .leading, spacing: 7) {
                Text(post.topic).font(.headline); Text("\(post.memberDisplayName) · \(post.platform) · \(post.publishDate)").font(.caption).foregroundStyle(.secondary)
                HStack { Label(post.viewCount.formatted(), systemImage: "eye"); Label(post.likeCount.formatted(), systemImage: "heart") }.font(.caption)
                if let url = URL(string: post.contentUrl), ["http", "https"].contains(url.scheme?.lowercased() ?? "") { Link("打开内容", destination: url) }
            }.padding(.vertical, 5)
        }.overlay { if posts.isEmpty { Text("暂无内容").foregroundStyle(.secondary) } }
            .navigationTitle("内容圈子").task { posts = await model.circlePosts() }.refreshable { posts = await model.circlePosts() }
    }
}

private struct AnnouncementCenterView: View {
    @EnvironmentObject private var model: AppModel; @State private var items: [Announcement] = []; @State private var expanded: Set<String> = []
    var body: some View {
        List(items) { item in
            VStack(alignment: .leading, spacing: 7) {
                HStack { Text(item.title).font(.headline); if item.pinned { Image(systemName: "pin.fill").foregroundStyle(.orange) } }
                Text(item.content).lineLimit(expanded.contains(item.id) ? nil : 4)
                if item.content.count > 100 { Button(expanded.contains(item.id) ? "收起" : "展开全文") { if expanded.contains(item.id) { expanded.remove(item.id) } else { expanded.insert(item.id) } }.font(.footnote) }
                Text("\(item.authorDisplayName) · \(item.publishedAt.formatted(date: .abbreviated, time: .omitted))").font(.caption).foregroundStyle(.secondary)
            }.padding(.vertical, 5)
        }.navigationTitle("公告中心").task { items = await model.announcements() }.refreshable { items = await model.announcements() }
    }
}

private struct AssistantCenterView: View {
    @EnvironmentObject private var model: AppModel; @State private var availability: AssistantAvailability?
    var body: some View {
        List {
            Section("AI 服务") { LabeledContent("状态", value: availability?.available == true ? "可用" : "暂不可用"); LabeledContent("Provider", value: availability?.provider ?? "—"); LabeledContent("模型", value: availability?.model ?? "—") }
            Section("客户洞察") { ForEach(model.customers.prefix(30)) { customer in NavigationLink(value: customer) { Text(customer.fullName).privacySensitive() } } }
            Section { Label("AI 只生成可确认洞察，不自动改写客户档案。", systemImage: "checkmark.shield") }
        }.navigationTitle("AI 助手").navigationDestination(for: Customer.self) { CustomerDetailView(customer: $0) }.task { availability = await model.assistantAvailability() }
    }
}

private struct AgentAuthorizationView: View {
    @EnvironmentObject private var model: AppModel; @State private var items: [AgentAuthorization] = []; @State private var pending: AgentAuthorization?
    var body: some View {
        List(items) { item in VStack(alignment: .leading, spacing: 7) { Text(item.clientName).font(.headline); Text(item.scopes.sorted().joined(separator: " · ")).font(.caption).foregroundStyle(.secondary); Button("撤销授权", role: .destructive) { pending = item } }.padding(.vertical, 5) }
            .overlay { if items.isEmpty { Text("暂无 Agent 授权").foregroundStyle(.secondary) } }.navigationTitle("我的 Agent").task { await load() }
            .confirmationDialog("撤销 \(pending?.clientName ?? "") 的授权？", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
                Button("确认撤销", role: .destructive) { if let item = pending { Task { if await model.revokeAgentAuthorization(item.id) { await load() }; pending = nil } } }; Button("取消", role: .cancel) { pending = nil }
            }
    }
    private func load() async { items = await model.agentAuthorizations() }
}

private struct DeviceManagementView: View {
    @EnvironmentObject private var model: AppModel; @State private var items: [MobileDevice] = []; @State private var pending: MobileDevice?
    var body: some View {
        List(items) { item in
            VStack(alignment: .leading, spacing: 7) {
                HStack { Text(item.deviceName).font(.headline); if item.current { Pill(text: "当前设备", color: YinggeTheme.mint) } }
                Text("\(item.platform.rawValue) · \(item.appVersion) · \(item.lastUsedAt.formatted())").font(.caption).foregroundStyle(.secondary)
                if !item.current && item.revokedAt == nil { Button("撤销设备", role: .destructive) { pending = item } }
            }.padding(.vertical, 5)
        }.navigationTitle("设备管理").task { await load() }
            .confirmationDialog("撤销这台设备？", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) { Button("确认撤销", role: .destructive) { if let item = pending { Task { if await model.revokeDevice(item.id) { await load() }; pending = nil } } }; Button("取消", role: .cancel) { pending = nil } }
    }
    private func load() async { items = await model.devices() }
}

struct ReleaseHistoryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var releases: [ProductRelease] = []
    @State private var expanded: Set<String> = []
    var body: some View {
        List {
            if releases.isEmpty {
                Label("暂无更新记录", systemImage: "clock.arrow.circlepath").foregroundStyle(.secondary)
            }
            ForEach(releases) { release in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(release.title).font(.headline)
                        Spacer()
                        if release.current { Pill(text: "当前", color: YinggeTheme.mint) }
                    }
                    Text("v\(release.version) · \(release.releasedAt.formatted(date: .abbreviated, time: .omitted))")
                        .font(.caption).foregroundStyle(.secondary)
                    Text(release.summary).lineLimit(expanded.contains(release.id) ? nil : 3)
                    if expanded.contains(release.id) {
                        ForEach(orderedSectionKeys(release), id: \.self) { category in
                            let entries = release.sections[category] ?? []
                            if !entries.isEmpty {
                                VStack(alignment: .leading, spacing: 5) {
                                    Text(category).font(.subheadline.bold()).foregroundStyle(YinggeTheme.blue)
                                    ForEach(entries, id: \.self) { Text("• \($0)").font(.subheadline) }
                                }
                            }
                        }
                    }
                    if !release.sections.isEmpty || release.summary.count > 90 {
                        Button(expanded.contains(release.id) ? "收起" : "查看完整更新") {
                            if expanded.contains(release.id) { expanded.remove(release.id) } else { expanded.insert(release.id) }
                        }.font(.footnote)
                    }
                    if !release.acknowledged {
                        Button("我已了解") { Task { if await model.acknowledgeRelease(release) { await load() } } }
                            .buttonStyle(.borderedProminent)
                    }
                }.padding(.vertical, 6)
            }
        }
        .navigationTitle("更新日志")
        .task { await load() }
        .refreshable { await load() }
    }
    private func load() async { releases = await model.releaseHistory() }
    private func orderedSectionKeys(_ release: ProductRelease) -> [String] {
        let preferred = ["新增", "改进", "修复", "安全"]
        let known = preferred.filter { release.sections[$0] != nil }
        let rest = release.sections.keys.filter { !preferred.contains($0) }.sorted()
        return known + rest
    }
}

struct ProductReleaseNoticeView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let release: ProductRelease
    @State private var expanded = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Image(systemName: "sparkles.rectangle.stack.fill").font(.system(size: 44)).foregroundStyle(YinggeTheme.blue)
                    Text(release.title).font(.largeTitle.bold())
                    Text("v\(release.version) · \(release.releasedAt.formatted(date: .long, time: .omitted))").foregroundStyle(.secondary)
                    Text(release.summary).font(.title3)
                    if expanded {
                        ForEach(release.sections.keys.sorted(), id: \.self) { category in
                            VStack(alignment: .leading, spacing: 7) {
                                Text(category).font(.headline).foregroundStyle(YinggeTheme.blue)
                                ForEach(release.sections[category] ?? [], id: \.self) { Text("• \($0)") }
                            }.yinggeCard()
                        }
                    }
                    Button(expanded ? "收起更新详情" : "查看完整更新") {
                        if expanded { expanded = false }
                        else { Task { if await model.acknowledgeRelease(release, clearNotice: false) { expanded = true } } }
                    }.buttonStyle(.bordered)
                    Button("我已了解") { acknowledge() }.buttonStyle(.borderedProminent).controlSize(.large)
                }.padding(24)
            }.background(YinggeTheme.mist).navigationTitle("版本更新").navigationBarTitleDisplayMode(.inline)
                .interactiveDismissDisabled()
        }
    }
    private func acknowledge() {
        Task { if await model.acknowledgeRelease(release) { dismiss() } }
    }
}

struct MobileAppReleaseView: View {
    @EnvironmentObject private var model: AppModel
    let release: MobileAppRelease
    var body: some View {
        ZStack {
            YinggeTheme.mist.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Image(systemName: release.mandatory ? "exclamationmark.shield.fill" : "arrow.down.app.fill")
                        .font(.system(size: 52)).foregroundStyle(release.mandatory ? YinggeTheme.coral : YinggeTheme.blue)
                    Text(release.mandatory ? "需要更新后继续使用" : "发现新版本").font(.largeTitle.bold())
                    Text("v\(release.versionName)（\(release.versionCode)）").foregroundStyle(.secondary)
                    Text(release.notes).font(.title3)
                    Label("当前为 Ad Hoc 手动安装版本，请联系管理员获取经签名的新安装包。", systemImage: "person.crop.circle.badge.questionmark")
                        .font(.footnote).foregroundStyle(.secondary).yinggeCard()
                    if !release.mandatory {
                        Button("稍后提醒") { model.appRelease = nil }.buttonStyle(.bordered).controlSize(.large)
                    }
                }.padding(28)
            }
        }.interactiveDismissDisabled(release.mandatory)
    }
}
