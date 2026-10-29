import SwiftUI
import YinggeNativeApp

@main
struct YinggeNativeAppMain: App {
    @StateObject private var store = ArchiveStore()

    var body: some Scene {
        WindowGroup("英格学生档案") {
            ContentView()
                .environmentObject(store)
                .frame(minWidth: 1180, minHeight: 760)
        }
        .windowResizability(.contentSize)

        Settings {
            SettingsWorkspaceView()
                .environmentObject(store)
                .frame(width: 720, height: 560)
        }
    }
}
