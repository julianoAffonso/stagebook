#!/bin/bash
# Lê e mexe na tela de um aparelho pelo adb, sem precisar olhar print (barato para a IA que testa).
#   tools/tela.sh SERIAL textos              lista os textos e ícones visíveis, com a posição de cada um
#   tools/tela.sh SERIAL tocar "Ajustes"     toca no primeiro elemento cujo texto ou descrição contém isso
#   tools/tela.sh SERIAL tem "Salvar"        sai 0 se o texto está na tela, 1 se não está
#   tools/tela.sh SERIAL print NOME          salva a captura em ~/Desktop/stagebook-teste/NOME.png
#   tools/tela.sh SERIAL abrir | fechar      abre ou fecha o Stagebook
set -uo pipefail
[ $# -ge 2 ] || { sed -n '2,7p' "$0"; exit 1; }
S=$1; CMD=$2; ARG=${3:-}
ADB=${ADB:-adb}
PKG=br.app.stagebook
PROVAS="$HOME/Desktop/stagebook-teste"

dump() {
    "$ADB" -s "$S" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || { echo "não li a tela (aparelho desconectado?)" >&2; exit 2; }
    "$ADB" -s "$S" exec-out cat /sdcard/ui.xml
}

# cada linha: x y | texto | descrição  (x y = centro do elemento)
nodes() {
    dump | python3 -c '
import re, sys, xml.etree.ElementTree as ET
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    t, d = n.get("text", ""), n.get("content-desc", "")
    if not (t or d): continue
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    print(f"{(x1+x2)//2} {(y1+y2)//2} | {t} | {d}")'
}

case "$CMD" in
    textos) nodes ;;
    tem) nodes | grep -qiF -- "$ARG" ;;
    tocar)
        linha=$(nodes | grep -iF -- "$ARG" | head -1)
        [ -n "$linha" ] || { echo "\"$ARG\" não está na tela"; exit 1; }
        set -- $linha
        "$ADB" -s "$S" shell input tap "$1" "$2"
        echo "toquei em $1,$2: $linha" ;;
    print)
        mkdir -p "$PROVAS"
        "$ADB" -s "$S" exec-out screencap -p > "$PROVAS/${ARG:-tela}.png" && echo "$PROVAS/${ARG:-tela}.png" ;;
    abrir) "$ADB" -s "$S" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 && echo aberto ;;
    fechar) "$ADB" -s "$S" shell am force-stop "$PKG" && echo fechado ;;
    *) sed -n '2,7p' "$0"; exit 1 ;;
esac
