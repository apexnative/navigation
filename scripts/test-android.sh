#!/bin/bash
# The library's Robolectric unit tests. Prints OK, or the tests that failed (or why the tests didn't compile).
# Usage: scripts/test-android.sh [gradle args...], e.g. --tests '*SomeTest'
source "$(dirname "$0")/lib/quiet.sh"
rm -rf "$ROOT/android/build/test-results/testDebugUnitTest"
tests() {
  cd example/android && ../../scripts/android-env.sh ./gradlew :apexnative_navigation:testDebugUnitTest -q "$@"
}
quiet test-android junit tests "$@"
