# Re:TUI-FM

Re:TUI-FM is a standalone Android file manager for Re:T-UI Launcher. It provides a terminal-style file browser with command input, path suggestions, guarded file operations, previews, favorites, and a Re:T-UI-themed cyberdeck interface.

## Why this exists

- Playstore has strict policies on what permissions a launcher can have, however, the original launcher had the ability to manage files which I wanted to keep. 
- This launcher is to act as a companion app for the launcher. They shared same UI elements and styles so users who access this app from the launcher dont feel like they opened up a whole different application. 
- This can also server as a leaning tool for TUI based file management with helpful hints shows right at the application. 

## Features

- Home page with search, file categories, recent folders, storage roots and Recently deleted.
- Phones and narrow windows use one file list with a tappable path bar and a bottom action bar; wide screens keep the two-pane Midnight Commander layout.
- Two-step setup inside the FM window: storage access, then a quick start.
- Floating preview window for images, video, audio, PDFs and text (line numbers, wrap toggle). Tap the preview to choose an app.
- Archives (ZIP, JAR, RAR, 7z, TAR, tar.gz/bz2/xz) open as folders; entries can be previewed without extracting, and extraction asks where to put them.
- Search by name, wildcard or type, with `size>10M`, `size<500K`, `newer>7d` and `older>12h` filters.
- Inline text editing for small text-like files.
- Guarded trash flow through `.retui-trash`, with restore and optional automatic cleanup.
- Long-press multi-selection for batch copy, move, trash, share, ZIP and extract.
- Copy/move progress, cancellation, and per-transfer conflict choices: keep both, replace, or skip.
- Folder bookmarks through Places; rename with collision protection; properties and recursive folder size.
- Name/date/size/type sorting and a saved hidden-files toggle.
- A guarded `$` command line (`help` lists the commands); shell syntax is rejected.
- Launcher colors by default, adjusted for readable contrast; Re:TUI gray and 41 bundled MC skins are optional.
- Re:T-UI visual handoff through launch extras for colors, fonts, margins, wallpaper, frames and CRT styling.
- IME, navigation bar, status bar, and display-cutout handling through AndroidX window insets.

## Launcher contract

Re:T-UI Launcher opens FM with `com.dvil.retui.fm.OPEN_CONSOLE` and structured extras. Launcher owns the public `files` command grammar; FM owns the file-operation UI and behavior. See [`docs/launcher/FM_CONTRACT.md`](docs/launcher/FM_CONTRACT.md).

```text
files
files -open <path>
files -search <name> [type]
files -ls
files -share [file]
files -cd <directory>
```

Intent extras:

- `action=open`, with `path` and/or `target`. Folders open; files open with Android.
- `action=search`, with `search_name`, optional `search_type`, and optional `path` as the search root.
- `action=ls`, with `path`, opens that folder.
- `action=share`, with `target`; without `target`, FM starts a selection to share.

`files -cd` only changes Launcher's remembered directory and does not start FM. The old raw `command` extra and the `files <name> [type]` shorthand are no longer supported.

## Multi-select

Long-press a file or folder to begin selection, then tap more items to toggle them. `COPY` changes the bar to `PASTE`; navigate to the destination folder, tap `PASTE`, and confirm that directory. The other selection actions are `MOVE`, `TRASH`, `SHARE`, `ZIP`, `EXTRACT` for archives, and `X` to clear. Back exits active selection before navigating, while a pending copy remains available as you browse.

## Permissions

Re:TUI-FM is designed for local file navigation and requests broad storage access on Android where required:

- `MANAGE_EXTERNAL_STORAGE`
- `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` on Android 8–10, with legacy storage enabled on Android 10
- `REQUEST_INSTALL_PACKAGES` so a user can open a local APK and hand it to an installer they choose

Setup asks for access on first run. Afterwards, use **More > File > Storage access** (**File > Storage access** in the two-pane layout) to grant access or reopen Android settings after a permanent denial. Android 11+ still restricts protected folders even with All files access.

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
