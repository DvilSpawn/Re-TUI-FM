# Performance baseline

Measurements use the same Pixel 7 Android 15 emulator, the GitHub debug variant, granted storage access, and five force-stopped cold launches. Folder fixtures are synthetic and live under `/sdcard/Download/retui-fm-performance`.

| Build | Median cold start | Open 1,000 entries | Open 5,000 entries | Total PSS after 5,000 |
|---|---:|---:|---:|---:|
| `418ab5a` before walkthrough | 1,504 ms | 1,567 ms | 3,044 ms | 72,282 KB |
| Working tree with walkthrough | 889 ms | 1,267 ms | 1,755 ms | 70,861 KB |

The changed build has no greater-than-15% launch-time or memory regression in this run. The runs were sequential on one emulator, so absolute timings include emulator and runtime warm-up effects; the blocker rule remains a same-device comparison under the same setup.

Run `SERIAL=<device> scripts/performance_baseline.sh` for each release candidate. Also exercise a large copy/move, cancellation, an existing destination with each conflict choice, a low-storage failure, background and return, and removable-media disconnection. Record those device-specific results beside the release test notes rather than treating emulator results as physical-device proof.
