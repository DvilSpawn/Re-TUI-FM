# Reproducible Play screenshots

All captures use the current debug APK and synthetic files under `/sdcard/Download/ReTUI Demo`. No personal files, accounts, device frames, or third-party screens belong in the set.

Capture the phone at `1080x1920`. Capture both tablet sets in portrait at a 9:16 resolution of at least 1080 pixels on the short edge, with a tablet display density that keeps `smallestScreenWidthDp` at 600 or higher.

Use these states in the order recorded by `play-store/listing/en-US/screenshots.json`:

- Phone: Home; dual-pane browser; filtered search; active transfer; open ZIP archive.
- Each tablet: Home; browser; selection/transfer controls; search or open archive.

Save direct captures in a temporary root containing `phone/`, `7-inch-tablet/`, and `10-inch-tablet/`. Use the final filenames from the metadata file. Then run:

```sh
python3 scripts/finalize_play_screenshots.py /path/to/raw-captures
python3 scripts/validate_play_assets.py
```

The finalizer converts every image to RGB. It adds a 168-pixel caption band to phone images only, which occupies 8.75% of the image. Tablet captures remain direct app captures without added text.
