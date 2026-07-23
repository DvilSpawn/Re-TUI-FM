# FM user-journey test and implementation plan

Date: 2026-07-22
Device: Pixel 7 AVD, Android 15 / API 35

## Journeys selected

The test uses the recurring actions documented for Android file managers:

- Browse and search for files.
- Create folders.
- Copy and move files.
- Preview or open files.
- Delete with a recoverable/confirmed flow.
- Create archives.
- Save favorite locations and revisit recent locations.

Sources:

- [Files by Google help](https://support.google.com/files/answer/9848742?hl=en-GB) documents browse, search, rename, copy, move, share, and clean/delete workflows.
- [Android shared-storage guidance](https://developer.android.com/training/data-storage/shared) separates media, documents, and other shared files.
- [Android Storage Access Framework guidance](https://developer.android.com/training/data-storage/shared/documents-files) covers opening, creating, editing, deleting, and directory access while preserving user control.
- [Material Files](https://github.com/zhanghai/MaterialFiles) includes breadcrumbs, archive viewing/extraction/creation, and multiple storage backends as established file-manager capabilities.

## Automated result

Run:

```bash
ADB=/Users/mako/Library/Android/sdk/platform-tools/adb scripts/emulator_user_journeys.sh
```

Original result: **7 passed, 3 failed**. After implementing the structured Launcher contract, UI multi-select, navigable copy/paste, and anchored selection rendering, the expanded Pixel 7 suite is **22 passed, 0 failed**.

| Journey | Result | Evidence |
|---|---:|---|
| Create folder | Pass | `projects` created on emulator storage |
| Copy file | Pass | destination file created |
| Move/rename file | Pass | destination created and source removed |
| Preview text | Pass | preview displayed file contents |
| Search by name | Pass | result displayed `moved.txt` |
| Delete safely | Pass | confirmation shown before trashing |
| Structured Launcher search/open | Pass | `action=search` and `action=open` reach the correct FM surfaces |
| Multi-select | Pass | long-press selects the first item and taps toggle more items |
| Anchored multi-select | Pass | first and additional selections retain the item’s existing Y-position |
| Clear selection | Pass | closing the selection bar removes every selected highlight |
| Copy and paste by navigation | Pass | Copy becomes Paste, survives navigation, confirms the destination, and copies every selected file |
| Create ZIP archive | Pass | two selected files are written into a ZIP from the UI |
| Bulk move | Pass | both selected files move and remain intact |
| Bulk trash | Pass | both selected files leave the source and enter guarded trash flow |

## Shortcomings found

### Resolved - Public command contract ownership

Launcher now owns the public `files` grammar and FM accepts structured `action=search|open` intents. The old raw `command` extra remains only as a temporary compatibility path and is no longer documented as the public FM surface.

### Resolved - Recoverability remains in the FM UI

Deletion asks for confirmation and moves items into `.retui-trash`; Recently deleted exposes restore through item menus. Bulk mutation intentionally remains an FM UI responsibility rather than part of the Launcher command grammar.

### Resolved - Archive creation

Long-press multi-select now exposes ZIP creation alongside bulk copy, move, trash, and share. Archive inspection and safe extraction remain future enhancements.

### P1 - Favorites and recent navigation are inconsistent

The UI has custom Places and home tiles, while the documented terminal vocabulary uses `fav` and `recent`. These need one shared backing model so touch, keyboard, and launcher-intent entry points behave identically.

### P1 - Automated accessibility is fragile by construction

Most interactive views have visible text but no explicit content descriptions or stable IDs. Text-based UI inspection worked on the Pixel 7, but icon-only toolbar controls cannot be selected semantically. This also makes TalkBack use and durable UI automation harder.

### P2 - Android storage integration is narrow

The FM is optimized for direct local paths and all-files access. It does not expose Storage Access Framework providers, so cloud/document providers and user-granted directory trees are outside the current experience. This is a product limitation, not a blocker for a local companion FM.

## Implementation plan

### Phase 1 - Bring Launcher to the structured contract

1. Parse `files -search <name> [type]` and send `action=search` with search extras.
2. Parse `files -open <directory>`, resolve it against Launcher current-directory state, and send `action=open` with an absolute path.
3. Preserve the old `files <name> [type]` search shorthand for one compatibility window.

Acceptance: Launcher passes its parser tests and all open/search forms reach the expected FM surface without raw command forwarding.

### Phase 2 - Extend archive operations

1. Keep the completed multi-select ZIP creation covered by the Pixel 7 suite.
2. Add archive preview (entry names, compressed/uncompressed sizes) before extraction.
3. Add guarded extraction with zip-slip protection and collision handling.

Acceptance: create, inspect, and extract a nested fixture on the Pixel 7; reject an entry that escapes the chosen destination.

### Phase 3 - Unify navigation state

1. Back Favorites/Places with one persisted model and expose it to both menus and commands.
2. Record a small deduplicated recent-directory list whenever navigation succeeds.
3. Keep trash locations separately tracked so recent navigation never exposes `.retui-trash` as an ordinary working folder.

Acceptance: a place added by touch is reachable by `fav go`; a directory opened by command appears in `recent`; both survive app restart.

### Phase 4 - Accessibility and durable UI tests

1. Add content descriptions to icon-only Home, Parent, New folder, and Refresh buttons.
2. Give key interactive/output views stable resource IDs where text alone is ambiguous.
3. Extend the Pixel 7 suite with taps for folder navigation, toolbar actions, trash confirmation, and restore.

Acceptance: TalkBack announces every toolbar action, and tests locate controls semantically rather than by screen coordinates.

### Phase 5 - Decide the storage boundary explicitly

Keep direct-path/all-files access if this remains a local Re:T-UI companion. If broader Android/provider support becomes a goal, add Storage Access Framework roots as a separate provider-backed surface rather than forcing content URIs into the current `File`-based pane model.
