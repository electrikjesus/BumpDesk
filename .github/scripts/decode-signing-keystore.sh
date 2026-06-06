#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-app/release.keystore}"

if [ -z "${SIGNING_KEY:-}" ]; then
  echo "::error::SIGNING_KEY repository secret is not set. See keystore.properties.example."
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"

# Secrets pasted from wrapped base64 or copied with stray whitespace break plain base64 -d.
printf '%s' "$SIGNING_KEY" | tr -d '[:space:]' | base64 --decode --ignore-garbage > "$OUTPUT_PATH"

if [ ! -s "$OUTPUT_PATH" ]; then
  echo "::error::Decoded keystore is empty. Update SIGNING_KEY using: base64 -w0 keystore/bumpdesk-release.jks"
  exit 1
fi

if ! file "$OUTPUT_PATH" | grep -q 'Java KeyStore'; then
  echo "::error::Decoded file is not a Java KeyStore. SIGNING_KEY must be base64 of the .jks file, not the raw keystore."
  exit 1
fi

echo "Decoded signing keystore to $OUTPUT_PATH ($(wc -c < "$OUTPUT_PATH") bytes)"
