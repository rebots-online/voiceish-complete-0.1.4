# Voiceish phone-only setup

No ADB, USB debugging, root, computer, or developer options are required to install the Android companion.

## Install on your phone

1. Download **voiceish-setup-0.1.4.apk**, install it using Android's normal prompt, and open **Voiceish Setup**. If asked, approve app installation for the browser or file manager you used.
2. Tap **Allow Voiceish Setup to install**, enable **Allow from this source**, and return. Tap **Install / update Voiceish SIM**, then approve Android's prompt. Keep Setup open until the prompt appears. This installs the bundled, production-signed Voiceish SIM 0.1.4.
3. Tap **Open Voiceish SIM**, allow SMS and phone access, and start the bridge. If Android does not show another permission prompt, use **Voiceish SIM permissions** in Setup to check its SMS permission.
4. Once all three permissions show allowed and the bridge starts, pair the Chrome extension or website using the phone's pairing text. Keep the phone and computer on the same Wi-Fi.

The matching production-signed app is updated without uninstalling or deleting its data. A version signed with another key is rejected; Setup never uninstalls it automatically. Setup also refuses to downgrade a newer version.

If Send SMS still shows denied, copy **setup diagnostics** from Voiceish Setup. This includes Android's installer identity and the SMS installer allowlist when Android permits querying it. It contains no messages, phone numbers, or pairing secrets. Do not repeatedly reinstall or erase app data as a troubleshooting step. Android or device policy may still block the permission; this build cannot override those restrictions.

After setup succeeds, Voiceish Setup can be uninstalled; leave Voiceish SIM installed. Notifications are optional. Your usual SMS app remains in place.

## What changes

Voiceish Setup uses Android's public PackageInstaller session API. The session explicitly allowlists READ_SMS and SEND_SMS so the companion may request them. Allowlisting is not a runtime permission grant: you still approve access yourself. It uses a local bundled APK, declares no network or SMS permissions, and does not install arbitrary files or fetch executable code.

The installer validates the companion package name, version, full APK SHA-256, and organizational signing certificate before writing an install session. Android also verifies the APK on installation. The result callback is an explicit mutable PendingIntent to a non-exported receiver. All install sessions require user confirmation. Pending sessions can be cancelled and retried if the process was closed or a confirmation could not be shown.

- Setup package: `com.voiceish.setup`, version 0.1.4 (5), Android 10/API 29 or newer.
- Companion package: `com.voiceish.sim`, version 0.1.4 (5), with a new Android version code and displayed version; SMS behavior is unchanged.
- Both certificate SHA-256: `56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943`.

This is a supported Android installation mechanism, not a confirmed fix for every vendor policy. Compilation, signatures, bundled payload, manifest and package checks have been verified. On-device install, permission grant and carrier SMS sending on the Samsung SM-F946W/API 36 still require a handset check.

## Build

Use the Java 17 and Android SDK 35 toolchain and production signing settings described in BUILD.md. Then run:

```sh
bash scripts/build-setup.sh
python3 scripts/verify-setup-release.py
```

The output is `dist/voiceish-setup-0.1.4.apk`. The source includes the version-matched production-signed companion under `android/setup/src/main/assets/voiceish-sim.apk` and its pinned hash in `Payload.java`. The normal companion build remains `scripts/build-native.sh`. No keystore or password is included in this project.

For Gradle with the same private signing environment, the installer module is `:setup:assembleRelease`. Debug builds use Android's development signing and are not the delivered production-signed setup APK.

When deliberately updating the bundled companion, build it with the production key, review its package/version changes, then update the asset, `Payload.java`, the expected companion version in MainActivity, and the release checks together. Do not replace the payload without updating and verifying its pin. No build step generates a substitute production key.

## Phone verification checklist

- Install Setup through a browser/file manager without ADB and approve its install-source setting.
- Confirm the Android install/update dialog; reject/cancel once and verify setup can retry.
- Update an existing production-signed 0.1.4 installation and confirm pairing data remains.
- Rotate or leave/reopen Setup while installation is pending; complete or cancel/retry.
- Open companion, grant SMS/phone, return to Setup and confirm READ_SMS, SEND_SMS and READ_PHONE_STATE all say allowed.
- If denied, capture Setup's copyable diagnostics, including installer identity/allowlist.
- Start bridge; pair, receive an actual SMS, and send a user-chosen test text through the default SMS SIM.
- Verify the flow with the phone locked and verify no false ready state when SEND_SMS is denied.

Reference: [Android installer permission allowlisting](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setWhitelistedRestrictedPermissions(java.util.Set%3Cjava.lang.String%3E)).
