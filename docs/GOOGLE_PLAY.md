# Google Play submission

## Distribution boundary

- Upload only `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab` to Google Play.
- Publish only `app/build/outputs/apk/github/release/app-github-release.apk` on GitHub.
- Keep the application ID `com.dvil.retui.fm` and Play version code monotonic.
- Configure the Play listing as paid in Play Console. There is no in-app purchase or code-level feature lock.
- Use Google Play App Signing. The local release key signs the GitHub APK and acts only as the upload key for the Play AAB; Google signs customer-delivered Play APKs.
- Do not promise in-place switching between channels. Different installed certificates require uninstalling one channel before installing the other.

### Signing certificates

- **GitHub APK and Play upload key:** `F1:F5:0B:57:32:86:BF:BD:59:B8:BF:BB:39:D2:02:EA:3A:DF:17:3F:12:07:7D:98:11:0B:B9:ED:98:1B:93:F2`
- **Google Play app-signing key:** `73:0E:1D:CC:20:0C:63:06:41:17:27:70:54:32:4A:6D:65:A5:F0:12:04:AA:40:E5:B0:0E:22:AA:B6:7C:D0:6D`

The Google Play fingerprint identifies Google's app-signing certificate; it is not a private key and cannot sign the local AAB. Build the Play AAB with the registered upload key. After upload, Google Play signs the APKs delivered to customers with the Play app-signing key above. GitHub releases continue to use the existing local release key.

## All files access declaration

**Core purpose:** Re:T-UI Files is a file manager. Its primary interface browses folders and files across shared storage and lets the user search, preview, edit, create, rename, copy, move, archive, share, trash, restore, and permanently delete their own files.

**Why access is required:** These operations must work across folders and file types without asking the user to select each item or directory separately. Restricting the app to its private directory, MediaStore categories, or individual Storage Access Framework selections would prevent its core file-manager navigation and multi-file operations from working.

**User control:** Android's All files access screen grants or denies access. File operations happen only after the user navigates to a file or explicitly runs an operation. Destructive actions use confirmation and the app's trash flow where applicable.

## Request install packages declaration

**Core purpose connection:** APK files are a supported file type in the file manager. When a user explicitly opens a local APK, Re:T-UI Files hands that file to Android and lets the user choose an available package installer.

**Behavior:** The app does not silently install packages, download packages for self-update, or install anything in the background. Installation begins only from the user's Open action and remains controlled by Android and the installer selected by the user.

## Data safety answers

- Data collected: none.
- Data shared by the developer: none.
- Third-party analytics: none.
- Automatic crash reporting: none.
- Data processed locally: file names, paths, contents, favorites, recent folders, theme preferences, and trash metadata needed for user-requested file operations.
- User-initiated sharing: selected files may be sent to another app through Android's share/open surfaces. This is not developer collection or sharing.
- Data deletion: users can delete app data through Android settings. Files created or managed outside app storage remain under user control and are not automatically removed with the app.

Re-check these answers before every submission if analytics, networking, cloud backup, accounts, advertising, or external SDKs are added.

## Listing disclosure

The store description and screenshots must prominently show that Re:T-UI Files is a general-purpose file manager. Mention broad file navigation and user-initiated APK opening so the restricted permissions match the advertised core functionality.

## Console-only gates

- Publish `docs/privacy.html` at a stable public HTTPS URL and enter that URL in Play Console.
- Complete the All files access and Request install packages declaration forms using the text above.
- Set the app price before first production availability; Google Play may not allow a previously free app to become paid.
- Complete content rating, target audience, ads, app access, Data safety, and store listing forms.
- App access: no account, login, membership, or special instructions are required.
- Ads: no, unless advertising is added later.
- Target audience: select the actual intended adult/general productivity audience; the app is not designed for children.
- Upload to an internal test track first and review Play's pre-launch and policy reports.

## Readiness check — v0.1.17 (2026-09-13)

Technical packaging checks passed; production submission readiness is not yet confirmed.

- Package `com.dvil.retui.fm`, version code 18, version name 0.1.17; min SDK 26, target SDK 36. Confirm code 18 exceeds every version already uploaded to Play Console.
- GitHub and Play debug unit tests: 20 each, all passed. Release assembly, Play bundle, and Play release lint passed (0 errors, 7 warnings).
- APK signature and AAB signature verified against the local certificate above; Google bundletool validation passed. Play Console upload-certificate registration remains unverified.
- No native `.so` libraries are packaged, so there is no native 16 KB alignment remediation.
- Final Play manifest has no Internet permission or broad photo/video/audio read permissions; backup and device transfer are disabled.
- Lint warnings cover restricted storage, an older dependency, launcher icon shape/location, two untranslated strings, and the exported Launcher metadata provider. The provider checks the calling UID's package against the Launcher package and exposes read-only queries.
- Added the missing `docs/privacy.html`. Publish it at a public HTTPS URL and enter that URL in Console; no hosted policy URL was verified.
- Still required or unverified in Console: restricted-permission declarations/approval, Data safety, content rating, audience, ads/app-access answers, pricing, listing assets, upload-key registration, and internal-track/pre-launch results. Existing screenshots were not revalidated against this release.
- No fresh device installation or runtime/visual acceptance test was performed for this release.

Policy references checked:
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [All files access](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en)
- [Package installation permission](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en)
- [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
