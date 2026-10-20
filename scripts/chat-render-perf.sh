#!/usr/bin/env bash
# In-memory production-renderer fixture. No provider, settings, DB, or user content access.
set -euo pipefail
if (( $# < 3 )); then
  echo "Usage: $0 baseline|optimized MAIN_APK TEST_APK [output-directory]" >&2
  exit 2
fi
label="$1"
main_apk="$2"
test_apk="$3"
output_dir="${4:-artifacts/chat-performance/$label}"
adb_path="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
serial="${SERIAL:-392288d7}"
package="${PACKAGE:-app.amber.agent.graphite}"
runner="$package.test/app.amber.agent.ChatDeviceFixtureRunner"
case "$label" in
  baseline) optimized=false ;;
  optimized) optimized=true ;;
  *) echo "Expected baseline or optimized" >&2; exit 2 ;;
esac
# A labelled APK pair may need the same layout strategy on both sides.
# CHAT_RENDER_LAYOUT=true forces the production streamingTailLayout for both.
case "${CHAT_RENDER_LAYOUT:-}" in
  true|optimized) optimized=true ;;
  false|animate) optimized=false ;;
  "") ;;
  *) echo "CHAT_RENDER_LAYOUT must be true/false" >&2; exit 2 ;;
esac
# macOS 27's arm64 adb USB backend can abort inside libunwind. ADB_ARCH=native opts out.
adb_cmd=("$adb_path")
if [[ "$(uname -s)" == Darwin && "${ADB_ARCH:-x86_64}" == x86_64 ]]; then
  adb_cmd=(arch -x86_64 "$adb_path")
fi
run_adb() { "${adb_cmd[@]}" -s "$serial" "$@"; }
mkdir -p "$output_dir"
run_adb get-state
# Scope the display cap to this measurement and restore both original values.
# A window hint alone is ignored by MIUI's touch boost on this device.
peak_before="$(run_adb shell settings get system peak_refresh_rate | tr -d '\r')"
min_before="$(run_adb shell settings get system min_refresh_rate | tr -d '\r')"
# MIUI keeps its own per-panel preference; peak/min alone are ignored once set.
user_rate_before="$(run_adb shell settings get secure user_refresh_rate | tr -d '\r')"
miui_rate_before="$(run_adb shell settings get secure miui_refresh_rate | tr -d '\r')"
printf 'peak_refresh_rate=%s\nmin_refresh_rate=%s\nuser_refresh_rate=%s\nmiui_refresh_rate=%s\n' \
  "$peak_before" "$min_before" "$user_rate_before" "$miui_rate_before" > "$output_dir/refresh-before.txt"
restore_refresh() {
  if [[ "$peak_before" == null ]]; then run_adb shell settings delete system peak_refresh_rate > /dev/null
  else run_adb shell settings put system peak_refresh_rate "$peak_before"; fi
  if [[ "$min_before" == null ]]; then run_adb shell settings delete system min_refresh_rate > /dev/null
  else run_adb shell settings put system min_refresh_rate "$min_before"; fi
  if [[ "$user_rate_before" == null ]]; then run_adb shell settings delete secure user_refresh_rate > /dev/null
  else run_adb shell settings put secure user_refresh_rate "$user_rate_before"; fi
  if [[ "$miui_rate_before" == null ]]; then run_adb shell settings delete secure miui_refresh_rate > /dev/null
  else run_adb shell settings put secure miui_refresh_rate "$miui_rate_before"; fi
}
trap restore_refresh EXIT
apply_refresh_cap() {
  # Re-assert for every profile: MIUI can promote the panel back to 120 Hz
  # between instrumentation processes even while the system cap is set.
  run_adb shell settings put system peak_refresh_rate 60.0
  run_adb shell settings put system min_refresh_rate 60.0
  run_adb shell settings put secure user_refresh_rate 60
  run_adb shell settings put secure miui_refresh_rate 60
}
apply_refresh_cap
run_adb shell input keyevent KEYCODE_WAKEUP
# A secure keyguard needs the owner; this command only dismisses an unsecured keyguard.
run_adb shell wm dismiss-keyguard
run_adb shell dumpsys window policy > "$output_dir/window-policy.txt"
run_adb shell dumpsys display > "$output_dir/display.txt"
run_adb shell getprop ro.build.fingerprint > "$output_dir/fingerprint.txt"
shasum -a 256 "$main_apk" "$test_apk" > "$output_dir/apk-sha256.txt"
run_adb install -r "$main_apk" | tee "$output_dir/install-main.txt"
run_adb install -r "$test_apk" | tee "$output_dir/install-test.txt"
# Both runs use the same temporary display cap, font, content and gestures.
trace_running=false
stop_trace() {
  if [[ "$trace_running" == true ]]; then
    run_adb shell atrace --async_stop -z > "$output_dir/$profile.ctrace" || true
    trace_running=false
  fi
}
cleanup() { stop_trace; restore_refresh; }
trap cleanup EXIT
for profile in ${PROFILES:-short chinese mixed}; do
  apply_refresh_cap
  run_adb shell dumpsys display | rg -m1 'mActiveSfDisplayMode' > "$output_dir/$profile-refresh-before.txt" || true
  run_adb shell dumpsys gfxinfo "$package" reset > /dev/null
  if run_adb shell atrace --async_start -b 32768 -a "$package" gfx view sched freq idle dalvik \
    > "$output_dir/$profile-trace-start.txt" 2>&1; then
    trace_running=true
  fi
  # MIUI may freeze a newly started instrumentation process before Application
  # initialization. A single shell foreground kick avoids changing freezer policy.
  ( sleep 1
    run_adb shell am start -W --activity-single-top -n "$package/androidx.activity.ComponentActivity"
  ) > "$output_dir/$profile-foreground-kick.txt" 2>&1 &
  kick_pid=$!
  run_adb shell am instrument -w \
    -e amberRenderPerf true -e amberRenderLabel "$label" \
    -e amberRenderProfile "$profile" -e amberRenderOptimizedLayout "$optimized" \
    -e class app.amber.agent.ChatRenderingDevicePerfTest "$runner" \
    | tee "$output_dir/$profile-instrumentation.txt"
  wait "$kick_pid" || true
  stop_trace
  if ! rg -q 'OK \(1 test\)' "$output_dir/$profile-instrumentation.txt"; then
    echo "Fixture did not complete; inspect the instrumentation result and foreground/device state." >&2
    exit 1
  fi
  run_adb pull "/sdcard/Android/data/$package/files/amber-render-$label-$profile.json" \
    "$output_dir/$profile.json"
  run_adb pull "/sdcard/Android/data/$package/files/amber-render-$label-$profile-gfxinfo.txt" \
    "$output_dir/$profile-gfxinfo.txt"
  run_adb logcat -d -s AmberRenderPerf:I > "$output_dir/$profile-logcat.txt"
done
