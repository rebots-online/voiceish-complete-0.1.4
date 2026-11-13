# Voiceish SIM — Architecture (release 0.1.4)

Snapshot of the finished product, per the Admin-Manual Architecture Law. Every component, entity, enumeration, relationship, workflow, screen, string-bearing data format, algorithm, and build step of release 0.1.4 is defined here. CHECKLIST.md at the repository root is the recipe that produces this snapshot; nothing may be coded that is not in the snapshot, and nothing may appear in the checklist that is not in the snapshot.

All `file:line` references are relative to the repository root and were verified against the working tree on 2026-10-01. Entity IDs (`E-XXX-n`) are the join keys between this file's §9 enumeration table and CHECKLIST task attestations.

---

## §1 Purpose and system shape

Voiceish SIM lets a desktop Chrome user read and send SMS through the SIM that the user's Android phone has selected for SMS. Three clients exist; exactly one server exists:

- **Android companion** `com.voiceish.sim` — hosts the encrypted bridge on the phone (the only server).
- **Android setup installer** `com.voiceish.setup` — installs the companion on the phone without ADB.
- **Chrome extension** "Voice-ish SIM" v0.2.2 (MV3) — popup client; also bridges the hosted web app.
- **Web app / PWA** (`source/web/dist/`) — browser client served over HTTPS; talks direct to the phone or through the extension.

All messaging traffic is phone ⇄ desktop over the local network (HTTP transport, encrypted payloads). There is no hosted relay, no analytics, no remote extension scripts, no third-party runtime dependency on Android.

```
┌────────────────────┐  pairing text (one-time, out of band)   ┌────────────────────┐
│ Chrome extension   │ ───────────────────────────────────────▶ │ Android companion  │
│ popup / site-bridge│ ◀═══════ AES-256-GCM envelopes ════════▶ │ BridgeService      │
└────────────────────┘      POST /v1 on port 8765 (LAN)       │  └ LocalServer     │
┌────────────────────┐                                         │  └ Protocol        │
│ Web app / PWA      │  direct (same as extension) or via      │  └ SmsEngine       │
│ app.js / engine.js │  extension postMessage (site-bridge)    │  └ SmsStore (SQLite)│
└────────────────────┘                                         └────────────────────┘
```

## §2 Component map

| Component | Identity | Location | Versions |
| --- | --- | --- | --- |
| Companion | `com.voiceish.sim`, minSdk 26, targetSdk 35 | `source/android/app/` | 0.1.4 / versionCode 5 |
| Setup installer | `com.voiceish.setup`, minSdk 29, targetSdk 35 | `source/android/setup/` | 0.1.4 / versionCode 5 |
| Extension | MV3, minimum Chrome 120 | `source/extension/` | 0.2.2 |
| Web app / PWA | static, service-worker shell | `source/web/dist/` | shell cache `voiceish-shell-v014` |
| Tests | Node `--test` + JVM interop | `source/tests/`, `source/extension/tests/` | 0.1.4 |
| Build & packaging | bash + Python 3 | `source/scripts/` | 0.1.4 |

## §3 Android companion (`com.voiceish.sim`)

Manifest (`source/android/app/src/main/AndroidManifest.xml`): permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_SMS`, `SEND_SMS`, `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, `WAKE_LOCK`; `allowBackup="false"`, `fullBackupContent="false"`, `usesCleartextTraffic="true"`; telephony feature required. Declared components: launcher `MainActivity`, foreground `BridgeService` (`foregroundServiceType="remoteMessaging"`), receiver `SmsSentReceiver` (not exported).

- **BridgeService** (`.../sim/BridgeService.java:15`) — foreground-service owner. `onStartCommand` (`:26`) resolves and pins the SIM via `SimState.resolve`, creates the `bridge` notification channel, starts `LocalServer`, holds a wake lock, and honors the `STOP` action (`"com.voiceish.sim.STOP"`, `:16`). Static observables: `running` (`:17`), `lastError` (`:18`), `simLabel` (`:19`), `subscriptionId` (`:20`). `stopRunning` (`:54`) and `shutdown` (`:59`) tear down engine and server.
- **LocalServer** (`.../sim/LocalServer.java`) — raw-socket HTTP/1.x server on port 8765. Binds only to private peers: loopback, RFC 1918 site-local, link-local, or IPv6 ULA `fc00::/7` (`:49-51`). `serve` (`:52`) parses one request: `POST /v1` only (`:70-72`), `OPTIONS` preflight answered `204` without content (`:71`), header block capped at 16 384 bytes → `413` (`:62`), malformed → `400`, unknown path → `404`, wrong method → `405`. No request-body logging.
- **Protocol** (`.../sim/Protocol.java:8`) — envelope and dispatch. `handle` (`:14`) validates version, decrypts the request envelope with `Crypto` (AAD `voiceish-sim:v1:request`), checks request `id` is a UUID (`isUuid`, `:13`) and `ts` within ±90 s (`CLOCK_SKEW`, `:27-28`), delegates replay rejection to `SmsStore.rememberRequest`, dispatches `status|messages|send` (`:33-37`), and returns the encrypted response envelope (fresh 12-byte IV, AAD `voiceish-sim:v1:response`, `:47-50`). `BridgeException` → `{ok:false,error:{code,message}}`; `SecurityException` → `PERMISSION_DENIED`; anything else → `INTERNAL_ERROR`.
- **SmsEngine** (`.../sim/SmsEngine.java:17`) — action implementations. Constructor marks interrupted sends `unknown` (`:24` via `SmsStore.markInterrupted`). `status` (`:31`) reports version `0.1.4`, device, pinned SIM `{id,label,ready[,reason]}`, and the three runtime permissions. `messages` (`:42`) queries `Telephony.Sms` filtered to the pinned `SUBSCRIPTION_ID` and inbox/sent types `IN (1,2,4,5,6)` (`:49`), newest-first, default limit 300 capped to 500 (`:45`), payload capped at 1 000 000 bytes (`:61`), returns `messages` + `sends` (receipts via `SmsStore.recent`). `send` (`:72`) is the idempotent send pipeline (§7.3).
- **SmsStore** (`.../sim/SmsStore.java:13`) — SQLite `sms-receipts.db` version 1 (`:19`). Schema in §5. `rememberRequest` (`:27`) prunes requests older than 180 s, rejects replays (`REPLAY_REJECTED`), caps the table at 10 000 (`BUSY`). `existing` (`:39`) is the dedupe lookup — same `sendId` + identical `recipient`/`body`/`subscription_id` returns the previous status; any difference is `SEND_ID_CONFLICT`. `reserve` (`:50`) commits the send + its parts atomically **before** radio invocation. `markUnknown`/`markUnsubmitted`/`markInterrupted`/`expirePending` (120 s, `:78`) converge uncertain states to `unknown`/`failed`. `completePart` (`:83`) records each segment result under a one-time `token`; any failed part → `failed`, all parts `RESULT_OK` → `sent`. `recent` (`:111`) returns the 100 newest receipts for the subscription.
- **Crypto** (`.../sim/Crypto.java:9`) — AES/GCM/NoPadding, 32-byte key, 12-byte IV, 128-bit tag, AAD literal `voiceish-sim:v1:request` or `voiceish-sim:v1:response` (`transform`, `:17-24`). The identical byte-level algorithm is runnable on a desktop JVM for `tests/CryptoInterop.java`.
- **KeyStoreHelper** (`.../sim/KeyStoreHelper.java:15`) — wraps the 32-byte pairing key under Android Keystore alias `voiceish-sim-pairing-wrap-v1` (`:16`); `rotate` regenerates the key, revoking every paired browser.
- **SimState** (`.../sim/SimState.java:9`) — pinned SIM identity `{id,label}`. `resolve` (`:30`) requires all three runtime permissions (`REQUIRED_PERMISSIONS`, `:14`), takes Android's default SMS subscription, errors `NO_DEFAULT_SIM` / `SIM_INACTIVE`. `requireReady` (`:39`) re-validates permissions, default-SIM drift (`DEFAULT_SIM_CHANGED`), and active status.
- **MainActivity** (`.../sim/MainActivity.java:40`) — single-activity UI built in code (no layout XML), `FLAG_SECURE` (`:50`), navy/gold theme constants (`:41`), 1.5 s refresh loop while resumed (`:47`, `:98-99`). Sections: bridge start/stop, permission status + request (`requestAccess`, `:110`; `onRequestPermissionsResult`, `:117`), pairing text display (cleared on pause, `:99`), network/address display, diagnostics copy/share (`copyDiagnostics`, `:125` — no SMS, numbers, or secrets).
- **SmsSentReceiver** (`.../sim/SmsSentReceiver.java:7`) — receives action `com.voiceish.sim.SMS_SENT` and forwards `(sendId, part, token, result)` to `SmsStore.completePart` (`:10`).
- **BridgeException** (`.../sim/BridgeException.java:3`) — the coded error type; codes are a closed set (§8.2).

## §4 Setup installer (`com.voiceish.setup`)

Manifest (`source/android/setup/src/main/AndroidManifest.xml`): permission `REQUEST_INSTALL_PACKAGES`; `<queries>` for `com.voiceish.sim`; launcher `MainActivity`; not-exported receiver `InstallReceiver`.

- **MainActivity** (`.../setup/MainActivity.java:38`) — two-step guided flow. Constants: `TARGET = "com.voiceish.sim"` (`:39`), `CERT = "56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943"` (`:40`) — the SHA-256 of the organizational signer; the payload APK is accepted only when signed by that certificate. Flow: `begin()` streams the embedded payload to external storage and starts an install session; `launchConfirmation()` hands Android's user confirmation; `cancelSession()` abandons a pending session; `InstallReceiver` observes `PACKAGE_ADDED`/`REPLACED`, verifies the installing certificate via `signedByOrganization`, then enables `openCompanion()` (safe-launch via `safeStart`). State survives rotation through the `visible` WeakReference (`:41`) and `building` flag (`:44`) plus `SharedPreferences` (`state()`, `:45`). The companion payload ships at `android/setup/src/main/assets/voiceish-sim.apk` with a pinned SHA-256 enforced by `scripts/verify-setup-release.py` (`pin_source`, `scripts/verify-setup-release.py:28`).
- **InstallReceiver** (`.../setup/InstallReceiver.java`) — install-broadcast receiver described above.
- **Payload** (`.../setup/Payload.java`) — embedded-payload constants.

## §5 Data stores

| Store | Medium | Contents |
| --- | --- | --- |
| `sms-receipts.db` (v1) | SQLite on phone (`SmsStore.java:19`) | `sends(id PK, recipient, body, created, state, subscription_id, part_count, token, error)`; `parts(send_id, part_index, result, PK(send_id,part_index))`; `requests(id PK, accepted)`; index `sends_created` (`SmsStore.java:20-25`). `onUpgrade` throws — schema is frozen (`:26`). |
| Pairing key | Android Keystore, alias `voiceish-sim-pairing-wrap-v1` (`KeyStoreHelper.java:16`) | Wrapped 32-byte AES key; `rotate` replaces it. App backup disabled by manifest. |
| Extension state | `chrome.storage.local` only (never sync) (`manifest.json` `permissions:["storage"]`) | pairing `{v,url,key,device}`, per-address drafts, outgoing-attempt records (`background.js:4,9,24`). |
| Web state | `localStorage` (`voiceish-route`) + in-memory `state` (`app.js:7,14`) | route choice + session UI state; pairing/drafts live in the extension when routed through it. |
| PWA shell | Cache Storage `voiceish-shell-v014` (`sw.js:1`) | precached SHELL list (`sw.js:2`), network-first with cache fallback. |

## §6 Protocol v1 (authoritative strings)

- **Pairing text** (phone → user → desktop, out of band): `{ "v":1, "url":"http://<lan-ip>:8765", "key":"<base64 of 32 random bytes>", "device":"Phone" }`. The key never travels over HTTP.
- **Envelope**, both directions: `{ "v":1, "iv":"<base64 12B>", "data":"<base64 AES-256-GCM ciphertext+128-bit tag>" }`. Plaintext is UTF-8 JSON. AAD is the literal `voiceish-sim:v1:request` (phone→desktop responses use `voiceish-sim:v1:response`). Java side: `Crypto.java:22`; JS side: `extension/core.js:2-3`.
- **Request**: `{ "id":"<UUID>", "ts":<ms UTC>, "action":"status|messages|send", "params":{} }`; reject |ts−now| > 90 s and reused ids (180 s retention).
- **Response**: `{ "id":<same>, "ok":true, "data":{} }` or `{ "id":<same>, "ok":false, "error":{"code","message"} }`. The browser checks the echoed id (`extension/core.js:57`).
- **Limits**: HTTP request ≤ 64 KiB (header block 16 384 B → 413); messages payload ≤ 1 000 000 B (phone, `SmsEngine.java:61`); response text ≤ 3 MiB (extension, `core.js:79`); list limit default 300 / cap 500; receipts cap 100; send body 1–2000 UTF-16 code units; rpc timeout 12 s (`core.js:66`); site-bridge call timeout 15 s (`app.js:12`); uncertain sends expire to `unknown` after 120 s (`SmsStore.java:81`).

## §7 Workflows

**§7.1 Bridge start.** UI `startBridge` (`MainActivity.java:100`) → `SimState.resolve` (permissions + default-SMS subscription) → foreground `BridgeService.onStartCommand` → `LocalServer` listens on 8765 → `SmsEngine` marks previously-`sending` rows `unknown`. Pairing text is displayed from the wrapped key; rotating the key revokes browsers and stops the bridge.

**§7.2 Read.** Client `rpc("messages",{limit})` (`core.js:66`) → `Protocol.handle` → `SmsEngine.messages` (pinned-subscription filter; `SIM_FILTER_UNSUPPORTED` when the provider cannot filter, `:68`) → client merges provider `messages` with store `sends` receipts by address/body/date (`reconcileMessages`, `core.js:104`); the full inbox is never permanently cached.

**§7.3 Send (idempotent, never silently retransmitted).** Client validates (`validateSend`, `core.js:95`) and posts `send` with a fresh `sendId` UUID ≠ request id. Phone: `SmsEngine.send` (`:72`) validates `sendId` (`INVALID_SEND_ID`), E.164-ish recipient (`INVALID_RECIPIENT`), body 1–2000 (`INVALID_BODY`); `SmsStore.existing` dedupes (`SEND_ID_CONFLICT` on content mismatch); `sim.requireReady` + `SIM_MISMATCH` guard; `divideMessage` splits; **`reserve` commits before the radio** (`:99`); pre-radio SIM change → `markUnsubmitted` (`failed`); `sendMultipartTextMessage` failure → `markUnknown`; per-part `SmsSentReceiver` callbacks → `completePart` → `sent` only when every part succeeded. No automatic retransmission exists anywhere; uncertain sends stay `unknown` with an explicit check-the-inbox instruction.

**§7.4 SIM change.** `SimState.requireReady` detects a drifted default (`DEFAULT_SIM_CHANGED`) or inactive SIM (`SIM_INACTIVE`) and surfaces it in `status`. The client must have the user restart the bridge; it never silently switches SIMs. Switching SIMs on the desktop requires **Forget phone & erase drafts** and a fresh pairing.

**§7.5 Revoke / forget.** Phone: key `rotate` (`KeyStoreHelper`) revokes all paired browsers. Desktop: extension **Forget phone** (`resetMemory`, `popup.js:19`; web `forget` action) clears pairing + drafts.

**§7.6 Phone-only install.** Setup `begin()` → install session → Android confirmation → `InstallReceiver` verifies the organizational certificate → **open companion**. Existing matching installs update in place with data kept.

## §8 Enumerations (closed sets)

**§8.1 Actions.** Wire: `status | messages | send` (`Protocol.java:33-37`). Extension/site-bridge messages: `state | refresh | draft | send | compose-again | forget` (`site-bridge.js:2`). Web routes: `direct | extension` (`app.js:14`).

**§8.2 Error codes** (`BridgeException` code values; complete): `BRIDGE_STOPPED` (`SmsEngine.java:29`), `INBOX_UNAVAILABLE` (`:51`), `PERMISSION_DENIED` (`SmsEngine.java:66`; `SimState.java:28`; `Protocol.java:43`), `SIM_FILTER_UNSUPPORTED` (`SmsEngine.java:68`), `REPLAY_REJECTED` (`SmsStore.java:32`), `BUSY` (`SmsStore.java:34`), `SEND_ID_CONFLICT` (`SmsStore.java:46`), `NO_DEFAULT_SIM` (`SimState.java:34`), `SIM_INACTIVE` (`SimState.java:36,44`), `DEFAULT_SIM_CHANGED` (`SimState.java:42`), `CLOCK_SKEW` (`Protocol.java:28`), `INVALID_PARAMS` (`Protocol.java:31`), `UNKNOWN_ACTION` (`Protocol.java:37`), `INTERNAL_ERROR` (`Protocol.java:45`), `INVALID_SEND_ID` (`SmsEngine.java:75`), `INVALID_RECIPIENT` (`SmsEngine.java:79`), `INVALID_BODY` (`SmsEngine.java:80,89`), `SIM_MISMATCH` (`SmsEngine.java:84`).

**§8.3 Send states.** Receipt lifecycle: `sending → sent | failed | unknown` (reserve `sending`, `SmsStore.java:55`; terminal via `completePart`/`mark*`/`expirePending`). Wire `send` response status: `sending|sent|failed|unknown`.

**§8.4 Message fields.** Provider `type`: `in|out`; provider `status`: `received|sent|failed|pending` (`SmsEngine.java:57-58`).

**§8.5 Runtime permissions.** `READ_SMS`, `SEND_SMS`, `READ_PHONE_STATE` (`SimState.java:14`); notifications are requested separately (`POST_NOTIFICATIONS`).

**§8.6 Identities and constants.** Companion `com.voiceish.sim` 0.1.4/5; setup `com.voiceish.setup` 0.1.4/5; extension `Voice-ish SIM` 0.2.2 (Chrome ≥ 120); bridge port 8765; stop action `com.voiceish.sim.STOP`; sent action `com.voiceish.sim.SMS_SENT`; Keystore alias `voiceish-sim-pairing-wrap-v1`; DB `sms-receipts.db` v1; cache `voiceish-shell-v014`; signer cert SHA-256 `56c13674…bd943` (`setup/MainActivity.java:40`); hosted origins `https://voipmsms.robinc.chatgpt.site`, `https://voiceish-sim.robinc.chatgpt.site` (`site-bridge.js:3`).

## §9 Screens

- **Companion** (`sim/MainActivity.java`): header; status card (bridge start/stop, SIM label, address); permission card (Read SMS / Send SMS / Phone-SIM status, request + recovery hints, diagnostics copy/share); pairing card (copyable pairing text, shown only while resumed); network card; boundaries footnote.
- **Setup** (`setup/MainActivity.java`): step 1 "Allow this installer"; step 2 "Install the companion"; status line; Install / Continue confirmation / Cancel / Open / Settings buttons.
- **Extension popup** (`popup.html`/`popup.js`): pairing setup; connected header (device, SIM, connection chip); thread list with search; conversation with per-message status; compose (Enter sends, Shift+Enter newline); modals (forget, compose-again confirm).
- **Web app** (`web/dist/index.html` + `app.js`): route choice (direct/extension); thread pane; conversation pane with date dividers and per-send status ("Sent to carrier"/"Sending"/"Unconfirmed"); composer with 2000-char counter and uncertain-send warning + "I checked — compose again"; setup dialog; install dialog; demo mode (`enterDemo`, `app.js:31`); toast + error notice.

## §10 Security model

AES-256-GCM with 128-bit tag and fresh random IV per message; direction-split AAD prevents request/response confusion. Pairing key: 32 random bytes, never on the wire; Keystore-wrapped on phone; `storage.local` (not sync) in Chrome. Replay protection: UUID ids + ±90 s skew + 180 s retention + 10 000-request cap. Send idempotence: persist-before-radio + content-bound dedupe. Transport is plain HTTP **on the LAN only**: the server accepts private-address peers exclusively and cleartext is manifest-sanctioned; the encryption layer protects content and integrity, while HTTP exposes timing/metadata. Anyone holding the pairing text can use the bridge on the LAN — treat it as a password. No analytics, no remote scripts, no backup of app data. Signing: organizational production key only, read from the private Admin-Manual checkout; builds fail closed rather than fall back to a development key (`android/app/build.gradle` `validateOrganizationalSigning`).

## §11 Build, verification, and packaging pipeline

1. `scripts/build-native.sh` — direct-SDK build (aapt2 → javac → d8 → zipalign → apksigner, v2/v3 verified) → `dist/voiceish-sim-0.1.4.apk`. Toolchain: Java 17 (supports `-m jdk.compiler/com.sun.tools.javac.Main`), SDK Platform 35, Build-Tools 35.0.0; overridable via `VOICEISH_ANDROID_JAR` / `VOICEISH_BUILD_TOOLS`.
2. `scripts/build-setup.sh` — same toolchain → `dist/voiceish-setup-0.1.4.apk`.
3. `scripts/verify-setup-release.py` — package identities, versions, SDKs, installer permission, payload hash pin (`pin_source`, `:28`).
4. `scripts/package-extension.py` (+ shared `package.py`) → `voiceish-sim-chrome-0.2.2.zip`.
5. `scripts/export-standalone.py` → `Voiceish-0.1.4.html` (no npm dependencies).
6. `scripts/package-complete.py` → the complete bundle; `SHA256SUMS.txt` pins hashes. All outputs are refused if they already exist — published releases are immutable and require a new version + filename.

Signing inputs (either Gradle or direct-SDK path): `ADMIN_MANUAL_ROOT`/`VOICEISH_KEYSTORE_PROPERTIES` + `VOICEISH_KEYSTORE`, `VOICEISH_KEY_ALIAS`, `VOICEISH_STORE_PASS`, `VOICEISH_KEY_PASS`; properties canonical at `Admin-Manual/CREDENTIALS/PlayStore/keystore.properties`, alias `upload`. Gradle alternative: AGP 8.7.3 / Gradle 8.9, `gradle assembleRelease`.

## §12 Entity enumeration table

Exhaustive join table for CHECKLIST attestations. Paths relative to repo root; `A = source/android/app/src/main/java/com/voiceish/sim`, `S = source/android/setup/src/main/java/com/voiceish/setup`, `X = source/extension`, `W = source/web/dist`.

### §12.1 Companion — protocol & crypto

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-PRC-1 | `Crypto` (encrypt/decrypt/transform) | class | `A/Crypto.java:9` | T1 |
| E-PRC-2 | AAD literals `voiceish-sim:v1:{request,response}` | const | `A/Crypto.java:22`, `X/core.js:2-3` | T1, T6 |
| E-PRC-3 | `Protocol` (handle/isUuid/dispatch) | class | `A/Protocol.java:8` | T1 |
| E-PRC-4 | Envelope `{v,iv,data}` + request/response shapes | format | `A/Protocol.java:14-51` | T1, T6, T9 |
| E-PRC-5 | `BridgeException` + `code` field | class | `A/BridgeException.java:3` | T1 |
| E-PRC-6 | Error-code closed set (18 codes) | enum | §8.2 rows | T1, T9 |
| E-PRC-7 | Limits: ±90 s skew, 180 s replay, 10 000 cap, 64 KiB/16 KiB, 1 MB, 3 MiB, 500/300, 100, 2000, 120 s | consts | `A/Protocol.java:27`, `A/SmsStore.java:30-34`, `A/LocalServer.java:62`, `A/SmsEngine.java:45,61,80`, `A/SmsStore.java:81,113`, `X/core.js:66,79` | T1, T2, T3, T9 |

### §12.2 Companion — SMS domain

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-SIM-1 | `SimState` (resolve/requireReady/requirePermissions/granted/missingPermissions) | class | `A/SimState.java:9` | T2, T4 |
| E-SIM-2 | `REQUIRED_PERMISSIONS` {READ_SMS, SEND_SMS, READ_PHONE_STATE} | const | `A/SimState.java:14` | T4 |
| E-SIM-3 | `SmsEngine` (status/messages/send/stop/rememberRequest) | class | `A/SmsEngine.java:17` | T2 |
| E-SIM-4 | Inbox filter `SUBSCRIPTION_ID=? AND TYPE IN (1,2,4,5,6)` | logic | `A/SmsEngine.java:49` | T2 |
| E-SIM-5 | `SmsStore` (SQLite `sms-receipts.db` v1; schema 3 tables + index; onUpgrade throws) | class | `A/SmsStore.java:13,20-26` | T2 |
| E-SIM-6 | Send pipeline (validate→existing→reserve→radio→completePart) | workflow | `A/SmsEngine.java:72-113`, `A/SmsStore.java:39-110` | T2 |
| E-SIM-7 | `SmsSentReceiver` (action `com.voiceish.sim.SMS_SENT`) | receiver | `A/SmsSentReceiver.java:7` | T2 |
| E-SIM-8 | Send states `sending|sent|failed|unknown`; message `type in|out`, `status received|sent|failed|pending` | enum | `A/SmsStore.java:55,65-82,100-107`, `A/SmsEngine.java:57-58` | T2, T6 |
| E-SIM-9 | `KeyStoreHelper` (alias `voiceish-sim-pairing-wrap-v1`, wrap/get/rotate) | class | `A/KeyStoreHelper.java:15` | T1, T2 |

### §12.3 Companion — server, service, UI

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-SRV-1 | `LocalServer` (bind private-only; serve; POST /v1; OPTIONS 204; 400/404/405/413) | class | `A/LocalServer.java:49-72` | T3 |
| E-SRV-2 | `BridgeService` (foreground `remoteMessaging`, STOP action, wake lock, observables) | service | `A/BridgeService.java:15-62` | T3 |
| E-SRV-3 | Notification channel `bridge` / "Phone texting bridge" | string | `A/BridgeService.java:32` | T3 |
| E-UI-1 | Companion `MainActivity` (code-built UI, FLAG_SECURE, refresh loop) | activity | `A/MainActivity.java:40-99` | T4 |
| E-UI-2 | `startBridge` / `requestAccess` / `onRequestPermissionsResult` / `copyDiagnostics` | methods | `A/MainActivity.java:100-127` | T4 |
| E-UI-3 | Companion manifest (9 permissions, 3 components, backup off, cleartext on) | manifest | `source/android/app/src/main/AndroidManifest.xml` | T3, T4 |
| E-UI-4 | Companion Gradle (namespace, 26/35, versionCode 5, v0.1.4, Java 17, organizational signing + `validateOrganizationalSigning`) | build | `source/android/app/build.gradle` | T10 |

### §12.4 Setup installer

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-SET-1 | Setup `MainActivity` (begin/launchConfirmation/cancelSession/openCompanion/safeStart/signedByOrganization/refresh; TARGET, CERT) | activity | `S/MainActivity.java:38-88` | T5 |
| E-SET-2 | `InstallReceiver` (PACKAGE_ADDED/REPLACED → cert check → enable open) | receiver | `S/InstallReceiver.java` | T5 |
| E-SET-3 | `Payload` + embedded `assets/voiceish-sim.apk` + hash pin | asset | `S/Payload.java`, `source/android/setup/src/main/assets/`, `scripts/verify-setup-release.py:28` | T5, T10 |
| E-SET-4 | Setup manifest (REQUEST_INSTALL_PACKAGES, queries `com.voiceish.sim`) | manifest | `source/android/setup/src/main/AndroidManifest.xml` | T5 |
| E-SET-5 | Setup Gradle (minSdk 29, versionCode 5, shared signing) | build | `source/android/setup/build.gradle` | T10 |

### §12.5 Chrome extension

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-EXT-1 | `manifest.json` (MV3, 0.2.2, storage, optional http host, CSP, site-bridge on 2 origins) | manifest | `X/manifest.json` | T6 |
| E-EXT-2 | `core.js` protocol kit (endpoint, hostPermission, from/toBase64, parsePairing, importKey, encrypt, decrypt, validateResponse, rpc, addressKey, destination, validateSend, reconcileMessages) | module | `X/core.js:2-104` | T6 |
| E-EXT-3 | `background.js` (mutate, readState, publicState, requirePairing, samePairing, updateAttempt) | worker | `X/background.js:4-24` | T6 |
| E-EXT-4 | `popup.js` (state, call, absorb, resetMemory, render*, threads, select, refresh, openModal/closeModal) | UI | `X/popup.js:4-173` | T7 |
| E-EXT-5 | `popup.html` / `popup.css` / icons 16-128 | UI | `X/popup.html`, `X/popup.css`, `X/icons/` | T7 |
| E-EXT-6 | `site-bridge.js` (allowed action set, 2-origin allowlist, postMessage channel) | content | `X/site-bridge.js:2-4` | T7 |

### §12.6 Web app / PWA

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-WEB-1 | `app.js` (state, extensionCall, call, chooseRoute, render*, send, refresh, persistDraft, demo mode, PWA install) | UI | `W/app.js:5-43` | T8 |
| E-WEB-2 | `engine.js` (mutate, readState, publicState, requirePairing, samePairing, updateAttempt, handle) | module | `W/engine.js:13-49` | T8 |
| E-WEB-3 | `core.js` (dist copy of E-EXT-2) | module | `W/core.js` | T8 |
| E-WEB-4 | `sw.js` (`voiceish-shell-v014`, SHELL precache, network-first) | worker | `W/sw.js:1-30` | T8 |
| E-WEB-5 | `index.html` / `style.css` / `manifest.webmanifest` / `geist.ttf` / icons / `favicon.svg` | UI | `W/` | T8 |

### §12.7 Tests, build, packaging, docs

| ID | Entity | Kind | Location | Engaged-by |
| --- | --- | --- | --- | --- |
| E-TST-1 | `package.json` test chain (`node --test extension/tests/*.test.js` + crypto-interop + popup-integration; linkedom 0.18.13) | script | `source/package.json` | T9 |
| E-TST-2 | `synthetic-phone.mjs` (`testServer`, same envelope crypto) | test | `source/tests/synthetic-phone.mjs:10-12` | T9 |
| E-TST-3 | `crypto-interop.mjs` + `CryptoInterop.java` (JVM↔WebCrypto AES-GCM) | test | `source/tests/` | T9 |
| E-TST-4 | `popup-integration.mjs` + `extension/tests/*.test.js` | test | `source/tests/`, `source/extension/tests/` | T9 |
| E-BLD-1 | `build-native.sh` (aapt2→javac→d8→zipalign→apksigner v2/v3; env overrides) | script | `source/scripts/build-native.sh` | T10 |
| E-BLD-2 | `build-setup.sh` | script | `source/scripts/build-setup.sh` | T10 |
| E-BLD-3 | `verify-setup-release.py` (`pin_source`) | script | `source/scripts/verify-setup-release.py:28` | T10 |
| E-BLD-4 | `package-extension.py` + `package.py` → `voiceish-sim-chrome-0.2.2.zip` | script | `source/scripts/` | T10 |
| E-BLD-5 | `export-standalone.py` → `Voiceish-0.1.4.html` | script | `source/scripts/export-standalone.py` | T10 |
| E-BLD-6 | `package-complete.py` → bundle + `SHA256SUMS.txt`; refuse-overwrite immutability | script | `source/scripts/package-complete.py` | T10 |
| E-BLD-7 | Signing contract (Admin-Manual keystore/alias upload; env overrides; fail-closed) | build | `source/android/app/build.gradle`, `source/docs/BUILD.md` | T10 |
| E-DOC-1 | Docs set (PROTOCOL/BUILD/PHONE-SETUP/VERIFICATION/RELEASE-0.1.4, extension+web READMEs, DESIGN) | docs | `source/docs/`, `source/*/README.md` | T11 |
| E-DOC-2 | Root docs (README.md, DOCS/ARCHITECTURE.md, CHECKLIST.md, START-HERE.md, SHA256SUMS.txt) | docs | repo root | T11 |
