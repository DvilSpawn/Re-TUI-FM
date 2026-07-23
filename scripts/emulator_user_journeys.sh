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
  sleep 2
}

launch_action() {
  local action=$1 path=${2:-$ROOT}
  adb shell am force-stop "$PACKAGE" >/dev/null
  adb shell am start -W -a "$ACTION" -p "$PACKAGE" \
    --es action "$action" --es path "$path" >/dev/null
  sleep 2
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

item_top() {
  local expected=$1 xml
  xml=$(ui_text)
  if [[ $xml =~ text=\"$expected\"[^\>]*bounds=\"\[[0-9]+,([0-9]+)\] ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

item_selected() {
  local expected=$1 state=$2 xml
  xml=$(ui_text)
  [[ $xml =~ text=\"$expected\"[^\>]*selected=\"$state\" ]]
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
adb shell mkdir -p "$ROOT/scroll"
printf 'hello from RETUI FM\n' | adb shell sh -c "'cat > $ROOT/inbox/note.txt'"
printf 'second selected file\n' | adb shell sh -c "'cat > $ROOT/inbox/second.txt'"
adb push app/build/outputs/apk/debug/app-debug.apk "$ROOT/return-test.apk" >/dev/null
adb shell content delete --uri content://media/external/file \
  --where "_data='$ROOT/return-test.apk'" >/dev/null 2>&1 || true
adb shell content insert --uri content://media/external/file \
  --bind "_data:s:$ROOT/return-test.apk" \
  --bind mime_type:s:application/vnd.android.package-archive \
  --bind _display_name:s:return-test.apk >/dev/null
adb shell sh -c "'i=1; while [ \$i -le 24 ]; do touch $ROOT/scroll/item-\$(printf %02d \$i).txt; i=\$((i + 1)); done'"

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
sleep 2
expect_ui 'search by file name' 'moved.txt'

launch 'rm archive/moved.txt'
expect_ui 'deletion asks for confirmation' 'Delete moved.txt?'

launch_action open "$ROOT/inbox"
expect_ui 'structured open action opens a directory' 'note.txt'

adb shell am force-stop "$PACKAGE" >/dev/null
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 2
if tap_text APKs; then
  sleep 3
fi
if [[ $(ui_text) == *return-test.apk* ]]; then
  adb shell am start -a android.settings.SETTINGS >/dev/null
  sleep 1
  adb shell am force-stop com.android.settings
  sleep 2
  expect_ui 'returning from Android opener keeps the APK category' 'return-test.apk'
else
  fail 'returning from Android opener keeps the APK category' 'could not open the APK category item'
fi

launch_action open "$ROOT/inbox"
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

launch_action open "$ROOT/scroll"
before=$(item_top item-10.txt || true)
if long_press_text item-10.txt; then
  after_first=$(item_top item-10.txt || true)
  if [[ -z "$before" || "$before" != "$after_first" ]]; then
    fail 'first selection keeps the right pane anchored' "item moved from y=$before to y=$after_first"
  else
    pass 'first selection keeps the right pane anchored'
  fi
  if tap_text item-11.txt; then
    after=$(item_top item-10.txt || true)
    if [[ -n "$after_first" && "$after_first" == "$after" ]]; then
      pass 'additional selection keeps the right pane anchored'
    else
      fail 'additional selection keeps the right pane anchored' "item moved from y=$after_first to y=$after"
    fi
    if tap_text X && item_selected item-10.txt false && item_selected item-11.txt false; then
      pass 'closing selection clears every item highlight'
    else
      fail 'closing selection clears every item highlight' 'an item remained visually selected'
    fi
  else
    fail 'additional selection keeps the right pane anchored' 'second visible item not found'
  fi
else
  fail 'additional selection keeps the right pane anchored' 'first visible item not found'
fi

launch_action open "$ROOT/inbox"
long_press_text note.txt && tap_text second.txt
if tap_text COPY; then
  expect_ui 'copy changes the action to paste' 'PASTE'
  adb shell input keyevent BACK
  sleep 1
  if tap_text projects; then
    expect_ui 'pending copy survives folder navigation' 'PASTE'
    if tap_text PASTE && tap_text PASTE; then
      sleep 2
      expect_file 'paste copies the first selected file' "$ROOT/projects/note.txt"
      expect_file 'paste copies every selected file' "$ROOT/projects/second.txt"
    else
      fail 'paste selected files' 'PASTE action or confirmation not found'
    fi
  else
    fail 'pending copy survives folder navigation' 'destination folder not found'
  fi
else
  fail 'copy changes the action to paste' 'COPY action not found'
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
