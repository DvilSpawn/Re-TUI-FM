#!/usr/bin/env bash
set -eu

ADB_BIN=${ADB:-adb}
PACKAGE=com.dvil.retui.fm
ACTIVITY="$PACKAGE/.MainActivity"
ROOT=/sdcard/Download/retui-fm-performance
serial=${SERIAL:-$($ADB_BIN devices | awk '$2 == "device" { print $1; exit }')}

if [[ -z "$serial" ]]; then
  printf 'No Android device is connected.\n' >&2
  exit 2
fi

adb() { command "$ADB_BIN" -s "$serial" "$@"; }
launch_time() {
  adb shell am force-stop "$PACKAGE" >/dev/null
  adb shell am start -W -n "$ACTIVITY" "$@" | awk '/TotalTime:/ { print $2 }'
}

adb shell "mkdir -p '$ROOT/1000' '$ROOT/5000'; i=1; while [ \$i -le 5000 ]; do : > '$ROOT/5000/file-'\$i.txt; if [ \$i -le 1000 ]; then : > '$ROOT/1000/file-'\$i.txt; fi; i=\$((i+1)); done"

times=()
for _ in 1 2 3 4 5; do times+=("$(launch_time)"); done
median=$(printf '%s\n' "${times[@]}" | sort -n | sed -n '3p')
folder_1000=$(launch_time --es action open --es path "$ROOT/1000")
sleep 2
folder_5000=$(launch_time --es action open --es path "$ROOT/5000")
sleep 3
total_pss_kb=$(adb shell dumpsys meminfo "$PACKAGE" | awk '/^[[:space:]]*TOTAL[[:space:]]/ { print $2; exit }')

printf 'serial\tcold_start_median_ms\tfolder_1000_ms\tfolder_5000_ms\ttotal_pss_kb\n'
printf '%s\t%s\t%s\t%s\t%s\n' "$serial" "$median" "$folder_1000" "$folder_5000" "$total_pss_kb"
