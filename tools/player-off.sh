#!/usr/bin/env bash
# The one check that the Stagebook player is off on a device, shared by every path that installs on it.
#   tools/player-off.sh <serial>    exit 0 = off; exit 1 = on, or its state could not be read (says which)
# Installing with the player on cuts the sound mid-song and the USB interface beeps. The check used to live only
# inside tools/install.sh, and a direct `adb install` of a diagnostic build went around it (observation 0027).
set -euo pipefail
SERIAL="${1:?usage: tools/player-off.sh <serial>}"
ADB=(adb -s "$SERIAL")
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "refused: device $SERIAL does not answer on adb"; exit 1; }
services="$("${ADB[@]}" shell dumpsys activity services br.app.stagebook 2>/dev/null)" || { echo "refused: could not read the player state"; exit 1; }
if grep -q "br.app.stagebook/.player.PlayerService" <<<"$services"; then
    echo "refused: the Stagebook player is on on the device (tap Turn off on the Stagebook notification)"; exit 1
fi
