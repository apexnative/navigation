#!/bin/bash
# Builds the Showcase for the iOS simulator; compiler warnings in ios/ fail it. With a simulator's UDID it builds for
# that simulator, installs the app and starts it again.
# Usage: scripts/build-ios.sh [UDID]
source "$(dirname "$0")/lib/quiet.sh"
UDID=$1
DESTINATION=${UDID:+id=$UDID}
build() {
  cd example/ios && LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 node ../../scripts/ios-warnings.mjs xcodebuild \
    -workspace Showcase.xcworkspace -scheme Showcase -configuration Debug -sdk iphonesimulator \
    -destination "${DESTINATION:-generic/platform=iOS Simulator}" -derivedDataPath build/DerivedData \
    COMPILER_INDEX_STORE_ENABLE=NO build
}
start() {
  xcrun simctl bootstatus "$UDID" -b >/dev/null &&
    xcrun simctl install "$UDID" example/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/Showcase.app &&
    xcrun simctl launch --terminate-running-process "$UDID" dev.apexnative.navigation.showcase
}
quiet build-ios xcode build || exit 1
[ -z "$UDID" ] || quiet start-ios tail start
