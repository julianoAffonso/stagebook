#!/usr/bin/env python3
"""
The help (T07): docs/ajuda/ (Portuguese) and docs/help/ (English), one page each, built from the captures folder
(tools/capturas.sh) so every picture is the app as it is now. A screen that exists on phone and tablet gets a switch
between the two (the reader sees their own device). Run again after tools/capturas.sh.
    tools/ajuda.py [captures folder]      (default: ~/Desktop/stagebook-capturas)
"""
import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CAPS = Path(sys.argv[1] if len(sys.argv) > 1 else Path.home() / "Desktop/stagebook-capturas")
OUT = {"pt-BR": ROOT / "docs/ajuda", "en": ROOT / "docs/help"}

# (tablet capture, phone capture, caption pt, caption en); None = that device has no capture of this screen
Fig = tuple

SECTIONS = [
    {
        "id": "musicas", "pt": "Músicas", "en": "Songs",
        "text_pt": [
            "A aba **Músicas** é a sua biblioteca. Busque pelo nome ou pelo artista e filtre por **tags** (Nacional, Rock, Casamento…).",
            "Toque numa música para abrir o palco; segure para **selecionar várias** e pôr num setlist, mudar as tags, compartilhar ou apagar.",
            "No topo, ao lado do título, aparecem só quando importa: a folha do **modo essencial**, o grupo com o número do que a **banda** mandou e o computador do **editor web** ligado. Um toque em cada um leva ao que interessa.",
        ],
        "text_en": [
            "The **Songs** tab is your library. Search by title or artist and filter by **tags** (Local, Rock, Wedding…).",
            "Tap a song to open the stage; hold it to **select several** and add them to a setlist, change their tags, share or delete them.",
            "At the top, next to the title, they only show when they matter: the **essential mode** leaf, the group with the number of things the **band** sent and the computer of the **web editor** turned on. Tapping each takes you where it matters.",
        ],
        "figs": [Fig(("tablet_1_musicas", "celular_1_musicas", "A lista de músicas", "The song list")),
                 Fig(("tablet_1_musicas_tags_abertas", None, "Filtro por tags", "Filter by tags")),
                 Fig(("jornada_musicas_1_selecao", None, "Várias músicas selecionadas", "Several songs selected"))],
    },
    {
        "id": "importar", "pt": "Importar", "en": "Importing",
        "text_pt": [
            "**Importar** traz músicas dos sites de letra e cifra que estão na sua lista (Ajustes → Sites de cifra ou de letra). Você confere a página e toca Importar: o app não baixa nada sozinho.",
            "O mesmo botão lê o **QR** de um setlist e recebe um envio pelas **3 palavras** (ex.: 47-violao-lua).",
            "Só letra e cifra entram: arquivos ou textos que não parecem música são recusados.",
        ],
        "text_en": [
            "**Import** brings songs from the lyrics and chords sites on your list (Settings → Chord sites or Lyrics sites). You check the page and tap Import: the app never downloads anything by itself.",
            "The same button reads a setlist's **QR** and receives something sent with **3 words** (e.g. 47-violao-lua).",
            "Only lyrics and chords come in: files or texts that don't look like a song are refused.",
        ],
        "figs": [Fig(("tablet_10_importar", "celular_10_importar", "Importando de um site", "Importing from a site")),
                 Fig(("jornada_importar_menu", None, "O menu de importar", "The import menu"))],
    },
    {
        "id": "editor", "pt": "Editor da música", "en": "Song editor",
        "text_pt": [
            "A letra e a cifra ocupam a tela. Os **dados da música** (nome, artista, ano, BPM, tom, capotraste, tags e backing track) ficam recolhidos no topo: um toque abre.",
            "Os acordes vão entre colchetes, no formato ChordPro (`[G]Hoje eu [D]vou`); cifra com os acordes na linha de cima, colada, vira esse formato sozinha.",
            "**Tom** − e + transpõe a cifra meio tom. **Capotraste** − e + não muda os diagramas, só o som (\"Com capo na 2ª casa, soa em A\"). O tom e o capo ficam nos dados da música, não na letra.",
            "**Partes**: qualquer linha só com um nome entre colchetes vira parte da música (`[Refrão]`, `[Solo de violino]`); os atalhos no topo inserem as mais comuns.",
            "O app entende a escrita da cifra como ela veio (`A7+`, `E7/9`, `E*`) e mostra o diagrama certo.",
        ],
        "text_en": [
            "Lyrics and chords fill the screen. The **song details** (name, artist, year, BPM, key, capo, tags and backing track) stay folded at the top: one tap opens them.",
            "Chords go in brackets, ChordPro style (`[G]Here I [D]go`); a chart pasted with the chords on the line above turns into it by itself.",
            "**Key** − and + transposes the chords a half step. **Capo** − and + doesn't change the diagrams, only the sound (\"With the capo on fret 2, it sounds in A\"). Key and capo live in the song details, not in the lyrics.",
            "**Parts**: any line with just a name in brackets is a part of the song (`[Chorus]`, `[Violin solo]`); the shortcuts at the top insert the usual ones.",
            "The app reads chords as the chart writes them (`A7+`, `E7/9`, `E*`) and shows the right diagram.",
        ],
        "figs": [Fig(("tablet_4_editor_dados", "celular_4_editor", "Dados da música abertos", "Song details open")),
                 Fig(("tablet_4_editor_tags", "celular_4_editor_tags", "Tags da música", "The song's tags")),
                 Fig(("tablet_4_editor_capo", None, "Tom e capotraste", "Key and capo")),
                 Fig(("tablet_4_editor_secoes", None, "Atalhos das partes", "Part shortcuts"))],
    },
    {
        "id": "setlists", "pt": "Setlists", "en": "Setlists",
        "text_pt": [
            "Crie um setlist (sem nome, ele leva a data e a hora), toque no nome para renomear e ponha as músicas em ordem arrastando.",
            "**Escolher músicas** mostra a biblioteca com busca e tags: marque na ordem em que vão tocar.",
            "Ao receber um setlist, a conferência mostra o que você tem, o que tem dúvida e o que falta. Nada some em silêncio: antes de criar, o app diz o que vai ficar de fora e oferece importar as que faltam.",
        ],
        "text_en": [
            "Create a setlist (with no name it takes the date and time), tap its name to rename it and drag the songs into order.",
            "**Pick songs** shows the library with search and tags: check them in the order they'll be played.",
            "When a setlist arrives, the check shows what you have, what's in doubt and what's missing. Nothing disappears quietly: before creating it, the app says what will be left out and offers to import the missing ones.",
        ],
        "figs": [Fig(("tablet_2_setlists", "celular_2_setlists", "Os setlists", "The setlists")),
                 Fig(("tablet_3_setlist", "celular_3_setlist", "Dentro de um setlist", "Inside a setlist")),
                 Fig(("tablet_9_escolher_musicas", "celular_9_escolher_musicas", "Escolhendo as músicas", "Picking the songs")),
                 Fig(("jornada_conferencia_criar_aviso", None, "Antes de criar: o que vai faltar", "Before creating: what will be missing"))],
    },
    {
        "id": "compartilhar", "pt": "Compartilhar e QR", "en": "Sharing and QR",
        "text_pt": [
            "Num setlist, **Compartilhar**: um **QR** que funciona sem internet (o outro tablet lê com a câmera) ou **3 palavras** para enviar pela internet, ditas ou digitadas. Valem 12 horas e vão cifradas.",
            "\"Levar as músicas\" manda também as letras: quem recebe só usa as que não tem; as dele nunca mudam.",
        ],
        "text_en": [
            "In a setlist, **Share**: a **QR** that works offline (the other tablet reads it with the camera) or **3 words** to send over the internet, said out loud or typed. They last 12 hours and travel encrypted.",
            "\"Bring the songs\" sends the lyrics too: whoever receives only uses the ones they don't have; theirs never change.",
        ],
        "figs": [Fig(("jornada_compartilhar_1_folha", None, "Compartilhar", "Share")),
                 Fig(("jornada_compartilhar_2_codigo", None, "As 3 palavras", "The 3 words")),
                 Fig(("jornada_qr_3_conferir_tablet", "jornada_qr_2_conferir", "Conferindo o que chegou", "Checking what arrived"))],
    },
    {
        "id": "palco", "pt": "Palco", "en": "Stage",
        "text_pt": [
            "O palco mostra a letra grande, com a cifra e as partes destacadas. Toque ou use o pedal para **virar a página** (ou **rolar**, em Ajustes → Visual do palco); a linha onde você continua acende por um instante.",
            "No topo ficam o **tom** e o **capo**: um toque abre − e + ali mesmo. Com o palco aberto nada aparece por cima: o que a banda mandar espera você sair.",
            "Toque num acorde para ver o **diagrama** (violão, guitarra, ukulele, cavaquinho ou teclado), as variações, o som e os **detalhes** (o grau no campo harmônico do tom).",
            "O **metrônomo** começa no BPM da música; a **rolagem automática** faz a letra subir sozinha no modo Rolar.",
        ],
        "text_en": [
            "The stage shows the lyrics large, with chords and parts highlighted. Tap or use the pedal to **turn the page** (or **scroll**, in Settings → Stage look); the line where you continue lights up for a moment.",
            "At the top are the **key** and the **capo**: one tap opens − and + right there. While the stage is open nothing shows over it: whatever the band sends waits until you leave.",
            "Tap a chord to see its **diagram** (acoustic or electric guitar, ukulele, cavaquinho or keyboard), the variations, its sound and the **details** (its degree in the song's key).",
            "The **metronome** starts at the song's BPM; **auto-scroll** moves the lyrics up by itself in Scroll mode.",
        ],
        "figs": [Fig(("tablet_6_palco", "celular_7_palco", "O palco", "The stage")),
                 Fig(("tablet_6_palco_cifra", "celular_7_palco_cifra", "Com a cifra", "With chords")),
                 Fig(("tablet_6_acorde_violao", None, "O diagrama do acorde", "The chord diagram")),
                 Fig(("tablet_6_acorde_detalhes", None, "Detalhes do acorde", "Chord details")),
                 Fig(("tablet_6_palco_metronomo", None, "Metrônomo", "Metronome")),
                 Fig(("tablet_6_palco_tab", "celular_7_palco_tab", "Tablatura", "Tablature"))],
    },
    {
        "id": "backing", "pt": "Backing track", "en": "Backing track",
        "text_pt": [
            "Cada música pode ter um **backing track** (OGG multicanal, MP3 ou WAV), escolhido nos dados da música. No palco, o play só aparece nas músicas que têm faixa.",
            "Toca por uma **interface de áudio USB** (USB Audio 2.0, 48 kHz) ou pela saída do aparelho. Se a interface cair no meio do show, o app **não** passa para o alto-falante: espera ela voltar.",
            "No fim da música ele para e volta ao começo, esperando o próximo play. Segurar o play troca ou tira a faixa e abre o mixer da música.",
        ],
        "text_en": [
            "Each song can have a **backing track** (multichannel OGG, MP3 or WAV), picked in the song details. On stage, play only shows on songs that have one.",
            "It plays through a **USB audio interface** (USB Audio 2.0, 48 kHz) or the device's own output. If the interface drops mid-show the app does **not** switch to the speaker: it waits for it to come back.",
            "At the end of the song it stops and goes back to the start, waiting for the next play. Holding play changes or removes the track and opens the song's mixer.",
        ],
        "figs": [Fig(("tablet_11_player_com_faixa", "celular_11_player_tocando", "Tocando com backing track", "Playing with a backing track")),
                 Fig(("jornada_faixa_4_menu", None, "Trocar ou tirar a faixa", "Change or remove the track"))],
    },
    {
        "id": "mixer", "pt": "Mixer", "en": "Mixer",
        "text_pt": [
            "Um canal para cada canal do arquivo, com volume, saída da interface, **igualar o volume** (média ou pico) e um **equalizador** de 5 bandas com limitador.",
            "Toque no nome do canal para renomear; no ícone ao lado, para os ajustes dele (unir em estéreo, tocar no meio…).",
            "No palco, o mixer abre por cima com a música tocando, e o que você muda ali fica **só nesta música**; \"Usar o mixer global\" volta ao geral.",
        ],
        "text_en": [
            "One channel per file channel, with volume, the interface output, **level matching** (average or peak) and a 5-band **equalizer** with a limiter.",
            "Tap a channel's name to rename it; the icon next to it opens its settings (join as stereo, play in the middle…).",
            "On stage the mixer opens over the song while it plays, and what you change there stays **with that song only**; \"Use the global mixer\" goes back to the general one.",
        ],
        "figs": [Fig(("jornada_mixer_2_tablet", "jornada_mixer_1_celular", "O mixer", "The mixer")),
                 Fig(("jornada_mixer_3_editar_canal", None, "Ajustes do canal", "Channel settings")),
                 Fig(("jornada_eq_musica_2_inteiro", None, "Mixer e EQ da música no palco", "The song's mixer and EQ on stage"))],
    },
    {
        "id": "banda", "pt": "Banda", "en": "Band",
        "text_pt": [
            "No mesmo Wi-Fi (do lugar ou o ponto de acesso de um celular), um aparelho toca **Pedir para entrar** e o outro **Autorizar**, como no Bluetooth. Fica guardado até alguém remover.",
            "Depois disso, mandar um setlist para a banda é um toque: quem está no Wi-Fi recebe na hora; os outros, pela internet, quando abrirem o app.",
            "O que chega aparece uma vez embaixo (\"Ana enviou o setlist…\" · Ver) e fica no ícone da banda no topo até você conferir. **Conduzir a banda** sincroniza a música da vez em todos.",
        ],
        "text_en": [
            "On the same Wi-Fi (the venue's or a phone's hotspot), one device taps **Ask to join** and the other **Approve**, like Bluetooth. It's kept until someone removes it.",
            "After that, sending a setlist to the band is one tap: whoever is on the Wi-Fi gets it right away; the others, over the internet, when they open the app.",
            "What arrives shows once at the bottom (\"Ana sent the setlist…\" · View) and stays on the band icon at the top until you check it. **Lead the band** keeps everyone on the same song.",
        ],
        "figs": [Fig(("jornada_banda_1_ajustes", None, "Ajustes → Banda", "Settings → Band")),
                 Fig(("jornada_banda_3_compartilhar", None, "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso", None, "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("banda_conduzir_faixas", None, "Conduzindo a banda", "Leading the band"))],
    },
    {
        "id": "ajustes", "pt": "Ajustes", "en": "Settings",
        "text_pt": [
            "**Modo essencial** (o primeiro da lista): para quem toca sozinho ou com a bateria no fim. Desliga tudo o que não é letra, cifra, diagramas e pedal; ou só uma parte.",
            "**Visual do palco**: fontes, cores, tamanho das partes, virar página ou rolar, metrônomo. **Pedal**: Bluetooth ou MIDI, e o que cada botão faz.",
            "**Biblioteca e backup**: um arquivo `.stagebook` com o que você marcar (músicas, setlists, backing tracks, ajustes). Serve de backup e para passar músicas para alguém.",
        ],
        "text_en": [
            "**Essential mode** (first on the list): for playing alone or on low battery. It turns off everything that isn't lyrics, chords, diagrams and the pedal; or just part of it.",
            "**Stage look**: fonts, colours, part title size, turning pages or scrolling, metronome. **Pedal**: Bluetooth or MIDI, and what each button does.",
            "**Library and backup**: a `.stagebook` file with what you check (songs, setlists, backing tracks, settings). It's a backup and a way to pass songs to someone.",
        ],
        "figs": [Fig(("tablet_5_ajustes", "celular_5_ajustes", "Os ajustes", "Settings")),
                 Fig(("tablet_5_ajustes_essencial", None, "Modo essencial", "Essential mode")),
                 Fig(("tablet_5_ajustes_backup", None, "Biblioteca e backup", "Library and backup")),
                 Fig((None, "celular_5_ajustes_pedal", "Pedal", "Pedal"))],
    },
    {
        "id": "editor-web", "pt": "Editor no computador", "en": "Editor on the computer",
        "text_pt": [
            "Em Ajustes → Editar no computador, **Ligar o editor** mostra um endereço (`http://…:8090`). No navegador do computador, no mesmo Wi-Fi, toque **Pedir para entrar**; o tablet mostra um código de 4 números, igual ao da página: autorize só se forem iguais.",
            "No computador você edita as músicas com teclado de verdade (tags, tom e capo nos campos, a letra em ChordPro; Ctrl+S salva), monta setlists e importa: colar a cifra, o endereço da página, soltar arquivos, as 3 palavras, o que a banda mandou ou um arquivo `.stagebook`.",
            "Enquanto ele está ligado, o ícone do computador aparece no topo das abas; um toque mostra o endereço e **Fechar o editor web**. Ele também desliga sozinho depois de 5 minutos sem uso e ao fechar o app.",
        ],
        "text_en": [
            "In Settings → Edit on the computer, **Turn on the editor** shows an address (`http://…:8090`). In the computer's browser, on the same Wi-Fi, tap **Ask to come in**; the tablet shows a 4-digit code, the same as the page's: approve only if they match.",
            "On the computer you edit songs with a real keyboard (tags, key and capo in their fields, the lyrics in ChordPro; Ctrl+S saves), build setlists and import: paste chords, a page address, drop files, the 3 words, what the band sent or a `.stagebook` file.",
            "While it's on, the computer icon shows at the top of the tabs; one tap shows the address and **Close the web editor**. It also turns off by itself after 5 minutes unused and when the app closes.",
        ],
        "figs": [Fig((None, "celular_5_ajustes_editor_web", "Ligando o editor no tablet", "Turning on the editor on the tablet")),
                 Fig(("editor_web_1_pedido", None, "Pedindo para entrar", "Asking to come in")),
                 Fig(("editor_web_2_musica", None, "Editando uma música", "Editing a song")),
                 Fig(("editor_web_3_setlist", None, "Um setlist", "A setlist")),
                 Fig(("editor_web_4_importar", None, "Importar no computador", "Importing on the computer"))],
    },
    {
        "id": "atualizar", "pt": "Atualizar", "en": "Updating",
        "text_pt": [
            "Quando sai uma versão nova, o app avisa ao abrir (com o player desligado). A banda deve usar a mesma versão: um aparelho em outra versão aparece em Ajustes → Banda.",
            "Tudo fica no aparelho: o que sai vai cifrado, sem conta, propaganda ou rastreio.",
        ],
        "text_en": [
            "When a new version is out, the app says so on opening (with the player off). The band should use the same version: a device on another one shows in Settings → Band.",
            "Everything stays on the device: what leaves it travels encrypted, with no account, ads or tracking.",
        ],
        "figs": [Fig(("tablet_8_versao_nova", None, "Versão nova", "New version"))],
    },
]

UI = {
    "pt-BR": {"title": "Ajuda do Stagebook", "lead": "Letra, cifra e backing track no palco, com a banda junto.", "contents": "Nesta página",
              "tablet": "Tablet", "phone": "Celular", "other": "English", "other_href": "../help/", "lang": "pt-BR"},
    "en": {"title": "Stagebook help", "lead": "Lyrics, chords and backing tracks on stage, with the band in sync.", "contents": "On this page",
           "tablet": "Tablet", "phone": "Phone", "other": "Português", "other_href": "../ajuda/", "lang": "en"},
}

CSS = """
:root { --bg:#121212; --surface:#1E1E1E; --raised:#262626; --fg:#ECECEC; --muted:#A0A7A4; --accent:#4AF6CD; --on-accent:#00382C;
        --line:#2E2E2E; color-scheme: dark; }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
header { padding: 32px max(16px, env(safe-area-inset-left, 0px)) 8px; max-width: 1180px; margin: 0 auto; display:flex; gap:20px; align-items:center; flex-wrap:wrap; }
header img { width:72px; height:72px; border-radius:18px; }
h1 { margin:0; font-size: 30px; } .lead { margin:4px 0 0; color:var(--muted); }
.lang { margin-left:auto; color:var(--accent); }
.wrap { max-width:1180px; margin:0 auto; padding: 0 max(16px, env(safe-area-inset-right, 0px)) 64px max(16px, env(safe-area-inset-left, 0px));
        display:grid; grid-template-columns: 220px 1fr; gap: 32px; }
nav.toc { position: sticky; top: 16px; align-self:start; background:var(--surface); border-radius:20px; padding:16px; }
nav.toc b { display:block; color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.06em; margin-bottom:8px; }
nav.toc a { display:block; color:var(--fg); text-decoration:none; padding:4px 8px; border-radius:10px; }
nav.toc a:hover { background:var(--raised); }
section { padding-top: 24px; min-width: 0; }
h2 { font-size: 24px; margin: 0 0 8px; color: var(--accent); text-wrap: balance; }
p { max-width: 70ch; } code { background:var(--raised); padding:1px 6px; border-radius:6px; font-size: 14px; }
.figs { display:grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; margin: 16px 0 8px; }
figure { margin:0; background:var(--surface); border-radius:20px; padding:12px; }
figure.wide { grid-column: 1 / -1; }
figure img { width:100%; height:auto; border-radius:12px; display:block; }
figure img.phone { max-height: 560px; width:auto; max-width:100%; margin: 0 auto; }
figcaption { color:var(--muted); font-size:14px; margin-top:8px; display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
.switch { margin-left:auto; display:flex; background:var(--raised); border-radius:999px; padding:2px; }
.switch button { border:0; background:none; color:var(--fg); font: 600 12px system-ui, sans-serif; padding:4px 10px; border-radius:999px; cursor:pointer; }
.switch button[aria-pressed=true] { background:var(--accent); color:var(--on-accent); }
@media (max-width: 800px) { .wrap { grid-template-columns: 1fr; } nav.toc { position: static; } }
"""

JS = """
document.querySelectorAll('figure[data-tablet][data-phone]').forEach(f => {
  const img = f.querySelector('img');
  f.querySelectorAll('.switch button').forEach(b => b.onclick = () => {
    const phone = b.dataset.d === 'phone';
    img.src = phone ? f.dataset.phone : f.dataset.tablet;
    img.className = phone ? 'phone' : '';
    f.classList.toggle('wide', !phone);
    f.querySelectorAll('.switch button').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
  });
});
"""


def md(text):
    """**bold** and `code` (the only marks the texts use)."""
    out = html.escape(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    return re.sub(r"`(.+?)`", r"<code>\1</code>", out)


def picture(lang, name, img_dir):
    """Copies a capture into the help as a JPEG no larger than 1400 px (light pages); returns its file name."""
    src = next((CAPS / lang).rglob(f"{name}.png"), None)
    if src is None:
        raise SystemExit(f"captura não encontrada: {lang}/{name}.png (rode tools/capturas.sh)")
    dst = img_dir / f"{name}.jpg"
    subprocess.run(["sips", "-Z", "1400", "-s", "format", "jpeg", "-s", "formatOptions", "82", str(src), "--out", str(dst)],
                   check=True, capture_output=True)
    return dst.name


def portrait(path):
    """A capture standing up (a phone, or a tablet held upright): shown with a limited height."""
    out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(path)], check=True, capture_output=True, text=True).stdout
    w, h = (int(re.search(rf"{k}: (\d+)", out).group(1)) for k in ("pixelWidth", "pixelHeight"))
    return h > w


def build(lang):
    ui, out = UI[lang], OUT[lang]
    key = "pt" if lang == "pt-BR" else "en"
    if out.exists(): shutil.rmtree(out)
    img_dir = out / "img"
    img_dir.mkdir(parents=True)
    toc, body = [], []
    for s in SECTIONS:
        toc.append(f'<a href="#{s["id"]}">{html.escape(s[key])}</a>')
        figs = []
        for tablet, phone, cap_pt, cap_en in s["figs"]:
            cap = html.escape(cap_pt if key == "pt" else cap_en)
            t_img = f"img/{picture(lang, tablet, img_dir)}" if tablet else None
            p_img = f"img/{picture(lang, phone, img_dir)}" if phone else None
            first = t_img or p_img
            cls = "phone" if portrait(out / first) else ""
            switch = ""
            attrs = ""
            if t_img and p_img:
                attrs = f' data-tablet="{t_img}" data-phone="{p_img}"'
                switch = (f'<span class="switch"><button data-d="tablet" aria-pressed="true">{ui["tablet"]}</button>'
                          f'<button data-d="phone" aria-pressed="false">{ui["phone"]}</button></span>')
            # a lying tablet capture takes the whole row (side by side it was too small to read)
            wide = "" if cls else ' class="wide"'
            figs.append(f'<figure{wide}{attrs}><img src="{first}" class="{cls}" alt="{cap}" loading="lazy"><figcaption>{cap}{switch}</figcaption></figure>')
        paras = "".join(f"<p>{md(p)}</p>" for p in s["text_" + key])
        body.append(f'<section id="{s["id"]}"><h2>{html.escape(s[key])}</h2>{paras}<div class="figs">{"".join(figs)}</div></section>')
    page = f"""<!doctype html>
<html lang="{ui['lang']}">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{ui['title']}</title><link rel="icon" href="../logo.png"><style>{CSS}</style></head>
<body>
<header><img src="../logo.png" alt="Stagebook"><div><h1>{ui['title']}</h1><p class="lead">{ui['lead']}</p></div>
<a class="lang" href="{ui['other_href']}">{ui['other']}</a></header>
<div class="wrap"><nav class="toc"><b>{ui['contents']}</b>{''.join(toc)}</nav><main>{''.join(body)}</main></div>
<script>{JS}</script>
</body></html>
"""
    (out / "index.html").write_text(page, encoding="utf-8")
    size = sum(f.stat().st_size for f in img_dir.iterdir()) / 1e6
    print(f"{lang}: {out.relative_to(ROOT)}/index.html, {len(list(img_dir.iterdir()))} imagens ({size:.1f} MB)")


if __name__ == "__main__":
    for lang in OUT:
        build(lang)
