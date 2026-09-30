#!/usr/bin/env bash
# Build the dependency-free Java Android app using the official SDK tools.
# No Gradle installation or Maven downloads are required.
set -euo pipefail
umask 077

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${1:-$PROJECT_DIR/android/app}"
OUTPUT_APK="${2:-$PROJECT_DIR/dist/voiceish-sim-0.1.4.apk}"
# Release filenames are immutable. Bump both versionName and versionCode for a changed release.
if [[ -e "$OUTPUT_APK" ]]; then
  echo "Refusing to replace existing release: $OUTPUT_APK. Use a new version and filename." >&2
  exit 1
fi

TOOLCHAIN_DIR="${VOICEISH_TOOLCHAIN:-$PROJECT_DIR/../toolchain}"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ -n "$SDK_DIR" ]]; then
  ANDROID_JAR="${VOICEISH_ANDROID_JAR:-$SDK_DIR/platforms/android-35/android.jar}"
  BUILD_TOOLS="${VOICEISH_BUILD_TOOLS:-$SDK_DIR/build-tools/35.0.0}"
else
  ANDROID_JAR="${VOICEISH_ANDROID_JAR:-$TOOLCHAIN_DIR/platform/android-35/android.jar}"
  BUILD_TOOLS="${VOICEISH_BUILD_TOOLS:-$TOOLCHAIN_DIR/build-tools/android-15}"
fi

if [[ ! -f "$ANDROID_JAR" || ! -x "$BUILD_TOOLS/aapt2" ]]; then
  cat >&2 <<'HELP'
Android API 35 and SDK Build Tools 35.0.0 are required.
Install them with Android Studio's SDK Manager, then set ANDROID_SDK_ROOT.
Alternatively set VOICEISH_ANDROID_JAR and VOICEISH_BUILD_TOOLS explicitly.
HELP
  exit 1
fi

command -v java >/dev/null || { echo 'Java 17+ is required.' >&2; exit 1; }
command -v zip >/dev/null || { echo 'The zip command is required.' >&2; exit 1; }
if command -v javac >/dev/null; then
  JAVAC=(javac)
else
  JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi
"${JAVAC[@]}" -version

SOURCE_DIR="$APP_DIR/src/main"
BUILD_DIR="$APP_DIR/build/native"
mkdir -p "$BUILD_DIR" "$(dirname -- "$OUTPUT_APK")"
# Only erase this script's generated working directories, never source files.
rm -rf -- "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex"
rm -f -- "$BUILD_DIR/classes.zip" "$BUILD_DIR/resources.zip" "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex"

RES_ARGS=()
if [[ -d "$SOURCE_DIR/res" ]] && find "$SOURCE_DIR/res" -type f -print -quit | grep -q .; then
  "$BUILD_TOOLS/aapt2" compile --dir "$SOURCE_DIR/res" -o "$BUILD_DIR/resources.zip"
  RES_ARGS=(-R "$BUILD_DIR/resources.zip")
fi
ASSET_ARGS=()
if [[ -d "$SOURCE_DIR/assets" ]]; then
  ASSET_ARGS=(-A "$SOURCE_DIR/assets")
fi
MANIFEST="$SOURCE_DIR/AndroidManifest.xml"
if ! grep -qE 'package[[:space:]]*=' "$MANIFEST"; then
  # AGP 8 obtains this from Gradle; aapt2 needs it in its temporary manifest.
  APPLICATION_ID="${VOICEISH_APPLICATION_ID:-$(sed -nE "s/^[[:space:]]*applicationId[[:space:]]+['\"]([^'\"]+)['\"].*/\1/p" "$APP_DIR/build.gradle" | head -n 1)}"
  if [[ ! "$APPLICATION_ID" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
    echo 'Set VOICEISH_APPLICATION_ID to the Android package name.' >&2
    exit 1
  fi
  sed "s/<manifest /<manifest package=\"$APPLICATION_ID\" /" "$MANIFEST" > "$BUILD_DIR/AndroidManifest.xml"
  MANIFEST="$BUILD_DIR/AndroidManifest.xml"
fi
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  --min-sdk-version "${VOICEISH_MIN_SDK:-26}" --target-sdk-version 35 \
  --version-code "${VOICEISH_VERSION_CODE:-5}" --version-name "${VOICEISH_VERSION_NAME:-0.1.4}" \
  --java "$BUILD_DIR/generated" \
  --auto-add-overlay \
  "${RES_ARGS[@]}" "${ASSET_ARGS[@]}" \
  -o "$BUILD_DIR/resources.apk"

JAVA_SOURCES=()
while IFS= read -r -d '' JAVA_SOURCE; do
  JAVA_SOURCES+=("$JAVA_SOURCE")
done < <(find "$SOURCE_DIR/java" "$BUILD_DIR/generated" -type f -name '*.java' -print0)
if (( ${#JAVA_SOURCES[@]} == 0 )); then
  echo 'No Java source files found.' >&2
  exit 1
fi
"${JAVAC[@]}" -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$BUILD_TOOLS/core-lambda-stubs.jar" \
  -d "$BUILD_DIR/classes" "${JAVA_SOURCES[@]}"

(cd "$BUILD_DIR/classes" && zip -q -r "$BUILD_DIR/classes.zip" .)
"$BUILD_TOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.zip"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q "$BUILD_DIR/unsigned.apk" classes*.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

# Java Properties handles escaping correctly and keeps passwords out of shell output.
# Never generate a substitute key: releases use the shared Admin-Manual key.
cat > "$BUILD_DIR/SignApk.java" <<'JAVA'
import java.nio.file.*;
import java.util.Properties;
class SignApk {
    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
    public static void main(String[] args) throws Exception {
        Path propsFile = Path.of(env("VOICEISH_KEYSTORE_PROPERTIES",
            env("ADMIN_MANUAL_ROOT", "/home/robin/Admin-Manual")
            + "/CREDENTIALS/PlayStore/keystore.properties")).toAbsolutePath();
        Properties props = new Properties();
        if (Files.isRegularFile(propsFile)) {
            try (var input = Files.newInputStream(propsFile)) { props.load(input); }
        }
        String file = env("VOICEISH_KEYSTORE", props.getProperty("storeFile", ""));
        String alias = env("VOICEISH_KEY_ALIAS", props.getProperty("keyAlias", ""));
        String storePass = env("VOICEISH_STORE_PASS", props.getProperty("storePassword", ""));
        String keyPass = env("VOICEISH_KEY_PASS", props.getProperty("keyPassword", storePass));
        if (file.isEmpty() || alias.isEmpty() || storePass.isEmpty() || keyPass.isEmpty()) {
            System.err.println("Shared signing settings missing. Set ADMIN_MANUAL_ROOT or VOICEISH_KEYSTORE_PROPERTIES; see docs/BUILD.md. No replacement key will be generated.");
            System.exit(1);
        }
        Path key = Path.of(file);
        if (!key.isAbsolute()) key = propsFile.getParent().resolve(key);
        if (!Files.isRegularFile(key)) {
            System.err.println("Shared keystore unavailable. Set VOICEISH_KEYSTORE to its local absolute path.");
            System.exit(1);
        }
        ProcessBuilder signer = new ProcessBuilder(args[0], "sign",
            "--ks", key.toString(), "--ks-key-alias", alias,
            "--ks-pass", "env:VOICEISH_STORE_PASS", "--key-pass", "env:VOICEISH_KEY_PASS",
            "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true", "--out", args[1], args[2]);
        signer.environment().put("VOICEISH_STORE_PASS", storePass);
        signer.environment().put("VOICEISH_KEY_PASS", keyPass);
        System.exit(signer.inheritIO().start().waitFor());
    }
}
JAVA
java "$BUILD_DIR/SignApk.java" "$BUILD_TOOLS/apksigner" "$OUTPUT_APK" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT_APK"
"$BUILD_TOOLS/zipalign" -c 4 "$OUTPUT_APK"
chmod 644 "$OUTPUT_APK"
echo "Built and signature-verified: $OUTPUT_APK"
