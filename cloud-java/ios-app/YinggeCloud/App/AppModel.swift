import Foundation
import SwiftUI
import UIKit
import UserNotifications

@MainActor
final class AppModel: ObservableObject {
    enum Phase: Equatable { case restoring, signedOut, passwordChangeRequired, signedIn }
    @Published var phase: Phase = .restoring
    @Published var actor: Me?
    @Published var dashboard: Dashboard?
    @Published var customers: [Customer] = []
    @Published var reminders: [Reminder] = []
    @Published var dailySummary: DailySummary?
    @Published var offlineReadOnly = false
    @Published var appRelease: MobileAppRelease?
    @Published var productReleaseNotice: ProductRelease?
    @Published var pushStatus = "尚未请求系统通知权限"
    @Published var errorMessage: String?
    @Published var busy = false

    @AppStorage("yingge.serverURL") var serverURL = "http://192.0.2.10"
    private var repository: LiveYinggeRepository?

    func restore() async {
        guard let repository = makeRepository() else { phase = .signedOut; return }
        self.repository = repository
        do {
            let bootstrap = try await repository.restore()
            actor = bootstrap.actor; phase = bootstrap.actor.passwordChangeRequired ? .passwordChangeRequired : .signedIn
            if phase == .signedIn { await refreshHome(); await startPushLifecycle() }
        } catch { phase = .signedOut }
    }

    func login(username: String, password: String) async {
        guard let repository = makeRepository() else { errorMessage = "服务器地址无效"; return }
        self.repository = repository; busy = true; defer { busy = false }
        do {
            actor = try await repository.login(username: username, password: password)
            phase = actor?.passwordChangeRequired == true ? .passwordChangeRequired : .signedIn
            if phase == .signedIn { await refreshHome(); await startPushLifecycle() }
        } catch { errorMessage = error.localizedDescription }
    }
    func registrationOptions(code: String) async -> [RegistrationTeam] {
        guard let repository = repository ?? makeRepository() else { errorMessage = "服务器地址无效"; return [] }
        self.repository = repository
        do { return try await repository.registrationOptions(code: code).teams }
        catch { errorMessage = error.localizedDescription; return [] }
    }
    func register(username: String, realName: String, password: String, code: String, teamID: String) async -> Bool {
        guard let repository = repository ?? makeRepository() else { errorMessage = "服务器地址无效"; return false }
        self.repository = repository; busy = true; defer { busy = false }
        do {
            _ = try await repository.register(.init(username: username, realName: realName, password: password, registrationCode: code, teamId: teamID))
            actor = try await repository.login(username: username, password: password)
            phase = actor?.passwordChangeRequired == true ? .passwordChangeRequired : .signedIn
            if phase == .signedIn { await refreshHome(); await startPushLifecycle() }; return true
        } catch { errorMessage = error.localizedDescription; return false }
    }
    func changePassword(current: String, new: String) async -> Bool {
        guard let repository else { return false }; busy = true; defer { busy = false }
        do { try await repository.changePassword(current: current, new: new); actor = nil; phase = .signedOut; return true }
        catch { errorMessage = error.localizedDescription; return false }
    }

    func logout() async {
        try? await repository?.logout(); actor = nil; dashboard = nil; customers = []; reminders = []; offlineReadOnly = false; appRelease = nil; productReleaseNotice = nil; phase = .signedOut
    }
    func enterBackground() async { await repository?.enterBackground() }

    func applyServer(_ value: String) async -> Bool {
        let candidate = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: candidate), YinggeAPIClient.validatedBaseURL(url) != nil else { errorMessage = "服务器地址无效"; return false }
        busy = true; defer { busy = false }
        if let repository { try? await repository.logout() }
        serverURL = candidate
        repository = nil; actor = nil; dashboard = nil; customers = []; reminders = []
        offlineReadOnly = false; appRelease = nil; productReleaseNotice = nil; phase = .signedOut
        return true
    }

    func refreshHome() async {
        guard let repository else { return }
        do {
            await repository.beginNetworkRefresh()
            async let dash = repository.dashboard()
            async let customerPage = repository.customers(query: nil)
            async let reminderPage = repository.reminders()
            let (d, c, r) = try await (dash, customerPage, reminderPage)
            dashboard = d; customers = c.items; reminders = r
            offlineReadOnly = await repository.isOfflineReadOnly()
        } catch { errorMessage = error.localizedDescription }
        do { appRelease = try await repository.currentAppRelease() } catch { appRelease = nil }
        do { productReleaseNotice = try await repository.unacknowledgedRelease() } catch { productReleaseNotice = nil }
    }

    func loadDaily() async {
        do { dailySummary = try await repository?.dailyToday() }
        catch { errorMessage = error.localizedDescription }
    }
    func saveDaily(deep: Int, issues: String, plan: String) async -> Bool {
        guard requireOnlineWrite(), let repository else { return false }; busy = true; defer { busy = false }
        do {
            dailySummary = try await repository.saveDaily(deep: deep, issues: issues, plan: plan); return true
        } catch { errorMessage = error.localizedDescription; return false }
    }
    func saveCustomer(_ draft: CustomerWriteRequest, id: String?) async -> Customer? {
        guard requireOnlineWrite(), let repository else { return nil }; busy = true; defer { busy = false }
        do { let value = try await repository.saveCustomer(draft, id: id); await refreshHome(); return value }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func interactions(for customerID: String) async -> [Interaction] {
        do { return try await repository?.interactions(customerID: customerID) ?? [] }
        catch { errorMessage = error.localizedDescription; return [] }
    }
    func assistantSummary(for customerID: String) async -> AssistantCustomerSummary? {
        do { return try await repository?.assistantSummary(customerID: customerID) }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func completeReminder(_ reminder: Reminder) async -> Bool {
        guard requireOnlineWrite() else { return false }
        do {
            let updated = try await repository?.updateReminder(reminder.id, status: "COMPLETED")
            if let updated, let index = reminders.firstIndex(where: { $0.id == updated.id }) { reminders[index] = updated }
            return true
        }
        catch { errorMessage = error.localizedDescription; return false }
    }
    func addInteraction(customerID: String, request: InteractionCreateRequest) async -> Bool {
        guard requireOnlineWrite() else { return false }
        do { _ = try await repository?.addInteraction(customerID: customerID, request: request); return true }
        catch { errorMessage = error.localizedDescription; return false }
    }
    func requestDeal(for customer: Customer) async -> DealRequest? {
        guard requireOnlineWrite() else { return nil }
        do { let value = try await repository?.requestDeal(customerID: customer.id); await refreshHome(); return value }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func qqExchangeRequest() async -> URLRequest? {
        do { return try await repository?.qqExchangeRequest() }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func releaseHistory() async -> [ProductRelease] { do { return try await repository?.releaseHistory().items ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func acknowledgeRelease(_ release: ProductRelease, clearNotice: Bool = true) async -> Bool {
        guard requireOnlineWrite() else { return false }
        do {
            try await repository?.acknowledgeRelease(release.id)
            if clearNotice, productReleaseNotice?.id == release.id { productReleaseNotice = nil }
            return true
        }
        catch { errorMessage = error.localizedDescription; return false }
    }
    func notifications(unreadOnly: Bool = false) async -> NotificationInbox? {
        do { return try await repository?.notifications(unreadOnly: unreadOnly) }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func saveNotificationPreferences(_ values: NotificationPreferences) async -> NotificationPreferences? {
        guard requireOnlineWrite() else { return nil }
        do { return try await repository?.saveNotificationPreferences(values) }
        catch { errorMessage = error.localizedDescription; return nil }
    }
    func startPushLifecycle() async {
        let center = UNUserNotificationCenter.current()
        do {
            var settings = await center.notificationSettings()
            if settings.authorizationStatus == .notDetermined {
                _ = try await center.requestAuthorization(options: [.alert, .badge, .sound])
                settings = await center.notificationSettings()
            }
            guard [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus) else {
                pushStatus = "系统通知权限未开启"; return
            }
            UIApplication.shared.registerForRemoteNotifications()
            pushStatus = "系统通知已授权，正在注册 APNs"
        } catch { pushStatus = "系统通知授权失败：\(error.localizedDescription)" }
    }
    func receivedAPNSToken(_ data: Data) {
        guard !data.isEmpty else { pushStatus = "APNs 返回了空令牌"; return }
        pushStatus = "APNs 已注册；等待 UniPush SDK 提供 registrationId"
    }
    func receivedPushRegistrationFailure(_ error: Error) { pushStatus = "APNs 注册失败：\(error.localizedDescription)" }
    func registerUniPush(registrationID: String, vendor: String?) async {
        guard requireOnlineWrite(), let repository else { return }
        let clean = registrationID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.count >= 16 else { pushStatus = "UniPush registrationId 无效"; return }
        do { try await repository.registerUniPush(registrationID: clean, vendor: vendor); pushStatus = "推送设备已在客户云注册" }
        catch { errorMessage = error.localizedDescription; pushStatus = "推送注册失败" }
    }
    func unregisterPush() async -> Bool {
        guard requireOnlineWrite(), let repository else { return false }
        do { try await repository.unregisterPush(); pushStatus = "客户云推送已停用"; return true }
        catch { errorMessage = error.localizedDescription; return false }
    }
    func workTasks() async -> [WorkTask] { do { return try await repository?.workTasks() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func claimTask(_ id: String) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.claimTask(id); return true } catch { errorMessage = error.localizedDescription; return false } }
    func submitTask(_ id: String, summary: String, evidenceURL: String?) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.submitTask(id, summary: summary, evidenceURL: evidenceURL); return true } catch { errorMessage = error.localizedDescription; return false } }
    func performanceToday() async -> PerformanceToday? { do { return try await repository?.performanceToday() } catch { errorMessage = error.localizedDescription; return nil } }
    func createPerformanceClaim(interactionID: String, type: String) async -> PerformanceClaim? { guard requireOnlineWrite() else { return nil }; do { return try await repository?.createPerformanceClaim(interactionID: interactionID, type: type) } catch { errorMessage = error.localizedDescription; return nil } }
    func uploadAndSubmitPerformanceClaim(_ claim: PerformanceClaim, files: [PerformanceEvidenceFile]) async -> Bool {
        guard requireOnlineWrite(), let repository else { return false }
        guard !files.isEmpty || !claim.evidence.isEmpty else { errorMessage = "请至少添加一张证据图片"; return false }
        guard claim.evidence.count + files.count <= 3 else { errorMessage = PerformanceEvidenceValidationError.tooMany.localizedDescription; return false }
        do {
            for file in files { _ = try await repository.uploadPerformanceEvidence(claimID: claim.id, file: file) }
            _ = try await repository.submitPerformanceClaim(claim.id); return true
        } catch { errorMessage = error.localizedDescription; return false }
    }
    func consultations() async -> [ConsultationRecord] { do { return try await repository?.consultations() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func consultation(_ id: String) async -> ConsultationRecord? { do { return try await repository?.consultation(id) } catch { errorMessage = error.localizedDescription; return nil } }
    func consultationCandidates() async -> [ConsultationCandidate] { do { return try await repository?.consultationCandidates() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func consultationFailureReasons() async -> [ConsultationFailureReason] { do { return try await repository?.consultationFailureReasons() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func createConsultation(_ body: ConsultationCreate) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.createConsultation(body); return true } catch { errorMessage = error.localizedDescription; return false } }
    func completeConsultation(_ record: ConsultationRecord, summary: String, nextStep: String, continueFollowUp: Bool,
                              nextOwner: String, nextFollowUpAt: Date?, failureReason: ConsultationFailureReason?, failureOther: String?) async -> Bool {
        guard requireOnlineWrite() else { return false }
        do { _ = try await repository?.completeConsultation(record.id, body: .init(expectedVersion: record.version, resultSummary: summary, continueFollowUp: continueFollowUp, nextStep: nextStep, nextOwnerMembershipId: nextOwner, nextFollowUpAt: nextFollowUpAt, failureReasonId: continueFollowUp ? nil : failureReason?.id, failureReasonOther: continueFollowUp ? nil : failureOther)); return true }
        catch { errorMessage = error.localizedDescription; return false }
    }
    func contentOverview() async -> ContentOverview? { do { return try await repository?.contentOverview() } catch { errorMessage = error.localizedDescription; return nil } }
    func submitContent(_ body: ContentPostCreate) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.submitContent(body); return true } catch { errorMessage = error.localizedDescription; return false } }
    func updateContentMetrics(_ post: ContentPost, views: Int64, likes: Int64, favorites: Int64) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.updateContentMetrics(post.id, body: .init(viewCount: views, likeCount: likes, favoriteCount: favorites)); return true } catch { errorMessage = error.localizedDescription; return false } }
    func circlePosts() async -> [ContentPost] { do { return try await repository?.circlePosts().items ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func announcements() async -> [Announcement] { do { return try await repository?.announcements() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func agentAuthorizations() async -> [AgentAuthorization] { do { return try await repository?.agentAuthorizations() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func revokeAgentAuthorization(_ id: String) async -> Bool { guard requireOnlineWrite() else { return false }; do { try await repository?.revokeAgentAuthorization(id); return true } catch { errorMessage = error.localizedDescription; return false } }
    func devices() async -> [MobileDevice] { do { return try await repository?.devices() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func revokeDevice(_ id: String) async -> Bool { guard requireOnlineWrite() else { return false }; do { try await repository?.revokeDevice(id); return true } catch { errorMessage = error.localizedDescription; return false } }
    func assistantAvailability() async -> AssistantAvailability? { do { return try await repository?.assistantAvailability() } catch { errorMessage = error.localizedDescription; return nil } }
    func customerPool() async -> [CustomerPoolEntry] { do { return try await repository?.customerPool() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func secondLineRecruitments() async -> [SecondLineRecruitment] { do { return try await repository?.secondLineRecruitments() ?? [] } catch { errorMessage = error.localizedDescription; return [] } }
    func claimPool(_ id: String) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.claimPoolEntry(id); return true } catch { errorMessage = error.localizedDescription; return false } }
    func claimSecondLine(_ id: String) async -> Bool { guard requireOnlineWrite() else { return false }; do { _ = try await repository?.claimSecondLine(id); return true } catch { errorMessage = error.localizedDescription; return false } }
    func collaboration(customerID: String) async -> CustomerCollaboration? { do { return try await repository?.collaboration(customerID: customerID) } catch { errorMessage = error.localizedDescription; return nil } }
    func transfer(_ customer: Customer, target: CustomerCollaborationMember, reason: String?) async -> Customer? { guard requireOnlineWrite() else { return nil }; do { return try await repository?.transfer(customer: customer, targetID: target.membershipId, reason: reason) } catch { errorMessage = error.localizedDescription; return nil } }

    private func makeRepository() -> LiveYinggeRepository? {
        guard let url = URL(string: serverURL), let valid = YinggeAPIClient.validatedBaseURL(url) else { return nil }
        return LiveYinggeRepository(baseURL: valid)
    }

    private func requireOnlineWrite() -> Bool {
        guard !offlineReadOnly else {
            errorMessage = "当前为离线只读模式，请联网并刷新后再提交业务数据。"
            return false
        }
        return true
    }
}
