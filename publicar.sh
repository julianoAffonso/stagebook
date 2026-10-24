#!/bin/bash
# Publica uma versão no GitHub Releases (julianoAffonso/stagebook), com dois arquivos de nomes fixos:
#   stagebook.apk           — app (release compactado, assinado com a chave de ~/.android/stagebook-release.*)
#   stagebook-versao.json   — versionCode/versionName do stagebook.apk; o app (Ajustes → Atualizar o app e o
#                             aviso de versão nova) lê os dois por releases/latest/download/, sempre o mais novo
# Com o repositório privado o release sai, mas o app não consegue baixar (precisa de login): abrir o
# repositório antes de distribuir.
#
#   ./publicar.sh
set -euo pipefail
cd "$(dirname "$0")"
# auditoria pedida e ainda aberta (o hook do pedido criou a marca): não publica antes dela
if [ -f .claude/auditoria-pendente ]; then
    echo "Auditoria pedida e ainda aberta ($(cat .claude/auditoria-pendente)): termine o audit e apague .claude/auditoria-pendente antes de publicar." >&2
    exit 1
fi
# a versão vem do git (nº de commits): publicar só o que está commitado, senão o número não diz o que é
if [ -n "$(git status --porcelain)" ]; then
    echo "Há mudanças não commitadas: faça o commit antes de publicar." >&2
    exit 1
fi
if [ ! -f "$HOME/.android/stagebook-release.properties" ]; then
    echo "Sem a chave de assinatura (~/.android/stagebook-release.*): o release não pode ser publicado." >&2
    exit 1
fi
./gradlew -q testDebugUnitTest lintDebug assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
TOOLS=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)
# assinado com a chave do Stagebook? (outra chave = os aparelhos recusam a atualização)
CERT=a0af59f083c430bfcd0aa42ff825f01277f4afc3c3fb0f9aba80225842a1c4a5
if ! "$TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -q "SHA-256 digest: $CERT"; then
    echo "O APK não está assinado com a chave do Stagebook: não publico." >&2
    exit 1
fi
badging=$("$TOOLS/aapt" dump badging "$APK" | head -1)
code=$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$badging")
name=$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$badging")
tag="v${name%% *}"   # v0.1.<nº de commits>
out=app/build/publicar
rm -rf "$out" && mkdir -p "$out"
cp "$APK" "$out/stagebook.apk"
printf '{"versionCode":%s,"versionName":"%s"}\n' "$code" "$name" > "$out/stagebook-versao.json"
# o release aponta para o commit: ele precisa estar no GitHub
git push -q origin HEAD
# notas: os commits desde o último release (só os títulos)
last=$(git describe --tags --abbrev=0 2>/dev/null || true)
notes=$(git log --format='- %s' ${last:+$last..}HEAD)
gh release create "$tag" "$out/stagebook.apk" "$out/stagebook-versao.json" \
    --target "$(git rev-parse HEAD)" --title "Stagebook ${name%% *}" --notes "$notes" --latest
git fetch -q --tags
echo "publicado: $name ($tag)"
