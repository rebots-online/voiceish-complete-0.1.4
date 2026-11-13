# Voiceish SIM — complete release 0.1.4

Text from your Android phone's primary SMS SIM, from desktop Chrome or a hosted web workspace, over your own local Wi-Fi. No online relay, no account, no subscription. Your carrier's normal SMS charges apply.

This repository is the **complete 0.1.4 release bundle**: every downloadable deliverable, a self-contained HTML export, and all sources with build instructions. It replaces every 0.1.0–0.1.3 download.

## What is in this repository

| Path | What it is |
| --- | --- |
| `voiceish-setup-0.1.4.apk` | Phone-only installer — the one APK to install first. Installs the SIM companion through Android's user-confirmed installer; no ADB. |
| `voiceish-sim-0.1.4.apk` | The Android companion (the bridge). Included for completeness; start with Setup. |
| `voiceish-sim-chrome-0.2.2.zip` | Unpacked Chrome extension (MV3), ready to load via `chrome://extensions` → Developer mode → Load unpacked. |
| `Voiceish-0.1.4.html` | One self-contained HTML export (embedded CSS/JS/font/favicon + both setup downloads). Live texting still requires the phone bridge. |
| `SHA256SUMS.txt` | SHA-256 hashes of the exact included binaries and HTML. |
| `START-HERE.md` | Release notes and install walkthrough. |
| `source/` | Both Android Gradle projects, extension source, web app, tests, and build/packaging scripts. |
| `DOCS/ARCHITECTURE.md` | Architecture snapshot of the finished product — components, protocol v1, closed sets, and the full entity enumeration table. |
| `CHECKLIST.md` | Task recipe behind the snapshot — self-contained tasks with entity attestations and acceptance criteria. |

## Quick start

1. **Phone** — install `voiceish-setup-0.1.4.apk`, allow it to install apps, then approve the Voiceish SIM installation prompt. See `source/docs/PHONE-SETUP.md`.
2. **Companion** — open Voiceish SIM, grant SMS/phone access, start the bridge, and copy the pairing text. In Android's SIM settings, keep your intended SIM selected for SMS (not "Ask every time").
3. **Desktop** — unzip the extension ZIP, load it unpacked in Chrome, paste the pairing text (treat it as a password), and text. Enter sends; Shift+Enter adds a line.

Both devices must be on the same local network. Keep the bridge running while texting; after a phone reboot, start the bridge again.

## Documentation map

| Document | Contents |
| --- | --- |
| [`START-HERE.md`](START-HERE.md) | Release notes, included files, pairing walkthrough. |
| [`DOCS/ARCHITECTURE.md`](DOCS/ARCHITECTURE.md) | System architecture, wire protocol v1, data stores, security model, entity table. |
| [`CHECKLIST.md`](CHECKLIST.md) | Build-out recipe: tasks, entity attestations, acceptance, outstanding handset verification. |
| `source/docs/PROTOCOL.md` | The phone↔browser API, byte-for-byte. |
| `source/docs/BUILD.md` | Toolchain versions, organizational signing, build commands. |
| `source/docs/PHONE-SETUP.md` | No-ADB installation flow and handset checks. |
| `source/docs/VERIFICATION.md` | Checks performed for this release and what remains outstanding. |

## Build from source

Requirements: Java 17, Android SDK Platform 35 / Build Tools 35.0.0, Python 3, Node 22+ (tests only), and the organizational signing key from the private Admin-Manual checkout. No development-key fallback exists. From `source/`:

```sh
export ANDROID_SDK_ROOT=/your/android-sdk
export ADMIN_MANUAL_ROOT=/your/private/Admin-Manual
export VOICEISH_KEYSTORE="$ADMIN_MANUAL_ROOT/CREDENTIALS/PlayStore/production.keystore"
bash scripts/build-native.sh          # dist/voiceish-sim-0.1.4.apk
bash scripts/build-setup.sh           # dist/voiceish-setup-0.1.4.apk
python3 scripts/verify-setup-release.py
python3 scripts/package-extension.py  # voiceish-sim-chrome-0.2.2.zip
python3 scripts/export-standalone.py  # Voiceish-0.1.4.html
python3 scripts/package-complete.py
```

Full details: `source/docs/BUILD.md`. Release files are immutable — scripts refuse to overwrite existing outputs; a changed release requires a new version and filename.

## Tests

From `source/`, with Node 22+ and Java 17: `npm install && npm test`. Runs extension unit tests, a JVM/WebCrypto AES-GCM interoperability test, a popup integration test, and a synthetic in-process phone speaking the real wire protocol. All tests use synthetic data; none proves a carrier round trip.

## Security and privacy in one paragraph

Message bodies and commands cross the local network only as AES-256-GCM ciphertext (128-bit tag, per-message IV, direction-separated AAD). The pairing key never travels in the clear; it is wrapped by Android Keystore on the phone and kept in Chrome profile-local storage (never sync) on the desktop. Requests are replay-protected (UUID + ±90 s clock window + 180 s server-side retention); sends are idempotent by `sendId` with the receipt persisted before the radio is touched. The bridge binds to private-network peers only. Rotate the key in the app to revoke paired browsers; **Forget phone** in the extension clears pairing and drafts.

## Boundaries of this version

SMS only — no MMS, RCS, attachments, calls, contact lookup, or second-SIM selection. Up to 500 recent records for the pinned SMS SIM. Primary SIM is the one Android selected for SMS; a changed default requires a bridge restart. "Sent" means the radio accepted every segment — not a delivery or read receipt. See `START-HERE.md` for the full list.

## Versioning

- Android companion & setup: `0.1.4` / versionCode 5 (`com.voiceish.sim`, `com.voiceish.setup`).
- Chrome extension: `0.2.2` (own monotonic version).
- Web/PWA shell cache: `voiceish-shell-v014`.

**Licence:** proprietary — © 2026 Robin L. M. Cheung, MBA. All rights reserved. No use, copying, modification, or distribution without prior written permission; see [`LICENSE`](LICENSE).
