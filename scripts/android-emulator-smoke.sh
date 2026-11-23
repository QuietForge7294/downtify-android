#!/usr/bin/env bash
set -euo pipefail

package=com.henriquesebastiao.downtify.debug
test_package="$package.test"
reports=build/ci-smoke
mkdir -p "$reports"
trap 'adb logcat -d -t 1000 > "$reports/logcat.txt" 2>&1 || true' EXIT

adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
component=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER "$package" | tr -d '\r' | tail -n 1)
[[ "$component" == "$package/"* ]]
adb shell am start -W -n "$component" | tee "$reports/launch.txt"
grep -q '^Status: ok' "$reports/launch.txt"
adb shell dumpsys activity activities > "$reports/activities.txt"
grep -E '(topResumedActivity|mResumedActivity)' "$reports/activities.txt" | grep -F "$package/"

adb shell am instrument -w -r \
    -e class com.henriquesebastiao.downtify.LegacyCarProcessTest \
    "$test_package/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee "$reports/instrumentation.txt"
# Instrumentation can exit zero even when all tests were aborted or failed.
grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$reports/instrumentation.txt"
