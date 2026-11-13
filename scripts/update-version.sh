#!/usr/bin/env bash
# =============================================================================
# ~/Admin-Manual/versioning/update-version.sh — THE canonical version stamper
#
# Lineage: Kintsugi-Unbroken tauri2/scripts/update-version.sh (the WORKING
# variant — the root-level Kintsugi script and every read-only re-invention
# are dead ends). Copy this file into <project>/scripts/, fill in the three
# PRODUCT_* variables, wire it into the build (`prebuild` npm hook / gradle /
# make). DO NOT re-derive this logic per project.
#
# THE ONE INVARIANT RE-INVENTIONS KEEP LOSING:
#   >>> The bump lives INSIDE the stamper. <<<
#   A stamper that only READS a version file produces v1.0.x forever.
#
# Scheme (BUILD_CONVENTIONS):
#   MAJOR = manual milestone (edit version.txt by hand, once per milestone)
#   MINOR = auto-bumped UNCONDITIONALLY on every invocation (the heartbeat;
#           resets to 0 when MAJOR changes)
#   BUILD = epoch-minutes % 100000, 5-digit zero-padded (display only)
#
#   versionName = MAJOR.MINOR.BUILD      e.g. 1.28.06942
#   versionCode = MAJOR*100000 + MINOR   (BUILD excluded — Play needs monotonic)
#
# Canonical file: version.txt (single line). Runtime mirror: version.json.
# release.lock (sourced shell: MAJOR= MINOR= BUILD_NUM=) freezes values so
# multi-artifact releases stamp identical strings; delete to resume bumping.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
PROJECT_ROOT="$(pwd)"

# ── Fill these in per project (identifier: no underscores, no hyphens — must ──
# ── equal tauri.conf.json "identifier" / applicationId EXACTLY) ──────────────
PRODUCT_NAME="Voiceish"
INTERNAL_NAME="voiceish"
PACKAGE_NAME="com.voiceish.sim"

LOCK_FILE="$PROJECT_ROOT/release.lock"
if [ -f "$LOCK_FILE" ]; then
  echo "[update-version] Using frozen release.lock values"
  # shellcheck source=/dev/null
  source "$LOCK_FILE" # sets MAJOR, MINOR, BUILD_NUM
else
  CURRENT_VERSION=$(tr -d '[:space:]' < version.txt 2>/dev/null || echo "1.0.0")
  CURRENT_MAJOR=$(echo "$CURRENT_VERSION" | cut -d. -f1)
  CURRENT_MINOR=$(echo "$CURRENT_VERSION" | cut -d. -f2)
  MAJOR="$CURRENT_MAJOR"                       # manual milestone lives in version.txt
  MINOR=$((CURRENT_MINOR + 1))                 # THE heartbeat — never remove
  BUILD_NUM=$(( $(date +%s) / 60 % 100000 ))
fi

BUILD_PADDED="$(printf '%05d' "${BUILD_NUM}")"
DISPLAY_VERSION="${MAJOR}.${MINOR}.${BUILD_PADDED}"
VERSION_CODE=$(( MAJOR * 100000 + MINOR ))
echo "Stamping version: ${DISPLAY_VERSION}  (versionCode: ${VERSION_CODE})"

# ── canonical + runtime mirror ────────────────────────────────────────────────
echo "${DISPLAY_VERSION}" > version.txt
cat > version.json << EOF
{
  "version": "${DISPLAY_VERSION}",
  "versionBase": "${MAJOR}.${MINOR}",
  "buildNumber": "${BUILD_PADDED}",
  "versionCode": ${VERSION_CODE},
  "buildDate": "$(date -Iseconds)",
  "productName": "${PRODUCT_NAME}",
  "internalName": "${INTERNAL_NAME}",
  "packageName": "${PACKAGE_NAME}"
}
EOF

# ── stamp whatever manifests the project has (each guarded, no failures) ─────
[ -f package.json ] && command -v jq >/dev/null && {
  jq --arg v "${DISPLAY_VERSION}" '.version = $v' package.json > package.json.tmp
  mv package.json.tmp package.json
}
[ -f src-tauri/tauri.conf.json ] && \
  sed -i 's/"version": "[^"]*"/"version": "'"${DISPLAY_VERSION}"'"/' src-tauri/tauri.conf.json
[ -f src-tauri/Cargo.toml ] && \
  sed -i '0,/^version *= *"[^"]*"/s//version = "'"${DISPLAY_VERSION}"'"/' src-tauri/Cargo.toml
[ -d src-tauri/gen/android ] && cat > src-tauri/gen/android/tauri.properties << EOF
tauri.android.versionCode=${VERSION_CODE}
tauri.android.versionName=${DISPLAY_VERSION}
EOF
# Gradle-native projects: have build.gradle READ version.txt at configuration
# time (Groovy expression) instead of sed-stamping it.

export PROJECT_VERSION="${DISPLAY_VERSION}"
export PROJECT_VERSION_CODE="${VERSION_CODE}"
echo "Done: ${DISPLAY_VERSION} (versionCode ${VERSION_CODE})"
