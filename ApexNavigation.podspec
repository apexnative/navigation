# The CocoaPods spec of @apexnative/navigation. It builds the same sources as ios/Package.swift (the SwiftPM target
# ApexNavigation): the Objective-C++ core and modules under ios/, with the public headers of ios/Public/ApexNavigation
# (<ApexNavigation/ApexNavigation.h>, <ApexNavigation/ANNAppDelegate.h>, <ApexNavigation/ANNSceneDelegate.h>). Other
# headers stay private to the pod and are imported by bare name. The C++ of the Fabric components ANNSlot and
# ANNSafeAreaView is under ios/cpp, next to the code React Native's codegen makes from src/: headers, and one source
# file with the store of the slots' measured heights.
require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ApexNavigation"
  s.module_name  = "ApexNavigation"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/apexnative/navigation.git", :tag => "v#{s.version}" }

  s.source_files         = "ios/**/*.{h,m,mm,cpp}"
  s.public_header_files  = "ios/Public/ApexNavigation/*.h"
  # The prefix header is SwiftPM's; CocoaPods makes its own with Foundation and UIKit.
  s.exclude_files        = ["ios/build/**/*", "ios/ApexNavigation-prefix.h"]
  s.frameworks           = "UIKit", "Foundation", "CoreGraphics"
  s.requires_arc         = true
  # Kept by install_modules_dependencies, which adds the folly flags after it.
  s.compiler_flags       = "-DUSE_HERMES=1"

  # A module map for the pod, so Swift apps `import ApexNavigation` (the Showcase's AppDelegate does).
  s.pod_target_xcconfig = {
    "CLANG_CXX_LANGUAGE_STANDARD" => "c++20",
    "DEFINES_MODULE" => "YES",
  }

  # React Native's dependencies of a New Architecture library: React-Core, the codegen of src/ (ApexNavigationSpec),
  # Fabric, the TurboModule headers and the header search paths they need.
  install_modules_dependencies(s)
  # The app's generated provider of modules and components, which ANNAppDelegate hands to React Native's factory.
  s.dependency "ReactAppDependencyProvider"
end
