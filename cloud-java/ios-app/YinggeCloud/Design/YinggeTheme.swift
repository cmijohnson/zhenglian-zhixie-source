import SwiftUI

enum YinggeTheme {
    static let blue = Color(red: 0.20, green: 0.36, blue: 0.86)
    static let navy = Color(red: 0.05, green: 0.10, blue: 0.24)
    static let mist = Color(red: 0.95, green: 0.97, blue: 1.00)
    static let mint = Color(red: 0.16, green: 0.68, blue: 0.51)
    static let coral = Color(red: 0.92, green: 0.36, blue: 0.32)
    static let gradient = LinearGradient(colors: [blue, Color(red: 0.22, green: 0.53, blue: 0.95)], startPoint: .topLeading, endPoint: .bottomTrailing)
}

struct YinggeCard: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(16).background(.background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: YinggeTheme.navy.opacity(0.07), radius: 18, y: 8)
    }
}
extension View { func yinggeCard() -> some View { modifier(YinggeCard()) } }

struct SectionEyebrow: View {
    let text: String
    var body: some View { Text(text.uppercased()).font(.caption2.weight(.bold)).tracking(2).foregroundStyle(YinggeTheme.blue) }
}

struct LaunchView: View {
    var body: some View {
        ZStack {
            YinggeTheme.mist.ignoresSafeArea()
            VStack(spacing: 22) {
                Image("BrandLogo").resizable().scaledToFit().frame(maxWidth: 270).accessibilityLabel("英格客户云")
                ProgressView().tint(YinggeTheme.blue)
            }.padding(32)
        }
    }
}

