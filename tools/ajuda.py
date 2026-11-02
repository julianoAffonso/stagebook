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
            "A aba Músicas é a sua biblioteca. Dá para buscar pelo nome ou pelo artista e filtrar pelas tags que você criou.",
            "Tocando numa música, ela abre no palco. Segurando, você seleciona várias de uma vez para pôr num setlist, mudar as tags, compartilhar ou apagar.",
            "Ao lado do título podem aparecer alguns ícones: a folha quando o modo essencial está ligado, o grupo com um número quando a banda mandou algo, e o computador quando o editor web está ligado. Tocando no ícone você vai direto para o assunto.",
        ],
        "text_en": [
            "The Songs tab is your library. You can search by title or artist and filter by the tags you created.",
            "Tap a song and it opens on stage. Hold it to select several at once and add them to a setlist, change their tags, share or delete them.",
            "A few icons may show next to the title: the leaf when essential mode is on, the group with a number when the band sent something, and the computer when the web editor is on. Tapping an icon takes you straight to it.",
        ],
        "figs": [Fig(("tablet_1_musicas", "celular_1_musicas", "A lista de músicas", "The song list")),
                 Fig(("tablet_1_musicas_tags_abertas", None, "Filtro por tags", "Filter by tags")),
                 Fig(("jornada_musicas_1_selecao", None, "Várias músicas selecionadas", "Several songs selected"))],
    },
    {
        "id": "importar", "pt": "Importar", "en": "Importing",
        "text_pt": [
            "Em Importar você busca a música nos sites da sua lista (Ajustes → Sites de cifra ou Sites de letra), abre a página e toca em Importar. O app só baixa o que você mandar.",
            "O mesmo botão lê o QR de um setlist e recebe o que alguém mandou pelas 3 palavras.",
            "Se o texto não parecer letra ou cifra, o app não aceita.",
        ],
        "text_en": [
            "In Import you look the song up on the sites on your list (Settings → Chord sites or Lyrics sites), open the page and tap Import. The app only downloads what you ask for.",
            "The same button reads a setlist's QR and receives what someone sent with the 3 words.",
            "If the text doesn't look like lyrics or chords, the app won't take it.",
        ],
        "figs": [Fig(("tablet_10_importar", "celular_10_importar", "Importando de um site", "Importing from a site")),
                 Fig(("jornada_importar_menu", None, "O menu de importar", "The import menu"))],
    },
    {
        "id": "editor", "pt": "Editor da música", "en": "Song editor",
        "text_pt": [
            "No editor a letra fica com a tela toda. Nome, artista, ano, BPM, tom, capotraste, tags e backing track ficam em Dados da música, no topo; toque para abrir.",
            "Os acordes vão entre colchetes, no meio da letra: [G]Hoje eu [D]vou. Se você colar uma cifra com os acordes na linha de cima, o app converte sozinho.",
            "O − e + do tom transpõem a cifra meio tom de cada vez. O do capotraste não mexe nos diagramas, só avisa em que tom vai soar.",
            "Uma linha só com um nome entre colchetes vira o título de uma parte: [Refrão], [Solo de violino]. Os atalhos no topo põem as partes mais usadas.",
            "Os acordes ficam escritos como vieram do site (A7+, E7/9, E*), e o app mostra o diagrama certo para cada um.",
            "Para destacar uma frase, selecione-a na letra e toque em Destacar (o botão com o marca-texto). Ela fica pintada no editor e no palco. No palco também dá: segure a linha e toque em Destacar esta linha. A cor do destaque fica em Ajustes → Visual do palco.",
        ],
        "text_en": [
            "In the editor the lyrics get the whole screen. Name, artist, year, BPM, key, capo, tags and backing track are under Song details at the top; tap to open them.",
            "Chords go in brackets, inside the lyrics: [G]Here I [D]go. If you paste a chart with the chords on the line above, the app converts it for you.",
            "The key's − and + transpose the chords a half step at a time. The capo's don't change the diagrams; they only tell you what key it will sound in.",
            "A line with just a name in brackets becomes a part title: [Chorus], [Violin solo]. The shortcuts at the top add the usual parts.",
            "Chords stay written the way the site wrote them (A7+, E7/9, E*), and the app shows the right diagram for each.",
            "To highlight a phrase, select it in the lyrics and tap Highlight (the marker button). It shows painted in the editor and on stage. On stage too: hold the line and tap Highlight this line. The highlight color is in Settings → Stage look.",
        ],
        "figs": [Fig(("tablet_4_editor_dados", "celular_4_editor", "Dados da música abertos", "Song details open")),
                 Fig(("tablet_4_editor_tags", "celular_4_editor_tags", "Tags da música", "The song's tags")),
                 Fig(("tablet_4_editor_capo", None, "Tom e capotraste", "Key and capo")),
                 Fig(("tablet_4_editor_secoes", None, "Atalhos das partes", "Part shortcuts"))],
    },
    {
        "id": "setlists", "pt": "Setlists", "en": "Setlists",
        "text_pt": [
            "Para criar um setlist, toque em Novo setlist. Sem nome, ele fica com a data e a hora; tocando no nome você troca. Para mudar a ordem, arraste as músicas.",
            "Em Escolher músicas, marque as músicas na ordem em que vão ser tocadas. A busca e as tags ajudam a achar.",
            "Quando chega um setlist de alguém, o app mostra quais músicas você já tem e quais faltam. Antes de criar, ele avisa o que vai ficar de fora e oferece importar as que faltam.",
        ],
        "text_en": [
            "To create a setlist, tap New setlist. With no name it takes the date and time; tap the name to change it. Drag songs to change the order.",
            "In Pick songs, check the songs in the order they'll be played. Search and tags help you find them.",
            "When a setlist comes from someone, the app shows which songs you already have and which are missing. Before creating it, it tells you what will be left out and offers to import the missing ones.",
        ],
        "figs": [Fig(("tablet_2_setlists", "celular_2_setlists", "Os setlists", "The setlists")),
                 Fig(("tablet_3_setlist", "celular_3_setlist", "Dentro de um setlist", "Inside a setlist")),
                 Fig(("tablet_9_escolher_musicas", "celular_9_escolher_musicas", "Escolhendo as músicas", "Picking the songs")),
                 Fig(("jornada_conferencia_criar_aviso", None, "Antes de criar: o que vai faltar", "Before creating: what will be missing"))],
    },
    {
        "id": "compartilhar", "pt": "Compartilhar e QR", "en": "Sharing and QR",
        "text_pt": [
            "Num setlist, Compartilhar mostra um QR, que o outro tablet lê com a câmera, mesmo sem internet. Ou gera 3 palavras para mandar pela internet; dá para ditar. Elas valem por 12 horas.",
            "Com Levar as músicas ligado, as letras vão junto. Quem recebe só fica com as que não tinha; as dele não mudam.",
        ],
        "text_en": [
            "In a setlist, Share shows a QR that the other tablet reads with its camera, even offline. Or it makes 3 words to send over the internet; you can just say them. They last 12 hours.",
            "With Bring the songs on, the lyrics go too. The other person only keeps the ones they didn't have; theirs don't change.",
        ],
        "figs": [Fig(("jornada_compartilhar_1_folha", None, "Compartilhar", "Share")),
                 Fig(("jornada_compartilhar_2_codigo", None, "As 3 palavras", "The 3 words")),
                 Fig(("jornada_qr_3_conferir_tablet", "jornada_qr_2_conferir", "Conferindo o que chegou", "Checking what arrived"))],
    },
    {
        "id": "palco", "pt": "Palco", "en": "Stage",
        "text_pt": [
            "No palco a letra aparece grande, com a cifra e as partes. Toque nas laterais ou use o pedal para virar a página. Se preferir rolar, troque em Ajustes → Visual do palco.",
            "O tom e o capo ficam no topo; tocando neles você muda ali mesmo. Enquanto o palco está aberto, nada aparece por cima: o que a banda mandar fica esperando.",
            "Tocando num acorde você vê o diagrama, para violão, guitarra, ukulele, cavaquinho ou teclado, com as variações e o som. Em Detalhes aparece o grau do acorde no tom da música.",
            "O metrônomo começa no BPM da música, e mudar o BPM nele grava na música. Com o player ligado, o som dele pode sair numa saída da interface USB (Ajustes → Metrônomo e player → Saída do som), para o baterista ouvir pela mesa. Ao ligar o metrônomo, o app liga a interface sozinho, com ou sem faixa. No modo Rolar, a rolagem automática sobe a letra sozinha.",
        ],
        "text_en": [
            "On stage the lyrics show large, with the chords and parts. Tap the sides or use the pedal to turn the page. If you'd rather scroll, change it in Settings → Stage look.",
            "Key and capo are at the top; tap them to change them right there. While the stage is open nothing pops up over it: whatever the band sends waits.",
            "Tap a chord to see its diagram for guitar, electric guitar, ukulele, cavaquinho or keyboard, with variations and sound. Details shows the chord's degree in the song's key.",
            "The metronome starts at the song's BPM, and changing the BPM there saves it in the song. With the player on, its sound can go out on a USB interface output (Settings → Metronome and player → Sound output), so the drummer hears it through the desk. Turning the metronome on turns the interface on by itself, with or without a track. In Scroll mode, auto-scroll moves the lyrics up on its own.",
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
            "Cada música pode ter um backing track (OGG com vários canais, MP3 ou WAV). Escolha nos Dados da música. No palco, o play só aparece nas músicas que têm um.",
            "O som sai por uma interface de áudio USB (USB Audio 2.0, 48 kHz) ou pela saída do aparelho. Se a interface desconectar no meio do show, o app espera ela voltar em vez de jogar o som no alto-falante.",
            "Em alguns aparelhos, como celulares Samsung, a interface conecta mas não sai som. Nesse caso ligue Tocar pela interface através do Android, em Ajustes → Metrônomo e player: o som vai pelo áudio do Android, com todos os canais da interface.",
            "Na primeira vez que ligar a interface no cabo USB, o Android pergunta qual app abrir: toque em Stagebook e em Apenas uma vez. O Stagebook abre e pede o acesso ao áudio: permita. Tire e ponha o cabo de novo, escolha Stagebook e toque em Sempre. Pronto: ao religar o cabo, o som volta sozinho, sem perguntar nada. O Stagebook não grava nada; a entrada da interface só acerta o relógio.",
            "Quando a música acaba, ele para e volta ao começo. Segurando o play você troca ou tira a faixa e abre o mixer da música.",
        ],
        "text_en": [
            "Each song can have a backing track (multichannel OGG, MP3 or WAV). Pick it in Song details. On stage, play only shows on songs that have one.",
            "Sound goes out through a USB audio interface (USB Audio 2.0, 48 kHz) or the device's own output. If the interface disconnects mid-show, the app waits for it to come back instead of sending the sound to the speaker.",
            "On some devices, like Samsung phones, the interface connects but no sound comes out. Then turn on Play on the interface through Android, in Settings → Metronome and player: the sound goes through Android's audio, with all the interface's channels.",
            "The first time you plug the interface into the USB cable, Android asks which app to open: tap Stagebook and Just once. Stagebook opens and asks for audio access: allow it. Unplug and plug the cable again, pick Stagebook and tap Always. That's it: when the cable comes back, the sound returns by itself, without asking anything. Stagebook records nothing; the interface's input only keeps the clock.",
            "When the song ends it stops and goes back to the start. Hold play to change or remove the track and to open the song's mixer.",
        ],
        "figs": [Fig(("tablet_11_player_com_faixa", "celular_11_player_tocando", "Tocando com backing track", "Playing with a backing track")),
                 Fig(("jornada_faixa_4_menu", None, "Trocar ou tirar a faixa", "Change or remove the track"))],
    },
    {
        "id": "mixer", "pt": "Mixer", "en": "Mixer",
        "text_pt": [
            "O mixer tem um canal para cada canal do arquivo: volume, saída na interface, igualar o volume pela média ou pelo pico e um equalizador de 5 bandas.",
            "Tocando no nome do canal você renomeia; o ícone ao lado abre os outros ajustes, como juntar dois canais em estéreo.",
            "No palco, o mixer abre por cima com a música tocando, para ajustar ouvindo. Com Usar o mixer global ligado, o que você muda vale para todas as músicas; desligado, vale só para aquela música. O mixer fica só no palco: em Ajustes nada toca, então não daria para ajustar ouvindo.",
        ],
        "text_en": [
            "The mixer has one channel per file channel: volume, interface output, level matching by average or peak, and a 5-band equalizer.",
            "Tap a channel's name to rename it; the icon next to it opens the other settings, like joining two channels as stereo.",
            "On stage the mixer opens over the song while it plays, so you adjust while listening. With Use the global mixer on, what you change applies to every song; off, only to that song. The mixer is only on the stage: nothing plays in Settings, so it couldn't be adjusted by ear.",
        ],
        "figs": [Fig(("jornada_mixer_2_tablet", "jornada_mixer_1_celular", "O mixer", "The mixer")),
                 Fig(("jornada_mixer_3_editar_canal", None, "Ajustes do canal", "Channel settings")),
                 Fig(("jornada_eq_musica_2_inteiro", None, "Mixer e EQ da música no palco", "The song's mixer and EQ on stage"))],
    },
    {
        "id": "banda", "pt": "Banda", "en": "Band",
        "text_pt": [
            "Com os aparelhos no mesmo Wi-Fi (pode ser o ponto de acesso de um celular), um toca em Pedir para entrar e o outro em Autorizar. Depois disso eles se reconhecem até alguém remover.",
            "Mandar um setlist para a banda é um toque. Quem está no mesmo Wi-Fi recebe na hora; quem não está, recebe pela internet quando abrir o app.",
            "Quando chega algo, aparece um aviso rápido embaixo e um número no ícone da banda, no topo, até você conferir. Em Conduzir a banda, todos acompanham a música que você está tocando.",
        ],
        "text_en": [
            "With the devices on the same Wi-Fi (a phone's hotspot works too), one taps Ask to join and the other taps Approve. After that they know each other until someone removes it.",
            "Sending a setlist to the band takes one tap. Whoever is on the same Wi-Fi gets it right away; the others get it over the internet when they open the app.",
            "When something arrives you get a short notice at the bottom and a number on the band icon at the top until you check it. With Lead the band, everyone follows the song you're playing.",
        ],
        "figs": [Fig(("jornada_banda_1_ajustes", None, "Ajustes → Banda", "Settings → Band")),
                 Fig(("jornada_banda_3_compartilhar", None, "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso", None, "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("banda_conduzir_faixas", None, "Conduzindo a banda", "Leading the band"))],
    },
    {
        "id": "ajustes", "pt": "Ajustes", "en": "Settings",
        "text_pt": [
            "O Modo essencial é o primeiro da lista. Serve para quem toca sozinho ou está com pouca bateria: deixa só letra, cifra, diagramas e pedal. Dá para desligar só uma parte.",
            "Em Visual do palco ficam as fontes, as cores, o tamanho dos títulos das partes e virar página ou rolar. Em Metrônomo e player ficam o metrônomo, a saída USB e o botão da faixa no palco. Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado. Em Pedal você liga um pedal Bluetooth ou MIDI e escolhe o que cada botão faz. Com mais de um pedal, os botões ficam separados por pedal, com o conectado em cima.",
            "Com um pedal Bluetooth ligado, o teclado da tela pode sumir: o celular acha que o pedal é um teclado de verdade. Isso é do Android, não do app. No Gboard, ligue Configurações → Teclado físico → Mostrar teclado na tela. Em outros teclados, toque no ícone de teclado na barra de baixo e ligue Teclado na tela.",
            "Em Biblioteca e backup você salva um arquivo .stagebook com o que escolher: músicas, setlists, backing tracks e ajustes. Serve de backup e para passar músicas para outra pessoa.",
        ],
        "text_en": [
            "Essential mode is first on the list. It's for playing alone or on low battery: it leaves only lyrics, chords, diagrams and the pedal. You can turn off just part of it.",
            "Stage look has the fonts, colours, part title size and turning pages or scrolling. Metronome and player has the metronome, the USB output and the stage's track button. When essential mode turns one of them off, the item is named after the one left on. In Pedal you connect a Bluetooth or MIDI pedal and choose what each button does. With more than one pedal, the buttons are grouped by pedal, the connected one on top.",
            "With a Bluetooth pedal connected, the on-screen keyboard may disappear: the phone takes the pedal for a real keyboard. That comes from Android, not the app. In Gboard, turn on Settings → Physical keyboard → Show on-screen keyboard. In other keyboards, tap the keyboard icon on the bottom bar and turn on the on-screen keyboard.",
            "In Library and backup you save a .stagebook file with what you choose: songs, setlists, backing tracks and settings. It works as a backup and to pass songs to someone else.",
        ],
        "figs": [Fig(("tablet_5_ajustes", "celular_5_ajustes", "Os ajustes", "Settings")),
                 Fig(("tablet_5_ajustes_essencial", None, "Modo essencial", "Essential mode")),
                 Fig(("tablet_5_ajustes_backup", None, "Biblioteca e backup", "Library and backup")),
                 Fig((None, "celular_5_ajustes_pedal", "Pedal", "Pedal"))],
    },
    {
        "id": "editor-web", "pt": "Editor no computador", "en": "Editor on the computer",
        "text_pt": [
            "Em Ajustes → Editar no computador, toque em Ligar o editor. Aparece um endereço como http://192.168.0.10:8090. Abra no navegador do computador, no mesmo Wi-Fi, e toque em Pedir para entrar. O aparelho mostra um código de 4 números; autorize só se for o mesmo da página.",
            "No computador dá para editar as músicas com teclado (Ctrl+S salva), mudar tom, capo e tags, destacar frases (botão Destacar), montar setlists e importar colando a cifra, colando o endereço da página ou soltando arquivos.",
            "Enquanto o editor está ligado, o ícone do computador aparece no topo das abas. Tocando nele você vê o endereço e pode fechar o editor. Ele também desliga sozinho depois de 5 minutos sem uso ou quando você fecha o app.",
        ],
        "text_en": [
            "In Settings → Edit on the computer, tap Turn on the editor. An address shows up, like http://192.168.0.10:8090. Open it in the computer's browser, on the same Wi-Fi, and tap Ask to come in. The device shows a 4-digit code; approve only if it matches the page.",
            "On the computer you can edit songs with a keyboard (Ctrl+S saves), change key, capo and tags, highlight phrases (Highlight button), build setlists and import by pasting chords, pasting a page address or dropping files.",
            "While the editor is on, the computer icon shows at the top of the tabs. Tap it to see the address and close the editor. It also turns off by itself after 5 minutes unused or when you close the app.",
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
            "Quando sai uma versão nova, o app avisa ao abrir. É bom que toda a banda use a mesma versão; um aparelho em outra versão aparece marcado em Ajustes → Banda.",
            "Em Ajustes → Sobre ficam o idioma, a versão e o botão Abrir a ajuda, que traz para esta página (precisa de internet).",
            "Suas músicas e ajustes ficam no aparelho. O que vai para a banda ou pela internet vai cifrado. Não tem conta, propaganda nem rastreio.",
        ],
        "text_en": [
            "When a new version is out, the app tells you when it opens. It's best if the whole band uses the same version; a device on another one shows up marked in Settings → Band.",
            "Settings → About has the language, the version and the Open the help button, which brings you to this page (it needs the internet).",
            "Your songs and settings stay on the device. What goes to the band or over the internet is encrypted. There's no account, no ads and no tracking.",
        ],
        "figs": [Fig(("tablet_5_ajustes_atualizar", "celular_5_ajustes_lingua", "Ajustes → Sobre: ajuda, idioma e atualizar", "Settings → About: help, language and updating")),
                 Fig(("tablet_8_versao_nova", None, "Versão nova", "New version"))],
    },
]

UI = {
    "pt-BR": {"title": "Ajuda do Stagebook", "lead": "Como usar cada parte do app.", "contents": "Nesta página",
              "tablet": "Tablet", "phone": "Celular", "other": "English", "other_href": "../help/", "lang": "pt-BR"},
    "en": {"title": "Stagebook help", "lead": "How to use each part of the app.", "contents": "On this page",
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
