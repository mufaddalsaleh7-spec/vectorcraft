#!/usr/bin/env bash
# Build VectorCraft's WebAssembly app, package it in an offline Android WebView APK, and copy the
# installable debug APK to dist/android/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT/packaging/android"

for tool in cargo rustup trunk gradle; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "error: $tool is required (see packaging/android/README.md)" >&2
    exit 1
  fi
done

rustup target add wasm32-unknown-unknown
(cd "$ROOT/apps/vectorcraft-web" && trunk build --release)

gradle --no-daemon --project-dir "$ANDROID_DIR" assembleDebug

APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "error: Gradle did not produce $APK" >&2
  exit 1
fi
mkdir -p "$ROOT/dist/android"
cp "$APK" "$ROOT/dist/android/vectorcraft-android-debug.apk"
echo "Built $ROOT/dist/android/vectorcraft-android-debug.apk"
