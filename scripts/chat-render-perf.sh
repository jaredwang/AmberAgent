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
runner="$package.test/app.amber.agent.AmberAgentAndroidTestRunner"
case "$label" in
  baseline) optimized=false ;;
  optimized) optimized=true ;;
  *) echo "Expected baseline or optimized" >&2; exit 2 ;;
esac
# macOS 27's arm64 adb USB backend can abort inside libunwind. ADB_ARCH=native opts out.
adb_cmd=("$adb_path")
if [[ "$(uname -s)" == Darwin && "${ADB_ARCH:-x86_64}" == x86_64 ]]; then
  adb_cmd=(arch -x86_64 "$adb_path")
fi
run_adb() { "${adb_cmd[@]}" -s "$serial" "$@"; }
mkdir -p "$output_dir"
run_adb get-state
run_adb shell input keyevent KEYCODE_WAKEUP
# A secure keyguard needs the owner; this command only dismisses an unsecured keyguard.
run_adb shell wm dismiss-keyguard
run_adb shell dumpsys window policy > "$output_dir/window-policy.txt"
run_adb shell dumpsys display > "$output_dir/display.txt"
run_adb shell getprop ro.build.fingerprint > "$output_dir/fingerprint.txt"
shasum -a 256 "$main_apk" "$test_apk" > "$output_dir/apk-sha256.txt"
run_adb install -r "$main_apk" | tee "$output_dir/install-main.txt"
run_adb install -r "$test_apk" | tee "$output_dir/install-test.txt"
# Keep refresh rate, battery state, font, and device settings unchanged between runs.
trace_running=false
stop_trace() {
  if [[ "$trace_running" == true ]]; then
    run_adb shell atrace --async_stop -z > "$output_dir/$profile.ctrace" || true
    trace_running=false
  fi
}
trap stop_trace EXIT
for profile in short chinese mixed; do
  run_adb shell dumpsys gfxinfo "$package" reset > /dev/null
  if run_adb shell atrace --async_start -b 32768 -a "$package" gfx view sched freq idle dalvik \
    > "$output_dir/$profile-trace-start.txt" 2>&1; then
    trace_running=true
  fi
  run_adb shell am instrument -w \
    -e amberRenderPerf true -e amberRenderLabel "$label" \
    -e amberRenderProfile "$profile" -e amberRenderOptimizedLayout "$optimized" \
    -e class app.amber.agent.ChatRenderingDevicePerfTest "$runner" \
    | tee "$output_dir/$profile-instrumentation.txt"
  stop_trace
  if ! rg -q 'OK \(1 test\)' "$output_dir/$profile-instrumentation.txt"; then
    echo "Fixture did not complete; unlock the phone and allow the USB test APK install." >&2
    exit 1
  fi
  run_adb pull "/sdcard/Android/data/$package/files/amber-render-$label-$profile.json" \
    "$output_dir/$profile.json"
  run_adb shell dumpsys gfxinfo "$package" framestats > "$output_dir/$profile-gfxinfo.txt"
  run_adb logcat -d -s AmberRenderPerf:I > "$output_dir/$profile-logcat.txt"
done
