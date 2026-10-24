#!/bin/bash
# Simula o motor de áudio no Mac com uma interface USB falsa (8 saídas) e compara com a versão validada na mesa.
#   tools/engine-sim/run.sh <backing 44,1 kHz .ogg> <backing 48 kHz .ogg> [pasta para os .wav da mesa]
# Faixas reais (com direitos autorais: nunca no git): ~/Desktop/previous-app-faixas; a 48 kHz é bohemian-rhapsody-2.
# Precisa: clang, python3, um JDK (jni.h).
# Não substitui o teste na mesa: prova que o som gerado é o mesmo e testa quedas, vazamento e consumo.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OGG=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
OGG48=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
WORK=$(mktemp -d)
trap '[ -n "${KEEP:-}" ] && echo "arquivos em $WORK" || rm -rf "$WORK"' EXIT
JDK=$(/usr/libexec/java_home)

mkdir -p "$WORK/validado" "$WORK/atual"
cp "$HERE/reference/engine.c" "$WORK/validado/engine.c"   # o motor validado na mesa (the previous app, 23/10/2026)
cp "$ROOT/app/src/main/cpp/engine.c" "$WORK/atual/engine.c"
STB="$ROOT/app/src/main/cpp/third_party"

build() {   # <nome> [pasta do engine.c] [opções]
    clang -O2 -w -I"$HERE/shim" -I"$STB" -I"$JDK/include" -I"$JDK/include/darwin" \
        -DENGINE_C="\"$WORK/${2:-$1}/engine.c\"" -DENGINE_NAME="\"$1\"" ${3:-} \
        -o "$WORK/sim-$1" "$HERE/sim.c" "$STB/stb_vorbis.c" -lm -lpthread
}
build validado
build atual
build medidor atual -DSIM_METER   # o atual com os medidores ao vivo ligados

status=0
# REF_OGG/REF_OGG48: o motor validado toca estas (OGG) enquanto o atual toca as dadas (ex.: o mesmo áudio em WAV)
for v in validado atual medidor; do
    if [ $v = validado ] && [ -n "${REF_OGG:-}" ]; then
        "$WORK/sim-$v" "$REF_OGG" "$REF_OGG48" "$WORK" ${3:+"$3"} || true
    else
        "$WORK/sim-$v" "$OGG" "$OGG48" "$WORK" ${3:+"$3"} || { [ $v != validado ] && status=1; }
    fi
    echo
done
echo "comparação do som gerado (validado x atual e x atual com medidores)"
python3 "$HERE/compare.py" "$WORK" || status=1
exit $status
