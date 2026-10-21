// swift-tools-version: 6.0
// SwiftPM package of @apexnative/navigation, for apps on React Native's SwiftPM setup (`npx react-native spm`).
// React Native's autolinking uses this manifest as is and links this directory at
// <app>/build/generated/autolinking/libs/ApexNavigation, so the paths of the React Native packages below
// are relative to that link. CocoaPods apps use ApexNavigation.podspec instead.

import PackageDescription

// Folders whose headers other folders import by bare name, as the CocoaPods header map allows.
let headerFolders = [
    ".", "Contract", "Core", "Layouts/Root", "Layouts/Screen", "Layouts/Stack", "Layouts/Tabs", "Layouts/SideMenu",
    "Layouts/SplitView", "Layouts/Arrangement", "Layouts/Modal", "Layouts/Overlay", "Layouts/External", "TopBar", "Options",
    "Options/Params", "Animations", "Animations/Elements", "Animations/Interpolators", "React", "React/Slots",
    "TurboModules", "Utils", "Public/ApexNavigation",
]

let package = Package(
    name: "ApexNavigation",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "ApexNavigation", targets: ["ApexNavigation"]),
    ],
    dependencies: [
        .package(name: "ReactNative", path: "../../../../xcframeworks"),
        .package(name: "React-GeneratedCode", path: "../../../ios"),
    ],
    targets: [
        .target(
            name: "ApexNavigation",
            dependencies: [
                .product(name: "ReactHeaders", package: "ReactNative"),
                .product(name: "ReactNativeHeaders", package: "ReactNative"),
                .product(name: "ReactNativeDependenciesHeaders", package: "ReactNative"),
                .product(name: "ReactAppHeaders", package: "React-GeneratedCode"),
                .product(name: "ReactAppDependencyProvider", package: "React-GeneratedCode"),
            ],
            path: ".",
            exclude: ["ARCHITECTURE.md", "CLAUDE.md", "CORE.md", "RECIPES.md", "ApexNavigation-prefix.h"],
            // <ApexNavigation/ApexNavigation.h>, <ApexNavigation/ANNAppDelegate.h>, …
            publicHeadersPath: "Public",
            cSettings: headerFolders.map { .headerSearchPath($0) } + [
                .unsafeFlags(["-include", "ApexNavigation-prefix.h"]),
            ],
            cxxSettings: headerFolders.map { .headerSearchPath($0) } + [
                .unsafeFlags(["-include", "ApexNavigation-prefix.h"]),
                // The Fabric ABI of the prebuilt React framework differs between Debug and Release.
                .define("DEBUG", .when(configuration: .debug)),
                .define("NDEBUG", .when(configuration: .release)),
            ],
            linkerSettings: [
                .linkedFramework("UIKit"),
                .linkedFramework("Foundation"),
                .linkedFramework("CoreGraphics"),
            ]
        ),
    ],
    cxxLanguageStandard: .cxx20
)
