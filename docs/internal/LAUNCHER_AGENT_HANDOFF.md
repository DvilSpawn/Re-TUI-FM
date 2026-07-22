# Launcher agent handoff: FM parity

## Current FM state

FM now accepts:

- `action=search` plus `search_name`, optional `search_type`, and optional `path`.
- `action=open` plus `path`.
- No action, optionally with `path`, to open the normal FM surface.

FM long-press selection now owns bulk Copy, Move, Trash, Share, and ZIP. These are deliberately not Launcher commands.

## Required Launcher changes

Work in `app/src/main/java/ohi/andre/consolelauncher/commands/main/raw/files.kt`.

1. Replace `parseSearch()` with a small operation parser:
   - no args -> open FM;
   - `-search <name> [type]` -> structured search extras;
   - `-open <path>` -> resolve and send an absolute path;
   - anything else -> legacy search alias for compatibility.
2. Continue using `CommandAbstraction.PLAIN_TEXT` so quoted and multi-word paths survive.
3. Continue setting the explicit FM package/action and applying `RetuiThemeBridge` extras.
4. Do not send the raw `command` extra for new operations.
5. Update `help_files` in `app/src/main/res/values/strings.xml` to show the new forms.
6. Add parser tests for missing arguments, quoted paths, relative paths, search types, and legacy search.

## Suggested parser result

Keep it small:

```kotlin
sealed interface FilesRequest {
    data object Home : FilesRequest
    data class Search(val name: String, val type: String?) : FilesRequest
    data class Open(val path: String) : FilesRequest
}
```

If a sealed type adds more code than the parser needs, an operation string plus fields is acceptable. The important boundary is structured intent extras, not the local representation.

## Do not change

- `com.dvil.retui.fm.OPEN_CONSOLE`
- `com.dvil.retui.fm`
- `RetuiThemeBridge.putLauncherThemeExtras()`
- Full trailing-text preservation through `PLAIN_TEXT`

## Verification

Run Launcher parser/unit checks, then verify against an installed FM:

```text
files
files -search note txt
files -open Download
files -open "Download/My Folder"
files note txt
```

Expected: all five open FM; searches show matching results; open requests land in the resolved folder; theme parity remains unchanged.
