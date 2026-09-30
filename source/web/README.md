# Voiceish SIM web workspace

Installable static PWA and updated Chrome extension for the existing organizationally signed Voiceish SIM Android companion. No hosted SMS relay or server worker is used.

## Use

Open the Site, choose Connect your phone, install Voiceish Setup on the handset, allow it to install the bundled companion, approve Android’s install screen, then open Voiceish SIM, grant SMS/phone permissions and start its bridge. No ADB is needed. The handset must still verify SEND_SMS is granted; this is not yet a verified Samsung recovery. Paste its pairing JSON into the direct connection form. Allow the browser's local-network prompt if offered. Both devices must be on the same reachable private network.

If the browser blocks HTTPS-to-local-HTTP requests, use the Chrome extension route. Download and unzip the extension, load the directory through chrome://extensions → Developer mode → Load unpacked, pair in the extension popup, and reload the Site. The content script is restricted to the exact Site origin and forwards only an enumerated set of message operations. It never exposes the pairing key to the Site. Chrome permission requests occur in the extension popup.

The existing Android protocol and SMS radio behavior are unchanged. Device connection, SMS permissions and a ready pinned default SIM are required before the web app enables sending. In demo mode, sends affect sample messages only. No actual SMS is sent by tests.

## Source and deployment

Authored static assets are in dist. There is no compilation step or external JavaScript runtime dependency. extension contains the bundled Chrome source. DESIGN.md is the StitchDesign semantic design system.

core.js reuses the existing AES-GCM protocol. engine.js adapts the existing conservative extension state machine to browser-local storage; send IDs are durably saved before network dispatch, ambiguous receipts block automatic retries, and a SIM change requires re-pairing. Pairing keys and drafts in direct mode are stored unencrypted in this browser profile, as disclosed during setup. Inbox data remains in memory. Switching between direct and extension routes uses separate pairing and draft stores.

The service worker caches static app assets only. It never caches SMS responses, phone requests, pairing state, downloads, or authenticated document navigation. The hosted app follows the current Site access settings. Offline opening is not promised.

## Checks

Run `node --test tests/engine.test.mjs` for synthetic-phone tests of AES-GCM reads, sends, receipt reconciliation and SIM changes. JavaScript syntax and asset references must pass before deployment. Live carrier delivery, locked-phone connectivity, install prompts and real-browser local-network access require the user's devices.

## Packaging

Rebuild dist/voiceish-sim-chrome-0.2.2.zip from the extension folder with manifest.json at the archive root. Do not include private credentials or Android keystores. The downloadable APK is the existing organizationally signed v0.1.4 update with corrected permission setup and diagnostics.


The phone setup download is `dist/voiceish-setup-0.1.4.apk`, a separate installer signed with the same organizational certificate. It bundles the production-signed Voiceish SIM 0.1.4 and uses a user-confirmed PackageInstaller session explicitly allowlisting READ_SMS and SEND_SMS. It checks package, version, payload hash and signer before installation, and reports runtime grants afterward. It never automatically grants SMS access, uninstalls the old app, or downloads arbitrary APKs. Source and build instructions are delivered separately as `voiceish-sim-source-0.1.4.zip`.


Release 0.1.4 matches the imported navy/cobalt/gold Voiceish reference across Android, extension and website. The complete downloadable release includes a standalone HTML with embedded CSS/JS/font and downloads, all binaries and all source. The extension version is 0.2.2.
