import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum YinggeAPIError: LocalizedError, Sendable {
    case invalidServerURL, invalidResponse, unauthenticated
    case server(status: Int, envelope: APIErrorEnvelope)
    case decoding(String)
    public var errorDescription: String? {
        switch self {
        case .invalidServerURL: return "服务器地址无效"
        case .invalidResponse: return "服务器返回了无法识别的响应"
        case .unauthenticated: return "登录已失效，请重新登录"
        case let .server(_, e): return e.traceId.map { "\(e.message)（\($0)）" } ?? e.message
        case let .decoding(message): return "数据解析失败：\(message)"
        }
    }
}

public actor YinggeAPIClient {
    public private(set) var baseURL: URL
    public let appVersion: String
    public let versionCode: Int64
    public let deviceID: UUID
    private let session: URLSession
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let onTokenRotation: (@Sendable (MobileTokenResponse) -> Void)?
    private var accessToken: String?
    private var refreshToken: String?
    private var refreshTask: Task<MobileTokenResponse, Error>?

    public init(baseURL: URL, appVersion: String, versionCode: Int64, deviceID: UUID, session: URLSession? = nil,
                onTokenRotation: (@Sendable (MobileTokenResponse) -> Void)? = nil) {
        let canonicalBaseURL = Self.validatedBaseURL(baseURL) ?? baseURL
        self.baseURL = canonicalBaseURL
        self.appVersion = appVersion; self.versionCode = versionCode; self.deviceID = deviceID
        let configuration = URLSessionConfiguration.default
        configuration.httpShouldSetCookies = false; configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        self.session = session ?? URLSession(configuration: configuration, delegate: SameOriginRedirectDelegate(origin: canonicalBaseURL), delegateQueue: nil)
        self.onTokenRotation = onTokenRotation
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601; self.encoder = encoder
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let c = try decoder.singleValueContainer(); let value = try c.decode(String.self)
            if let date = Self.fractionalDateFormatter.date(from: value) ?? Self.dateFormatter.date(from: value) { return date }
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "Invalid ISO-8601 date: \(value)")
        }
        self.decoder = decoder
    }

    public func configureTokens(accessToken: String?, refreshToken: String?) {
        self.accessToken = accessToken; self.refreshToken = refreshToken
    }
    public func clearAccessToken() { accessToken = nil }

    public func login(username: String, password: String, deviceName: String) async throws -> MobileTokenResponse {
        let token: MobileTokenResponse = try await execute(
            path: EndpointCatalog.mobileLogin, method: "POST",
            body: try encoder.encode(MobileLoginRequest(username: username, password: password, deviceName: deviceName)),
            authenticated: false, retryAfterRefresh: false, headers: [:]
        )
        configureTokens(accessToken: token.accessToken, refreshToken: token.refreshToken)
        onTokenRotation?(token)
        return token
    }

    public func refresh() async throws -> MobileTokenResponse {
        if let refreshTask { return try await refreshTask.value }
        guard let refreshToken else { throw YinggeAPIError.unauthenticated }
        let task = Task { try await self.performRefresh(using: refreshToken) }
        refreshTask = task
        do {
            let token = try await task.value
            refreshTask = nil
            return token
        } catch {
            refreshTask = nil; accessToken = nil
            throw error
        }
    }

    private func performRefresh(using token: String) async throws -> MobileTokenResponse {
        let rotated: MobileTokenResponse = try await execute(path: EndpointCatalog.mobileRefresh, method: "POST",
                                                              body: try encoder.encode(MobileRefreshRequest(refreshToken: token)),
                                                              authenticated: false, retryAfterRefresh: false, headers: [:])
        configureTokens(accessToken: rotated.accessToken, refreshToken: rotated.refreshToken)
        onTokenRotation?(rotated)
        return rotated
    }

    public func logout() async throws {
        let _: EmptyResponse = try await send(path: EndpointCatalog.mobileLogout, method: "POST")
        configureTokens(accessToken: nil, refreshToken: nil)
    }

    public func send<Response: Decodable>(path: String, method: String = "GET", headers: [String: String] = [:]) async throws -> Response {
        try await execute(path: path, method: method, body: nil, authenticated: true, retryAfterRefresh: true, headers: headers)
    }
    public func send<Body: Encodable, Response: Decodable>(path: String, method: String, body: Body,
                                                            headers: [String: String] = [:]) async throws -> Response {
        try await execute(path: path, method: method, body: try encoder.encode(body), authenticated: true,
                          retryAfterRefresh: true, headers: headers)
    }
    public func sendUnauthenticated<Body: Encodable, Response: Decodable>(path: String, method: String, body: Body) async throws -> Response {
        try await execute(path: path, method: method, body: try encoder.encode(body), authenticated: false,
                          retryAfterRefresh: false, headers: [:])
    }
    public func sendOptional<Response: Decodable>(path: String, method: String = "GET") async throws -> Response? {
        try await executeOptional(path: path, method: method, body: nil, authenticated: true,
                                  retryAfterRefresh: true, headers: [:])
    }
    public func upload<Response: Decodable>(path: String, fieldName: String = "file", fileName: String,
                                             contentType: String, data: Data) async throws -> Response {
        let boundary = "YinggeBoundary-\(UUID().uuidString)"
        let body = Self.multipartBody(boundary: boundary, fieldName: fieldName, fileName: fileName,
                                      contentType: contentType, data: data)
        return try await execute(path: path, method: "POST", body: body, authenticated: true,
                                 retryAfterRefresh: true,
                                 headers: ["Content-Type": "multipart/form-data; boundary=\(boundary)"])
    }
    public func currentRelease() async throws -> MobileAppRelease? {
        try await executeOptional(path: EndpointCatalog.currentRelease, method: "GET", body: nil,
                                  authenticated: true, retryAfterRefresh: true, headers: [:])
    }

    public func sendEventStream(path: String) async throws -> AssistantCustomerSummary {
        try await executeEventStream(path: path, retryAfterRefresh: true)
    }

    public nonisolated static func requiredHeaders(appVersion: String, versionCode: Int64, deviceID: UUID) -> [String: String] {
        [
            "X-Yingge-App-Version": appVersion,
            "X-Yingge-Version-Code": String(versionCode),
            "X-Yingge-Platform": "IOS",
            "X-Yingge-Device-Id": deviceID.uuidString.lowercased()
        ]
    }

    public nonisolated static func multipartBody(boundary: String, fieldName: String, fileName: String,
                                                  contentType: String, data: Data) -> Data {
        let safeField = fieldName.replacingOccurrences(of: "\"", with: "")
        let safeName = fileName.replacingOccurrences(of: "\"", with: "_")
            .replacingOccurrences(of: "\r", with: "_").replacingOccurrences(of: "\n", with: "_")
        var result = Data()
        result.append(Data("--\(boundary)\r\n".utf8))
        result.append(Data("Content-Disposition: form-data; name=\"\(safeField)\"; filename=\"\(safeName)\"\r\n".utf8))
        result.append(Data("Content-Type: \(contentType)\r\n\r\n".utf8)); result.append(data)
        result.append(Data("\r\n--\(boundary)--\r\n".utf8)); return result
    }

    public nonisolated static func validatedBaseURL(_ value: URL) -> URL? {
        guard let components = URLComponents(url: value, resolvingAgainstBaseURL: false),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              components.host != nil, components.user == nil, components.password == nil,
              components.query == nil, components.fragment == nil,
              components.path.isEmpty || components.path == "/" else { return nil }
        var canonical = components; canonical.scheme = scheme; canonical.path = ""; return canonical.url
    }

    private func execute<Response: Decodable>(path: String, method: String, body: Data?, authenticated: Bool,
                                               retryAfterRefresh: Bool, headers: [String: String]) async throws -> Response {
        if let value: Response = try await executeOptional(path: path, method: method, body: body,
                                                            authenticated: authenticated,
                                                            retryAfterRefresh: retryAfterRefresh, headers: headers) { return value }
        if Response.self == EmptyResponse.self { return EmptyResponse() as! Response }
        throw YinggeAPIError.invalidResponse
    }

    private func executeOptional<Response: Decodable>(path: String, method: String, body: Data?, authenticated: Bool,
                                                       retryAfterRefresh: Bool, headers: [String: String]) async throws -> Response? {
        guard let url = URL(string: path, relativeTo: baseURL)?.absoluteURL else { throw YinggeAPIError.invalidServerURL }
        guard Self.sameOrigin(url, baseURL) else { throw YinggeAPIError.invalidServerURL }
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (name, value) in Self.requiredHeaders(appVersion: appVersion, versionCode: versionCode, deviceID: deviceID) {
            request.setValue(value, forHTTPHeaderField: name)
        }
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if authenticated, let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let (data, rawResponse) = try await session.data(for: request)
        guard let response = rawResponse as? HTTPURLResponse else { throw YinggeAPIError.invalidResponse }
        if response.statusCode == 401, authenticated, retryAfterRefresh, refreshToken != nil {
            _ = try await refresh()
            return try await executeOptional(path: path, method: method, body: body, authenticated: true,
                                             retryAfterRefresh: false, headers: headers)
        }
        if response.statusCode == 401 { throw YinggeAPIError.unauthenticated }
        guard (200..<300).contains(response.statusCode) else {
            let envelope = (try? decoder.decode(APIErrorEnvelope.self, from: data))
                ?? APIErrorEnvelope(code: "HTTP_\(response.statusCode)", message: String(data: data, encoding: .utf8) ?? "请求失败")
            throw YinggeAPIError.server(status: response.statusCode, envelope: envelope)
        }
        if response.statusCode == 204 || data.isEmpty { return nil }
        do { return try decoder.decode(Response.self, from: data) }
        catch { throw YinggeAPIError.decoding(error.localizedDescription) }
    }

    private func executeEventStream(path: String, retryAfterRefresh: Bool) async throws -> AssistantCustomerSummary {
        guard let url = URL(string: path, relativeTo: baseURL)?.absoluteURL,
              Self.sameOrigin(url, baseURL) else { throw YinggeAPIError.invalidServerURL }
        var request = URLRequest(url: url); request.httpMethod = "GET"
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        for (name, value) in Self.requiredHeaders(appVersion: appVersion, versionCode: versionCode, deviceID: deviceID) {
            request.setValue(value, forHTTPHeaderField: name)
        }
        if let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }
        let (data, rawResponse) = try await session.data(for: request)
        guard let response = rawResponse as? HTTPURLResponse else { throw YinggeAPIError.invalidResponse }
        if response.statusCode == 401, retryAfterRefresh, refreshToken != nil {
            _ = try await refresh()
            return try await executeEventStream(path: path, retryAfterRefresh: false)
        }
        if response.statusCode == 401 { throw YinggeAPIError.unauthenticated }
        guard (200..<300).contains(response.statusCode) else {
            let envelope = (try? decoder.decode(APIErrorEnvelope.self, from: data))
                ?? APIErrorEnvelope(code: "HTTP_\(response.statusCode)", message: String(data: data, encoding: .utf8) ?? "请求失败")
            throw YinggeAPIError.server(status: response.statusCode, envelope: envelope)
        }
        return try Self.parseAssistantEventStream(data)
    }

    public nonisolated static func parseAssistantEventStream(_ data: Data) throws -> AssistantCustomerSummary {
        guard let raw = String(data: data, encoding: .utf8) else { throw YinggeAPIError.invalidResponse }
        var deltas: [String] = []
        var result: String?
        for block in raw.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n\n") {
            var event = "message"
            var payloadLines: [String] = []
            for line in block.split(separator: "\n", omittingEmptySubsequences: false) {
                if line.hasPrefix("event:") { event = line.dropFirst(6).trimmingCharacters(in: .whitespaces) }
                else if line.hasPrefix("data:") { payloadLines.append(line.dropFirst(5).trimmingCharacters(in: .whitespaces)) }
            }
            let payload = payloadLines.joined(separator: "\n")
            guard !payload.isEmpty else { continue }
            if event == "delta" { deltas.append(Self.decodedSSEString(payload) ?? payload) }
            if event == "result" { result = Self.decodedSSEContent(payload) ?? Self.decodedSSEString(payload) ?? payload }
            if event == "error" {
                let object = (try? JSONSerialization.jsonObject(with: Data(payload.utf8))) as? [String: Any]
                let code = object?["code"] as? String ?? "AI_STREAM_ERROR"
                let message = object?["message"] as? String ?? "AI 服务暂时无法完成请求"
                throw YinggeAPIError.server(status: 502, envelope: APIErrorEnvelope(code: code, message: message))
            }
        }
        let content = (result ?? deltas.joined()).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { throw YinggeAPIError.invalidResponse }
        return AssistantCustomerSummary(content: content)
    }

    private nonisolated static func decodedSSEContent(_ payload: String) -> String? {
        guard let object = (try? JSONSerialization.jsonObject(with: Data(payload.utf8))) as? [String: Any] else { return nil }
        return object["content"] as? String
    }

    private nonisolated static func decodedSSEString(_ payload: String) -> String? {
        try? JSONDecoder().decode(String.self, from: Data(payload.utf8))
    }

    private static let fractionalDateFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
    }()
    private static let dateFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; return f
    }()
    private nonisolated static func sameOrigin(_ lhs: URL, _ rhs: URL) -> Bool {
        lhs.scheme?.lowercased() == rhs.scheme?.lowercased() && lhs.host?.lowercased() == rhs.host?.lowercased()
            && normalizedPort(lhs) == normalizedPort(rhs)
    }
    private nonisolated static func normalizedPort(_ value: URL) -> Int? {
        value.port ?? (value.scheme?.lowercased() == "https" ? 443 : (value.scheme?.lowercased() == "http" ? 80 : nil))
    }
}

private final class SameOriginRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let origin: URL
    init(origin: URL) { self.origin = origin }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard let target = request.url,
              target.scheme?.lowercased() == origin.scheme?.lowercased(),
              target.host?.lowercased() == origin.host?.lowercased(),
              (target.port ?? (target.scheme == "https" ? 443 : 80)) == (origin.port ?? (origin.scheme == "https" ? 443 : 80))
        else { completionHandler(nil); return }
        completionHandler(request)
    }
}
