# Re:TUI-FM

Re:TUI-FM is a standalone Android file manager for Re:T-UI. It provides a terminal-style file browser with command input, path suggestions, guarded file operations, previews, favorites, and a Re:T-UI-themed cyberdeck interface.

## Features

- Tree and list browsing with directory expansion.
- Command input for file operations.
- Path, command, flag, favorite, and trash suggestions.
- Bounded previews for text, Markdown, JSON, CSV, ZIP, directories, and images.
- Inline text editing for small text-like files.
- Guarded trash flow through `.retui-trash` before destructive deletion.
- Selection commands for batch trash, share, zip, copy, and move.
- Favorites and recent-directory navigation.
- Re:T-UI visual handoff through launch extras for colors, fonts, margins, wallpaper, and CRT/cyberdeck styling.
- IME, navigation bar, status bar, and display-cutout handling through AndroidX window insets.

## Commands

Core commands:

```text
help
cd [folder]
cd ..
ls
pwd
tree [-a -d -f -h -s -D -F -i -r --dirsfirst --ignore-case --noreport -L n -P pattern -I pattern --sort name|size|mtime]
find [path] -name [pattern] [-x|-a] [--type image|video|audio|doc|dir|file] [--size +100M]
search [pattern]
filter [pattern]
preview [file]
peek [file]
edit [text file]
open [file]
share [file]
mkdir [folder]
cp [-r] [source] [destination]
mv [source] [destination]
rm [-r] [file]
rm --permanent [file]
trash [file]
restore [label|all]
zip -r [archive.zip] [folder]
sel add|rm|list|clear|trash|share|zip|cp|mv
recent
back
fav here
fav add [label] [path]
fav go [label]
fav rm [label]
fav rename [old] [new]
fav list
permission
refresh
exit
```

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
