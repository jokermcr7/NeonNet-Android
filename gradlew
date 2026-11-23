#!/usr/bin/env sh
set -eu
VERSION=8.11.1
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$VERSION-bin/neonnet"
BIN="$BASE/gradle-$VERSION/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  else wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"; fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$BIN" "$@"
