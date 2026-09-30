# Verification — Voiceish release 0.1.4

Both Android packages now have versionName 0.1.4 and versionCode 5:

| Component | Package | File |
| --- | --- | --- |
| Companion | com.voiceish.sim | voiceish-sim-0.1.4.apk |
| Phone installer | com.voiceish.setup | voiceish-setup-0.1.4.apk |

Both are signed with the shared production organizational certificate. SHA-256:
`56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943`.

The SDK build and APK signature verification were run for both packages. `scripts/verify-setup-release.py` checks version names/codes, package identities, minimum and target SDKs, installer permissions, payload hash and exact embedded companion identity. The source archive includes the version-matched signed companion payload and all sources to build both apps.

The release adds the imported navy/gold theme and corrects versioning and artifact identity. SMS behavior and installation mechanics are unchanged from the preceding implementation. Compilation and signature verification do not confirm a successful runtime SEND_SMS grant on Samsung. Device installation/update, user confirmation, permission recovery, actual carrier SMS, and locked-phone connectivity still need handset verification. See PHONE-SETUP.md.

The extension's now uses the navy/gold theme at version 0.2.2 and is included in this 0.1.4 source release. Earlier protocol and UI tests are included for rerunning; they use synthetic data and are not evidence of a carrier round trip. The web source is included under web/.

Release files are immutable: build and packaging scripts refuse to replace existing output files. A changed published APK requires a new versionName, a higher versionCode, a new filename and updated checksums. Do not re-sign or replace an already published version under its old filename.
