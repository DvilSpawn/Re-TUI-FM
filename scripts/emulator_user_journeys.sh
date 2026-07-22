#!/usr/bin/env bash
set -u

ADB_BIN=${ADB:-adb}
PACKAGE=com.dvil.retui.fm
ACTION=com.dvil.retui.fm.OPEN_CONSOLE
ROOT=/sdcard/Download/retui-fm-test
passed=0
failed=0

pass() { printf 'PASS  %s\n' "$1"; passed=$((passed + 1)); }
fail() { printf 'FAIL  %s -- %s\n' "$1" "$2"; failed=$((failed + 1)); }

launch() {
  adb shell am force-stop "$PACKAGE" >/dev/null
  adb shell am start -W -a "$ACTION" -p "$PACKAGE" \
    --es path "$ROOT" --es command "'$1'" >/dev/null
  sleep 1
}

launch_action() {
  local action=$1 path=${2:-$ROOT}
  adb shell am force-stop "$PACKAGE" >/dev/null
  adb shell am start -W -a "$ACTION" -p "$PACKAGE" \
    --es action "$action" --es path "$path" >/dev/null
  sleep 1
}

ui_text() {
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | tr '\n' ' '
}

expect_file() {
  local name=$1 path=$2
  if adb shell test -e "$path"; then pass "$name"; else fail "$name" "missing $path"; fi
}

expect_absent() {
  local name=$1 path=$2
  if adb shell test ! -e "$path"; then pass "$name"; else fail "$name" "still present: $path"; fi
}

expect_ui() {
  local name=$1 expected=$2 actual
  actual=$(ui_text)
  if [[ "$actual" == *"$expected"* ]]; then pass "$name"; else fail "$name" "UI did not contain: $expected"; fi
}

tap_text() {
  local expected=$1 xml x1 y1 x2 y2
  xml=$(ui_text)
  if [[ $xml =~ text=\"$expected\"[^\>]*bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\" ]]; then
    x1=${BASH_REMATCH[1]}; y1=${BASH_REMATCH[2]}; x2=${BASH_REMATCH[3]}; y2=${BASH_REMATCH[4]}
    adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
    sleep 1
    return 0
  fi
  return 1
}

long_press_text() {
  local expected=$1 xml x1 y1 x2 y2
  xml=$(ui_text)
  if [[ $xml =~ text=\"$expected\"[^\>]*bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\" ]]; then
    x1=${BASH_REMATCH[1]}; y1=${BASH_REMATCH[2]}; x2=${BASH_REMATCH[3]}; y2=${BASH_REMATCH[4]}
    adb shell input swipe $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) 900
    sleep 1
    return 0
  fi
  return 1
}

serial=$($ADB_BIN devices | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')
if [[ -z "$serial" ]]; then
  printf 'No running Android emulator found.\n' >&2
  exit 2
fi
adb() { "$ADB_BIN" -s "$serial" "$@"; }

adb wait-for-device
adb shell appops set "$PACKAGE" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
adb shell rm -rf "$ROOT"
adb shell mkdir -p "$ROOT/inbox" "$ROOT/archive"
printf 'hello from RETUI FM\n' | adb shell sh -c "'cat > $ROOT/inbox/note.txt'"
printf 'second selected file\n' | adb shell sh -c "'cat > $ROOT/inbox/second.txt'"

launch 'mkdir projects'
expect_file 'create a folder' "$ROOT/projects"

launch 'cp inbox/note.txt projects/copied.txt'
expect_file 'copy a file' "$ROOT/projects/copied.txt"

launch 'mv projects/copied.txt archive/moved.txt'
expect_file 'move or rename a file' "$ROOT/archive/moved.txt"
expect_absent 'move removes the source' "$ROOT/projects/copied.txt"

launch 'preview archive/moved.txt'
expect_ui 'preview a text file' 'hello from RETUI FM'

adb shell am force-stop "$PACKAGE" >/dev/null
adb shell am start -W -a "$ACTION" -p "$PACKAGE" --es action search \
  --es path "$ROOT" --es search_name moved >/dev/null
sleep 1
expect_ui 'search by file name' 'moved.txt'

launch 'rm archive/moved.txt'
expect_ui 'deletion asks for confirmation' 'Delete moved.txt?'

launch_action open "$ROOT/inbox"
expect_ui 'structured open action opens a directory' 'note.txt'
if long_press_text note.txt; then
  expect_ui 'long press starts selection mode' '1 selected'
else
  fail 'long press starts selection mode' 'could not locate note.txt'
fi
if tap_text second.txt; then
  expect_ui 'tap adds another selected file' '2 selected'
else
  fail 'tap adds another selected file' 'could not locate second.txt'
fi
if tap_text ZIP; then
  adb shell input text journey.zip
  if tap_text CREATE; then
    sleep 2
    expect_file 'create ZIP from multiple selected files' "$ROOT/inbox/journey.zip"
  else
    fail 'create ZIP from multiple selected files' 'CREATE action not found'
  fi
else
  fail 'create ZIP from multiple selected files' 'ZIP action not found'
fi

launch_action open "$ROOT/inbox"
long_press_text note.txt && tap_text second.txt
if tap_text MOVE; then
  adb shell input text "$ROOT/archive"
  if tap_text MOVE; then
    sleep 2
    expect_file 'bulk move selected files' "$ROOT/archive/note.txt"
    expect_file 'bulk move keeps every selected item' "$ROOT/archive/second.txt"
  else
    fail 'bulk move selected files' 'MOVE confirmation not found'
  fi
else
  fail 'bulk move selected files' 'MOVE action not found'
fi

launch_action open "$ROOT/archive"
long_press_text note.txt && tap_text second.txt
if tap_text TRASH && tap_text TRASH; then
  sleep 1
  expect_absent 'bulk trash removes first selected item' "$ROOT/archive/note.txt"
  expect_absent 'bulk trash removes every selected item' "$ROOT/archive/second.txt"
else
  fail 'bulk trash selected files' 'TRASH action or confirmation not found'
fi

printf '\nResult: %d passed, %d failed\n' "$passed" "$failed"
exit "$failed"
