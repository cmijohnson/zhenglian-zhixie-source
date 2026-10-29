// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "YinggeNativeApp",
    platforms: [
        .macOS(.v14),
    ],
    products: [
        .executable(name: "YinggeNativeApp", targets: ["YinggeNativeAppMain"]),
        .executable(name: "YinggeNativeSmokeRunner", targets: ["YinggeNativeSmokeRunner"]),
    ],
    dependencies: [
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.19"),
    ],
    targets: [
        .target(
            name: "YinggeNativeApp",
            dependencies: [
                .product(name: "ZIPFoundation", package: "ZIPFoundation"),
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3"),
            ]
        ),
        .executableTarget(
            name: "YinggeNativeAppMain",
            dependencies: ["YinggeNativeApp"],
            path: "Sources/YinggeNativeAppMain"
        ),
        .executableTarget(
            name: "YinggeNativeSmokeRunner",
            dependencies: ["YinggeNativeApp"],
            path: "Sources/YinggeNativeSmokeRunner",
            linkerSettings: [
                .linkedLibrary("sqlite3"),
            ]
        ),
    ]
)
