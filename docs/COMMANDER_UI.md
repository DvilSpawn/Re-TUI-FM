# Commander-style touch interface

The existing Re:TUI Files backend remains responsible for file operations. The UI takes its layout and menu organization from Midnight Commander's documented interface: https://github.com/MidnightCommander/mc/blob/master/doc/man/mc.1.in.

Below 600 dp of screen width (phones and narrow floating windows), FM uses a compact layout instead: Home, then a single two-line file list with a tappable path bar and a bottom action bar (Browse/Places/Search/More on Home; Places/Search/Sort/New/More in folders). The action bar chips share one width and fill the bar when the window is too narrow for them. The two-pane description below applies to wider screens.

- Left lists directories and their parent; saved and recent locations are available through Places. The first tap selects a directory; the next tap on the same directory opens its contents on the right. Selecting another row or using keyboard navigation changes that selection. Enter opens the current directory immediately.
- Right uses a scrolling terminal-style file list. Tapping opens an item; long-press marks it for batch operations. Marked items have an asterisk, independently of the cursor highlight.
- Places, File, Command, Options and Sort organize existing Android actions. Places provides common/saved folders, providers, recent folders and folder-browser navigation; Sort directly controls the right-hand listing. Unix-only MC actions are omitted.
- File lists show name, size and modification time in separate columns, with Modified hidden when the right pane is narrower than 240 dp.
- The numbered F1–F10 bar uses two rows of 32 dp touch buttons. Copy/move retain FM's existing mark, choose destination and confirm workflow, with Paste/Move here replacing the corresponding button while an operation is pending. Trash retains confirmation and recovery.
- The command line opens FM's existing guarded file-command palette. The selection toolbar has a fixed clear/cancel button while the action list scrolls.
- By default FM uses the Launcher colors it receives, run through a WCAG readability pass (`Readability.kt`) so text and selection stay legible. Re:TUI gray (the gray, gold and teal MC palette) and the MC skins are optional. Explicit Files color/font overrides, Launcher font selection, font scale and native Android text/accessibility semantics remain supported. The duplicate icon toolbar and path bar are removed; paths are in the panel headings and tapping the right-pane path opens breadcrumbs.

## Launcher terminal renderer

`app/src/main/java/com/dvil/retui/fm/terminal/TerminalGridView.kt` is adapted from Re:TUI Launcher's `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TerminalGridView.kt`, as present at Launcher commit `529c2e3` plus its current working tree, on 2026-10-13. The Launcher checkout is not modified.

Source project: https://github.com/DvilSpawn/Re-TUI

The renderer retains the fixed character cells, glyph fitting, Unicode/DEC line drawing and ANSI styling. FM supplies resolved colors instead of depending on the Launcher's settings classes. `TerminalLabelView` uses the renderer for visible text while preserving Android's native text layout, click targets and accessibility text. File names are displayed as plain text, with terminal control characters neutralized before rendering.

The copied renderer is covered by the GNU GPL v3 text in `LICENSES/RetuiTerminalRenderer.txt`. The MC skin assets described below are included; MC implementation code is not imported.


## Midnight Commander skin collection

Appearance → MC skins bundles all 41 `.ini` skins listed in upstream `misc/skins/Makefile.am` at commit `32e3114bfbad57794dc6752488eb02fb9b058452` (2026-10-13 snapshot):
https://github.com/MidnightCommander/mc/tree/32e3114bfbad57794dc6752488eb02fb9b058452/misc/skins

The files in `app/src/main/assets/mc-skins` are unchanged upstream files, including their author credits. Upstream's skin-format README and GNU GPL v3 license are bundled alongside them. License text is also in `LICENSES/MidnightCommander.txt`.

The picker previews a sample without changing the live file panes. Apply saves the skin ID and replaces local color overrides, while preserving navigation, marks, pending copy/move state and scroll. Cancel preserves appearance and browsing state. Individual color edits still create a custom Files palette; choosing a skin restores its original role colors. The picker lists Launcher colors first, then Re:TUI gray, then the MC skins.

`CommanderSkin` resolves per-component section/core fallbacks, aliases, ANSI names, indexed colors, RGB cubes, gray ramps, and short/full true colors. All 41 skins and every upstream color role are covered by parser tests. FM uses the pane/frame/header, directory and common file-type, marked/markselect, selection, menu, dialog/input, command line and function-button roles. Bold, italic, underline and reverse attributes are honored. Single/double heavy frames are adapted to native rectangular touch containers; MC's widget-specific glyphs, unused editor/diff/viewer roles and blinking are retained in the source assets but do not add MC widgets to FM.

As in terminals, the first 16 colors depend on the palette; FM uses the standard xterm palette. Terminal `default` resolves to an opaque black background and light-gray foreground, keeping Android-owned pane forms readable. `root` skin variants are visual choices and do not grant root access.


## Resizable wallpaper-hosted FM

Launcher `files` invocation still opens `FloatingFilesActivity` through the same `OPEN_CONSOLE` intent contract. It now hosts an internal floating panel above the current Android wallpaper, following the Launcher Help/Calculator panel pattern rather than resizing an Android dialog window. Standalone FM retains its full-screen workspace with wallpaper visible around its margins. MC panes/forms stay opaque, global dimming is removed, and CRT effects are confined to the workspace.

Four 48 dp corner targets sit in a 24 dp transparent gutter. Drag a corner to resize with its opposite corner fixed. Tap grows by 32 dp; long press shrinks by 32 dp. Content targets have priority where they overlap a grip. Drag the menu bar after the Android touch threshold to move; a tap still opens the selected menu.

Bounds are stored as normalized fractions in Files preferences and instance state. Defaults are centered 92% × 82%, capped at 720 × 760 dp. The minimum outer rectangle is 320 × 420 dp, capped to the available safe area. Rotation and IME insets adjust the displayed rectangle; automatic inset changes do not write preferences. Theme changes reuse the remembered rectangle.

Resizing adjusts existing native views in place. The Modified column hides below 240 dp of right-list width and returns when widened; date sorting remains in the Sort menu. List position, marks, pending copy/move state, and form input are retained. Opening Appearance from a form suspends that form; applying or canceling a skin/color choice returns to the same input and working controls.

Both launch modes wrap the MC workspace in a Launcher-style outer panel, with a `RE:TUI FM` title tab and 48 dp close/help targets at the upper right. The title bar and MC menu bar both move the floating window. The visible gap below the title tabs is 8 dp. Corner glyphs use 22% opacity while retaining their full touch targets. The wrapper uses the shared panel background/border and header colors, output corner radius, cyberdeck outline and dashed-border settings. An accepted Launcher frame asset replaces the outline, with its declared edge widths reserved around the content. MC skins only affect the inner workspace; the outer frame keeps its Launcher identity. Corner targets stay outside the frame, and CRT effects remain inside it.


The status bar is hidden in both floating and fullscreen modes. The floating header has a fullscreen/restore button to the left of Close. Fullscreen fills the safe screen area, hides corner grips, and disables dragging. It reuses the same workspace views, including open forms and selection/transfer state. Restoring returns to the saved floating rectangle; fullscreen and temporary IME bounds never overwrite it. Fullscreen choice is remembered across reopening, rotation and appearance changes.


Saved Android provider tree grants are resolved to their root document before metadata, capabilities, listing and root creation requests. Unavailable metadata falls back to a generic provider name instead of crashing Places. Saved grants/bookmarks are retained.

The floating host applies system safe-area and configured display margins once. It ignores the legacy `top_margin` panel offset because its header and outer gutter already reserve chrome space. The remembered bounds and keyboard geometry use the same available-area calculation.

## Progress checkpoint and validation

The 2026-10-26 source checkpoint passed `testPlaystoreDebugUnitTest assemblePlaystoreRelease`, with 49 unit tests and zero failures in the test results. The journey script passes `bash -n`; the source diff passes `git diff --cached --check` with `app/src/main/assets/mc-skins` excluded. The unchanged upstream README and `mashdark256.ini` each retain an extra blank line at EOF, which Git reports as whitespace warnings. Version remains 0.1.24 (24).

Physical I2220 phone checks from 2026-10-13 covered corner anchoring, minimum/clamped geometry, moving and menu taps, remembered bounds, rotation, keyboard/form retention, marks and pending transfers, theme changes, responsive columns, fullscreen/restore, hidden status bar, Places/Sort, and the previously crashing saved-provider listing. The last top-spacing check verified removal of the extra 18 dp offset while preserving the 8 dp title-to-menu gap. These device checks were not rerun for the GitHub checkpoint.

Known limits: a warm incoming `OPEN_CONSOLE` request sometimes retained the previous folder; this remains unresolved. Arbitrary custom Launcher frame assets, provider write operations after the URI fix, and legacy Android 8–10 keyboard handling have not been separately verified on-device. Local screenshots, APKs and generated reports are excluded from the source commit.
