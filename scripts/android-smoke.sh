#!/usr/bin/env bash
# Run inside an Android emulator environment; preserve test failures while collecting evidence.
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p emulator-evidence
adb logcat -d -s AndroidRuntime > emulator-evidence/runtime.log
adb pull /sdcard/Android/data/com.routerevive.app/files/smoke-screens emulator-evidence/ || true
exit "$result"
