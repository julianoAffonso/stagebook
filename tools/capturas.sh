#!/usr/bin/env bash
# Captures folder (T39): every screenshot of the app, in Portuguese and in English, sorted by journey. Used to check
# the screens and to build the help. Run again after any change to a feature, screen or text (mandate of 30/10).
#   tools/capturas.sh [destination]   (default: ~/Desktop/stagebook-capturas)
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="${1:-$HOME/Desktop/stagebook-capturas}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for lang in pt-BR en; do
  q=""; [ "$lang" = "en" ] && q="en-rUS"
  ./gradlew -q recordRoborazziDebug --tests '*ScreenshotTest*' -PshotsLang="$q" -PshotsDir="$WORK/$lang" --rerun-tasks > "$WORK/$lang.log" 2>&1 \
    || { echo "capturas ($lang) falharam: veja $WORK/$lang.log"; tail -20 "$WORK/$lang.log"; trap - EXIT; exit 1; }
done

python3 -I tools/capturas_jornadas.py "$WORK" "$DEST"
echo "capturas em: $DEST"
