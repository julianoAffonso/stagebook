#!/bin/bash
# Motor de áudio nativo: o mesmo comando do motor validado (só o alvo é Android 9, API 28, para
# o Fire HD 10 e aparelhos simples). Não trocar por CMake/ndk-build: as opções padrão deles mudam o código gerado
# de um motor de tempo real. A prova de que o som é o mesmo é o simulador (tools/engine-sim), não o hash do .so.
#   build_native.sh <pasta de saída>   -> <pasta>/<abi>/libstagebook_audio.so
set -euo pipefail
OUT=$1
cd "$(dirname "$0")/src/main/cpp"
SDK=$HOME/Library/Android/sdk
BIN=$SDK/ndk/27.2.12479018/toolchains/llvm/prebuilt/darwin-x86_64/bin
STB=third_party
build() {   # <abi> <compilador>
    mkdir -p "$OUT/$1"
    "$BIN/$2" -shared -fPIC -O2 -Wall -Wno-unused-but-set-variable -I$STB -o "$OUT/$1/libstagebook_audio.so" \
        engine.c $STB/stb_vorbis.c -llog -lm
}
build arm64-v8a aarch64-linux-android28-clang
build armeabi-v7a armv7a-linux-androideabi28-clang
build x86_64 x86_64-linux-android28-clang
