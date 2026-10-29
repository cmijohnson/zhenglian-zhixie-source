import XCTest
@testable import YinggeCore

final class YinggeCoreTests: XCTestCase {
    func testCustomerQualityAndContactPresentation() {
        let customer = Customer(
            id: "customer-1",
            fullName: "顾同学",
            phone: "182****5623",
            intentionLevel: "高",
            tags: [TagSummary(id: "tag-a", name: "A类", category: "客户质量")]
        )
        XCTAssertEqual(customer.qualityCode, "A")
        XCTAssertEqual(customer.primaryContact, "手机 182****5623")
    }

    func testConvertedLegacyStatusIsNotWritableFromEditor() {
        let customer = Customer(id: "customer-1", fullName: "顾同学", intentionLevel: "高", currentStatus: "已转化")
        XCTAssertEqual(CustomerWriteRequest(customer: customer).currentStatus, "跟进中")
    }

    func testCompactContractCustomerDecodesWithoutLegacyFields() throws {
        let json = #"{"id":"c1","fullName":"顾同学","dealState":"PENDING","canRequestDeal":false,"createdAt":"2026-08-22T08:00:00Z","updatedAt":"2026-08-22T08:00:00Z","version":3}"#.data(using: .utf8)!
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        let customer = try decoder.decode(Customer.self, from: json)
        XCTAssertEqual(customer.dealState, .pending)
        XCTAssertFalse(customer.canRequestDeal)
        XCTAssertEqual(customer.currentStatus, "未设置")
    }

    func testEndpointCatalogMatchesIOSNativeContractAndContainsNoSecrets() {
        XCTAssertEqual(EndpointCatalog.contractOperations.count, 58)
        let catalog = EndpointCatalog.contractOperations.joined(separator: "\n").lowercased()
        XCTAssertFalse(catalog.contains("organizationid"))
        XCTAssertFalse(catalog.contains("qce"))
        XCTAssertFalse(catalog.contains("token"))
        XCTAssertFalse(catalog.contains("/apk"))
        XCTAssertFalse(catalog.contains("/admin/"))
        XCTAssertFalse(catalog.contains("web-sessions/exchange"))
    }

    func testCustomerSearchUsesBackendSearchParameter() {
        let path = EndpointCatalog.EmployeeBusiness.customerList(search: " 顾 同学&2026 ")
        let components = URLComponents(string: path)
        let values = Dictionary(uniqueKeysWithValues: (components?.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(values["page"], "0")
        XCTAssertEqual(values["size"], "50")
        XCTAssertEqual(values["search"], "顾 同学&2026")
        XCTAssertNil(values["q"])
        XCTAssertFalse(path.lowercased().contains("organizationid"))
    }

    func testEmployeeBusinessCatalogIsTypedAndContainsNoBypassParameters() {
        XCTAssertGreaterThanOrEqual(EndpointCatalog.EmployeeBusiness.routes.count, 40)
        let catalog = EndpointCatalog.EmployeeBusiness.routes.map { "\($0.method) \($0.path) \($0.request ?? "") \($0.response)" }.joined(separator: "\n").lowercased()
        XCTAssertFalse(catalog.contains("organizationid"))
        XCTAssertFalse(catalog.contains("qce"))
        XCTAssertFalse(catalog.contains("long-termtoken"))
        XCTAssertFalse(EndpointCatalog.EmployeeBusiness.routes.contains { $0.method.isEmpty || $0.path.isEmpty || $0.response.isEmpty })
        XCTAssertEqual(Set(EndpointCatalog.EmployeeBusiness.routes.map { "\($0.method) \($0.path)" }).count,
                       EndpointCatalog.EmployeeBusiness.routes.count)
    }

    func testReachableOperationsCoverPublicAndEmployeeCalls() {
        let operations = EndpointCatalog.reachableOperations
        let operationKeys = Set(operations.map { "\($0.method)|\($0.path)" })
        XCTAssertEqual(operationKeys, EndpointCatalog.contractOperations)
        XCTAssertEqual(operations.count, operationKeys.count)
        XCTAssertTrue(operations.contains { $0.method == "POST" && $0.path == EndpointCatalog.mobileLogin && $0.request == "MobileLoginRequest" })
        XCTAssertTrue(operations.contains { $0.method == "POST" && $0.path == EndpointCatalog.customerDealRequests && $0.request == "Idempotency-Key" })
        XCTAssertTrue(operations.contains { $0.method == "POST" && $0.path.contains("/performance/claims/{claimId}/evidence") })
        XCTAssertTrue(operations.contains { $0.method == "PATCH" && $0.path == "/api/v1/reminders/{reminderId}" })
        XCTAssertTrue(operations.contains { $0.method == "GET" && $0.path == "/api/v1/consultations/{consultationId}" })
        XCTAssertTrue(operations.contains { $0.method == "GET" && $0.path == "/api/v1/assistant/customers/{customerId}/summary" })
        XCTAssertFalse(operations.contains { $0.path.lowercased().contains("organizationid") })
    }

    func testAssistantEventStreamUsesResultAndSurfacesErrors() throws {
        let data = Data("event: meta\ndata: {\"purpose\":\"customer-summary\"}\n\nevent: delta\ndata: \"第一段\"\n\nevent: result\ndata: {\"content\":\"完整摘要\"}\n\nevent: done\ndata: {\"ok\":true}\n\n".utf8)
        XCTAssertEqual(try YinggeAPIClient.parseAssistantEventStream(data).content, "完整摘要")
        let error = Data("event: error\ndata: {\"code\":\"AI_RATE_LIMITED\",\"message\":\"请求过快\"}\n\n".utf8)
        XCTAssertThrowsError(try YinggeAPIClient.parseAssistantEventStream(error)) { value in
            XCTAssertEqual(value.localizedDescription, "请求过快")
        }
    }

    func testReminderCompletionRequestAndConsultationDetailPath() throws {
        let body = ReminderUpdateRequest(status: "COMPLETED")
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(body)) as? [String: Any]
        XCTAssertEqual(object?["status"] as? String, "COMPLETED")
        XCTAssertEqual(EndpointCatalog.EmployeeBusiness.reminder("reminder-1"), "/api/v1/reminders/reminder-1")
        XCTAssertEqual(EndpointCatalog.EmployeeBusiness.consultation("consultation-1"), "/api/v1/consultations/consultation-1")
    }

    func testBusinessIntentKeyPersistsUntilExplicitClearAndIsOriginScoped() {
        let suite = "yingge.intent.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = PersistentIntentKeyStore(defaults: defaults)
        let first = store.key(scope: "https://one.example|member-1", operation: "deal", resourceID: "customer-1")
        XCTAssertEqual(first, store.key(scope: "https://one.example|member-1", operation: "deal", resourceID: "customer-1"))
        XCTAssertNotEqual(first, store.key(scope: "https://two.example|member-1", operation: "deal", resourceID: "customer-1"))
        store.clear(scope: "https://one.example|member-1", operation: "deal", resourceID: "customer-1")
        XCTAssertNotEqual(first, store.key(scope: "https://one.example|member-1", operation: "deal", resourceID: "customer-1"))
    }

    func testPublicRegistrationRequestNeverCarriesOldAuthorization() async throws {
        PublicAuthURLProtocol.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PublicAuthURLProtocol.self]
        let client = YinggeAPIClient(baseURL: URL(string: "https://new.example")!, appVersion: "1", versionCode: 1,
                                     deviceID: UUID(), session: URLSession(configuration: configuration))
        await client.configureTokens(accessToken: "token-from-old-session", refreshToken: "old-refresh")
        let _: RegistrationOptions = try await client.sendUnauthenticated(
            path: EndpointCatalog.registrationOptions, method: "POST", body: RegistrationOptionsRequest(registrationCode: "INVITE")
        )
        XCTAssertFalse(PublicAuthURLProtocol.sawAuthorization)
    }

    func testQQWorkbenchRoutePolicyIsExactAndFailClosed() {
        let origin = URL(string: "https://crm.example.com:8443")!
        XCTAssertTrue(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://crm.example.com:8443/api/v1/mobile/web-sessions/exchange")!, origin: origin))
        XCTAssertTrue(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://crm.example.com:8443/qq-workbench/chat")!, origin: origin))
        XCTAssertTrue(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://crm.example.com:8443/static/qce/_next/chunk.js")!, origin: origin))
        XCTAssertTrue(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://crm.example.com:8443/api/v1/qq-workbench/messages")!, origin: origin))
        XCTAssertFalse(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://crm.example.com:8443/api/v1/customers")!, origin: origin))
        XCTAssertFalse(QQWorkbenchRoutePolicy.isAllowed(URL(string: "https://evil.example.com:8443/qq-workbench")!, origin: origin))
        XCTAssertFalse(QQWorkbenchRoutePolicy.isAllowed(URL(string: "http://crm.example.com:8443/qq-workbench")!, origin: origin))
    }

    func testPerformanceEvidenceValidationAndMultipartEncoding() throws {
        let jpeg = Data([0xff, 0xd8, 0xff, 0xe0, 0x01])
        let file = try PerformanceEvidenceValidator.validate(data: jpeg, suggestedName: "证明 截图")
        XCTAssertEqual(file.contentType, "image/jpeg")
        XCTAssertTrue(file.fileName.hasSuffix(".jpg"))
        let body = YinggeAPIClient.multipartBody(boundary: "fixed", fieldName: "file", fileName: file.fileName,
                                                 contentType: file.contentType, data: file.data)
        let rendered = String(decoding: body, as: UTF8.self)
        XCTAssertTrue(rendered.contains("name=\"file\""))
        XCTAssertTrue(rendered.contains("Content-Type: image/jpeg"))
        XCTAssertNotNil(body.range(of: jpeg))
        XCTAssertThrowsError(try PerformanceEvidenceValidator.validate(data: Data([1, 2, 3])))
        XCTAssertThrowsError(try PerformanceEvidenceValidator.validate(data: Data(repeating: 0xff, count: PerformanceEvidenceValidator.maximumBytes + 1)))
    }

    func testProductReleaseDecodesAsTypedHistory() throws {
        let json = #"{"id":"r1","version":"2026.08.22.1","title":"移动端更新","summary":"完善原生业务能力","sections":{"新增":["更新日志"]},"releasedAt":"2026-08-22T08:00:00Z","current":true,"acknowledged":false}"#.data(using: .utf8)!
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        let release = try decoder.decode(ProductRelease.self, from: json)
        XCTAssertEqual(release.sections["新增"], ["更新日志"])
        XCTAssertTrue(release.current)
        XCTAssertFalse(release.acknowledged)
    }

    func testConsultationNonContinuationCarriesServerFailureReason() throws {
        let request = ConsultationComplete(expectedVersion: 7, resultSummary: "暂不推进", continueFollowUp: false,
                                           nextStep: "归档复盘", nextOwnerMembershipId: "member-1", nextFollowUpAt: nil,
                                           failureReasonId: "reason-1", failureReasonOther: nil)
        let value = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as? [String: Any]
        XCTAssertEqual(value?["continueFollowUp"] as? Bool, false)
        XCTAssertEqual(value?["failureReasonId"] as? String, "reason-1")
        XCTAssertNil(value?["nextFollowUpAt"])
    }

    func testMobileAppReleaseDecodesCurrentUpdatePolicy() throws {
        let json = #"{"id":"ios-1","platform":"IOS","versionCode":12,"versionName":"2026.08.22","minimumSupportedVersionCode":10,"mandatory":false,"notes":"修复和改进"}"#.data(using: .utf8)!
        let release = try JSONDecoder().decode(MobileAppRelease.self, from: json)
        XCTAssertEqual(release.platform, .ios)
        XCTAssertEqual(release.versionCode, 12)
        XCTAssertFalse(release.mandatory)
    }

    func testPushRegistrationNeverDisguisesAPNSAsUniPush() throws {
        let request = PushRegistrationRequest(registrationId: "real-unipush-registration-id", vendor: "APPLE")
        let value = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as? [String: Any]
        XCTAssertEqual(value?["provider"] as? String, "UNIPUSH")
        XCTAssertEqual(value?["registrationId"] as? String, "real-unipush-registration-id")
    }

    func testEveryMobileRequestHeaderIncludesVersionCodeAndDeviceIdentity() {
        let id = UUID(uuidString: "ABDA2B91-191D-4E55-9F77-6148903EE38A")!
        let headers = YinggeAPIClient.requiredHeaders(appVersion: "2026.8.22", versionCode: 108, deviceID: id)
        XCTAssertEqual(headers["X-Yingge-App-Version"], "2026.8.22")
        XCTAssertEqual(headers["X-Yingge-Version-Code"], "108")
        XCTAssertEqual(headers["X-Yingge-Platform"], "IOS")
        XCTAssertEqual(headers["X-Yingge-Device-Id"], id.uuidString.lowercased())
    }

    func testServerURLIsStrictOriginOnly() {
        XCTAssertNotNil(YinggeAPIClient.validatedBaseURL(URL(string: "https://crm.example.com:8443/")!))
        XCTAssertNil(YinggeAPIClient.validatedBaseURL(URL(string: "https://user:pass@crm.example.com")!))
        XCTAssertNil(YinggeAPIClient.validatedBaseURL(URL(string: "https://crm.example.com/api")!))
        XCTAssertNil(YinggeAPIClient.validatedBaseURL(URL(string: "https://crm.example.com?next=evil")!))
    }

    func testEncryptedSQLiteCacheSeparatesNamespacesAndHidesPlaintext() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = MemorySecureStore()
        let first = ProtectedFileCache(namespace: "server-a|member-a", root: root, secureStore: store)
        let second = ProtectedFileCache(namespace: "server-a|member-b", root: root, secureStore: store)
        let secret = CacheFixture(message: "sensitive-customer-note-48291")
        try await first.write(secret, key: "customer")
        let firstValue = try await first.read(CacheFixture.self, key: "customer")
        let secondValue = try await second.read(CacheFixture.self, key: "customer")
        XCTAssertEqual(firstValue, secret)
        XCTAssertNil(secondValue)
        let bytes = try Data(contentsOf: root.appendingPathComponent("YinggeCloud/encrypted-cache.sqlite3"))
        XCTAssertFalse(String(decoding: bytes, as: UTF8.self).contains(secret.message))
    }

    func testConcurrentUnauthorizedRequestsShareOneRefreshRotation() async throws {
        RefreshURLProtocol.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RefreshURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let client = YinggeAPIClient(baseURL: URL(string: "https://crm.example.com")!, appVersion: "1", versionCode: 1,
                                     deviceID: UUID(), session: session)
        await client.configureTokens(accessToken: "expired", refreshToken: "refresh-one")
        async let first: ProbeResponse = client.send(path: "/probe")
        async let second: ProbeResponse = client.send(path: "/probe")
        let values = try await [first, second]
        XCTAssertEqual(values.map(\.value), ["ok", "ok"])
        XCTAssertEqual(RefreshURLProtocol.refreshCount, 1)
    }
}

private struct CacheFixture: Codable, Equatable, Sendable { let message: String }
private struct ProbeResponse: Codable, Sendable { let value: String }

private final class MemorySecureStore: SecureKeyValueStoring, @unchecked Sendable {
    private var values: [String: Data] = [:]; private let lock = NSLock()
    func data(for key: String) throws -> Data? { lock.lock(); defer { lock.unlock() }; return values[key] }
    func set(_ data: Data, for key: String) throws { lock.lock(); defer { lock.unlock() }; values[key] = data }
    func remove(_ key: String) throws { lock.lock(); defer { lock.unlock() }; values.removeValue(forKey: key) }
}

private final class RefreshURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock(); private static var rotations = 0
    static var refreshCount: Int { lock.lock(); defer { lock.unlock() }; return rotations }
    static func reset() { lock.lock(); rotations = 0; lock.unlock() }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url else { return }
        let data: Data; let status: Int
        if url.path.contains("/mobile/auth/refresh") {
            Self.lock.lock(); Self.rotations += 1; Self.lock.unlock()
            Thread.sleep(forTimeInterval: 0.12)
            status = 200
            data = #"{"accessToken":"fresh","accessExpiresAt":"2026-08-22T09:00:00Z","refreshToken":"refresh-two","refreshExpiresAt":"2026-09-22T09:00:00Z","actor":{"userId":"u","membershipId":"m","organizationId":"o","username":"user","displayName":"User","roles":["MEMBER"],"passwordChangeRequired":false}}"#.data(using: .utf8)!
        } else if request.value(forHTTPHeaderField: "Authorization") == "Bearer fresh" {
            status = 200; data = #"{"value":"ok"}"#.data(using: .utf8)!
        } else { status = 401; data = #"{"code":"UNAUTHENTICATED","message":"expired"}"#.data(using: .utf8)! }
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

private final class PublicAuthURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock(); private static var capturedAuthorization = false
    static var sawAuthorization: Bool { lock.lock(); defer { lock.unlock() }; return capturedAuthorization }
    static func reset() { lock.lock(); capturedAuthorization = false; lock.unlock() }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); Self.capturedAuthorization = request.value(forHTTPHeaderField: "Authorization") != nil; Self.lock.unlock()
        let data = #"{"teams":[]}"#.data(using: .utf8)!
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
