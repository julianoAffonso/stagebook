#!/bin/bash
# Native audio engine: the same command as the validated engine (only the target is Android 9, API 28, for the
# Fire HD 10 and simpler devices). Don't switch to CMake/ndk-build: their default flags change the code generated for
# a real-time engine. The proof that the sound is the same is the simulator (tools/engine-sim), not the .so hash.
#   build_native.sh <output folder>   -> <folder>/<abi>/libstagebook_audio.so
set -euo pipefail
OUT=$1
cd "$(dirname "$0")/src/main/cpp"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
HOST=$([ "$(uname)" = Darwin ] && echo darwin-x86_64 || echo linux-x86_64)   # Mac here, Linux in the GitHub check
BIN=$SDK/ndk/27.2.12479018/toolchains/llvm/prebuilt/$HOST/bin
STB=third_party
build() {   # <abi> <compilador>
    mkdir -p "$OUT/$1"
    "$BIN/$2" -shared -fPIC -O2 -Wall -Wno-unused-but-set-variable -I$STB -o "$OUT/$1/libstagebook_audio.so" \
        engine.c $STB/stb_vorbis.c -llog -lm -laaudio
}
build arm64-v8a aarch64-linux-android28-clang
build armeabi-v7a armv7a-linux-androideabi28-clang
build x86_64 x86_64-linux-android28-clang
