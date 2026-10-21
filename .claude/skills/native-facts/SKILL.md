---
name: native-facts
description: Finds exact facts about iOS, Android and React Native APIs — signatures, the OS version they need, how the system behaves — from the SDKs this project builds with. Use before native code relies on a platform API, and before calling something a bug of the library.
argument-hint: <a question about a UIKit, SwiftUI, Android, Material or React Native API>
---

# Native facts

Answer from the sources the project builds with, in this order of trust. Never from memory alone: memory is where a
question starts, not its answer.

## Sources

1. **iOS SDK headers** of the installed Xcode (`xcodebuild -version`). The SDK:
   `$(xcrun --sdk iphonesimulator --show-sdk-path)`.
   - Objective-C APIs: `System/Library/Frameworks/<Framework>.framework/Headers/*.h` — signatures,
     `API_AVAILABLE(ios(…))`, `API_DEPRECATED`, `NS_SWIFT_NAME`.
   - Swift-only APIs (SwiftUI and others):
     `System/Library/Frameworks/<Framework>.framework/Modules/<Framework>.swiftmodule/arm64-apple-ios-simulator.swiftinterface`.
2. **Android.**
   - The framework: `$ANDROID_HOME/sources/android-<level>/` (`ANDROID_HOME` defaults to `~/Library/Android/sdk`), at
     the project's `compileSdk` (`example/android/build.gradle`). When that level's sources aren't installed, use the
     nearest installed level and name it in the answer.
   - Material and AndroidX: their `*-sources.jar` in
     `~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/`, at the version in `android/build.gradle`;
     read a file with `unzip -p <jar> <path inside>`. When that version isn't cached, use the nearest cached one and
     name it.
3. **React Native** in `node_modules/react-native/` (its version in `package.json` there): `React/` (iOS),
   `ReactAndroid/` (Android), `ReactCommon/` (C++), `Libraries/` (JS).
4. **A test app**, for behavior the sources can't show (what UIKit does on a swipe, what Material draws). A minimal app
   in the scratchpad, never in the repository, run only on the simulator or device the task names.
5. **Apple and Android documentation** on the web: to explain, not to settle a signature or a version.

## The answer

For each fact:

- the fact, in one or two sentences;
- the source: `file:line` or a URL;
- the OS version or library version it needs;
- how sure: `header` / `source`, `test app`, `docs`, or `not verified`.

Add a short code example when it helps. Say plainly when sources disagree, when a version had to be approximated, or
when the answer wasn't found.

## Don't

- Edit the repository.
- Download SDK packages or sources without asking: say what is missing and how to install it.
