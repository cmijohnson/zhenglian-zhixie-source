import SwiftUI
import WebKit

struct QQWorkbenchView: View {
    @EnvironmentObject private var model: AppModel
    @State private var request: URLRequest?
    var body: some View {
        Group {
            if let request { ControlledWebView(request: request).ignoresSafeArea(edges: .bottom) }
            else { VStack(spacing: 14) { ProgressView(); Text("正在建立一次性 QQ 工作台会话…").font(.footnote).foregroundStyle(.secondary) } }
        }.navigationTitle("QQ 对话").navigationBarTitleDisplayMode(.inline).task { request = await model.qqExchangeRequest() }
            .toolbar { Button { Task { request = await model.qqExchangeRequest() } } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("刷新 QQ 工作台会话") }
    }
}

private struct ControlledWebView: UIViewRepresentable {
    let request: URLRequest
    func makeCoordinator() -> Coordinator { Coordinator(allowedOrigin: request.url) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration(); config.websiteDataStore = .nonPersistent()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: config); view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true; view.scrollView.contentInsetAdjustmentBehavior = .automatic
        context.coordinator.installNetworkAllowList(on: view, request: request)
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    final class Coordinator: NSObject, WKNavigationDelegate {
        let allowedOrigin: URL?; init(allowedOrigin: URL?) { self.allowedOrigin = allowedOrigin }
        func installNetworkAllowList(on webView: WKWebView, request: URLRequest) {
            guard let origin = allowedOrigin, QQWorkbenchRoutePolicy.isAllowed(request.url, origin: origin),
                  let allowed = QQWorkbenchRoutePolicy.contentRuleAllowPattern(origin: origin) else { return }
            let rules: [[String: Any]] = [
                ["trigger": ["url-filter": ".*"], "action": ["type": "block"]],
                ["trigger": ["url-filter": allowed], "action": ["type": "ignore-previous-rules"]]
            ]
            guard let data = try? JSONSerialization.data(withJSONObject: rules),
                  let json = String(data: data, encoding: .utf8) else { return }
            WKContentRuleListStore.default().compileContentRuleList(
                forIdentifier: "yingge-qq-\(UUID().uuidString)", encodedContentRuleList: json
            ) { list, _ in
                DispatchQueue.main.async {
                    guard let list else { return }
                    webView.configuration.userContentController.add(list)
                    webView.load(request)
                }
            }
        }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(QQWorkbenchRoutePolicy.isAllowed(action.request.url, origin: allowedOrigin) ? .allow : .cancel)
        }
    }
}
