import SwiftUI

struct ReminderListView: View {
    @EnvironmentObject private var model: AppModel
    private var sorted: [Reminder] { model.reminders.sorted { $0.remindAt < $1.remindAt } }
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(sorted) { reminder in
                    HStack(spacing: 14) {
                        Image(systemName: reminder.overdue ? "exclamationmark" : "bell.fill").font(.headline)
                            .foregroundStyle(reminder.overdue ? .white : YinggeTheme.blue).frame(width: 42, height: 42)
                            .background(reminder.overdue ? YinggeTheme.coral : YinggeTheme.blue.opacity(0.12), in: Circle())
                        VStack(alignment: .leading, spacing: 5) {
                            Text(reminder.title).font(.headline).lineLimit(2)
                            Text(reminder.remindAt.formatted(date: .abbreviated, time: .shortened)).font(.subheadline).foregroundStyle(reminder.overdue ? YinggeTheme.coral : .secondary)
                            if reminder.status == "COMPLETED" { Pill(text: "已完成", color: YinggeTheme.mint) }
                        }; Spacer()
                        if reminder.status == "PENDING" && !model.offlineReadOnly && model.actor?.canWriteCustomers == true {
                            Button("完成") { Task { _ = await model.completeReminder(reminder) } }
                                .buttonStyle(.bordered).controlSize(.small)
                        }
                    }.yinggeCard()
                }
                if sorted.isEmpty { VStack(spacing: 12) { Image(systemName: "bell.slash").font(.largeTitle); Text("当前没有跟进提醒") }.foregroundStyle(.secondary).padding(60) }
            }.padding()
        }.background(YinggeTheme.mist).navigationTitle("跟进提醒").refreshable { await model.refreshHome() }
    }
}

struct DailySummaryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var deep = 0; @State private var issues = ""; @State private var plan = ""; @State private var saved = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionEyebrow(text: "Daily reflection"); Text("每日小结").font(.largeTitle.bold())
                    Text(Date().formatted(date: .complete, time: .omitted)).foregroundStyle(.secondary)
                }
                if saved {
                    VStack(spacing: 16) { Image(systemName: "checkmark.circle.fill").font(.system(size: 54)).foregroundStyle(YinggeTheme.mint); Text("今日小结已保存").font(.title2.bold()); Button("继续修改") { saved = false } }.frame(maxWidth: .infinity).padding(32).yinggeCard()
                } else {
                    VStack(alignment: .leading, spacing: 18) {
                        ReadonlyMetric(title: "26 级新增有效线索", value: model.dailySummary?.grade26NewValidLeads ?? 0)
                        if model.dailySummary?.performanceManaged == true {
                            ReadonlyMetric(title: "深度沟通（审核后）", value: model.dailySummary?.deepCommunicationCount ?? 0)
                        } else { Stepper("深度沟通人数：\(deep)", value: $deep, in: 0...999) }
                        FieldBlock(title: "今天遇到的问题", text: $issues, prompt: "记录阻碍、需要协助的事项")
                        FieldBlock(title: "明天的计划", text: $plan, prompt: "写下明确、可执行的下一步")
                        Button { Task { saved = await model.saveDaily(deep: deep, issues: issues, plan: plan) } } label: {
                            Text(model.dailySummary?.submitted == true ? "保存修改" : "提交小结").fontWeight(.semibold).frame(maxWidth: .infinity, minHeight: 52)
                        }.buttonStyle(.plain).foregroundStyle(.white).background(YinggeTheme.gradient, in: RoundedRectangle(cornerRadius: 16)).disabled(issues.isEmpty || plan.isEmpty || model.busy)
                    }.yinggeCard()
                }
            }.padding()
        }.background(YinggeTheme.mist).navigationTitle("小结").navigationBarTitleDisplayMode(.inline)
            .task { await model.loadDaily(); hydrate() }.onChange(of: model.dailySummary?.version) { _ in hydrate() }
    }
    private func hydrate() { guard let v = model.dailySummary else { return }; deep = v.deepCommunicationCount; issues = v.todayIssues; plan = v.tomorrowPlan }
}
private struct ReadonlyMetric: View {
    let title: String; let value: Int
    var body: some View { HStack { Text(title); Spacer(); Text(value.formatted()).font(.title2.bold()).foregroundStyle(YinggeTheme.blue) }.accessibilityElement(children: .combine) }
}
private struct FieldBlock: View {
    let title: String; @Binding var text: String; let prompt: String
    var body: some View { VStack(alignment: .leading, spacing: 8) { Text(title).font(.headline); TextField(prompt, text: $text, axis: .vertical).lineLimit(5...10).padding(12).background(YinggeTheme.mist, in: RoundedRectangle(cornerRadius: 14)) } }
}
