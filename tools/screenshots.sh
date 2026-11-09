#!/usr/bin/env bash
# Captures folder (T39): every screenshot of the app, in Portuguese and in English, sorted by journey. Used to check
# the screens and to build the help. Run again after any change to a feature, screen or text (mandate of 30/10).
#   tools/screenshots.sh [destination]   (default: ~/Desktop/stagebook-capturas)
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

# the editor on the computer: its real page with sample data, photographed by Chrome (same two languages)
python3 -I tools/screenshots_web.py "$WORK" > "$WORK/web.log" 2>&1 || { echo "capturas do editor web falharam: veja $WORK/web.log"; tail -20 "$WORK/web.log"; trap - EXIT; exit 1; }
python3 -I tools/screenshots_journeys.py "$WORK" "$DEST"
# screens the tests can't draw, photographed on a real device and kept in git (same folders and two languages)
cp -R tools/device-shots/. "$DEST/"
echo "capturas em: $DEST"
