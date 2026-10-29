import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showServer = false
    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    Text(String(model.actor?.displayName.prefix(1) ?? "英")).font(.title2.bold()).foregroundStyle(.white).frame(width: 54, height: 54).background(YinggeTheme.gradient, in: Circle())
                    VStack(alignment: .leading) { Text(model.actor?.displayName ?? "").font(.headline); Text(model.actor?.roles.sorted().joined(separator: " · ") ?? "").font(.caption).foregroundStyle(.secondary) }
                }.padding(.vertical, 6)
            }
            Section("效率工具") {
                NavigationLink { QQWorkbenchView() } label: { Label("QQ 对话工作台", systemImage: "message.fill") }
                NavigationLink { NotificationInboxView() } label: { Label("通知中心", systemImage: "tray.full.fill") }
                NavigationLink { NotificationSettingsView() } label: { Label("通知偏好", systemImage: "bell.badge") }
            }
            Section("全部业务") {
                ForEach(NativeFeatures.all) { feature in
                    NavigationLink { NativeBusinessDestination(feature: feature) } label: {
                        Label { VStack(alignment: .leading) { Text(feature.title); Text(feature.subtitle).font(.caption).foregroundStyle(.secondary) } } icon: { Image(systemName: feature.icon) }
                    }
                }
            }
            Section("应用") {
                Button { showServer = true } label: { Label("服务器设置", systemImage: "server.rack") }
                LabeledContent("版本", value: (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "1.0.0")
            }
            Section { Button(role: .destructive) { Task { await model.logout() } } label: { Label("退出登录", systemImage: "rectangle.portrait.and.arrow.right") } }
        }.navigationTitle("更多").sheet(isPresented: $showServer) { ServerSettingsView() }
    }
}

struct NotificationSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var values = NotificationPreferences()
    @State private var saving = false
    @State private var saved = false
    var body: some View {
        Form {
            Section("设备推送") {
                LabeledContent("状态", value: model.pushStatus)
                Button("重新请求系统通知权限") { Task { await model.startPushLifecycle() } }
                Button("停用本设备推送", role: .destructive) { Task { _ = await model.unregisterPush() } }
            }
            Toggle("紧急提醒", isOn: $values.urgentReminders); Toggle("任务", isOn: $values.tasks)
            Toggle("咨询交接", isOn: $values.consultations); Toggle("公告", isOn: $values.announcements); Toggle("版本更新", isOn: $values.releases)
            Section {
                Button(saved ? "已保存" : "保存通知偏好") { save() }.disabled(saving)
            }
            Section { Text("系统会完成 APNs 权限和设备令牌生命周期。服务器当前只接受真实 UniPush registrationId；未接入 UniPush iOS SDK 前不会把 APNs 令牌伪装后上传。") .font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle("通知偏好").onChange(of: values) { _ in saved = false }
    }
    private func save() { saving = true; Task { if let result = await model.saveNotificationPreferences(values) { values = result; saved = true }; saving = false } }
}
