// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "YinggeMobileCore",
    defaultLocalization: "zh-Hans",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(name: "YinggeCore", targets: ["YinggeCore"])
    ],
    targets: [
        .target(name: "YinggeCore", path: "Sources/YinggeCore"),
        .testTarget(name: "YinggeCoreTests", dependencies: ["YinggeCore"], path: "Tests/YinggeCoreTests")
    ]
)
