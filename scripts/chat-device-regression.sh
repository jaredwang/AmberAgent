#!/usr/bin/env bash
# Runs production chat/CAS/Room in the test Application's separate storage namespace.
set -euo pipefail
adb_path="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
serial="${SERIAL:-392288d7}"
package="${PACKAGE:-app.amber.agent.graphite}"
output_dir="${1:-artifacts/chat-performance/device-regression}"
adb_cmd=("$adb_path")
if [[ "$(uname -s)" == Darwin && "${ADB_ARCH:-x86_64}" == x86_64 ]]; then
  adb_cmd=(arch -x86_64 "$adb_path")
fi
run_adb() { "${adb_cmd[@]}" -s "$serial" "$@"; }
mkdir -p "$output_dir"
run_adb shell run-as "$package" sha256sum files/datastore/settings.preferences_pb > "$output_dir/user-model-settings-before.sha256"
# RouteActivity's Context-local launch preferences belong to the Activity context.
# Save/restore these UI-only preferences while all DB/DataStore work is sandboxed.
run_adb exec-out run-as "$package" cat shared_prefs/amber_agent.preferences.xml > "$output_dir/launch-preferences-backup.xml"
restore_launch_preferences() {
  run_adb shell am force-stop "$package" || true
  run_adb shell "run-as $package sh -c 'cat > shared_prefs/amber_agent.preferences.xml'" \
    < "$output_dir/launch-preferences-backup.xml"
}
trap restore_launch_preferences EXIT
for method in cancelRegenerateSwitchHistoryAndCopy reopenDurableReplyInFreshProcess; do
  ( sleep 1
    run_adb shell am start -W --activity-single-top -n "$package/androidx.activity.ComponentActivity"
  ) > "$output_dir/$method-foreground-kick.txt" 2>&1 &
  kick_pid=$!
  run_adb shell am instrument -w -e amberDeviceRegression true \
    -e class "app.amber.agent.ChatInteractionDeviceRegressionTest#$method" \
    "$package.test/app.amber.agent.ChatDeviceFixtureRunner" | tee "$output_dir/$method.txt"
  wait "$kick_pid" || true
  run_adb logcat -d -s AmberDeviceRegression:I > "$output_dir/$method-logcat.txt"
  rg -q 'OK \(1 test\)' "$output_dir/$method.txt"
done
restore_launch_preferences
trap - EXIT
run_adb shell run-as "$package" sha256sum files/datastore/settings.preferences_pb > "$output_dir/user-model-settings-after.sha256"
cmp "$output_dir/user-model-settings-before.sha256" "$output_dir/user-model-settings-after.sha256"
