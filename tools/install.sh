#!/usr/bin/env bash
# Installs the last commit's build on a test device, behind the project's locks.
#   tools/install.sh <serial> [--debug]  (serials come from: source ~/.cache/stagebook-aparelhos.env → $TCL, $S26)
# --debug: the development build, for a device that already has it (the signature must match)
# Refuses, without installing, if: there are uncommitted changes, the audit mark exists, the device doesn't answer, or
# the Stagebook player is on (installing with the player on cuts the sound mid-song). Uses the signed release build,
# the same as GitHub's: over an install with another signature Android refuses and NOTHING is erased (it never
# uninstalls). Prints "installed: <version>" only after reading the version on the device and checking it's the commit's.
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${1:?usage: tools/install.sh <serial> [--debug]}"
VARIANT=release; [ "${2:-}" = "--debug" ] && VARIANT=debug
ADB=(adb -s "$SERIAL")
# audit-pending marker: its path lives outside the repository (~/.cache/stagebook-audit-mark holds it; none = no check)
MARK=$(cat "$HOME/.cache/stagebook-audit-mark" 2>/dev/null || true)
# without it the audit lock is off: say so instead of installing quietly (audit 02/11)
[ -n "$MARK" ] || echo "warning: no ~/.cache/stagebook-audit-mark, the audit lock is off" >&2
MARK="${MARK/#\~/$HOME}"   # a path written with ~

[ -z "$(git status --porcelain)" ] || { echo "refused: uncommitted changes"; exit 1; }
[ -z "$MARK" ] || [ ! -e "$MARK" ] || { echo "refused: audit pending ($MARK)"; exit 1; }
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "refused: device $SERIAL does not answer on adb"; exit 1; }
services="$("${ADB[@]}" shell dumpsys activity services br.app.stagebook 2>/dev/null)" || { echo "refused: could not read the player state"; exit 1; }
if grep -q "br.app.stagebook/.player.PlayerService" <<<"$services"; then
    echo "refused: the Stagebook player is on on the device (tap Turn off on the Stagebook notification)"; exit 1
fi

want="0.1.$(git rev-list --count HEAD) ($(git rev-parse --short HEAD))"
if [ "$VARIANT" = debug ]; then ./gradlew -q assembleDebug; else ./gradlew -q assembleRelease; fi
apk=app/build/outputs/apk/$VARIANT/app-$VARIANT.apk
out="$("${ADB[@]}" install -r "$apk" 2>&1)" || { echo "not installed: $out"; exit 1; }
got="$("${ADB[@]}" shell dumpsys package br.app.stagebook | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"
[ "$got" = "$want" ] || { echo "version on the device ($got) is not the commit one ($want)"; exit 1; }
echo "installed: $got"
