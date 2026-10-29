import Foundation

/// Auditable allow-list kept in sync with `contracts/mobile-api.yaml`.
public enum EndpointCatalog {
    public static let registrationOptions = "/api/v1/mobile/auth/registration-options"
    public static let mobileRegister = "/api/v1/mobile/auth/register"
    public static let mobileLogin = "/api/v1/mobile/auth/login"
    public static let mobileRefresh = "/api/v1/mobile/auth/refresh"
    public static let mobileLogout = "/api/v1/mobile/auth/logout"
    public static let changePassword = "/api/v1/auth/password"
    public static let mobileDevices = "/api/v1/mobile/auth/devices"
    public static let mobileDevice = "/api/v1/mobile/auth/devices/{deviceId}"
    public static let mobileBootstrap = "/api/v1/mobile/bootstrap"
    public static let pushRegistration = "/api/v1/mobile/devices/push-registration"
    public static let notificationPreferences = "/api/v1/mobile/devices/notification-preferences"
    public static let webSessions = "/api/v1/mobile/web-sessions"
    public static let webSessionExchange = "/api/v1/mobile/web-sessions/exchange"
    public static let currentRelease = "/api/v1/mobile/app-releases/current"
    public static let customer = "/api/v1/customers/{customerId}"
    public static let customerDealRequests = "/api/v1/customers/{customerId}/deal-requests"

    /// The single machine-readable, fail-closed native iOS surface.
    /// This set must exactly equal the IOS entries in `contracts/mobile-api.yaml`.
    /// WebView ticket exchange is intentionally excluded because it is consumed by WKWebView as a form submission.
    public static let contractOperations: Set<String> = [
        "POST|/api/v1/mobile/auth/registration-options",
        "POST|/api/v1/mobile/auth/register",
        "POST|/api/v1/mobile/auth/login",
        "POST|/api/v1/mobile/auth/refresh",
        "POST|/api/v1/mobile/auth/logout",
        "POST|/api/v1/auth/password",
        "GET|/api/v1/mobile/auth/devices",
        "DELETE|/api/v1/mobile/auth/devices/{deviceId}",
        "GET|/api/v1/mobile/bootstrap",
        "POST|/api/v1/mobile/devices/push-registration",
        "DELETE|/api/v1/mobile/devices/push-registration",
        "PUT|/api/v1/mobile/devices/notification-preferences",
        "POST|/api/v1/mobile/web-sessions",
        "GET|/api/v1/mobile/app-releases/current",
        "GET|/api/v1/dashboard",
        "GET|/api/v1/customers",
        "POST|/api/v1/customers",
        "GET|/api/v1/customers/{customerId}",
        "PATCH|/api/v1/customers/{customerId}",
        "GET|/api/v1/customers/{customerId}/collaboration",
        "POST|/api/v1/customers/{customerId}/transfer",
        "GET|/api/v1/customers/{customerId}/interactions",
        "POST|/api/v1/customers/{customerId}/interactions",
        "POST|/api/v1/customers/{customerId}/deal-requests",
        "GET|/api/v1/reminders",
        "PATCH|/api/v1/reminders/{reminderId}",
        "GET|/api/v1/daily-reports/today",
        "PUT|/api/v1/daily-reports/{date}",
        "GET|/api/v1/notifications",
        "GET|/api/v1/tasks",
        "POST|/api/v1/tasks/{taskId}/claim",
        "PATCH|/api/v1/tasks/{taskId}/my-assignment",
        "GET|/api/v1/performance/today",
        "POST|/api/v1/performance/claims",
        "POST|/api/v1/performance/claims/{claimId}/evidence",
        "POST|/api/v1/performance/claims/{claimId}/submit",
        "GET|/api/v1/consultations",
        "POST|/api/v1/consultations",
        "GET|/api/v1/consultations/{consultationId}",
        "GET|/api/v1/consultations/candidates",
        "POST|/api/v1/consultations/{consultationId}/complete",
        "GET|/api/v1/consultation-failure-reasons",
        "GET|/api/v1/content-tasks/overview",
        "POST|/api/v1/content-posts",
        "PATCH|/api/v1/content-posts/{postId}/metrics",
        "GET|/api/v1/circle/posts",
        "GET|/api/v1/announcements",
        "GET|/api/v1/assistant/availability",
        "GET|/api/v1/assistant/customers/{customerId}/summary",
        "GET|/api/v1/releases",
        "GET|/api/v1/releases/current/unacknowledged",
        "POST|/api/v1/releases/{releaseId}/acknowledge",
        "GET|/api/v1/agent/authorizations",
        "DELETE|/api/v1/agent/authorizations/{authorizationId}",
        "GET|/api/v1/customer-pools",
        "POST|/api/v1/customer-pools/{entryId}/claims",
        "GET|/api/v1/second-line-recruitments",
        "POST|/api/v1/second-line-recruitments/{recruitmentId}/claim"
    ]

    /// Every network operation reachable from the production iOS navigation.
    /// Each entry declares HTTP method, path template, request and response type
    /// so contract checks can compare both directions without a generic proxy.
    public static let reachableOperations: [EmployeeBusiness.Route] = [
        .init("POST", registrationOptions, request: "RegistrationOptionsRequest", response: "RegistrationOptions"),
        .init("POST", mobileRegister, request: "MobileRegisterRequest", response: "RegistrationResult"),
        .init("POST", mobileLogin, request: "MobileLoginRequest", response: "MobileTokenResponse"),
        .init("POST", mobileRefresh, request: "MobileRefreshRequest", response: "MobileTokenResponse"),
        .init("POST", mobileLogout, response: "Void"),
        .init("POST", changePassword, request: "PasswordChangeRequest", response: "Void"),
        .init("GET", mobileDevices, response: "[MobileDevice]"),
        .init("DELETE", mobileDevice, response: "Void"),
        .init("GET", mobileBootstrap, response: "MobileBootstrap"),
        .init("POST", pushRegistration, request: "PushRegistrationRequest(UNIPUSH)", response: "Void"),
        .init("DELETE", pushRegistration, response: "Void"),
        .init("PUT", notificationPreferences, request: "NotificationPreferences", response: "NotificationPreferences"),
        .init("POST", webSessions, request: "WebSessionTicketRequest", response: "WebSessionTicket"),
        .init("GET", currentRelease, response: "MobileAppRelease?"),
        .init("POST", customerDealRequests, request: "Idempotency-Key", response: "DealRequest")
    ] + EmployeeBusiness.routes

    public enum EmployeeBusiness {
        public struct Route: Hashable, Sendable {
            public let method: String; public let path: String; public let request: String?; public let response: String
            public init(_ method: String, _ path: String, request: String? = nil, response: String) {
                self.method = method; self.path = path; self.request = request; self.response = response
            }
        }
        public static let dashboard = "/api/v1/dashboard"
        public static let customers = "/api/v1/customers"
        public static let reminders = "/api/v1/reminders"
        public static let dailyToday = "/api/v1/daily-reports/today"
        public static let customerPool = "/api/v1/customer-pools"
        public static let secondLineRecruitments = "/api/v1/second-line-recruitments"
        public static let tasks = "/api/v1/tasks"
        public static let performance = "/api/v1/performance/today"
        public static let consultations = "/api/v1/consultations"
        public static let contentTasks = "/api/v1/content-tasks/overview"
        public static let contentCircle = "/api/v1/circle/posts"
        public static let announcements = "/api/v1/announcements"
        public static let assistantAvailability = "/api/v1/assistant/availability"
        public static let releases = "/api/v1/releases"
        public static let notifications = "/api/v1/notifications"
        public static let performanceClaims = "/api/v1/performance/claims"
        public static let consultationFailureReasons = "/api/v1/consultation-failure-reasons"
        public static let currentUnacknowledgedRelease = "/api/v1/releases/current/unacknowledged"
        public static let consultationCandidates = "/api/v1/consultations/candidates"
        public static let contentPosts = "/api/v1/content-posts"
        public static let agentAuthorizations = "/api/v1/agent/authorizations"
        public static func customer(_ id: String) -> String { "/api/v1/customers/\(id)" }
        public static func customerList(search: String?) -> String {
            var components = URLComponents()
            components.path = customers
            var items = [URLQueryItem(name: "page", value: "0"), URLQueryItem(name: "size", value: "50")]
            if let search = search?.trimmingCharacters(in: .whitespacesAndNewlines), !search.isEmpty {
                items.append(URLQueryItem(name: "search", value: search))
            }
            components.queryItems = items
            return components.string ?? "\(customers)?page=0&size=50"
        }
        public static func interactions(_ customerID: String) -> String { "/api/v1/customers/\(customerID)/interactions" }
        public static func assistantSummary(_ customerID: String) -> String { "/api/v1/assistant/customers/\(customerID)/summary" }
        public static func reminder(_ id: String) -> String { "/api/v1/reminders/\(id)" }
        public static func daily(_ date: String) -> String { "/api/v1/daily-reports/\(date)" }
        public static func taskClaim(_ id: String) -> String { "/api/v1/tasks/\(id)/claim" }
        public static func taskAssignment(_ id: String) -> String { "/api/v1/tasks/\(id)/my-assignment" }
        public static func performanceClaimSubmit(_ id: String) -> String { "/api/v1/performance/claims/\(id)/submit" }
        public static func performanceClaimEvidence(_ id: String) -> String { "/api/v1/performance/claims/\(id)/evidence" }
        public static func consultationComplete(_ id: String) -> String { "/api/v1/consultations/\(id)/complete" }
        public static func consultation(_ id: String) -> String { "/api/v1/consultations/\(id)" }
        public static func contentMetrics(_ id: String) -> String { "/api/v1/content-posts/\(id)/metrics" }
        public static func agentAuthorization(_ id: String) -> String { "/api/v1/agent/authorizations/\(id)" }
        public static func mobileDevice(_ id: String) -> String { "/api/v1/mobile/auth/devices/\(id)" }
        public static func acknowledgeRelease(_ id: String) -> String { "/api/v1/releases/\(id)/acknowledge" }
        public static func poolClaim(_ id: String) -> String { "/api/v1/customer-pools/\(id)/claims" }
        public static func secondLineClaim(_ id: String) -> String { "/api/v1/second-line-recruitments/\(id)/claim" }
        public static func collaboration(_ customerID: String) -> String { "/api/v1/customers/\(customerID)/collaboration" }
        public static func transfer(_ customerID: String) -> String { "/api/v1/customers/\(customerID)/transfer" }

        /// Strongly typed employee route inventory. CI can scan this list instead of relying on a generic JSON proxy.
        public static let routes: [Route] = [
            .init("GET", dashboard, response: "Dashboard"),
            .init("GET", customers, response: "PageResponse<Customer>"),
            .init("POST", customers, request: "CustomerWriteRequest", response: "Customer"),
            .init("GET", "/api/v1/customers/{customerId}", response: "Customer"),
            .init("PATCH", "/api/v1/customers/{customerId}", request: "CustomerWriteRequest", response: "Customer"),
            .init("GET", "/api/v1/customers/{customerId}/interactions", response: "[Interaction]"),
            .init("POST", "/api/v1/customers/{customerId}/interactions", request: "InteractionCreateRequest", response: "Interaction"),
            .init("GET", reminders, response: "[Reminder]"),
            .init("PATCH", "/api/v1/reminders/{reminderId}", request: "ReminderUpdateRequest", response: "Reminder"),
            .init("GET", dailyToday, response: "DailySummary"),
            .init("PUT", "/api/v1/daily-reports/{date}", request: "DailySummaryWriteRequest", response: "DailySummary"),
            .init("GET", customerPool, response: "[CustomerPoolEntry]"),
            .init("POST", "/api/v1/customer-pools/{entryId}/claims", request: "PoolClaimRequest", response: "PoolClaimResult"),
            .init("GET", secondLineRecruitments, response: "[SecondLineRecruitment]"),
            .init("POST", "/api/v1/second-line-recruitments/{recruitmentId}/claim", response: "SecondLineClaimResult"),
            .init("GET", "/api/v1/customers/{customerId}/collaboration", response: "CustomerCollaboration"),
            .init("POST", "/api/v1/customers/{customerId}/transfer", request: "CustomerTransferRequest", response: "Customer"),
            .init("GET", tasks, response: "[WorkTask]"),
            .init("POST", "/api/v1/tasks/{taskId}/claim", response: "WorkAssignment"),
            .init("PATCH", "/api/v1/tasks/{taskId}/my-assignment", request: "WorkAssignmentUpdate", response: "WorkAssignment"),
            .init("GET", performance, response: "PerformanceToday"),
            .init("POST", performanceClaims, request: "PerformanceClaimCreate", response: "PerformanceClaim"),
            .init("POST", "/api/v1/performance/claims/{claimId}/evidence", request: "multipart PerformanceEvidenceFile", response: "PerformanceEvidence"),
            .init("POST", "/api/v1/performance/claims/{claimId}/submit", response: "PerformanceClaim"),
            .init("GET", consultations, response: "[ConsultationRecord]"),
            .init("POST", consultations, request: "ConsultationCreate", response: "ConsultationRecord"),
            .init("GET", "/api/v1/consultations/{consultationId}", response: "ConsultationRecord"),
            .init("GET", consultationCandidates, response: "[ConsultationCandidate]"),
            .init("GET", consultationFailureReasons, response: "[ConsultationFailureReason]"),
            .init("POST", "/api/v1/consultations/{consultationId}/complete", request: "ConsultationComplete", response: "ConsultationRecord"),
            .init("GET", contentTasks, response: "ContentOverview"),
            .init("POST", contentPosts, request: "ContentPostCreate", response: "ContentPost"),
            .init("PATCH", "/api/v1/content-posts/{postId}/metrics", request: "ContentMetricsUpdate", response: "ContentPost"),
            .init("GET", contentCircle, response: "PageResponse<ContentPost>"),
            .init("GET", announcements, response: "[Announcement]"),
            .init("GET", assistantAvailability, response: "AssistantAvailability"),
            .init("GET", "/api/v1/assistant/customers/{customerId}/summary", response: "text/event-stream AssistantCustomerSummary"),
            .init("GET", releases, response: "PageResponse<ProductRelease>"),
            .init("GET", currentUnacknowledgedRelease, response: "ProductRelease?"),
            .init("POST", "/api/v1/releases/{releaseId}/acknowledge", response: "Void"),
            .init("GET", notifications, response: "NotificationInbox"),
            .init("GET", agentAuthorizations, response: "[AgentAuthorization]"),
            .init("DELETE", "/api/v1/agent/authorizations/{authorizationId}", response: "Void"),
        ]
    }

    public static func replacing(_ template: String, parameter: String, with value: String) -> String {
        template.replacingOccurrences(of: "{\(parameter)}", with: value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value)
    }
}

public enum QQWorkbenchRoutePolicy {
    public static func isAllowed(_ url: URL?, origin: URL?) -> Bool {
        guard let url, let origin else { return false }
        let scheme = url.scheme?.lowercased(), allowedScheme = origin.scheme?.lowercased()
        let port = url.port ?? (scheme == "https" ? 443 : 80)
        let allowedPort = origin.port ?? (allowedScheme == "https" ? 443 : 80)
        guard scheme == allowedScheme, url.host?.lowercased() == origin.host?.lowercased(), port == allowedPort else { return false }
        let path = url.path
        return path == EndpointCatalog.webSessionExchange
            || path == "/qq-workbench" || path.hasPrefix("/qq-workbench/")
            || path == "/static/qce" || path.hasPrefix("/static/qce/")
            || path == "/api/v1/qq-workbench" || path.hasPrefix("/api/v1/qq-workbench/")
    }

    public static func contentRuleAllowPattern(origin: URL) -> String? {
        guard let scheme = origin.scheme?.lowercased(), let host = origin.host?.lowercased() else { return nil }
        let originText = origin.port.map { "\(scheme)://\(host):\($0)" } ?? "\(scheme)://\(host)"
        let escaped = NSRegularExpression.escapedPattern(for: originText)
        return "^\(escaped)/(api/v1/mobile/web-sessions/exchange|qq-workbench(?:/.*)?|static/qce(?:/.*)?|api/v1/qq-workbench(?:/.*)?)(?:\\?.*)?$"
    }
}
