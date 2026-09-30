# Complete Voiceish source release 0.1.3

This archive supersedes every prior source download, including 0.1.2. Use the 0.1.3 release set. It has no dependency on any 0.1.0 or 0.1.1 download.

- `android/app/`: Voiceish SIM companion 0.1.3, Android versionCode 4.
- `android/setup/`: Voiceish Setup installer 0.1.3, Android versionCode 4, including the exact signed companion 0.1.3 payload and hash pin.
- `extension/`: complete Chrome extension source, unchanged extension version 0.2.1. Load this folder unpacked in Chrome.
- `web/`: complete current website/PWA source, static assets, semantic design documentation and tests. Serve `web/dist/` over HTTPS for installation as a PWA. Copy the newly built `dist/voiceish-setup-0.1.3.apk` and `dist/voiceish-sim-0.1.3.apk` into `web/dist/` when hosting its download links. The extension download can be created from the `extension/` folder with `manifest.json` at the ZIP root.
- `scripts/`, `tests/`, `docs/`: native build, release verification, packaging, protocol, test sources and installation instructions.

## Build both APKs

Provide Java 17, Android SDK Platform 35 / Build Tools 35.0.0, and the existing private organizational signing credentials described in `docs/BUILD.md`. Those development tools and signing secrets are not bundled.

```sh
export ANDROID_SDK_ROOT=/your/android-sdk
export ADMIN_MANUAL_ROOT=/your/private/Admin-Manual
export VOICEISH_KEYSTORE=/your/private/Admin-Manual/CREDENTIALS/PlayStore/production.keystore
bash scripts/build-native.sh
bash scripts/build-setup.sh
python3 scripts/verify-setup-release.py
python3 scripts/package.py
```

No previous release archive is needed. Setup uses the exact included companion payload; it does not fetch another version. A deliberate companion payload change requires updating its version, pin, expectations and release metadata together. Build outputs are protected from accidental replacement.

## Install

Install `voiceish-setup-0.1.3.apk` on the phone, allow it to install apps, approve its installation/update prompt, then open Voiceish SIM and allow SMS/phone access. Matching production-signed installed apps update in place. No ADB is part of the installation flow. See `docs/PHONE-SETUP.md` for details and remaining handset checks.
