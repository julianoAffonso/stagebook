#!/usr/bin/env bash
# Instala a versão do último commit num aparelho de teste, com as travas do projeto.
#   tools/instalar.sh <serial>          (os seriais saem de: source ~/.cache/stagebook-aparelhos.env → $TCL, $S26)
# Recusa, sem instalar, se: há mudança sem commit, a marca de auditoria existe, o aparelho não responde, ou o player
# do Stagebook está ligado (instalar com o player ligado corta o som no meio). Usa a versão final assinada (release),
# a mesma do GitHub: por cima de uma instalação com outra assinatura o Android recusa e NADA é apagado (nunca
# desinstala). Imprime "instalado: <versão>" só depois de ler a versão no aparelho e conferir que é a do commit.
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${1:?uso: tools/instalar.sh <serial>}"
ADB=(adb -s "$SERIAL")
MARK="$HOME/Desktop/previous-app/.claude/auditoria-pendente"

[ -z "$(git status --porcelain)" ] || { echo "recusado: há mudança sem commit"; exit 1; }
[ ! -e "$MARK" ] || { echo "recusado: auditoria pendente ($MARK)"; exit 1; }
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "recusado: aparelho $SERIAL não responde no adb"; exit 1; }
services="$("${ADB[@]}" shell dumpsys activity services br.app.stagebook 2>/dev/null)" || { echo "recusado: não deu para ler o estado do player"; exit 1; }
if grep -q "br.app.stagebook/.player.PlayerService" <<<"$services"; then
    echo "recusado: o player do Stagebook está ligado no aparelho (desligue em Ajustes → Player ou no aviso)"; exit 1
fi

want="0.1.$(git rev-list --count HEAD) ($(git rev-parse --short HEAD))"
./gradlew -q assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
out="$("${ADB[@]}" install -r "$apk" 2>&1)" || { echo "não instalou: $out"; exit 1; }
got="$("${ADB[@]}" shell dumpsys package br.app.stagebook | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"
[ "$got" = "$want" ] || { echo "versão no aparelho ($got) não é a do commit ($want)"; exit 1; }
echo "instalado: $got"
