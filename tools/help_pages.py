#!/usr/bin/env python3
"""
The help (T07): docs/ajuda/ (Portuguese) and docs/help/ (English), one page each, built from the captures folder
(tools/screenshots.sh) so every picture is the app as it is now. A screen that exists on phone and tablet gets a switch
between the two (the reader sees their own device). Run again after tools/screenshots.sh.
    tools/help_pages.py [captures folder]      (default: ~/Desktop/stagebook-capturas)
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

def sub(sid, pt, en, text_pt, text_en, figs=(), level=3):
    """A topic inside a section: its captures sit right under its text. level 4 hangs under the level 3 before it."""
    return {"id": sid, "pt": pt, "en": en, "text_pt": text_pt, "text_en": text_en, "figs": figs, "level": level}


SECTIONS = [
    {
        "id": "instalar", "pt": "Instalar", "en": "Installing",
        "text_pt": [
            "O Stagebook não está na Play Store: ele sai só pelo GitHub, de graça. No aparelho, abra [github.com/julianoAffonso/stagebook/releases](https://github.com/julianoAffonso/stagebook/releases/latest) e baixe o stagebook.apk da versão mais nova. Precisa de Android 9 ou mais novo.",
            "Abra o arquivo baixado. Na primeira vez o Android pede para permitir instalar apps dessa fonte (o navegador ou o app de arquivos): permita, volte e toque em Instalar. Se o Play Protect disser que não conhece o app, escolha instalar mesmo assim.",
            "Depois disso não precisa voltar ao GitHub: o app avisa quando sai versão nova e se atualiza. Na primeira atualização o Android pede a mesma permissão, agora para o Stagebook.",
            "Por que fora da Play Store? O app importa letras e cifras de sites, e as regras da loja sobre conteúdo com direito autoral deixam esse tipo de app sujeito a ser retirado de uma hora para outra. Além disso, ele se atualiza direto pelo GitHub, o que a loja não permite. Assim as versões saem sem esperar revisão, e o código fica aberto para quem quiser conferir.",
        ],
        "text_en": [
            "Stagebook isn't on the Play Store: it's only on GitHub, for free. On the device, open [github.com/julianoAffonso/stagebook/releases](https://github.com/julianoAffonso/stagebook/releases/latest) and download stagebook.apk from the newest version. It needs Android 9 or newer.",
            "Open the downloaded file. The first time, Android asks to allow installing apps from that source (the browser or the files app): allow it, go back and tap Install. If Play Protect says it doesn't know the app, choose to install anyway.",
            "After that there's no need to go back to GitHub: the app tells you when a new version is out and updates itself. On the first update Android asks for the same permission, this time for Stagebook.",
            "Why not the Play Store? The app imports lyrics and chords from websites, and the store's rules on copyrighted content leave this kind of app open to being pulled at any time. It also updates itself straight from GitHub, which the store doesn't allow. This way new versions come out without waiting for a review, and the code is open for anyone to check.",
        ],
        "figs": [],
    },
    {
        "id": "musicas", "pt": "Músicas", "en": "Songs",
        "text_pt": [
            "A aba Músicas é a sua biblioteca. Tocando numa música, ela abre no palco. Nova música começa uma em branco, e Importar traz de outros lugares.",
        ],
        "text_en": [
            "The Songs tab is your library. Tap a song and it opens on stage. New song starts a blank one, and Import brings them from elsewhere.",
        ],
        "figs": [Fig(("tablet_1_musicas", "celular_1_musicas", "A lista de músicas", "The song list"))],
        "subs": [
            sub("musicas-filtrar", "Buscar e filtrar", "Search and filter",
                ["Dá para buscar pelo nome ou pelo artista e filtrar pelas tags que você criou. Com um filtro ligado, o total no topo mostra também quantas sobraram, a lista volta ao começo e o × ao lado limpa tudo."],
                ["You can search by title or artist and filter by the tags you created. With a filter on, the total at the top also shows how many are left, the list goes back to the start and the × next to it clears everything."],
                [Fig((None, "celular_1_musicas_filtradas", "Com um filtro ligado", "With a filter on")),
                 Fig(("tablet_1_musicas_tags_abertas", None, "Filtro por tags", "Filter by tags"))]),
            sub("musicas-selecionar", "Selecionar várias", "Select several",
                ["Segure uma música para selecionar. Depois toque nas outras, ou em Selecionar todas, para pôr num setlist, mudar as tags, compartilhar ou apagar de uma vez."],
                ["Hold a song to select it. Then tap the others, or Select all, to add them to a setlist, change their tags, share or delete them at once."],
                [Fig(("jornada_musicas_1_selecao", None, "Várias músicas selecionadas", "Several songs selected"))]),
            sub("musicas-icones", "Ícones ao lado do título", "Icons next to the title",
                ["- **Folha:** o modo essencial está ligado.",
                 "- **Grupo com um número:** a banda mandou algo.",
                 "- **Computador:** o editor web está ligado.",
                 "Tocando no ícone você vai direto para o assunto."],
                ["- **Leaf:** essential mode is on.",
                 "- **Group with a number:** the band sent something.",
                 "- **Computer:** the web editor is on.",
                 "Tap an icon to go straight to it."]),
        ],
    },
    {
        "id": "importar", "pt": "Importar", "en": "Importing",
        "text_pt": [
            "O botão Importar, na aba Músicas, reúne tudo o que traz música de fora:",
            "- **Sincronizar com a banda:** procura novidades da banda agora.",
            "- **Recebidos da banda:** o que a banda mandou, para você conferir.",
            "- **Da internet:** abre os sites da sua lista.",
            "- **Ler QR:** lê o QR de um setlist com a câmera.",
            "- **Digitar as 3 palavras:** recebe o que alguém mandou pela internet.",
        ],
        "text_en": [
            "The Import button, on the Songs tab, gathers everything that brings songs in:",
            "- **Sync with the band:** looks for what's new from the band right now.",
            "- **Received from the band:** what the band sent, for you to check.",
            "- **From the web:** opens the sites on your list.",
            "- **Scan QR:** reads a setlist's QR with the camera.",
            "- **Type the 3 words:** receives what someone sent over the internet.",
        ],
        "figs": [Fig(("jornada_importar_menu", None, "O menu Importar", "The Import menu"))],
        "subs": [
            sub("importar-internet", "Da internet", "From the web",
                ["O importador abre o site marcado em [Sites de cifra](#ajustes-sites). Digite a música, o artista ou o endereço da página; uma busca digitada procura no Google. Abra a página da música e toque em Importar. Se o app não achar a letra, selecione-a com o dedo e toque de novo.",
                 "O app só baixa o que você mandar, e o que você importa fica só neste aparelho. Se o texto não parecer letra ou cifra, o app não aceita.",
                 "A música abre no editor para você conferir e salvar."],
                ["The importer opens the site marked in [Chord sites](#ajustes-sites). Type the song, the artist or the page address; a typed search goes to Google. Open the song's page and tap Import. If the app can't find the lyrics, select them with your finger and tap it again.",
                 "The app only downloads what you ask for, and what you import stays on this device. If the text doesn't look like lyrics or chords, the app won't take it.",
                 "The song opens in the editor for you to check and save."]),
            sub("importar-dados", "Tom, capo, artista e ano", "Key, capo, artist and year",
                ["Tom, capotraste e BPM vêm do que a página diz, em qualquer site. Com capotraste, o tom guardado é o dos desenhos da cifra. O que faltar de artista, ano e BPM o app completa pela internet. Sem internet, preencha em Dados da música."],
                ["Key, capo and BPM come from what the page says, on any site. With a capo, the saved key is the one of the chart's shapes. Whatever is missing of artist, year and BPM the app fills in over the internet. Offline, fill it in under Song details."]),
            sub("importar-tab", "Tablatura", "Tablature",
                ["Se a cifra tiver tablatura, o app pergunta se ela vem junto: Com a tablatura ou Sem a tablatura. Dá para apagar depois no editor."],
                ["If the chart has tablature, the app asks whether to bring it: With the tab or Without the tab. You can delete it later in the editor."]),
        ],
    },
    {
        "id": "editor", "pt": "Editor da música", "en": "Song editor",
        "text_pt": [
            "No editor a letra fica com a tela toda. O ícone de apresentação no topo, Ver como fica no palco, mostra o resultado antes de salvar, e o lápis volta ao texto.",
        ],
        "text_en": [
            "In the editor the lyrics get the whole screen. The presentation icon at the top, See it on stage, shows the result before you save, and the pencil goes back to the text.",
        ],
        "figs": [],
        "subs": [
            sub("editor-dados", "Dados da música", "Song details",
                ["Nome, artista, ano, BPM, tom, capotraste, tags e backing track ficam em Dados da música, no topo. Toque para abrir. Deixou artista ou ano em branco? Ao salvar, o app completa pela internet.",
                 "Em Tags você marca as da música ou cria uma Nova tag."],
                ["Name, artist, year, BPM, key, capo, tags and backing track are under Song details at the top. Tap to open them. Left artist or year blank? When you save, the app fills them in over the internet.",
                 "In Tags you check the song's tags or create a New tag."],
                [Fig(("tablet_4_editor_dados", "celular_4_editor", "Dados da música abertos", "Song details open")),
                 Fig(("tablet_4_editor_tags", "celular_4_editor_tags", "Tags da música", "The song's tags"))]),
            sub("editor-tom", "Tom e capotraste", "Key and capo",
                ["O − e + do tom transpõem a cifra meio tom de cada vez. O do capotraste não mexe nos diagramas, só avisa em que tom vai soar."],
                ["The key's − and + transpose the chords a half step at a time. The capo's don't change the diagrams; they only tell you what key it will sound in."],
                [Fig(("tablet_4_editor_capo", None, "Tom e capotraste", "Key and capo"))]),
            sub("editor-acordes", "Acordes", "Chords",
                ["Os acordes vão entre colchetes, no meio da letra: [G]Hoje eu [D]vou. Se você colar uma cifra com os acordes na linha de cima, o app converte sozinho. Os atalhos acima da letra inserem os acordes da música, e Acorde insere um novo.",
                 "Eles ficam escritos como vieram do site (A7+, E7/9, E*), e o app mostra o diagrama certo para cada um.",
                 "Anotações na linha dos acordes ficam na mesma linha no palco: repetições como 2x ou (x4), barras | e rótulos como Intro:. Um acorde seguido das casas de cada corda (B/F#  224442) aparece como os outros acordes, com as casas ao lado, e o desenho dele no violão e na guitarra usa essas casas. Ao transpor, as casas acompanham o acorde."],
                ["Chords go in brackets, inside the lyrics: [G]Here I [D]go. If you paste a chart with the chords on the line above, the app converts it for you. The shortcuts above the lyrics insert the song's chords, and Chord inserts a new one.",
                 "They stay written the way the site wrote them (A7+, E7/9, E*), and the app shows the right diagram for each.",
                 "Notes on the chords line stay on that line on stage: repeats like 2x or (x4), bars | and labels like Intro:. A chord followed by the fret of each string (B/F#  224442) shows like the other chords, with the frets beside it, and its acoustic and electric guitar diagram uses those frets. When you transpose, the frets move with the chord."],
                [Fig((None, "celular_4_editor_cifra", "Letra com os acordes", "Lyrics with chords"))]),
            sub("editor-partes", "Partes da música", "Song parts",
                ["Uma linha só com um nome entre colchetes vira o título de uma parte: [Refrão], [Solo de violino]. Os atalhos no topo põem as partes mais usadas."],
                ["A line with just a name in brackets becomes a part title: [Chorus], [Violin solo]. The shortcuts at the top add the usual parts."],
                [Fig(("tablet_4_editor_secoes", None, "Atalhos das partes", "Part shortcuts"))]),
            sub("editor-salvar", "Salvar e excluir", "Saving and deleting",
                ["A música precisa de um nome, e não dá para ter duas com o mesmo nome e artista. Saindo com algo sem salvar, o app pergunta antes. A lixeira, numa música já salva, exclui a música, que também sai dos setlists."],
                ["A song needs a name, and you can't have two with the same name and artist. If you leave with something unsaved, the app asks first. The trash can, on a saved song, deletes it, and it also leaves the setlists."]),
            sub("editor-marca", "Marca-texto", "Highlighter",
                ["Para destacar uma frase, selecione-a na letra e toque em Marca-texto. Ela fica pintada no editor e no palco. A cor fica em [Visual do palco](#ajustes-letra)."],
                ["To highlight a phrase, select it in the lyrics and tap Highlighter. It shows painted in the editor and on stage. The color is in [Stage look](#ajustes-letra)."]),
        ],
    },
    {
        "id": "setlists", "pt": "Setlists", "en": "Setlists",
        "text_pt": [
            "Para criar um setlist, toque em Novo setlist. Sem nome, ele fica com a data e a hora. Acima da lista dá para buscar pelo nome e, em Ordem, ver os recentes primeiro ou de A a Z. Com uma busca, o total no topo mostra também quantos sobraram, e o × ao lado limpa.",
        ],
        "text_en": [
            "To create a setlist, tap New setlist. With no name it takes the date and time. Above the list you can search by name and, in Order, show the newest first or A to Z. With a search on, the total at the top also shows how many are left, and the × next to it clears it.",
        ],
        "figs": [Fig(("tablet_2_setlists", "celular_2_setlists", "Os setlists", "The setlists"))],
        "subs": [
            sub("setlists-dentro", "Dentro do setlist", "Inside a setlist",
                ["- Toque no nome para renomear. A lixeira no topo apaga o setlist, e as músicas continuam na biblioteca.",
                 "- Arraste as músicas para mudar a ordem. Segure uma para selecionar e tirar do setlist.",
                 "- O play no topo abre o setlist no palco, e tocar numa música abre o palco nela.",
                 "- Compartilhar manda o setlist, e o ícone de grupo conduz a banda."],
                ["- Tap the name to rename it. The trash can at the top deletes the setlist, and the songs stay in the library.",
                 "- Drag songs to change the order. Hold one to select it and take it out of the setlist.",
                 "- The play button at the top opens the setlist on stage, and tapping a song opens the stage on it.",
                 "- Share sends the setlist, and the group icon leads the band."],
                [Fig(("tablet_3_setlist", "celular_3_setlist", "Dentro de um setlist", "Inside a setlist"))]),
            sub("setlists-musicas", "Pôr músicas", "Adding songs",
                ["Dentro do setlist, toque em Músicas e marque as músicas na ordem em que vão ser tocadas. A busca e as tags ajudam a achar. Pela aba Músicas também dá: selecione várias e use Adicionar ao setlist.",
                 "Um setlist precisa caber num QR, então tem limite de tamanho. Quando encher, o app avisa: crie outro."],
                ["Inside the setlist, tap Songs and check the songs in the order they'll be played. Search and tags help you find them. You can also do it from the Songs tab: select several and use Add to setlist.",
                 "A setlist has to fit in a QR, so its size has a limit. When it's full the app tells you: create another."],
                [Fig(("tablet_9_escolher_musicas", "celular_9_escolher_musicas", "Escolhendo as músicas", "Picking the songs"))]),
        ],
    },
    {
        "id": "compartilhar", "pt": "Compartilhar e QR", "en": "Sharing and QR",
        "text_pt": [
            "Num setlist, ou com várias músicas selecionadas, Compartilhar oferece os caminhos abaixo. Quem recebe sempre confere antes de entrar.",
        ],
        "text_en": [
            "In a setlist, or with several songs selected, Share offers the ways below. Whoever receives always checks before anything comes in.",
        ],
        "figs": [Fig(("jornada_compartilhar_1_folha", None, "Compartilhar", "Share"))],
        "subs": [
            sub("compartilhar-qr", "QR sem internet", "QR without internet",
                ["Quem está do lado lê com a câmera, em Importar → Ler QR, mesmo sem internet. Vão só os títulos. Na primeira vez, o app pede a câmera."],
                ["Whoever is next to you reads it with the camera, in Import → Scan QR, even offline. Only the titles go. The first time, the app asks for the camera."]),
            sub("compartilhar-palavras", "3 palavras pela internet", "3 words over the internet",
                ["Enviar pela internet gera 3 palavras e um QR para quem vai receber. Dá para ditar as palavras, copiar ou mandar por mensagem. Elas valem por 12 horas. Quem recebe vai em Importar → Digitar as 3 palavras.",
                 "Com Levar as músicas ligado, as letras vão junto. Quem recebe só fica com as que não tinha; as dele não mudam."],
                ["Send over the internet makes 3 words and a QR for whoever will receive. You can say the words, copy them or send them in a message. They last 12 hours. The other person goes to Import → Type the 3 words.",
                 "With Bring the songs on, the lyrics go too. The other person only keeps the ones they didn't have; theirs don't change."],
                [Fig(("jornada_compartilhar_2_codigo", None, "As 3 palavras", "The 3 words")),
                 Fig(("jornada_compartilhar_3_receber", None, "Recebendo pelas 3 palavras", "Receiving with the 3 words"))]),
            sub("compartilhar-conferir", "Conferir o que chegou", "Checking what arrived",
                ["Por QR, 3 palavras ou pela banda, a tela de conferir é a mesma. Ela mostra o que é novo, o que você já tem e o que falta. Antes de criar, o app avisa o que vai ficar de fora.",
                 "Se você tem outra versão de uma música, Ver o que muda mostra a diferença, e você escolhe entre Usar a recebida e Fica a minha. Se veio só a letra, dá para aceitar ou usar Buscar com cifra.",
                 "Se já existe um setlist com o mesmo nome, você escolhe entre Substituir e Criar outro."],
                ["By QR, 3 words or from the band, the review screen is the same. It shows what's new, what you already have and what's missing. Before creating, the app tells you what will be left out.",
                 "If you have another version of a song, See what changes shows the difference, and you pick Use theirs or Keep mine. If only the lyrics came, you can accept them or use Find with chords.",
                 "If a setlist with the same name already exists, you pick Replace or Create another."],
                [Fig(("jornada_qr_3_conferir_tablet", "jornada_qr_2_conferir", "Conferindo o que chegou", "Checking what arrived")),
                 Fig(("jornada_conferencia_criar_aviso", None, "Antes de criar: o que vai faltar", "Before creating: what will be missing")),
                 Fig(("jornada_conferencia_versoes", None, "Outra versão da mesma música", "Another version of the same song"))]),
            sub("compartilhar-faltam", "Buscar as que faltam", "Getting the missing songs",
                ["- **Pedir à banda:** se alguém estiver na rede.",
                 "- **Pedir pela internet a quem mandou:** se essa pessoa for da banda. As músicas chegam depois em Recebidos da banda.",
                 "- **Importar dos sites:** sempre dá. O app abre uma música de cada vez, e você pode pular ou parar."],
                ["- **Ask the band:** if someone is on the network.",
                 "- **Ask the sender online:** if they're in the band. The songs arrive later in Received from the band.",
                 "- **Import from the sites:** always possible. The app opens one song at a time, and you can skip or stop."],
                [Fig(("jornada_qr_5_faltam_tablet", "jornada_qr_4_faltam", "Como buscar as que faltam", "Ways to get the missing songs"))]),
        ],
    },
    {
        "id": "palco", "pt": "Palco", "en": "Stage",
        "text_pt": [
            "No palco a letra aparece grande, com a cifra e as partes. Num setlist, o topo mostra a posição, como 2 de 12, e o fim da música mostra a próxima. Enquanto o palco está aberto, nada aparece por cima: o que a banda mandar fica esperando.",
        ],
        "text_en": [
            "On stage the lyrics show large, with the chords and parts. In a setlist, the top shows the position, like 2 of 12, and the end of the song shows the next one. While the stage is open nothing pops up over it: whatever the band sends waits.",
        ],
        "figs": [Fig(("tablet_6_palco", "celular_7_palco", "O palco", "The stage")),
                 Fig(("tablet_6_palco_cifra", "celular_7_palco_cifra", "Com a cifra", "With chords"))],
        "subs": [
            sub("palco-gestos", "Virar a página", "Turning the page",
                ["- **Toque:** no lado direito avança, no esquerdo volta.",
                 "- **Dedo:** arraste para o lado.",
                 "- **Pedal:** veja [Pedal](#ajustes-pedal).",
                 "Num setlist, avançar na última página passa para a próxima música, e voltar na primeira volta para a anterior. Um botão de pedal também pode pular direto de música.",
                 "Prefere rolar a letra? Troque em [Visual do palco](#ajustes-pagina). Em Rolar, cada toque ou pedal leva até a primeira linha que não cabia, e ela acende por um instante."],
                ["- **Tap:** the right side goes forward, the left side goes back.",
                 "- **Finger:** drag sideways.",
                 "- **Pedal:** see [Pedal](#ajustes-pedal).",
                 "In a setlist, going forward on the last page moves to the next song, and going back on the first page returns to the previous one. A pedal button can also jump straight between songs.",
                 "Rather scroll the lyrics? Change it in [Stage look](#ajustes-pagina). In Scroll, each tap or pedal press goes to the first line that didn't fit, and it lights up for a moment."],
                [Fig(("tablet_6_palco_rolar", None, "No modo Rolar", "In Scroll mode"))]),
            sub("palco-rolagem", "Rolagem automática", "Auto-scroll",
                ["No modo Rolar, o ▶ faz a letra subir sozinha, na velocidade que você escolher. Tocar na tela ou apertar o pedal pausa."],
                ["In Scroll mode, ▶ moves the lyrics up on their own, at the speed you pick. Tapping the screen or pressing the pedal pauses it."],
                [Fig(("tablet_6_palco_rolagem_auto", None, "Rolagem automática", "Auto-scroll"))]),
            sub("palco-zoom", "Zoom da música", "A song's zoom",
                ["Enquanto você não muda o zoom, a música se ajusta sozinha à página do aparelho: fica no maior tamanho com o menor número de páginas, sem cortar um verso. Quando a letra não tem linha em branco, ela conta como um verso só. Afaste ou aproxime dois dedos para mudar o tamanho só da música aberta. O zoom fica guardado nela. No aviso que aparece, Automático volta ao ajuste pela página e Padrão fixa o tamanho dos ajustes. Com a lista de acordes ligada, o zoom muda só a letra e as cifras."],
                ["Until you change its zoom, a song adjusts itself to the page of the device: the largest size with the fewest pages, without cutting a verse. Lyrics with no blank lines count as a single verse. Spread or pinch two fingers to change the size of the open song only. The zoom stays saved in it. In the notice that shows up, Automatic goes back to fitting the page and Default sets the size in Settings. With the chord list on, the zoom changes only the lyrics and chords."],
                [Fig((None, "celular_7_palco_zoom", "O aviso do zoom", "The zoom notice"))]),
            sub("palco-tom", "Tom e capo", "Key and capo",
                ["O tom e o capo ficam no topo. Tocando neles você muda ali mesmo, e fica salvo na música."],
                ["Key and capo are at the top. Tap them to change them right there, and it's saved in the song."],
                [Fig((None, "celular_7_palco_tom_capo", "Mudando tom e capo", "Changing key and capo"))]),
            sub("palco-editar", "Corrigir e marcar", "Fixing and marking",
                ["O lápis no topo abre o editor nesta página. Segurando o dedo numa linha aparecem Editar esta linha e Marca-texto.",
                 "Com o Marca-texto ligado, passe o dedo nas palavras para destacar. Passar de novo apaga. Enquanto ele está ligado, o palco não vira página com o dedo; toque em Pronto para terminar."],
                ["The pencil at the top opens the editor on this page. Holding a line shows Edit this line and Highlighter.",
                 "With the Highlighter on, run your finger over the words to mark them. Again removes it. While it's on, the stage doesn't turn pages by touch; tap Done to finish."]),
            sub("palco-sair", "Sair do palco", "Leaving the stage",
                ["A seta no topo sai do palco. Num setlist, ou acompanhando a banda, ela pergunta antes, para um toque sem querer não tirar a letra da tela no show. Uma música aberta pela aba Músicas sai direto."],
                ["The arrow at the top leaves the stage. In a setlist, or following the band, it asks first, so a stray tap doesn't take the lyrics off the screen during the show. A song opened from the Songs tab leaves at once."]),
            sub("palco-acorde", "Diagrama do acorde", "Chord diagram",
                ["Tocando num acorde você vê o diagrama, para violão, guitarra, ukulele, cavaquinho ou teclado. As setas trocam a variação, Usar esta guarda a escolhida nesta música e Tocar faz o som.",
                 "Com capotraste, o diagrama mostra o capo na casa dele, com o desenho tocado a partir dali. No teclado, os acordes aparecem ajustados para você tocar junto com a banda."],
                ["Tap a chord to see its diagram for acoustic guitar, electric guitar, ukulele, cavaquinho or keyboard. The arrows change the variation, Use this one keeps it in this song and Play makes the sound.",
                 "With a capo, the diagram shows it on its fret, with the shape played from there. On keyboard, the chords show adjusted so you can play along with the band."],
                [Fig(("tablet_6_acorde_violao", None, "O diagrama do acorde", "The chord diagram")),
                 Fig(("tablet_6_acorde_capo", None, "O capo no diagrama", "The capo on the diagram")),
                 Fig(("tablet_6_palco_teclado_capo", None, "Teclado numa cifra com capo", "Keyboard on a chart with a capo"))]),
            sub("palco-detalhes", "Detalhes do acorde", "Chord details",
                ["Detalhes mostra o grau do acorde no tom da música, o campo harmônico dele e onde mais ele aparece. Com capotraste, o campo harmônico mostra o que soa, não o desenho da cifra."],
                ["Details shows the chord's degree in the song's key, its harmonic field and where else it appears. With a capo, the harmonic field shows what sounds, not the chart's shape."],
                [Fig(("tablet_6_acorde_detalhes", "celular_6_acorde_detalhes", "Detalhes do acorde", "Chord details"))]),
            sub("palco-lista", "Lista de acordes", "Chord list",
                ["Os diagramas de todos os acordes da música podem ficar no início ou no fim do palco, crescendo para ocupar a largura da tela. Ligue em [Diagramas dos acordes](#ajustes-diagramas).",
                 "Segure um acorde para tirá-lo da lista. O × no canto esconde a lista inteira nessa música, e o botão com a seta traz tudo de volta."],
                ["The diagrams of all the song's chords can sit at the start or end of the stage, grown to fill the screen's width. Turn it on in [Chord diagrams](#ajustes-diagramas).",
                 "Hold a chord to take it off the list. The × in the corner hides the whole list in that song, and the arrow button brings everything back."],
                [Fig(("tablet_6_palco_acordes_topo", "celular_7_palco_acordes_topo", "Lista de acordes no início", "Chord list at the start"))]),
            sub("palco-tab", "Tablatura", "Tablature",
                ["A tablatura aparece no meio da letra, como veio da cifra."],
                ["Tablature shows inside the lyrics, the way it came in the chart."],
                [Fig(("tablet_6_palco_tab", "celular_7_palco_tab", "Tablatura", "Tablature"))]),
            sub("palco-metronomo", "Metrônomo", "Metronome",
                ["O metrônomo é o ícone embaixo, ao lado do relógio. Ele começa no BPM da música, ou em 100 se ela não tiver, e mudar o BPM nele grava na música. Ali mesmo você começa, para e muda a velocidade. Contagem, som e brilho ficam em [Metrônomo e player](#ajustes-metronomo).",
                 "Ao ligar o metrônomo com saída na interface USB, o app liga a interface sozinho, com ou sem faixa."],
                ["The metronome is the icon at the bottom, next to the clock. It starts at the song's BPM, or 100 if it has none, and changing the BPM there saves it in the song. Right there you start it, stop it and change the speed. Count-in, sound and glow are in [Metronome and player](#ajustes-metronomo).",
                 "Turning the metronome on with its output on the USB interface turns the interface on by itself, with or without a track."],
                [Fig(("tablet_6_palco_metronomo", None, "Metrônomo", "Metronome")),
                 Fig(("tablet_6_palco_metronomo_contagem", None, "A contagem antes de começar", "The count-in before it starts"))]),
        ],
    },
    {
        "id": "backing", "pt": "Backing track", "en": "Backing track",
        "text_pt": [
            "Cada música pode ter um backing track, em OGG com vários canais, MP3 ou WAV. Escolha nos Dados da música. No palco, o play só aparece nas músicas que têm um.",
        ],
        "text_en": [
            "Each song can have a backing track, as multichannel OGG, MP3 or WAV. Pick it in Song details. On stage, play only shows on songs that have one.",
        ],
        "figs": [Fig(("tablet_11_player_com_faixa", "celular_11_player_tocando", "Tocando com backing track", "Playing with a backing track"))],
        "subs": [
            sub("backing-tocar", "Tocar e trocar", "Playing and changing",
                ["Quando a música acaba, ele para e volta ao começo. Segurando o play você troca ou tira a faixa e abre o mixer da música.",
                 "Trocar de música com a faixa tocando não para a faixa: ela continua até você tocar em parar ou ela acabar, e o botão de parar fica em qualquer música enquanto isso. Depois o play já toca a música que está na tela.",
                 "Para a faixa não parar com a tela apagada, o Android precisa liberar o Stagebook da economia de bateria. O app pede isso numa janela: toque em Continuar e depois em Permitir."],
                ["When the song ends it stops and goes back to the start. Hold play to change or remove the track and to open the song's mixer.",
                 "Changing songs while a track plays doesn't stop it: it goes on until you tap stop or it ends, and the stop button stays on any song meanwhile. Then play plays the song on screen.",
                 "For the track not to stop with the screen off, Android has to free Stagebook from battery saving. The app asks in a window: tap Continue and then Allow."],
                [Fig(("jornada_faixa_4_menu", None, "Trocar ou tirar a faixa", "Change or remove the track"))]),
            sub("backing-saida", "Por onde o som sai", "Where the sound goes out",
                ["O som sai por uma interface de áudio USB, em USB Audio 2.0 e 48 kHz, ou pela saída do aparelho. Se a interface desconectar no meio do show, o app espera ela voltar em vez de jogar o som no alto-falante.",
                 "Em alguns aparelhos, como celulares Samsung e tablets mais simples, a interface conecta mas o som não sai ou sai picotado. Nesse caso ligue Tocar pela interface através do Android, em [Metrônomo e player](#ajustes-player)."],
                ["Sound goes out through a USB audio interface, with USB Audio 2.0 and 48 kHz, or the device's own output. If the interface disconnects mid-show, the app waits for it to come back instead of sending the sound to the speaker.",
                 "On some devices, like Samsung phones and simpler tablets, the interface connects but there's no sound or it stutters. Then turn on Play on the interface through Android, in [Metronome and player](#ajustes-player)."]),
            sub("backing-primeira", "A primeira vez com a interface", "The first time with the interface",
                ["- Ligue a interface no cabo USB. O Android pergunta qual app abrir: toque em Stagebook e em Apenas uma vez.",
                 "- O Stagebook abre e pede o acesso ao áudio: permita.",
                 "- Tire e ponha o cabo de novo, escolha Stagebook e toque em Sempre.",
                 "Pronto: ao religar o cabo, o som volta sozinho, sem perguntar nada. O Stagebook não grava nada; a entrada da interface só acerta o relógio."],
                ["- Plug the interface into the USB cable. Android asks which app to open: tap Stagebook and Just once.",
                 "- Stagebook opens and asks for audio access: allow it.",
                 "- Unplug and plug the cable again, pick Stagebook and tap Always.",
                 "That's it: when the cable comes back, the sound returns by itself, without asking anything. Stagebook records nothing; the interface's input only keeps the clock."]),
        ],
    },
    {
        "id": "mixer", "pt": "Mixer", "en": "Mixer",
        "text_pt": [
            "O mixer fica só no palco, por cima da música tocando, para ajustar ouvindo. Toque uma música, segure o play e abra o mixer. Em Ajustes nada toca, então não daria para ajustar ouvindo.",
        ],
        "text_en": [
            "The mixer is only on the stage, over the playing song, so you adjust while listening. Play a song, hold play and open the mixer. Nothing plays in Settings, so it couldn't be adjusted by ear.",
        ],
        "figs": [Fig(("jornada_mixer_2_tablet", "jornada_mixer_1_celular", "O mixer", "The mixer"))],
        "subs": [
            sub("mixer-canais", "Canais", "Channels",
                ["O mixer tem um canal para cada canal do arquivo, com fader e botão para desligar o canal. Ver os níveis ao vivo mostra o sinal de cada um. Tocando no nome do canal você renomeia, e o ícone ao lado abre os outros ajustes:",
                 "- **Saída:** em qual saída da interface ele toca. A escolha fica guardada para cada interface.",
                 "- **Igualar o volume:** Não toca como foi gravado, Média deixa todas as músicas no mesmo volume percebido e Pico, com o mesmo pico.",
                 "- **Estéreo e mono:** junta dois canais em estéreo, ou toca no meio, igual nos dois lados.",
                 "- **Equalizador ligado:** faz o canal passar pelo equalizador."],
                ["The mixer has one channel per file channel, with a fader and a button to turn the channel off. Monitor live levels shows each one's signal. Tap a channel's name to rename it, and the icon next to it opens the other settings:",
                 "- **Output:** which interface output it plays on. The choice is kept for each interface.",
                 "- **Even out the volume:** No plays it as recorded, Average gives all songs the same perceived volume and Peak the same peak.",
                 "- **Stereo and mono:** joins two channels as stereo, or plays in the center, the same on both sides.",
                 "- **Equalizer on:** sends the channel through the equalizer."],
                [Fig(("jornada_mixer_3_editar_canal", None, "Ajustes do canal", "Channel settings"))]),
            sub("mixer-global", "Mixer global ou da música", "Global or song mixer",
                ["Com Usar o mixer global ligado, o que você muda vale para todas as músicas. Desligado, vale só para aquela música. Voltar ao global apaga os ajustes próprios dela."],
                ["With Use the global mixer on, what you change applies to every song. Off, it applies only to that song. Going back to the global one erases the song's own settings."],
                [Fig(("jornada_eq_musica_2_inteiro", None, "Mixer e EQ da música no palco", "The song's mixer and EQ on stage"))]),
            sub("mixer-eq", "Equalizador", "Equalizer",
                ["São 5 bandas numa curva só, aplicada em cada canal marcado com EQ, cada um separado. Um limitador segura os picos, e Zerar volta a curva ao plano. Sem nenhum canal marcado, ligar o equalizador não muda nada: o app avisa."],
                ["It has 5 bands in a single curve, applied to each channel marked EQ, each one separately. A limiter holds the peaks, and Reset flattens the curve. With no channel marked, turning the equalizer on changes nothing: the app tells you."]),
        ],
    },
    {
        "id": "banda", "pt": "Banda", "en": "Band",
        "text_pt": [
            "Autorizados uma vez, os aparelhos da banda trocam setlists e músicas sem as 3 palavras e podem acompanhar quem conduz.",
        ],
        "text_en": [
            "Once allowed, the band's devices exchange setlists and songs without the 3 words and can follow whoever leads.",
        ],
        "figs": [],
        "subs": [
            sub("banda-entrar", "Entrar na banda", "Joining the band",
                ["Com os aparelhos no mesmo Wi-Fi, que pode ser o ponto de acesso de um celular, os dois abrem [Banda e transmissão](#ajustes-banda). Um toca em Pedir para entrar e o outro em Autorizar. Depois disso eles se reconhecem até alguém remover. Na primeira vez, o app pergunta como a banda vai ver este aparelho."],
                ["With the devices on the same Wi-Fi, which can be a phone's hotspot, both open [Band and broadcast](#ajustes-banda). One taps Ask to join and the other taps Allow. After that they know each other until someone removes it. The first time, the app asks how the band will see this device."]),
            sub("banda-mandar", "Mandar e receber", "Sending and receiving",
                ["Mandar um setlist para a banda é um toque, em Compartilhar. Quem está no mesmo Wi-Fi recebe na hora; quem não está, recebe pela internet quando abrir o app, em até 12 horas.",
                 "Quando chega algo, aparece um aviso rápido embaixo e um número no ícone da banda, ao lado do título da aba, até você conferir. Tudo fica em Recebidos da banda: Conferir abre, e a lixeira descarta. Se mais de uma pessoa mandou o mesmo setlist, abra cada versão e escolha uma."],
                ["Sending a setlist to the band takes one tap, in Share. Whoever is on the same Wi-Fi gets it right away; the others get it over the internet when they open the app, within 12 hours.",
                 "When something arrives you get a short notice at the bottom and a number on the band icon next to the tab's title until you check it. It all stays in Received from the band: Check opens it, and the trash can discards it. If more than one person sent the same setlist, open each version and pick one."],
                [Fig(("jornada_banda_3_compartilhar", None, "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso", None, "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("tablet_2_recebidos_versoes", None, "Versões do mesmo setlist", "Versions of the same setlist"))]),
            sub("banda-conduzir", "Conduzir a banda", "Leading the band",
                ["Conduzir a banda é o ícone de grupo no topo do setlist. Ele abre o palco, e os aparelhos da banda com o app aberto no mesmo Wi-Fi entram sozinhos em Acompanhando, na mesma música, e seguem cada troca.",
                 "- Quem acompanha não precisa ter o setlist, só as músicas. Se faltar uma, a faixa embaixo fica vermelha.",
                 "- No fim de cada música aparece a próxima do setlist de quem conduz.",
                 "- Quem abriu o app depois entra sozinho em alguns segundos. Se não entrar, toque em Procurar a banda.",
                 "- Quem está editando, escolhendo músicas, recebendo ou importando não é tirado da tela: a música abre quando a pessoa volta.",
                 "- Quem conduz vê em Ver o que falta quem não tem cada música.",
                 "- Sair do palco não encerra. Para isso, toque em Encerrar."],
                ["Lead the band is the group icon at the top of a setlist. It opens the stage, and band devices with the app open on the same Wi-Fi switch to Following by themselves, on the same song, and follow every change.",
                 "- Followers don't need the setlist, only the songs. If one is missing, the bar at the bottom turns red.",
                 "- At the end of each song they see the leader's next one.",
                 "- Someone who opens the app later joins by themselves within seconds. If not, tap Look for the band.",
                 "- Someone editing, picking songs, receiving or importing isn't pulled off the screen: the song opens when they come back.",
                 "- The leader sees in See what's missing who lacks each song.",
                 "- Leaving the stage doesn't end it. Tap End for that."],
                [Fig(("banda_conduzir_faixas", None, "Conduzindo a banda", "Leading the band")),
                 Fig(("tablet_6_palco_acompanhando", None, "Acompanhando no palco", "Following on stage"))]),
        ],
    },
    {
        "id": "ajustes", "pt": "Ajustes", "en": "Settings", "toc_subs": True,
        "text_pt": [
            "Os itens abaixo seguem a ordem da tela. No tablet deitado, a lista fica à esquerda e o assunto aberto à direita. Nos cartões, o ⓘ ao lado do título explica o que eles fazem. Com o modo essencial, alguns itens e opções somem até você religar.",
        ],
        "text_en": [
            "The items below follow the order on screen. On a tablet lying down, the list sits on the left and the open topic on the right. On the cards, the ⓘ next to the title explains what they do. With essential mode, some items and options disappear until you turn them back on.",
        ],
        "figs": [Fig(("tablet_5_ajustes", "celular_5_ajustes", "Os ajustes", "Settings"))],
        "subs": [
            sub("ajustes-palco", "Visual do palco", "Stage look",
                ["Tudo aqui vale para todas as músicas. A amostra no topo da tela fica presa e mostra cada mudança na hora."],
                ["Everything here applies to all songs. The sample at the top of the screen stays in place and shows each change right away."]),
            sub("ajustes-letra", "Letra", "Lyrics",
                ["- **Tamanho da letra:** vale para todas as músicas. Com o Zoom automático, é a referência: a música fica maior ou menor para aproveitar a página. No palco, a pinça muda só a música aberta.",
                 "- **Fonte da letra e Letra em negrito:** Padrão, Condensada, Serifada ou Monoespaçada.",
                 "- **Cor da letra, Cor do destaque e Cor do fundo:** o destaque é o do marca-texto.",
                 "Se uma cor ficar difícil de ler sobre o fundo, o app avisa logo abaixo."],
                ["- **Lyrics size:** applies to all songs. With Automatic zoom it's the reference: a song gets larger or smaller to make the most of the page. On stage, a pinch changes only the open song.",
                 "- **Lyrics font and Bold lyrics:** Default, Condensed, Serif or Monospace.",
                 "- **Lyrics color, Highlight color and Background color:** the highlight is the highlighter's.",
                 "If a color gets hard to read on the background, the app warns you right below."],
                [Fig((None, "celular_5_ajustes_letra", "Letra", "Lyrics")),
                 Fig((None, "celular_5_ajustes_cifras_ilegivel", "O aviso de cor ilegível", "The hard-to-read color warning"))], level=4),
            sub("ajustes-acordes", "Acordes na letra", "Chords in the lyrics",
                ["- **No palco:** Letra e cifra mostra os acordes em cima da letra. Só letra mostra só a letra, mesmo nas músicas com cifra.",
                 "- **Tamanho dos acordes, Fonte dos acordes e Cor dos acordes:** só aparecem com Letra e cifra."],
                ["- **On stage:** Lyrics and chords shows the chords above the lyrics. Lyrics only shows just the lyrics, even on songs with chords.",
                 "- **Chord size, Chords font and Chord color:** these show only with Lyrics and chords."],
                [Fig((None, "celular_5_ajustes_cifras", "Acordes na letra", "Chords in the lyrics"))], level=4),
            sub("ajustes-titulos", "Títulos de seção", "Section titles",
                ["Linhas como [Refrão], Verso 2, Ponte, Intro ou Solo viram títulos em destaque no palco.",
                 "- **Mostrar os títulos das seções:** desligado, eles somem do palco.",
                 "- **Tamanho dos títulos, Fonte dos títulos e Cor do título da seção:** o visual deles.",
                 "- **Nomes de seção no idioma do app:** Refrão, Chorus e outras formas aparecem todas como Refrão. A letra guardada não muda."],
                ["Lines like [Chorus], Verse 2, Bridge, Intro or Solo become highlighted titles on stage.",
                 "- **Show section titles:** off, they leave the stage.",
                 "- **Title size, Titles font and Section title color:** how they look.",
                 "- **Section names in the app's language:** Chorus, Refrão and other forms all show the same way. The saved lyrics don't change."],
                [Fig(("tablet_5_ajustes_secoes", "celular_5_ajustes_letra_secoes", "Títulos de seção", "Section titles"))], level=4),
            sub("ajustes-pagina", "Página", "Page",
                ["- **Como avançar na tela:** Virar página mostra uma página por vez, em uma ou duas colunas. Rolar mostra a letra numa coluna só.",
                 "- **Dividir em colunas:** só em Virar página. Automático divide em 2 colunas quando a letra não cabe numa página e as frases são curtas. Também dá para escolher Nunca ou Sempre. O pedal vira as duas colunas juntas.",
                 "- **Zoom automático:** só em Virar página. Ajusta cada música à tela sem cortar versos, até você mudar o zoom dela. Desligado, a música fica no tamanho dos ajustes.",
                 "- **Velocidade da rolagem automática:** só em Rolar."],
                ["- **Moving on screen:** Turn pages shows one page at a time, in one or two columns. Scroll shows the lyrics in a single column.",
                 "- **Split into columns:** only in Turn pages. Automatic splits into 2 columns when the lyrics don't fit one page and the lines are short. You can also pick Never or Always. The pedal turns both columns together.",
                 "- **Automatic zoom:** only in Turn pages. Fits each song to the screen without cutting verses, until you change its zoom. Off, a song stays at the size in Settings.",
                 "- **Auto-scroll speed:** only in Scroll."],
                [Fig(("tablet_5_ajustes_pagina", "celular_5_ajustes_pagina", "Página, no modo Virar página", "Page, in Turn pages mode")),
                 Fig(("tablet_5_ajustes_rolar", None, "Página, no modo Rolar", "Page, in Scroll mode"))], level=4),
            sub("ajustes-diagramas", "Diagramas dos acordes", "Chord diagrams",
                ["Este cartão só aparece com Letra e cifra.",
                 "- **Instrumento:** violão, guitarra, ukulele, cavaquinho ou teclado.",
                 "- **Mostrar o diagrama ao tocar no acorde:** desligado, tocar num acorde conta como toque na página.",
                 "- **Som do acorde:** Dedilhado, Acorde cheio ou Os dois.",
                 "- **Lista de acordes:** Esconder, Início ou Fim. Veja [Lista de acordes](#palco-lista)."],
                ["This card shows only with Lyrics and chords.",
                 "- **Instrument:** acoustic guitar, electric guitar, ukulele, cavaquinho or keyboard.",
                 "- **Show the diagram when you tap a chord:** off, tapping a chord counts as tapping the page.",
                 "- **Chord sound:** Picked, Full chord or Both.",
                 "- **Chord list:** Hide, Start or End. See [Chord list](#palco-lista)."],
                [Fig((None, "celular_5_ajustes_cifras_inteira", "Diagramas dos acordes", "Chord diagrams"))], level=4),
            sub("ajustes-tela", "Tela", "Screen",
                ["**Manter a tela ligada com o app aberto:** o aparelho não apaga enquanto o Stagebook estiver na frente. Fora do app, apaga normalmente. O palco fica sempre aceso, com isso ligado ou não."],
                ["**Keep the screen on while the app is open:** the device doesn't sleep while Stagebook is in front. Outside the app it sleeps normally. The stage always stays lit, with this on or not."], level=4),
            sub("ajustes-metronomo", "Metrônomo e player", "Metronome and player",
                ["Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado.",
                 "- **Mostrar no palco:** desligado, o ícone do metrônomo some do palco.",
                 "- **Contagem 1–2–3–4 antes:** conta um compasso ao começar.",
                 "- **Som:** o clique do metrônomo.",
                 "- **Saída do som:** com o player ligado, Aparelho é o fone ou alto-falante, e as saídas da interface USB deixam o baterista ouvir pela mesa. Para elas aparecerem, toque uma faixa com a interface conectada.",
                 "- **Brilho na borda da tela e Cor do brilho:** a batida pisca na borda, sem som."],
                ["When essential mode turns one of them off, the item is named after the one left on.",
                 "- **Show on stage:** off, the metronome icon leaves the stage.",
                 "- **Count in 1–2–3–4:** counts one bar when it starts.",
                 "- **Sound:** the metronome's click.",
                 "- **Sound output:** with the player on, Device is the headphones or speaker, and the USB interface outputs let the drummer hear it through the desk. For them to show up, play a track with the interface connected.",
                 "- **Glow on the screen edge and Glow color:** the beat flashes on the edge, with no sound."],
                [Fig(("tablet_5_ajustes_metronomo", None, "Metrônomo", "Metronome"))]),
            sub("ajustes-player", "Saída USB e botão da faixa", "USB output and track button",
                ["- **O que a saída USB precisa:** uma interface class compliant, com USB Audio Class 2.0 e 48 kHz, e um aparelho com USB OTG e o cabo certo.",
                 "- **Tocar pela interface através do Android:** ligue se a interface conecta mas o som não sai ou sai picotado. O som passa pelo áudio do Android, como nos outros apps de música.",
                 "- **Parar volta ao início:** desligado, o botão da faixa pausa, e a música continua de onde parou.",
                 "O mixer e o equalizador ficam no [palco](#mixer)."],
                ["- **What the USB output needs:** a class compliant interface, with USB Audio Class 2.0 and 48 kHz, and a device with USB OTG and the right cable.",
                 "- **Play on the interface through Android:** turn it on if the interface connects but there's no sound or it stutters. The sound goes through Android's audio, like other music apps.",
                 "- **Stop goes back to the start:** off, the track button pauses, and the song goes on from where it stopped.",
                 "The mixer and equalizer are on the [stage](#mixer)."],
                [Fig(("jornada_faixa_1_ajustes_player", None, "Saída USB e botão da faixa", "USB output and track button"))], level=4),
            sub("ajustes-pedal", "Pedal", "Pedal",
                ["O Stagebook aceita pedal Bluetooth, MIDI Bluetooth e MIDI USB. Qual é o meu? explica a diferença."],
                ["Stagebook takes Bluetooth, Bluetooth MIDI and USB MIDI pedals. Which one is mine? explains the difference."]),
            sub("ajustes-pedal-conectar", "Conectar o pedal", "Connect the pedal",
                ["- **Pedal Bluetooth comum:** vira a página como as setas de um teclado, como a maioria dos viradores de página. Ele é pareado nas configurações de Bluetooth do Android.",
                 "- **Pedal MIDI Bluetooth:** manda mensagens MIDI. O app procura aqui mesmo: toque no pedal achado para usar. Esquecer tira ele do app.",
                 "- **Pedal MIDI pelo USB:** desligado, o Stagebook deixa o USB livre para uma interface de áudio.",
                 "Pedal desligado ou longe conecta sozinho quando ligar. Em versões mais antigas do Android, a localização precisa estar ligada para a procura achar um pedal MIDI.",
                 "Com um pedal Bluetooth ligado, o teclado da tela pode sumir: o celular acha que o pedal é um teclado de verdade. Isso é do Android, não do app. No Gboard, ligue Configurações → Teclado físico → Mostrar teclado na tela. Em outros teclados, toque no ícone de teclado na barra de baixo e ligue Teclado na tela."],
                ["- **Regular Bluetooth pedal:** turns the page like a keyboard's arrows, as most page turners do. It's paired in Android's Bluetooth settings.",
                 "- **Bluetooth MIDI pedal:** sends MIDI messages. The app looks for it right here: tap the pedal it finds to use it. Forget removes it from the app.",
                 "- **MIDI pedal over USB:** off, Stagebook leaves the USB free for an audio interface.",
                 "A pedal that is off or far away connects by itself when it's on. On older Android versions, location must be on for the search to find a MIDI pedal.",
                 "With a Bluetooth pedal connected, the on-screen keyboard may disappear: the phone takes the pedal for a real keyboard. That comes from Android, not the app. In Gboard, turn on Settings → Physical keyboard → Show on-screen keyboard. In other keyboards, tap the keyboard icon on the bottom bar and turn on the on-screen keyboard."],
                [Fig((None, "celular_5_ajustes_pedal_midi", "Conectar o pedal", "Connect the pedal"))], level=4),
            sub("ajustes-pedal-botoes", "Botões", "Buttons",
                ["Aperte um botão do pedal: ele aparece aqui e você escolhe o que ele faz. As opções são Avançar a página, Voltar a página, Próxima música, Música anterior, Play/pause do backing track e Nada.",
                 "Em Botões do pedal fica cada botão já apertado, com o que faz. O que não foi mudado aparece como padrão: setas para baixo e para a direita, Page Down e Espaço avançam; setas para cima e para a esquerda e Page Up voltam. Com mais de um pedal, os botões ficam separados por pedal, com o conectado em cima."],
                ["Press a pedal button: it shows here and you pick what it does. The options are Next page, Previous page, Next song, Previous song, Play/pause backing track and Nothing.",
                 "Under the pedal's buttons you see each button already pressed, with what it does. One you didn't change shows as default: down and right arrows, Page Down and Space go forward; up and left arrows and Page Up go back. With more than one pedal, the buttons are grouped by pedal, the connected one on top."],
                [Fig((None, "celular_5_ajustes_pedal", "Botões do pedal", "Pedal buttons"))], level=4),
            sub("ajustes-sites", "Sites de cifra", "Chord sites",
                ["Em Só letra, o item se chama Sites de letra. O site marcado é onde o importador abre, e marcar outro leva ele para o topo. Adicionar site põe um endereço novo, e o × tira da lista."],
                ["In Lyrics only, the item is called Lyrics sites. The marked site is where the importer opens, and marking another moves it to the top. Add site puts in a new address, and the × takes one off the list."],
                [Fig((None, "celular_5_ajustes_importacao", "A lista de sites", "The site list"))]),
            sub("ajustes-biblioteca", "Biblioteca e backup", "Library and backup",
                ["- **Sua biblioteca:** quantas músicas e setlists você tem.",
                 "- **Tags:** Separar nacionais e internacionais marca, pelo idioma da letra, as músicas que ainda não têm uma dessas tags. Dá para corrigir depois.",
                 "- **Achar refrão e versos:** nas músicas sem títulos de parte, o app acha o refrão, que é a estrofe que se repete, e os versos. A letra não muda, e os títulos que já existem ficam como estão.",
                 "- **Outras formas de compartilhar:** lembra que o setlist vai na hora por [Compartilhar](#compartilhar)."],
                ["- **Your library:** how many songs and setlists you have.",
                 "- **Find chorus and verses:** on songs with no part titles, the app finds the chorus, which is the verse that repeats, and the verses. The lyrics don't change, and existing titles stay as they are.",
                 "- **Other ways to share:** reminds you that a setlist goes right away through [Share](#compartilhar).",
                 "The automatic split of local and international tags only shows with the app in Portuguese."],
                [Fig(("tablet_5_ajustes_backup", None, "Biblioteca e backup", "Library and backup"))]),
            sub("ajustes-backup", "Salvar e abrir arquivo", "Save and open a file",
                ["**Salvar arquivo** grava um .stagebook com o que estiver marcado: Músicas e setlists, com o zoom de cada música, Backing tracks e Ajustes. Serve de backup, para trocar de aparelho ou para mandar a alguém. Para mandar só as letras, desmarque as backing tracks, que deixam o arquivo grande.",
                 "**Abrir arquivo** mostra o que o arquivo tem para você marcar. Juntar com as minhas traz só o que você não tem. Substituir as minhas troca suas músicas e setlists pelos do arquivo. Os ajustes do arquivo substituem os daqui e, em outro aparelho, a banda precisa ser autorizada de novo."],
                ["**Save file** writes a .stagebook with what's checked: Songs and setlists, with each song's zoom, Backing tracks and Settings. It works as a backup, to change devices or to send to someone. To send only the lyrics, uncheck the backing tracks, which make the file large.",
                 "**Open file** shows what the file has for you to check. Join with mine brings only what you don't have. Replace mine swaps your songs and setlists for the file's. The file's settings replace the ones here and, on another device, the band has to be allowed again."],
                [Fig(("tablet_5_backup_restaurar", None, "Abrindo um arquivo", "Opening a file"))], level=4),
            sub("ajustes-apagar", "Apagar tudo", "Erase everything",
                ["Deixa o app como novo: apaga músicas, setlists, backing tracks, ajustes e a banda deste aparelho, sem volta. A janela oferece Fazer um backup antes e pede para digitar um código de confirmação."],
                ["Leaves the app like new: it erases songs, setlists, backing tracks, settings and the band from this device, for good. The window offers Back up first and asks you to type a confirmation code."],
                [Fig(("tablet_5_apagar_tudo", None, "Apagar tudo", "Erase everything"))], level=4),
            sub("ajustes-banda", "Banda e transmissão", "Band and broadcast",
                ["- **Este aparelho:** o nome que a banda vê. Mudar o nome troca.",
                 "- **Na rede agora:** quem pode entrar. Pedir para entrar fica ao lado de cada um. Um aparelho em outra versão do app aparece marcado.",
                 "- **Da banda:** quem já foi autorizado, marcado quando está conectado. A lixeira remove; para voltar, é preciso pedir para entrar de novo.",
                 "- **Avisar a música tocando:** Enviar a música atual para OBS e outros avisa na rede qual música está no palco, com artista, tom e BPM. A letra nunca vai. Veja o [guia do Now Playing](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.pt-BR.md).",
                 "O uso do dia a dia está em [Banda](#banda)."],
                ["- **This device:** the name the band sees. Change name changes it.",
                 "- **On the network now:** who can join. Ask to join sits next to each one. A device on another app version shows up marked.",
                 "- **In the band:** who was already allowed, marked when connected. The trash can removes one; to come back, they have to ask to join again.",
                 "- **Tell what's playing:** Send the current song to OBS and others tells the network which song is on stage, with artist, key and BPM. The lyrics never go out. See the [Now Playing guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.md).",
                 "Day-to-day use is in [Band](#banda)."],
                [Fig(("jornada_banda_1_ajustes", None, "Banda e transmissão", "Band and broadcast"))]),
            sub("ajustes-computador", "Editar no computador", "Edit on the computer",
                ["Liga o editor web e lista os Computadores autorizados. Veja [Editor no computador](#editor-web)."],
                ["Turns the web editor on and lists the Authorized computers. See [Editor on the computer](#editor-web)."]),
            sub("ajustes-essencial", "Modo essencial", "Essential mode",
                ["Serve para quem quer a tela mais simples. Ligar o modo essencial deixa só letra, cifra, diagramas e pedal, e desliga o que roda por trás. Em O que fica ligado dá para desligar só uma parte:",
                 "- **Banda na rede:** achar a banda no Wi-Fi, enviar pela banda e Conduzir a banda.",
                 "- **Internet:** enviar e receber pela internet, versão nova do app e completar artista, ano e BPM.",
                 "- **Player de faixas:** backing tracks, interface de áudio USB e mixer.",
                 "- **Metrônomo e extras dos acordes:** som e detalhes do acorde, metrônomo e Now Playing.",
                 "Desligada, a parte some da tela e para de rodar. Enquanto alguma parte estiver desligada, um aviso ao abrir o app lista o que está fora e leva de volta para cá. Não mostrar de novo tira o aviso. Veja [Quanto o app pesa](#desempenho)."],
                ["It's for a simpler screen. Turning on essential mode leaves only lyrics, chords, diagrams and the pedal, and turns off what runs in the background. In What stays on you can turn off just part of it:",
                 "- **Band on the network:** finding the band on Wi-Fi, sending through the band and Lead the band.",
                 "- **Internet:** sending and receiving over the internet, new app versions and filling in artist, year and BPM.",
                 "- **Track player:** backing tracks, USB audio interface and mixer.",
                 "- **Metronome and chord extras:** chord sound and details, metronome and Now Playing.",
                 "Once off, a part leaves the screen and stops running. While any part is off, a notice when the app opens lists what's out and leads back here. Don't show again removes it. See [How light it is](#desempenho)."],
                [Fig(("tablet_5_ajustes_essencial", None, "Modo essencial", "Essential mode")),
                 Fig(("tablet_1_aviso_essencial", None, "O aviso do modo essencial", "The essential mode notice"))]),
            sub("ajustes-avancado", "Avançado", "Advanced",
                ["São ajustes do próprio Android. Para mudá-los, o Stagebook precisa de uma permissão dada uma vez por um computador com o adb: a tela mostra o comando e Copiar o comando. Sem ela, os ajustes aparecem mas não mudam. Alguns só existem em certos aparelhos e só aparecem neles.",
                 "### Para tocar sem falhas",
                 "Eles já derrubaram a interface de áudio ou pararam o app no palco. Vêm ligados; desligue um só se souber por quê.",
                 "- **Liberar da economia de bateria:** o Android não para o Stagebook com a tela apagada. Este vale sem o adb: é um toque em Permitir.",
                 "- **Carga inteligente desligada:** em alguns tablets, a proteção de carga derruba a interface USB por um instante. O custo é carregar até 100%.",
                 "- **Som do Android fora da interface USB:** os sons do Android não disputam a interface com o player.",
                 "- **Gerenciador de bateria desligado:** o Android para de sugerir restringir o Stagebook.",
                 "### Bateria do aparelho",
                 "- **Animações mais curtas:** as telas trocam mais rápido e gastam menos bateria.",
                 "- **Tela apaga depois de 10 minutos:** fora do palco.",
                 "- **Sem buscas em segundo plano:** Wi-Fi e Bluetooth param de procurar localização quando estão desligados.",
                 "- **Dados móveis só sem Wi-Fi:** os dados móveis não ficam ligados com o Wi-Fi conectado.",
                 "### Computador",
                 "- **Modo desenvolvedor e depuração por Wi-Fi:** para testar pelo computador com o adb. Vem desligado."],
                ["These are Android's own settings. To change them, Stagebook needs a permission given once from a computer with adb: the screen shows the command and Copy the command. Without it the settings show but don't change. Some only exist on certain devices and only show there.",
                 "### For playing without failures",
                 "These have dropped the audio interface or stopped the app on stage. They come on; turn one off only if you know why.",
                 "- **Free from battery saving:** Android won't stop Stagebook with the screen off. This one works without adb: it's one tap on Allow.",
                 "- **Smart charging off:** on some tablets, charge protection drops the USB interface for a moment. The cost is charging up to 100%.",
                 "- **Android sound off the USB interface:** Android's sounds don't fight the player for the interface.",
                 "- **Battery manager off:** Android stops suggesting restricting Stagebook.",
                 "### Device battery",
                 "- **Shorter animations:** screens change faster and use less battery.",
                 "- **Screen off after 10 minutes:** outside the stage.",
                 "- **No searches in the background:** Wi-Fi and Bluetooth stop looking for location while they're off.",
                 "- **Mobile data only without Wi-Fi:** mobile data doesn't stay on with Wi-Fi connected.",
                 "### Computer",
                 "- **Developer mode and Wi-Fi debugging:** for testing from a computer with adb. It comes off."],
                [Fig(("tablet_5_ajustes_avancado", None, "Avançado", "Advanced")),
                 Fig((None, "celular_5_ajustes_avancado_permissao", "A permissão pelo computador", "The permission from the computer"))]),
            sub("ajustes-sobre", "Sobre", "About",
                ["- **Ajuda:** Abrir a ajuda traz para esta página. Precisa de internet.",
                 "- **Idioma:** Automático segue o aparelho, ou escolha português ou inglês. Para ajudar a traduzir, siga o [guia de tradução](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.pt-BR.md).",
                 "- **Atualizar o app:** a versão instalada e Procurar atualização. Veja [Atualizar](#atualizar).",
                 "- **Privacidade, Código disponível e Apoie o Stagebook:** como seus dados são tratados, o código do app e como apoiar.",
                 "No tablet, a versão também aparece embaixo do logo, no topo da barra lateral. Tocar no logo abre só o Sobre, e a seta volta para a tela onde você estava."],
                ["- **Help:** Open the help brings you to this page. It needs the internet.",
                 "- **Language:** Automatic follows the device, or pick Portuguese or English. To help translate, follow the [translation guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.md).",
                 "- **Update the app:** the installed version and Check for updates. See [Updating](#atualizar).",
                 "- **Privacy, Source available and Support Stagebook:** how your data is handled, the app's code and how to support it.",
                 "On a tablet, the version also shows under the logo at the top of the side bar. Tapping the logo opens just About, and the arrow takes you back to the screen you were on."],
                [Fig(("tablet_5_ajustes_atualizar", "celular_5_ajustes_lingua", "Ajustes → Sobre: ajuda, idioma e atualizar", "Settings → About: help, language and updating"))]),
        ],
    },
    {
        "id": "editor-web", "pt": "Editor no computador", "en": "Editor on the computer",
        "text_pt": [
            "O editor abre o Stagebook no navegador do computador: teclado de verdade, tela grande e tudo salvo direto no aparelho. O palco atualiza na hora. Nada passa pela internet: o computador conversa só com o aparelho, no seu Wi-Fi.",
        ],
        "text_en": [
            "The editor opens Stagebook in the computer's browser: a real keyboard, a big screen and everything saved straight to the device. The stage updates right away. Nothing goes through the internet: the computer talks only to the device, on your Wi-Fi.",
        ],
        "figs": [],
        "subs": [
            sub("web-ligar", "Ligar e entrar", "Turn it on and come in",
                ["- No aparelho, em Ajustes → Editar no computador, toque em Ligar o editor. Aparece um endereço como http://192.168.0.10:8090.",
                 "- Abra esse endereço no navegador do computador, no mesmo Wi-Fi. Rede de convidados costuma bloquear a conversa entre aparelhos.",
                 "- Toque em Pedir para entrar. O aparelho mostra um código de 4 números. Autorize só se for o mesmo da página.",
                 "- O computador fica guardado em Computadores autorizados, e dali dá para tirá-lo a qualquer hora."],
                ["- On the device, in Settings → Edit on the computer, tap Turn on the editor. An address shows up, like http://192.168.0.10:8090.",
                 "- Open that address in the computer's browser, on the same Wi-Fi. Guest networks usually block devices from talking to each other.",
                 "- Tap Ask to come in. The device shows a 4-digit code. Allow it only if it matches the page.",
                 "- The computer is kept under Authorized computers, and you can remove it from there at any time."],
                [Fig((None, "celular_5_ajustes_editor_web", "Ligando o editor no aparelho", "Turning on the editor on the device")),
                 Fig(("editor_web_1_pedido", None, "Pedindo para entrar", "Asking to come in"))]),
            sub("web-musicas", "Músicas", "Songs",
                ["- À esquerda ficam a busca, o filtro de tags e a lista. Clique numa música para abrir, ou em Nova música para começar uma.",
                 "- Dá para editar nome, artista, ano, BPM, tags e a letra com as cifras. Separe as tags com vírgula; as que você já usa aparecem como sugestão.",
                 "- Os botões − e + mudam o tom e o capotraste, como no app. A letra só muda de vez quando você salva.",
                 "- Para destacar uma frase, selecione-a e clique em Marca-texto.",
                 "- Com a música já salva, dá para enviar a backing track em OGG, MP3 ou WAV.",
                 "- Ctrl+S salva. Saindo com algo sem salvar, a página avisa antes."],
                ["- On the left are the search, the tag filter and the list. Click a song to open it, or New song to start one.",
                 "- You can edit name, artist, year, BPM, tags and the lyrics with chords. Separate tags with commas; the ones you already use show up as suggestions.",
                 "- The − and + buttons change the key and the capo, as in the app. The song only changes for good when you save.",
                 "- To highlight a phrase, select it and click Highlighter.",
                 "- Once the song is saved, you can send its backing track as OGG, MP3 or WAV.",
                 "- Ctrl+S saves. If you leave with something unsaved, the page warns you first."],
                [Fig(("editor_web_2_musica", None, "Editando uma música", "Editing a song"))]),
            sub("web-setlists", "Setlists", "Setlists",
                ["- A busca acha o setlist pelo nome. O combo Ordem mostra os recentes primeiro ou de A a Z, e a escolha vale também para a aba Setlists do aparelho.",
                 "- Dentro do setlist, a busca logo abaixo acrescenta músicas e as setas mudam a posição de cada uma."],
                ["- The search finds a setlist by name. The Order dropdown shows the newest first or A to Z, and the choice also applies to the device's Setlists tab.",
                 "- Inside a setlist, the search below adds songs and the arrows move each one."],
                [Fig(("editor_web_3_setlist", None, "Setlists com a busca e o combo Ordem", "Setlists with the search and the Order dropdown"))]),
            sub("web-importar", "Importar", "Importing",
                ["- Cole o endereço de uma página e clique em Trazer a música, ou cole a letra com as cifras em qualquer formato comum e clique em Converter. A música abre no editor para você conferir antes de salvar.",
                 "- Solte arquivos .txt, .pro ou .chordpro na área de arquivos, ou use Escolher arquivos, para trazer várias de uma vez.",
                 "- Digite as 3 palavras para receber o que alguém compartilhou, confira o que você já tem e crie o setlist.",
                 "- Em Recebidos da banda ficam os envios, com o botão para sincronizar.",
                 "- Abrir um arquivo `.stagebook` mostra o que o backup tem. Você escolhe as partes, e então junta com o que já existe ou substitui tudo."],
                ["- Paste a page address and click Bring the song, or paste lyrics with chords in any common format and click Convert. The song opens in the editor so you can check it before saving.",
                 "- Drop .txt, .pro or .chordpro files on the files area, or use Choose files, to bring in several at once.",
                 "- Type the 3 words to receive what someone shared, check what you already have and create the setlist.",
                 "- Received from the band holds what they sent, with the button to sync.",
                 "- Opening a `.stagebook` file shows what the backup has. You pick the parts, then merge them with what's there or replace everything."],
                [Fig(("editor_web_4_importar", None, "Importar no computador", "Importing on the computer"))]),
            sub("web-ligado", "Enquanto está ligado", "While it's on",
                ["O ícone do computador aparece ao lado do título das abas. Tocando nele você vê o endereço e pode fechar o editor. Ele começa desligado toda vez que o app abre e desliga sozinho depois de 5 minutos sem uso ou quando você fecha o app."],
                ["The computer icon shows next to the tabs' titles. Tap it to see the address and close the editor. It starts off every time the app opens, and turns off by itself after 5 minutes unused or when you close the app."]),
        ],
    },
    {
        "id": "assistentes", "pt": "Assistentes de IA", "en": "AI assistants",
        "text_pt": [
            "Com o servidor MCP do Stagebook, assistentes de IA como o Claude, o Cursor ou o Gemini mexem na sua biblioteca pelo mesmo editor. Você pede em português e o assistente faz no aparelho, sem copiar e colar.",
            "### O que você precisa",
            "- O [Node.js](https://nodejs.org) 20 ou mais novo no computador. O servidor está no npm como `stagebook-mcp`, e o assistente o baixa e atualiza sozinho.",
            "- O aparelho e o computador no mesmo Wi-Fi, com o Stagebook aberto e Ajustes → Editar no computador ligado.",
            "### Ligar no assistente",
            "- **Claude Code:** rode `claude mcp add stagebook -- npx -y stagebook-mcp`.",
            "- **Claude Desktop, Cursor, Windsurf e Gemini CLI:** na configuração de MCP, acrescente `{ \"mcpServers\": { \"stagebook\": { \"command\": \"npx\", \"args\": [\"-y\", \"stagebook-mcp\"] } } }`.",
            "- **VS Code e OpenAI Codex CLI:** o formato muda um pouco. Veja no [guia do servidor](https://github.com/julianoAffonso/stagebook/blob/main/mcp/README.pt-BR.md), que também diz onde fica o arquivo de cada assistente.",
            "- O app do ChatGPT só alcança servidores pela internet, então não chega ao aparelho no seu Wi-Fi.",
            "### Primeira conexão",
            "- Peça ao assistente para conectar no Stagebook. Ele procura na rede, ou você passa o endereço que aparece no aparelho.",
            "- O aparelho mostra um pedido com um código. Se bater, toque em Autorizar. Da próxima vez ele entra sem perguntar.",
            "- A autorização dura 90 dias sem uso, e Computadores autorizados remove a qualquer hora.",
            "### O que dá para pedir",
            "- **Músicas:** \"acha as músicas em Sol\", \"passa esta meio tom abaixo\", \"coloca capo na 2\", \"confere as cifras desta\".",
            "- **Importar:** \"importa esta cifra\" colando o texto ou o endereço da página.",
            "- **Setlists:** \"monta um setlist para sábado com 15 músicas\", \"acha o setlist do casamento\", \"deixa os setlists de A a Z\".",
            "- **Backing tracks:** \"usa este arquivo como faixa da música\", com o caminho completo do arquivo.",
            "- **Receber:** \"recebe o setlist destas 3 palavras\", \"o que a banda mandou?\".",
            "- **Backup:** \"o que tem neste arquivo .stagebook?\" e \"junta o que falta\".",
            "### O que pede confirmação",
            "Apagar, tirar uma faixa, esvaziar um setlist, substituir a biblioteca e restaurar os ajustes só acontecem depois que o assistente pergunta a você.",
            "### Se não conectar",
            "- **Não acha o aparelho:** confira se o Stagebook está aberto, se o editor está ligado e se os dois estão no mesmo Wi-Fi. O editor desliga sozinho sem uso. Passe o endereço ao assistente.",
            "- **No Mac:** em Ajustes do Sistema → Privacidade e Segurança → Rede Local, ligue o app que roda o assistente e reabra esse app.",
            "- **A chave não vale mais:** ela foi removida ou passou 90 dias sem uso. Conecte de novo.",
            "- **Já tem um pedido na tela:** responda no aparelho. Depois de um Recusar, ele espera 5 minutos para aceitar outro.",
            "- **Uma parte não responde:** receber por código precisa da internet ligada, e as funções de banda precisam da banda ligada, nos Ajustes do aparelho.",
            "### Privacidade",
            "O servidor só conversa com o aparelho. O assistente vê o que as ferramentas devolvem, como títulos, artistas, acordes e, quando ele pede, a letra, e manda isso ao provedor de IA dele, como o resto da conversa.",
        ],
        "text_en": [
            "With Stagebook's MCP server, AI assistants like Claude, Cursor or Gemini work on your library through the same editor. You ask in plain words and the assistant does it on the device, with no copying and pasting.",
            "### What you need",
            "- [Node.js](https://nodejs.org) 20 or newer on the computer. The server is on npm as `stagebook-mcp`, and the assistant downloads and updates it by itself.",
            "- The device and the computer on the same Wi-Fi, with Stagebook open and Settings → Edit on the computer turned on.",
            "### Connect your assistant",
            "- **Claude Code:** run `claude mcp add stagebook -- npx -y stagebook-mcp`.",
            "- **Claude Desktop, Cursor, Windsurf and Gemini CLI:** in the MCP settings, add `{ \"mcpServers\": { \"stagebook\": { \"command\": \"npx\", \"args\": [\"-y\", \"stagebook-mcp\"] } } }`.",
            "- **VS Code and OpenAI Codex CLI:** the format changes a little. See the [server guide](https://github.com/julianoAffonso/stagebook/blob/main/mcp/README.md), which also says where each assistant keeps its file.",
            "- The ChatGPT app only reaches servers over the internet, so it can't get to the device on your Wi-Fi.",
            "### First connection",
            "- Ask the assistant to connect to Stagebook. It searches the network, or you give it the address shown on the device.",
            "- The device shows a request with a code. If it matches, tap Allow. Next time it comes in without asking.",
            "- The authorization lasts 90 days unused, and Authorized computers removes it at any time.",
            "### What you can ask",
            "- **Songs:** \"find the songs in G\", \"move this one down a half tone\", \"put a capo on 2\", \"check this one's chords\".",
            "- **Importing:** \"import these chords\", pasting the text or the page address.",
            "- **Setlists:** \"build a setlist for Saturday with 15 songs\", \"find the wedding setlist\", \"put the setlists A to Z\".",
            "- **Backing tracks:** \"use this file as the song's track\", with the file's full path.",
            "- **Receiving:** \"receive the setlist with these 3 words\", \"what did the band send?\".",
            "- **Backup:** \"what's in this .stagebook file?\" and \"merge what's missing\".",
            "### What asks you first",
            "Deleting, removing a track, emptying a setlist, replacing the library and restoring settings only happen after the assistant asks you.",
            "### If it doesn't connect",
            "- **It can't find the device:** check that Stagebook is open, the editor is on and both are on the same Wi-Fi. The editor turns off when unused. Give the assistant the address.",
            "- **On a Mac:** in System Settings → Privacy & Security → Local Network, turn on the app running the assistant and reopen that app.",
            "- **The key no longer works:** it was removed or went 90 days unused. Connect again.",
            "- **A request is already on screen:** answer it on the device. After a Deny, it waits 5 minutes before taking another.",
            "- **One part doesn't answer:** receiving by code needs the internet on, and the band tools need the band on, in the device's Settings.",
            "### Privacy",
            "The server only talks to the device. The assistant sees what the tools return, like titles, artists, chords and, when it asks, the lyrics, and sends that to its AI provider, like the rest of the conversation.",
        ],
        "figs": [],
    },
    {
        "id": "atualizar", "pt": "Atualizar", "en": "Updating",
        "text_pt": [
            "Quando sai uma versão nova, o app avisa ao abrir e mostra o que mudou. Atualizar baixa e abre a instalação, que o Android pede para confirmar, e Agora não deixa para o dia seguinte. Se só fechar a janela, o aviso volta na próxima vez que abrir o app. Se você pulou versões, aparece o que mudou em cada uma. A mesma lista fica em Ajustes → Sobre → Atualizar o app. É bom que toda a banda use a mesma versão; um aparelho em outra versão aparece marcado em Ajustes → Banda e transmissão.",
            "Em aparelhos Samsung, o Bloqueador automático pode impedir a instalação, e ele volta a ligar sozinho depois de um tempo. Quando a versão nova termina de baixar, a janela mostra Abrir o Bloqueador automático: desligue e volte para instalar. O botão aparece também em Ajustes → Sobre → Atualizar o app.",
            "Suas músicas e ajustes ficam no aparelho. O que vai para a banda ou pela internet vai cifrado. Não tem conta, propaganda nem rastreio.",
        ],
        "text_en": [
            "When a new version is out, the app tells you when it opens and shows what changed. Update downloads it and opens the install, which Android asks you to confirm, and Not now leaves it for the next day. If you just close the window, the notice comes back the next time you open the app. If you skipped versions, you see what changed in each one. The same list is in Settings → About → Update the app. It's best if the whole band uses the same version; a device on another one shows up marked in Settings → Band and broadcast.",
            "On Samsung devices, Auto Blocker can stop the install, and it turns itself back on after a while. When the new version finishes downloading, the window shows Open Auto Blocker: turn it off and come back to install. The button also shows in Settings → About → Update the app.",
            "Your songs and settings stay on the device. What goes to the band or over the internet is encrypted. There's no account, no ads and no tracking.",
        ],
        "figs": [Fig(("tablet_8_versao_nova", "celular_8_versao_nova", "Versão nova e o que mudou", "New version and what changed")),
                 Fig(("tablet_8_bloqueador", None, "Num Samsung, depois de baixar", "On a Samsung, after downloading"))],
    },
    {
        "id": "desempenho", "pt": "Quanto o app pesa", "en": "How light it is",
        "text_pt": [
            "O Stagebook foi medido em três aparelhos: um tablet TCL NXTPAPER 11 Plus, um Galaxy S26 Ultra e um tablet Chuwi Hi10 XPro, mais simples. Parado na lista ou no palco, ele usa de 0,1% a 1,5% de um núcleo do processador; virando página, até 13%; tocando um backing track numa interface USB, 15%. Os três aparelhos têm 8 núcleos, então mesmo tocando ele usa menos de 2% do processador inteiro.",
            "A memória fica entre 115 e 255 MB, conforme o aparelho e a tela. O modo essencial quase não muda esses números: o ganho dele é deixar na tela só o que você usa.",
        ],
        "text_en": [
            "Stagebook was measured on three devices: a TCL NXTPAPER 11 Plus tablet, a Galaxy S26 Ultra and a simpler Chuwi Hi10 XPro tablet. Idle on the list or the stage it uses 0.1% to 1.5% of one processor core; turning pages, up to 13%; playing a backing track on a USB interface, 15%. All three devices have 8 cores, so even playing it uses less than 2% of the whole processor.",
            "Memory stays between 115 and 255 MB, depending on the device and the screen. Essential mode barely changes these numbers: its gain is keeping on screen only what you use.",
        ],
        "figs": [],
    },
    {
        "id": "problemas", "pt": "Se algo não funcionar", "en": "If something doesn't work",
        "text_pt": ["As mensagens mais comuns e o que fazer. Para assistentes de IA, veja [Se não conectar](#assistentes)."],
        "text_en": ["The most common messages and what to do. For AI assistants, see [If it doesn't connect](#assistentes)."],
        "figs": [],
        "subs": [
            sub("problemas-som", "Som e interface USB", "Sound and USB interface",
                ["- **A saída USB não respondeu:** tire e ponha o cabo de novo.",
                 "- **Saída USB não encontrada:** o som foi para a saída do aparelho. Confira o cabo e o adaptador.",
                 "- **Saída USB desconectada:** o app espera a interface voltar. Confira o cabo.",
                 "- **Permissão da saída USB negada:** tire e ponha o cabo e autorize, como em [A primeira vez com a interface](#backing-primeira).",
                 "- **A interface conecta mas não sai som, ou sai picotado:** ligue [Tocar pela interface através do Android](#ajustes-player).",
                 "- **A interface não funciona:** ela precisa de USB Audio 2.0 e 48 kHz. Interfaces só USB Audio 1.0, ou só de 44,1 kHz, ainda não funcionam.",
                 "- **Esse arquivo não serve:** use OGG, MP3 ou WAV.",
                 "- **O som cai de tempos em tempos:** confira [Para tocar sem falhas](#ajustes-avancado)."],
                ["- **The USB output didn't respond:** unplug the cable and plug it back in.",
                 "- **No USB output found:** the sound went to the device's output. Check the cable and adapter.",
                 "- **USB output disconnected:** the app waits for the interface to come back. Check the cable.",
                 "- **USB output permission denied:** unplug and plug the cable and allow it, as in [The first time with the interface](#backing-primeira).",
                 "- **The interface connects but there's no sound, or it stutters:** turn on [Play on the interface through Android](#ajustes-player).",
                 "- **The interface doesn't work:** it needs USB Audio 2.0 and 48 kHz. Interfaces with only USB Audio 1.0, or only 44.1 kHz, don't work yet.",
                 "- **This file won't work:** use OGG, MP3 or WAV.",
                 "- **The sound drops now and then:** check [For playing without failures](#ajustes-avancado)."]),
            sub("problemas-pedal", "Pedal", "Pedal",
                ["- **Nenhum pedal MIDI achado:** confira se ele está ligado, perto e no modo MIDI Bluetooth, e se o Bluetooth do aparelho está ligado.",
                 "- **Este aparelho não tem suporte a MIDI:** use um pedal Bluetooth comum.",
                 "- **O teclado da tela sumiu:** veja [Conectar o pedal](#ajustes-pedal-conectar)."],
                ["- **No MIDI pedal found:** check that it's on, nearby and in Bluetooth MIDI mode, and that the device's Bluetooth is on.",
                 "- **This device doesn't support MIDI:** use a regular Bluetooth pedal.",
                 "- **The on-screen keyboard disappeared:** see [Connect the pedal](#ajustes-pedal-conectar)."]),
            sub("problemas-receber", "QR e 3 palavras", "QR and 3 words",
                ["- **O QR veio incompleto:** aproxime e leia de novo.",
                 "- **Este QR não é do Stagebook:** aponte para o QR do setlist ou o das 3 palavras.",
                 "- **Nada chegou com essas palavras:** confira. Elas valem por 12 horas depois do envio.",
                 "- **Veio de um Stagebook mais novo:** [atualize o app](#atualizar) para receber."],
                ["- **The QR came in incomplete:** move closer and scan again.",
                 "- **This QR isn't from Stagebook:** point at the setlist QR or the 3 words QR.",
                 "- **Nothing came with these words:** check them. They last 12 hours after sending.",
                 "- **It came from a newer Stagebook:** [update the app](#atualizar) to receive it."]),
            sub("problemas-banda", "Banda", "Band",
                ["- **Não acha o outro aparelho:** os dois precisam estar no mesmo Wi-Fi, com Banda e transmissão aberto.",
                 "- **Outra versão do app:** atualizem os dois para a mesma versão.",
                 "- **Está no palco agora:** peça de novo depois do show."],
                ["- **It can't find the other device:** both must be on the same Wi-Fi, with Band and broadcast open.",
                 "- **Different app version:** update both to the same version.",
                 "- **They're on stage right now:** ask again after the show."]),
            sub("problemas-importar", "Importar", "Importing",
                ["- **Não achei a letra nesta página:** abra a página da música ou selecione a letra com o dedo e toque em Importar.",
                 "- **Esta é a página de busca:** abra a página da cifra e toque em Importar.",
                 "- **Isso não parece letra nem cifra:** o Stagebook guarda só letras e cifras, em texto."],
                ["- **Couldn't find the lyrics on this page:** open the song's page, or select the lyrics with your finger and tap Import.",
                 "- **This is the search page:** open the chords page and tap Import.",
                 "- **This doesn't look like lyrics or chords:** Stagebook only keeps lyrics and chords, as text."]),
            sub("problemas-atualizar", "Atualizar", "Updating",
                ["- **Num Samsung, a atualização não instala:** desligue o Bloqueador automático, como em [Atualizar](#atualizar), e tente de novo.",
                 "- **O download não terminou:** confira a internet e tente de novo."],
                ["- **On a Samsung, the update won't install:** turn off Auto Blocker, as in [Updating](#atualizar), and try again.",
                 "- **The download didn't finish:** check the internet and try again."]),
            sub("problemas-web", "Editor no computador", "Editor on the computer",
                ["- **Não achei o Stagebook:** confira se o app está aberto e o editor ligado. Ele desliga sozinho depois de 5 minutos sem uso.",
                 "- **A página não abre:** os dois precisam estar no mesmo Wi-Fi, e rede de convidados costuma bloquear."],
                ["- **Can't reach Stagebook:** check that the app is open and the editor is on. It turns off by itself after 5 minutes unused.",
                 "- **The page doesn't open:** both must be on the same Wi-Fi, and guest networks usually block it."]),
        ],
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
nav.toc { position: sticky; top: 16px; align-self:start; background:var(--surface); border-radius:20px; padding:16px; max-height: calc(100vh - 32px); overflow-y:auto; }
nav.toc b { display:block; color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.06em; margin-bottom:8px; }
nav.toc a { display:block; color:var(--fg); text-decoration:none; padding:4px 8px; border-radius:10px; }
nav.toc a:hover { background:var(--raised); }
nav.toc a.sub { padding-left:20px; font-size:14px; color:var(--muted); }
section { padding-top: 32px; min-width: 0; }
section + section { border-top: 1px solid var(--line); margin-top: 32px; }
[id] { scroll-margin-top: 16px; }
section a { color: var(--accent); }
h2 { font-size: 24px; margin: 0 0 8px; color: var(--accent); text-wrap: balance; }
h3 { font-size: 19px; margin: 28px 0 4px; }
h4 { font-size: 16px; margin: 20px 0 4px; color: var(--muted); }
section ul { margin: 4px 0 12px; padding-left: 20px; }
section li { margin-bottom: 4px; }
p { max-width: 70ch; } code { background:var(--raised); padding:1px 6px; border-radius:6px; font-size: 14px; }
/* two lying captures per row on a tablet; a standing one (phone) keeps its own width, no empty frame around it */
.figs { display:flex; flex-wrap:wrap; gap: 20px; margin: 16px 0 8px; align-items:flex-start; }
figure { margin:0; background:var(--surface); border-radius:20px; padding:12px; flex: 1 1 360px; min-width:0; max-width:min(100%, 560px); }
figure.tall { flex: 0 1 auto; }
figure img { width:100%; height:auto; border-radius:12px; display:block; }
figure.tall img { height: 440px; width:auto; max-width:100%; }
figcaption { color:var(--muted); font-size:14px; margin-top:8px; display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
.switch { margin-left:auto; display:flex; background:var(--raised); border-radius:999px; padding:2px; }
.switch button { border:0; background:none; color:var(--fg); font: 600 12px system-ui, sans-serif; padding:4px 10px; border-radius:999px; cursor:pointer; }
.switch button[aria-pressed=true] { background:var(--accent); color:var(--on-accent); }
/* standing tablet or phone: the contents become a compact strip of links instead of a tall list */
@media (max-width: 800px) { .wrap { grid-template-columns: 1fr; } nav.toc { position: static; padding: 12px; }
  nav.toc { max-height: none; }
  nav.toc a, nav.toc a.sub { display:inline-block; background:var(--raised); margin: 0 4px 6px 0; padding: 4px 12px; border-radius: 999px; font-size: 14px; } }
"""

JS = """
document.querySelectorAll('figure[data-tablet][data-phone]').forEach(f => {
  const img = f.querySelector('img');
  f.querySelectorAll('.switch button').forEach(b => b.onclick = () => {
    const phone = b.dataset.d === 'phone';
    img.src = phone ? f.dataset.phone : f.dataset.tablet;
    f.classList.toggle('tall', phone);
    f.querySelectorAll('.switch button').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
  });
});
"""


def md(text):
    """**bold**, `code` and [text](https://link or #topic) (the only marks the texts use)."""
    out = html.escape(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"\[([^\[\]]+?)\]\(((?:https://|#)[^)\s]+)\)", r'<a href="\2">\1</a>', out)
    return re.sub(r"`(.+?)`", r"<code>\1</code>", out)


def blocks(lines):
    """A text's lines: "### " is a small subtitle, lines in a row starting with "- " are a list, the rest are paragraphs."""
    out, items = [], []
    for line in lines + [""]:
        if line.startswith("- "):
            items.append(f"<li>{md(line[2:])}</li>")
            continue
        if items:
            out.append(f"<ul>{''.join(items)}</ul>")
            items = []
        if line.startswith("### "):
            out.append(f"<h4>{md(line[4:])}</h4>")
        elif line:
            out.append(f"<p>{md(line)}</p>")
    return "".join(out)


def picture(lang, name, img_dir):
    """Copies a capture into the help as a JPEG no larger than 1400 px (light pages); returns its file name."""
    src = next((CAPS / lang).rglob(f"{name}.png"), None)
    if src is None:
        raise SystemExit(f"captura não encontrada: {lang}/{name}.png (rode tools/screenshots.sh)")
    dst = img_dir / f"{name}.jpg"
    subprocess.run(["sips", "-Z", "1400", "-s", "format", "jpeg", "-s", "formatOptions", "82", str(src), "--out", str(dst)],
                   check=True, capture_output=True)
    return dst.name


def portrait(path):
    """A capture standing up (a phone, or a tablet held upright): shown with a limited height."""
    out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(path)], check=True, capture_output=True, text=True).stdout
    w, h = (int(re.search(rf"{k}: (\d+)", out).group(1)) for k in ("pixelWidth", "pixelHeight"))
    return h > w


def figures(part, ui, key, lang, img_dir):
    """The captures of a section or topic, right under its text."""
    figs = []
    for tablet, phone, cap_pt, cap_en in part["figs"]:
        cap = html.escape(cap_pt if key == "pt" else cap_en)
        t_img = f"img/{picture(lang, tablet, img_dir)}" if tablet else None
        p_img = f"img/{picture(lang, phone, img_dir)}" if phone else None
        first = t_img or p_img
        switch = ""
        attrs = ""
        if t_img and p_img:
            attrs = f' data-tablet="{t_img}" data-phone="{p_img}"'
            switch = (f'<span class="switch"><button data-d="tablet" aria-pressed="true">{ui["tablet"]}</button>'
                      f'<button data-d="phone" aria-pressed="false">{ui["phone"]}</button></span>')
        # lying captures go two per row (user, 03/11: one per row made the page very long); a standing one keeps
        # its own width
        tall = ' class="tall"' if portrait(img_dir.parent / first) else ""
        figs.append(f'<figure{tall}{attrs}><img src="{first}" alt="{cap}" loading="lazy"><figcaption>{cap}{switch}</figcaption></figure>')
    return f'<div class="figs">{"".join(figs)}</div>' if figs else ""


def build(lang):
    ui, out = UI[lang], OUT[lang]
    key = "pt" if lang == "pt-BR" else "en"
    if out.exists(): shutil.rmtree(out)
    img_dir = out / "img"
    img_dir.mkdir(parents=True)
    toc, body = [], []
    for s in SECTIONS:
        toc.append(f'<a href="#{s["id"]}">{html.escape(s[key])}</a>')
        subs = s.get("subs", [])
        if s.get("toc_subs"):
            toc += [f'<a class="sub" href="#{t["id"]}">{html.escape(t[key])}</a>' for t in subs if t["level"] == 3]
        topics = "".join(
            f'<h{t["level"]} id="{t["id"]}">{html.escape(t[key])}</h{t["level"]}>{blocks(t["text_" + key])}{figures(t, ui, key, lang, img_dir)}'
            for t in subs
        )
        body.append(f'<section id="{s["id"]}"><h2>{html.escape(s[key])}</h2>{blocks(s["text_" + key])}{figures(s, ui, key, lang, img_dir)}{topics}</section>')
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
