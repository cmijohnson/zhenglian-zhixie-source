import SwiftUI
import UserNotifications

@main
struct YinggeCloudApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            Group {
                switch model.phase {
                case .restoring: LaunchView()
                case .signedOut: LoginView()
                case .passwordChangeRequired: PasswordChangeView()
                case .signedIn: RootTabView()
                }
            }
            .environmentObject(model)
            .tint(YinggeTheme.blue)
            .task { if model.phase == .restoring { await model.restore() } }
            .onReceive(NotificationCenter.default.publisher(for: .yinggeAPNSTokenAvailable)) { note in
                if let data = note.object as? Data { model.receivedAPNSToken(data) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .yinggePushRegistrationFailed)) { note in
                if let error = note.object as? Error { model.receivedPushRegistrationFailure(error) }
            }
            .onReceive(NotificationCenter.default.publisher(for: .yinggeUniPushRegistrationAvailable)) { note in
                if let registrationID = note.object as? String {
                    Task { await model.registerUniPush(registrationID: registrationID, vendor: note.userInfo?["vendor"] as? String) }
                }
            }
            .alert("提示", isPresented: Binding(get: { model.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil } })) {
                Button("知道了", role: .cancel) { model.errorMessage = nil }
            } message: { Text(model.errorMessage ?? "") }
        }
        .onChange(of: scenePhase) { phase in if phase == .background { Task { await model.enterBackground() } } }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // APNs is intentionally adapter-only. Contract registration remains UniPush until the backend adds APNS.
        NotificationCenter.default.post(name: .yinggeAPNSTokenAvailable, object: deviceToken)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NotificationCenter.default.post(name: .yinggePushRegistrationFailed, object: error)
    }
}
extension Notification.Name {
    static let yinggeAPNSTokenAvailable = Notification.Name("yingge.apns-token")
    static let yinggePushRegistrationFailed = Notification.Name("yingge.apns-failed")
    /// The external UniPush adapter posts its registrationId here after its SDK is configured.
    static let yinggeUniPushRegistrationAvailable = Notification.Name("yingge.unipush-registration")
}
