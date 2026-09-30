# Voice-ish SIM Chrome extension

The original Voice-ish 0.3.0 interface, adapted for the Android phone companion’s primary SMS SIM. Original icons, blue toolbar, conversation layout, bubble styling and green send button are retained under the included MIT license (Copyright © 2026 Robin).

## Load and pair

1. Open `chrome://extensions` in desktop Chrome. Enable **Developer mode**, choose **Load unpacked**, and select this `extension` folder.
2. On Android, install and open the companion APK, allow its SMS/phone permissions, and start the bridge. Keep phone and desktop on the same local network.
3. Copy the phone’s pairing JSON into the extension. Choose **Connect my phone**, and allow access to that phone’s exact local host if Chrome asks.
4. Pick a conversation or choose **New message**. **Enter** sends; **Shift+Enter** adds a line. The ↗ toolbar button opens a full tab.

Only the primary/default SMS SIM is available. MMS, attachments, RCS and calls are not included. Alphanumeric sender names can be read but cannot receive SMS replies. Standard carrier SMS charges apply.

The extension refreshes every five seconds while its window is visible. It has no background notification polling. The latest 300 provider messages and up to 100 phone send receipts are displayed; the inbox stays in memory. Your pairing key and drafts (including unresolved send attempts) use Chrome local storage, never sync.

## Phone address or SIM changed

Use **Settings → Phone address** after a Wi-Fi address change. Chrome grants access to the selected host; host permissions cannot restrict a TCP port, but requests use only the configured endpoint. The manifest declares HTTP hosts as *optional* so a phone at any private IPv4 address can be paired; no host access is granted on installation.

The first successful connection binds this browser to the bridge’s selected subscription. If you change Android’s default SMS SIM, restart the bridge, then choose **Forget phone & erase drafts** and pair again. This explicit step prevents existing drafts or replies silently moving to another SIM.

**Forget phone & erase drafts** removes the local key, drafts and granted host permissions. To revoke access on the phone itself, regenerate its key.

## Interrupted sending

Every send receives an ID that is saved before contacting the phone. The phone also persists that ID before dispatching an SMS. Reopening Chrome or refreshing never automatically repeats a send. If delivery is uncertain, check the phone’s SMS app first; **I checked my phone — compose again** restores a draft that you may explicitly send as a new message. A failed multipart message can have partially sent.

Conversations are grouped by exact number after punctuation removal. The extension does not guess country codes or conflate distinct phone numbers. Send receipts are reconciled conservatively with provider messages, so ambiguous duplicates remain visible instead of hiding a possible extra send. Android may not place a third-party send into the system SMS provider on every device; the phone’s persistent send receipts still appear here.

## Verification

Run `npm test` (Node 20+). Tests use synthetic encrypted requests in memory; they never contact a phone or send a carrier SMS. Tests cover authenticated encryption, large inbox payloads, endpoint validation, response ID binding, conservative receipt matching, saved IDs across worker reloads, no automatic retransmission, and SIM binding. Real Chrome local-network permission behavior and carrier sending need device qualification.
