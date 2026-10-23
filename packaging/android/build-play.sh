#!/usr/bin/env bash
# Build the signed Android App Bundle for Google Play using a private upload key supplied locally
# or by CI secrets. Never commit the keystore or its passwords.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT/packaging/android"

for tool in cargo rustup trunk gradle; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "error: $tool is required (see packaging/android/README.md)" >&2
    exit 1
  fi
done

for name in ANDROID_KEYSTORE_PATH ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    echo "error: $name is required; configure your private Play upload key first" >&2
    exit 1
  fi
done
if [ ! -f "$ANDROID_KEYSTORE_PATH" ]; then
  echo "error: keystore not found at ANDROID_KEYSTORE_PATH" >&2
  exit 1
fi

rustup target add wasm32-unknown-unknown
(cd "$ROOT/apps/vectorcraft-web" && trunk build --release)
gradle --no-daemon --project-dir "$ANDROID_DIR" bundleRelease

VERSION="$(awk '/^\[workspace.package\]/{in_pkg=1;next} /^\[/{in_pkg=0} in_pkg && $1=="version"{gsub(/[" ]/, "", $3);print $3;exit}' "$ROOT/Cargo.toml")"
AAB="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"
if [ ! -f "$AAB" ]; then
  echo "error: Gradle did not produce $AAB" >&2
  exit 1
fi
mkdir -p "$ROOT/dist/android"
cp "$AAB" "$ROOT/dist/android/vectorcraft-${VERSION}-play.aab"
echo "Built $ROOT/dist/android/vectorcraft-${VERSION}-play.aab"
