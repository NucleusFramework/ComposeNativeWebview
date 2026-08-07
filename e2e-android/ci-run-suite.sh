#!/usr/bin/env bash
# Visual e2e driver for CI (android-emulator-runner).
# Kept as a single script because the action runs `script:` line-by-line with `sh -c`.
set -euo pipefail

./gradlew :e2e-android:installDebug --no-configuration-cache
adb logcat -c
adb uninstall dev.nucleusframework.webview.e2e >/dev/null 2>&1 || true
./gradlew :e2e-android:installDebug --no-configuration-cache
adb logcat -c
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n dev.nucleusframework.webview.e2e/.MainActivity

# Wait for suite log line (max ~12 min)
for i in $(seq 1 360); do
  if adb logcat -d | grep -q 'SUITE_FINISHED'; then
    adb logcat -d | grep -E 'SUITE_FINISHED|allGreen' | tail -10
    if adb logcat -d | grep -q 'SUITE_FINISHED passed=true'; then
      echo "Android visual e2e PASSED"
      exit 0
    else
      echo "Android visual e2e FAILED" >&2
      adb logcat -d | grep 'ComposeWebViewE2E' | tail -120 || true
      exit 1
    fi
  fi
  sleep 2
done

echo "Timed out waiting for SUITE_FINISHED" >&2
adb logcat -d | grep -E 'ComposeWebViewE2E|AndroidRuntime' | tail -120 || true
exit 1
