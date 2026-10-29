import SwiftUI

struct NativeFeatureSpec: Identifiable, Hashable {
    let id = UUID(); let title: String; let subtitle: String; let icon: String
}

enum NativeFeatures {
    static let ownership = NativeFeatureSpec(title: "公共池与二线协作", subtitle: "公共池、二线招募及归属处理", icon: "arrow.triangle.2.circlepath")
    static let tasks = NativeFeatureSpec(title: "任务中心", subtitle: "查看分配、领取与提交成果", icon: "checklist")
    static let performance = NativeFeatureSpec(title: "绩效中心", subtitle: "今日积分、明细与交流申报", icon: "chart.bar.fill")
    static let consultations = NativeFeatureSpec(title: "咨询交接", subtitle: "交接卡、预约和结果回填", icon: "arrow.left.arrow.right")
    static let contentTasks = NativeFeatureSpec(title: "内容任务", subtitle: "内容提交与指标更新", icon: "rectangle.stack.fill")
    static let circle = NativeFeatureSpec(title: "内容圈子", subtitle: "查看团队内容", icon: "circle.grid.cross.fill")
    static let announcements = NativeFeatureSpec(title: "公告中心", subtitle: "置顶、优先级与历史公告", icon: "megaphone.fill")
    static let assistant = NativeFeatureSpec(title: "AI 助手", subtitle: "服务可用性与客户洞察入口", icon: "sparkles")
    static let releases = NativeFeatureSpec(title: "更新日志", subtitle: "版本改进和修复记录", icon: "clock.arrow.circlepath")
    static let agent = NativeFeatureSpec(title: "我的 Agent", subtitle: "已授权客户端与撤销授权", icon: "command")
    static let devices = NativeFeatureSpec(title: "设备管理", subtitle: "移动设备及撤销会话", icon: "iphone.gen3")
    static let all = [ownership, tasks, performance, consultations, contentTasks, circle, announcements, assistant, releases, agent, devices]
}
