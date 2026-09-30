#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# The asset is the existing production-signed companion, checked at runtime
# against Payload.java, its package name, version and organizational signer.
# No remote payload downloads, ADB, root or silent installation.
env VOICEISH_MIN_SDK=29 VOICEISH_VERSION_CODE=5 VOICEISH_VERSION_NAME=0.1.4 \
  bash "$PROJECT_DIR/scripts/build-native.sh" "$PROJECT_DIR/android/setup" \
  "$PROJECT_DIR/dist/voiceish-setup-0.1.4.apk"
