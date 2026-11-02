#!/bin/bash
# Lê e mexe na tela de um aparelho pelo adb, sem precisar olhar print (barato para a IA que testa).
#   tools/tela.sh SERIAL textos              lista os textos e ícones visíveis, com a posição de cada um
#                                            (texto longo ou com quebra de linha sai como [texto N caracteres]:
#                                            é a letra da música, que nunca pode aparecer; "textos-inteiro" mostra tudo)
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

# texto com quebra de linha ou mais de 60 caracteres é letra de música: sai só o tamanho
MASK='
import re as _re
def mask(s, full):
    if full or ("\n" not in s and len(s) <= 60): return s
    return f"[texto {len(s)} caracteres]"
# the stage in Portuguese or English: its lyrics area runs from the top bar to the clock
EDIT = ("Editar a letra nesta página", "Edit the lyrics on this page")
TOP = EDIT + ("Tom:", "Key:", "Voltar", "Back")
BOTTOM = ("Metrônomo", "Metronome")
def stage_area(nodes, box):
    if not any(n.get("content-desc", "") in EDIT for n in nodes): return None
    top = max([box(n)[3] for n in nodes if n.get("content-desc", "").startswith(TOP)] or [0])
    bottom = min([box(n)[1] for n in nodes if n.get("content-desc") in BOTTOM or _re.fullmatch(r"\d\d:\d\d", n.get("text", ""))] or [10**6])
    return top, bottom
def lyric(n, area, box):
    # touching the lyrics area at all (a line cut at an edge too), except page numbers
    if area is None or not n.get("text"): return False
    y1, y2 = box(n)[1], box(n)[3]
    return y2 > area[0] and y1 < area[1] and not _re.fullmatch(r"[\d…]+", n.get("text", "").strip())
'

# cada linha: x y | texto | descrição | clicável (1/0)  (x y = centro do elemento)
# no palco (tem "Editar a letra nesta página") a letra pode vir linha a linha, em textos curtos: todo texto abaixo da
# barra do topo e acima do relógio sai escondido, menos números (páginas) e reticências
nodes() {
    dump | python3 -c "$MASK"'
import re, sys, xml.etree.ElementTree as ET
full = sys.argv[1] == "1"
all_ = [n for n in ET.fromstring(sys.stdin.read()).iter("node") if n.get("text") or n.get("content-desc")]
box = lambda n: list(map(int, re.findall(r"\d+", n.get("bounds"))))
area = None if full else stage_area(all_, box)
for n in all_:
    t, d = n.get("text", ""), n.get("content-desc", "")
    raw = t
    t, d = mask(t, full), mask(d, full)
    x1, y1, x2, y2 = box(n)
    if lyric(n, area, box):
        t = f"[texto {len(raw)} caracteres]"
    c = "1" if n.get("clickable") == "true" else "0"
    print(f"{(x1+x2)//2} {(y1+y2)//2} | {t} | {d} | {c}")' "${1:-0}"
}

# toca no elemento cujo texto ou descrição é exatamente o pedido (de preferência clicável); se não houver, o que contém
achar() {
    dump | python3 -c "$MASK"'
import re, sys, xml.etree.ElementTree as ET
alvo = sys.argv[1].strip().lower()
all_ = [n for n in ET.fromstring(sys.stdin.read()).iter("node") if n.get("text") or n.get("content-desc")]
box = lambda n: list(map(int, re.findall(r"\d+", n.get("bounds"))))
area = stage_area(all_, box)
nodes = []
for n in all_:
    # on the stage the lyrics are never a target: a line containing the word was tapped and printed (audit 02/11)
    if lyric(n, area, box): continue
    x1, y1, x2, y2 = box(n)
    nodes.append(((x1+x2)//2, (y1+y2)//2, n.get("text", ""), n.get("content-desc", ""), n.get("clickable") == "true"))
for exato in (True, False):
    for clic in (True, None):
        for x, y, t, d, c in nodes:
            if clic and not c: continue
            rot = [t.strip().lower(), d.strip().lower()]
            ok = (alvo in rot) if exato else any(alvo in r for r in rot if r)
            if ok:
                print(f"{x} {y} | {mask(t, False)} | {mask(d, False)}"); sys.exit(0)
sys.exit(1)' "$ARG"
}

case "$CMD" in
    textos) nodes 0 ;;
    textos-inteiro) nodes 1 ;;
    tem) nodes 1 | grep -qiF -- "$ARG" ;;
    tocar)
        linha=$(achar) || { echo "\"$ARG\" não está na tela"; exit 1; }
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
