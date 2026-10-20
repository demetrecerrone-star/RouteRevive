#!/usr/bin/env bash
# Run inside an Android emulator environment; preserve test failures while collecting evidence.
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p emulator-evidence
adb logcat -d -s AndroidRuntime > emulator-evidence/runtime.log
adb pull /sdcard/Download/routerevive-smoke emulator-evidence/ || result=1
exit "$result"
