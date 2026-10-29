import SwiftUI

struct CustomerListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = ""
    @State private var showCreate = false
    private var filtered: [Customer] {
        guard !query.isEmpty else { return model.customers }
        return model.customers.filter { [$0.fullName, $0.nickname, $0.school, $0.major, $0.phone, $0.wechatId, $0.qqId].compactMap { $0 }.contains { $0.localizedCaseInsensitiveContains(query) } }
    }
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if model.offlineReadOnly {
                    Label("离线只读 · 客户列表来自上次加密同步", systemImage: "lock.fill")
                        .font(.footnote).foregroundStyle(.orange).frame(maxWidth: .infinity, alignment: .leading).yinggeCard()
                }
                ForEach(filtered) { customer in
                    NavigationLink(value: customer) { CustomerRow(customer: customer) }.buttonStyle(.plain)
                }
                if filtered.isEmpty { VStack(spacing: 10) { Image(systemName: "person.crop.circle.badge.questionmark").font(.largeTitle); Text("没有匹配客户").font(.headline); Text("尝试姓名、学校或联系方式").font(.subheadline) }.foregroundStyle(.secondary).padding(50) }
            }.padding()
        }.background(YinggeTheme.mist).navigationTitle("客户").searchable(text: $query, prompt: "姓名、学校或联系方式")
            .navigationDestination(for: Customer.self) { CustomerDetailView(customer: $0) }
            .toolbar { if model.actor?.canWriteCustomers == true && !model.offlineReadOnly { Button { showCreate = true } label: { Label("新建客户", systemImage: "plus") } } }
            .sheet(isPresented: $showCreate) { NavigationStack { CustomerFormView(customer: nil) } }
            .refreshable { await model.refreshHome() }
    }
}

struct CustomerRow: View {
    let customer: Customer
    var body: some View {
        HStack(spacing: 14) {
            Text(String(customer.fullName.prefix(1))).font(.title3.bold()).foregroundStyle(.white)
                .frame(width: 46, height: 46).background(YinggeTheme.gradient, in: Circle())
            VStack(alignment: .leading, spacing: 6) {
                HStack { Text(customer.fullName).font(.headline); if let quality = customer.qualityCode { Pill(text: "\(quality) 类", color: quality == "A" ? .orange : YinggeTheme.blue) } }
                Text([customer.school, customer.major, customer.grade].compactMap { $0 }.joined(separator: " · ")).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                Text(customer.primaryContact).font(.caption).foregroundStyle(.secondary).lineLimit(1).privacySensitive()
            }
            Spacer(); Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
        }.yinggeCard().accessibilityElement(children: .combine)
    }
}

struct Pill: View {
    let text: String; var color: Color = YinggeTheme.blue
    var body: some View { Text(text).font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4).foregroundStyle(color).background(color.opacity(0.12), in: Capsule()) }
}

struct CustomerDetailView: View {
    @EnvironmentObject private var model: AppModel
    @State var customer: Customer
    @State private var showEdit = false
    @State private var showTimeline = false
    @State private var assistantSummary: AssistantCustomerSummary?
    @State private var collaboration: CustomerCollaboration?
    @State private var insightExpanded = false
    @State private var confirmingDeal = false
    @State private var showTransfer = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                GroupBox("客户画像") {
                    VStack(spacing: 12) {
                        InfoRow("学校 / 专业", [customer.school, customer.major].compactMap { $0 }.joined(separator: " · ").nilIfEmpty ?? "未填写")
                        InfoRow("年级 / 城市", [customer.grade, customer.city].compactMap { $0 }.joined(separator: " · ").nilIfEmpty ?? "未填写")
                        InfoRow("联系方式", customer.primaryContact)
                        InfoRow("负责人", customer.ownerDisplayName ?? "未分配")
                        InfoRow("需求摘要", customer.demandSummary ?? "尚未补充")
                    }.padding(.top, 6)
                }.groupBoxStyle(YinggeGroupBoxStyle())
                dealCard
                if collaboration?.canTransferOwnership == true, collaboration?.transferCandidates.isEmpty == false {
                    Button { showTransfer = true } label: { Label("转接客户所有权", systemImage: "person.crop.circle.badge.arrow.forward").frame(maxWidth: .infinity, minHeight: 44) }.buttonStyle(.bordered)
                }
                intelligenceCard
                Button { showTimeline = true } label: { Label("查看与新增时间线", systemImage: "point.3.connected.trianglepath.dotted").frame(maxWidth: .infinity, minHeight: 48) }
                    .buttonStyle(.borderedProminent)
            }.padding()
        }.background(YinggeTheme.mist).navigationTitle(customer.fullName).navigationBarTitleDisplayMode(.inline)
            .toolbar { if model.actor?.canWriteCustomers == true && !model.offlineReadOnly { Button("编辑") { showEdit = true } } }
            .sheet(isPresented: $showEdit) { NavigationStack { CustomerFormView(customer: customer) } }
            .sheet(isPresented: $showTimeline) { NavigationStack { TimelineView(customer: customer) } }
            .sheet(isPresented: $showTransfer) { if let collaboration { CustomerTransferSheet(customer: customer, candidates: collaboration.transferCandidates) { value in customer = value; self.collaboration = await model.collaboration(customerID: value.id) } } }
            .task { async let nextSummary = model.assistantSummary(for: customer.id); async let nextCollaboration = model.collaboration(customerID: customer.id); (assistantSummary, collaboration) = await (nextSummary, nextCollaboration) }
            .confirmationDialog("确认申请标记成单？", isPresented: $confirmingDeal, titleVisibility: .visible) {
                Button("提交管理员确认") { Task { if await model.requestDeal(for: customer) != nil { customer.dealState = .pending; customer.canRequestDeal = false } } }
                Button("取消", role: .cancel) {}
            } message: { Text("该操作只发起申请，不会在员工端直接把客户改为已成单。") }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Text(customer.fullName).font(.largeTitle.bold()); Spacer(); Pill(text: customer.currentStatus, color: YinggeTheme.mint) }
            Text(customer.nickname ?? "无昵称").foregroundStyle(.secondary)
            HStack { if let q = customer.qualityCode { Pill(text: "\(q) 类") }; Pill(text: customer.intentionLevel + "意向", color: .orange) }
        }.frame(maxWidth: .infinity, alignment: .leading).yinggeCard()
    }
    private var dealCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionEyebrow(text: "Deal status")
            HStack {
                VStack(alignment: .leading) {
                    Text(dealTitle).font(.title3.bold())
                    Text(dealSubtitle).font(.footnote).foregroundStyle(.secondary)
                }; Spacer(); Image(systemName: customer.dealState == .won ? "checkmark.seal.fill" : "arrow.up.right.circle.fill").font(.title).foregroundStyle(customer.dealState == .won ? YinggeTheme.mint : YinggeTheme.blue)
            }
            if customer.dealState == .none && customer.canRequestDeal && model.actor?.canWriteCustomers == true {
                Button("申请标记成单") { confirmingDeal = true }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
            }
        }.yinggeCard()
    }
    private var dealTitle: String { customer.dealState == .won ? "已成单" : (customer.dealState == .pending ? "待管理员确认" : "尚未标记成单") }
    private var dealSubtitle: String { customer.dealState == .pending ? "员工申请已提交，档案不会提前变更" : "极简状态只记录是否成单，不记录金额与提成" }

    private var intelligenceCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { SectionEyebrow(text: "AI insight"); Spacer() }
            Text("客户智能分析").font(.title3.bold())
            if let content = assistantSummary?.content, !content.isEmpty {
                Text(content).font(.subheadline).foregroundStyle(.secondary).lineLimit(insightExpanded ? nil : 5)
                if content.count > 120 { Button(insightExpanded ? "收起" : "展开完整分析") { withAnimation { insightExpanded.toggle() } }.font(.footnote.bold()) }
            } else { Text("暂无可展示的助手摘要，或 AI 服务当前不可用。").foregroundStyle(.secondary) }
            Button { Task { assistantSummary = await model.assistantSummary(for: customer.id) } } label: { Label("刷新摘要", systemImage: "arrow.clockwise") }.font(.footnote.bold())
            Text("摘要来自当前客户档案与时间线，不会自动改写正式资料。").font(.caption2).foregroundStyle(.secondary)
        }.yinggeCard()
    }
}

private struct CustomerTransferSheet: View {
    @EnvironmentObject private var model: AppModel; @Environment(\.dismiss) private var dismiss
    let customer: Customer; let candidates: [CustomerCollaborationMember]; let completed: (Customer) async -> Void
    @State private var target = ""; @State private var reason = ""
    var body: some View {
        NavigationStack {
            Form {
                Section("接收人") { Picker("成员", selection: $target) { Text("请选择").tag(""); ForEach(candidates) { Text("\($0.displayName) · \($0.teamNames)").tag($0.membershipId) } } }
                Section("转接说明") { TextField("客户交接、排班调整或团队协同", text: $reason, axis: .vertical) }
                Section { Label("待办提醒会同步转移；录入人、历史时间线和现有协作者保持不变。", systemImage: "info.circle") }
            }.navigationTitle("转接客户").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("确认转接") { transfer() }.disabled(target.isEmpty) } }
        }
    }
    private func transfer() { guard let member = candidates.first(where: { $0.membershipId == target }) else { return }; Task { if let value = await model.transfer(customer, target: member, reason: reason.isEmpty ? nil : reason) { await completed(value); dismiss() } } }
}

private struct InfoRow: View {
    let label: String; let value: String
    init(_ label: String, _ value: String) { self.label = label; self.value = value }
    var body: some View { HStack(alignment: .top) { Text(label).foregroundStyle(.secondary).frame(width: 90, alignment: .leading); Text(value).frame(maxWidth: .infinity, alignment: .trailing).privacySensitive() }.font(.subheadline) }
}
private struct YinggeGroupBoxStyle: GroupBoxStyle {
    func makeBody(configuration: Configuration) -> some View { VStack(alignment: .leading, spacing: 8) { configuration.label.font(.headline); configuration.content }.yinggeCard() }
}
private extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }
