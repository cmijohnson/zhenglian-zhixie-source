import SwiftUI

struct TimelineView: View {
    @EnvironmentObject private var model: AppModel
    let customer: Customer
    @State private var items: [Interaction] = []
    @State private var showAdd = false
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(items) { interaction in
                    HStack(alignment: .top, spacing: 14) {
                        VStack(spacing: 0) { Circle().fill(YinggeTheme.blue).frame(width: 12, height: 12); Rectangle().fill(YinggeTheme.blue.opacity(0.18)).frame(width: 2, height: 118) }
                        VStack(alignment: .leading, spacing: 7) {
                            HStack { Pill(text: interaction.channel); Spacer(); Text(interaction.contactedAt, style: .date).font(.caption).foregroundStyle(.secondary) }
                            Text(interaction.summary).font(.headline)
                            if let details = interaction.details, !details.isEmpty { ExpandableText(text: details) }
                            if let next = interaction.nextFollowUpAt { Label("下次：\(next.formatted(date: .abbreviated, time: .shortened))", systemImage: "calendar").font(.caption).foregroundStyle(.secondary) }
                        }.frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, 16)
                    }
                }
                if items.isEmpty { VStack(spacing: 10) { Image(systemName: "text.bubble").font(.largeTitle); Text("暂无时间线记录") }.foregroundStyle(.secondary).padding(50) }
            }.padding()
        }.background(YinggeTheme.mist).navigationTitle("\(customer.fullName) · 时间线").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .primaryAction) { Button { showAdd = true } label: { Label("新增", systemImage: "plus") } } }
            .sheet(isPresented: $showAdd) { NavigationStack { InteractionForm(customer: customer) { Task { items = await model.interactions(for: customer.id) } } } }
            .task { items = await model.interactions(for: customer.id) }
    }
}

struct ExpandableText: View {
    let text: String; @State private var expanded = false
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(text).font(.subheadline).foregroundStyle(.secondary).lineLimit(expanded ? nil : 4)
            if text.count > 100 { Button(expanded ? "收起" : "展开") { withAnimation { expanded.toggle() } }.font(.caption.bold()) }
        }
    }
}

private struct InteractionForm: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let customer: Customer; let onSaved: () -> Void
    @State private var request = InteractionCreateRequest()
    var body: some View {
        Form {
            Section("沟通事实") {
                DatePicker("实际发生时间", selection: $request.contactedAt)
                Picker("渠道", selection: $request.channel) { ForEach(["QQ", "微信", "电话", "线下", "其他"], id: \.self) { Text($0) } }
                Picker("记录类型", selection: $request.interactionType) {
                    Text("客户有回复").tag("CUSTOMER_REPLIED"); Text("真实触达未回复").tag("OUTREACH_NO_REPLY")
                    Text("约定以后联系").tag("FUTURE_CONTACT_AGREED"); Text("内部备注").tag("INTERNAL_NOTE")
                }
            }
            Section("内容") {
                TextField("摘要 *", text: $request.summary, axis: .vertical).lineLimit(2...5)
                TextField("详细记录", text: Binding(get: { request.details ?? "" }, set: { request.details = $0.isEmpty ? nil : $0 }), axis: .vertical).lineLimit(4...12)
                TextField("沟通结果", text: Binding(get: { request.outcome ?? "" }, set: { request.outcome = $0.isEmpty ? nil : $0 }), axis: .vertical)
                DatePicker("建议下次跟进", selection: Binding(get: { request.nextFollowUpAt ?? Date().addingTimeInterval(86_400) }, set: { request.nextFollowUpAt = $0 }))
            }
        }.navigationTitle("记录沟通").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { Task { if await model.addInteraction(customerID: customer.id, request: request) { onSaved(); dismiss() } } }.disabled(request.summary.isEmpty) }
            }
    }
}

