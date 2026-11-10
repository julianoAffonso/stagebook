#!/usr/bin/env bash
# Installs an already built APK that is NOT the last commit's (a local diagnostic build), behind the player lock.
#   tools/install-apk.sh <serial> [apk]   (default apk: the release build in app/build/outputs)
# For the commit's build use tools/install.sh, which also checks the tree and the audit mark. This one exists so that
# a diagnostic build never goes in with a bare `adb install`: same refusal with the player on (tools/player-off.sh).
# Prints the version found on the device afterwards; it never uninstalls.
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${1:?usage: tools/install-apk.sh <serial> [apk]}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
[ -f "$APK" ] || { echo "refused: no APK at $APK (build it first)"; exit 1; }
tools/player-off.sh "$SERIAL" || exit 1
ADB=(adb -s "$SERIAL")
out="$("${ADB[@]}" install -r "$APK" 2>&1)" || { echo "not installed: $out"; exit 1; }
got="$("${ADB[@]}" shell dumpsys package br.app.stagebook | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"
echo "installed: $got"
