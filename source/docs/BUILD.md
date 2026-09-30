# Building the Android companion

The companion has no third-party Android libraries. It can be built with the
official Android SDK tools directly, or opened as a standard Gradle project in
Android Studio.

## Direct SDK build

On Linux or macOS, install Java 17, `zip`, Android SDK Platform 35, and Android SDK
Build Tools 35.0.0. Set `ANDROID_SDK_ROOT` to your SDK directory, then run:

```bash
./scripts/build-native.sh
```

The result is `dist/voiceish-sim-0.1.4.apk`. The script compiles the resources and
Java sources, converts the classes to DEX, aligns the APK, signs it, then verifies
its APK Signature Scheme v2/v3 signatures and alignment. Minimum Android version
is Android 8.0 (API 26); target SDK is Android 15 (API 35).

If your SDK tools are installed in a nonstandard location, use:

```bash
VOICEISH_ANDROID_JAR=/absolute/path/android.jar \
VOICEISH_BUILD_TOOLS=/absolute/path/build-tools/35.0.0 \
./scripts/build-native.sh
```

The optional first and second arguments are the Android app directory and output
APK path. Both should be absolute paths when overriding them.

```bash
./scripts/build-native.sh /absolute/project/android/app /absolute/output/voiceish.apk
```

## Organizational signing (required)

Both the direct SDK build and Gradle release build read the shared organizational
key from the private `Admin-Manual` checkout:
`CREDENTIALS/PlayStore/keystore.properties` and `production.keystore`, alias
`upload`. The canonical GitHub mirror is
[Robin-s-AI-World/Admin-Manual](https://github.com/Robin-s-AI-World/Admin-Manual).
Fetch that private repository using your authorized GitHub account when Forgejo
is unavailable. The source archive deliberately contains no private signing key
or passwords; access to Admin-Manual is required to reproduce the signature.

The default checkout location is `/home/robin/Admin-Manual`. For another location:

```bash
export ADMIN_MANUAL_ROOT=/absolute/private/path/Admin-Manual
export VOICEISH_KEYSTORE="$ADMIN_MANUAL_ROOT/CREDENTIALS/PlayStore/production.keystore"
./scripts/build-native.sh
```

Set `VOICEISH_KEYSTORE` when the `storeFile` path in the shared properties points
to another machine. Alternatively, `VOICEISH_KEYSTORE_PROPERTIES` selects an
explicit properties file. Properties must include `storeFile`, `storePassword`,
`keyAlias`, and optionally `keyPassword` (defaults to `storePassword`). Relative
`storeFile` paths resolve against the properties file directory. Environment
variables `VOICEISH_KEYSTORE`, `VOICEISH_KEY_ALIAS`, `VOICEISH_STORE_PASS`, and
`VOICEISH_KEY_PASS` override the corresponding properties. Supply passwords
through a secure build environment; never commit them or print them in logs.

The build fails if signing settings are unavailable. It never generates a
replacement development key. Keep the shared key outside this project and use it
for future updates. The initial development-signed APK cannot be updated in place
with this organizational certificate: uninstall it once, install this build, and
pair again. Uninstalling clears Voiceish pairing/receipt data, not the phone SMS
inbox. Version `0.1.4` (versionCode 2) corrects permission setup and diagnostics. Install it over organizationally signed v0.1.4 without uninstalling.

## Android Studio / Gradle

Open the `android/` directory in Android Studio. The project specifies its
Android Gradle plugin version in `android/build.gradle`; use a compatible Gradle
installation and Java 17. Android Studio can install the required SDK packages.
For the pinned Android Gradle plugin 8.7.3, use Gradle 8.9. The archive does not
bundle a Gradle distribution or wrapper; the direct SDK build above does not
require Gradle. Set the same signing environment before launching Android Studio
or Gradle, then build the release variant:

```bash
cd android
gradle assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Release signing has no
development-key fallback. Android Studio sync can run without credentials, but a
release build requires them. Gradle debug variants use Android's normal debug
key and are not the organizationally signed deliverable. This rebuild was
executed with the direct SDK script; Gradle configuration was reviewed, but a
Gradle build was not run.

## Build environment used for the supplied APK

- OpenJDK 17.0.20. The existing runtime included the compiler module; the script
  supports `java -m jdk.compiler/com.sun.tools.javac.Main` when the `javac`
  launcher is absent.
- Official Android API 35, revision 2:
  [platform archive](https://dl.google.com/android/repository/platform-35_r02.zip).
- Official Android Build Tools 35.0.0 for Linux:
  [build tools archive](https://dl.google.com/android/repository/build-tools_r35_linux.zip).
- The archives were checked against the SHA-1 checksums published in Google's
  [SDK repository metadata](https://dl.google.com/android/repository/repository2-3.xml):
  `0bb560a90a7a2cbd0dd8348224d518b638fe7949` for the platform and
  `2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc` for the build tools.

Compilation and signature verification do not establish carrier delivery or
device-specific background behavior. Those require the paired phone and a test
SMS during installation.


## Phone-only setup installer

Run `bash scripts/build-setup.sh` using the same toolchain and organizational signing environment. This produces `dist/voiceish-setup-0.1.4.apk`. Validate it with `python3 scripts/verify-setup-release.py`. The included companion is pinned to the production-signed version 0.1.4. See [PHONE-SETUP.md](PHONE-SETUP.md) for install flow, security design, payload updates, and the handset verification checklist.


## Complete bundle and single HTML

After both APKs are built, run `python3 scripts/package-extension.py`, `python3 scripts/export-standalone.py`, then `python3 scripts/package-complete.py`. This produces the three deliverables described in `RELEASE-0.1.4.md`. The complete ZIP includes the APK, extension ZIP, standalone HTML with embedded CSS/JS/font/downloads, and all editable source. Python 3 alone handles exports and packaging.
