import Foundation
import UserNotifications

actor ArchiveNotificationCoordinator {
    private var center: UNUserNotificationCenter? {
        guard Bundle.main.bundleURL.pathExtension == "app" else {
            return nil
        }
        return UNUserNotificationCenter.current()
    }

    func currentPermission() async -> ArchiveNotificationPermission {
        guard let center else {
            return .notDetermined
        }
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .ephemeral, .provisional:
            return .authorized
        case .denied:
            return .denied
        case .notDetermined:
            return .notDetermined
        @unknown default:
            return .notDetermined
        }
    }

    func requestAuthorization() async -> ArchiveNotificationPermission {
        guard let center else {
            return .denied
        }
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            return granted ? .authorized : .denied
        } catch {
            return .denied
        }
    }

    func sync(reminders: [ReminderEventRecord]) async {
        guard let center else { return }
        let permission = await currentPermission()
        guard permission == .authorized else { return }

        center.removeAllPendingNotificationRequests()

        for reminder in reminders {
            guard reminder.status == "pending",
                  let remindDate = ArchiveDate.parse(reminder.remindAt),
                  remindDate > Date()
            else {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.body = "打开英格学生档案原生版，查看本次跟进详情。"
            content.sound = .default

            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute],
                from: remindDate
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: "yingge-reminder-\(reminder.id)",
                content: content,
                trigger: trigger
            )
            try? await center.add(request)
        }
    }
}
