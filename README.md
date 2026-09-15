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
- Copy/move progress in the right pane, cancellation, and per-transfer conflict choices: keep both, replace, or skip.
- Folder bookmarks through Places.
- Rename files/folders with collision protection; file/folder properties and recursive size.
- Select all/invert selection, name/date/size/type sorting, and a saved hidden-files toggle.
- App-owned forms and menus stay inside the right pane with opaque, contrast-checked surfaces; Back restores the browser.
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

Long-press a file or folder to begin selection, then tap more items to toggle them. `COPY` changes the bar to `PASTE`; navigate to the destination folder, tap `PASTE`, and confirm that directory. The other selection actions are `MOVE`, `TRASH`, `SHARE`, `ZIP`, and `X` to clear. Back exits active selection before navigating, while a pending copy remains available as you browse.

## Permissions

Re:TUI-FM is designed for local file navigation and requests broad storage access on Android where required:

- `MANAGE_EXTERNAL_STORAGE`
- `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` on Android 8–10, with legacy storage enabled on Android 10
- `REQUEST_INSTALL_PACKAGES` so a user can open a local APK and hand it to an installer they choose

Use **File > Storage access** to grant access or reopen Android settings after a permanent denial. Android 11+ still restricts protected folders even with All files access.

## Building

Requirements:

- Android Studio or Android SDK command-line tools
- JDK compatible with the Android Gradle Plugin

Build the free GitHub APK:

```bash
./gradlew assembleGithubRelease
```

Build the paid Google Play bundle:

```bash
./gradlew bundlePlaystoreRelease
```

Release signing is optional and is read from `local.properties` when present:

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keystores and `local.properties` are ignored by git.

## Distribution

- **GitHub:** free signed APK at `app/build/outputs/apk/github/release/app-github-release.apk`.
- **Google Play:** paid app bundle at `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`.

Both builds contain the same file-manager features. Price, licensing, and availability are managed by Google Play, not by a code-level feature lock. Never attach the Play Store `.aab` to a GitHub release.

The GitHub APK is signed locally. The Play AAB is signed with the upload key, then Google Play App Signing signs the APKs delivered to customers. Because the installed certificates differ, users cannot switch between GitHub and Play builds with an in-place update; they must uninstall one channel before installing the other.

The pinned channel fingerprints and release signing procedure are recorded in [`docs/GOOGLE_PLAY.md`](docs/GOOGLE_PLAY.md). GitHub releases keep the existing local release signature; Google Play customer installs use the Play app-signing certificate.

Play Console declarations and privacy copy are maintained in [`docs/GOOGLE_PLAY.md`](docs/GOOGLE_PLAY.md) and [`docs/privacy.html`](docs/privacy.html).

## Repository Status

This is an early standalone companion app. The public repository intentionally excludes local signing credentials and generated build output.
