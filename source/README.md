# Phone-only installation

Start with **voiceish-setup-0.1.4.apk**. See [phone setup](docs/PHONE-SETUP.md) for the no-ADB installation flow and [build settings](docs/BUILD.md) for the production signing configuration. This adds a separate installer; the companion below is now version 0.1.4.

# Voiceish SIM 0.1.4 — permission setup correction

Install the APK over organizationally signed v0.1.4. No uninstall or re-pair is needed.

After granting required permissions, step 1 now reads **Phone access complete** and the button becomes **Continue — start bridge**. Tap it, then copy the pairing text under step 2 into the website or extension. Notifications are optional and requested separately.

The app separately shows **Read SMS**, **Send SMS**, and **Phone / SIM status**. If any still reads **NOT granted**, open Android App info → Permissions. For restricted SMS access, check whether your phone offers **Allow restricted settings** in the App info menu. This app cannot override an Android permission denial. If Android Settings and the app disagree, tap **Copy permission diagnostics** and share the resulting text. It includes no SMS, phone numbers or pairing secrets.

This fixes the misleading setup flow; it does not prove why a particular handset reports a permission denial. Physical-device testing is still required.

Website: https://voipmsms.robinc.chatgpt.site

# Voiceish SIM — first installable build

Your existing Voiceish messenger, connected directly to your Android phone's SMS SIM.

## What is included

- Native Android companion APK, Android 8 or newer; built against Android 15.
- Unpacked Chrome extension for desktop Chrome on Windows, Linux, or macOS.
- Original Voiceish v0.3.0 conversation styling and icons, adapted for phone SMS.
- Read recent SMS conversations, start a conversation, reply, search, and refresh.
- Primary SIM only: specifically the SIM selected for **SMS** in Android's SIM settings.
- Local Wi-Fi connection with encrypted, authenticated message payloads and revocable pairing.

The extension runs in **desktop Chrome**. Install the APK on the phone. Both devices must be reachable on the same local network for this version. No VoIP.ms account, online relay, subscription, or Google account is required. Normal carrier SMS charges still apply.

## Install

1. On Android, open `voiceish-sim-0.1.4.apk` and permit installation from the app you used to open it if Android asks.
2. In Android's SIM settings, ensure your intended primary SIM is selected for SMS; do not leave it on “Ask every time.”
3. Open **Voiceish SIM**, grant the requested SMS and phone permissions, and start the bridge. Check the displayed SIM. Keep your current default messaging app. Notifications make the bridge's running state visible.
4. On your computer, unzip `voiceish-sim-chrome-0.1.4.zip` into a permanent folder. Open `chrome://extensions`, turn on **Developer mode**, select **Load unpacked**, and choose the extracted folder containing `manifest.json`.
5. Copy the pairing text displayed on the phone to the extension's setup field. Treat this text as a password. Confirm Chrome's requested access to that phone address.
6. Open a conversation, or choose **New message** and enter a phone number. **Enter sends; Shift+Enter adds a line.**

The phone's bridge must remain running while texting from Chrome. After rebooting the phone, open Voiceish SIM and start it again. If your router changes the phone's IP address, update the extension's phone address using the address displayed by the app.

If Android blocks the SMS permission for a sideloaded app, open **Settings → Apps → Voiceish SIM → ⋮ → Allow restricted settings** (if shown), then grant SMS under **Permissions** and return to the app. The availability of this step depends on the Android version and installer. Only do this for the APK you intended to install. The app includes a shortcut to its app settings.

## Boundaries of this first version

- **SMS only.** RCS, iMessage, pictures, MMS, calls, contact-name lookup, and second-SIM selection are not included.
- Shows up to 300 recent SMS records for the pinned SIM. Provider records without that SIM's subscription identifier are excluded to avoid crossing identities.
- Inbox polling occurs while the extension is open; this is not a background-notification product yet.
- “Sent” means Android reports successful radio submission for every segment. It is not a recipient-read receipt or delivery guarantee.
- If a connection drops during sending, check the receipt and phone inbox before explicitly composing another message. The extension never automatically resends an uncertain submission.
- Changing the default SMS SIM requires restarting the bridge, then choosing **Forget phone & erase drafts** in the extension and pairing again. The app will not silently send through a different SIM. Save any draft you need before forgetting.
- Android vendors can restrict background networking during sleep. If the bridge stops responding, open the phone app; if necessary set its battery usage to Unrestricted in Android app settings. Guest Wi-Fi/client isolation may prevent direct phone-to-PC traffic.
- This APK uses your shared organizational signing key from Admin-Manual. If you installed the earlier development-signed APK, uninstall it once before installing this rebuild, then pair again. Uninstalling clears local pairing/receipt data; it does not delete the phone's SMS inbox. Physical-phone qualification remains outstanding.

## Privacy and connection

Message bodies and commands are AES-256-GCM encrypted with a random shared pairing key before crossing the local network. The local HTTP connection reveals connection metadata, but not those encrypted contents. Pairing secrets are protected by Android Keystore on the phone; the desktop copy is stored in that Chrome profile's local extension storage, never Chrome Sync. The extension does not permanently copy the inbox, but retains local drafts and outgoing-attempt records. Android stores outgoing receipts in its private application database and disables backups.

Use **Forget phone** in the extension to clear its pairing and drafts. Rotate the key in the Android app to revoke previously paired browsers. The service listens only for local/private peers, but a person with the pairing text can use it on the local network; keep it private.

## Source and build

See `docs/PROTOCOL.md` for the shared phone/browser API and `docs/VERIFICATION.md` for the exact checks performed and outstanding phone checks.

For automated checks, install Node 22+ and Java 17, run `npm install` in the source root, then `npm test`. These tests use synthetic messages only and cannot reach a real phone.

The source archive contains `android/`, `extension/`, tests, and the SDK-only build script. There are no Android third-party runtime dependencies or remote extension scripts. Open `android/` in Android Studio to build with its Gradle project, or use `scripts/build-native.sh` with an Android SDK and Java 17. See `docs/BUILD.md` for required SDK/Java versions, build commands, and Admin-Manual signing settings. Release builds require your existing organizational key; private credentials are not included in this source archive.

Derived from `voipms-voiceish-chrome-extension-v0.3.0-voxvolley-mit-20260802.zip`. Original MIT license retained. New work © 2026 Robin L. M. Cheung.

## Platform references

- [Android SmsManager](https://developer.android.com/reference/android/telephony/SmsManager): subscription-specific multipart SMS and sent callbacks.
- [Android foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types): the remoteMessaging service type fits cross-device text continuity.
- [Chrome extension network requests](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests): host permission for extension-origin requests to the paired phone.
- [Android 15 compatibility requirements](https://source.android.com/docs/compatibility/15/android-15-cdd): restricted-settings handling for sideloaded applications.
