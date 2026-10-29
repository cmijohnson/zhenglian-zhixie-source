import Foundation
import UIKit

actor LiveYinggeRepository {
    private let client: YinggeAPIClient
    private let baseURL: URL
    private let keychain = KeychainStore()
    private let refreshKey: String
    private let serverScope: String
    private let intentKeys = PersistentIntentKeyStore()
    private var membershipID: String?
    private var cache: ProtectedFileCache?
    private var offlineReadOnly = false

    init(baseURL: URL) {
        self.baseURL = baseURL
        let serverScope = String(baseURL.absoluteString.lowercased().map { $0.isLetter || $0.isNumber ? $0 : "-" })
        self.serverScope = serverScope
        self.refreshKey = "mobile.refresh-token.\(serverScope)"
        let defaults = UserDefaults.standard
        let deviceID: UUID
        if let raw = defaults.string(forKey: "yingge.deviceID"), let value = UUID(uuidString: raw) { deviceID = value }
        else { deviceID = UUID(); defaults.set(deviceID.uuidString, forKey: "yingge.deviceID") }
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"
        let build = Int64(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1") ?? 1
        let store = keychain
        let refreshKey = refreshKey
        client = YinggeAPIClient(baseURL: baseURL, appVersion: version, versionCode: build, deviceID: deviceID) { token in
            try? store.set(Data(token.refreshToken.utf8), for: refreshKey)
        }
    }

    func restore() async throws -> MobileBootstrap {
        let refresh = try keychain.data(for: refreshKey).flatMap { String(data: $0, encoding: .utf8) }
        guard refresh != nil else { throw YinggeAPIError.unauthenticated }
        await client.configureTokens(accessToken: nil, refreshToken: refresh)
        let bootstrap: MobileBootstrap = try await client.send(path: EndpointCatalog.mobileBootstrap)
        configureCache(for: bootstrap.actor)
        return bootstrap
    }
    func login(username: String, password: String) async throws -> Me {
        let actor = try await client.login(username: username, password: password, deviceName: UIDevice.current.name).actor
        configureCache(for: actor)
        return actor
    }
    func registrationOptions(code: String) async throws -> RegistrationOptions {
        try await client.sendUnauthenticated(path: EndpointCatalog.registrationOptions, method: "POST", body: RegistrationOptionsRequest(registrationCode: code))
    }
    func register(_ request: MobileRegisterRequest) async throws -> RegistrationResult {
        try await client.sendUnauthenticated(path: EndpointCatalog.mobileRegister, method: "POST", body: request)
    }
    func changePassword(current: String, new: String) async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.changePassword, method: "POST", body: PasswordChangeRequest(currentPassword: current, newPassword: new))
        await client.configureTokens(accessToken: nil, refreshToken: nil)
        cache = nil
        try keychain.remove(refreshKey)
    }
    func logout() async throws {
        var remoteError: Error?
        do { try await client.logout() } catch { remoteError = error }
        await client.configureTokens(accessToken: nil, refreshToken: nil)
        cache = nil; membershipID = nil; try? keychain.remove(refreshKey)
        if let remoteError { throw remoteError }
    }
    func enterBackground() async { await client.clearAccessToken() }
    func beginNetworkRefresh() { offlineReadOnly = false }
    func isOfflineReadOnly() -> Bool { offlineReadOnly }
    func dashboard() async throws -> Dashboard {
        do {
            let value: Dashboard = try await client.send(path: EndpointCatalog.EmployeeBusiness.dashboard)
            try? await cache?.write(value, key: "dashboard")
            return value
        } catch {
            if let cached = try? await cache?.read(Dashboard.self, key: "dashboard") { offlineReadOnly = true; return cached }
            throw error
        }
    }
    func customers(query: String?) async throws -> PageResponse<Customer> {
        let q = query?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let key = "customers.\(Data(q.lowercased().utf8).base64EncodedString())"
        do {
            let value: PageResponse<Customer> = try await client.send(path: EndpointCatalog.EmployeeBusiness.customerList(search: q))
            try? await cache?.write(value, key: key)
            return value
        } catch {
            if let cached = try? await cache?.read(PageResponse<Customer>.self, key: key) { offlineReadOnly = true; return cached }
            throw error
        }
    }
    func customer(_ id: String) async throws -> Customer {
        do {
            let value: Customer = try await client.send(path: EndpointCatalog.EmployeeBusiness.customer(id))
            try? await cache?.write(value, key: "customer.\(id)")
            return value
        } catch {
            if let cached = try? await cache?.read(Customer.self, key: "customer.\(id)") { offlineReadOnly = true; return cached }
            throw error
        }
    }
    func saveCustomer(_ draft: CustomerWriteRequest, id: String?) async throws -> Customer {
        if let id { return try await client.send(path: EndpointCatalog.EmployeeBusiness.customer(id), method: "PATCH", body: draft) }
        return try await client.send(path: EndpointCatalog.EmployeeBusiness.customers, method: "POST", body: draft)
    }
    func interactions(customerID: String) async throws -> [Interaction] {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.interactions(customerID))
    }
    func addInteraction(customerID: String, request: InteractionCreateRequest) async throws -> Interaction {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.interactions(customerID), method: "POST", body: request)
    }
    func assistantSummary(customerID: String) async throws -> AssistantCustomerSummary {
        try await client.sendEventStream(path: EndpointCatalog.EmployeeBusiness.assistantSummary(customerID))
    }
    func reminders() async throws -> [Reminder] {
        do {
            let value: [Reminder] = try await client.send(path: EndpointCatalog.EmployeeBusiness.reminders)
            try? await cache?.write(value, key: "reminders")
            return value
        } catch {
            if let cached = try? await cache?.read([Reminder].self, key: "reminders") { offlineReadOnly = true; return cached }
            throw error
        }
    }
    func updateReminder(_ id: String, status: String) async throws -> Reminder {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.reminder(id), method: "PATCH",
                              body: ReminderUpdateRequest(status: status))
    }
    func dailyToday() async throws -> DailySummary { try await client.send(path: EndpointCatalog.EmployeeBusiness.dailyToday) }
    func saveDaily(deep: Int, issues: String, plan: String) async throws -> DailySummary {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
        return try await client.send(path: EndpointCatalog.EmployeeBusiness.daily(f.string(from: Date())), method: "PUT",
                                     body: DailySummaryWriteRequest(deepCommunicationCount: deep, todayIssues: issues, tomorrowPlan: plan))
    }
    func requestDeal(customerID: String) async throws -> DealRequest {
        guard let membershipID else { throw YinggeAPIError.unauthenticated }
        let path = EndpointCatalog.replacing(EndpointCatalog.customerDealRequests, parameter: "customerId", with: customerID)
        let scope = "\(serverScope)|\(membershipID)"
        let key = intentKeys.key(scope: scope, operation: "deal", resourceID: customerID)
        do {
            let result: DealRequest = try await client.send(path: path, method: "POST", headers: ["Idempotency-Key": key])
            intentKeys.clear(scope: scope, operation: "deal", resourceID: customerID)
            return result
        } catch let error as YinggeAPIError {
            if case .server = error { intentKeys.clear(scope: scope, operation: "deal", resourceID: customerID) }
            throw error
        }
    }
    func saveNotificationPreferences(_ values: NotificationPreferences) async throws -> NotificationPreferences {
        try await client.send(path: EndpointCatalog.notificationPreferences, method: "PUT", body: values)
    }
    func registerUniPush(registrationID: String, vendor: String?) async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.pushRegistration, method: "POST",
                                                     body: PushRegistrationRequest(registrationId: registrationID, vendor: vendor))
    }
    func unregisterPush() async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.pushRegistration, method: "DELETE")
    }
    func workTasks() async throws -> [WorkTask] { try await client.send(path: EndpointCatalog.EmployeeBusiness.tasks) }
    func claimTask(_ id: String) async throws -> WorkAssignment {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.taskClaim(id), method: "POST")
    }
    func submitTask(_ id: String, summary: String, evidenceURL: String?) async throws -> WorkAssignment {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.taskAssignment(id), method: "PATCH",
                              body: WorkAssignmentUpdate(status: "SUBMITTED", resultSummary: summary, evidenceUrl: evidenceURL))
    }
    func performanceToday() async throws -> PerformanceToday { try await client.send(path: EndpointCatalog.EmployeeBusiness.performance) }
    func createPerformanceClaim(interactionID: String, type: String) async throws -> PerformanceClaim {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.performanceClaims, method: "POST",
                              body: PerformanceClaimCreate(interactionId: interactionID, requestedType: type))
    }
    func submitPerformanceClaim(_ id: String) async throws -> PerformanceClaim {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.performanceClaimSubmit(id), method: "POST")
    }
    func uploadPerformanceEvidence(claimID: String, file: PerformanceEvidenceFile) async throws -> PerformanceEvidence {
        try await client.upload(path: EndpointCatalog.EmployeeBusiness.performanceClaimEvidence(claimID),
                                fileName: file.fileName, contentType: file.contentType, data: file.data)
    }
    func consultations() async throws -> [ConsultationRecord] { try await client.send(path: EndpointCatalog.EmployeeBusiness.consultations) }
    func consultation(_ id: String) async throws -> ConsultationRecord {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.consultation(id))
    }
    func consultationCandidates() async throws -> [ConsultationCandidate] { try await client.send(path: EndpointCatalog.EmployeeBusiness.consultationCandidates) }
    func consultationFailureReasons() async throws -> [ConsultationFailureReason] {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.consultationFailureReasons + "?includeInactive=false")
    }
    func createConsultation(_ body: ConsultationCreate) async throws -> ConsultationRecord {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.consultations, method: "POST", body: body)
    }
    func completeConsultation(_ id: String, body: ConsultationComplete) async throws -> ConsultationRecord {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.consultationComplete(id), method: "POST", body: body)
    }
    func contentOverview() async throws -> ContentOverview { try await client.send(path: EndpointCatalog.EmployeeBusiness.contentTasks) }
    func submitContent(_ body: ContentPostCreate) async throws -> ContentPost {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.contentPosts, method: "POST", body: body)
    }
    func updateContentMetrics(_ id: String, body: ContentMetricsUpdate) async throws -> ContentPost {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.contentMetrics(id), method: "PATCH", body: body)
    }
    func circlePosts() async throws -> PageResponse<ContentPost> {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.contentCircle + "?page=0&size=30&sort=recommended")
    }
    func announcements() async throws -> [Announcement] { try await client.send(path: EndpointCatalog.EmployeeBusiness.announcements) }
    func agentAuthorizations() async throws -> [AgentAuthorization] { try await client.send(path: EndpointCatalog.EmployeeBusiness.agentAuthorizations) }
    func revokeAgentAuthorization(_ id: String) async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.EmployeeBusiness.agentAuthorization(id), method: "DELETE")
    }
    func devices() async throws -> [MobileDevice] { try await client.send(path: EndpointCatalog.mobileDevices) }
    func revokeDevice(_ id: String) async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.EmployeeBusiness.mobileDevice(id), method: "DELETE")
    }
    func assistantAvailability() async throws -> AssistantAvailability { try await client.send(path: EndpointCatalog.EmployeeBusiness.assistantAvailability) }
    func customerPool() async throws -> [CustomerPoolEntry] { try await client.send(path: EndpointCatalog.EmployeeBusiness.customerPool) }
    func claimPoolEntry(_ id: String) async throws -> PoolClaimResult {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.poolClaim(id), method: "POST",
                              body: PoolClaimRequest(idempotencyKey: "ios-pool-\(UUID().uuidString)"))
    }
    func secondLineRecruitments() async throws -> [SecondLineRecruitment] { try await client.send(path: EndpointCatalog.EmployeeBusiness.secondLineRecruitments) }
    func claimSecondLine(_ id: String) async throws -> SecondLineClaimResult {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.secondLineClaim(id), method: "POST")
    }
    func collaboration(customerID: String) async throws -> CustomerCollaboration {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.collaboration(customerID))
    }
    func transfer(customer: Customer, targetID: String, reason: String?) async throws -> Customer {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.transfer(customer.id), method: "POST",
                              body: CustomerTransferRequest(targetMembershipId: targetID, expectedVersion: customer.version, reason: reason))
    }
    func qqExchangeRequest() async throws -> URLRequest {
        let ticket: WebSessionTicket = try await client.send(path: EndpointCatalog.webSessions, method: "POST",
                                                              body: WebSessionTicketRequest(target: .qqWorkbench))
        guard let url = URL(string: ticket.exchangeUrl, relativeTo: baseURL)?.absoluteURL,
              url.scheme?.lowercased() == baseURL.scheme?.lowercased(),
              url.host?.lowercased() == baseURL.host?.lowercased(),
              normalizedPort(url) == normalizedPort(baseURL) else { throw YinggeAPIError.invalidServerURL }
        var request = URLRequest(url: url); request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        let encoded = ticket.ticket.addingPercentEncoding(withAllowedCharacters: allowed) ?? ticket.ticket
        request.httpBody = Data("ticket=\(encoded)".utf8); return request
    }
    func currentAppRelease() async throws -> MobileAppRelease? { try await client.currentRelease() }
    func releaseHistory() async throws -> PageResponse<ProductRelease> {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.releases + "?page=0&size=50")
    }
    func unacknowledgedRelease() async throws -> ProductRelease? {
        try await client.sendOptional(path: EndpointCatalog.EmployeeBusiness.currentUnacknowledgedRelease)
    }
    func acknowledgeRelease(_ id: String) async throws {
        let _: EmptyResponse = try await client.send(path: EndpointCatalog.EmployeeBusiness.acknowledgeRelease(id), method: "POST")
    }
    func notifications(unreadOnly: Bool = false) async throws -> NotificationInbox {
        try await client.send(path: EndpointCatalog.EmployeeBusiness.notifications + "?unreadOnly=\(unreadOnly)&size=100")
    }
    private func configureCache(for actor: Me) {
        let server = baseURL.absoluteString.lowercased()
        membershipID = actor.membershipId
        cache = ProtectedFileCache(namespace: "\(server)|\(actor.membershipId)")
    }

    private func normalizedPort(_ url: URL) -> Int? {
        url.port ?? (url.scheme?.lowercased() == "https" ? 443 : (url.scheme?.lowercased() == "http" ? 80 : nil))
    }
}
