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

Supported type vocabulary includes file extensions and FM category names such as `image`, `video`, `audio`, `doc`, `apk`, `archive`, and `dir`.

### Open a directory

```text
files -open <directory>
```

Extras:

```text
action=open
path=<absolute resolved directory>
```

Launcher must resolve relative paths against `MainPack.currentDirectory` before sending the intent. Quoted paths with spaces must remain one path.

## Compatibility

During migration, retain the old shorthand:

```text
files <name> [type]
```

Treat it as a legacy alias for `files -search <name> [type]`. Do not remove it until the next Launcher compatibility window. FM temporarily still accepts the old `command` extra, but new Launcher work must use structured extras.

## Future actions

Reserve the same `action` extra for future navigation-only operations such as `recent`, `trash`, or `category`. Bulk file mutation stays inside FM selection mode and should not be added to Launcher commands without a separate safety design.

## Launcher acceptance checks

1. `files` opens FM home or the current directory.
2. `files -search invoice pdf` shows PDF matches for `invoice`.
3. `files -open Download` resolves and opens the directory.
4. `files -open "My Folder"` preserves spaces.
5. Legacy `files invoice pdf` still searches during migration.
6. Missing FM returns `Re:T-UI Files is not installed.`
7. Theme extras continue to accompany every request.
