import Darwin
import Foundation
import YinggeNativeApp

@main
struct YinggeNativeSmokeRunner {
    static func main() {
        do {
            let checks = try ArchiveSmokeChecks.run()
            print("Native repository smoke checks passed.")
            for check in checks {
                print("- \(check)")
            }
        } catch {
            fputs("Native repository smoke checks failed: \(error.localizedDescription)\n", stderr)
            exit(1)
        }
    }
}
