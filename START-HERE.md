# Voiceish complete release 0.1.4

Use this release in place of every 0.1.0–0.1.3 download. The previous Android packages are obsolete. This complete ZIP includes both downloadable deliverables, a self-contained HTML export, all sources and build instructions. No previous download is needed.

## Included files

- **voiceish-setup-0.1.4.apk** — the one APK to install on your phone. Uses your organizational production key. It includes and installs the SIM companion through Android's user-confirmed installer; no ADB.
- **voiceish-sim-0.1.4.apk** — the companion payload, included for completeness. Start with Setup for the permission-aware installation flow.
- **voiceish-sim-chrome-0.2.2.zip** — ready-to-load extension. Unzip, open chrome://extensions, enable Developer mode, Load unpacked, choose the folder containing manifest.json. This extension has its own monotonically increasing version, 0.2.2.
- **Voiceish-0.1.4.html** — one self-contained file: embedded CSS, JavaScript, font, favicon, and both setup downloads. Opening it does not require companion web assets. Live texting still requires your phone and a permitted connection. PWA installation and the extension website bridge use the hosted HTTPS workspace, not a file:// document.
- **source/** — both Android projects, the complete extension source, original web HTML/CSS/JS/assets, tests, build/export/packaging scripts, and instructions.
- **SHA256SUMS.txt** — hashes of the exact included binaries and HTML.

The deep navy panels, cobalt message bubbles, and gold accents follow the Voiceish desktop/Android reference imported into this project. Applied to website, extension, Android companion and Setup.

## Install and pair

Install Setup on your Android phone, allow it to install apps, then approve the Voiceish SIM installation prompt. Open Voiceish SIM, allow SMS/phone access, start its bridge, and pair the extension or hosted workspace with its pairing text. Keep both devices on the same Wi-Fi. After successful setup, the installer may be removed; keep Voiceish SIM installed.

Both Android apps display 0.1.4 and have versionCode 5. Both use the existing production organizational certificate. The SMS permission recovery mechanism is unchanged; it still needs confirmation on the Samsung handset. Compilation/signature/package checks are not a real SIM round-trip test.

## Build from source

Read `source/docs/BUILD.md` for Java 17, Android SDK Platform 35 / Build Tools 35.0.0, and private Admin-Manual signing configuration. These tools and signing secrets are not included. No development-key fallback is used. From source/:

```sh
export ANDROID_SDK_ROOT=/your/android-sdk
export ADMIN_MANUAL_ROOT=/your/private/Admin-Manual
export VOICEISH_KEYSTORE=/your/private/Admin-Manual/CREDENTIALS/PlayStore/production.keystore
bash scripts/build-native.sh
bash scripts/build-setup.sh
python3 scripts/verify-setup-release.py
python3 scripts/package-extension.py
python3 scripts/export-standalone.py
python3 scripts/package-complete.py
```

The setup source includes the exact signed companion in `android/setup/src/main/assets/voiceish-sim.apk` and its hash pin. Deliberate payload changes require updating the version, pin and expected version together. Scripts refuse to overwrite existing release files. A changed published release must receive a new version and filename.

For the website, `source/web/dist/` contains the editable static app. Serve over HTTPS. Copy the built setup APK and extension ZIP there for the download links. If hosting on a new domain, explicitly add that domain to the extension manifest/content-script allowlist and bump its version. No hosted message relay is included. The independent HTML export can be regenerated using the Python script above without npm dependencies.
