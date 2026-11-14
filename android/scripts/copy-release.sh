#!/usr/bin/env bash
# Copies the signed release APKs to /release at the repo root (overwrites in place).
# Run after: ./gradlew :customer:assembleRelease :picker:assembleRelease
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/android"

for f in customer:customer picker:picker; do
  mod="${f%%:*}"; name="${f##*:}"
  src="$OUT/$mod/build/outputs/apk/release/$mod-release.apk"
  [ -f "$src" ] || { echo "missing $src — build it first" >&2; exit 1; }
  cp "$src" "$ROOT/release/railbite-$name.apk"
  echo "copied $src -> release/railbite-$name.apk"
done
