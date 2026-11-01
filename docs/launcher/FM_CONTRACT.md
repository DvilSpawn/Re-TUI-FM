# Launcher to FM contract

This is the Git-facing integration contract for Re:T-UI Launcher and Re:T-UI FM.

## Ownership

- Launcher parses the public `files` command.
- FM receives structured intent extras and performs the UI action.
- FM owns file validation, selection state, confirmation, errors, and filesystem operations.
- Do not forward new user-entered commands through the legacy `command` extra.

## Intent

```text
action: com.dvil.retui.fm.OPEN_CONSOLE
package: com.dvil.retui.fm
```

### Open FM

```text
files
```

No operation extras are required. Launcher may include its current directory as `path`.

### Search

```text
files -search <name> [type]
```

Extras:

```text
action=search
search_name=<name>
search_type=<optional type>
path=<optional search root>
```

Supported type vocabulary: any extension (`pdf` or `.pdf`), plus `image`, `video`, `audio`, `doc`, `apk`, `archive`, and `dir`/`folder` (with plurals and synonyms such as `photo`, `music`, `document`).

### Open a file or directory

```text
files -open <path>
```

Extras:

```text
action=open
target=<path as typed>
path=<launcher current directory>
```

FM resolves `target` against `path`. Directories open in the browser; files open with Android. Quoted paths with spaces must remain one path.

### List a directory

```text
files -ls
```

Extras: `action=ls` and `path=<launcher current directory>`. FM opens that folder.

### Share

```text
files -share [file]
```

Extras: `action=share`, optional `target`, and `path`. With a target, FM shares that file. Without one, FM starts a selection so the user picks what to share.

### Change directory

```text
files -cd <directory>
```

Launcher-only: it updates `MainPack.currentDirectory` through the companion provider and does not start FM.

## Compatibility

The `files <name> [type]` shorthand has been retired; Launcher now reports an unknown option. FM no longer reads the raw `command` extra.

## Future actions

Reserve the same `action` extra for future navigation-only operations such as `recent`, `trash`, or `category`. Bulk file mutation stays inside FM selection mode and should not be added to Launcher commands without a separate safety design.

## Launcher acceptance checks

1. `files` opens FM home or the current directory.
2. `files -search invoice pdf` shows PDF matches for `invoice`.
3. `files -open Download` resolves and opens the directory; `files -open notes.txt` opens the file.
4. `files -open "My Folder"` preserves spaces.
5. `files -ls` opens the Launcher's current directory in FM.
6. `files -share notes.txt` shares that file; `files -share` starts a share selection.
7. Missing FM returns `Re:T-UI Files is not installed.`
8. Theme extras continue to accompany every request.
