# Re:TUI-FM

Re:TUI-FM is a standalone Android file manager for Re:T-UI Launcher. It provides a terminal-style file browser with command input, path suggestions, guarded file operations, previews, favorites, and a Re:T-UI-themed cyberdeck interface.

## Why this exists

- Playstore has strict policies on what permissions a launcher can have, however, the original launcher had the ability to manage files which I wanted to keep. 
- This launcher is to act as a companion app for the launcher. They shared same UI elements and styles so users who access this app from the launcher dont feel like they opened up a whole different application. 
- This can also server as a leaning tool for TUI based file management with helpful hints shows right at the application. 

## Features

- Tree and list browsing with directory expansion.
- Command input for file operations.
- Path, command, flag, favorite, and trash suggestions.
- Bounded previews for text, Markdown, JSON, CSV, ZIP, directories, and images.
- Inline text editing for small text-like files.
- Guarded trash flow through `.retui-trash` before destructive deletion.
- Long-press multi-selection for batch trash, share, ZIP, copy, and move.
- Favorites and recent-directory navigation.
- Re:T-UI visual handoff through launch extras for colors, fonts, margins, wallpaper, and CRT/cyberdeck styling.
- IME, navigation bar, status bar, and display-cutout handling through AndroidX window insets.

## Launcher contract

Re:T-UI Launcher opens FM with `com.dvil.retui.fm.OPEN_CONSOLE` and structured extras. Launcher owns the public `files` command grammar; FM owns the file-operation UI and behavior.

```text
files
files -search <name> [type]
files -open <directory>
```

Intent extras:

- `action=search`, with `search_name` and optional `search_type`.
- `action=open`, with `path`.
- `path` may accompany search to select its root.

The older `command` extra remains temporarily supported for installed Launcher versions during migration, but it is not the forward contract.

## Multi-select

Long-press a file or folder to begin selection, tap more items to toggle them, then use the visible selection bar: `COPY`, `MOVE`, `TRASH`, `SHARE`, `ZIP`, or `X` to clear. Back also exits selection mode before navigating.

## Permissions

Re:TUI-FM is designed for local file navigation and requests broad storage access on Android where required:

- `MANAGE_EXTERNAL_STORAGE`
- legacy `READ_EXTERNAL_STORAGE` on older Android versions
- Android 13+ media read permissions for images, video, and audio

The `permission` command opens the relevant Android storage access settings.

## Building

Requirements:

- Android Studio or Android SDK command-line tools
- JDK compatible with the Android Gradle Plugin

Build a debug APK:

```bash
./gradlew assembleDebug
```

Build a release APK:

```bash
./gradlew assembleRelease
```

Release signing is optional and is read from `local.properties` when present:

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keystores and `local.properties` are ignored by git.

## Releases

GitHub releases publish a signed APK for sideload testing when local signing credentials are available. The app is currently versioned independently from the main Re:T-UI launcher.

## Repository Status

This is an early standalone companion app. The public repository intentionally excludes local signing credentials and generated build output.
