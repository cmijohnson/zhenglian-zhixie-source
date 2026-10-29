import Foundation
import Security
import CryptoKit
import SQLite3

public protocol SecureKeyValueStoring: Sendable {
    func data(for key: String) throws -> Data?
    func set(_ data: Data, for key: String) throws
    func remove(_ key: String) throws
}

public enum SecureStorageError: Error {
    case unhandled(OSStatus)
}

public final class KeychainStore: SecureKeyValueStoring, @unchecked Sendable {
    private let service: String

    public init(service: String = "com.eagleslab.yingge") {
        self.service = service
    }

    public func data(for key: String) throws -> Data? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw SecureStorageError.unhandled(status) }
        return result as? Data
    }

    public func set(_ data: Data, for key: String) throws {
        let query = baseQuery(key)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status: OSStatus
        if try self.data(for: key) == nil {
            var insert = query
            attributes.forEach { insert[$0.key] = $0.value }
            status = SecItemAdd(insert as CFDictionary, nil)
        } else {
            status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        }
        guard status == errSecSuccess else { throw SecureStorageError.unhandled(status) }
    }

    public func remove(_ key: String) throws {
        let status = SecItemDelete(baseQuery(key) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SecureStorageError.unhandled(status)
        }
    }

    private func baseQuery(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}

/// Keeps one business-intent idempotency key stable across UI retries and app
/// restarts. Call `clear` only after the server returned an unambiguous business
/// response; transport failures intentionally retain the same key.
public final class PersistentIntentKeyStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let lock = NSLock()
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func key(scope: String, operation: String, resourceID: String) -> String {
        let storageKey = storageKey(scope: scope, operation: operation, resourceID: resourceID)
        lock.lock(); defer { lock.unlock() }
        if let existing = defaults.string(forKey: storageKey), !existing.isEmpty { return existing }
        let value = "ios-\(operation)-\(UUID().uuidString.lowercased())"
        defaults.set(value, forKey: storageKey)
        return value
    }
    public func clear(scope: String, operation: String, resourceID: String) {
        lock.lock(); defer { lock.unlock() }
        defaults.removeObject(forKey: storageKey(scope: scope, operation: operation, resourceID: resourceID))
    }
    private func storageKey(scope: String, operation: String, resourceID: String) -> String {
        let digest = SHA256.hash(data: Data("\(scope)|\(operation)|\(resourceID)".utf8))
        return "yingge.intent." + digest.map { String(format: "%02x", $0) }.joined()
    }
}

public protocol CacheStoring: Sendable {
    func read<Value: Decodable & Sendable>(_ type: Value.Type, key: String) async throws -> Value?
    func write<Value: Encodable & Sendable>(_ value: Value, key: String) async throws
    func remove(key: String) async throws
    func removeAll() async throws
}

public enum EncryptedCacheError: Error { case openFailed, prepareFailed, stepFailed, missingCiphertext }

/// AES-GCM encrypted SQLite cache. `namespace` must bind both server and account
/// (for example `https-example-com|membership-uuid`) so offline data can never
/// bleed across environments or signed-in identities.
public actor ProtectedFileCache: CacheStoring {
    private let databaseURL: URL
    private let namespace: String
    private let secureStore: any SecureKeyValueStoring
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(namespace: String, root: URL? = nil, secureStore: any SecureKeyValueStoring = KeychainStore()) {
        precondition(!namespace.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Cache namespace is required")
        self.namespace = namespace
        self.secureStore = secureStore
        let base = root ?? FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let directory = base.appendingPathComponent("YinggeCloud", isDirectory: true)
        databaseURL = directory.appendingPathComponent("encrypted-cache.sqlite3")
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        #if os(iOS)
        try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
        #endif
    }

    public func read<Value: Decodable & Sendable>(_ type: Value.Type, key: String) async throws -> Value? {
        try withDatabase { database in
            let sql = "SELECT encrypted_value FROM encrypted_cache WHERE namespace=? AND cache_key=?"
            var statement: OpaquePointer?; guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { throw EncryptedCacheError.prepareFailed }
            defer { sqlite3_finalize(statement) }
            bind(namespace, at: 1, to: statement); bind(key, at: 2, to: statement)
            let result = sqlite3_step(statement)
            if result == SQLITE_DONE { return nil }
            guard result == SQLITE_ROW, let bytes = sqlite3_column_blob(statement, 0) else { throw EncryptedCacheError.stepFailed }
            let encrypted = Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, 0)))
            let box = try AES.GCM.SealedBox(combined: encrypted)
            return try decoder.decode(Value.self, from: AES.GCM.open(box, using: try encryptionKey()))
        }
    }

    public func write<Value: Encodable & Sendable>(_ value: Value, key: String) async throws {
        let plaintext = try encoder.encode(value)
        guard let encrypted = try AES.GCM.seal(plaintext, using: encryptionKey()).combined else { throw EncryptedCacheError.missingCiphertext }
        try withDatabase { database in
            let sql = "INSERT INTO encrypted_cache(namespace,cache_key,encrypted_value,updated_at) VALUES(?,?,?,?) ON CONFLICT(namespace,cache_key) DO UPDATE SET encrypted_value=excluded.encrypted_value,updated_at=excluded.updated_at"
            var statement: OpaquePointer?; guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { throw EncryptedCacheError.prepareFailed }
            defer { sqlite3_finalize(statement) }
            bind(namespace, at: 1, to: statement); bind(key, at: 2, to: statement)
            _ = encrypted.withUnsafeBytes { raw in sqlite3_bind_blob(statement, 3, raw.baseAddress, Int32(raw.count), sqliteTransient) }
            sqlite3_bind_double(statement, 4, Date().timeIntervalSince1970)
            guard sqlite3_step(statement) == SQLITE_DONE else { throw EncryptedCacheError.stepFailed }
        }
    }

    public func remove(key: String) async throws {
        try executeDelete(sql: "DELETE FROM encrypted_cache WHERE namespace=? AND cache_key=?", key: key)
    }

    public func removeAll() async throws {
        try executeDelete(sql: "DELETE FROM encrypted_cache WHERE namespace=?", key: nil)
    }

    private func encryptionKey() throws -> SymmetricKey {
        let account = "encrypted-cache-key.\(Data(namespace.utf8).base64EncodedString())"
        if let stored = try secureStore.data(for: account) { return SymmetricKey(data: stored) }
        let key = SymmetricKey(size: .bits256); let data = key.withUnsafeBytes { Data($0) }
        try secureStore.set(data, for: account); return key
    }
    private func withDatabase<T>(_ body: (OpaquePointer) throws -> T) throws -> T {
        var database: OpaquePointer?; guard sqlite3_open_v2(databaseURL.path, &database, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK,
                                           let database else { throw EncryptedCacheError.openFailed }
        defer { sqlite3_close(database) }
        let schema = "CREATE TABLE IF NOT EXISTS encrypted_cache(namespace TEXT NOT NULL,cache_key TEXT NOT NULL,encrypted_value BLOB NOT NULL,updated_at REAL NOT NULL,PRIMARY KEY(namespace,cache_key))"
        guard sqlite3_exec(database, schema, nil, nil, nil) == SQLITE_OK else { throw EncryptedCacheError.stepFailed }
        return try body(database)
    }
    private func executeDelete(sql: String, key: String?) throws {
        try withDatabase { database in
            var statement: OpaquePointer?; guard sqlite3_prepare_v2(database, sql, -1, &statement, nil) == SQLITE_OK else { throw EncryptedCacheError.prepareFailed }
            defer { sqlite3_finalize(statement) }; bind(namespace, at: 1, to: statement)
            if let key { bind(key, at: 2, to: statement) }
            guard sqlite3_step(statement) == SQLITE_DONE else { throw EncryptedCacheError.stepFailed }
        }
    }
    private func bind(_ value: String, at index: Int32, to statement: OpaquePointer?) {
        sqlite3_bind_text(statement, index, value, -1, sqliteTransient)
    }
}

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
