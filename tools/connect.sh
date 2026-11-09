#!/bin/bash
# Connects the Mac's adb to the test devices over Wireless debugging (Android 11+).
#   tools/connect.sh                      connects every device that is advertising and says which is which
#   tools/connect.sh parear IP:PORT CODE  pairs a new device (once per device)
# On the device: Developer options → Wireless debugging on, same Wi-Fi as the Mac. The port changes every time
# debugging is turned on, so it comes from macOS Bonjour (dns-sd), not adb's mDNS (off on some Macs: adb 37's
# aborts on the Quick Share announcement). At the end it writes ~/.cache/stagebook-aparelhos.env with TCL=, S26=,
# FIRE= (serials for adb -s); load it with: source ~/.cache/stagebook-aparelhos.env
set -uo pipefail
ADB=${ADB:-$(command -v adb)}
[ -n "$ADB" ] || { echo "adb not found (brew install android-platform-tools)"; exit 1; }
ENV_FILE="$HOME/.cache/stagebook-aparelhos.env"

# dns-sd never ends by itself: run it for a few seconds and stop it
browse() { dns-sd "$@" & local p=$!; sleep 3; kill $p 2>/dev/null; wait $p 2>/dev/null; }

if [ "${1:-}" = "parear" ]; then
    [ $# -eq 3 ] || { echo "usage: tools/connect.sh parear IP:PORT CODE (from the \"Pair device with pairing code\" screen)"; exit 1; }
    "$ADB" pair "$2" "$3" || exit 1
    echo "paired; now run tools/connect.sh"
    exit 0
fi

"$ADB" start-server >/dev/null 2>&1

# each device with debugging on advertises _adb-tls-connect; find the IP and port of each
for inst in $(browse -B _adb-tls-connect._tcp local. 2>/dev/null | awk '$2 == "Add" {print $NF}' | sort -u); do
    alvo=$(browse -L "$inst" _adb-tls-connect._tcp local. 2>/dev/null | sed -n 's/.*can be reached at \([^ ]*\) .*/\1/p' | head -1)
    host=${alvo%:*}; porta=${alvo##*:}
    [ -n "$porta" ] || { echo "$inst: port not found"; continue; }
    ip=$(browse -G v4 "${host%.}" 2>/dev/null | awk '$2 == "Add" {print $(NF-1)}' | head -1)
    [ -n "$ip" ] || { echo "$inst: IP not found"; continue; }
    "$ADB" connect "$ip:$porta" | sed "s/^/$inst: /"
done

# which is which, by manufacturer
: > "$ENV_FILE.tmp"
achou=0
while read -r serial estado _; do
    [ "$estado" = "device" ] || { [ -n "$serial" ] && echo "$serial: $estado (accept debugging on the device screen or pair again)"; continue; }
    # </dev/null: adb shell reads stdin, and inside the loop it would eat the other devices' lines
    fab=$("$ADB" -s "$serial" shell getprop ro.product.manufacturer </dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')
    modelo=$("$ADB" -s "$serial" shell getprop ro.product.model </dev/null | tr -d '\r')
    case "$fab" in
        tcl*) nome=TCL ;;
        samsung*) nome=S26 ;;
        amazon*) nome=FIRE ;;
        *) nome=OUTRO ;;
    esac
    echo "$nome=$serial  ($modelo)"
    [ "$nome" != OUTRO ] && echo "export $nome=$serial" >> "$ENV_FILE.tmp"
    achou=1
done < <("$ADB" devices | tail -n +2 | grep -v '^$')
mv "$ENV_FILE.tmp" "$ENV_FILE"
[ $achou -eq 1 ] || { echo "no device connected: turn on Wireless debugging on the device (same Wi-Fi as the Mac) or pair (tools/connect.sh parear)"; exit 1; }
echo "serials in $ENV_FILE (source it to use \$TCL, \$S26, \$FIRE)"
