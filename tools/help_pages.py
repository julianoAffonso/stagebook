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
            "O Stagebook não está na Play Store: ele sai só pelo GitHub, de graça. No aparelho, abra [stagebook.app.br](https://stagebook.app.br) e toque em Baixar para Android, que traz o stagebook.apk da versão mais nova. Precisa de Android 9 ou mais novo.",
            "Abra o arquivo baixado. Na primeira vez o Android pede para permitir instalar apps dessa fonte (o navegador ou o app de arquivos): permita, volte e toque em Instalar. Se o Play Protect disser que não conhece o app, escolha instalar mesmo assim.",
            "Depois disso não precisa voltar ao GitHub: o app avisa quando sai versão nova e se atualiza. Na primeira atualização o Android pede a mesma permissão, agora para o Stagebook.",
            "Por que fora da Play Store? O app importa letras e cifras de sites, e as regras da loja sobre conteúdo com direito autoral deixam esse tipo de app sujeito a ser retirado de uma hora para outra. Além disso, ele se atualiza direto pelo GitHub, o que a loja não permite. Assim as versões saem sem esperar revisão, e o código fica aberto para quem quiser conferir.",
        ],
        "text_en": [
            "Stagebook isn't on the Play Store: it's only on GitHub, for free. On the device, open [stagebook.app.br](https://stagebook.app.br) and tap Download for Android, which gets stagebook.apk from the newest version. It needs Android 9 or newer.",
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
                [Fig(("tablet_1_musicas_filtradas", "celular_1_musicas_filtradas", "Com um filtro ligado", "With a filter on")),
                 Fig(("tablet_1_musicas_tags_abertas", "celular_1_musicas_tags_abertas", "Filtro por tags", "Filter by tags"))]),
            sub("musicas-selecionar", "Selecionar várias", "Select several",
                ["Segure uma música para selecionar. Depois toque nas outras, ou em Selecionar todas, para adicionar a um setlist, mudar as tags, compartilhar ou apagar de uma vez."],
                ["Hold a song to select it. Then tap the others, or Select all, to add them to a setlist, change their tags, share or delete them at once."],
                [Fig(("jornada_musicas_1_selecao_tablet", "jornada_musicas_1_selecao", "Várias músicas selecionadas", "Several songs selected"))]),
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
            "- **Da internet:** abre os sites da sua lista. No perfil Automático, o app pergunta antes se você quer importar letra ou cifra, e abre o site daquele tipo.",
            "- **Ler QR:** lê o QR de um setlist com a câmera.",
            "- **Digitar as 3 palavras:** recebe o que alguém mandou pela internet.",
        ],
        "text_en": [
            "The Import button, on the Songs tab, gathers everything that brings songs in:",
            "- **Sync with the band:** looks for what's new from the band right now.",
            "- **Received from the band:** what the band sent, for you to check.",
            "- **From the web:** opens the sites on your list. In the Automatic profile, the app first asks whether you want to import lyrics or chords, and opens that kind of site.",
            "- **Scan QR:** reads a setlist's QR with the camera.",
            "- **Type the 3 words:** receives what someone sent over the internet.",
        ],
        "figs": [Fig(("jornada_importar_menu_tablet", "jornada_importar_menu", "O menu Importar", "The Import menu"))],
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
                ["Se a cifra tiver tablatura, o app pergunta se ela vem junto: Com a tablatura ou Sem a tablatura. Dá para apagar depois no editor.",
                 "Música que só tem tablatura e letra, sem acordes, entra sem pergunta. No perfil de letra e cifra a tablatura vem junto. No perfil só letra entra só o que é cantado.",
                 "Sem a tablatura, saem também as legendas dela e as partes que eram só tablatura, como um solo."],
                ["If the chart has tablature, the app asks whether to bring it: With the tab or Without the tab. You can delete it later in the editor.",
                 "A song with only tablature and lyrics, no chords, comes in without the question. With the lyrics and chords profile the tab comes along. With lyrics only, just what is sung comes in.",
                 "Without the tab, its captions and the parts that were only tablature, like a solo, go too."]),
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
                [Fig(("tablet_4_editor_capo", "celular_4_editor_capo", "Tom e capotraste", "Key and capo"))]),
            sub("editor-acordes", "Acordes", "Chords",
                ["Os acordes vão entre colchetes, no meio da letra: [G]Hoje eu [D]vou. Se você colar uma cifra com os acordes na linha de cima, o app converte sozinho. Os atalhos acima da letra inserem os acordes da música, e Acorde insere um novo. Um colchete com asterisco, como [*2x], é uma anotação. Ela aparece em cima da letra, junto dos acordes, mas não é acorde. No meio de uma palavra, ela vai para o fim da palavra.",
                 "Eles ficam escritos como vieram do site (A7+, E7/9, E*), e o app mostra o diagrama certo para cada um.",
                 "Anotações na linha dos acordes ficam na mesma linha no palco: repetições como 2x ou (x4), barras |, rótulos como Intro: e um comentário depois de um traço, como Am G6 - Riff da intro. Um acorde seguido das casas de cada corda (B/F#  224442) aparece como os outros acordes, com as casas ao lado, e o desenho dele no violão e na guitarra usa essas casas. Ao transpor, as casas acompanham o acorde."],
                ["Chords go in brackets, inside the lyrics: [G]Here I [D]go. If you paste a chart with the chords on the line above, the app converts it for you. The shortcuts above the lyrics insert the song's chords, and Chord inserts a new one. A bracket with an asterisk, like [*2x], is a note. It shows above the lyrics with the chords, but it isn't a chord. Inside a word, it moves to the end of the word.",
                 "They stay written the way the site wrote them (A7+, E7/9, E*), and the app shows the right diagram for each.",
                 "Notes on the chords line stay on that line on stage: repeats like 2x or (x4), bars |, labels like Intro: and a comment after a dash, like Am G6 - Intro riff. A chord followed by the fret of each string (B/F#  224442) shows like the other chords, with the frets beside it, and its acoustic and electric guitar diagram uses those frets. When you transpose, the frets move with the chord."],
                [Fig(("tablet_4_editor_cifra", "celular_4_editor_cifra", "Letra com os acordes", "Lyrics with chords"))]),
            sub("editor-partes", "Partes da música", "Song parts",
                ["Uma linha só com um nome entre colchetes vira o título de uma parte: [Refrão], [Solo de violino]. Os atalhos no topo põem as partes mais usadas."],
                ["A line with just a name in brackets becomes a part title: [Chorus], [Violin solo]. The shortcuts at the top add the usual parts."],
                [Fig(("tablet_4_editor_secoes", "celular_4_editor_secoes", "Atalhos das partes", "Part shortcuts"))]),
            sub("editor-salvar", "Salvar e excluir", "Saving and deleting",
                ["A música precisa de um nome, e não dá para ter duas com o mesmo nome e artista. Saindo com algo sem salvar, o app pergunta antes. A lixeira, numa música já salva, exclui a música, que também sai dos setlists."],
                ["A song needs a name, and you can't have two with the same name and artist. If you leave with something unsaved, the app asks first. The trash can, on a saved song, deletes it, and it also leaves the setlists."]),
            sub("editor-marca", "Marca-texto", "Highlighter",
                ["Para destacar uma frase, selecione-a na letra e toque em Marca-texto. Ela fica pintada no editor e no palco. A cor fica em [Visual do palco](#ajustes-letra)."],
                ["To highlight a phrase, select it in the lyrics and tap Highlighter. It shows painted in the editor and on stage. The color is in [Stage look](#ajustes-letra)."]),
            sub("editor-midi", "MIDI da música", "Song MIDI",
                ["A música pode mandar mensagens MIDI quando aparece no palco e quando você sai dela, para trocar o preset da pedaleira, a cena da mesa ou o timbre do teclado. Toque no ícone do teclado no alto do editor. Ele fica aceso quando a música manda alguma coisa.",
                 "Em Ao entrar e Ao sair, adicione as mensagens: Program change (PC), Control change (CC), Nota (Note On/Off) ou Banco e programa (CC0/CC32 + PC), cada uma com o canal. Os números ficam dentro do padrão MIDI: programa de 1 a 128, controle, valor e nota de 0 a 127, banco de 0 a 16383. O MIDI é salvo com a música e vai no backup, mas não vai para a banda, porque cada músico tem o seu equipamento.",
                 "Para onde vai fica em [Saída MIDI](#ajustes-midi-saida). No modo essencial o MIDI não é enviado."],
                ["A song can send MIDI messages when it shows on stage and when you leave it, to change your pedalboard's preset, the mixer's scene or the keyboard's sound. Tap the keyboard icon at the top of the editor. It's lit when the song sends something.",
                 "Under On entering and On leaving, add the messages: Program change (PC), Control change (CC), Note (Note On/Off) or Bank and program (CC0/CC32 + PC), each with its channel. The numbers stay inside the MIDI standard: program 1 to 128, controller, value and note 0 to 127, bank 0 to 16383. The MIDI is saved with the song and goes in the backup, but not to the band, since each musician has their own gear.",
                 "Where it goes is in [MIDI out](#ajustes-midi-saida). In essential mode no MIDI is sent."],
                [Fig(("tablet_4_editor_midi", "celular_4_editor_midi", "MIDI da música", "Song MIDI"))]),
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
            sub("setlists-intervalo", "Intervalo", "Breaks",
                ["Show em mais de um bloco? Toque em Intervalo. Ele entra antes da última música: arraste para o lugar certo, como uma música. O × tira o intervalo. Um setlist pode ter vários, sempre entre duas músicas.",
                 "Com intervalo, cada bloco mostra o número dele e quantas músicas tem, e a numeração das músicas continua de um bloco para o outro. No palco, o intervalo aparece na hora dele: veja [Intervalo no palco](#palco-intervalo).",
                 "O intervalo fica neste aparelho: compartilhar o setlist manda só as músicas, e o editor no computador ainda não mostra os intervalos."],
                ["A show in more than one set? Tap Break. It goes in before the last song: drag it into place, like a song. The × removes the break. A setlist can have several, always between two songs.",
                 "With a break, each set shows its number and how many songs it has, and the song numbers keep counting from one set to the next. On stage the break shows up at its time: see [Break on stage](#palco-intervalo).",
                 "The break stays on this device: sharing the setlist sends only the songs, and the editor on the computer doesn't show breaks yet."],
                [Fig(("tablet_3_setlist_intervalo", "celular_3_setlist_intervalo", "Um intervalo entre dois blocos", "A break between two sets"))]),
            sub("setlists-musicas", "Adicionar músicas", "Adding songs",
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
        "figs": [Fig(("jornada_compartilhar_1_folha_tablet", "jornada_compartilhar_1_folha", "Compartilhar", "Share"))],
        "subs": [
            sub("compartilhar-qr", "QR sem internet", "QR without internet",
                ["Quem está do lado lê com a câmera, em Importar → Ler QR, mesmo sem internet. Vão só os títulos. Na primeira vez, o app pede a câmera."],
                ["Whoever is next to you reads it with the camera, in Import → Scan QR, even offline. Only the titles go. The first time, the app asks for the camera."]),
            sub("compartilhar-palavras", "3 palavras pela internet", "3 words over the internet",
                ["Enviar pela internet gera 3 palavras e um QR para quem vai receber. Dá para ditar as palavras, copiar ou mandar por mensagem. Elas valem por 12 horas. Quem recebe vai em Importar → Digitar as 3 palavras.",
                 "A mensagem leva também um link. Quem toca nele abre o Stagebook direto na tela de receber. Quem ainda não tem o app vai para a página de baixar, e o link fica copiado: ao abrir o app pela primeira vez, ele recebe sozinho. Link tocado com o palco ou a edição de uma música aberta espera você sair.",
                 "Com Levar as músicas ligado, as letras vão junto. Quem recebe só fica com as que não tinha; as dele não mudam."],
                ["Send online makes 3 words and a QR for whoever will receive. You can say the words, copy them or send them in a message. They last 12 hours. The other person goes to Import → Type the 3 words.",
                 "The message also has a link. Tapping it opens Stagebook straight on the receive screen. Someone without the app yet goes to the download page, and the link stays copied: when they open the app for the first time, it receives by itself. A link tapped with the stage or a song editor open waits until you leave.",
                 "With Bring the songs on, the lyrics go too. The other person only keeps the ones they didn't have; theirs don't change."],
                [Fig(("jornada_compartilhar_2_codigo_tablet", "jornada_compartilhar_2_codigo", "As 3 palavras", "The 3 words")),
                 Fig(("jornada_compartilhar_3_receber_tablet", "jornada_compartilhar_3_receber", "Recebendo pelas 3 palavras", "Receiving with the 3 words"))]),
            sub("compartilhar-conferir", "Conferir o que chegou", "Checking what arrived",
                ["Por QR, 3 palavras ou pela banda, a tela de conferir é a mesma. Ela mostra o que é novo, o que você já tem e o que falta. Antes de criar, o app avisa o que vai ficar de fora.",
                 "Se você tem outra versão de uma música, Ver o que muda mostra a diferença, e você escolhe entre Usar a recebida e Fica a minha. Se veio só a letra, dá para aceitar ou usar Buscar com cifra.",
                 "Se já existe um setlist com o mesmo nome, você escolhe entre Substituir e Criar outro."],
                ["By QR, 3 words or from the band, the review screen is the same. It shows what's new, what you already have and what's missing. Before creating, the app tells you what will be left out.",
                 "If you have another version of a song, See what changes shows the difference, and you pick Use theirs or Keep mine. If only the lyrics came, you can accept them or use Find with chords.",
                 "If a setlist with the same name already exists, you pick Replace or Create another."],
                [Fig(("jornada_qr_3_conferir_tablet", "jornada_qr_2_conferir", "Conferindo o que chegou", "Checking what arrived")),
                 Fig(("jornada_conferencia_criar_aviso_tablet", "jornada_conferencia_criar_aviso", "Antes de criar: o que vai faltar", "Before creating: what will be missing")),
                 Fig(("jornada_conferencia_versoes_tablet", "jornada_conferencia_versoes", "Outra versão da mesma música", "Another version of the same song"))]),
            sub("compartilhar-faltam", "Buscar as que faltam", "Getting the missing songs",
                ["- **Pedir à banda:** se alguém estiver na rede.",
                 "- **Pedir as que faltam pela internet:** aparece com o nome de quem mandou, se essa pessoa for da banda. As músicas chegam depois em Recebidos da banda.",
                 "- **Importar as que faltam:** sempre dá, pelos sites da sua lista. O app abre uma música de cada vez, e você pode pular ou parar."],
                ["- **Ask the band:** if someone is on the network.",
                 "- **Ask for the missing ones online:** shows with the sender's name, if they're in the band. The songs arrive later in Received from the band.",
                 "- **Import the missing ones:** always possible, from the sites on your list. The app opens one song at a time, and you can skip or stop."],
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
                 "- **Pedal e MIDI:** veja [Pedal e MIDI](#ajustes-pedal).",
                 "Num setlist, avançar na última página passa para a próxima música, e voltar na primeira volta para a anterior. Um botão de pedal também pode pular direto de música.",
                 "Prefere rolar a letra? Troque em [Visual do palco](#ajustes-pagina). Em Rolar, cada toque ou pedal leva até a primeira linha que não cabia, e ela acende por um instante. Como o texto sobe e desce, os gestos também são na vertical: tocar na metade de baixo avança, tocar na de cima volta, e arrastar o dedo para cima avança e para baixo volta. O zoom Automático também vale em Rolar: lá ele usa o maior tamanho em que a linha mais comprida da música cabe inteira na largura da tela, em pé ou deitado. As bolinhas do rodapé contam os passos da rolagem; quando não cabem todas, aparecem abreviadas como as páginas, com a última sempre à vista."],
                ["- **Tap:** the right side goes forward, the left side goes back.",
                 "- **Finger:** drag sideways.",
                 "- **Pedal and MIDI:** see [Pedal and MIDI](#ajustes-pedal).",
                 "In a setlist, going forward on the last page moves to the next song, and going back on the first page returns to the previous one. A pedal button can also jump straight between songs.",
                 "Rather scroll the lyrics? Change it in [Stage look](#ajustes-pagina). In Scroll, each tap or pedal press goes to the first line that didn't fit, and it lights up for a moment. Since the text moves up and down, the gestures are vertical too: tapping the lower half moves forward, tapping the upper half goes back, and dragging up moves forward while dragging down goes back. The Automatic zoom works in Scroll too: there it uses the biggest size at which the song's longest line fits whole on the screen's width, upright or on its side. The dots in the footer count the scroll steps; when they don't all fit, they're abbreviated like the pages, with the last one always in view."],
                [Fig(("tablet_6_palco_rolar", "celular_7_palco_rolar", "No modo Rolar", "In Scroll mode"))]),
            sub("palco-intervalo", "Intervalo no palco", "Break on stage",
                ["Quando o setlist tem um [intervalo](#setlists-intervalo) depois da música, o fim dela mostra Próxima: Intervalo. Avançar abre a tela do intervalo, com o tempo que ele já dura e a música que vem a seguir, com o tom.",
                 "Avançar de novo abre a próxima música. Voltar retorna à música de antes do intervalo. Abrir uma música direto pelo setlist não passa pelo intervalo."],
                ["When the setlist has a [break](#setlists-intervalo) after the song, its end shows Next: Break. Going forward opens the break screen, with how long it has lasted and the song that comes next, with its key.",
                 "Going forward again opens the next song. Going back returns to the song before the break. Opening a song straight from the setlist skips the break."],
                [Fig(("tablet_6_palco_intervalo", "celular_7_palco_intervalo", "O intervalo no palco", "The break on stage"))]),
            sub("palco-rolagem", "Rolagem automática", "Auto-scroll",
                ["No modo Rolar, o ▶ faz a letra subir sozinha, na velocidade que você escolher. Tocar na tela ou apertar o pedal pausa."],
                ["In Scroll mode, ▶ moves the lyrics up on their own, at the speed you pick. Tapping the screen or pressing the pedal pauses it."],
                [Fig(("tablet_6_palco_rolagem_auto", "celular_7_palco_rolagem_auto", "Rolagem automática", "Auto-scroll"))]),
            sub("palco-zoom", "Zoom da música", "A song's zoom",
                ["**Como funciona.** Cada música guarda um zoom para cada tamanho e posição de tela: em pé e deitado são dois valores separados. Na primeira vez que a música abre numa posição, o app calcula o zoom e guarda: o maior tamanho com o menor número de páginas, sem cortar um verso. Se diminuir um pouco a letra poupa uma página, ela diminui. Se uma seção atravessaria a virada de página, a letra diminui um pouco para mantê-la inteira. Cada seção começa um verso novo. Sem seção nem linha em branco, a letra conta como um verso só.",
                 "**Pinça e Automático.** Afaste ou aproxime dois dedos para escolher o tamanho. Vale só para a música aberta, nesta posição: ao virar a tela, a outra posição continua com o zoom dela. O aviso mostra a porcentagem e o botão Automático, que apaga a sua escolha e calcula de novo. Com a lista de acordes ligada, o zoom muda só a letra e as cifras.",
                 "**Colunas.** Em [Dividir em colunas](#ajustes-pagina), Automático decide para cada música e para cada zoom, calculado ou escolhido por você. Música que cabe numa página fica em 1 coluna. Deitado, usa 2 colunas sempre que dão menos páginas. Em pé, usa 2 quando quase todas as frases cabem na metade da tela. Com a pinça, a letra pode crescer além da metade da tela e passa para 1 coluna. Com 1 ou 2 colunas fixas, o zoom respeita a sua escolha.",
                 "**Quando calcula de novo.** O zoom calculado vale enquanto a letra e o visual do palco continuarem iguais. Se você muda a letra, o tamanho da letra em Ajustes ou as colunas, o app calcula outra vez. O zoom escolhido por você não muda.",
                 "**Exportar.** O arquivo .stagebook e o arquivo de músicas levam os zooms de todas as telas, calculados e escolhidos. Em outro aparelho, a música usa o zoom do tamanho e da posição daquela tela. Se não houver, calcula o dela."],
                ["**How it works.** Each song keeps one zoom for each screen size and position: upright and on its side are two separate values. The first time a song opens in a position, the app calculates the zoom and keeps it: the largest size with the fewest pages, without cutting a verse. If a slightly smaller letter saves a page, it gets smaller. If a section would cross a page turn, the lyrics get a little smaller to keep it whole. Each section starts a new verse. With no sections or blank lines, the lyrics count as a single verse.",
                 "**Pinch and Automatic.** Spread or pinch two fingers to choose the size. It applies only to the open song, in this position: when you turn the screen, the other position keeps its own zoom. The notice shows the percentage and the Automatic button, which clears your choice and calculates again. With the chord list on, the zoom changes only the lyrics and chords.",
                 "**Columns.** In [Split into columns](#ajustes-pagina), Automatic decides for each song and each zoom, calculated or chosen by you. A song that fits one page stays in 1 column. On its side, it uses 2 columns whenever they take fewer pages. Upright, it uses 2 when nearly all lines fit half the screen. With a pinch, the lyrics can grow past half the screen and go to 1 column. With 1 or 2 fixed columns, the zoom follows your choice.",
                 "**When it calculates again.** The calculated zoom holds while the lyrics and the look of the stage stay the same. If you change the lyrics, the lyrics size in Settings or the columns, the app calculates again. A zoom you chose doesn't change.",
                 "**Export.** The .stagebook file and the songs file carry the zooms of every screen, calculated and chosen. On another device, a song uses the zoom of that screen's size and position. If there's none, it calculates its own."],
                [Fig(("tablet_6_palco_zoom", "celular_7_palco_zoom", "O aviso do zoom", "The zoom notice"))]),
            sub("palco-tom", "Tom e capo", "Key and capo",
                ["O tom e o capo ficam no topo. Tocando neles você muda ali mesmo, e fica salvo na música."],
                ["Key and capo are at the top. Tap them to change them right there, and it's saved in the song."],
                [Fig(("tablet_6_palco_tom_capo", "celular_7_palco_tom_capo", "Mudando tom e capo", "Changing key and capo"))]),
            sub("palco-editar", "Corrigir e marcar", "Fixing and marking",
                ["O lápis no topo abre o editor nesta página. Segurando o dedo numa linha aparecem Editar esta linha e Marca-texto.",
                 "Com o Marca-texto ligado, passe o dedo nas palavras para destacar. Passar de novo apaga. Enquanto ele está ligado, o palco não vira página com o dedo; toque em Pronto para terminar."],
                ["The pencil at the top opens the editor on this page. Holding a line shows Edit this line and Highlighter.",
                 "With the Highlighter on, run your finger over the words to mark them. Again removes it. While it's on, the stage doesn't turn pages by touch; tap Done to finish."]),
            sub("palco-sair", "Sair do palco", "Leaving the stage",
                ["A seta no topo sai do palco. Num setlist, ou acompanhando a banda, ela pergunta antes, para um toque sem querer não tirar a letra da tela no show. Uma música aberta pela aba Músicas sai direto."],
                ["The arrow at the top leaves the stage. In a setlist, or following the band, it asks first, so a stray tap doesn't take the lyrics off the screen during the show. A song opened from the Songs tab leaves at once."]),
            sub("palco-acorde", "Diagrama do acorde", "Chord diagram",
                ["Tocando num acorde você vê o diagrama, para violão, guitarra, ukulele, cavaquinho ou teclado. As setas trocam a variação, Usar esta guarda a escolhida nesta música e Tocar faz o som. Tocar num acorde da [lista de acordes](#palco-lista) abre o mesmo diagrama.",
                 "Com capotraste, o diagrama mostra o capo na casa dele, com o desenho tocado a partir dali. No teclado, os acordes aparecem ajustados para você tocar junto com a banda."],
                ["Tap a chord to see its diagram for acoustic guitar, electric guitar, ukulele, cavaquinho or keyboard. The arrows change the variation, Use this one keeps it in this song and Play makes the sound. Tapping a chord in the [chord list](#palco-lista) opens the same diagram.",
                 "With a capo, the diagram shows it on its fret, with the shape played from there. On keyboard, the chords show adjusted so you can play along with the band."],
                [Fig(("tablet_6_acorde_violao", "celular_6_acorde_violao", "O diagrama do acorde", "The chord diagram")),
                 Fig(("tablet_6_acorde_capo", "celular_6_acorde_capo", "O capo no diagrama", "The capo on the diagram")),
                 Fig(("tablet_6_palco_teclado_capo", "celular_7_palco_teclado_capo", "Teclado numa cifra com capo", "Keyboard on a chart with a capo"))]),
            sub("palco-detalhes", "Detalhes do acorde", "Chord details",
                ["Detalhes mostra o grau do acorde no tom da música, o campo harmônico dele e onde mais ele aparece. Com capotraste, o campo harmônico mostra o que soa, não o desenho da cifra."],
                ["Details shows the chord's degree in the song's key, its harmonic field and where else it appears. With a capo, the harmonic field shows what sounds, not the chart's shape."],
                [Fig(("tablet_6_acorde_detalhes", "celular_6_acorde_detalhes", "Detalhes do acorde", "Chord details"))]),
            sub("palco-lista", "Lista de acordes", "Chord list",
                ["Os diagramas de todos os acordes da música podem ficar no início ou no fim do palco, crescendo para ocupar a largura da tela. Uma lista longa segue para a próxima coluna ou página, sempre no mesmo tamanho. Ligue em [Diagramas dos acordes](#ajustes-diagramas).",
                 "Segure um acorde para tirá-lo da lista. O × no canto esconde a lista inteira nessa música, e o botão com a seta traz tudo de volta."],
                ["The diagrams of all the song's chords can sit at the start or end of the stage, grown to fill the screen's width. A long list runs on to the next column or page, always at the same size. Turn it on in [Chord diagrams](#ajustes-diagramas).",
                 "Hold a chord to take it off the list. The × in the corner hides the whole list in that song, and the arrow button brings everything back."],
                [Fig(("tablet_6_palco_acordes_topo", "celular_7_palco_acordes_topo", "Lista de acordes no início", "Chord list at the start"))]),
            sub("palco-tab", "Tablatura", "Tablature",
                ["A tablatura aparece no meio da letra, como veio da cifra. Ela encolhe para caber na coluna e na página, e as cordas ficam sempre juntas. O zoom da letra não depende dela."],
                ["Tablature shows inside the lyrics, the way it came in the chart. It shrinks to fit the column and the page, and its strings always stay together. The lyrics' zoom doesn't depend on it."],
                [Fig(("tablet_6_palco_tab", "celular_7_palco_tab", "Tablatura", "Tablature"))]),
            sub("palco-metronomo", "Metrônomo", "Metronome",
                ["O metrônomo é o ícone embaixo, ao lado do relógio. Ele começa no BPM da música, ou em 100 se ela não tiver, e mudar o BPM nele grava na música. Ali mesmo você começa, para e muda a velocidade. Contagem, som e brilho ficam em [Metrônomo e player](#ajustes-metronomo).",
                 "Ao ligar o metrônomo com saída na interface USB, o app liga a interface sozinho, com ou sem faixa."],
                ["The metronome is the icon at the bottom, next to the clock. It starts at the song's BPM, or 100 if it has none, and changing the BPM there saves it in the song. Right there you start it, stop it and change the speed. Count-in, sound and glow are in [Metronome and player](#ajustes-metronomo).",
                 "Turning the metronome on with its output on the USB interface turns the interface on by itself, with or without a track."],
                [Fig(("tablet_6_palco_metronomo", "celular_7_palco_metronomo", "Metrônomo", "Metronome")),
                 Fig(("tablet_6_palco_metronomo_contagem", "celular_7_palco_metronomo_contagem", "A contagem antes de começar", "The count-in before it starts"))]),
            sub("palco-relogio", "Relógio e tempo de show", "Clock and show time",
                ["Tocar no relógio troca o que ele mostra: a hora, o tempo de show, com um cronômetro, e quanto falta, com uma ampulheta. O tempo de show começa a contar na primeira vez que aparece. Nos últimos 10 minutos, quanto falta fica vermelho, e depois do fim mostra o atraso com um sinal de menos.",
                 "Segurando o relógio, abre a Duração do show. Os botões − e + mudam meia hora de cada vez, a partir de 1 hora, e Começar a contar agora zera o tempo de show. Um show começado há mais de 12 horas conta como novo."],
                ["Tapping the clock changes what it shows: the time, the show time, with a stopwatch, and the time left, with an hourglass. The show time starts counting the first time it shows up. In the last 10 minutes the time left turns red, and past the end it shows how late you are with a minus sign.",
                 "Holding the clock opens Show length. The − and + buttons change it half an hour at a time, starting at 1 hour, and Start counting now resets the show time. A show started more than 12 hours ago counts as a new one."],
                [Fig(("tablet_6_palco_tempo_show", "celular_7_palco_tempo_show", "Quanto falta e a duração do show", "Time left and the show's length"))]),
        ],
    },
    {
        "id": "backing", "pt": "Backing track", "en": "Backing track",
        "text_pt": [
            "Cada música pode ter um backing track, em OGG com vários canais, MP3 ou WAV. Escolha nos Dados da música. No palco, o play só aparece nas músicas que têm um. Se o áudio de uma música não veio com ela, um alerta aparece no lugar do play. Toque nele para ver o nome do áudio e anexar de novo.",
        ],
        "text_en": [
            "Each song can have a backing track, as multichannel OGG, MP3 or WAV. Pick it in Song details. On stage, play only shows on songs that have one. If a song's audio didn't come with it, an alert shows where play would be. Tap it to see the audio's name and attach it again.",
        ],
        "figs": [Fig(("tablet_11_player_com_faixa", "celular_11_player_tocando", "Tocando com backing track", "Playing with a backing track")),
                 Fig(("tablet_11_player_usb_fora", "celular_11_player_usb_fora", "A interface saiu no meio da música", "The interface came out mid-song"))],
        "subs": [
            sub("backing-tocar", "Tocar e trocar", "Playing and changing",
                ["Quando a música acaba, ele para e volta ao começo. Segurando o play você troca ou tira a faixa e abre o mixer da música.",
                 "Trocar de música com a faixa tocando não para a faixa: ela continua até você tocar em parar ou ela acabar, e o botão de parar fica em qualquer música enquanto isso. Depois o play já toca a música que está na tela.",
                 "Para a faixa não parar com a tela apagada, o Android precisa liberar o Stagebook da economia de bateria. O app pede isso numa janela, uma vez em cada aparelho: toque em Continuar e depois em Permitir."],
                ["When the song ends it stops and goes back to the start. Hold play to change or remove the track and to open the song's mixer.",
                 "Changing songs while a track plays doesn't stop it: it goes on until you tap stop or it ends, and the stop button stays on any song meanwhile. Then play plays the song on screen.",
                 "For the track not to stop with the screen off, Android has to free Stagebook from battery saving. The app asks in a window, once on each device: tap Continue and then Allow."],
                [Fig(("jornada_faixa_4_menu_tablet", "jornada_faixa_4_menu", "Trocar ou tirar a faixa", "Change or remove the track"))]),
            sub("backing-saida", "Por onde o som sai", "Where the sound goes out",
                ["O som sai por uma interface de áudio USB, em USB Audio 1.0 ou 2.0, a 44,1 ou 48 kHz, ou pela saída do aparelho. Se a interface desconectar no meio do show, o app espera ela voltar em vez de jogar o som no alto-falante.",
                 "Sem nada tocando, o fluxo USB fecha depois de 10 segundos e abre de novo ao tocar ou ligar o metrônomo, com o aviso para aguardar. O player em si desliga sozinho depois de 10 minutos com o app fora da tela, ou se a interface não voltar em 10 minutos. Arrastar o aviso do player para fora das notificações também desliga. Tocando, o aviso volta.",
                 "Em alguns aparelhos, a interface conecta mas o som não sai ou sai picotado. Se o motor do Stagebook falhar várias vezes seguidas, o app passa para o áudio do Android sozinho e guarda essa escolha para a interface. Você também pode ligar Tocar pela interface através do Android, em [Metrônomo e player](#ajustes-player). Por esse caminho vale o volume de mídia do aparelho, guardado para cada saída. Se ele estiver em zero, a música toca sem som, e o palco avisa: suba nos botões de volume."],
                ["Sound goes out through a USB audio interface, with USB Audio 1.0 or 2.0, at 44.1 or 48 kHz, or the device's own output. If the interface disconnects mid-show, the app waits for it to come back instead of sending the sound to the speaker.",
                 "With nothing playing, the USB stream closes after 10 seconds and opens again on play or when the metronome starts, with a notice to wait. The player itself turns off by itself after 10 minutes with the app out of sight, or if the interface doesn't come back within 10 minutes. Swiping the player's notification away turns it off too. While playing, the notification comes back.",
                 "On some devices, the interface connects but there's no sound or it stutters. If Stagebook's engine fails several times in a row, the app moves to Android's audio by itself and saves that choice for the interface. You can also turn on Play on the interface through Android, in [Metronome and player](#ajustes-player). That way the device's media volume counts, kept for each output. If it's at zero, the song plays with no sound and the stage tells you: raise it with the volume buttons."]),
            sub("backing-primeira", "A primeira vez com a interface", "The first time with the interface",
                ["- Ligue a interface no cabo USB. O Android pergunta qual app abrir: toque em Stagebook e em Apenas uma vez.",
                 "- O Stagebook abre e pede o acesso ao áudio: permita. Se aparecer \"Autorize o USB na janela do Android\" no palco, é a janela do Android pedindo isso.",
                 "- Tire e ponha o cabo de novo, escolha Stagebook e toque em Sempre.",
                 "Pronto: ao religar o cabo, o som volta sozinho, sem perguntar nada. Se o cabo sair com a música tocando, o botão da faixa pisca o ícone de USB desconectado em vermelho. Se o cabo voltar em até 1 minuto, a música continua sozinha de onde parou, mesmo que a interface leve uns segundos a mais para ligar. Depois disso, a música para e volta ao início. Tocar no ícone durante a espera também para a música. O Stagebook não grava nada; a entrada da interface só acerta o relógio."],
                ["- Plug the interface into the USB cable. Android asks which app to open: tap Stagebook and Just once.",
                 "- Stagebook opens and asks for audio access: allow it. If the stage says \"Allow USB in Android's window\", that's Android's window asking for it.",
                 "- Unplug and plug the cable again, pick Stagebook and tap Always.",
                 "That's it: when the cable comes back, the sound returns by itself, without asking anything. If the cable comes out while a song plays, the track button blinks the USB unplugged icon in red. If the cable is back within 1 minute, the song carries on by itself where it was, even if the interface takes a few more seconds to start. After that, the song stops and goes back to the start. Tapping the icon while it waits stops the song too. Stagebook records nothing; the interface's input only keeps the clock."]),
            sub("backing-testado", "O que foi testado", "What was tested",
                ["Tudo passou na versão 0.1.900. Passar quer dizer: uma música inteira ouvida limpa, pausar e retomar, e tirar e pôr o cabo com a música tocando voltou sozinho. Interfaces: o mixer Behringer FLOW 8 (USB Audio 2.0, 4 saídas) e a placa Nanocore (USB Audio 1.0, 44,1 kHz).",
                 "- **TCL NXTPAPER 11 Plus (tablet, Android 14):** passou com as duas interfaces. Na versão 0.1.1011, a Behringer UMC404HD (USB Audio 2.0, 4 saídas, alimentada pelo tablet, ligada direto) também passou: música inteira limpa, parar e tocar de novo, e tirar e pôr o cabo com a música tocando.",
                 "- **Chuwi Hi10 XPro (tablet simples, Android 13):** passou com as duas. Com o mixer, o USB do tablet perde leituras na entrada da interface, então o app marca o ritmo pelo relógio do sistema: o som sai limpo, com um soluço curto e raro a cada 15 a 60 s.",
                 "- **Samsung Galaxy S26 Ultra (celular, Android 17):** passou com as duas. Com o mixer, a entrada da interface para de responder depois de cerca de um segundo, e o app percebe e marca o ritmo sozinho.",
                 "- **OnePlus 10 Pro (celular, Android 16):** passou com as duas, e com o mixer pelos dois caminhos (motor do Stagebook e áudio do Android).",
                 "A UMC404HD também passou no S26, no Chuwi e no OnePlus, nas versões 0.1.1038 a 0.1.1066. O mixer digital Behringer XR18 (USB Audio 2.0, 18 saídas) passou no S26. No Chuwi, a USB do tablet perde de 2 a 4% dos pacotes com ela e o som engasga, pelos dois caminhos. É limite do aparelho, e o app avisa, como em [Ao espetar a interface](#backing-espetar).",
                 "Não testado: a XR18 no TCL e no OnePlus, outras interfaces e um fone USB simples (suportados, mas fora dessa lista)."],
                ["Everything passed on version 0.1.900. A pass means: a whole song heard clean, pause and resume, and unplugging and plugging the cable with the music playing came back on its own. Interfaces: the Behringer FLOW 8 mixer (USB Audio 2.0, 4 outputs) and the Nanocore card (USB Audio 1.0, 44.1 kHz).",
                 "- **TCL NXTPAPER 11 Plus (tablet, Android 14):** passed with both interfaces. On version 0.1.1011, the Behringer UMC404HD (USB Audio 2.0, 4 outputs, powered by the tablet, plugged in directly) passed too: a whole song clean, stop and play again, and unplugging and plugging the cable with the music playing.",
                 "- **Chuwi Hi10 XPro (budget tablet, Android 13):** passed with both. With the mixer, the tablet's USB loses reads on the interface's input, so the app paces itself from the system clock: the sound is clean, with a rare short hiccup every 15 to 60 s.",
                 "- **Samsung Galaxy S26 Ultra (phone, Android 17):** passed with both. With the mixer, the interface's input stops answering after about a second, and the app notices and paces itself.",
                 "- **OnePlus 10 Pro (phone, Android 16):** passed with both, and with the mixer on both routes (Stagebook's engine and Android's audio).",
                 "The UMC404HD also passed on the S26, the Chuwi and the OnePlus, on versions 0.1.1038 to 0.1.1066. The Behringer XR18 digital mixer (USB Audio 2.0, 18 outputs) passed on the S26. On the Chuwi, the tablet's USB loses 2 to 4% of the packets with it and the sound stutters, on both ways. It's the device's limit, and the app warns, as in [When you plug in the interface](#backing-espetar).",
                 "Not tested: the XR18 on the TCL and the OnePlus, other interfaces and a plain USB headset (supported, but not on this list)."]),
            sub("backing-espetar", "Ao espetar a interface", "When you plug in the interface",
                ["Ao espetar a interface, o app já mostra o nome dela no mixer e no metrônomo, sem esperar o play. Uma interface já usada nesse aparelho volta com as saídas e o roteamento dela. Ao tirar o cabo, a tela passa para Aparelho; ao recolocar, volta para a interface.",
                 "Na primeira vez com uma interface nesse aparelho, o app prepara a interface assim que ela é espetada, com o aviso para aguardar, e lê quantas saídas ela tem. Para isso o player precisa estar ligado em Ajustes. Ao mesmo tempo, ele mede quantos pacotes a USB do aparelho entrega. Se perder mais de 0,3%, aparece **A USB pode falhar**, com quanto perdeu: o som pode engasgar ou falhar no meio da música, e usar assim é por sua conta e risco. Com interfaces de muitos canais, o aviso diz que são eles que pesam. O aviso aparece uma vez por interface em cada aparelho, e de novo só se a perda piorar."],
                ["When you plug in the interface, the app shows its name in the mixer and the metronome right away, without waiting for play. An interface already used on that device comes back with its outputs and routing. When you unplug the cable, the screen moves to Device; plug it back and it returns to the interface.",
                 "The first time with an interface on that device, the app prepares the interface as soon as it's plugged in, with the notice to wait, and reads how many outputs it has. The player has to be on in Settings for that. At the same time it measures how many packets the device's USB delivers. If it loses more than 0.3%, **USB may fail** shows up, with how much it lost: the sound may stutter or drop in the middle of a song, and using it that way is at your own risk. With interfaces with many channels, the warning says they're what weighs. The warning shows once per interface on each device, and again only if the loss gets worse."]),
            sub("backing-soluco", "Se ouvir pequenos soluços", "If you hear small hiccups",
                ["O app segue o relógio da própria interface quando o aparelho deixa. Em alguns tablets o relógio do USB é fraco: então o app marca o ritmo pelo relógio do sistema, e pode sobrar um soluço minúsculo e raro. Não é defeito da interface nem do app, e o caminho pelo áudio do Android tem o mesmo limite.",
                 "Em alguns celulares, ler a entrada da interface ao mesmo tempo que toca faz a saída parar. O app percebe na primeira vez, fica mudo por alguns segundos e volta a tocar sem a entrada, com o ritmo pelo relógio do aparelho. Daí em diante essa interface já abre assim nesse aparelho.",
                 "Pelo motor do Stagebook, o Android pede permissão para usar a interface USB na primeira vez: toque em OK.",
                 "Se a interface conecta e o som não sai ou sai picotado, ligue Tocar pela interface através do Android, em [Metrônomo e player](#ajustes-player)."],
                ["The app follows the interface's own clock when the device allows it. On some tablets the USB clock is weak: then the app paces itself from the system clock, and a rare tiny hiccup may remain. It's not a fault of the interface or of the app, and Android's own audio route has the same limit.",
                 "On some phones, reading the interface's input while playing makes the output stop. The app notices the first time, goes quiet for a few seconds and plays again without the input, paced by the device's clock. From then on that interface opens that way on that device.",
                 "On Stagebook's engine route, Android asks permission to use the USB interface the first time: tap OK.",
                 "If the interface connects and the sound doesn't come out or breaks up, turn on Play on the interface through Android, in [Metronome and player](#ajustes-player)."]),
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
                 "- **Saída:** em qual saída da interface ele toca. Vale qualquer uma das primeiras 32 saídas, como as 18 de uma mesa digital. Pela rota do Android, o sistema entrega no máximo 8 saídas. Uma saída acima disso fica em vermelho e não toca até você voltar ao caminho normal. A escolha fica guardada para cada interface.",
                 "- **Igualar o volume:** Não toca como foi gravado, Média deixa todas as músicas no mesmo volume percebido e Pico, com o mesmo pico.",
                 "- **Estéreo e mono:** junta dois canais em estéreo, ou toca no meio, igual nos dois lados.",
                 "- **Equalizador ligado:** faz o canal passar pelo equalizador."],
                ["The mixer has one channel per file channel, with a fader and a button to turn the channel off. Monitor live levels shows each one's signal. Tap a channel's name to rename it, and the icon next to it opens the other settings:",
                 "- **Output:** which interface output it plays on. Any of the first 32 outputs works, like the 18 of a digital desk. Through Android, the system gives at most 8 outputs. An output above that turns red and doesn't play until you go back to the usual way. The choice is kept for each interface.",
                 "- **Even out the volume:** No plays it as recorded, Average gives all songs the same perceived volume and Peak the same peak.",
                 "- **Stereo and mono:** joins two channels as stereo, or plays in the center, the same on both sides.",
                 "- **Equalizer on:** sends the channel through the equalizer."],
                [Fig(("jornada_mixer_3_editar_canal_tablet", "jornada_mixer_3_editar_canal", "Ajustes do canal", "Channel settings"))]),
            sub("mixer-global", "Mixer global ou da música", "Global or song mixer",
                ["Com Usar o mixer global ligado, o que você muda vale para todas as músicas. Desligado, vale só para aquela música. Voltar ao global apaga os ajustes próprios dela."],
                ["With Use the global mixer on, what you change applies to every song. Off, it applies only to that song. Going back to the global one erases the song's own settings."],
                [Fig(("jornada_eq_musica_2_inteiro", "jornada_eq_musica_2_inteiro_celular", "Mixer e EQ da música no palco", "The song's mixer and EQ on stage"))]),
            sub("mixer-eq", "Equalizador", "Equalizer",
                ["São 5 bandas numa curva só, aplicada em cada canal marcado com EQ, cada um separado. Um limitador segura os picos, e Zerar volta a curva ao plano. Ele liga sozinho quando você marca o primeiro canal e desliga quando desmarca o último. Sem nenhum canal marcado, dá para ajustar a curva, e a tela avisa que ela ainda não vale."],
                ["It has 5 bands in a single curve, applied to each channel marked EQ, each one separately. A limiter holds the peaks, and Reset flattens the curve. It turns on by itself when you mark the first channel and off when you unmark the last one. With no channel marked, you can still set the curve, and the screen tells you it has no effect yet."]),
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
                ["Com os aparelhos no mesmo Wi-Fi, que pode ser o ponto de acesso de um celular, os dois abrem [Banda e transmissão](#ajustes-banda). Um toca em Pedir para entrar e o outro em Autorizar. Depois disso eles se reconhecem até alguém remover. Na primeira vez, o app pergunta como a banda vai ver este aparelho.",
                 "Está longe? Qualquer um da banda toca em Convidar pela internet, no mesmo lugar, e manda a mensagem por onde quiser. Quem toca no link abre o Stagebook no convite, como \"Ana convidou você para a banda\", e toca em Entrar na banda. Quem ainda não tem o app baixa pela página do link, e o convite aparece ao abrir o app pela primeira vez. O convite só abre pelo link: digitar as 3 palavras não basta, e quem perder o link pede o link de novo a quem convidou. O convite vale por 12 horas e para a primeira pessoa que entrar. Para chamar mais alguém, mande um convite novo para cada um. Se outra pessoa já usou o link, o app avisa. Link tocado com o palco aberto não interrompe o show: o app guarda e abre quando você sair do palco. Na edição de uma música, ele espera você sair da edição, para nada do que foi digitado se perder."],
                ["With the devices on the same Wi-Fi, which can be a phone's hotspot, both open [Band and broadcast](#ajustes-banda). One taps Ask to join and the other taps Allow. After that they know each other until someone removes it. The first time, the app asks how the band will see this device.",
                 "Far away? Anyone in the band taps Invite over the internet, in the same place, and sends the message any way they like. Tapping the link opens Stagebook on the invite, like \"Ana invited you to their band\", and then Join the band. Someone without the app yet downloads it from the link's page, and the invite shows up when they open the app for the first time. The invite only opens from its link: typing the 3 words is not enough, and anyone who loses the link asks the person who invited them for it again. The invite lasts 12 hours and is for the first person who joins. To bring in more people, send each one a new invite. If someone else already used the link, the app says so. A link tapped while the stage is open never interrupts the show: the app keeps it and opens it when you leave the stage. While editing a song, it waits until you leave the editor, so nothing you typed is lost."],
                [Fig(("jornada_banda_6_convite_tablet", "jornada_banda_6_convite", "O convite para a banda", "The band invite")),
                 Fig(("tablet_6_palco_link_guardado", "celular_7_palco_link_guardado", "Link tocado no palco espera sair", "A link tapped on stage waits for you to leave"))]),
            sub("banda-mandar", "Mandar e receber", "Sending and receiving",
                ["Mandar um setlist para a banda é um toque, em Compartilhar. Quem está no mesmo Wi-Fi recebe na hora; quem não está, recebe pela internet quando abrir o app, em até 12 horas.",
                 "Quando chega algo, aparece um aviso rápido embaixo e um número no ícone da banda, ao lado do título da aba, até você conferir. Tudo fica em Recebidos da banda: Conferir abre, e a lixeira descarta. Se mais de uma pessoa mandou o mesmo setlist, abra cada versão e escolha uma."],
                ["Sending a setlist to the band takes one tap, in Share. Whoever is on the same Wi-Fi gets it right away; the others get it over the internet when they open the app, within 12 hours.",
                 "When something arrives you get a short notice at the bottom and a number on the band icon next to the tab's title until you check it. It all stays in Received from the band: Check opens it, and the trash can discards it. If more than one person sent the same setlist, open each version and pick one."],
                [Fig(("jornada_banda_3_compartilhar_tablet", "jornada_banda_3_compartilhar", "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso_tablet", "jornada_banda_4_aviso", "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("tablet_2_recebidos_versoes", "celular_2_recebidos_versoes", "Versões do mesmo setlist", "Versions of the same setlist"))]),
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
                [Fig(("banda_conduzir_faixas", "banda_conduzir_faixas_celular", "Conduzindo a banda", "Leading the band")),
                 Fig(("tablet_6_palco_acompanhando", "celular_7_palco_acompanhando", "Acompanhando no palco", "Following on stage"))]),
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
                ["- **Tamanho da letra:** vale para todas as músicas. É a referência do zoom automático: a música fica maior ou menor para aproveitar a página. No palco, a pinça muda só a música aberta, e Páginas e Rolar guardam cada um o seu zoom.",
                 "- **Fonte da letra e Letra em negrito:** Padrão, Condensada, Serifada ou Monoespaçada.",
                 "- **Cor da letra, Cor do destaque e Cor do fundo:** o destaque é o do marca-texto.",
                 "Se uma cor ficar difícil de ler sobre o fundo, o app avisa logo abaixo."],
                ["- **Lyrics size:** applies to all songs. It's the reference for the automatic zoom: a song gets larger or smaller to make the most of the page. On stage, a pinch changes only the open song, and Pages and Scroll each keep their own zoom.",
                 "- **Lyrics font and Bold lyrics:** Default, Condensed, Serif or Monospace.",
                 "- **Lyrics color, Highlight color and Background color:** the highlight is the highlighter's.",
                 "If a color gets hard to read on the background, the app warns you right below."],
                [Fig(("tablet_5_ajustes_letra", "celular_5_ajustes_letra", "Letra", "Lyrics")),
                 Fig(("tablet_5_ajustes_cifras_ilegivel", "celular_5_ajustes_cifras_ilegivel", "O aviso de cor ilegível", "The hard-to-read color warning"))], level=4),
            sub("ajustes-acordes", "Acordes na letra", "Chords in the lyrics",
                ["- **No palco:** Letra e cifra mostra os acordes em cima da letra. Só letra mostra só a letra, sem acordes nem anotações, mesmo nas músicas com cifra. Automático mostra como Letra e cifra, e o importador pergunta se a música entra como letra ou cifra.",
                 "- **Tamanho dos acordes, Fonte dos acordes e Cor dos acordes:** aparecem com Letra e cifra e com Automático."],
                ["- **On stage:** Lyrics and chords shows the chords above the lyrics. Lyrics only shows just the lyrics, with no chords or notes, even on songs with chords. Automatic shows them like Lyrics and chords, and the importer asks whether a song comes in as lyrics or chords.",
                 "- **Chord size, Chords font and Chord color:** these show with Lyrics and chords and with Automatic."],
                [Fig(("tablet_5_ajustes_cifras", "celular_5_ajustes_cifras", "Acordes na letra", "Chords in the lyrics"))], level=4),
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
                 "- **Velocidade da rolagem automática:** só em Rolar."],
                ["- **Moving on screen:** Turn pages shows one page at a time, in one or two columns. Scroll shows the lyrics in a single column.",
                 "- **Split into columns:** only in Turn pages. Automatic splits into 2 columns when the lyrics don't fit one page and the lines are short. You can also pick Never or Always. The pedal turns both columns together.",
                 "- **Auto-scroll speed:** only in Scroll."],
                [Fig(("tablet_5_ajustes_pagina", "celular_5_ajustes_pagina", "Página, no modo Virar página", "Page, in Turn pages mode")),
                 Fig(("tablet_5_ajustes_rolar", "celular_5_ajustes_rolar", "Página, no modo Rolar", "Page, in Scroll mode"))], level=4),
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
                [Fig(("tablet_5_ajustes_cifras_inteira", "celular_5_ajustes_cifras_inteira", "Diagramas dos acordes", "Chord diagrams"))], level=4),
            sub("ajustes-tela", "Tela", "Screen",
                ["**Manter a tela ligada com o app aberto:** o aparelho não apaga enquanto o Stagebook estiver na frente. Fora do app, apaga normalmente. O palco fica sempre aceso, com isso ligado ou não.",
                 "**Não perturbe no palco:** com o palco aberto, as notificações não aparecem e o telefone não toca. Contatos favoritos e quem ligar duas vezes seguidas ainda passam. Ao sair do palco, tudo volta como estava. Vem ligado.",
                 "Na primeira vez no palco, o app pergunta antes. Toque em Autorizar e ligue o Stagebook na lista do Android. Enquanto o Android não autorizar, o cartão avisa e mostra o botão Autorizar."],
                ["**Keep the screen on while the app is open:** the device doesn't sleep while Stagebook is in front. Outside the app it sleeps normally. The stage always stays lit, with this on or not.",
                 "**Do not disturb on stage:** while the stage is open, notifications don't pop up and the phone doesn't ring. Starred contacts and someone calling twice in a row still get through. When you leave the stage, everything goes back as it was. It comes on.",
                 "The first time on stage, the app asks first. Tap Allow and turn on Stagebook in Android's list. Until Android allows it, the card says so and shows the Allow button."],
                [Fig(("tablet_5_ajustes_tela", "celular_5_ajustes_tela", "Tela", "Screen"))], level=4),
            sub("ajustes-metronomo", "Metrônomo e player", "Metronome and player",
                ["Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado.",
                 "- **Mostrar no palco:** desligado, o ícone do metrônomo some do palco.",
                 "- **Contagem 1 … 2 … 1 2 3 4 antes:** ao começar, dois tempos lentos e depois um compasso, para a contagem não ser confundida com o começo da música. O palco mostra os números bem grandes, numa cor própria, e nas pausas nem o som nem o brilho aparecem.",
                 "- **Som:** o clique do metrônomo.",
                 "- **Volume:** o volume do clique, de 0 a 100%, de 10 em 10%, no aparelho e na saída USB. Só aparece com o Som ligado. Ele é guardado e vai no backup, com os outros ajustes.",
                 "- **Testar o metrônomo:** toca o clique a 100 BPM, sem contagem, com a saída e o volume escolhidos, para você ouvir sem ir ao palco. Com a saída na interface USB, o teste liga o player sozinho, como o palco faz, e o clique começa assim que a interface fica pronta. Toque em Parar o teste, ou saia da tela, para parar. Mudar a saída com ele tocando para o teste. Se mudar o volume, a mudança vale no próximo teste.",
                 "- **Saída do som:** com o player ligado, Aparelho é o fone, se houver um conectado. Sem fone, é o Bluetooth conectado, e sem os dois, o alto-falante. As saídas da interface USB deixam o baterista ouvir pela mesa. Para elas aparecerem, espete a interface: o app lê as saídas na hora. Pela rota do Android, alguns aparelhos mandam o clique para a USB junto com a música. Neles, Aparelho fica desabilitado.",
                 "- **Brilho na borda da tela e Cor do brilho:** a batida pisca na borda, sem som."],
                ["When essential mode turns one of them off, the item is named after the one left on.",
                 "- **Show on stage:** off, the metronome icon leaves the stage.",
                 "- **Count-in 1 … 2 … 1 2 3 4:** when it starts, two slow beats and then a bar, so the count isn't taken for the start of the song. The stage shows the numbers very big, in a color of their own, and on the pauses there's neither sound nor glow.",
                 "- **Sound:** the metronome's click.",
                 "- **Volume:** the click's volume, from 0 to 100% in steps of 10%, on the device and on the USB output. It only shows with Sound on. It's kept and goes in the backup, with the other settings.",
                 "- **Test the metronome:** plays the click at 100 BPM, with no count-in, on the chosen output and volume, so you can hear it without going to the stage. With the output on the USB interface, the test turns the player on by itself, as the stage does, and the click starts as soon as the interface is ready. Tap Stop the test, or leave the screen, to stop it. Changing the output while it plays stops the test. If you change the volume, the change counts on the next test.",
                 "- **Sound output:** with the player on, Device is the headphones, if any are connected. Without them, it's the connected Bluetooth, and without either, the speaker. The USB interface outputs let the drummer hear it through the desk. For them to show up, plug the interface in: the app reads its outputs right away. Through Android, some devices send the click to the USB with the song. On those, Device is disabled.",
                 "- **Glow on the screen edge and Glow color:** the beat flashes on the edge, with no sound."],
                [Fig(("tablet_5_ajustes_metronomo", "celular_5_ajustes_metronomo", "Metrônomo", "Metronome"))]),
            sub("ajustes-player", "Saída USB e botão da faixa", "USB output and track button",
                ["- **O que a saída USB precisa:** uma interface class compliant, com USB Audio Class 1.0 ou 2.0, a 44,1 ou 48 kHz, e um aparelho com USB OTG e o cabo certo.",
                 "- **Tocar pela interface através do Android:** use só se a interface conectar e o som não sair ou sair picotado. O som passa pelo áudio do Android, que pode engasgar.",
                 "- **Cada interface tem o seu caminho:** com a interface conectada, a chave vale só para ela, neste aparelho, e fica guardada. Se o motor do Stagebook falhar várias vezes seguidas com uma interface, o app passa para o áudio do Android sozinho e avisa. Se o Android não tocar nela, o app volta para o motor. Uma escolha sua na chave sempre vale mais. Sem interface conectada, a chave vale para as interfaces novas.",
                 "- **A interface carrega o aparelho:** o cartão avisa quando a interface USB manda energia para o aparelho. Assim, algumas interfaces falham no motor do Stagebook. Se o som falhar, ligue Tocar pela interface através do Android.",
                 "- **Parar volta ao início:** desligado, o botão da faixa pausa, e a música continua de onde parou.",
                 "O mixer e o equalizador ficam no [palco](#mixer)."],
                ["- **What the USB output needs:** a class compliant interface, with USB Audio Class 1.0 or 2.0, at 44.1 or 48 kHz, and a device with USB OTG and the right cable.",
                 "- **Play on the interface through Android:** use it only if the interface connects but there's no sound or it stutters. Sound goes through Android's audio, which can stutter too.",
                 "- **Each interface has its own way:** with the interface plugged in, the switch counts only for it, on this device, and is saved. If Stagebook's engine fails several times in a row with an interface, the app moves to Android's audio by itself and tells you. If Android doesn't play on it, the app goes back to the engine. A choice of yours on the switch always wins. With no interface plugged in, the switch counts for new interfaces.",
                 "- **The interface charges the device:** the card warns when the USB interface sends power to the device. Some interfaces fail on Stagebook's engine this way. If the sound fails, turn on Play on the interface through Android.",
                 "- **Stop goes back to the start:** off, the track button pauses, and the song goes on from where it stopped.",
                 "The mixer and equalizer are on the [stage](#mixer)."],
                [Fig(("jornada_faixa_1_ajustes_player_tablet", "jornada_faixa_1_ajustes_player", "Saída USB e botão da faixa", "USB output and track button")),
                 Fig(("jornada_faixa_1b_interface_tablet", "jornada_faixa_1b_interface", "A escolha guardada da interface e o aviso de energia", "The interface's saved choice and the power warning"))], level=4),
            sub("ajustes-pedal", "Pedal e MIDI", "Pedal and MIDI",
                ["O Stagebook aceita pedal Bluetooth, MIDI Bluetooth e MIDI USB. Qual é o meu? explica a diferença. Aqui também fica a [Saída MIDI](#ajustes-midi-saida) das músicas."],
                ["Stagebook takes Bluetooth, Bluetooth MIDI and USB MIDI pedals. Which one is mine? explains the difference. The songs' [MIDI out](#ajustes-midi-saida) is here too."]),
            sub("ajustes-pedal-conectar", "Conectar pedal ou aparelho MIDI", "Connect a pedal or MIDI device",
                ["- **Pedal Bluetooth comum:** vira a página como as setas de um teclado, como a maioria dos viradores de página. Ele é pareado nas configurações de Bluetooth do Android.",
                 "- **Aparelho MIDI Bluetooth:** um pedal ou controlador que manda mensagens MIDI, ou um aparelho que recebe o MIDI das músicas, como um rack ou um teclado. O app procura aqui mesmo: toque no aparelho achado para usar. Depois ele aparece em Saída MIDI. Esquecer tira ele do app.",
                 "- **Pedal MIDI pelo USB:** desligado, o Stagebook deixa o USB livre para uma interface de áudio.",
                 "Pedal desligado ou longe conecta sozinho quando ligar. Em versões mais antigas do Android, a localização precisa estar ligada para a procura achar um aparelho MIDI.",
                 "Com um pedal Bluetooth ligado, o teclado da tela pode sumir: o celular acha que o pedal é um teclado de verdade. Isso é do Android, não do app. No Gboard, ligue Configurações → Teclado físico → Mostrar teclado na tela. Em outros teclados, toque no ícone de teclado na barra de baixo e ligue Teclado na tela."],
                ["- **Regular Bluetooth pedal:** turns the page like a keyboard's arrows, as most page turners do. It's paired in Android's Bluetooth settings.",
                 "- **Bluetooth MIDI device:** a pedal or controller that sends MIDI messages, or a device that receives the songs' MIDI, like a rack or a keyboard. The app looks for it right here: tap the device it finds to use it. Then it shows up in MIDI out. Forget removes it from the app.",
                 "- **MIDI pedal over USB:** off, Stagebook leaves the USB free for an audio interface.",
                 "A pedal that is off or far away connects by itself when it's on. On older Android versions, location must be on for the search to find a MIDI device.",
                 "With a Bluetooth pedal connected, the on-screen keyboard may disappear: the phone takes the pedal for a real keyboard. That comes from Android, not the app. In Gboard, turn on Settings → Physical keyboard → Show on-screen keyboard. In other keyboards, tap the keyboard icon on the bottom bar and turn on the on-screen keyboard."],
                [Fig(("tablet_5_ajustes_pedal_midi", "celular_5_ajustes_pedal_midi", "Conectar pedal ou aparelho MIDI", "Connect a pedal or MIDI device"))], level=4),
            sub("ajustes-pedal-botoes", "Botões", "Buttons",
                ["Aperte um botão do pedal: ele aparece aqui e você escolhe o que ele faz. As opções são Avançar a página, Voltar a página, Próxima música, Música anterior, Play/pause do backing track e Nada.",
                 "Em Botões do pedal fica cada botão já apertado, com o que faz. O que não foi mudado aparece como padrão: setas para baixo e para a direita, Page Down e Espaço avançam; setas para cima e para a esquerda e Page Up voltam. Com mais de um pedal, os botões ficam separados por pedal, com o conectado em cima."],
                ["Press a pedal button: it shows here and you pick what it does. The options are Next page, Previous page, Next song, Previous song, Play/pause backing track and Nothing.",
                 "Under the pedal's buttons you see each button already pressed, with what it does. One you didn't change shows as default: down and right arrows, Page Down and Space go forward; up and left arrows and Page Up go back. With more than one pedal, the buttons are grouped by pedal, the connected one on top."],
                [Fig(("tablet_5_ajustes_pedal", "celular_5_ajustes_pedal", "Botões do pedal", "Pedal buttons"))], level=4),
            sub("ajustes-midi-saida", "Saída MIDI", "MIDI out",
                ["Escolha para onde vai o [MIDI das músicas](#editor-midi): um aparelho USB ou Bluetooth. O Bluetooth aparece na lista depois de conectado, em [Conectar pedal ou aparelho MIDI](#ajustes-pedal-conectar); se nenhum aparecer, a tela mostra onde conectar. Se o aparelho escolhido não estiver ligado, ele continua na lista como desconectado, e o MIDI volta a ir quando ele voltar.",
                 "Mesa ou placa de som na USB não aparece, porque abrir o MIDI dela tiraria a interface de áudio do player."],
                ["Pick where the [songs' MIDI](#editor-midi) goes: a USB or Bluetooth device. Bluetooth shows up in the list once it's connected, in [Connect a pedal or MIDI device](#ajustes-pedal-conectar); if none shows up, the screen says where to connect one. If the chosen device isn't on, it stays in the list as not connected, and MIDI goes there again when it comes back.",
                 "A mixer or sound card on USB doesn't show up, because opening its MIDI would take the audio interface from the player."],
                [Fig(("tablet_5_ajustes_midi_saida", "celular_5_ajustes_midi_saida", "Saída MIDI", "MIDI out"))], level=4),
            sub("ajustes-sites", "Sites de cifra", "Chord sites",
                ["Em Só letra, o item se chama Sites de letra. Em Automático ele se chama Sites de importação e mostra as duas listas, a de cifra e a de letra. O site marcado é onde o importador abre, e marcar outro leva ele para o topo. Adicionar site põe um endereço novo, e o × tira da lista."],
                ["In Lyrics only, the item is called Lyrics sites. In Automatic it is called Import sites and shows both lists, chords and lyrics. The marked site is where the importer opens, and marking another moves it to the top. Add site puts in a new address, and the × takes one off the list."],
                [Fig(("tablet_5_ajustes_importacao", "celular_5_ajustes_importacao", "A lista de sites", "The site list"))]),
            sub("ajustes-biblioteca", "Biblioteca e backup", "Library and backup",
                ["- **Sua biblioteca:** quantas músicas e setlists você tem.",
                 "- **Tags:** Separar nacionais e internacionais marca, pelo idioma da letra, as músicas que ainda não têm uma dessas tags. Dá para corrigir depois.",
                 "- **Achar refrão e versos:** nas músicas sem títulos de parte, o app acha o refrão, que é a estrofe que se repete, e os versos. A letra não muda, e os títulos que já existem ficam como estão.",
                 "- **Outras formas de compartilhar:** lembra que o setlist vai na hora por [Compartilhar](#compartilhar)."],
                ["- **Your library:** how many songs and setlists you have.",
                 "- **Find chorus and verses:** on songs with no part titles, the app finds the chorus, which is the verse that repeats, and the verses. The lyrics don't change, and existing titles stay as they are.",
                 "- **Other ways to share:** reminds you that a setlist goes right away through [Share](#compartilhar).",
                 "The automatic split of local and international tags only shows with the app in Portuguese."],
                [Fig(("tablet_5_ajustes_backup", "celular_5_ajustes_backup", "Biblioteca e backup", "Library and backup"))]),
            sub("ajustes-backup", "Salvar e abrir arquivo", "Save and open a file",
                ["**Salvar arquivo** grava um .stagebook com o que estiver marcado: Músicas e setlists, com o zoom de cada música, Backing tracks e Ajustes. Serve de backup, para trocar de aparelho ou para mandar a alguém. Para mandar só as letras, desmarque as backing tracks, que deixam o arquivo grande. Assim o arquivo não leva nem o nome dos áudios, e quem recebe não vê alerta de áudio faltando. O mesmo vale ao abrir um arquivo desmarcando as backing tracks.",
                 "**Abrir arquivo** mostra o que o arquivo tem para você marcar. Juntar com as minhas traz só o que você não tem. Substituir as minhas troca suas músicas e setlists pelos do arquivo. Os ajustes do arquivo substituem os daqui e, em outro aparelho, a banda precisa ser autorizada de novo. O app avisa quando for o caso.",
                 "Recebeu um .stagebook numa conversa, num download ou no gerenciador de arquivos? Toque nele e escolha o Stagebook. O app abre direto no Abrir arquivo. O arquivo passa pelas mesmas verificações do Abrir arquivo. Um arquivo que não é do Stagebook não abre, e o app avisa. Com o palco ou a edição de uma música aberta, o arquivo espera você sair.",
                 "A cópia que o próprio Android faz na sua conta leva só as músicas, os setlists e a língua do app. Backing tracks, ajustes e banda não vão. Para levar tudo a outro aparelho, use o Salvar arquivo. A música que chegou sem o áudio guarda o nome dele. Depois de abrir o arquivo, uma janela lista essas músicas com o nome de cada áudio. Na edição da música, o campo Backing track fica em cor de alerta, com esse nome e o pedido para anexar de novo. No palco, um alerta aparece no lugar do play."],
                ["**Save file** writes a .stagebook with what's checked: Songs and setlists, with each song's zoom, Backing tracks and Settings. It works as a backup, to change devices or to send to someone. To send only the lyrics, uncheck the backing tracks, which make the file large. The file then doesn't carry the audio names either, and whoever gets it sees no missing-audio alert. The same goes for opening a file with the backing tracks unchecked.",
                 "**Open file** shows what the file has for you to check. Join with mine brings only what you don't have. Replace mine swaps your songs and setlists for the file's. The file's settings replace the ones here and, on another device, the band has to be allowed again. The app tells you when that's the case.",
                 "Got a .stagebook in a chat, a download or the file manager? Tap it and choose Stagebook. The app opens right on Open file. The file goes through the same checks as Open file. A file that isn't Stagebook's doesn't open, and the app says so. With the stage or a song editor open, the file waits until you leave.",
                 "The copy Android itself keeps in your account carries only the songs, the setlists and the app's language. Backing tracks, settings and the band don't go. To take everything to another device, use Save file. A song that arrived without its audio keeps the audio's name. After the file opens, a window lists those songs with each audio's name. In the song editor, the Backing track field turns to the alert color, with that name and the request to attach it again. On stage, an alert shows where play would be."],
                [Fig(("tablet_5_backup_restaurar", "celular_5_backup_restaurar", "Abrindo um arquivo", "Opening a file"))], level=4),
            sub("ajustes-apagar", "Apagar tudo", "Erase everything",
                ["Deixa o app como novo: apaga músicas, setlists, backing tracks, ajustes e a banda deste aparelho, sem volta. A janela oferece Fazer um backup antes e pede para digitar um código de confirmação. Se o Android guarda uma cópia do app na sua conta, as músicas e os setlists podem voltar quando você instalar o app de novo."],
                ["Leaves the app like new: it erases songs, setlists, backing tracks, settings and the band from this device, for good. The window offers Back up first and asks you to type a confirmation code. If Android keeps a copy of the app in your account, the songs and setlists can come back when you install the app again."],
                [Fig(("tablet_5_apagar_tudo", "celular_5_apagar_tudo", "Apagar tudo", "Erase everything"))], level=4),
            sub("ajustes-banda", "Banda e transmissão", "Band and broadcast",
                ["- **Este aparelho:** o nome que a banda vê. Mudar o nome troca.",
                 "- **Na rede agora:** quem está no mesmo Wi-Fi e ainda não é da banda. Pedir para entrar fica ao lado de cada um.",
                 "- **Banda:** quem já foi autorizado. A bolinha acesa mostra quem está neste Wi-Fi agora, e o círculo vazio, quem está fora dele. Um aparelho em outra versão do app aparece marcado. A lixeira remove, e para voltar é preciso pedir para entrar de novo. Convidar pela internet manda um link para quem está longe. Veja [Entrar na banda](#banda-entrar).",
                 "- **Avisar a música tocando:** Enviar a música atual para OBS e outros avisa na rede qual música está no palco, com artista, tom e BPM. A letra nunca vai. Vem desligado. Veja o [guia do Now Playing](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.pt-BR.md).",
                 "O uso do dia a dia está em [Banda](#banda)."],
                ["- **This device:** the name the band sees. Change name changes it.",
                 "- **On the network now:** who is on the same Wi-Fi and isn't in the band yet. Ask to join sits next to each one.",
                 "- **Band:** who was already allowed. The lit dot shows who is on this Wi-Fi now, and the empty ring who is off it. A device on another app version shows up marked. The trash can removes them, and to come back they have to ask to join again. Invite over the internet sends a link to someone far away. See [Joining the band](#banda-entrar).",
                 "- **Tell what's playing:** Send the current song to OBS and others tells the network which song is on stage, with artist, key and BPM. The lyrics never go out. It comes off. See the [Now Playing guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.md).",
                 "Day-to-day use is in [Band](#banda)."],
                [Fig(("jornada_banda_1_ajustes_tablet", "jornada_banda_1_ajustes", "Banda e transmissão", "Band and broadcast"))]),
            sub("ajustes-computador", "Editar no computador", "Edit on the computer",
                ["Liga o editor web e lista os Computadores autorizados. Veja [Editor no computador](#editor-web)."],
                ["Turns the web editor on and lists the Authorized computers. See [Editor on the computer](#editor-web)."]),
            sub("ajustes-essencial", "Modo essencial", "Essential mode",
                ["Serve para quem quer a tela mais simples. Ligar o modo essencial deixa só letra, cifra, diagramas e pedal, e desliga o que roda por trás. Em O que fica ligado dá para desligar só uma parte:",
                 "- **Banda na rede:** achar a banda no Wi-Fi, enviar pela banda e Conduzir a banda.",
                 "- **Internet:** enviar e receber pela internet, versão nova do app e completar artista, ano e BPM.",
                 "- **Player de faixas:** backing tracks, interface de áudio USB e mixer.",
                 "- **Metrônomo e extras dos acordes:** som e detalhes do acorde, metrônomo, Now Playing e MIDI das músicas.",
                 "Desligada, a parte some da tela e para de rodar. Nos Ajustes, somem também os cartões e as opções que só existem para ela:",
                 "- **Banda na rede e Internet:** a seção Banda e transmissão (só o aviso Now Playing fica, junto dos extras), o link para autorizar a banda de novo em Biblioteca e backup, e o aviso de versão nova em Sobre.",
                 "- **Player de faixas:** o cartão do player em Metrônomo e player, o cartão Para tocar sem falhas em Avançado e o comando de play do backing track nos botões do pedal. Sem player, nada abre o fluxo da interface de áudio USB.",
                 "- **Metrônomo e extras dos acordes:** o cartão do metrônomo, o Now Playing, o MIDI da música em Pedal e MIDI, o som e os detalhes do acorde, e o ícone do metrônomo no palco.",
                 "O item do menu some quando todas as partes dele estão desligadas. O que guarda os seus dados continua aparecendo, como as backing tracks na lista do backup. Enquanto alguma parte estiver desligada, um aviso ao abrir o app lista o que está fora e leva de volta para cá. Não mostrar de novo tira o aviso. Uma folha no título de Músicas e Setlists lembra do modo: cheia com tudo desligado, apagada com só algumas partes, e tocar nela mostra o que está fora. Ela nunca aparece no palco. Veja [Quanto o app pesa](#desempenho)."],
                ["It's for a simpler screen. Turning on essential mode leaves only lyrics, chords, diagrams and the pedal, and turns off what runs in the background. In What stays on you can turn off just part of it:",
                 "- **Band on the network:** finding the band on Wi-Fi, sending through the band and Lead the band.",
                 "- **Internet:** sending and receiving over the internet, new app versions and filling in artist, year and BPM.",
                 "- **Track player:** backing tracks, USB audio interface and mixer.",
                 "- **Metronome and chord extras:** chord sound and details, metronome, Now Playing and song MIDI.",
                 "Once off, a part leaves the screen and stops running. In Settings, the cards and options that exist only for it go away too:",
                 "- **Band on the network and Internet:** the Band and broadcast section (only Now Playing stays, with the extras), the link to authorize the band again in Library and backup, and the new-version notice in About.",
                 "- **Track player:** the player card in Metronome and player, the For playing without failures card in Advanced and the backing track play command in the pedal buttons. With no player, nothing opens the USB audio interface stream.",
                 "- **Metronome and chord extras:** the metronome card, Now Playing, the song MIDI in Pedal and MIDI, the chord sound and details, and the metronome icon on stage.",
                 "A menu item goes away when all of its parts are off. What holds your data stays, like the backing tracks in the backup list. While any part is off, a notice when the app opens lists what's out and leads back here. Don't show again removes it. A leaf next to the title of Songs and Setlists is a reminder: solid with everything off, dimmed with only some parts off, and tapping it shows what's out. It never shows on stage. See [How light it is](#desempenho)."],
                [Fig(("tablet_5_ajustes_essencial", "celular_5_ajustes_essencial", "Modo essencial", "Essential mode")),
                 Fig(("tablet_1_aviso_essencial", "celular_1_aviso_essencial", "O aviso do modo essencial", "The essential mode notice"))]),
            sub("ajustes-avancado", "Avançado", "Advanced",
                ["São ajustes do próprio Android. Para mudá-los, o Stagebook precisa de uma permissão dada uma vez por um computador com o adb: a tela mostra o comando e Copiar o comando. Sem ela, os ajustes aparecem mas não mudam. Alguns só existem em certos aparelhos e só aparecem neles.",
                 "Eles valem para o aparelho inteiro, não só para o Stagebook. O app grava de novo sempre que abre, porque o Android às vezes volta atrás sozinho. Por isso, mude por estas chaves: o que você mudar nos ajustes do Android volta na próxima abertura. Desligar uma chave volta o valor que o aparelho tinha antes do Stagebook mudar. Desligada, a chave para de gravar. Os de “Para tocar sem falhas” só são gravados com o player ligado; com ele desligado, o Stagebook não mexe neles. A depuração é a exceção: nunca é gravada de novo.",
                 "### Para tocar sem falhas",
                 "Eles já derrubaram a interface de áudio ou pararam o app no palco. Vêm ligados; desligue um só se souber por quê.",
                 "- **Liberar da economia de bateria:** o Android não para o Stagebook com a tela apagada. Este vale sem o adb: é um toque em Permitir.",
                 "- **Carga inteligente desligada:** em alguns tablets, a proteção de carga derruba a interface USB por um instante. O custo é carregar até 100%.",
                 "- **Gerenciador de bateria desligado:** o Android para de sugerir restringir o Stagebook.",
                 "### Bateria do aparelho",
                 "- **Animações mais curtas:** as telas trocam mais rápido e gastam menos bateria.",
                 "- **Tela apaga depois de 10 minutos:** fora do palco.",
                 "- **Sem buscas em segundo plano:** Wi-Fi e Bluetooth param de procurar localização quando estão desligados.",
                 "- **Dados móveis só sem Wi-Fi:** os dados móveis não ficam ligados com o Wi-Fi conectado.",
                 "### Computador",
                 "- **Modo desenvolvedor e depuração por Wi-Fi:** para testar pelo computador com o adb. Vem desligado."],
                ["These are Android's own settings. To change them, Stagebook needs a permission given once from a computer with adb: the screen shows the command and Copy the command. Without it the settings show but don't change. Some only exist on certain devices and only show there.",
                 "They apply to the whole device, not just Stagebook. The app writes them again every time it opens, because Android sometimes goes back by itself. So change them with these switches: what you change in Android's settings comes back the next time it opens. Turning a switch off brings back the value the device had before Stagebook changed it. Once off, the switch stops writing. The ones under “For playing without failures” are only written with the player on; with it off, Stagebook leaves them alone. Debugging is the exception: it's never written again.",
                 "### For playing without failures",
                 "These have dropped the audio interface or stopped the app on stage. They come on; turn one off only if you know why.",
                 "- **Free from battery saving:** Android won't stop Stagebook with the screen off. This one works without adb: it's one tap on Allow.",
                 "- **Smart charging off:** on some tablets, charge protection drops the USB interface for a moment. The cost is charging up to 100%.",
                 "- **Battery manager off:** Android stops suggesting restricting Stagebook.",
                 "### Device battery",
                 "- **Shorter animations:** screens change faster and use less battery.",
                 "- **Screen off after 10 minutes:** outside the stage.",
                 "- **No searches in the background:** Wi-Fi and Bluetooth stop looking for location while they're off.",
                 "- **Mobile data only without Wi-Fi:** mobile data doesn't stay on with Wi-Fi connected.",
                 "### Computer",
                 "- **Developer mode and Wi-Fi debugging:** for testing from a computer with adb. It comes off."],
                [Fig(("tablet_5_ajustes_avancado", "celular_5_ajustes_avancado", "Avançado", "Advanced")),
                 Fig(("tablet_5_ajustes_avancado_permissao", "celular_5_ajustes_avancado_permissao", "A permissão pelo computador", "The permission from the computer"))]),
            sub("ajustes-sobre", "Sobre", "About",
                ["- **Ajuda:** Abrir a ajuda traz para esta página, que fica em [stagebook.app.br/ajuda](https://stagebook.app.br/ajuda). Precisa de internet.",
                 "- **Idioma:** Automático segue o aparelho, ou escolha português ou inglês. Para ajudar a traduzir, siga o [guia de tradução](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.pt-BR.md).",
                 "- **Atualizar o app:** a versão instalada e Procurar atualização. Veja [Atualizar](#atualizar).",
                 "- **Privacidade, Código disponível e Apoie o Stagebook:** como seus dados são tratados, o código do app e como apoiar.",
                 "No tablet, a versão também aparece embaixo do logo, no topo da barra lateral. Tocar no logo abre só o Sobre, e a seta volta para a tela onde você estava."],
                ["- **Help:** Open the help brings you to this page, at [stagebook.app.br/help](https://stagebook.app.br/help). It needs the internet.",
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
                [Fig(("tablet_5_ajustes_editor_web", "celular_5_ajustes_editor_web", "Ligando o editor no aparelho", "Turning on the editor on the device")),
                 Fig(("editor_web_1_pedido", None, "Pedindo para entrar", "Asking to come in"))]),
            sub("web-musicas", "Músicas", "Songs",
                ["- À esquerda ficam a busca, o filtro de tags e a lista. Clique numa música para abrir, ou em Nova música para começar uma.",
                 "- Dá para editar nome, artista, ano, BPM, tags e a letra com as cifras. Separe as tags com vírgula; as que você já usa aparecem como sugestão.",
                 "- Os botões − e + mudam o tom e o capotraste, como no app. A letra só muda de vez quando você salva.",
                 "- Para destacar uma frase, selecione-a e clique em Marca-texto.",
                 "- Em MIDI da música ficam as mensagens Ao entrar e Ao sair, como no [editor do app](#editor-midi).",
                 "- Com a música já salva, dá para enviar a backing track em OGG, MP3 ou WAV.",
                 "- Ctrl+S salva. Saindo com algo sem salvar, a página avisa antes."],
                ["- On the left are the search, the tag filter and the list. Click a song to open it, or New song to start one.",
                 "- You can edit name, artist, year, BPM, tags and the lyrics with chords. Separate tags with commas; the ones you already use show up as suggestions.",
                 "- The − and + buttons change the key and the capo, as in the app. The song only changes for good when you save.",
                 "- To highlight a phrase, select it and click Highlighter.",
                 "- Song MIDI holds the messages On entering and On leaving, as in the [app's editor](#editor-midi).",
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
            "Quando sai uma versão nova, o app avisa ao abrir e mostra o que mudou. Atualizar baixa a versão e instala. Na primeira vez o Android pede uma confirmação. Daí em diante, no Android 12 ou mais novo, o app se atualiza sem pedir de novo e reinicia para terminar. No Android 11 ou mais antigo o Android pede a confirmação a cada versão. Agora não deixa para o dia seguinte. Se só fechar a janela, o aviso volta na próxima vez que abrir o app. Se você pulou versões, aparece o que mudou em cada uma. A mesma lista fica em Ajustes → Sobre → Atualizar o app. É bom que toda a banda use a mesma versão; um aparelho em outra versão aparece marcado em Ajustes → Banda e transmissão.",
            "Enquanto o Android instala, o botão Instalar fica desligado. Ele volta quando o Android responde ou quando você volta para o app. Em aparelhos Samsung, o Bloqueador automático pode impedir a instalação, e ele volta a ligar sozinho depois de um tempo. Quando a versão nova termina de baixar, a janela mostra Abrir o Bloqueador automático: desligue e volte para instalar. O botão aparece também em Ajustes → Sobre → Atualizar o app.",
            "Suas músicas e ajustes ficam no aparelho. O que vai para a banda ou pela internet vai cifrado. Não tem conta, propaganda nem rastreio.",
            "Ao terminar de instalar, o Android fecha o app. Ele volta aberto na tela onde você estava. Onde o Android não deixa o app abrir sozinho, aparece a notificação Stagebook atualizado: toque nela e o app volta de onde parou.",
        ],
        "text_en": [
            "When a new version is out, the app tells you when it opens and shows what changed. Update downloads the version and installs it. The first time Android asks you to confirm. From then on, on Android 12 or newer, the app updates without asking again and restarts to finish. On Android 11 or older, Android asks to confirm every version. Not now leaves it for the next day. If you just close the window, the notice comes back the next time you open the app. If you skipped versions, you see what changed in each one. The same list is in Settings → About → Update the app. It's best if the whole band uses the same version; a device on another one shows up marked in Settings → Band and broadcast.",
            "While Android installs, the Install button stays off. It comes back when Android answers or when you come back to the app. On Samsung devices, Auto Blocker can stop the install, and it turns itself back on after a while. When the new version finishes downloading, the window shows Open Auto Blocker: turn it off and come back to install. The button also shows in Settings → About → Update the app.",
            "Your songs and settings stay on the device. What goes to the band or over the internet is encrypted. There's no account, no ads and no tracking.",
            "When the install finishes, Android closes the app. It comes back open on the screen you were on. Where Android doesn't let the app open by itself, the notification Stagebook updated shows up: tap it and the app goes back where you were.",
        ],
        "figs": [Fig(("tablet_8_versao_nova", "celular_8_versao_nova", "Versão nova e o que mudou", "New version and what changed")),
                 Fig(("tablet_8_bloqueador", "celular_8_bloqueador", "Num Samsung, depois de baixar", "On a Samsung, after downloading"))],
    },
    {
        "id": "desempenho", "pt": "Quanto o app pesa", "en": "How light it is",
        "text_pt": [
            "O Stagebook foi medido em quatro aparelhos: um tablet TCL NXTPAPER 11 Plus, um Galaxy S26 Ultra, um OnePlus 10 Pro e um tablet Chuwi Hi10 XPro, mais simples. Parado na lista ou no palco, ele usa menos de 1% de um núcleo do processador; virando página, até 15%; tocando um backing track, de 4% a 19%. Os quatro aparelhos têm 8 núcleos, então mesmo tocando ele usa menos de 3% do processador inteiro.",
            "A memória fica entre 30 e 255 MB, conforme o aparelho e a tela. O modo essencial quase não muda esses números: o ganho dele é deixar na tela só o que você usa.",
            "Tocando uma faixa pela interface USB, o app usou de 3,7% a 31,3% de um núcleo, conforme o aparelho e a mesa: no S26, 6,7% com o mixer USB Audio 2.0 e 3,7% com a placa USB Audio 1.0; no Chuwi, 29,3% e 16,9%; no OnePlus, 31,3% e 22,2%; no TCL, 5,1% e 3,0%. O TCL foi medido com 70% de bateria e o Android mantém seis dos oito núcleos em baixa frequência, então os números dele contam só os dois núcleos disponíveis, ponderados pela frequência. Sem ponderar, são 19,5% e 10,0%. Com o player ligado e a interface conectada, mas nada tocando, o app fecha o fluxo USB depois de 10 segundos, para não gastar bateria à toa. Ao tocar uma faixa ou ligar o metrônomo na interface, o fluxo abre de novo em uma fração de segundo. Toda vez que o fluxo abre, o app confere por cerca de 2 segundos se o relógio da interface está certo, e o som só começa depois disso; o aviso para aguardar aparece se passar de meio segundo. Essa conferência é feita uma vez, antes da música, para o som não mudar no meio dela. Antes de fechar, o fluxo parado usava cerca de 19% de um núcleo no OnePlus; com ele fechado, o app usa 0,4% no OnePlus, 0,4% no TCL, 0,1% no S26 e 0,1% no Chuwi.",
        ],
        "text_en": [
            "Stagebook was measured on four devices: a TCL NXTPAPER 11 Plus tablet, a Galaxy S26 Ultra, a OnePlus 10 Pro and a simpler Chuwi Hi10 XPro tablet. Idle on the list or the stage it uses less than 1% of one processor core; turning pages, up to 15%; playing a backing track, 4% to 19%. All four devices have 8 cores, so even playing it uses less than 3% of the whole processor.",
            "Memory stays between 30 and 255 MB, depending on the device and the screen. Essential mode barely changes these numbers: its gain is keeping on screen only what you use.",
            "Playing a track over the USB interface, the app used between 3.7% and 31.3% of one core, depending on the device and the interface: on the S26, 6.7% with the USB Audio 2.0 mixer and 3.7% with the USB Audio 1.0 card; on the Chuwi, 29.3% and 16.9%; on the OnePlus, 31.3% and 22.2%; on the TCL, 5.1% and 3.0%. The TCL was measured at 70% battery with Android keeping six of its eight cores at low frequency, so its numbers count only the two available cores, weighted by frequency. Unweighted they are 19.5% and 10.0%. With the player on and the interface plugged in but nothing playing, the app closes the USB stream after 10 seconds, so it doesn't use battery for nothing. Playing a track or turning on the metronome on the interface opens it again in a fraction of a second. Every time it opens, the app checks for about 2 seconds that the interface's clock is right, and the sound only starts after that; the notice to wait shows if it takes longer than half a second. The check happens once, before the music, so the sound doesn't change in the middle of it. Before it closed, the idle stream used about 19% of one core on the OnePlus; with it closed, the app uses 0.4% on the OnePlus, 0.4% on the TCL, 0.1% on the S26 and 0.1% on the Chuwi.",
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
                 "- **A interface não aparece no app, mesmo com o cabo ligado:** desligue e ligue a interface e depois tire e ponha o cabo, virando o conector. Algumas portas USB-C ficam ligadas sem trocar dados até a interface ser religada. Em alguns aparelhos o app percebe isso e avisa na tela do palco, com a frase **O cabo USB está ligado, mas a interface não responde**. Nos outros, o Android só mostra a interface depois que ela responde, e o app não tem como saber.",
                 "- **Saída USB não encontrada:** o som foi para a saída do aparelho. Confira o cabo e o adaptador.",
                 "- **Saída USB desconectada:** o app espera a interface voltar. Confira o cabo.",
                 "- **Permissão da saída USB negada:** tire e ponha o cabo e autorize, como em [A primeira vez com a interface](#backing-primeira).",
                 "- **Pequenos soluços no som:** veja [Se ouvir pequenos soluços](#backing-soluco).",
                 "- **A interface conecta mas não sai som, ou sai picotado:** ligue [Tocar pela interface através do Android](#ajustes-player).",
                 "- **O app passou para o Android sozinho, ou voltou para o motor:** um caminho falhou várias vezes com essa interface. A escolha fica guardada para ela. Para mudar, use a chave com a interface conectada.",
                 "- **A interface não funciona:** ela precisa ser class compliant, com USB Audio 1.0 ou 2.0, a 44,1 ou 48 kHz. Confira também o cabo e se o aparelho tem USB OTG.",
                 "- **Esse arquivo não serve:** use OGG, MP3 ou WAV.",
                 "- **O som cai de tempos em tempos:** confira [Para tocar sem falhas](#ajustes-avancado)."],
                ["- **The USB output didn't respond:** unplug the cable and plug it back in.",
                 "- **The interface doesn't show up in the app, even with the cable plugged in:** turn the interface off and on, then unplug the cable and plug it back in, flipping the connector. Some USB-C ports stay powered without exchanging data until the interface is reconnected. On some devices the app notices it and says so on the stage, with **A USB cable is plugged in, but the interface doesn't answer**. On the others, Android only lists the interface after it answers, and the app has no way to know.",
                 "- **No USB output found:** the sound went to the device's output. Check the cable and adapter.",
                 "- **USB output disconnected:** the app waits for the interface to come back. Check the cable.",
                 "- **USB output permission denied:** unplug and plug the cable and allow it, as in [The first time with the interface](#backing-primeira).",
                 "- **Small hiccups in the sound:** see [If you hear small hiccups](#backing-soluco).",
                 "- **The interface connects but there's no sound, or it stutters:** turn on [Play on the interface through Android](#ajustes-player).",
                 "- **The app moved to Android by itself, or back to the engine:** one way failed several times with that interface. The choice is saved for it. To change it, use the switch with the interface plugged in.",
                 "- **The interface doesn't work:** it has to be class compliant, with USB Audio 1.0 or 2.0, at 44.1 or 48 kHz. Also check the cable and that the device has USB OTG.",
                 "- **This file won't work:** use OGG, MP3 or WAV.",
                 "- **The sound drops now and then:** check [For playing without failures](#ajustes-avancado)."]),
            sub("problemas-pedal", "Pedal", "Pedal",
                ["- **Nenhum pedal MIDI achado:** confira se ele está ligado, perto e no modo MIDI Bluetooth, e se o Bluetooth do aparelho está ligado.",
                 "- **Este aparelho não tem suporte a MIDI:** use um pedal Bluetooth comum.",
                 "- **O teclado da tela sumiu:** veja [Conectar pedal ou aparelho MIDI](#ajustes-pedal-conectar)."],
                ["- **No MIDI pedal found:** check that it's on, nearby and in Bluetooth MIDI mode, and that the device's Bluetooth is on.",
                 "- **This device doesn't support MIDI:** use a regular Bluetooth pedal.",
                 "- **The on-screen keyboard disappeared:** see [Connect a pedal or MIDI device](#ajustes-pedal-conectar)."]),
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
                 "- **O download não terminou:** confira a internet e toque em Tentar de novo. Se o servidor falhar por um instante, o app já tenta de novo sozinho algumas vezes.",
                 "- **Erro ao instalar a atualização:** atualize de novo, pela janela ou em Ajustes → Sobre → Atualizar o app. Desta vez a instalação passa pela tela do Android."],
                ["- **On a Samsung, the update won't install:** turn off Auto Blocker, as in [Updating](#atualizar), and try again.",
                 "- **The download didn't finish:** check the internet and tap Try again. If the server fails for a moment, the app already tries again by itself a few times.",
                 "- **Error installing the update:** update again, from the window or in Settings → About → Update the app. This time the install goes through Android's screen."]),
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
              "tablet": "Tablet", "phone": "Celular", "other": "English", "other_href": "../help/", "lang": "pt-BR",
              "home": "Stagebook — página inicial", "repo": "Código do Stagebook no GitHub"},
    "en": {"title": "Stagebook help", "lead": "How to use each part of the app.", "contents": "On this page",
           "tablet": "Tablet", "phone": "Phone", "other": "Português", "other_href": "../ajuda/", "lang": "en",
           "home": "Stagebook — home page", "repo": "Stagebook source code on GitHub"},
}

CSS = """
:root { --bg:#121212; --surface:#1E1E1E; --raised:#262626; --fg:#ECECEC; --muted:#A0A7A4; --accent:#4AF6CD; --on-accent:#00382C;
        --line:#2E2E2E; color-scheme: dark; }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
header { padding: 32px max(16px, env(safe-area-inset-left, 0px)) 8px; max-width: 1180px; margin: 0 auto; display:flex; gap:20px; align-items:center; flex-wrap:wrap; }
header img { width:72px; height:72px; border-radius:18px; display:block; }
header a.home { border-radius:18px; } header a.home:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
h1 { margin:0; font-size: 30px; } .lead { margin:4px 0 0; color:var(--muted); }
.repo { margin-left:auto; color:var(--accent); } .repo + .lang { margin-left:0; }
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
<header><a class="home" href="../" aria-label="{ui['home']}"><img src="../logo.png" alt=""></a><div><h1>{ui['title']}</h1><p class="lead">{ui['lead']}</p></div>
<a class="repo" href="https://github.com/julianoAffonso/stagebook" aria-label="{ui['repo']}">GitHub</a><a class="lang" href="{ui['other_href']}">{ui['other']}</a></header>
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
