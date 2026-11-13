# Voiceish SIM 0.1.4 — CHECKLIST (recipe for DOCS/ARCHITECTURE.md)

Per the Architecture Law: every task below is fully self-contained and order-independent; any subset may be executed in parallel by any number of agents. Each task names its exact files and attests the exact entities (E-IDs) from `DOCS/ARCHITECTURE.md` §12 that it wires. Nothing is coded that is not attested here; nothing is attested that is not in the snapshot.

`[x]` marks tasks already realized by the released 0.1.4 codebase. A task may be flipped `[x]→✅` only after an entity-by-entity comparison of its attested E-IDs against the code (direct observation, no grep-for-existence proxy); the 2026-10-01 gate pass for this release is recorded in `DOCS/SESSION-REPORT-2026-10-01.md`. T12 items remain open: physical-handset verification was never performed and must not be marked complete.

Conventions: `A = source/android/app/src/main/java/com/voiceish/sim`, `S = source/android/setup/src/main/java/com/voiceish/setup`, `X = source/extension`, `W = source/web/dist`.

---

## T1 — Companion protocol & crypto core  `[x]`

**Build** `A/Crypto.java`, `A/KeyStoreHelper.java`, `A/Protocol.java`, `A/BridgeException.java`.

- `Crypto` (E-PRC-1): AES/GCM/NoPadding; key exactly 32 bytes, IV exactly 12 random bytes per message, 128-bit tag; `encrypt`/`decrypt`/`transform`; AAD literals exactly `voiceish-sim:v1:request` and `voiceish-sim:v1:response` per direction (E-PRC-2). Bad key size → `BridgeException`.
- `KeyStoreHelper` (E-SIM-9): Android Keystore, alias exactly `voiceish-sim-pairing-wrap-v1`; wrap/get the 32-byte pairing key; `rotate` replaces the key (revokes all paired browsers).
- `Protocol.handle` (E-PRC-3): accept envelope `{v:1, iv, data}` (E-PRC-4); decrypt with request AAD; validate `id` UUID (`isUuid`) and `|ts−now| ≤ 90 000 ms` (`CLOCK_SKEW`, E-PRC-7); delegate replay/retention to `SmsStore.rememberRequest`; dispatch closed action set `status|messages|send` (E-PRC-4); `SecurityException→PERMISSION_DENIED`, `BridgeException→{ok:false,error:{code,message}}`, other `Exception→INTERNAL_ERROR`; respond with fresh IV + response AAD.
- `BridgeException` (E-PRC-5) carrying the closed error-code set (E-PRC-6, 18 codes enumerated in ARCHITECTURE §8.2).

**Accept:** a `{v:1,iv,data}` envelope round-trips through `Crypto` with both AADs and fails with the wrong AAD; non-UUID `id` → `INVALID_PARAMS`-family rejection; stale `ts` → `CLOCK_SKEW`; unknown action → `UNKNOWN_ACTION`; replayed `id` → `REPLAY_REJECTED`. Entities wired: E-PRC-1..7, E-SIM-9.

## T2 — Companion SMS domain  `[x]`

**Build** `A/SimState.java`, `A/SmsEngine.java`, `A/SmsStore.java`, `A/SmsSentReceiver.java`.

- `SimState` (E-SIM-1): `REQUIRED_PERMISSIONS = {READ_SMS, SEND_SMS, READ_PHONE_STATE}` (E-SIM-2); `resolve` → default SMS subscription `{id,label}` or `NO_DEFAULT_SIM`/`SIM_INACTIVE`/`PERMISSION_DENIED`; `requireReady` re-checks permissions, default-SIM drift (`DEFAULT_SIM_CHANGED`), active state.
- `SmsEngine` (E-SIM-3): constructor → `markInterrupted`; `status` (version `0.1.4`, device, SIM state, permissions); `messages` — inbox filter `SUBSCRIPTION_ID=? AND type IN (1,2,4,5,6)` (E-SIM-4), limit 300 default / 500 cap, 1 000 000-byte payload cap, `INBOX_UNAVAILABLE`, `SIM_FILTER_UNSUPPORTED`, receipts via `SmsStore.recent`; `send` implements the idempotent pipeline (E-SIM-6): UUID `sendId` ≠ request id → validate (`INVALID_SEND_ID`; recipient regex `\+?[0-9]{3,20}` → `INVALID_RECIPIENT`; body 1–2000 UTF-16 chars → `INVALID_BODY`) → `SmsStore.existing` dedupe (`SEND_ID_CONFLICT` on content mismatch) → `requireReady`/`SIM_MISMATCH` guard → `divideMessage` → **`SmsStore.reserve` committed before radio** → pre-radio failure `markUnsubmitted`, radio exception `markUnknown` → `sendMultipartTextMessage` with per-part PendingIntents; `createForSubscriptionId` on API ≥ 31. No auto-retransmission anywhere.
- `SmsStore` (E-SIM-5): SQLite `sms-receipts.db` v1; tables `sends(id PK, recipient, body, created, state, subscription_id, part_count, token, error)`, `parts(send_id, part_index, result, PK(send_id,part_index))`, `requests(id PK, accepted)`, index `sends_created`; `onUpgrade` throws. `rememberRequest` (180 s retention, replay reject, 10 000 cap → `BUSY`); `existing`; `reserve` (state `sending`, UUID `token`); `markUnknown`/`markUnsubmitted`/`markInterrupted`/`expirePending` 120 s; `completePart` (any part failed → `failed`; all `RESULT_OK` → `sent`); `recent` cap 100. Send states closed set `sending|sent|failed|unknown`; message `type in|out`; provider `status received|sent|failed|pending` (E-SIM-8).
- `SmsSentReceiver` (E-SIM-7): action exactly `com.voiceish.sim.SMS_SENT`; forwards `(sendId, part, token, result)` to `completePart`.

**Accept:** reserve-before-radio ordering holds on every send path; duplicate identical `sendId` returns prior status; same `sendId` different content → `SEND_ID_CONFLICT`; restart with pending sends → `unknown`; failed part → `failed`; all-parts-OK → `sent`. Entities wired: E-SIM-1..9.

## T3 — Companion server & service  `[x]`

**Build** `A/LocalServer.java`, `A/BridgeService.java`, companion `AndroidManifest.xml` (E-UI-3).

- `LocalServer` (E-SRV-1): `ServerSocket` port 8765, backlog 12, `SO_REUSEADDR`; accept only private peers — loopback, site-local, link-local, IPv6 ULA `fc00::/7` (`isLocal`) — others closed; header block ≤ 16 384 B else `413`; path `/v1` only else `404`; `OPTIONS` → `204`; non-POST → `405`; `transfer-encoding` present or content-type not `application/json` → `400`; body ≤ 65 536 B else `413`; handler exception → `401`; no header/body/number/key logging; graceful close + daemon threads.
- `BridgeService` (E-SRV-2): foreground service, `foregroundServiceType="remoteMessaging"`, channel exactly `bridge` / "Phone texting bridge" (E-SRV-3); action `com.voiceish.sim.STOP`; holds wake lock; static observables `running`, `lastError`, `simLabel`, `subscriptionId`; `onStartCommand` resolves SIM, starts `LocalServer`; `stopRunning`/`shutdown` tear down cleanly.
- Manifest: the 9 permissions, 3 components, `allowBackup=false`, `fullBackupContent=false`, `usesCleartextTraffic=true`, telephony feature required, receiver not exported.

**Accept:** from a paired browser on the LAN, `POST /v1` with a valid envelope returns 200 + encrypted response; `GET /v1` → 405; `OPTIONS` → 204; oversized headers → 413; a public-internet-sourced peer is refused; STOP action halts server + notification. Entities wired: E-SRV-1..3, E-UI-3, E-PRC-7.

## T4 — Companion UI  `[x]`

**Build** `A/MainActivity.java` (E-UI-1, E-UI-2). Code-built single activity (no layout XML); `FLAG_SECURE`; navy/gold theme; 1 500 ms refresh while resumed; sections for bridge start/stop, permission card (`startBridge`, `requestAccess`, `onRequestPermissionsResult`, recovery hints, `copyDiagnostics` — no SMS/numbers/secrets), pairing card (visible only while resumed, cleared on pause), network/address card, boundaries footnote.

**Accept:** with all permissions granted, tapping start launches the foreground service and the pairing text is copyable; denying a permission shows per-permission status and does not start the bridge; leaving the app clears the pairing text; diagnostics contain no message bodies, numbers, or secrets. Entities wired: E-UI-1, E-UI-2, E-SIM-1, E-SIM-2, E-SRV-2.

## T5 — Setup installer  `[x]`

**Build** `S/MainActivity.java` (E-SET-1), `S/InstallReceiver.java` (E-SET-2), `S/Payload.java` + `assets/voiceish-sim.apk` (E-SET-3), setup `AndroidManifest.xml` (E-SET-4).

- Constants: `TARGET="com.voiceish.sim"`, `CERT="56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943"` (organizational signer SHA-256).
- Flow: `begin()` streams embedded payload → install session; `launchConfirmation()` → Android user confirmation; `cancelSession()`; `InstallReceiver` on `PACKAGE_ADDED`/`REPLACED` verifies signer certificate via `signedByOrganization`, then enables `openCompanion()` (`safeStart`); state survives rotation (`visible`, `building`, `SharedPreferences`).
- Manifest: `REQUEST_INSTALL_PACKAGES`, `<queries>` for the companion, receiver not exported.

**Accept:** on a clean phone, allow-install → confirm → companion installed and openable; an APK signed by any other certificate is refused; cancel abandons the session; rotation mid-flow loses no state; an existing matching install updates in place. Entities wired: E-SET-1..4, E-BLD-3.

## T6 — Extension core & background  `[x]`

**Build** `X/manifest.json` (E-EXT-1), `X/core.js` (E-EXT-2), `X/background.js` (E-EXT-3).

- Manifest: MV3; name "Voice-ish SIM"; version `0.2.2`; `minimum_chrome_version` 120; permissions exactly `["storage"]`; optional host `http://*/*`; strict CSP (no remote code); `site-bridge.js` content script at `document_start` on exactly the two hosted origins.
- `core.js`: AADs matching T1 (E-PRC-2); `endpoint` private-IPv4 canonical check; `hostPermission`; base64 helpers; `parsePairing` (v/url/key/device, key 32 B); `importKey`; `encrypt`/`decrypt` (2 MiB cap); `validateResponse` (echoed id equality); `rpc` (12 s timeout, 3 MiB text cap); `addressKey`; `destination`; `validateSend` (recipient + 1–2000 body); `reconcileMessages` (merge provider messages + send receipts by address/body/date).
- `background.js`: `storage.local` only (never sync); pairing record, per-address drafts, outgoing attempts (`mutate`, `readState`, `publicState`, `requirePairing`, `samePairing`, `updateAttempt`).

**Accept:** a synthetic phone (tests/synthetic-phone.mjs) pairs and completes status/messages/send round-trips; wrong pairing key → 401 surfaced as auth failure; oversized response → explicit error not silence; state survives popup close; nothing is written to `storage.sync`. Entities wired: E-EXT-1..3, E-PRC-2, E-PRC-4, E-PRC-6, E-PRC-7.

## T7 — Extension popup & site bridge  `[x]`

**Build** `X/popup.js` (E-EXT-4), `X/popup.html`/`popup.css`/icons 16–128 (E-EXT-5), `X/site-bridge.js` (E-EXT-6).

- Popup: pairing setup; connected header (device, SIM, connection chip); thread list + search; conversation with per-message status; composer (Enter sends, Shift+Enter newline); modals: forget confirm, compose-again confirm; `resetMemory` clears pairing + drafts.
- Site bridge: allowed message set exactly `{state, refresh, draft, send, compose-again, forget}`; origin allowlist exactly the two hosted origins; postMessage channel to the page.

**Accept:** pairing text → connected state in one paste; threads render and refresh; send shows sending→sent/failed per receipts; forget clears all state; a page on any other origin receives nothing from the bridge. Entities wired: E-EXT-4..6, E-EXT-2.

## T8 — Web app / PWA  `[x]`

**Build** `W/app.js` (E-WEB-1), `W/engine.js` (E-WEB-2), `W/core.js` copy (E-WEB-3), `W/sw.js` (E-WEB-4), `W/index.html`, `W/style.css`, `W/manifest.webmanifest`, fonts/icons (E-WEB-5).

- Route choice `direct|extension` persisted at `localStorage["voiceish-route"]`; direct uses `core.js` rpc, extension path uses site-bridge messages with 15 s timeout; composer cap 2000 with counter; uncertain-send warning + "I checked — compose again"; demo mode with synthetic messages; PWA install prompt; visibilitychange refresh; service worker `voiceish-shell-v014`, SHELL precache, network-first with cache fallback.

**Accept:** app loads offline from the shell cache; route choice persists; sending through either route reaches the phone or reports failure explicitly; demo mode needs no phone; no remote dependencies. Entities wired: E-WEB-1..5, E-EXT-2.

## T9 — Tests  `[x]`

**Build** `source/package.json` test chain (E-TST-1), `source/tests/synthetic-phone.mjs` (E-TST-2), `source/tests/crypto-interop.mjs` + `source/tests/CryptoInterop.java` (E-TST-3), `source/tests/popup-integration.mjs` + `source/extension/tests/*.test.js` (E-TST-4).

- `npm test` = `node --test extension/tests/*.test.js` + `node tests/crypto-interop.mjs` + `node tests/popup-integration.mjs`; devDependency linkedom 0.18.13 only.
- synthetic-phone speaks the real wire protocol (real AES envelopes, matching AADs, replay/clock-skew/send-idempotence cases); crypto-interop runs `Crypto` on a desktop JVM against WebCrypto; popup-integration drives the popup against the synthetic phone.

**Accept:** `npm install && npm test` exits 0 from `source/`; all tests use synthetic data only; no test claims a carrier round trip. Entities wired: E-TST-1..4, E-PRC-1..7, E-EXT-2..4, E-SIM-3..8.

## T10 — Build, packaging & signing  `[x]`

**Build** `source/scripts/build-native.sh` (E-BLD-1), `build-setup.sh` (E-BLD-2), `verify-setup-release.py` (E-BLD-3), `package-extension.py`+`package.py` (E-BLD-4), `export-standalone.py` (E-BLD-5), `package-complete.py` (E-BLD-6); Gradle + signing contract (E-UI-4, E-SET-5, E-BLD-7).

- Direct-SDK path: aapt2 → javac (Java 17) → d8 → zipalign → apksigner with verified v2/v3 schemes; SDK Platform 35 / Build-Tools 35.0.0; env overrides `VOICEISH_ANDROID_JAR`, `VOICEISH_BUILD_TOOLS`; outputs `dist/voiceish-sim-0.1.4.apk`, `dist/voiceish-setup-0.1.4.apk`.
- Signing: organizational production key only, alias `upload`, from the private Admin-Manual checkout (`ADMIN_MANUAL_ROOT` or `VOICEISH_KEYSTORE*`); **fail closed — no development-key fallback** (`validateOrganizationalSigning`).
- Packaging: extension `voiceish-sim-chrome-0.2.2.zip`; standalone `Voiceish-0.1.4.html`; complete bundle + `SHA256SUMS.txt`; every script refuses to overwrite an existing output (release immutability — a changed release requires a new version + filename).
- `verify-setup-release.py`: package identities, versions, SDKs, installer permission, embedded payload hash pin.

**Accept:** all six scripts succeed end-to-end from a clean `source/` with only the documented env vars set; re-running any packaging script against existing outputs exits non-zero; removing the keystore env fails the build rather than producing a dev-signed APK; both APKs verify v2/v3. Entities wired: E-BLD-1..7, E-UI-4, E-SET-5.

## T11 — Documentation complement  `[x]`

**Build** root `README.md`, `DOCS/ARCHITECTURE.md`, `CHECKLIST.md`, `LICENSE`, `START-HERE.md`, `SHA256SUMS.txt` (E-DOC-2); `source/docs/{PROTOCOL,BUILD,PHONE-SETUP,VERIFICATION}.md`, release notes, extension/web READMEs, DESIGN (E-DOC-1).

- ARCHITECTURE.md is the exhaustive snapshot with the §12 entity table (E-IDs + `file:line` + engaged-by column); CHECKLIST tasks attest E-IDs; README is the front door with layout table, quick start, doc map, build, tests, security summary, boundaries, versioning; LICENSE is the proprietary all-rights-reserved default (Admin-Manual SC5) — no dual-licensing language anywhere; PROTOCOL.md matches the implemented wire format byte-for-byte; VERIFICATION.md separates performed checks from outstanding handset checks.

**Accept:** every entity in §12 is attested by ≥1 task; every task's attestations exist in §12; README links resolve; no secret or credential appears in any document; licence statement is the single proprietary default. Entities wired: E-DOC-1, E-DOC-2.

## T12 — Physical-handset verification (outstanding)  `[ ]`

Operator-held-device checks that no automated test can substitute (VERIFICATION.md). Each remains open until performed and recorded on a physical handset:

- [ ] Install both APKs via the setup installer on a real phone (restricted-settings path included) and confirm the certificate gate admits the organizational signature.
- [ ] Pair a real desktop Chrome over real LAN Wi-Fi (not loopback); confirm status/messages/send round-trips and guest-network/client-isolation failure modes behave as documented.
- [ ] Verify send receipts end-to-end against the carrier: `sent` = radio accepted all segments; a powered-off-radio or airplane-mode attempt yields `failed`/`unknown` (never a silent resend).
- [ ] Verify SIM-change behaviour: change Android's default SMS SIM → `DEFAULT_SIM_CHANGED` surfaced; forget+re-pair required; no cross-SIM send.
- [ ] Verify key rotation revokes a previously paired browser and Forget clears all desktop state.
- [ ] Confirm bridge survival across phone sleep/reboot and the post-reboot manual restart requirement.

**Accept (per item):** performed on a named physical handset/date with observed outcomes recorded in VERIFICATION.md; no item is marked complete by code inspection or synthetic tests alone. Entities exercised: E-SRV-1..3, E-SIM-1..9, E-SET-1..4, E-EXT-2..6, E-WEB-1..5 (runtime behaviour, not wiring).
