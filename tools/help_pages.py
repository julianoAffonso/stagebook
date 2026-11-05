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
            "A aba Músicas é a sua biblioteca. Dá para buscar pelo nome ou pelo artista e filtrar pelas tags que você criou. Com um filtro ligado, o total no topo mostra também quantas sobraram, a lista volta ao começo e o × ao lado limpa tudo.",
            "Tocando numa música, ela abre no palco. Segurando, você seleciona várias de uma vez para pôr num setlist, mudar as tags, compartilhar ou apagar.",
            "Ao lado do título podem aparecer alguns ícones: a folha quando o modo essencial está ligado, o grupo com um número quando a banda mandou algo, e o computador quando o editor web está ligado. Tocando no ícone você vai direto para o assunto.",
        ],
        "text_en": [
            "The Songs tab is your library. You can search by title or artist and filter by the tags you created. With a filter on, the total at the top also shows how many are left, the list goes back to the start and the × next to it clears everything.",
            "Tap a song and it opens on stage. Hold it to select several at once and add them to a setlist, change their tags, share or delete them.",
            "A few icons may show next to the title: the leaf when essential mode is on, the group with a number when the band sent something, and the computer when the web editor is on. Tapping an icon takes you straight to it.",
        ],
        "figs": [Fig(("tablet_1_musicas", "celular_1_musicas", "A lista de músicas", "The song list")),
                 Fig((None, "celular_1_musicas_filtradas", "Com um filtro ligado", "With a filter on")),
                 Fig(("tablet_1_musicas_tags_abertas", None, "Filtro por tags", "Filter by tags")),
                 Fig(("jornada_musicas_1_selecao", None, "Várias músicas selecionadas", "Several songs selected"))],
    },
    {
        "id": "importar", "pt": "Importar", "en": "Importing",
        "text_pt": [
            "Em Importar você busca a música nos sites da sua lista (Ajustes → Sites de cifra ou Sites de letra), abre a página e toca em Importar. O app só baixa o que você mandar. Tom, capotraste e BPM vêm do que a página diz, em qualquer site. Com capotraste, o tom guardado é o dos desenhos da cifra.",
            "O mesmo botão lê o QR de um setlist e recebe o que alguém mandou pelas 3 palavras.",
            "Se o texto não parecer letra ou cifra, o app não aceita.",
        ],
        "text_en": [
            "In Import you look the song up on the sites on your list (Settings → Chord sites or Lyrics sites), open the page and tap Import. The app only downloads what you ask for. Key, capo and BPM come from what the page says, on any site. With a capo, the saved key is the one of the chart's shapes.",
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
            "Anotações na linha dos acordes ficam na mesma linha no palco: repetições como 2x ou (x4), barras | e rótulos como Intro:. Um acorde seguido das casas de cada corda (B/F#  224442) aparece como os outros acordes, com as casas ao lado, e o desenho dele no violão e na guitarra usa essas casas. Ao transpor, as casas acompanham o acorde.",
            "Para destacar uma frase, selecione-a na letra e toque em Marca-texto. Ela fica pintada no editor e no palco. No palco também dá: segure o dedo numa linha, toque em Marca-texto e passe o dedo nas palavras (passar de novo apaga). Enquanto o marca-texto está ligado, o palco não vira página com o dedo; toque em Pronto para terminar. A cor do destaque fica em Ajustes → Visual do palco.",
        ],
        "text_en": [
            "In the editor the lyrics get the whole screen. Name, artist, year, BPM, key, capo, tags and backing track are under Song details at the top; tap to open them.",
            "Chords go in brackets, inside the lyrics: [G]Here I [D]go. If you paste a chart with the chords on the line above, the app converts it for you.",
            "The key's − and + transpose the chords a half step at a time. The capo's don't change the diagrams; they only tell you what key it will sound in.",
            "A line with just a name in brackets becomes a part title: [Chorus], [Violin solo]. The shortcuts at the top add the usual parts.",
            "Chords stay written the way the site wrote them (A7+, E7/9, E*), and the app shows the right diagram for each.",
            "Notes on the chords line stay on that line on stage: repeats like 2x or (x4), bars | and labels like Intro:. A chord followed by the fret of each string (B/F#  224442) shows like the other chords, with the frets beside it, and its acoustic and electric guitar diagram uses those frets. When you transpose, the frets move with the chord.",
            "To highlight a phrase, select it in the lyrics and tap Highlighter. It shows painted in the editor and on stage. On stage too: hold a line, tap Highlighter and run your finger over the words (again removes it). While the highlighter is on, the stage doesn't turn pages by touch; tap Done to finish. The highlight color is in Settings → Stage look.",
        ],
        "figs": [Fig(("tablet_4_editor_dados", "celular_4_editor", "Dados da música abertos", "Song details open")),
                 Fig(("tablet_4_editor_tags", "celular_4_editor_tags", "Tags da música", "The song's tags")),
                 Fig(("tablet_4_editor_capo", None, "Tom e capotraste", "Key and capo")),
                 Fig(("tablet_4_editor_secoes", None, "Atalhos das partes", "Part shortcuts"))],
    },
    {
        "id": "setlists", "pt": "Setlists", "en": "Setlists",
        "text_pt": [
            "Para criar um setlist, toque em Novo setlist. Sem nome, ele fica com a data e a hora; tocando no nome você troca. Para mudar a ordem, arraste as músicas. Acima da lista dá para buscar pelo nome e, em Ordem, ver os mais recentes primeiro ou de A a Z. Com uma busca, o total no topo mostra também quantos sobraram, e o × ao lado limpa.",
            "Dentro do setlist, toque em + Músicas e marque as músicas na ordem em que vão ser tocadas. A busca e as tags ajudam a achar. Compartilhar fica ao lado, e o play no topo abre o setlist no palco.",
            "Quando chega um setlist de alguém, o app mostra quais músicas você já tem e quais faltam. Antes de criar, ele avisa o que vai ficar de fora e oferece importar as que faltam.",
        ],
        "text_en": [
            "To create a setlist, tap New setlist. With no name it takes the date and time; tap the name to change it. Drag songs to change the order. Above the list you can search by name and, in Order, show the newest first or A to Z. With a search on, the total at the top also shows how many are left, and the × next to it clears it.",
            "Inside the setlist, tap + Songs and check the songs in the order they'll be played. Search and tags help you find them. Share sits next to it, and the play button at the top opens the setlist on stage.",
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
            "Chegou um setlist com música que você não tem? Por QR, 3 palavras ou pela banda, a tela de conferir é a mesma e mostra o que falta. Dá para pedir à banda, se alguém estiver na rede. Dá para pedir pela internet a quem mandou, se essa pessoa for da banda; as músicas chegam depois em Recebidos da banda. E dá sempre para importar dos sites.",
        ],
        "text_en": [
            "In a setlist, Share shows a QR that the other tablet reads with its camera, even offline. Or it makes 3 words to send over the internet; you can just say them. They last 12 hours.",
            "With Bring the songs on, the lyrics go too. The other person only keeps the ones they didn't have; theirs don't change.",
            "Got a setlist with songs you don't have? By QR, 3 words or from the band, the same review screen shows what's missing. You can ask the band, if someone is on the network. You can ask the sender online, if they're in the band; the songs arrive later in Received from the band. And you can always import them from the sites.",
        ],
        "figs": [Fig(("jornada_compartilhar_1_folha", None, "Compartilhar", "Share")),
                 Fig(("jornada_compartilhar_2_codigo", None, "As 3 palavras", "The 3 words")),
                 Fig(("jornada_qr_3_conferir_tablet", "jornada_qr_2_conferir", "Conferindo o que chegou", "Checking what arrived")),
                 Fig(("jornada_qr_5_faltam_tablet", "jornada_qr_4_faltam", "Como buscar as que faltam", "Ways to get the missing songs"))],
    },
    {
        "id": "palco", "pt": "Palco", "en": "Stage",
        "text_pt": [
            "No palco a letra aparece grande, com a cifra e as partes. Toque nas laterais ou use o pedal para virar a página. Se preferir rolar, troque em Ajustes → Visual do palco.",
            "O tom e o capo ficam no topo; tocando neles você muda ali mesmo. Enquanto o palco está aberto, nada aparece por cima: o que a banda mandar fica esperando.",
            "A seta no topo sai do palco. Num setlist, ou acompanhando a banda, ela pergunta antes, para um toque sem querer não tirar a letra da tela no show; uma música aberta pela aba Músicas sai direto.",
            "Tocando num acorde você vê o diagrama, para violão, guitarra, ukulele, cavaquinho ou teclado, com as variações e o som. Em Detalhes aparece o grau do acorde no tom da música. Com capotraste, o diagrama mostra o capo na casa dele, com o desenho tocado a partir dali, e o campo harmônico mostra o que soa, não o desenho da cifra.",
            "Os diagramas de todos os acordes da música podem ficar no início ou no fim do palco (Ajustes → Visual do palco → Lista de acordes), crescendo para ocupar a largura da tela. Segure um acorde para tirá-lo da lista; o × no canto esconde a lista inteira nessa música, e o botão com a seta traz de volta.",
            "O metrônomo é o ícone embaixo, ao lado do relógio. Ele começa no BPM da música, e mudar o BPM nele grava na música. Com o player ligado, o som dele pode sair numa saída da interface USB (Ajustes → Metrônomo e player → Saída do som), para o baterista ouvir pela mesa. Ao ligar o metrônomo, o app liga a interface sozinho, com ou sem faixa. No modo Rolar, a rolagem automática sobe a letra sozinha.",
        ],
        "text_en": [
            "On stage the lyrics show large, with the chords and parts. Tap the sides or use the pedal to turn the page. If you'd rather scroll, change it in Settings → Stage look.",
            "Key and capo are at the top; tap them to change them right there. While the stage is open nothing pops up over it: whatever the band sends waits.",
            "The arrow at the top leaves the stage. In a setlist, or following the band, it asks first, so a stray tap doesn't take the lyrics off the screen during the show; a song opened from the Songs tab leaves at once.",
            "Tap a chord to see its diagram for guitar, electric guitar, ukulele, cavaquinho or keyboard, with variations and sound. Details shows the chord's degree in the song's key. With a capo, the diagram shows it on its fret, with the shape played from there, and the harmonic field shows what sounds, not the chart's shape.",
            "The diagrams of all the song's chords can sit at the start or end of the stage (Settings → Stage look → Chord list), grown to fill the screen's width. Hold a chord to take it off the list; the × in the corner hides the whole list in that song, and the arrow button brings it back.",
            "The metronome is the icon at the bottom, next to the clock. It starts at the song's BPM, and changing the BPM there saves it in the song. With the player on, its sound can go out on a USB interface output (Settings → Metronome and player → Sound output), so the drummer hears it through the desk. Turning the metronome on turns the interface on by itself, with or without a track. In Scroll mode, auto-scroll moves the lyrics up on its own.",
        ],
        "figs": [Fig(("tablet_6_palco", "celular_7_palco", "O palco", "The stage")),
                 Fig(("tablet_6_palco_cifra", "celular_7_palco_cifra", "Com a cifra", "With chords")),
                 Fig(("tablet_6_acorde_violao", None, "O diagrama do acorde", "The chord diagram")),
                 Fig(("tablet_6_acorde_capo", None, "O capo no diagrama", "The capo on the diagram")),
                 Fig(("tablet_6_acorde_detalhes", "celular_6_acorde_detalhes", "Detalhes do acorde", "Chord details")),
                 Fig(("tablet_6_palco_metronomo", None, "Metrônomo", "Metronome")),
                 Fig(("tablet_6_palco_tab", "celular_7_palco_tab", "Tablatura", "Tablature"))],
    },
    {
        "id": "backing", "pt": "Backing track", "en": "Backing track",
        "text_pt": [
            "Cada música pode ter um backing track (OGG com vários canais, MP3 ou WAV). Escolha nos Dados da música. No palco, o play só aparece nas músicas que têm um.",
            "O som sai por uma interface de áudio USB (USB Audio 2.0, 48 kHz) ou pela saída do aparelho. Se a interface desconectar no meio do show, o app espera ela voltar em vez de jogar o som no alto-falante.",
            "Em alguns aparelhos, como celulares Samsung e tablets mais simples, a interface conecta mas o som não sai ou sai picotado. Nesse caso ligue Tocar pela interface através do Android, em Ajustes → Metrônomo e player: o som vai pelo áudio do Android, com todos os canais da interface.",
            "Na primeira vez que ligar a interface no cabo USB, o Android pergunta qual app abrir: toque em Stagebook e em Apenas uma vez. O Stagebook abre e pede o acesso ao áudio: permita. Tire e ponha o cabo de novo, escolha Stagebook e toque em Sempre. Pronto: ao religar o cabo, o som volta sozinho, sem perguntar nada. O Stagebook não grava nada; a entrada da interface só acerta o relógio.",
            "Quando a música acaba, ele para e volta ao começo. Segurando o play você troca ou tira a faixa e abre o mixer da música.",
            "Trocar de música com a faixa tocando não para a faixa: ela continua até você tocar em parar ou ela acabar, e o botão de parar fica em qualquer música enquanto isso. Depois o play já toca a música que está na tela.",
        ],
        "text_en": [
            "Each song can have a backing track (multichannel OGG, MP3 or WAV). Pick it in Song details. On stage, play only shows on songs that have one.",
            "Sound goes out through a USB audio interface (USB Audio 2.0, 48 kHz) or the device's own output. If the interface disconnects mid-show, the app waits for it to come back instead of sending the sound to the speaker.",
            "On some devices, like Samsung phones and simpler tablets, the interface connects but there's no sound or it stutters. Then turn on Play on the interface through Android, in Settings → Metronome and player: the sound goes through Android's audio, with all the interface's channels.",
            "The first time you plug the interface into the USB cable, Android asks which app to open: tap Stagebook and Just once. Stagebook opens and asks for audio access: allow it. Unplug and plug the cable again, pick Stagebook and tap Always. That's it: when the cable comes back, the sound returns by itself, without asking anything. Stagebook records nothing; the interface's input only keeps the clock.",
            "When the song ends it stops and goes back to the start. Hold play to change or remove the track and to open the song's mixer.",
            "Changing songs while a track plays doesn't stop it: it goes on until you tap stop or it ends, and the stop button stays on any song meanwhile. Then play plays the song on screen.",
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
            "Com os aparelhos no mesmo Wi-Fi (pode ser o ponto de acesso de um celular), os dois abrem Ajustes → Banda e transmissão; um toca em Pedir para entrar e o outro em Autorizar. Depois disso eles se reconhecem até alguém remover.",
            "Mandar um setlist para a banda é um toque. Quem está no mesmo Wi-Fi recebe na hora; quem não está, recebe pela internet quando abrir o app.",
            "Quando chega algo, aparece um aviso rápido embaixo e um número no ícone da banda, ao lado do título da aba, até você conferir. Em Conduzir a banda, todos acompanham a música que você está tocando.",
            "Conduzir a banda é o ícone de grupo no topo do setlist: ele abre o palco e os aparelhos da banda que estão com o app aberto no mesmo Wi-Fi entram sozinhos em Acompanhando, na mesma música, e seguem cada troca. Quem acompanha não precisa ter o setlist, só as músicas; se faltar uma, a faixa embaixo fica vermelha. No fim de cada música aparece a próxima do setlist de quem conduz. Quem abriu o app depois entra sozinho em alguns segundos; se não entrar, toque em Procurar a banda. Quem está editando uma música, escolhendo músicas, recebendo ou importando não é tirado da tela: a música abre quando a pessoa volta. Sair do palco não encerra: para isso, toque em Encerrar.",
        ],
        "text_en": [
            "With the devices on the same Wi-Fi (a phone's hotspot works too), both open Settings → Band and broadcast; one taps Ask to join and the other taps Approve. After that they know each other until someone removes it.",
            "Sending a setlist to the band takes one tap. Whoever is on the same Wi-Fi gets it right away; the others get it over the internet when they open the app.",
            "When something arrives you get a short notice at the bottom and a number on the band icon next to the tab's title until you check it. With Lead the band, everyone follows the song you're playing.",
            "Lead the band is the group icon at the top of a setlist: it opens the stage, and band devices with the app open on the same Wi-Fi switch to Following by themselves, on the same song, and follow every change. Followers don't need the setlist, only the songs; if one is missing, the bar at the bottom turns red. At the end of each song they see the leader's next one. Someone who opens the app later joins by themselves within seconds; if not, tap Look for the band. Someone editing a song, picking songs, receiving or importing isn't pulled off the screen: the song opens when they come back. Leaving the stage doesn't end it: tap End for that.",
        ],
        "figs": [Fig(("jornada_banda_1_ajustes", None, "Ajustes → Banda e transmissão", "Settings → Band and broadcast")),
                 Fig(("jornada_banda_3_compartilhar", None, "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso", None, "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("banda_conduzir_faixas", None, "Conduzindo a banda", "Leading the band"))],
    },
    {
        "id": "ajustes", "pt": "Ajustes", "en": "Settings",
        "text_pt": [
            "A lista de Ajustes segue esta ordem: Visual do palco, Metrônomo e player, Pedal, Sites de cifra (ou de letra), Biblioteca e backup, Banda e transmissão, Editar no computador, Modo essencial, Avançado e Sobre. No tablet deitado, a lista fica à esquerda e o assunto aberto à direita.",
            "O Modo essencial serve para quem quer a tela mais simples: deixa só letra, cifra, diagramas e pedal, e desliga o que roda por trás. Dá para desligar só uma parte.",
            "Em Visual do palco você escolhe Letra e cifra ou Só letra, e ficam as fontes, as cores, o tamanho dos títulos das partes e virar página ou rolar. Em Metrônomo e player ficam o metrônomo, a saída USB e o botão da faixa no palco. Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado. Em Pedal você liga um pedal Bluetooth ou MIDI e escolhe o que cada botão faz. Com mais de um pedal, os botões ficam separados por pedal, com o conectado em cima.",
            "Com um pedal Bluetooth ligado, o teclado da tela pode sumir: o celular acha que o pedal é um teclado de verdade. Isso é do Android, não do app. No Gboard, ligue Configurações → Teclado físico → Mostrar teclado na tela. Em outros teclados, toque no ícone de teclado na barra de baixo e ligue Teclado na tela.",
            "Em Biblioteca e backup você salva um arquivo .stagebook com o que escolher: músicas, setlists, backing tracks e ajustes. Serve de backup e para passar músicas para outra pessoa.",
            "Em Biblioteca e backup, Achar refrão e versos põe os títulos das partes nas músicas que vieram sem nenhum: a estrofe que se repete vira o refrão. A letra não muda.",
            "Em Avançado, Para tocar sem falhas reúne os ajustes do Android que já derrubaram a interface de áudio ou pararam o app no palco, como a economia de bateria. Eles vêm ligados; desligue um só se souber por quê.",
        ],
        "text_en": [
            "The Settings list goes in this order: Stage look, Metronome and player, Pedal, Chord sites (or Lyrics sites), Library and backup, Band and broadcast, Edit on the computer, Essential mode, Advanced and About. On a tablet lying down, the list sits on the left and the open topic on the right.",
            "Essential mode is for a simpler screen: it leaves only lyrics, chords, diagrams and the pedal, and turns off what runs in the background. You can turn off just part of it.",
            "Stage look is where you pick Lyrics and chords or Lyrics only, and has the fonts, colours, part title size and turning pages or scrolling. Metronome and player has the metronome, the USB output and the stage's track button. When essential mode turns one of them off, the item is named after the one left on. In Pedal you connect a Bluetooth or MIDI pedal and choose what each button does. With more than one pedal, the buttons are grouped by pedal, the connected one on top.",
            "With a Bluetooth pedal connected, the on-screen keyboard may disappear: the phone takes the pedal for a real keyboard. That comes from Android, not the app. In Gboard, turn on Settings → Physical keyboard → Show on-screen keyboard. In other keyboards, tap the keyboard icon on the bottom bar and turn on the on-screen keyboard.",
            "In Library and backup you save a .stagebook file with what you choose: songs, setlists, backing tracks and settings. It works as a backup and to pass songs to someone else.",
            "In Library and backup, Find chorus and verses adds part titles to songs that came without any: the verse that repeats becomes the chorus. The lyrics don't change.",
            "In Advanced, For playing without failures gathers the Android settings that have dropped the audio interface or stopped the app on stage, like battery saving. They come on; turn one off only if you know why.",
        ],
        "figs": [Fig(("tablet_5_ajustes", "celular_5_ajustes", "Os ajustes", "Settings")),
                 Fig(("tablet_5_ajustes_essencial", None, "Modo essencial", "Essential mode")),
                 Fig(("tablet_5_ajustes_backup", None, "Biblioteca e backup", "Library and backup")),
                 Fig((None, "celular_5_ajustes_pedal", "Pedal", "Pedal"))],
    },
    {
        "id": "editor-web", "pt": "Editor no computador", "en": "Editor on the computer",
        "text_pt": [
            "O editor abre o Stagebook no navegador do computador: teclado de verdade, tela grande e tudo salvo direto no aparelho. O palco atualiza na hora. Nada passa pela internet: o computador conversa só com o aparelho, no seu Wi-Fi.",
            "### Ligar e entrar",
            "- No aparelho, em Ajustes → Editar no computador, toque em Ligar o editor. Aparece um endereço como http://192.168.0.10:8090.",
            "- Abra esse endereço no navegador do computador, no mesmo Wi-Fi. Rede de convidados costuma bloquear a conversa entre aparelhos.",
            "- Toque em Pedir para entrar. O aparelho mostra um código de 4 números. Autorize só se for o mesmo da página.",
            "- O computador fica guardado em Computadores autorizados, e dali dá para tirá-lo a qualquer hora.",
            "### Músicas",
            "- À esquerda ficam a busca, o filtro de tags e a lista. Clique numa música para abrir, ou em Nova música para começar uma.",
            "- Dá para editar nome, artista, ano, BPM, tags e a letra com as cifras. As tags que você já usa aparecem como sugestão.",
            "- Os botões − e + mudam o tom e o capotraste, como no app. A letra só muda de vez quando você salva.",
            "- Para destacar uma frase, selecione-a e clique em Marca-texto.",
            "- Com a música já salva, dá para enviar a backing track em OGG, MP3 ou WAV.",
            "- Ctrl+S salva. Saindo com algo sem salvar, a página avisa antes.",
            "### Setlists",
            "- A busca acha o setlist pelo nome. O combo Ordem mostra os recentes primeiro ou de A a Z, e a escolha vale também para a aba Setlists do aparelho.",
            "- Dentro do setlist, a busca logo abaixo acrescenta músicas e as setas mudam a posição de cada uma.",
            "### Importar",
            "- Cole o endereço de uma página, ou a letra com as cifras em qualquer formato comum. A música abre no editor para você conferir antes de salvar.",
            "- Solte arquivos de texto ou ChordPro na área de arquivos para trazer várias de uma vez.",
            "- Digite as 3 palavras para receber o que alguém compartilhou, confira o que você já tem e crie o setlist.",
            "- Em Recebidos da banda ficam os envios, com o botão para sincronizar.",
            "- Abrir um arquivo `.stagebook` mostra o que o backup tem. Você escolhe as partes, e então junta com o que já existe ou substitui tudo.",
            "### Enquanto está ligado",
            "O ícone do computador aparece ao lado do título das abas. Tocando nele você vê o endereço e pode fechar o editor. Ele também desliga sozinho depois de 5 minutos sem uso ou quando você fecha o app.",
        ],
        "text_en": [
            "The editor opens Stagebook in the computer's browser: a real keyboard, a big screen and everything saved straight to the device. The stage updates right away. Nothing goes through the internet: the computer talks only to the device, on your Wi-Fi.",
            "### Turn it on and come in",
            "- On the device, in Settings → Edit on the computer, tap Turn on the editor. An address shows up, like http://192.168.0.10:8090.",
            "- Open that address in the computer's browser, on the same Wi-Fi. Guest networks usually block devices from talking to each other.",
            "- Tap Ask to come in. The device shows a 4-digit code. Approve only if it matches the page.",
            "- The computer is kept under Authorized computers, and you can remove it from there at any time.",
            "### Songs",
            "- On the left are the search, the tag filter and the list. Click a song to open it, or New song to start one.",
            "- You can edit name, artist, year, BPM, tags and the lyrics with chords. Tags you already use show up as suggestions.",
            "- The − and + buttons change the key and the capo, as in the app. The song only changes for good when you save.",
            "- To highlight a phrase, select it and click Highlighter.",
            "- Once the song is saved, you can send its backing track as OGG, MP3 or WAV.",
            "- Ctrl+S saves. If you leave with something unsaved, the page warns you first.",
            "### Setlists",
            "- The search finds a setlist by name. The Order dropdown shows the newest first or A to Z, and the choice also applies to the device's Setlists tab.",
            "- Inside a setlist, the search below adds songs and the arrows move each one.",
            "### Importing",
            "- Paste a page address, or lyrics with chords in any common format. The song opens in the editor so you can check it before saving.",
            "- Drop text or ChordPro files on the files area to bring in several at once.",
            "- Type the 3 words to receive what someone shared, check what you already have and create the setlist.",
            "- Received from the band holds what they sent, with the button to sync.",
            "- Opening a `.stagebook` file shows what the backup has. You pick the parts, then merge them with what's there or replace everything.",
            "### While it's on",
            "The computer icon shows next to the tabs' titles. Tap it to see the address and close the editor. It also turns off by itself after 5 minutes unused or when you close the app.",
        ],
        "figs": [Fig((None, "celular_5_ajustes_editor_web", "Ligando o editor no tablet", "Turning on the editor on the tablet")),
                 Fig(("editor_web_1_pedido", None, "Pedindo para entrar", "Asking to come in")),
                 Fig(("editor_web_2_musica", None, "Editando uma música", "Editing a song")),
                 Fig(("editor_web_3_setlist", None, "Setlists com a busca e o combo Ordem", "Setlists with the search and the Order dropdown")),
                 Fig(("editor_web_4_importar", None, "Importar no computador", "Importing on the computer"))],
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
        "id": "atualizar", "pt": "Atualizar", "en": "Updating",
        "text_pt": [
            "Quando sai uma versão nova, o app avisa ao abrir e mostra o que mudou. Se você pulou versões, aparece o que mudou em cada uma. A mesma lista fica em Ajustes → Sobre → Atualizar o app. É bom que toda a banda use a mesma versão; um aparelho em outra versão aparece marcado em Ajustes → Banda e transmissão.",
            "Em Ajustes → Sobre ficam o idioma, a versão e o botão Abrir a ajuda, que traz para esta página (precisa de internet). No tablet, a versão também aparece embaixo do logo, no topo da barra lateral; tocar no logo abre só o Sobre, e a seta volta para a tela onde você estava.",
            "Suas músicas e ajustes ficam no aparelho. O que vai para a banda ou pela internet vai cifrado. Não tem conta, propaganda nem rastreio.",
        ],
        "text_en": [
            "When a new version is out, the app tells you when it opens and shows what changed. If you skipped versions, you see what changed in each one. The same list is in Settings → About → Update the app. It's best if the whole band uses the same version; a device on another one shows up marked in Settings → Band and broadcast.",
            "Settings → About has the language, the version and the Open the help button, which brings you to this page (it needs the internet). On a tablet, the version also shows under the logo at the top of the side bar; tapping the logo opens just About, and the arrow takes you back to the screen you were on.",
            "Your songs and settings stay on the device. What goes to the band or over the internet is encrypted. There's no account, no ads and no tracking.",
        ],
        "figs": [Fig(("tablet_5_ajustes_atualizar", "celular_5_ajustes_lingua", "Ajustes → Sobre: ajuda, idioma e atualizar", "Settings → About: help, language and updating")),
                 Fig(("tablet_8_versao_nova", "celular_8_versao_nova", "Versão nova e o que mudou", "New version and what changed"))],
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
section a { color: var(--accent); }
h2 { font-size: 24px; margin: 0 0 8px; color: var(--accent); text-wrap: balance; }
h3 { font-size: 18px; margin: 20px 0 4px; }
section ul { margin: 4px 0 12px; padding-left: 20px; }
section li { margin-bottom: 4px; }
p { max-width: 70ch; } code { background:var(--raised); padding:1px 6px; border-radius:6px; font-size: 14px; }
/* two lying captures per row on a tablet; a standing one (phone) keeps its own width, no empty frame around it */
.figs { display:flex; flex-wrap:wrap; gap: 20px; margin: 16px 0 8px; align-items:flex-start; }
figure { margin:0; background:var(--surface); border-radius:20px; padding:12px; flex: 1 1 360px; min-width:0; max-width:100%; }
figure.tall { flex: 0 1 auto; }
figure img { width:100%; height:auto; border-radius:12px; display:block; }
figure.tall img { height: 440px; width:auto; max-width:100%; }
figcaption { color:var(--muted); font-size:14px; margin-top:8px; display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
.switch { margin-left:auto; display:flex; background:var(--raised); border-radius:999px; padding:2px; }
.switch button { border:0; background:none; color:var(--fg); font: 600 12px system-ui, sans-serif; padding:4px 10px; border-radius:999px; cursor:pointer; }
.switch button[aria-pressed=true] { background:var(--accent); color:var(--on-accent); }
/* standing tablet or phone: the contents become a compact strip of links instead of a tall list */
@media (max-width: 800px) { .wrap { grid-template-columns: 1fr; } nav.toc { position: static; padding: 12px; }
  nav.toc a { display:inline-block; background:var(--raised); margin: 0 4px 6px 0; padding: 4px 12px; border-radius: 999px; font-size: 14px; } }
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
    """**bold**, `code` and [text](https://link) (the only marks the texts use)."""
    out = html.escape(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"\[(.+?)\]\((https://[^)\s]+)\)", r'<a href="\2">\1</a>', out)
    return re.sub(r"`(.+?)`", r"<code>\1</code>", out)


def blocks(lines):
    """A text's lines: "### " is a subtitle, lines in a row starting with "- " are a list, the rest are paragraphs."""
    out, items = [], []
    for line in lines + [""]:
        if line.startswith("- "):
            items.append(f"<li>{md(line[2:])}</li>")
            continue
        if items:
            out.append(f"<ul>{''.join(items)}</ul>")
            items = []
        if line.startswith("### "):
            out.append(f"<h3>{md(line[4:])}</h3>")
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
            # lying captures go two per row (user, 03/11: one per row made the page very long); a standing one keeps
            # its own width
            tall = ' class="tall"' if cls else ""
            figs.append(f'<figure{tall}{attrs}><img src="{first}" alt="{cap}" loading="lazy"><figcaption>{cap}{switch}</figcaption></figure>')
        paras = blocks(s["text_" + key])
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
