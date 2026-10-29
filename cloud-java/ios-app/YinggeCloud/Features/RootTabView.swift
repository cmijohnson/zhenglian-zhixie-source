import SwiftUI

struct RootTabView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        TabView {
            NavigationStack { WorkbenchView() }.tabItem { Label("工作台", systemImage: "square.grid.2x2.fill") }
            NavigationStack { CustomerListView() }.tabItem { Label("客户", systemImage: "person.2.fill") }
            NavigationStack { ReminderListView() }.tabItem { Label("跟进", systemImage: "bell.badge.fill") }
            if model.actor?.roles.contains("VIEWER") == true {
                NavigationStack { NotificationInboxView() }.tabItem { Label("通知", systemImage: "tray.full.fill") }
            } else {
                NavigationStack { DailySummaryView() }.tabItem { Label("小结", systemImage: "square.and.pencil") }
            }
            NavigationStack { MoreView() }.tabItem { Label("更多", systemImage: "ellipsis.circle.fill") }
        }
        .task { await model.refreshHome() }
        .fullScreenCover(item: $model.appRelease) { release in MobileAppReleaseView(release: release) }
        .sheet(item: Binding(
            get: { model.appRelease == nil ? model.productReleaseNotice : nil },
            set: { if $0 == nil { model.productReleaseNotice = nil } }
        )) { release in ProductReleaseNoticeView(release: release) }
    }
}

struct NotificationInboxView: View {
    @EnvironmentObject private var model: AppModel
    @State private var inbox: NotificationInbox?
    @State private var unreadOnly = false
    @State private var expanded: Set<String> = []
    var body: some View {
        List {
            Section { Label("紧急提醒与系统通知会通过受控推送送达", systemImage: "bell.badge.fill") }
            Section {
                Toggle("只看未读", isOn: $unreadOnly)
                LabeledContent("未读", value: (inbox?.unreadCount ?? 0).formatted())
            }
            Section("业务通知") {
                if inbox?.items.isEmpty != false { Text("暂无通知").foregroundStyle(.secondary) }
                ForEach(inbox?.items ?? []) { item in
                    VStack(alignment: .leading, spacing: 7) {
                        HStack(alignment: .firstTextBaseline) {
                            Circle().fill(item.read ? Color.secondary.opacity(0.25) : YinggeTheme.blue).frame(width: 8, height: 8)
                            Text(item.title).font(.headline)
                            Spacer()
                            Text(item.createdAt.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                        }
                        Text(item.summary).lineLimit(expanded.contains(item.id) ? nil : 3)
                        if item.summary.count > 80 {
                            Button(expanded.contains(item.id) ? "收起" : "展开详情") {
                                if expanded.contains(item.id) { expanded.remove(item.id) } else { expanded.insert(item.id) }
                            }.font(.footnote)
                        }
                        HStack {
                            Text(item.eventType).font(.caption2).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 5)
                }
            }
        }
        .navigationTitle("通知")
        .task { await load() }
        .refreshable { await load() }
        .onChange(of: unreadOnly) { _ in Task { await load() } }
    }
    private func load() async { inbox = await model.notifications(unreadOnly: unreadOnly) }
}

struct WorkbenchView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    SectionEyebrow(text: "Today")
                    Text("早上好，\(model.actor?.displayName ?? "伙伴")").font(.largeTitle.bold()).foregroundStyle(.white)
                    Text("今天也把最重要的客户往前推进一步。") .foregroundStyle(.white.opacity(0.78))
                }.frame(maxWidth: .infinity, alignment: .leading).padding(22).background(YinggeTheme.gradient, in: RoundedRectangle(cornerRadius: 28))
                if model.offlineReadOnly {
                    Label("当前展示上次同步的加密缓存，仅供离线查看；联网后下拉刷新再进行业务操作。", systemImage: "wifi.slash")
                        .font(.footnote).foregroundStyle(.orange).yinggeCard()
                }
                if let stats = model.dashboard?.stats {
                    LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 12) {
                        MetricCard(title: "活跃客户", value: stats.totalActive, icon: "person.2.fill", color: YinggeTheme.blue)
                        MetricCard(title: "今日到期", value: stats.todayDueCount, icon: "clock.fill", color: .orange)
                        MetricCard(title: "已逾期", value: stats.overdueCount, icon: "exclamationmark.triangle.fill", color: YinggeTheme.coral)
                        MetricCard(title: "高意向", value: stats.highIntentionCount, icon: "sparkles", color: YinggeTheme.mint)
                    }
                } else { ProgressView("正在加载工作台…").frame(maxWidth: .infinity).yinggeCard() }
                Text("最近更新").font(.title3.bold())
                ForEach(model.dashboard?.recentUpdated.prefix(4).map { $0 } ?? []) { customer in
                    NavigationLink(value: customer) { CustomerRow(customer: customer) }.buttonStyle(.plain)
                }
            }.padding()
        }.background(YinggeTheme.mist).navigationDestination(for: Customer.self) { CustomerDetailView(customer: $0) }
            .refreshable { await model.refreshHome() }.navigationTitle("工作台").navigationBarTitleDisplayMode(.inline)
    }
}

private struct MetricCard: View {
    let title: String; let value: Int; let icon: String; let color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon).foregroundStyle(color).font(.title3)
            Text(value.formatted()).font(.system(size: 30, weight: .bold, design: .rounded))
            Text(title).font(.footnote).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, alignment: .leading).yinggeCard().accessibilityElement(children: .combine)
    }
}
