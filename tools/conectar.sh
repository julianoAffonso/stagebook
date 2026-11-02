#!/bin/bash
# Conecta o adb do Mac aos aparelhos de teste pela Depuração por Wi-Fi (Android 11+).
#   tools/conectar.sh                      conecta todos os aparelhos que estão anunciando e diz quem é quem
#   tools/conectar.sh parear IP:PORTA COD  pareia um aparelho novo (uma vez só por aparelho)
# No aparelho: Opções do desenvolvedor → Depuração por Wi-Fi ligada, no mesmo Wi-Fi do Mac. A porta muda cada vez
# que a depuração liga, por isso ela sai do Bonjour do macOS (dns-sd) e não do mDNS do adb (desligado em alguns Macs:
# o do adb 37 aborta com o anúncio do Quick Share). No fim grava ~/.cache/stagebook-aparelhos.env com TCL=, S26=,
# FIRE= (seriais para usar em adb -s); carregue com: source ~/.cache/stagebook-aparelhos.env
set -uo pipefail
ADB=${ADB:-$(command -v adb)}
[ -n "$ADB" ] || { echo "adb não encontrado (brew install android-platform-tools)"; exit 1; }
ENV_FILE="$HOME/.cache/stagebook-aparelhos.env"

# dns-sd não termina sozinho: roda por alguns segundos e para
browse() { dns-sd "$@" & local p=$!; sleep 3; kill $p 2>/dev/null; wait $p 2>/dev/null; }

if [ "${1:-}" = "parear" ]; then
    [ $# -eq 3 ] || { echo "uso: tools/conectar.sh parear IP:PORTA CODIGO (da tela \"Parear o dispositivo com um código\")"; exit 1; }
    "$ADB" pair "$2" "$3" || exit 1
    echo "pareado; agora rode tools/conectar.sh"
    exit 0
fi

"$ADB" start-server >/dev/null 2>&1

# cada aparelho com a depuração ligada anuncia _adb-tls-connect; descobre IP e porta de cada um
for inst in $(browse -B _adb-tls-connect._tcp local. 2>/dev/null | awk '$2 == "Add" {print $NF}' | sort -u); do
    alvo=$(browse -L "$inst" _adb-tls-connect._tcp local. 2>/dev/null | sed -n 's/.*can be reached at \([^ ]*\) .*/\1/p' | head -1)
    host=${alvo%:*}; porta=${alvo##*:}
    [ -n "$porta" ] || { echo "$inst: não achei a porta"; continue; }
    ip=$(browse -G v4 "${host%.}" 2>/dev/null | awk '$2 == "Add" {print $(NF-1)}' | head -1)
    [ -n "$ip" ] || { echo "$inst: não achei o IP"; continue; }
    "$ADB" connect "$ip:$porta" | sed "s/^/$inst: /"
done

# quem é quem, pelo fabricante
: > "$ENV_FILE.tmp"
achou=0
while read -r serial estado _; do
    [ "$estado" = "device" ] || { [ -n "$serial" ] && echo "$serial: $estado (aceite a depuração na tela do aparelho ou pareie de novo)"; continue; }
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
[ $achou -eq 1 ] || { echo "nenhum aparelho conectado: ligue a Depuração por Wi-Fi no aparelho (mesmo Wi-Fi do Mac) ou pareie (tools/conectar.sh parear)"; exit 1; }
echo "seriais em $ENV_FILE (source para usar \$TCL, \$S26, \$FIRE)"
