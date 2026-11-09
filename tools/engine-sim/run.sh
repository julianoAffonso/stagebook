#!/bin/bash
# Simulates the audio engine on the Mac with a fake USB interface (8 outputs) and compares it with the version
# validated on the mixing desk.
#   tools/engine-sim/run.sh <44.1 kHz backing .ogg> <48 kHz backing .ogg> [folder for the desk .wav files]
# Real tracks (copyrighted: never in git), one at 44.1 kHz and one at 48 kHz.
# Needs: clang, python3, a JDK (jni.h).
# Doesn't replace the desk test: it proves the generated sound is the same and tests drops, leaks and load.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OGG=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
OGG48=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
WORK=$(mktemp -d)
trap '[ -n "${KEEP:-}" ] && echo "files in $WORK" || rm -rf "$WORK"' EXIT
JDK=${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null)}   # CI sets JAVA_HOME; on a Mac java_home finds it
case "$(uname -s)" in Darwin) JNI_OS=darwin ;; *) JNI_OS=linux ;; esac   # jni_md.h lives under the OS's name

mkdir -p "$WORK/validated" "$WORK/current"
cp "$HERE/reference/engine.c" "$WORK/validated/engine.c"   # the engine validated on the desk (23/10/2026)
cp "$ROOT/app/src/main/cpp/engine.c" "$WORK/current/engine.c"
STB="$ROOT/app/src/main/cpp/third_party"

build() {   # <name> [engine.c folder] [options]
    clang -O2 -w -I"$HERE/shim" -I"$STB" -I"$JDK/include" -I"$JDK/include/$JNI_OS" \
        -DENGINE_C="\"$WORK/${2:-$1}/engine.c\"" -DENGINE_NAME="\"$1\"" ${3:-} \
        -o "$WORK/sim-$1" "$HERE/sim.c" "$STB/stb_vorbis.c" -lm -lpthread
}
build validated
build current
build meter current -DSIM_METER   # the current one with live meters on

status=0
# REF_OGG/REF_OGG48: the validated engine plays these (OGG) while the current one plays the given ones (e.g. the same audio as WAV)
for v in validated current meter; do
    if [ $v = validated ] && [ -n "${REF_OGG:-}" ]; then
        "$WORK/sim-$v" "$REF_OGG" "$REF_OGG48" "$WORK" ${3:+"$3"} || true
    else
        "$WORK/sim-$v" "$OGG" "$OGG48" "$WORK" ${3:+"$3"} || { [ $v != validated ] && status=1; }
    fi
    echo
done
clang -O2 -w -I"$HERE/shim" -I"$STB" -I"$JDK/include" -I"$JDK/include/$JNI_OS" \
    -DENGINE_C="\"$WORK/current/engine.c\"" -o "$WORK/sim-usb1" "$HERE/sim_usb1.c" "$STB/stb_vorbis.c" -lm -lpthread
"$WORK/sim-usb1" "$WORK" || status=1
echo
echo "generated sound comparison (validated x current and x current with meters)"
python3 "$HERE/compare.py" "$WORK" || status=1
exit $status
