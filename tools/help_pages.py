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
            "O menu é a sua biblioteca: toque na alça pequena, de três traços, na borda do palco (ou puxe-a para dentro) e ele abre por cima, ocupando quase toda a largura, com três abas embaixo, cada uma com o ícone e o nome numa linha só: Músicas, Setlists e Ajustes. O que chegou da banda aparece como um número depois do nome da aba. Ajustes mostra os grupos no próprio menu, e cada grupo abre em tela cheia, sem seta, com a mesma alça do palco para trazer o menu; o editor e o importador abertos pelo menu voltam para ele quando você sai. No tablet, o alfinete fixa o menu ao lado do palco e das páginas dos Ajustes, e tocar em outro grupo troca a página. Ele fica no fim da linha do título, junto dos ícones de modo essencial, banda e editor no computador e, com o menu fixo, de uma pessoa com um número quando alguém pede para entrar: toque nela para responder. Quando os ícones não cabem ao lado do título, sobem para uma linha própria acima dele. Arraste a alça para cima ou para baixo e ela fica onde você soltar. Para levá-la à outra borda, segure até o aparelho vibrar e arraste além do meio da tela. O menu abre do lado em que a alça está e, fixo no tablet, fica desse lado. Tocar fora do menu, ou no voltar do Android, fecha. A aba Músicas é a lista. Tocando numa música, ela abre no palco. O + embaixo à esquerda reúne o que traz música nova: Nova música começa uma em branco, e o resto traz de outros lugares (veja Importar). O botão de lista com marcas, embaixo à direita, liga as caixas de seleção: marque várias músicas para juntar a um setlist, mudar as tags, compartilhar ou apagar. Segurar uma música também começa a seleção. Uma música em PDF mostra uma folha de papel no fim da linha.",
            "Cada música mostra o nome e, embaixo, o artista. Na ponta da linha fica o tom e, embaixo dele, o BPM, o capo e uma marca de onda de áudio quando tem backing track. Só aparece o que a música tem. Um nome comprido fica numa linha só. Na primeira vez, sem nenhuma música, o menu abre sozinho com o logo do Stagebook e o + para criar ou trazer a primeira.",
        ],
        "text_en": [
            "The menu is your library: tap the small handle with three lines on the stage's edge (or pull it inward) and it opens over it, taking most of the width, with three tabs at the bottom, each with its icon and name on one line: Songs, Setlists and Settings. What the band sent shows as a number after the tab's name. Settings shows its groups in the menu itself, and each group opens full screen, with no arrow, with the same handle as the stage to bring the menu; the editor and the importer opened from the menu come back to it when you leave. On a tablet, the pin keeps the menu open beside the stage and the Settings pages, and tapping another group switches the page. It sits at the end of the title's line, with the essential mode, band and editor on the computer icons and, with the menu pinned, a person with a number when someone asks to come in: tap it to answer. When the icons don't fit beside the title, they move up to a line of their own above it. Drag the handle up or down and it stays where you let go. To take it to the other edge, hold it until the device buzzes and drag past the middle of the screen. The menu opens on the handle's side and, pinned on a tablet, stays on that side. Tapping outside the menu, or Android's back, closes it. The Songs tab is the list. Tap a song and it opens on stage. The + at the bottom left gathers what brings a new song in: New song starts a blank one, and the rest brings them from elsewhere (see Import). The checklist button at the bottom right turns the selection boxes on: choose several songs to add to a setlist, change their tags, share or delete. Holding a song also starts the selection. A song that is a PDF shows a sheet of paper at the end of its row.",
            "Each song shows its name and, under it, the artist. At the end of the row is the key and, under it, the BPM, the capo and an audio wave mark when it has a backing track. Only what the song has shows. A long name stays on one line. The first time, with no songs yet, the menu opens by itself with the Stagebook logo and the + to create or bring in the first one.",
        ],
        "figs": [Fig(("tablet_1_musicas", "celular_1_musicas", "A lista de músicas", "The song list")),
                 Fig(("tablet_1_primeiro_uso", "celular_1_primeiro_uso", "Na primeira vez, o menu abre sozinho", "The first time, the menu opens by itself"))],
        "subs": [
            sub("musicas-indice", "Índice de A a Z", "A to Z index",
                ["A lista fica em ordem alfabética. Com 20 músicas ou mais, uma letra separa cada grupo: A, B, C. Nomes que começam com número ou símbolo ficam em #, no começo. O acento não conta: Élan fica em E.",
                 "- **As letras na borda:** ao rolar a lista, as letras aparecem grandes na borda direita, e as das músicas que estão na tela ficam destacadas, numa faixa colorida que acompanha a rolagem. Elas somem uns três segundos depois que a lista para.",
                 "- **Ir para uma letra:** toque na letra, ou deslize o dedo pelas letras, e a lista vai para aquele grupo.",
                 "- **Com busca ou tags:** só aparecem as letras das músicas que sobraram. Com menos de 20 músicas, ou menos de 3 letras, a lista fica simples, sem letras."],
                ["The list is in alphabetical order. With 20 songs or more, a letter separates each group: A, B, C. Names that start with a number or a symbol go under #, at the start. Accents don't count: Élan goes under E.",
                 "- **The letters at the edge:** when you scroll the list, large letters show at the right edge, and those of the songs on screen are highlighted in a colored band that follows the scroll. They go away about three seconds after the list stops.",
                 "- **Going to a letter:** tap the letter, or slide a finger along the letters, and the list goes to that group.",
                 "- **With a search or tags:** only the letters of the songs left show. With fewer than 20 songs, or fewer than 3 letters, the list is plain, with no letters."],
                [Fig(("tablet_1_musicas_indice", "celular_1_musicas_indice", "As letras na lista e na borda", "The letters in the list and at the edge"))]),
            sub("musicas-filtrar", "Buscar e filtrar", "Search and filter",
                ["Dá para buscar pelo nome ou pelo artista. Ao lado da busca, o campo Mostrar e ordenar junta três escolhas num menu só, que fica aberto enquanto você escolhe; o ×, na primeira linha, fecha (tocar fora, até no palco, também fecha só o filtro, sem fechar o menu lateral):",
                 "- **Mostrar:** todas, só as tocadas nos últimos 30 dias, ou só as que nunca foram tocadas.",
                 "- **Tags:** marque uma ou mais das tags que você criou.",
                 "- **Ordem:** A a Z, Mais tocadas ou Menos tocadas. Com Nunca tocadas, a ordem fica em A a Z: Mais e Menos tocadas ficam apagadas, porque todas têm zero vezes.",
                 "Com um filtro ligado, o total no topo mostra também quantas sobraram, a lista volta ao começo e o × ao lado limpa a busca, as tags e o Mostrar. A ordem fica como você escolheu."],
                ["You can search by title or artist. Beside the search, the Show and sort field brings three choices together in one menu, which stays open while you choose; the ×, on its first line, closes it (a tap outside, even on the stage, also closes only the filter, not the side menu):",
                 "- **Show:** all, only the ones played in the last 30 days, or only the ones never played.",
                 "- **Tags:** tick one or more of the tags you created.",
                 "- **Order:** A to Z, Most played or Least played. With Never played, the order stays A to Z: Most and Least played are dimmed, since every song has zero plays.",
                 "With a filter on, the total at the top also shows how many are left, the list goes back to the start and the × next to it clears the search, the tags and Show. The order stays as you chose it."],
                [Fig(("tablet_1_musicas_filtradas", "celular_1_musicas_filtradas", "Com um filtro ligado", "With a filter on")),
                 Fig(("tablet_1_musicas_tags_abertas", "celular_1_musicas_tags_abertas", "Mostrar e ordenar", "Show and sort"))]),
            sub("musicas-tocadas", "Mais e menos tocadas", "Most and least played",
                ["Cada música conta as vezes que foi tocada: conta quando ela fica pelo menos 1 minuto no palco, aberta por um setlist ou sozinha, e só uma vez por dia (o dia vira às 5 da manhã, como no show). Ensaiar a mesma música dez vezes no dia vale uma.",
                 "- **Na ordem Mais tocadas ou Menos tocadas,** cada linha mostra quantas vezes e quando foi a última, como 18 vezes · última há 2 dias. As que nunca foram tocadas dizem Nunca tocada, e as letras de A a Z somem.",
                 "- **A contagem é da música:** apagar um setlist, ou usar Limpar tocadas, não muda nada. Ela vai no backup e volta com ele, mas fica só neste aparelho: não vai para a banda, nem pelo QR, nem no arquivo de músicas exportado.",
                 "A contagem começa na versão em que isso chegou: as músicas tocadas antes começam em zero."],
                ["Each song counts the times it was played: it counts when it stays at least 1 minute on stage, opened from a setlist or alone, and only once a day (the day turns at 5 in the morning, as in a show). Rehearsing the same song ten times in a day counts once.",
                 "- **In the Most played or Least played order,** each row shows how many times and when the last was, like 18 times · last 2 days ago. The ones never played say Never played, and the A to Z letters go away.",
                 "- **The count belongs to the song:** deleting a setlist, or using Clear played, changes nothing. It goes in the backup and comes back with it, but stays on this device only: it doesn't go to the band, by QR or in the exported songs file.",
                 "The count starts with the version that brought it: songs played before start at zero."],
                [Fig(("tablet_1_musicas_mais_tocadas", "celular_1_musicas_mais_tocadas", "Ordem Mais tocadas", "Most played order"))]),
            sub("musicas-selecionar", "Selecionar várias", "Select several",
                ["Segure uma música para selecionar. Depois toque nas outras, ou em Selecionar todas, para adicionar a um setlist, mudar as tags, compartilhar ou apagar de uma vez. Ao apagar uma só, a pergunta mostra o nome dela; com várias, mostra quantas."],
                ["Hold a song to select it. Then tap the others, or Select all, to add them to a setlist, change their tags, share or delete them at once. Deleting just one asks with its name; several ask with how many."],
                [Fig(("jornada_musicas_1_selecao_tablet", "jornada_musicas_1_selecao", "Várias músicas selecionadas", "Several songs selected")),
                 Fig(("jornada_musicas_2_apagar_tablet", "jornada_musicas_2_apagar", "Apagar uma música: a pergunta mostra o nome", "Deleting one song: the question shows its name"))]),
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
            "O + embaixo, na aba Músicas, reúne tudo o que traz música de fora:",
            "- **Importar PDFs:** escolhe um ou vários PDFs do aparelho; cada um vira uma música com o nome do arquivo (num arquivo chamado Artista - Título.pdf, o artista e o título já vêm separados), que você completa no editor. O app confere cada PDF e diz o motivo dos que não passam, sem parar os outros. Dá para abrir um PDF direto no Stagebook (num gerenciador de arquivos, num download ou numa conversa, em Abrir com ou Compartilhar) e ele entra do mesmo jeito.",
            "- **PDFs de sites:** abre o navegador nos sites de partituras livres em PDF (Mutopia Project e Musica Brasilis; aparece em qualquer perfil, e na pergunta do perfil automático (letra, cifra ou partitura em PDF); a lista se muda em Ajustes → Importar e backup, em Sites de partituras em PDF). Abra uma partitura e toque no PDF: o app pergunta se você quer importar, baixa, confere e cria a música. Nada entra sem o seu sim, e o que não é PDF fica de fora com um aviso. Sites que pedem conta ou pagamento ficaram de fora.",
            "- **Sincronizar com a banda:** procura novidades da banda agora.",
            "- **Recebidos da banda (n):** só aparece quando a banda mandou algo, com a quantidade.",
            "- **Da internet:** abre os sites da sua lista. No perfil Automático, o app pergunta antes o que você quer importar (Letra, Cifra ou Partitura em PDF) e abre o site daquele tipo.",
            "- **Ler QR:** lê o QR de um setlist com a câmera.",
            "- **Digitar as 3 palavras:** recebe o que alguém mandou pela internet. Some com a internet desligada nos Ajustes.",
        ],
        "text_en": [
            "The + at the bottom, on the Songs tab, gathers everything that brings songs in:",
            "- **Import PDFs:** picks one or several PDFs from the device; each becomes a song named after the file (in a file called Artist - Title.pdf, the artist and the title come already apart), for you to complete in the editor. The app checks each PDF and says why the ones that don't pass were left out, without stopping the others. You can also open a PDF straight in Stagebook (from a file manager, a download or a chat, with Open with or Share) and it comes in the same way.",
            "- **PDFs from sites:** opens the browser on free score sites in PDF (Mutopia Project and Musica Brasilis; it shows in every profile, and in the automatic profile's question (lyrics, chords or PDF score); the list is changed in Settings → Import and backup, under PDF score sites). Open a score and tap its PDF: the app asks whether to import it, downloads it, checks it and makes the song. Nothing comes in without your yes, and what isn't a PDF is left out with a notice. Sites that ask for an account or payment were left out.",
            "- **Sync with the band:** looks for what's new from the band right now.",
            "- **Received from the band (n):** only shows when the band sent something, with how many.",
            "- **From the web:** opens the sites on your list. In the Automatic profile, the app first asks what you want to import (Lyrics, Chords or PDF score) and opens that kind of site.",
            "- **Scan QR:** reads a setlist's QR with the camera.",
            "- **Type the 3 words:** receives what someone sent over the internet. It leaves when internet is off in Settings.",
        ],
        "figs": [Fig(("jornada_importar_menu_tablet", "jornada_importar_menu", "O + da aba Músicas", "The + of the Songs tab")),
                 Fig(("tablet_4_editor_importar_existente", "celular_4_editor_importar_existente", "Importar uma música que você já tem", "Importing a song you already have"))],
        "subs": [
            sub("importar-internet", "Da internet", "From the web",
                ["O importador abre o site marcado em [Sites de cifra](#ajustes-sites). Digite a música, o artista ou o endereço da página; uma busca digitada procura no Google. Abra a página da música e toque em Importar. Se o app não achar a letra, selecione-a com o dedo e toque de novo.",
                 "O app só baixa o que você mandar, e o que você importa fica só neste aparelho. Se o texto não parecer letra ou cifra, o app não aceita.",
                 "A música abre no editor para você conferir e salvar. Se você já tem uma música com o mesmo título e artista, o editor avisa no alto, e Salvar pergunta antes de trocar o texto dela pelo importado."],
                ["The importer opens the site marked in [Chord sites](#ajustes-sites). Type the song, the artist or the page address; a typed search goes to Google. Open the song's page and tap Import. If the app can't find the lyrics, select them with your finger and tap it again.",
                 "The app only downloads what you ask for, and what you import stays on this device. If the text doesn't look like lyrics or chords, the app won't take it.",
                 "The song opens in the editor for you to check and save. If you already have a song with the same title and artist, the editor says so at the top, and Save asks before replacing its text with the imported one."]),
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
            "O editor tem duas telas, uma de cada vez, em qualquer tamanho de aparelho: Dados e Letra e cifra (Letra, no perfil só letra). Troque no seletor logo abaixo do topo (num aparelho deitado e baixo, ele sobe para a barra do título, para sobrar altura à letra). Uma música que já existe abre na Letra e cifra, e uma nova abre em Dados, onde o nome é obrigatório. O topo mostra o nome da música com o artista embaixo, Salvar e a lixeira, nas duas telas.",
            "Quando você abre o editor pelo palco, passa de uma música para outra sem sair: as setas ‹ › com o contador, como 3 / 12, seguem a ordem de onde você veio. Pelo palco de um setlist, a ordem do setlist. Pelo palco aberto na lista de Músicas, a ordem da lista, com a busca e os filtros que estavam ligados. Nas pontas a seta fica apagada. Com texto sem salvar, o app pergunta: Salvar e continuar, Descartar ou Cancelar. No celular o contador fica na linha abaixo do nome.",
        ],
        "text_en": [
            "The editor has two screens, one at a time, on every device size: Data and Lyrics and chords (Lyrics, on the lyrics only profile). Switch with the selector just under the top (on a low device lying down, it moves up into the title bar, leaving the height to the lyrics). A song that already exists opens on Lyrics and chords, and a new one opens on Data, where the name is required. The top shows the song's name with the artist under it, Save and the trash can, on both screens.",
            "When you open the editor from the stage, you move from song to song without leaving: the ‹ › arrows with the counter, like 3 / 12, follow the order of where you came from. From a setlist's stage, the setlist's order. From the stage opened on the Songs list, the list's order, with the search and filters that were on. At the ends the arrow is dimmed. With unsaved text the app asks: Save and continue, Discard or Cancel. On a phone the counter sits on the line under the name.",
        ],
        "figs": [],
        "subs": [
            sub("editor-dados", "Dados da música", "Song details",
                ["Nome, artista, ano, BPM, tom, capotraste, tags, backing track e MIDI ficam na tela Dados. No tablet a coluna não estica: fica centralizada, com no máximo 560 dp. Deixou artista ou ano em branco? Ao salvar, o app completa pela internet.",
                 "Em Tags você marca as da música ou cria uma Nova tag."],
                ["Name, artist, year, BPM, key, capo, tags, backing track and MIDI are on the Data screen. On a tablet the column doesn't stretch: it sits in the middle, 560 dp wide at most. Left artist or year blank? When you save, the app fills them in over the internet.",
                 "In Tags you check the song's tags or create a New tag."],
                [Fig(("tablet_4_editor_dados", "celular_4_editor_dados", "Tela Dados", "Data screen"))]),
            sub("editor-pdf", "Música em PDF", "Song as a PDF",
                ["Uma música pode ser um PDF, como uma cifra que você já tem pronta. Na tela Dados, toque em PDF (ou em Anexar PDF) e escolha o arquivo. O app copia o PDF para dentro dele e confere: tem de ser um PDF de verdade, com no máximo 8 MB, até 300 páginas e sem senha. Se o arquivo não passa, o app diz o motivo e não guarda nada. Uma música sem nome ganha o nome do arquivo inteiro. O artista você preenche à mão: só o Importar PDFs separa Artista - Título.",
                 "Com PDF, a tela Letra e cifra mostra um cartão no lugar do campo de texto: a primeira página do PDF (uma prévia, como vai aparecer no palco), o nome do arquivo, quantas páginas e o tamanho, o interruptor Inverter as cores do PDF (branco do papel em preto, só desta música) e a frase Esta música é um PDF. Não dá para editar o PDF; no palco você pode marcá-lo com o marca-texto. Nome, artista, tom, andamento e tags continuam editáveis. Numa música em PDF o tom é só uma anotação: os botões de meio tom escrevem o tom (começando em C, se não havia) e não mexem no PDF. Recortar abre a primeira página com uma moldura: arraste as bordas para ficar só com a parte que você toca, e o palco mostra só ela, o maior possível. O recorte vale para todas as páginas ou só para a página que você escolheu (as setas trocam de página), e Sem recorte desfaz. O arquivo não muda, e o recorte fica só neste aparelho: a banda recebe o PDF inteiro. Trocar PDF põe outro arquivo no lugar (e limpa os recortes), e Remover PDF apaga o arquivo e traz a letra de volta.",
                 "Se a música já tem letra, o app pergunta antes de anexar: a letra fica guardada e volta quando você remove o PDF. O editor no computador faz o mesmo: o mesmo cartão, os mesmos avisos e as mesmas regras, para uma música já salva, com a prévia da primeira página, o interruptor de inverter as cores, o botão Recortar (a janela com a moldura, que se arrasta com o mouse, para todas as páginas ou só uma) e o tom anotado. Nas listas de músicas, no aparelho e no editor no computador, uma folha de papel marca a música em PDF.",
                 "**Banda no mesmo Wi-Fi:** o PDF vai junto com a música, e cada envio leva até 6 MB de PDFs no total: um PDF maior que isso, ou os que passarem da soma, vão sem o arquivo, e a tela de enviar avisa. Quem recebe confere o PDF do mesmo jeito, e só aceita de quem já está na banda; um PDF que não passa é descartado e a música chega sem ele, com o motivo na caixa de Recebidos da banda. Por causa dele a versão do protocolo da banda subiu para 4: todos os aparelhos da banda precisam atualizar o app.",
                 "**Quem está longe:** um membro da banda fora do Wi-Fi recebe pelo servidor da internet os PDFs pequenos (até 60 KB no total do envio) junto com a música, e a música sem o PDF para os que passam disso; a tela de enviar avisa quando há PDF no envio. O mesmo vale para o setlist enviado à caixa de cada um.",
                 "**Pela internet (3 palavras):** o servidor aceita mensagens de 4 KB e no máximo 60 por envio, e cada música e cada pedaço do PDF contam uma mensagem. Com um setlist de uma música cabem cerca de 133 KB de PDF (57 mensagens); com 15 músicas, cerca de 100 KB (43 mensagens). Quando não cabe, a tela de enviar diz quantas mensagens o PDF precisa e quantas o servidor aceita: as músicas vão sem o PDF, e para mandá-lo use a banda na mesma rede.",
                 "**No palco:** uma música em PDF mostra as páginas no lugar da letra. Para virar a página vale o que vale para a letra: tocar do lado direito ou esquerdo, deslizar ou usar o pedal. Os pontinhos do pé mostram em que página você está. Para ampliar, abra dois dedos sobre a página; ampliada, só dois dedos movem a página (um dedo continua sendo o do palco: toca para virar, e não faz a página andar sem querer), e feche os dois dedos para voltar ao tamanho inteiro. O zoom, o ponto e a página em que você deixou o PDF ficam guardados na música: ao virar a página, ou ao voltar outro dia, ela abre do mesmo jeito e na mesma página. Só a última página não é guardada: a música que você tocou até o fim abre da primeira no próximo show. Para voltar ao tamanho inteiro, feche os dois dedos. Para marcar, segure o dedo na página: aparecem ícones, o do Marca-texto e, se já houver marcas nela, também os de Desfazer (tira o último traço da página) e Limpar página (tira todos). Com o Marca-texto ligado, passe o dedo na página para pintar; a mesma barra das letras traz o Pronto para sair. Dois dedos continuam ampliando. As marcas ficam na música, no aparelho e no backup, na cor do marca-texto dos Ajustes; não vão pela banda. O limite da ampliação depende do tamanho da tela (até 4 vezes), para o aparelho não ficar sem memória. As cores invertidas viram o branco do papel em preto, para um palco escuro; ligue na edição da música (ou para todos, em Ajustes). Se o arquivo sumiu ou está danificado, o palco diz que não consegue abrir o PDF e pede para anexar de novo no editor.",
                 "**Backup e QR:** o PDF vai no backup junto com as backing tracks (a mesma opção); sem o arquivo a música aparece como PDF perdido, para você anexar de novo. O QR e o arquivo de letras não levam PDF."],
                ["A song can be a PDF, like a chart you already have. On the Data screen, tap PDF (or Attach PDF) and pick the file. The app copies the PDF into itself and checks it: it has to be a real PDF, 8 MB at most, up to 300 pages and without a password. If the file doesn't pass, the app says why and keeps nothing. A song with no name takes the whole file name. You fill in the artist by hand: only Import PDFs splits Artist - Title.",
                 "With a PDF, the Lyrics and chords screen shows a card in place of the text field: a preview of the PDF's first page (as it will show on stage), the file's name, how many pages and its size, the Invert the PDF's colors switch (the paper's white turned black, for this song only) and the sentence This song is a PDF. You can't edit the PDF; on stage you can mark it with the highlighter. Name, artist, key, tempo and tags stay editable. On a PDF song the key is only a note: the half-step buttons write the key (starting at C if there was none) and don't touch the PDF. Crop opens the first page with a frame: drag the edges to keep only the part you play from, and the stage shows just that, as large as possible. The crop applies to all pages or only to the page you pick (the arrows change page), and No crop undoes it. The file is not changed, and the crop stays on this device: the band gets the whole PDF. Replace PDF puts another file in its place (and clears the crops), and Remove PDF deletes the file and brings the lyrics back.",
                 "If the song already has lyrics, the app asks before attaching: the lyrics stay saved and come back when you remove the PDF. The editor on the computer does the same: the same card, the same notices and the same rules, for a song that is already saved, with the first page's preview, the invert colors switch, the Crop button (the window with the frame you drag with the mouse, for all pages or only one) and the key as a note. In the song lists, on the device and in the editor on the computer, a sheet of paper marks a PDF song.",
                 "**Band on the same Wi-Fi:** the PDF goes along with the song, and each send takes up to 6 MB of PDFs in all: a PDF bigger than that, or the ones past the total, go without the file, and the send screen says so. The receiving device checks the PDF the same way, and only accepts it from someone already in the band; a PDF that doesn't pass is discarded and the song arrives without it, with the reason in Received from the band. Because of it the band protocol version went up to 4: every band device needs to update the app.",
                 "**Someone far away:** a band member off the Wi-Fi gets the small PDFs (up to 60 KB in all in the send) through the internet server along with the song, and the song without its PDF for the ones past that; the send screen says so when the send has a PDF. The same goes for a setlist sent to each member's inbox.",
                 "**Over the internet (3 words):** the server takes 4 KB messages and 60 per send at most, and each song and each piece of the PDF count one message. With a one-song setlist about 133 KB of PDF fit (57 messages); with 15 songs, about 100 KB (43 messages). When it doesn't fit, the send screen says how many messages the PDF needs and how many the server takes: the songs go without the PDF, and to send it use the band on the same network.",
                 "**On stage:** a PDF song shows its pages in place of the lyrics. Turning the page works as it does for lyrics: tap the right or left side, swipe or use the pedal. The dots at the bottom show which page you are on. To enlarge, spread two fingers over the page; enlarged, only two fingers move the page (one finger stays the stage's: it taps to turn, and doesn't make the page move by accident), and pinching the two fingers together goes back to the whole page. The zoom, the spot and the page you left the PDF on are kept with the song: turning the page, or coming back another day, it opens the same way and on the same page. Only the last page is not kept: a song you played to the end opens from the first one at the next show. To go back to the whole page, pinch the two fingers together. To mark, hold your finger on the page: icons show up, the Highlighter's and, if the page already has marks, those of Undo (takes off the last stroke on the page) and Clear page (takes off all of them) too. With the Highlighter on, run your finger over the page to paint; the same bar as the lyrics' has Done to leave. Two fingers still enlarge. The marks stay in the song, on the device and in the backup, in the highlighter colour from Settings; they don't go through the band. How far it enlarges depends on the screen size (up to 4 times), so the device doesn't run out of memory. Inverted colors turn the paper's white into black, for a dark stage; turn them on in the song's editor (or for every song, in Settings). If the file is gone or damaged, the stage says it can't open the PDF and asks you to attach it again in the editor.",
                 "**Backup and QR:** the PDF goes in the backup with the backing tracks (the same option); without the file the song shows as PDF lost, for you to attach again. The QR and the lyrics file don't carry the PDF."],
                [Fig(("tablet_6_palco_pdf", "celular_7_palco_pdf", "Uma partitura em PDF no palco", "A PDF score on stage")),
                 Fig(("tablet_4_editor_pdf", "celular_4_editor_pdf", "Música em PDF no editor", "A song as a PDF in the editor")),
                 Fig(("editor_web_6_pdf", None, "Música em PDF no editor do computador", "A song as a PDF in the editor on the computer"))]),
            sub("editor-tom", "Tom e capotraste", "Key and capo",
                ["O − e + do tom transpõem a cifra meio tom de cada vez. O do capotraste não mexe nos diagramas: avisa em que tom vai soar. Se a música tem tablatura, as casas dela acompanham o tom e o capo."],
                ["The key's − and + transpose the chords a half step at a time. The capo's don't change the diagrams: they tell you what key it will sound in. If the song has tablature, its frets follow the key and the capo."],
                []),
            sub("editor-acordes", "Acordes", "Chords",
                ["Os acordes vão entre colchetes, no meio da letra: [G]Hoje eu [D]vou. Se você colar uma cifra com os acordes na linha de cima, o app converte sozinho. Na faixa acima da letra, logo depois do Marca-texto, ficam os acordes: o + insere um acorde novo e os outros inserem os acordes da música. As partes vêm depois deles. Um colchete com asterisco, como [*2x], é uma anotação. Ela aparece em cima da letra, junto dos acordes, mas não é acorde. No meio de uma palavra, ela vai para o fim da palavra.",
                 "Eles ficam escritos como vieram do site (A7+, E7/9, E*), e o app mostra o diagrama certo para cada um.",
                 "Anotações na linha dos acordes ficam na mesma linha no palco: repetições como 2x ou (x4), barras |, rótulos como Intro: e um comentário depois de um traço, como Am G6 - Riff da intro. Um acorde seguido das casas de cada corda (B/F#  224442) aparece como os outros acordes, com as casas ao lado, e o desenho dele no violão e na guitarra usa essas casas. Ao transpor, as casas acompanham o acorde."],
                ["Chords go in brackets, inside the lyrics: [G]Here I [D]go. If you paste a chart with the chords on the line above, the app converts it for you. In the strip above the lyrics, right after the Highlighter, come the chords: the + inserts a new chord and the others insert the song's chords. The parts come after them. A bracket with an asterisk, like [*2x], is a note. It shows above the lyrics with the chords, but it isn't a chord. Inside a word, it moves to the end of the word.",
                 "They stay written the way the site wrote them (A7+, E7/9, E*), and the app shows the right diagram for each.",
                 "Notes on the chords line stay on that line on stage: repeats like 2x or (x4), bars |, labels like Intro: and a comment after a dash, like Am G6 - Intro riff. A chord followed by the fret of each string (B/F#  224442) shows like the other chords, with the frets beside it, and its acoustic and electric guitar diagram uses those frets. When you transpose, the frets move with the chord."],
                [Fig(("tablet_4_editor_letra", "celular_4_editor_letra", "Tela Letra e cifra", "Lyrics and chords screen"))]),
            sub("editor-partes", "Partes da música", "Song parts",
                ["Uma linha só com um nome entre colchetes vira o título de uma parte: [Refrão], [Solo de violino]. Os atalhos da faixa de ferramentas (a última parte da faixa, numa linha só que rola de lado) põem as partes mais usadas, e o ícone de apresentação, Ver como fica no palco, mostra o resultado antes de salvar (o lápis volta ao texto)."],
                ["A line with just a name in brackets becomes a part title: [Chorus], [Violin solo]. The shortcuts in the tool strip (its last part, one line that scrolls sideways) add the usual parts, and the presentation icon, See it on stage, shows the result before you save (the pencil goes back to the text)."],
                []),
            sub("editor-salvar", "Salvar e excluir", "Saving and deleting",
                ["A música precisa de um nome, e não dá para ter duas com o mesmo nome e artista. Saindo com algo sem salvar, o app pergunta antes. A lixeira, no topo das duas telas e só numa música já salva, exclui a música, que também sai dos setlists."],
                ["A song needs a name, and you can't have two with the same name and artist. If you leave with something unsaved, the app asks first. The trash can, at the top of both screens and only on a saved song, deletes it, and it also leaves the setlists."]),
            sub("editor-marca", "Marca-texto", "Highlighter",
                ["Para destacar uma frase, selecione-a na letra e toque em Marca-texto. Ela fica pintada no editor e no palco. A cor fica em [Visual do palco](#ajustes-letra)."],
                ["To highlight a phrase, select it in the lyrics and tap Highlighter. It shows painted in the editor and on stage. The color is in [Stage look](#ajustes-letra)."]),
            sub("editor-midi", "MIDI da música", "Song MIDI",
                ["A música pode mandar mensagens MIDI quando aparece no palco e quando você sai dela, para trocar o preset da pedaleira, a cena da mesa ou o timbre do teclado. Toque em MIDI da música na tela Dados. O campo mostra Envia mensagens quando a música manda alguma coisa.",
                 "Em Ao entrar e Ao sair, adicione as mensagens: Program change (PC), Control change (CC), Nota (Note On/Off) ou Banco e programa (CC0/CC32 + PC), cada uma com o canal. Os números ficam dentro do padrão MIDI: programa de 1 a 128, controle, valor e nota de 0 a 127, banco de 0 a 16383. O MIDI é salvo com a música e vai no backup, mas não vai para a banda, porque cada músico tem o seu equipamento.",
                 "Para onde vai fica em [Saída MIDI](#ajustes-midi-saida). No modo essencial o MIDI não é enviado."],
                ["A song can send MIDI messages when it shows on stage and when you leave it, to change your pedalboard's preset, the mixer's scene or the keyboard's sound. Tap Song MIDI on the Data screen. The field says Sends messages when the song sends something.",
                 "Under On entering and On leaving, add the messages: Program change (PC), Control change (CC), Note (Note On/Off) or Bank and program (CC0/CC32 + PC), each with its channel. The numbers stay inside the MIDI standard: program 1 to 128, controller, value and note 0 to 127, bank 0 to 16383. The MIDI is saved with the song and goes in the backup, but not to the band, since each musician has their own gear.",
                 "Where it goes is in [MIDI out](#ajustes-midi-saida). In essential mode no MIDI is sent."],
                [Fig(("tablet_4_editor_midi", "celular_4_editor_midi", "MIDI da música", "Song MIDI"))]),
        ],
    },
    {
        "id": "setlists", "pt": "Setlists", "en": "Setlists",
        "text_pt": [
            "Tocar num setlist abre ele no menu, com as músicas na ordem; tocar numa música abre o palco nela. O ⋮ do cartão tem Tocar (abre o palco onde o show parou hoje, ou na primeira música), Compartilhar, Editar nome e data e Apagar o setlist. Para criar um setlist, toque no + embaixo e em Novo setlist (o mesmo + traz setlists e músicas de fora, por QR ou pelas 3 palavras): a janela pede o nome e a data do show, que já vem com o dia de hoje (toque nela para trocar ou no × para tirar). Sem nome, ele fica com a data e a hora. A lista mostra os setlists por mês, do show mais recente ao mais antigo, e os que não têm data ficam no fim, em Sem data. O botão de lista com marcas, embaixo à direita, liga as caixas para compartilhar ou apagar vários de uma vez. Ao compartilhar pela internet, se os PDFs das músicas não cabem, a folha diz o motivo e o envio vai sem eles. A data não vai junto quando você compartilha: quem recebe fica com o dia em que o setlist chegou. Acima da lista dá para buscar pelo nome. Com uma busca, o total no topo mostra também quantos sobraram, e o × ao lado limpa.",
        ],
        "text_en": [
            "Tapping a setlist opens it in the menu, with its songs in order; tapping a song opens the stage on it. The ⋮ on the card has Play (opens the stage where the show stopped today, or on the first song), Share, Edit name and date and Delete setlist. To create a setlist, tap the + at the bottom and New setlist (the same + brings setlists and songs in, by QR or by the 3 words): the window asks for the name and the show's date, which starts as today (tap it to change or the × to clear). With no name it takes the date and time. The list shows the setlists by month, from the most recent show to the oldest, and the ones with no date go at the end, under No date. The checklist button at the bottom right turns the boxes on to share or delete several at once. When sharing over the internet, if the songs' PDFs don't fit, the sheet says why and the send goes without them. The date doesn't go along when you share: whoever receives it gets the day the setlist arrived. Above the list you can search by name. With a search on, the total at the top also shows how many are left, and the × next to it clears it.",
        ],
        "figs": [Fig(("tablet_2_setlists", "celular_2_setlists", "Os setlists", "The setlists"))],
        "subs": [
            sub("setlists-dentro", "Abrir e editar o setlist", "Opening and editing a setlist",
                ["- Tocar no cartão abre o setlist no menu. Tocar, no ⋮ do cartão, abre o palco direto: na música em que o show parou hoje, ou na primeira. Se você tinha deixado um intervalo aberto, ele volta com o tempo.",
                 "- O ⋮ do cartão tem Tocar, Compartilhar, Editar nome e data e Apagar o setlist (que pergunta antes). Compartilhar abre a mesma folha do setlist aberto e, quando você termina ou fecha a folha, volta para a lista, sem deixar você no setlist. Tocar num setlist abre as músicas dele; com a condução armada, tocar nele abre o palco e começa a conduzir.",
                 "- Na edição, cada música mostra o artista, o tom e, embaixo do tom, o BPM, o capo e uma marca de onda de áudio quando tem backing track.",
                 "- Segure uma música para selecionar várias e tirar de uma vez.",
                 "- No alto ficam o lápis (editar), Compartilhar e o ⋮ com Renomear, Duplicar (uma cópia para montar outro show) e Apagar o setlist. As músicas continuam na biblioteca.",
                 "- Com o palco aberto, a alça da borda abre o [setlist no menu](#palco-setlist), onde ficam Editar, Compartilhar e, no ⋮, Conduzir a banda e Limpar tocadas."],
                ["- Tapping the card opens the setlist in the menu. Play, in the card's ⋮, opens the stage right away: on the song where the show stopped today, or on the first. If you had left a break open, it comes back with its time.",
                 "- The ⋮ on the card has Play, Share, Edit name and date and Delete setlist (which asks first). Share opens the same sheet as the open setlist's and, when you finish or close the sheet, goes back to the list instead of leaving you in the setlist. Tapping a setlist opens its songs; with leading armed, tapping it opens the stage and starts leading.",
                 "- In the edit, each song shows its artist, its key and, under the key, the BPM, the capo and an audio wave mark when it has a backing track.",
                 "- Hold a song to select several and take them out at once.",
                 "- At the top are the pencil (edit), Share and the ⋮ with Rename, Duplicate (a copy to build another show from) and Delete setlist. The songs stay in the library.",
                 "- With the stage open, the edge handle opens the [setlist in the menu](#palco-setlist), where Edit, Share and, in the ⋮, Lead the band and Clear played are."],
                [Fig(("jornada_setlist_1b_menu_cartao_tablet", "jornada_setlist_1b_menu_cartao", "O ⋮ do cartão", "The card's ⋮")),
                 Fig(("tablet_3_setlist", "celular_3_setlist", "O setlist aberto, com o tom, o BPM, o capo e a faixa em cada linha", "The open setlist, with the key, BPM, capo and track on each row"))]),
            sub("setlists-show", "Onde o show está", "Where the show is",
                ["No setlist aberto no menu, a faixa na ponta de cada música diz onde o show está.",
                 "- **Tocada:** a música ficou mais de um minuto no palco. Passar por ela rápido não conta.",
                 "- **Agora:** a música que está no palco, ou a última que esteve.",
                 "- **Próx.:** a que vem depois dela, mesmo com um intervalo no meio.",
                 "As marcas valem para o dia: abrir o palco em outro dia começa do zero. Para limpar na hora, use Limpar tocadas, no ⋮ do setlist aberto no menu. Elas ficam só neste aparelho e não vão junto quando você compartilha o setlist."],
                ["In the setlist open in the menu, the strip at the edge of each song tells where the show is.",
                 "- **Played:** the song stayed on stage for more than a minute. Passing by it quickly doesn't count.",
                 "- **Now:** the song on stage, or the last one that was.",
                 "- **Next:** the one after it, even with a break in between.",
                 "The marks last for the day: opening the stage on another day starts from zero. To clear them right away, use Clear played in the ⋮ of the setlist open in the menu. They stay on this device only and don't go along when you share the setlist."]),
            sub("setlists-editar", "Editar o setlist", "Editing a setlist",
                ["Toque num setlist: ele abre para você olhar, com as marcas de onde o show está. No alto, ‹ Todos os setlists volta para a lista (tocar de novo na aba Setlists faz o mesmo). Para mudar, toque no lápis, no alto à direita. Cada linha ganha uma alça à esquerda e um círculo com traço à direita.",
                 "- Arraste pela alça para mudar a ordem.",
                 "- O círculo com traço tira a música do setlist na hora. Tirou sem querer? Toque em Desfazer no aviso que aparece embaixo. A música continua na biblioteca.",
                 "- O disquete (Salvar) fica no alto e só acende quando algo mudou; ele termina a edição. Se você fechar o menu, tocar no × ou na aba Setlists com a edição aberta, o app pergunta se quer salvar as alterações no setlist, descartá-las ou continuar editando.",
                 "- Músicas e Intervalo também ficam no alto, só na edição. Compartilhar espera você salvar."],
                ["Tap a setlist: it opens to look at, with the marks of where the show is. At the top, ‹ All setlists goes back to the list (tapping the Setlists tab again does the same). To change it, tap the pencil at the top right. Every row gets a handle on the left and a circle with a dash on the right.",
                 "- Drag by the handle to change the order.",
                 "- The circle with a dash takes the song out of the setlist right away. Didn't mean to? Tap Undo on the notice at the bottom. The song stays in the library.",
                 "- The floppy (Save) is at the top and only lights up when something changed; it ends the edit. If you close the menu, tap the × or the Setlists tab with the edit open, the app asks whether to save the changes to the setlist, discard them or keep editing.",
                 "- Songs and Break are at the top too, only while editing. Share waits until you save."]),
            sub("setlists-intervalo", "Intervalo", "Breaks",
                ["Show em mais de um bloco? Toque em Intervalo. Ele entra antes da última música. Para pôr no lugar certo, arraste na edição do setlist, como uma música. Ali o círculo com traço tira o intervalo. Um setlist pode ter vários, sempre entre duas músicas. Com menos de 2 músicas o botão fica apagado, e tocar nele diz: Precisa de pelo menos 2 músicas.",
                 "Com intervalo, cada bloco mostra o número dele e quantas músicas tem, e a numeração das músicas continua de um bloco para o outro. No palco, o intervalo aparece na hora dele: veja [Intervalo no palco](#palco-intervalo).",
                 "Os intervalos vão junto quando você manda o setlist pela banda ou pelas 3 palavras: quem recebe fica com eles entre as mesmas músicas. Se faltar uma música lá, o intervalo fica antes da próxima que a pessoa tem. Pelo QR vão só as músicas.",
                 "Quem está com uma versão mais antiga do app não recebe o setlist com intervalos: o app avisa que veio de um Stagebook mais novo e pede para atualizar. Um setlist recebido pelo editor no computador também vem com os intervalos, e ali dá para ver e mudar os intervalos: veja [Setlists no computador](#web-setlists)."],
                ["A show in more than one set? Tap Break. It goes in before the last song. To put it in place, drag it in the setlist's edit, like a song. There the circle with a dash removes the break. A setlist can have several, always between two songs. With fewer than 2 songs the button is dimmed, and touching it says: Needs at least 2 songs.",
                 "With a break, each set shows its number and how many songs it has, and the song numbers keep counting from one set to the next. On stage the break shows up at its time: see [Break on stage](#palco-intervalo).",
                 "Breaks go along when you send the setlist through the band or by the 3 words: the other person gets them between the same songs. If a song is missing there, the break stays before the next one they have. By QR only the songs go.",
                 "Someone on an older version of the app doesn't get a setlist with breaks: the app says it came from a newer Stagebook and asks to update. A setlist received in the editor on the computer comes with its breaks too, and there you can see and change them: see [Setlists on the computer](#web-setlists)."],
                [Fig(("tablet_3_setlist_intervalo", "celular_3_setlist_intervalo", "Um intervalo entre dois blocos, na edição do setlist", "A break between two sets, in the setlist edit"))]),
            sub("setlists-musicas", "Adicionar músicas", "Adding songs",
                ["Dentro do setlist, toque no lápis e depois no + (Músicas); um setlist vazio já abre com o + à vista. Marque as músicas na ordem em que vão ser tocadas: cada marcada mostra o número do lugar que vai ocupar, contando as que o setlist já tem. A tela se chama Adicionar músicas, e o nome do setlist, no alto à esquerda (‹ Show de sábado), volta para ele. O check no alto, com o total de músicas que o setlist terá ao lado, adiciona as marcadas; se você voltar sem tocar nele, elas entram do mesmo jeito; se fechar o menu com músicas marcadas, o app pergunta: Adicionar, Descartar ou Continuar editando. As que já estão no setlist ficam esmaecidas e marcadas. A busca e as tags ajudam a achar, e as tags que você escolheu ficam guardadas para esse setlist: na próxima vez que abrir, o filtro volta como estava. Cada música mostra o artista, o tom, o BPM, o capo e a marca de backing track, como na lista de músicas. Pela aba Músicas também dá: selecione várias e use Adicionar ao setlist.",
                 "Um setlist precisa caber num QR, então tem limite de tamanho. Quando encher, o app avisa: crie outro."],
                ["Inside the setlist, tap the pencil and then the + (Songs); an empty setlist already opens with the + in view. Check the songs in the order they'll be played: each one checked shows the number of the place it will take, counting the ones the setlist already has. The screen is called Add songs, and the setlist's name at the top left (‹ Saturday show) goes back to it. The check at the top, with the total of songs the setlist will have beside it, adds the checked ones; if you go back without tapping it, they go in anyway; if you close the menu with songs checked, the app asks: Add, Discard or Keep editing. The ones already in the setlist are faded and checked. Search and tags help you find them, and the tags you chose are kept for that setlist: next time you open it, the filter is back as you left it. Each song shows its artist, key, BPM, capo and backing track mark, as in the song list. You can also do it from the Songs tab: select several and use Add to setlist.",
                 "A setlist has to fit in a QR, so its size has a limit. When it's full the app tells you: create another."],
                [Fig(("tablet_9_escolher_musicas", "celular_9_escolher_musicas", "Escolhendo as músicas", "Picking the songs"))]),
        ],
    },
    {
        "id": "compartilhar", "pt": "Compartilhar e QR", "en": "Sharing and QR",
        "text_pt": [
            "Num setlist, ou com várias músicas selecionadas, Compartilhar oferece os caminhos abaixo. Quem recebe sempre confere antes de entrar.",
            "Com alguém na banda, o primeiro caminho é Mandar para a banda: quem está no mesmo Wi-Fi recebe na hora, e os outros pela internet em até 12 horas. Veja [Mandar e receber](#banda-mandar).",
        ],
        "text_en": [
            "In a setlist, or with several songs selected, Share offers the ways below. Whoever receives always checks before anything comes in.",
            "With someone in the band, the first way is Send to the band: whoever is on the same Wi-Fi gets it right away, and the others online within 12 hours. See [Sending and receiving](#banda-mandar).",
        ],
        "figs": [Fig(("jornada_compartilhar_1_folha_tablet", "jornada_compartilhar_1_folha", "Compartilhar: com internet e, embaixo, sem internet", "Share: with internet and, below, without it"))],
        "subs": [
            sub("compartilhar-qr", "QR sem internet", "QR without internet",
                ["Quem está do lado lê com a câmera, no + embaixo → Ler QR, mesmo sem internet. O QR só aparece para um setlist; músicas soltas vão pela banda ou pela internet. Vão só os títulos: os [intervalos](#setlists-intervalo) do setlist não vão pelo QR. Na primeira vez, o app pede a câmera."],
                ["Whoever is next to you reads it with the camera, in the + at the bottom → Scan QR, even offline. The QR only shows for one setlist; loose songs go through the band or the internet. Only the titles go: the setlist's [breaks](#setlists-intervalo) don't go by QR. The first time, the app asks for the camera."]),
            sub("compartilhar-palavras", "3 palavras pela internet", "3 words over the internet",
                ["Enviar pela internet gera 3 palavras e um QR para quem vai receber. Dá para ditar as palavras, copiar ou mandar por mensagem. Elas valem por 12 horas. Quem recebe vai no + embaixo → Digitar as 3 palavras. Quem aceita o que veio pelas 3 palavras entra na banda de quem mandou, e quem mandou entra na dele: a janela das palavras mostra “Ana aceitou e agora é da banda”. Para desfazer, remova em Ajustes → Banda e computador. Cada envio pela internet leva até 60 mensagens: uma de quem manda, uma por setlist e uma por música. Um setlist com mais de 58 músicas vai só com os títulos. Uma música comprida demais para uma mensagem não vai, e a janela das palavras diz quantas ficaram de fora: mande essa pela banda. Se vieram vários setlists, o primeiro abre para conferir e os outros ficam em Recebidos da banda.",
                 "A mensagem leva também um link. Quem toca nele abre o Stagebook direto na tela de receber. Quem ainda não tem o app vai para a página de baixar, e o link fica copiado: ao abrir o app pela primeira vez, ele recebe sozinho. Link tocado com o palco na tela (ou com a edição de uma música aberta) espera: abre quando você abrir o menu ou sair da edição.",
                 "Com Levar as músicas ligado, as letras vão junto. Quem recebe só fica com as que não tinha; as dele não mudam."],
                ["Send online makes 3 words and a QR for whoever will receive. You can say the words, copy them or send them in a message. They last 12 hours. The other person goes to the + at the bottom → Type the 3 words. Whoever accepts what came by the 3 words joins the sender's band, and the sender joins theirs: the words window shows “Ana accepted and is now in the band”. To undo it, remove them in Settings → Band and computer. Each send over the internet takes up to 60 messages: one for the sender, one per setlist and one per song. A setlist with more than 58 songs goes with titles only. A song too long for one message doesn't go, and the words window says how many were left out: send that one through the band. If several setlists came, the first opens to check and the others wait in Received from the band.",
                 "The message also has a link. Tapping it opens Stagebook straight on the receive screen. Someone without the app yet goes to the download page, and the link stays copied: when they open the app for the first time, it receives by itself. A link tapped with the stage on screen (or a song editor open) waits: it opens when you open the menu or leave the editor.",
                 "With Bring the songs on, the lyrics go too. The other person only keeps the ones they didn't have; theirs don't change."],
                [Fig(("jornada_compartilhar_2_codigo_tablet", "jornada_compartilhar_2_codigo", "As 3 palavras", "The 3 words")),
                 Fig(("jornada_compartilhar_3_receber_tablet", "jornada_compartilhar_3_receber", "Recebendo pelas 3 palavras", "Receiving with the 3 words"))]),
            sub("compartilhar-conferir", "Conferir o que chegou", "Checking what arrived",
                ["Por QR, 3 palavras ou pela banda, a tela de conferir é a mesma. Cada música aparece como no setlist, com número, título, artista e, se você já tem a música, tom, BPM, capo e backing track. Embaixo, no mesmo cartão, o app diz o que acontece com ela e traz os controles. Se há músicas parecidas na sua biblioteca, Escolher abre a lista delas, com os mesmos dados. Antes de criar, o app avisa o que vai ficar de fora.",
                 "Se o setlist veio com [intervalos](#setlists-intervalo), a linha das músicas diz quantos ele vai ter com as músicas que entram. Pelo QR os intervalos não vêm.",
                 "Se você tem outra versão de uma música, Ver o que muda mostra a diferença, e você escolhe entre Usar a recebida e Fica a minha. Se veio só a letra, dá para aceitar ou usar Buscar com cifra.",
                 "Se já existe um setlist com o mesmo nome, você escolhe entre Substituir e Criar outro."],
                ["By QR, 3 words or from the band, the review screen is the same. Each song looks as it does in the setlist, with number, title, artist and, if you already have it, key, BPM, capo and backing track. Below, in the same card, the app says what happens to it and has the controls. If your library has similar songs, Choose opens the list of them, with the same facts. Before creating, the app tells you what will be left out.",
                 "If the setlist came with [breaks](#setlists-intervalo), the line with the songs says how many it will have with the songs going in. Breaks don't come by QR.",
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
            "Ao abrir o app, ele já volta ao palco onde você parou: a última música, ou o último setlist, na música em que o show estava. Se aquela música ou setlist foi apagada, o app abre sem palco, com o menu aberto. A alça na borda do palco abre o menu (veja Músicas); o voltar do Android, com o menu fechado, sai do app, e antes pergunta se você está num show. Com o menu aberto, o pedal não vira página nem música do palco atrás dele, e quem acompanha a banda espera: a música do condutor abre quando o menu fecha (ou no Ir da faixa de baixo).",
        ],
        "text_en": [
            "On stage the lyrics show large, with the chords and parts. In a setlist, the top shows the position, like 2 of 12, and the end of the song shows the next one. While the stage is open nothing pops up over it: whatever the band sends waits.",
            "When you open the app, it goes back to the stage where you left off: the last song, or the last setlist, on the song the show was on. If that song or setlist was deleted, the app opens with no stage and the menu open. The handle on the stage's edge opens the menu (see Songs); Android's back, with the menu closed, leaves the app, asking first if you are in a show. With the menu open, the pedal does not turn a page or song of the stage behind it, and whoever follows the band waits: the leader's song opens when the menu closes (or on Go in the strip below).",
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
            sub("palco-setlist", "O setlist no palco", "The setlist on stage",
                ["O setlist do palco e o do menu são um só. Abriu o palco por um setlist? Toque na alça pequena, de três traços, na borda esquerda do palco, ou puxe-a para a direita: o menu abre já nesse setlist, com a música do palco à vista. Quer a alça em outro lugar? Arraste para cima ou para baixo: ela fica onde você largar. Para a borda direita (para quem toca com a mão esquerda), segure até o aparelho vibrar e leve além do meio da tela. O menu passa a abrir desse lado.",
                 "- **O que aparece:** as músicas na ordem, com o artista, o tom, o BPM, o capo, a onda de áudio do backing track, os intervalos (o que está aberto diz o tempo) e as marcas de [onde o show está](#setlists-show).",
                 "- **Trocar de música:** toque na música. O palco abre nela.",
                 "- **No alto:** o lápis (editar), Compartilhar e o ⋮, com Conduzir a banda (só com alguém da banda adicionado), Limpar tocadas (só com alguma música tocada), Renomear, Duplicar (uma cópia para montar outro show) e Apagar.",
                 "Um botão do pedal também abre o menu: veja [Botões](#ajustes-pedal-botoes). Com o menu aberto, o pedal não vira página. Uma música aberta pela aba Músicas abre o menu na lista."],
                ["The stage's setlist and the menu's are one. Opened the stage from a setlist? Tap the small handle with three lines on the stage's left edge, or pull it to the right: the menu opens on that setlist, with the song on stage in view. Want the handle somewhere else? Drag it up or down: it stays where you let go. For the right edge (for left-handed players), hold it until the device buzzes and take it past the middle of the screen. The menu then opens on that side.",
                 "- **What you see:** the songs in order, with the artist, the key, the BPM, the capo, the backing track's audio wave, the breaks (an open one says its time) and the marks of [where the show is](#setlists-show).",
                 "- **Changing song:** tap the song. The stage opens on it.",
                 "- **At the top:** the pencil (edit), Share and the ⋮, with Lead the band (only with someone added to the band), Clear played (only with a song played), Rename, Duplicate (a copy to build another show from) and Delete.",
                 "A pedal button can open the menu too: see [Buttons](#ajustes-pedal-botoes). With the menu open the pedal doesn't turn pages. A song opened from the Songs tab opens the menu on the list."]),
            sub("palco-intervalo", "Intervalo no palco", "Break on stage",
                ["Quando o setlist tem um [intervalo](#setlists-intervalo) depois da música, o fim dela mostra Próxima: Intervalo. Avançar (ou tocar em Próxima: Intervalo) abre a tela do intervalo, com o tempo que ele já dura e a música que vem a seguir, com o tom.",
                 "Avançar de novo abre a próxima música. Voltar retorna à música de antes do intervalo. Voltar da primeira página da música seguinte mostra o intervalo de novo. Abrir uma música direto pelo setlist não passa pelo intervalo.",
                 "O tempo do intervalo vai pelo relógio e não para quando você sai: saia para um ajuste (editar uma música, Ajustes), volte com Voltar, e o palco está no intervalo, com o tempo certo. Com o menu aberto no setlist, a linha do intervalo ali mostra o tempo, correndo ou parado (com o ícone de pausa), e tocar nela abre o intervalo; tocar numa música abre sempre a música. Quem acompanha vê ali o tempo de quem conduz, e ele some quando a banda segue para a música seguinte. Ele só recomeça do zero depois que a música seguinte toca por um minuto. Quem conduz ou toca sozinho tem **Pausar** (⏸), **Retomar** (▶) e **Zerar** (↺) logo abaixo do tempo; zerar continua contando se estava contando, e fica parado em 00:00 se estava pausado. Quem acompanha só vê o tempo, e ele pausa, retoma e zera na hora junto com o de quem conduz. Com uma versão mais antiga do app, o tempo de quem acompanha segue contando sozinho.",
                 "O [tempo de show](#palco-relogio) para no intervalo e recomeça do zero no bloco seguinte. O relógio comum não muda."],
                ["When the setlist has a [break](#setlists-intervalo) after the song, its end shows Next: Break. Going forward (or touching Next: Break) opens the break screen, with how long it has lasted and the song that comes next, with its key.",
                 "Going forward again opens the next song. Going back returns to the song before the break. Going back from the first page of the next song shows the break again. Opening a song straight from the setlist skips the break.",
                 "The break's time runs by the clock and doesn't stop when you leave: leave for an adjustment (editing a song, Settings), come back with Back, and the stage is on the break, with the right time. With the menu open on the setlist, the break's row there shows the time, running or stopped (with the pause icon), and tapping it opens the break; tapping a song always opens the song. Whoever follows sees the leader's time there, and it goes away when the band moves on to the next song. It only starts from zero again after the next song has played for a minute. Whoever leads or plays alone has **Pause** (⏸), **Resume** (▶) and **Reset** (↺) right under the time; reset keeps counting if it was counting, and stays at 00:00 if it was paused. Whoever follows only sees the time, and it pauses, resumes and resets right along with the leader's. On an older version of the app, the follower's time keeps counting by itself.",
                 "The [show time](#palco-relogio) stops at the break and starts from zero in the next set. The plain clock doesn't change."],
                [Fig(("tablet_6_palco_intervalo", "celular_7_palco_intervalo", "O intervalo no palco", "The break on stage"))]),
            sub("palco-rolagem", "Rolagem automática", "Auto-scroll",
                ["No modo Rolar, quando a letra não cabe na tela, o ícone de rolagem ao lado do relógio abre a Rolagem automática: escolha a velocidade, de 1 a 10, e toque no ▶. Tocar na tela, apertar o pedal ou tocar no ícone pausa. Ao trocar de música ela para. Tocar na tela ou apertar o pedal pausa."],
                ["In Scroll mode, when the lyrics don't fit the screen, the scroll icon next to the clock opens Auto-scroll: pick the speed, 1 to 10, and tap ▶. Tapping the screen, pressing the pedal or tapping the icon pauses it. It stops when the song changes. Tapping the screen or pressing the pedal pauses it."],
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
                ["O lápis no topo abre o editor nesta página. Segurando o dedo numa linha aparecem dois ícones: o lápis (Editar esta linha) e o Marca-texto. Eles somem sozinhos em 4 segundos.",
                 "Com o Marca-texto ligado, passe o dedo nas palavras para destacar. Passar de novo apaga. Enquanto ele está ligado, o palco não vira página com o dedo; toque em Pronto para terminar."],
                ["The pencil at the top opens the editor on this page. Holding a line shows two icons: the pencil (Edit this line) and the Highlighter. They go away by themselves after 4 seconds.",
                 "With the Highlighter on, run your finger over the words to mark them. Again removes it. While it's on, the stage doesn't turn pages by touch; tap Done to finish."]),
            sub("palco-sair", "Sair do palco", "Leaving the stage",
                ["O palco é a base do app: não tem seta para sair. Para trocar de música ou de setlist, abra o menu pela alça. O voltar do Android, com o menu fechado, sai do app; num setlist, ou acompanhando a banda, ele pergunta antes, para um toque sem querer não tirar a letra da tela no show."],
                ["The stage is the app's base: it has no arrow to leave by. To change song or setlist, open the menu with the handle. Android's back, with the menu closed, leaves the app; in a setlist, or following the band, it asks first, so a stray tap doesn't take the lyrics off the screen during the show."]),
            sub("palco-acorde", "Diagrama do acorde", "Chord diagram",
                ["Tocando num acorde você vê o diagrama, para violão, guitarra, ukulele, cavaquinho ou teclado. As setas trocam a variação, Usar esta guarda a escolhida nesta música e Tocar faz o som. Tocar num acorde da [lista de acordes](#palco-lista) abre o mesmo diagrama.",
                 "Com capotraste, o diagrama mostra o capo na casa dele, com o desenho tocado a partir dali. No teclado, os acordes aparecem ajustados para você tocar junto com a banda."],
                ["Tap a chord to see its diagram for acoustic guitar, electric guitar, ukulele, cavaquinho or keyboard. The arrows change the variation, Use this one keeps it in this song and Play makes the sound. Tapping a chord in the [chord list](#palco-lista) opens the same diagram.",
                 "With a capo, the diagram shows it on its fret, with the shape played from there. On keyboard, the chords show adjusted so you can play along with the band."],
                [Fig(("tablet_6_acorde_violao", "celular_6_acorde_violao", "O diagrama do acorde", "The chord diagram")),
                 Fig(("tablet_6_acorde_capo", "celular_6_acorde_capo", "O capo no diagrama", "The capo on the diagram")),
                 Fig(("tablet_6_palco_teclado_capo", "celular_7_palco_teclado_capo", "Teclado numa cifra com capo", "Keyboard on a chart with a capo"))]),
            sub("palco-detalhes", "Detalhes do acorde", "Chord details",
                ["Detalhes abre uma segunda tela na mesma janela, para não tirar a atenção do diagrama: mostra o grau do acorde no tom da música, o campo harmônico dele e onde mais ele aparece. A seta no topo volta ao desenho. Com capotraste, a tela diz qual é o desenho e como o acorde soa, e o campo harmônico mostra o que soa, não o desenho da cifra."],
                ["Details opens a second screen in the same window, so the diagram keeps your attention: it shows the chord's degree in the song's key, its harmonic field and where else it appears. The arrow at the top goes back to the diagram. With a capo, the screen says which shape it is and how the chord sounds, and the harmonic field shows what sounds, not the chart's shape."],
                [Fig(("tablet_6_acorde_detalhes", "celular_6_acorde_detalhes", "Detalhes do acorde", "Chord details"))]),
            sub("palco-lista", "Lista de acordes", "Chord list",
                ["Os diagramas de todos os acordes da música podem ficar no início ou no fim do palco, crescendo para ocupar a largura da tela. Uma lista longa segue para a próxima coluna ou página, sempre no mesmo tamanho. Ligue em [Diagramas dos acordes](#ajustes-diagramas).",
                 "Segure um acorde e confirme em Esconder para tirá-lo da lista. O × no canto esconde a lista inteira nessa música, e o botão com a seta traz tudo de volta."],
                ["The diagrams of all the song's chords can sit at the start or end of the stage, grown to fill the screen's width. A long list runs on to the next column or page, always at the same size. Turn it on in [Chord diagrams](#ajustes-diagramas).",
                 "Hold a chord and confirm with Hide to take it off the list. The × in the corner hides the whole list in that song, and the arrow button brings everything back."],
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
                 "Segurando o relógio, abre a Duração do show. Os botões − e + mudam meia hora de cada vez, de 30 minutos a 6 horas (começa em 1 hora), e Começar a contar agora zera o tempo de show. Um show começado há mais de 12 horas conta como novo."],
                ["Tapping the clock changes what it shows: the time, the show time, with a stopwatch, and the time left, with an hourglass. The show time starts counting the first time it shows up. In the last 10 minutes the time left turns red, and past the end it shows how late you are with a minus sign.",
                 "Holding the clock opens Show length. The − and + buttons change it half an hour at a time, from 30 minutes to 6 hours (it starts at 1 hour), and Start counting now resets the show time. A show started more than 12 hours ago counts as a new one."],
                [Fig(("tablet_6_palco_tempo_show", "celular_7_palco_tempo_show", "Quanto falta e a duração do show", "Time left and the show's length"))]),
        ],
    },
    {
        "id": "backing", "pt": "Backing track", "en": "Backing track",
        "text_pt": [
            "Cada música pode ter um backing track, em OGG com vários canais, MP3 ou WAV. Escolha nos Dados da música, no campo com a onda de áudio, a mesma marca que aparece nas listas. No palco, o play só aparece nas músicas que têm um. Se o áudio de uma música não veio com ela, um alerta aparece no lugar do play. Toque nele para ver o nome do áudio e anexar de novo.",
        ],
        "text_en": [
            "Each song can have a backing track, as multichannel OGG, MP3 or WAV. Pick it in Song details, in the field with the audio wave, the same mark the lists show. On stage, play only shows on songs that have one. If a song's audio didn't come with it, an alert shows where play would be. Tap it to see the audio's name and attach it again.",
        ],
        "figs": [Fig(("tablet_11_player_tocando", "celular_11_player_tocando", "Tocando com backing track", "Playing with a backing track")),
                 Fig(("tablet_11_player_usb_fora", "celular_11_player_usb_fora", "A interface saiu no meio da música", "The interface came out mid-song"))],
        "subs": [
            sub("backing-tocar", "Tocar e trocar", "Playing and changing",
                ["Quando a música acaba, ele para e volta ao começo. Sem faixa, a música não mostra botão no palco: anexe a faixa nos Dados da música. Segurando o play você troca ou tira a faixa e abre o mixer da música.",
                 "Trocar de música com a faixa tocando não para a faixa: ela continua até você tocar em parar ou ela acabar, e o botão de parar fica em qualquer música enquanto isso. Depois o play já toca a música que está na tela.",
                 "Para a faixa não parar com a tela apagada, o Android precisa liberar o Stagebook da economia de bateria. O app pede isso numa janela, uma vez em cada aparelho: toque em Continuar e depois em Permitir."],
                ["When the song ends it stops and goes back to the start. A song with no track shows no button on stage: attach the track in the song's Data. Hold play to change or remove the track and to open the song's mixer.",
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
                 "Na primeira vez com uma interface nesse aparelho, o app prepara a interface assim que ela é espetada, com o aviso para aguardar, e lê quantas saídas ela tem. Para isso, Player de faixas precisa estar ligado em Ajustes → Aparelho → Modo essencial. Ao mesmo tempo, ele mede quantos pacotes a USB do aparelho entrega. Se perder mais de 0,3%, aparece **A USB pode falhar**, com quanto perdeu: o som pode engasgar ou falhar no meio da música, e usar assim é por sua conta e risco. Com interfaces de muitos canais, o aviso diz que são eles que pesam. O aviso aparece uma vez por interface em cada aparelho, e de novo só se a perda piorar."],
                ["When you plug in the interface, the app shows its name in the mixer and the metronome right away, without waiting for play. An interface already used on that device comes back with its outputs and routing. When you unplug the cable, the screen moves to Device; plug it back and it returns to the interface.",
                 "The first time with an interface on that device, the app prepares the interface as soon as it's plugged in, with the notice to wait, and reads how many outputs it has. For that, Track player has to be on in Settings → Device → Essential mode. At the same time it measures how many packets the device's USB delivers. If it loses more than 0.3%, **USB may fail** shows up, with how much it lost: the sound may stutter or drop in the middle of a song, and using it that way is at your own risk. With interfaces with many channels, the warning says they're what weighs. The warning shows once per interface on each device, and again only if the loss gets worse."]),
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
                ["O mixer tem um canal para cada canal do arquivo (mostra até 8), com fader e botão para desligar o canal. Ver os níveis ao vivo mostra o sinal de cada um. Tocando no nome do canal você renomeia, e o ícone ao lado abre os outros ajustes:",
                 "- **Saída:** em qual saída da interface ele toca. Vale qualquer uma das primeiras 32 saídas, como as 18 de uma mesa digital. Pela rota do Android, o sistema entrega no máximo 8 saídas. Uma saída acima disso fica em vermelho e não toca até você voltar ao caminho normal. A escolha fica guardada para cada interface.",
                 "- **Igualar o volume:** Não toca como foi gravado, Média deixa todas as músicas no mesmo volume percebido e Pico, com o mesmo pico.",
                 "- **Estéreo e mono:** junta dois canais em estéreo, ou toca no meio, igual nos dois lados.",
                 "- **Equalizador ligado:** faz o canal passar pelo equalizador."],
                ["The mixer has one channel per file channel (it shows up to 8), with a fader and a button to turn the channel off. Monitor live levels shows each one's signal. Tap a channel's name to rename it, and the icon next to it opens the other settings:",
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
                 Fig(("tablet_6_palco_link_guardado", "celular_7_palco_link_guardado", "Link tocado no palco espera você abrir o menu", "A link tapped on stage waits until you open the menu"))]),
            sub("banda-mandar", "Mandar e receber", "Sending and receiving",
                ["Mandar um setlist para a banda é um toque, em Compartilhar. Quem está no mesmo Wi-Fi recebe na hora; quem não está, recebe pela internet quando abrir o app, em até 12 horas. Para a banda vão até 30 setlists por envio, e Recebidos da banda guarda os 30 últimos.",
                 "Quando chega algo, aparece um aviso rápido embaixo e um número no ícone da banda, ao lado do título da aba, até você conferir. Tudo fica em Recebidos da banda: tocar na linha abre para conferir, e a lixeira descarta. Se mais de uma pessoa mandou o mesmo setlist, abra cada versão e escolha uma.",
                 "Todos os aparelhos da banda precisam estar na mesma versão do app para conversar. Se algum estiver diferente, o app avisa, na lista de aparelhos perto e ao pedir para entrar: atualize o app em todos. Pela internet, se o aparelho de quem recebe for mais antigo e o setlist tiver intervalos, ou se alguém pedir as músicas que faltam, o app avisa em Recebidos da banda que é preciso atualizar o app, em vez de abrir sem os intervalos ou ignorar o pedido."],
                ["Sending a setlist to the band takes one tap, in Share. Whoever is on the same Wi-Fi gets it right away; the others get it over the internet when they open the app, within 12 hours. Up to 30 setlists go to the band per send, and Received from the band keeps the latest 30.",
                 "When something arrives you get a short notice at the bottom and a number on the band icon next to the tab's title until you check it. It all stays in Received from the band: tapping the row opens it to check, and the trash can discards it. If more than one person sent the same setlist, open each version and pick one.",
                 "Every band device needs the same app version to talk. If one is different, the app warns you, in the nearby devices list and when asking to join: update the app on all of them. Over the internet, if the receiving device is older and the setlist has breaks, or someone asks for the missing songs, the app says in Received from the band that it needs updating, instead of opening it without the breaks or ignoring the request."],
                [Fig(("jornada_banda_3_compartilhar_tablet", "jornada_banda_3_compartilhar", "Mandar para a banda", "Send to the band")),
                 Fig(("jornada_banda_4_aviso_tablet", "jornada_banda_4_aviso", "O que a banda mandou, no topo", "What the band sent, at the top")),
                 Fig(("tablet_2_recebidos_versoes", "celular_2_recebidos_versoes", "Versões do mesmo setlist", "Versions of the same setlist"))]),
            sub("banda-conduzir", "Conduzir a banda", "Leading the band",
                ["Conduzir a banda sempre leva um setlist. Na aba Setlists, o ícone de grupo no alto arma a condução: ele acende, a faixa embaixo diz Pronto para conduzir e Cancelar, e o próximo setlist em que você tocar abre o palco e começa a conduzir, da música em que o show parou. Tocar no ícone de novo, ou em Cancelar, desarma. Fica armado até você abrir um setlist ou cancelar, também se girar a tela ou trocar de aba. O mesmo está no ⋮ do [setlist no palco](#palco-setlist), para o setlist que já está aberto. O ícone e o item só aparecem com alguém da banda adicionado e a banda ligada. Ele leva a banda a partir da música que está no palco, e os aparelhos da banda com o app aberto no mesmo Wi-Fi entram sozinhos em Acompanhando, na mesma música, e seguem cada troca.",
                 "- Quem acompanha não precisa ter o setlist, só as músicas. Se faltar uma, a faixa embaixo fica vermelha e ganha o botão Pedir. Ele pede a música à banda neste Wi-Fi. Se ninguém tiver, um aviso diz.",
                 "- No fim de cada música aparece a próxima do setlist de quem conduz.",
                 "- No [intervalo](#palco-intervalo) de quem conduz, quem acompanha continua livre no palco, com o pedal e o toque funcionando, e a faixa embaixo mostra o tempo do intervalo e a música que vem a seguir. Só quem não tem a música de antes vê a tela do intervalo sozinha, com o mesmo tempo e a próxima música; ela sai quando quem conduz segue ou volta, e até lá o pedal e o toque não viram a página. Quem está em outra versão do app não acompanha: atualize o app em todos.",
                 "- No intervalo, a faixa embaixo também mostra o tempo e a próxima música. Ela fica vermelha se você não tem a próxima.",
                 "- Quem abriu o app depois entra sozinho em alguns segundos. Se não entrar, quem conduz toca em Procurar a banda (no celular, a lupa na faixa embaixo).",
                 "- Sair, na faixa embaixo, para de acompanhar: nada mais abre sozinho. Enquanto a mesma pessoa conduz, a faixa mostra quem está conduzindo e o botão Acompanhar. Ele abre a música ou o intervalo em que a banda está. Procurar a banda, de quem conduz (no celular, a lupa), também traz de volta quem saiu.",
                 "- Quem está no meio de algo não é tirado da tela: editando, escolhendo músicas, recebendo, importando, em Ajustes, com um menu ou uma janela aberta, ou com o teclado aberto. A faixa embaixo diz para onde a banda foi (“Voz foi para “Evidências””) e que a música abre quando você fechar o menu. Ir abre na hora. Ao terminar (fechar o menu, a janela ou os Ajustes), o palco abre sozinho na música em que a banda está agora, sem acumular as que passaram. O intervalo também: a faixa avisa, e a tela dele abre na volta se ele ainda não acabou. No palco vale o mesmo: com a janela do metrônomo, do acorde ou da mesa aberta, a música da banda espera e abre quando você fecha. No editor de uma música, Ir sai do editor: se há algo não salvo, pergunta antes “Sair sem salvar?”, e só depois abre a música da banda. Com um setlist em edição é igual: a tela segura quem acompanha enquanto está aberta, e Ir pergunta antes se você quer Salvar, Descartar ou Continuar editando.",
                 "- Na faixa de quem conduz, “N da banda não respondeu” conta quem está neste Wi-Fi agora e não respondeu: some quando a pessoa responde ou sai, e muda se alguém chega.",
                 "- Seguir a banda não trava o palco: se você está numa música diferente da que a banda toca, o palco não é puxado de volta. A faixa embaixo diz “Voz está conduzindo “Evidências”” e Ir abre a música. Quem está na música da banda segue junto a cada troca. Se você chegar sozinho na música da banda, o aviso some.",
                 "- Quem conduz vê em Ver o que falta quem não tem cada música, com as músicas em linhas como no setlist (título e artista). Ali dá para enviar as que faltam a um aparelho, ou a todos de uma vez. Só recebe quem está acompanhando neste Wi-Fi. Quando a música chega, o aparelho sai da lista, também quando ele a pediu (Pedir) a quem conduz: a lista se atualiza em alguns segundos, sem tocar em Enviar.",
                 "- Quem acompanha vê em Ver o que falta aqui as músicas do setlist que não estão neste aparelho, antes de a banda chegar nelas, em linhas com título e artista. Só aparece quando falta alguma, e a música que chega sai da lista na hora; quando a lista esvazia, a janela fecha.",
                 "- Na mesma janela, primeiro Qual cópia pedir: Igual à de quem conduz (o padrão: cada música do jeito que quem conduz toca, letra, cifra ou PDF), Só letra, Cifra ou PDF. Quem responde manda essa cópia ou nada, e Só letra vem sem a cifra. Depois, as ações: Pedir à banda (todas as que faltam, aos aparelhos desta rede; as que chegarem entram na hora e a faixa diz quantas vieram), Pedir as que faltam a quem conduz pela internet (a mesma cópia; chegam em Recebidos da banda quando a pessoa abrir o Stagebook) e Importar as que faltam (cria o setlist aqui e importa uma a uma dos sites). O Pedir da faixa pede só a música da faixa (a que a banda toca ou, no intervalo, a próxima), e aparece também quando a faixa espera para ir.",
                 "- O que acabou de acontecer (a música que chegou, quantas a banda tinha, o pedido enviado pela internet) a própria faixa diz, por alguns segundos, no lugar das linhas dela, com a frase inteira. Depois ela volta ao normal.",
                 "- A música que você pediu, ou que quem conduz enviou do setlist, entra direto na biblioteca durante o show. Se é a música da vez, o palco abre nela. Uma música que você já tem nunca é trocada: a que chegou espera em Recebidos da banda, como todo o resto que a banda mandar.",
                 "- Quando há mais de uma cópia, vem a que tem cifra. Se a música chegar sem cifra e seu perfil mostra cifras, a faixa avisa, e o botão Pedir procura uma cópia com cifra. Ela entra no lugar da que tinha acabado de chegar. Se você já mexeu nessa música, a cópia com cifra espera em Recebidos da banda, para você conferir.",
                 "- Se quem conduz fica 10 minutos sem dar sinal (app fechado, sem bateria, fora do Wi-Fi), a faixa de quem acompanha fica vermelha e diz “Voz desconectou há 10 minutos”.",
                 "- Abrir o menu ou outra tela não encerra a condução. Para isso, toque em Encerrar."],
                ["Leading the band always belongs to a setlist. On the Setlists tab, the group icon at the top arms it: it lights up, the bar at the bottom says Ready to lead with Cancel, and the next setlist you tap opens the stage and starts leading, from the song where the show stopped. Tapping the icon again, or Cancel, disarms it. It stays armed until you open a setlist or cancel, also if you turn the screen or change tabs. The same is in the ⋮ of the [setlist on stage](#palco-setlist), for the setlist already open. The icon and the item only show with someone added to the band and the band on. It leads the band from the song on stage, and band devices with the app open on the same Wi-Fi switch to Following by themselves, on the same song, and follow every change.",
                 "- Followers don't need the setlist, only the songs. If one is missing, the bar at the bottom turns red and gets the Ask button. It asks the band on this Wi-Fi for the song. If nobody has it, a notice says so.",
                 "- At the end of each song they see the leader's next one.",
                 "- On the leader's [break](#palco-intervalo), followers stay free on stage, with the pedal and taps working, and the bar at the bottom shows the break's time and the song that comes next. Only someone without the song before it sees the break screen alone, with the same time and the next song; it goes away when the leader moves on or back, and until then the pedal and a tap turn no page. Someone on another app version doesn't follow: update the app on all of them.",
                 "- On the break, the bar at the bottom shows the time and the next song too. It turns red if you don't have the next one.",
                 "- Someone who opens the app later joins by themselves within seconds. If not, the leader taps Look for the band (on a phone, the magnifying glass on the bar at the bottom).",
                 "- Leave, on the bar at the bottom, stops following: nothing else opens by itself. While the same person leads, the bar shows who is leading and the Follow button. It opens the song or the break the band is on. The leader's Look for the band (the magnifying glass on a phone) also brings back whoever left.",
                 "- Someone in the middle of something isn't pulled off the screen: editing, picking songs, receiving, importing, in Settings, with a menu or a window open, or with the keyboard open. The bar at the bottom says where the band went (“Voz went to “Evidências””) and that the song opens when you close the menu. Go opens it at once. When you finish (closing the menu, the window or Settings), the stage opens by itself on the song the band is on now, without piling up the ones that passed. The break too: the bar tells them, and its screen opens when they come back if it isn't over. On stage it is the same: with the metronome, chord or mixer window open, the band's song waits and opens when you close it. In a song's editor, Go leaves the editor: if something isn't saved, it asks “Leave without saving?” first, and only then opens the band's song. A setlist being edited is the same: the screen holds a follower while it is open, and Go asks first whether to Save, Discard or Keep editing.",
                 "- On the leader's bar, “N band members didn't answer” counts who is on this Wi-Fi now and didn't answer: it goes away when the person answers or leaves, and changes if someone arrives.",
                 "- Following the band doesn't lock the stage: if you are on a different song than the band, the stage isn't pulled back. The bar at the bottom says “Voz is leading “Evidências”” and Go opens it. Whoever is on the band's song moves along at every change. If you get to the band's song on your own, the notice goes away.",
                 "- The leader sees in See what's missing who lacks each song, with the songs as rows like the setlist's (title and artist). From there you can send one device the songs it lacks, or all of them at once. Only devices following on this Wi-Fi get them. When the song arrives, the device leaves the list, also when it asked the leader for it (Ask): the list updates in a few seconds, without tapping Send.",
                 "- A follower sees in See what's missing here the songs of the setlist that aren't on this device, before the band gets to them, as rows with the title and the artist. It only shows when one is missing, a song that arrives leaves the list at once, and when the list empties the window closes.",
                 "- The same window first has Which copy to ask for: Same as the leader's (the default: each song the way the leader plays it, lyrics, chords or PDF), Lyrics, Chords or PDF. Whoever answers sends that copy or nothing, and Lyrics comes without the chords. Then the actions: Ask the band (all that are missing, from the devices on this network; the ones that arrive go in at once and the strip says how many came), Ask the leader for the missing ones online (the same copy; they arrive in Received from the band when that person opens Stagebook) and Import the missing ones (makes the setlist here and imports them one by one from the sites). The strip's Ask asks only for the strip's song (the one the band is on or, on a break, the next one), and it shows also while the strip waits to go.",
                 "- What has just happened (the song that arrived, how many the band had, the request sent online) is said by the strip itself, for a few seconds, in place of its lines, with the whole sentence. Then it goes back to normal.",
                 "- A song you asked for, or one of the setlist that the leader sent, goes straight into your library during the show. If it's the current song, the stage opens on it. A song you already have is never replaced: the one that arrived waits in Received from the band, like everything else the band sends.",
                 "- When there is more than one copy, you get the one with chords. If the song comes without chords and your profile shows chords, the bar says so, and the Ask button looks for a copy with chords. It takes the place of the one that had just arrived. If you have changed that song meanwhile, the copy with chords waits in Received from the band, for you to check.",
                 "- If the leader gives no sign for 10 minutes (app closed, no battery, off the Wi-Fi), the follower's strip turns red and says “Voz disconnected 10 minutes ago”.",
                 "- Opening the menu or another screen doesn't end the leading. Tap End for that."],
                [Fig(("jornada_setlist_1c_conduzir_armado_tablet", "jornada_setlist_1c_conduzir_armado", "Conduzir armado: escolha um setlist", "Lead armed: pick a setlist")),
                 Fig(("banda_conduzir_faixas", "banda_conduzir_faixas_celular", "As faixas da banda: conduzindo, acompanhando e os avisos", "The band strips: leading, following and the notices")),
                 Fig(("banda_conduzir_falta", "banda_conduzir_falta_celular", "O que falta, e enviar", "What's missing, and sending it")),
                 Fig(("banda_seguir_falta", "banda_seguir_falta_celular", "Quem acompanha: o que falta aqui, e pedir", "A follower: what's missing here, and asking")),
                 Fig(("banda_seguir_aviso", "banda_seguir_aviso_celular", "A faixa diz o que acabou de acontecer", "The strip says what has just happened")),
                 Fig(("tablet_6_palco_acompanhando", "celular_7_palco_acompanhando", "Acompanhando no palco", "Following on stage"))]),
        ],
    },
    {
        "id": "ajustes", "pt": "Ajustes", "en": "Settings", "toc_subs": True,
        "text_pt": [
            "A aba Ajustes do menu mostra só os grupos e a busca. Cada grupo abre na própria página, em tela cheia como o palco, sem seta: a alça na borda, como no palco, ou o voltar do Android, traz o menu de novo. Os itens abaixo seguem a ordem da tela. Nos cartões, o ⓘ ao lado do título explica o que eles fazem. Com o modo essencial, alguns itens e opções somem até você religar.",
        ],
        "text_en": [
            "The menu's Settings tab shows only the groups and the search. Each group opens on its own page, full screen like the stage, with no arrow: the handle on the edge, as on the stage, or Android's back, brings the menu again. The items below follow the order on screen. On the cards, the ⓘ next to the title explains what they do. With essential mode, some items and options disappear until you turn them back on.",
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
            sub("ajustes-pdf", "PDF", "PDF",
                ["Uma música em PDF mostra as páginas no palco no lugar da letra.",
                 "- **Inverter as cores do PDF:** vira o branco do papel em preto, para um palco escuro. Vale para todas as músicas em PDF. Para uma música só, use o interruptor do cartão do PDF, no editor."],
                ["A song that is a PDF shows its pages on stage in place of the lyrics.",
                 "- **Invert the PDF's colors:** turns the paper's white into black, for a dark stage. It applies to every PDF song. For one song only, use the switch on the PDF card, in the editor."],
                level=4),
            sub("ajustes-pagina", "Página", "Page",
                ["- **Como avançar na tela:** Virar página mostra uma página por vez, em uma ou duas colunas. Rolar mostra a letra numa coluna só.",
                 "- **Dividir em colunas:** só em Virar página. Automático divide em 2 colunas quando a letra não cabe numa página e as frases são curtas. Também dá para escolher Nunca ou Sempre. O pedal vira as duas colunas juntas.",
                 "- **Velocidade da rolagem automática:** só em Rolar."],
                ["- **Moving on screen:** Turn pages shows one page at a time, in one or two columns. Scroll shows the lyrics in a single column.",
                 "- **Split into columns:** only in Turn pages. Automatic splits into 2 columns when the lyrics don't fit one page and the lines are short. You can also pick Never or Always. The pedal turns both columns together.",
                 "- **Auto-scroll speed:** only in Scroll."],
                [Fig(("tablet_5_ajustes_pagina", "celular_5_ajustes_pagina", "Modo, em Virar página", "Mode, in Turn pages")),
                 Fig(("tablet_5_ajustes_rolar", "celular_5_ajustes_rolar", "Modo, em Rolar", "Mode, in Scroll"))], level=4),
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
                [Fig(("tablet_5_ajustes_tela", "celular_5_ajustes_tela", "Durante o show", "During the show"))], level=4),
            sub("ajustes-metronomo", "Metrônomo e player", "Metronome and player",
                ["Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado.",
                 "- **Mostrar no palco:** desligado, o ícone do metrônomo some do palco e as outras opções deste cartão somem também.",
                 "- **Contagem 1 … 2 … 1 2 3 4 antes:** ao começar, dois tempos lentos e depois um compasso, para a contagem não ser confundida com o começo da música. O palco mostra os números bem grandes, numa cor própria, e nas pausas nem o som nem o brilho aparecem.",
                 "- **Som:** o clique do metrônomo.",
                 "- **Volume:** o volume do clique, de 0 a 100%, de 10 em 10%, no aparelho e na saída USB. Só aparece com o Som ligado. Ele é guardado e vai no backup, com os outros ajustes.",
                 "- **Testar o metrônomo:** toca o clique a 100 BPM, sem contagem, com a saída e o volume escolhidos, para você ouvir sem ir ao palco. Com a saída na interface USB, o teste liga o player sozinho, como o palco faz, e o clique começa assim que a interface fica pronta. Toque em Parar o teste, ou saia da tela, para parar. Mudar a saída com ele tocando para o teste. Se mudar o volume, a mudança vale no próximo teste.",
                 "- **Saída do som:** com o player ligado, Aparelho é o fone, se houver um conectado. Sem fone, é o Bluetooth conectado, e sem os dois, o alto-falante. As saídas da interface USB deixam o baterista ouvir pela mesa. Para elas aparecerem, espete a interface: o app lê as saídas na hora. Pela rota do Android, alguns aparelhos mandam o clique para a USB junto com a música. Neles, Aparelho fica desabilitado.",
                 "- **Brilho na borda da tela e Cor do brilho:** a batida pisca na borda, sem som."],
                ["When essential mode turns one of them off, the item is named after the one left on.",
                 "- **Show on stage:** off, the metronome icon leaves the stage and the other options on this card go away too.",
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
                [Fig(("jornada_faixa_1_ajustes_player_tablet", "jornada_faixa_1_ajustes_player", "Saída USB e para tocar sem falhas", "USB output and playing without failures")),
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
                ["Aperte um botão do pedal: ele aparece aqui e você escolhe o que ele faz. As opções são Avançar a página, Voltar a página, Próxima música, Música anterior, Play/pause do backing track, Abrir o setlist e Nada. Abrir o setlist abre o menu no [setlist do palco](#palco-setlist).",
                 "Em Botões do pedal fica cada botão já apertado, com o que faz. O que não foi mudado aparece como padrão: setas para baixo e para a direita, Page Down, Espaço e Enter avançam; setas para cima e para a esquerda, Page Up e Backspace voltam. As teclas de mídia Próxima, Anterior e Play/Pause também já vêm com função. Com mais de um pedal, os botões ficam separados por pedal, com o conectado em cima.",
                 "Para tirar um botão da lista, toque no círculo com traço ao lado dele e confirme em Esquecer. O botão volta a fazer o padrão e aparece de novo quando você apertar."],
                ["Press a pedal button: it shows here and you pick what it does. The options are Next page, Previous page, Next song, Previous song, Play/pause backing track, Open the setlist and Nothing. Open the setlist opens the menu on the [stage's setlist](#palco-setlist).",
                 "Under Pedal buttons you see each button already pressed, with what it does. One you didn't change shows as default: down and right arrows, Page Down, Space and Enter go forward; up and left arrows, Page Up and Backspace go back. The media keys Next, Previous and Play/Pause also come with a function. With more than one pedal, the buttons are grouped by pedal, the connected one on top.",
                 "To take a button off the list, tap the circle with a dash next to it and confirm with Forget. The button goes back to its default and shows again when you press it."],
                [Fig(("tablet_5_ajustes_pedal", "celular_5_ajustes_pedal", "Botões do pedal", "Pedal buttons"))], level=4),
            sub("ajustes-midi-saida", "Saída MIDI", "MIDI out",
                ["Escolha para onde vai o [MIDI das músicas](#editor-midi): um aparelho USB ou Bluetooth. O Bluetooth aparece na lista depois de conectado, em [Conectar pedal ou aparelho MIDI](#ajustes-pedal-conectar); se nenhum aparecer, a tela mostra onde conectar. Se o aparelho escolhido não estiver ligado, ele continua na lista como desconectado, e o MIDI volta a ir quando ele voltar.",
                 "Mesa ou placa de som na USB não aparece, porque abrir o MIDI dela tiraria a interface de áudio do player."],
                ["Pick where the [songs' MIDI](#editor-midi) goes: a USB or Bluetooth device. Bluetooth shows up in the list once it's connected, in [Connect a pedal or MIDI device](#ajustes-pedal-conectar); if none shows up, the screen says where to connect one. If the chosen device isn't on, it stays in the list as not connected, and MIDI goes there again when it comes back.",
                 "A mixer or sound card on USB doesn't show up, because opening its MIDI would take the audio interface from the player."],
                [Fig(("tablet_5_ajustes_midi_saida", "celular_5_ajustes_midi_saida", "Saída MIDI", "MIDI out"))], level=4),
            sub("ajustes-sites", "Sites de cifra", "Chord sites",
                ["Em Só letra, o item se chama Sites de letra. Em Automático ele se chama Sites de importação e mostra as duas listas, a de cifra e a de letra. O site marcado é onde o importador abre, e marcar outro leva ele para o topo. Adicionar site põe um endereço novo, e o × tira da lista (ele só aparece quando a lista tem mais de um site). Em qualquer perfil há também o cartão Sites de partituras em PDF, que funciona do mesmo jeito."],
                ["In Lyrics only, the item is called Lyrics sites. In Automatic it is called Import sites and shows both lists, chords and lyrics. The marked site is where the importer opens, and marking another moves it to the top. Add site puts in a new address, and the × takes one off the list (it only shows when the list has more than one site). In every profile there is also the PDF score sites card, which works the same way."],
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
                 "Recebeu um .stagebook numa conversa, num download ou no gerenciador de arquivos? Toque nele e escolha o Stagebook. O app abre direto no Abrir arquivo. O arquivo passa pelas mesmas verificações do Abrir arquivo. Um arquivo que não é do Stagebook não abre, e o app avisa. Com o palco na tela (ou a edição de uma música aberta), o arquivo espera: abre quando você abrir o menu ou sair da edição.",
                 "A cópia que o próprio Android faz na sua conta leva as músicas, os setlists, os ajustes do palco e a língua do app. Backing tracks, PDFs, pedais, perfis da interface USB e banda não vão. Para levar tudo a outro aparelho, use o Salvar arquivo. A música que chegou sem o áudio guarda o nome dele. Depois de abrir o arquivo, uma janela lista essas músicas com o nome de cada áudio. Na edição da música, o campo Backing track fica em cor de alerta, com esse nome e o pedido para anexar de novo. No palco, um alerta aparece no lugar do play."],
                ["**Save file** writes a .stagebook with what's checked: Songs and setlists, with each song's zoom, Backing tracks and Settings. It works as a backup, to change devices or to send to someone. To send only the lyrics, uncheck the backing tracks, which make the file large. The file then doesn't carry the audio names either, and whoever gets it sees no missing-audio alert. The same goes for opening a file with the backing tracks unchecked.",
                 "**Open file** shows what the file has for you to check. Join with mine brings only what you don't have. Replace mine swaps your songs and setlists for the file's. The file's settings replace the ones here and, on another device, the band has to be allowed again. The app tells you when that's the case.",
                 "Got a .stagebook in a chat, a download or the file manager? Tap it and choose Stagebook. The app opens right on Open file. The file goes through the same checks as Open file. A file that isn't Stagebook's doesn't open, and the app says so. With the stage on screen (or a song editor open), the file waits: it opens when you open the menu or leave the editor.",
                 "The copy Android itself keeps in your account carries the songs, the setlists, the stage settings and the app's language. Backing tracks, PDFs, pedals, USB interface profiles and the band don't go. To take everything to another device, use Save file. A song that arrived without its audio keeps the audio's name. After the file opens, a window lists those songs with each audio's name. In the song editor, the Backing track field turns to the alert color, with that name and the request to attach it again. On stage, an alert shows where play would be."],
                [Fig(("tablet_5_backup_restaurar", "celular_5_backup_restaurar", "Abrindo um arquivo", "Opening a file"))], level=4),
            sub("ajustes-apagar", "Apagar tudo", "Erase everything",
                ["Deixa o app como novo: apaga músicas, setlists, backing tracks, ajustes e a banda deste aparelho, sem volta. A janela oferece Fazer um backup antes e pede para digitar um código de confirmação. Se o Android guarda uma cópia do app na sua conta, as músicas e os setlists podem voltar quando você instalar o app de novo."],
                ["Leaves the app like new: it erases songs, setlists, backing tracks, settings and the band from this device, for good. The window offers Back up first and asks you to type a confirmation code. If Android keeps a copy of the app in your account, the songs and setlists can come back when you install the app again."],
                [Fig(("tablet_5_apagar_tudo", "celular_5_apagar_tudo", "Apagar tudo", "Erase everything"))], level=4),
            sub("ajustes-banda", "Banda e transmissão", "Band and broadcast",
                ["- **Este aparelho:** o nome que a banda vê. Mudar o nome troca.",
                 "- **Na rede agora:** quem está no mesmo Wi-Fi e ainda não é da banda. Pedir para entrar fica ao lado de cada um.",
                 "- **Banda:** quem já foi autorizado. A bolinha acesa mostra quem está neste Wi-Fi agora, e o círculo vazio, quem está fora dele. Um aparelho em outra versão do app aparece marcado. A lixeira remove, e para voltar é preciso pedir para entrar de novo. Convidar pela internet manda um link para quem está longe. Veja [Entrar na banda](#banda-entrar).",
                 "- **Avisar a música tocando:** Enviar a música atual para OBS e outros avisa na rede qual música está no palco, com artista, tom, BPM, o nome do setlist e a posição nele. A letra nunca vai. Vem desligado. Veja o [guia do Now Playing](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.pt-BR.md).",
                 "O uso do dia a dia está em [Banda](#banda)."],
                ["- **This device:** the name the band sees. Change name changes it.",
                 "- **On the network now:** who is on the same Wi-Fi and isn't in the band yet. Ask to join sits next to each one.",
                 "- **Band:** who was already allowed. The lit dot shows who is on this Wi-Fi now, and the empty ring who is off it. A device on another app version shows up marked. The trash can removes them, and to come back they have to ask to join again. Invite over the internet sends a link to someone far away. See [Joining the band](#banda-entrar).",
                 "- **Tell what's playing:** Send the current song to OBS and others tells the network which song is on stage, with artist, key, BPM, the setlist's name and the position in it. The lyrics never go out. It comes off. See the [Now Playing guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/now-playing.md).",
                 "Day-to-day use is in [Band](#banda)."],
                [Fig(("jornada_banda_1_ajustes_tablet", "jornada_banda_1_ajustes", "Banda e computador", "Band and computer"))]),
            sub("ajustes-computador", "No navegador", "In the browser",
                ["Ligar o editor liga o editor web, e Computadores autorizados lista quem já entrou. Veja [Editor no computador](#editor-web)."],
                ["Turn on the editor turns the web editor on, and Authorized computers lists who already came in. See [Editor on the computer](#editor-web)."]),
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
                ["- No alto, o logo, o nome e o que o app faz.",
                 "- **Ajuda:** Abrir a ajuda traz para esta página, que fica em [stagebook.app.br/ajuda](https://stagebook.app.br/ajuda). Precisa de internet.",
                 "- **Idioma:** Automático segue o aparelho, ou escolha português ou inglês. Para ajudar a traduzir, siga o [guia de tradução](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.pt-BR.md).",
                 "- **Atualizar o app:** a versão instalada e Procurar atualização. Veja [Atualizar](#atualizar).",
                 "- **Privacidade, Código disponível e Apoie o Stagebook:** como seus dados são tratados, o código do app e como apoiar."],
                ["- At the top, the logo, the name and what the app does.",
                 "- **Help:** Open the help brings you to this page, at [stagebook.app.br/help](https://stagebook.app.br/help). It needs the internet.",
                 "- **Language:** Automatic follows the device, or pick Portuguese or English. To help translate, follow the [translation guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.md).",
                 "- **Update the app:** the installed version and Check for updates. See [Updating](#atualizar).",
                 "- **Privacy, Source available and Support Stagebook:** how your data is handled, the app's code and how to support it."],
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
                ["- No aparelho, em Ajustes → Banda e computador, no cartão No navegador, ligue Ligar o editor. Aparece um endereço como http://192.168.0.10:8090.",
                 "- Abra esse endereço no navegador do computador, no mesmo Wi-Fi. Rede de convidados costuma bloquear a conversa entre aparelhos.",
                 "- Toque em Pedir para entrar. O aparelho mostra um código de 4 números. Autorize só se for o mesmo da página. O pedido aparece numa janela; com o palco na tela ele espera, e a alça do menu mostra um número vermelho: abra o menu para a janela aparecer. Quem pede para entrar na banda com o seu palco na tela recebe o aviso de pedir de novo depois; com o menu aberto, ou em Ajustes, o pedido aparece na hora.",
                 "- O computador fica guardado em Computadores autorizados, e dali dá para tirá-lo a qualquer hora. O computador autorizado não pede de novo: o acesso dura 90 dias e cada vez que você usa o editor ele recomeça. Para cortar o acesso antes, remova o computador aqui. O mesmo vale para o Claude ou outro assistente ligado pelo MCP: depois de autorizado, conectar de novo não pede autorização."],
                ["- On the device, in Settings → Band and computer, on the In the browser card, turn on Turn on the editor. An address shows up, like http://192.168.0.10:8090.",
                 "- Open that address in the computer's browser, on the same Wi-Fi. Guest networks usually block devices from talking to each other.",
                 "- Tap Ask to come in. The device shows a 4-digit code. Allow it only if it matches the page. The request shows in a window; with the stage on screen it waits, and the menu's handle shows a red number: open the menu for the window to appear. Someone asking to join the band while your stage is on screen is told to ask again later; with the menu open, or in Settings, the request shows right away.",
                 "- The computer is kept under Authorized computers, and you can remove it from there at any time. An authorized computer isn't asked again: access lasts 90 days and each time you use the editor it starts over. To cut access sooner, remove the computer here. The same goes for Claude or another assistant connected by MCP: once authorized, connecting again doesn't ask."],
                [Fig(("tablet_5_ajustes_editor_web", "celular_5_ajustes_editor_web", "Ligando o editor no aparelho", "Turning on the editor on the device")),
                 Fig(("editor_web_1_pedido", None, "Pedindo para entrar", "Asking to come in"))]),
            sub("web-musicas", "Músicas", "Songs",
                ["- À esquerda ficam a busca, o filtro (tocadas nos últimos 30 dias, nunca tocadas ou uma tag), a ordem (A a Z, mais ou menos tocadas) e a lista. Clique numa música para abrir, ou em Nova música para começar uma.",
                 "- Dá para editar nome, artista, ano, BPM, tags e a letra com as cifras. Separe as tags com vírgula; as que você já usa aparecem como sugestão.",
                 "- Os botões − e + mudam o tom e o capotraste, como no app. A letra só muda de vez quando você salva.",
                 "- Para destacar uma frase, selecione-a e clique em Marca-texto.",
                 "- As listas de músicas do computador (músicas, setlist, as para acrescentar e as recebidas) mostram o tom, o BPM, o capo e a faixa como no aparelho: título, artista e, à direita, \"G · 120 bpm · capo 2\" com a onda de áudio quando a música tem backing track. O ano e as tags ficam no formulário da música.",
                 "- Em MIDI da música ficam as mensagens Ao entrar e Ao sair, como no [editor do app](#editor-midi).",
                 "- Com a música já salva, dá para enviar a backing track em OGG, MP3 ou WAV.",
                 "- Ctrl+S salva. Saindo com algo sem salvar, a página avisa antes. Se não salvar, aparece \"O setlist não foi salvo. Tente de novo.\"; salvar duas vezes seguidas não cria dois, e intervalos que não cabem no setlist recusam o salvamento sem mudar nada."],
                ["- On the left are the search, the filter (played in the last 30 days, never played or a tag), the order (A to Z, most or least played) and the list. Click a song to open it, or New song to start one.",
                 "- You can edit name, artist, year, BPM, tags and the lyrics with chords. Separate tags with commas; the ones you already use show up as suggestions.",
                 "- The − and + buttons change the key and the capo, as in the app. The song only changes for good when you save.",
                 "- To highlight a phrase, select it and click Highlighter.",
                 "- The computer's song lists (songs, the setlist, the ones to add and the received ones) show the key, BPM, capo and backing track like the device does: title, artist and, on the right, \"G · 120 bpm · capo 2\" with the audio wave when the song has a backing track. The year and tags stay in the song's form.",
                 "- Song MIDI holds the messages On entering and On leaving, as in the [app's editor](#editor-midi).",
                 "- Once the song is saved, you can send its backing track as OGG, MP3 or WAV.",
                 "- Ctrl+S saves. If you leave with something unsaved, the page warns you first. If it does not save, you see \"The setlist was not saved. Try again.\"; saving twice in a row does not create two, and breaks that do not fit the setlist refuse the save without changing anything."],
                [Fig(("editor_web_2_musica", None, "Editando uma música", "Editing a song"))]),
            sub("web-setlists", "Setlists", "Setlists",
                ["- Cada setlist tem o campo Data do show, ao lado do nome: o calendário do navegador escolhe o dia, e apagar o campo tira a data. Um setlist novo já vem com o dia de hoje.",
                 "- A busca acha o setlist pelo nome. A lista mostra os recentes primeiro, pela data do show, como no aparelho.",
                 "- Dentro do setlist, a busca logo abaixo acrescenta músicas e as setas mudam a posição de cada uma.",
                 "- O [intervalo](#setlists-intervalo) aparece como uma linha tracejada entre duas músicas. As setas sobem e descem o intervalo como uma música, e o × tira. Ele nunca fica no começo nem no fim da lista.",
                 "- O botão + Intervalo põe um intervalo no fim da lista, entre as duas últimas músicas, e você o move. Com menos de 2 músicas ele fica desativado. Embaixo, a linha mostra quantas músicas e quantos intervalos o setlist tem.",
                 "- Ao mover ou tirar uma música, os intervalos ficam entre as mesmas músicas, como no aparelho. Renomear o setlist no computador mantém os intervalos feitos no aparelho."],
                ["- Each setlist has the Show date field next to the name: the browser's calendar picks the day, and clearing the field takes the date off. A new setlist starts with today.",
                 "- The search finds a setlist by name. The list shows the newest first, by show date, as on the device.",
                 "- Inside a setlist, the search below adds songs and the arrows move each one.",
                 "- The [break](#setlists-intervalo) shows as a dashed line between two songs. The arrows move the break up and down like a song, and × removes it. It is never at the start or the end of the list.",
                 "- The + Break button puts a break at the end of the list, between the last two songs, and you move it. With fewer than 2 songs it is off. Below, a line tells how many songs and breaks the setlist has.",
                 "- When you move or remove a song, the breaks stay between the same songs, as on the device. Renaming the setlist on the computer keeps the breaks made on the device."],
                [Fig(("editor_web_3_setlist", None, "Setlist com a busca e um intervalo", "Setlist with the search and a break"))]),
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
                [Fig(("editor_web_4_importar", None, "Importar no computador", "Importing on the computer")),
                 Fig(("editor_web_5_recebido", None, "Conferindo um setlist recebido", "Checking a received setlist"))]),
            sub("web-ligado", "Enquanto está ligado", "While it's on",
                ["O ícone do computador aparece ao lado do título das abas. Tocando nele você vê o endereço e pode fechar o editor. Ele começa desligado toda vez que o app abre e desliga sozinho depois de 5 minutos sem nenhuma página aberta no computador, ou quando você fecha o app. Uma página aberta no navegador mantém ele ligado. Com o app fora da tela o editor não responde, e volta quando o app volta."],
                ["The computer icon shows next to the tabs' titles. Tap it to see the address and close the editor. It starts off every time the app opens, and turns off by itself after 5 minutes with no page open on the computer, or when you close the app. A page left open in the browser keeps it on. With the app out of sight the editor doesn't answer, and it comes back when the app does."]),
        ],
    },
    {
        "id": "assistentes", "pt": "Assistentes de IA", "en": "AI assistants",
        "text_pt": [
            "Com o servidor MCP do Stagebook, assistentes de IA como o Claude, o Cursor ou o Gemini mexem na sua biblioteca pelo mesmo editor. Você pede em português e o assistente faz no aparelho, sem copiar e colar.",
            "### O que você precisa",
            "- O [Node.js](https://nodejs.org) 20 ou mais novo no computador. O servidor está no npm como `stagebook-mcp`, e o assistente o baixa e atualiza sozinho.",
            "- O aparelho e o computador no mesmo Wi-Fi, com o Stagebook aberto e o editor ligado em Ajustes → Banda e computador → No navegador.",
            "### Ligar no assistente",
            "- **Claude Code:** rode `claude mcp add stagebook -- npx -y stagebook-mcp`.",
            "- **Claude Desktop, Cursor, Windsurf e Gemini CLI:** na configuração de MCP, acrescente `{ \"mcpServers\": { \"stagebook\": { \"command\": \"npx\", \"args\": [\"-y\", \"stagebook-mcp\"] } } }`.",
            "- **VS Code e OpenAI Codex CLI:** o formato muda um pouco. Veja no [guia do servidor](https://github.com/julianoAffonso/stagebook/blob/main/mcp/README.pt-BR.md), que também diz onde fica o arquivo de cada assistente.",
            "- O app do ChatGPT só alcança servidores pela internet, então não chega ao aparelho no seu Wi-Fi.",
            "### Primeira conexão",
            "- Peça ao assistente para conectar no Stagebook. Ele procura na rede, ou você passa o endereço que aparece no aparelho.",
            "- O aparelho mostra um pedido com um código. Se bater, toque em Autorizar. Da próxima vez ele entra sem perguntar.",
            "- A autorização dura 90 dias sem uso, e cada uso recomeça a contagem. Computadores autorizados remove a qualquer hora.",
            "### O que dá para pedir",
            "- **Músicas:** \"acha as músicas em Sol\", \"passa esta meio tom abaixo\", \"coloca capo na 2\", \"confere as cifras desta\".",
            "- **Importar:** \"importa esta cifra\" colando o texto ou o endereço da página.",
            "- **Setlists:** \"monta um setlist para sábado com 15 músicas\", \"acha o setlist do casamento\", \"põe esta música depois do intervalo\".",
            "- **Backing tracks:** \"usa este arquivo como faixa da música\", com o caminho completo do arquivo.",
            "- **Receber:** \"recebe o setlist destas 3 palavras\", \"o que a banda mandou?\".",
            "- **Backup:** \"o que tem neste arquivo .stagebook?\" e \"junta o que falta\".",
            "### O que pede confirmação",
            "Apagar, tirar uma faixa, esvaziar um setlist, substituir a biblioteca e restaurar os ajustes só acontecem depois que o assistente pergunta a você.",
            "### Se não conectar",
            "- **Não acha o aparelho:** confira se o Stagebook está aberto, se o editor está ligado e se os dois estão no mesmo Wi-Fi. O editor desliga sozinho sem uso. Passe o endereço ao assistente.",
            "- **No Mac:** em Ajustes do Sistema → Privacidade e Segurança → Rede Local, ligue o app que roda o assistente e reabra esse app.",
            "- **A chave não vale mais:** ela foi removida em Computadores autorizados, ou ficou 90 dias sem uso. Conecte de novo.",
            "- **Já tem um pedido na tela:** responda no aparelho. Depois de um Recusar, ele espera 5 minutos para aceitar outro.",
            "- **Uma parte não responde:** receber por código precisa da internet ligada, e as funções de banda precisam da banda ligada, nos Ajustes do aparelho.",
            "### Privacidade",
            "O servidor só conversa com o aparelho. O assistente vê o que as ferramentas devolvem, como títulos, artistas, acordes e, quando ele pede, a letra, e manda isso ao provedor de IA dele, como o resto da conversa.",
        ],
        "text_en": [
            "With Stagebook's MCP server, AI assistants like Claude, Cursor or Gemini work on your library through the same editor. You ask in plain words and the assistant does it on the device, with no copying and pasting.",
            "### What you need",
            "- [Node.js](https://nodejs.org) 20 or newer on the computer. The server is on npm as `stagebook-mcp`, and the assistant downloads and updates it by itself.",
            "- The device and the computer on the same Wi-Fi, with Stagebook open and the editor on in Settings → Band and computer → In the browser.",
            "### Connect your assistant",
            "- **Claude Code:** run `claude mcp add stagebook -- npx -y stagebook-mcp`.",
            "- **Claude Desktop, Cursor, Windsurf and Gemini CLI:** in the MCP settings, add `{ \"mcpServers\": { \"stagebook\": { \"command\": \"npx\", \"args\": [\"-y\", \"stagebook-mcp\"] } } }`.",
            "- **VS Code and OpenAI Codex CLI:** the format changes a little. See the [server guide](https://github.com/julianoAffonso/stagebook/blob/main/mcp/README.md), which also says where each assistant keeps its file.",
            "- The ChatGPT app only reaches servers over the internet, so it can't get to the device on your Wi-Fi.",
            "### First connection",
            "- Ask the assistant to connect to Stagebook. It searches the network, or you give it the address shown on the device.",
            "- The device shows a request with a code. If it matches, tap Allow. Next time it comes in without asking.",
            "- The authorization lasts 90 days without use, and each use starts the count again. Authorized computers removes it at any time.",
            "### What you can ask",
            "- **Songs:** \"find the songs in G\", \"move this one down a half tone\", \"put a capo on 2\", \"check this one's chords\".",
            "- **Importing:** \"import these chords\", pasting the text or the page address.",
            "- **Setlists:** \"build a setlist for Saturday with 15 songs\", \"find the wedding setlist\", \"move this song after the break\".",
            "- **Backing tracks:** \"use this file as the song's track\", with the file's full path.",
            "- **Receiving:** \"receive the setlist with these 3 words\", \"what did the band send?\".",
            "- **Backup:** \"what's in this .stagebook file?\" and \"merge what's missing\".",
            "### What asks you first",
            "Deleting, removing a track, emptying a setlist, replacing the library and restoring settings only happen after the assistant asks you.",
            "### If it doesn't connect",
            "- **It can't find the device:** check that Stagebook is open, the editor is on and both are on the same Wi-Fi. The editor turns off when unused. Give the assistant the address.",
            "- **On a Mac:** in System Settings → Privacy & Security → Local Network, turn on the app running the assistant and reopen that app.",
            "- **The key no longer works:** it was removed from Authorized computers, or went 90 days unused. Connect again.",
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
            "Quando sai uma versão nova, o app avisa e mostra o que mudou. Com o palco na tela ele não interrompe: o aviso aparece quando você abrir o menu. Atualizar baixa a versão e instala. Na primeira vez o Android pede uma confirmação. Daí em diante, no Android 12 ou mais novo, o app se atualiza sem pedir de novo e reinicia para terminar. No Android 11 ou mais antigo o Android pede a confirmação a cada versão. Agora não deixa para o dia seguinte. Se só fechar a janela, o aviso volta na próxima vez que abrir o app. Se você pulou versões, aparece o que mudou em cada uma. A mesma lista fica em Ajustes → Sobre → Atualizar o app. A banda precisa estar toda na mesma versão para trocar setlists e acompanhar; um aparelho em outra versão aparece marcado em Ajustes → Banda e transmissão.",
            "Enquanto o Android instala, o botão Instalar fica desligado. Ele volta quando o Android responde ou quando você volta para o app. Em aparelhos Samsung, o Bloqueador automático pode impedir a instalação, e ele volta a ligar sozinho depois de um tempo. Quando a versão nova termina de baixar, a janela mostra Abrir o Bloqueador automático: desligue e volte para instalar. O botão aparece também em Ajustes → Sobre → Atualizar o app.",
            "Suas músicas e ajustes ficam no aparelho. O que vai para a banda ou pela internet vai cifrado. Não tem conta, propaganda nem rastreio.",
            "Ao terminar de instalar, o Android fecha o app. Ele volta aberto na tela onde você estava; se o menu estava aberto, volta no palco com o menu aberto. Onde o Android não deixa o app abrir sozinho, aparece a notificação Stagebook atualizado: toque nela e o app volta de onde parou.",
        ],
        "text_en": [
            "When a new version is out, the app tells you and shows what changed. With the stage on screen it does not interrupt: the notice shows when you open the menu. Update downloads the version and installs it. The first time Android asks you to confirm. From then on, on Android 12 or newer, the app updates without asking again and restarts to finish. On Android 11 or older, Android asks to confirm every version. Not now leaves it for the next day. If you just close the window, the notice comes back the next time you open the app. If you skipped versions, you see what changed in each one. The same list is in Settings → About → Update the app. The whole band needs the same version to exchange setlists and follow; a device on another one shows up marked in Settings → Band and broadcast.",
            "While Android installs, the Install button stays off. It comes back when Android answers or when you come back to the app. On Samsung devices, Auto Blocker can stop the install, and it turns itself back on after a while. When the new version finishes downloading, the window shows Open Auto Blocker: turn it off and come back to install. The button also shows in Settings → About → Update the app.",
            "Your songs and settings stay on the device. What goes to the band or over the internet is encrypted. There's no account, no ads and no tracking.",
            "When the install finishes, Android closes the app. It comes back open on the screen you were on; if the menu was open, on the stage with the menu open. Where Android doesn't let the app open by itself, the notification Stagebook updated shows up: tap it and the app goes back where you were.",
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
                 "- **Isto veio de um Stagebook mais novo:** [atualize o app](#atualizar) para receber."],
                ["- **The QR came in incomplete:** move closer and scan again.",
                 "- **This QR isn't from Stagebook:** point at the setlist QR or the 3 words QR.",
                 "- **Nothing came with these words:** check them. They last 12 hours after sending.",
                 "- **This came from a newer Stagebook:** [update the app](#atualizar) to receive it."]),
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
                ["- **Não achei o Stagebook:** confira se o app está aberto e o editor ligado. Ele desliga sozinho depois de 5 minutos sem nenhuma página aberta.",
                 "- **A página não abre:** os dois precisam estar no mesmo Wi-Fi, e rede de convidados costuma bloquear."],
                ["- **Can't reach Stagebook:** check that the app is open and the editor is on. It turns off by itself after 5 minutes with no page open.",
                 "- **The page doesn't open:** both must be on the same Wi-Fi, and guest networks usually block it."]),
        ],
    },
]


# ---- Settings by task (user, 14/11) -------------------------------------------------------------------------------
# Seven groups in the order of use, then About. The topics keep their anchors (links from the other chapters still
# work); here they are moved under their group, with the path of each option.
def _regroup_settings():
    chapter = next(c for c in SECTIONS if c["id"] == "ajustes")
    by = {x["id"]: x for x in chapter["subs"]}

    def put(sid, pt=None, en=None, level=None, head_pt=None, head_en=None, drop_pt=(), drop_en=()):
        x = by[sid]
        if pt: x["pt"] = pt
        if en: x["en"] = en
        if level: x["level"] = level
        x["text_pt"] = ([head_pt] if head_pt else []) + [t for t in x["text_pt"] if t not in drop_pt]
        x["text_en"] = ([head_en] if head_en else []) + [t for t in x["text_en"] if t not in drop_en]

    def head(sid, pt, en, text_pt, text_en, figs=()):
        by[sid] = sub(sid, pt, en, text_pt, text_en, figs)

    head("ajustes-busca", "Buscar nos ajustes", "Search the settings",
         ["No topo da aba Ajustes do menu fica o campo Buscar nos ajustes, como nos ajustes do Android. Digite o nome do item ou uma palavra parecida (o campo vazio sugere “Ex.: midi, tela, pedal”). A busca também olha o texto das explicações, e o resultado diz “na explicação” quando só ele acerta. Cada resultado mostra o item, o caminho (grupo › cartão) e uma linha de explicação. Tocar abre a página do grupo, rola até o cartão e o destaca por um instante. Quem está desligado agora (pelo modo essencial, pelo perfil Só letra, pelo player desligado, pela banda ou pela internet) também aparece, esmaecido e com o motivo. Tocar nele abre o lugar que liga, sem ligar nada por você. O X limpa a busca, e Voltar fecha a busca antes de sair."],
         ["At the top of the menu's Settings tab there is the Search Settings field, as in Android's settings. Type the item's name or a similar word (the empty field suggests “e.g. midi, screen, pedal”). Search also reads the explanation texts, and the result says “in the explanation” when only that matches. Each result shows the item, the path (group › card) and a line of explanation. Tapping opens the group's page, scrolls to the card and highlights it for a moment. What is turned off right now (by essential mode, by the Lyrics only profile, by the player, the band or the internet being off) shows up too, dimmed and with the reason. Tapping it opens the place that turns it on, without turning anything on for you. The X clears the search, and Back closes the search before leaving."],
         [Fig(("tablet_5_ajustes_busca", "celular_5_ajustes_busca", "A busca nos ajustes", "Searching the settings"))])
    put("ajustes-palco", "Letra e cifra", "Lyrics and chords", 3,
        head_pt="Ajustes → Letra e cifra. A amostra no topo da tela fica presa: ao mudar o tamanho, os acordes ou os títulos você vê o efeito sem rolar. Tudo aqui vale para todas as músicas.",
        head_en="Settings → Lyrics and chords. The sample at the top of the screen stays in place: when you change the size, chords or titles you see the effect without scrolling. Everything here applies to all songs.",
        drop_pt=("Tudo aqui vale para todas as músicas. A amostra no topo da tela fica presa e mostra cada mudança na hora.",),
        drop_en=("Everything here applies to all songs. The sample at the top of the screen stays in place and shows each change right away.",))
    head("ajustes-virar", "Virar a página", "Turn the page",
         ["Ajustes → Virar a página. Reúne como a letra avança no palco (Modo), o Pedal e MIDI, a Saída MIDI e o que o aparelho faz Durante o show. O pedal fica aqui, junto da página."],
         ["Settings → Turn the page. It gathers how the lyrics move on stage (Mode), Pedal and MIDI, MIDI out and what the device does During the show. The pedal lives here, with the page."],
         [Fig(("tablet_5_ajustes_virar", "celular_5_ajustes_virar", "Virar a página", "Turn the page"))])
    put("ajustes-pagina", "Modo", "Mode", 4, head_pt="Ajustes → Virar a página → Modo.", head_en="Settings → Turn the page → Mode.")
    put("ajustes-pedal", "Pedal e MIDI", "Pedal and MIDI", 4,
        head_pt="Ajustes → Virar a página → Pedal e MIDI.", head_en="Settings → Turn the page → Pedal and MIDI.")
    put("ajustes-pedal-conectar", level=4)
    put("ajustes-pedal-botoes", level=4)
    put("ajustes-midi-saida", "Saída MIDI", "MIDI out", 4,
        head_pt="Ajustes → Virar a página → Saída MIDI. Some com o modo essencial.", head_en="Settings → Turn the page → MIDI out. It leaves with essential mode.")
    put("ajustes-tela", "Durante o show", "During the show", 4,
        head_pt="Ajustes → Virar a página → Durante o show.", head_en="Settings → Turn the page → During the show.")
    t = by["ajustes-tela"]
    t["text_pt"] += ["**Parar volta ao início:** com o player ligado, desligado faz o botão da faixa pausar, e a música continua de onde parou.",
                     "**Tela apaga depois de 10 minutos:** fora do palco. Fica aqui, junto da tela ligada. Sem a permissão do Android de Aparelho → Avançado, a chave aparece mas não muda."]
    t["text_en"] += ["**Stop goes back to the start:** with the player on, off makes the track button pause, and the song goes on from where it stopped.",
                     "**Screen off after 10 minutes:** outside the stage. It sits here, next to keeping the screen on. Without Android's permission from Device → Advanced, the switch shows but doesn't change."]
    head("ajustes-acordes-grupo", "Acordes", "Chords",
         ["Ajustes → Acordes. O instrumento, o diagrama ao tocar, o som e a lista de acordes. O grupo some no perfil Só letra, que não mostra acordes."],
         ["Settings → Chords. The instrument, the diagram on tap, the sound and the chord list. The group leaves with the Lyrics only profile, which shows no chords."])
    put("ajustes-diagramas", "Diagramas dos acordes", "Chord diagrams", 4,
        drop_pt=("Este cartão só aparece com Letra e cifra.",), drop_en=("This card shows only with Lyrics and chords.",))
    put("ajustes-metronomo", "Player e metrônomo", "Player and metronome", 3,
        head_pt="Ajustes → Player e metrônomo. O nome não muda, mesmo com uma das partes desligada pelo modo essencial.",
        head_en="Settings → Player and metronome. The name doesn't change, even with one of the parts turned off by essential mode.",
        drop_pt=("Com o modo essencial desligando um dos dois, o item leva só o nome do que ficou ligado.",),
        drop_en=("When essential mode turns one of them off, the item is named after the one left on.",))
    p4 = by["ajustes-player"]
    p4["pt"], p4["en"], p4["level"] = "Saída USB e para tocar sem falhas", "USB output and playing without failures", 4
    p4["text_pt"] = [x for x in p4["text_pt"] if not x.startswith("- **Parar volta")]
    p4["text_en"] = [x for x in p4["text_en"] if not x.startswith("- **Stop goes back")]
    av = by["ajustes-avancado"]
    i_play, i_bat = av["text_pt"].index("### Para tocar sem falhas"), av["text_pt"].index("### Bateria do aparelho")
    j_play, j_bat = av["text_en"].index("### For playing without failures"), av["text_en"].index("### Device battery")
    p4["text_pt"] += ["Ajustes → Player e metrônomo → Para tocar sem falhas:"] + av["text_pt"][i_play + 1:i_bat]
    p4["text_en"] += ["Settings → Player and metronome → For playing without failures:"] + av["text_en"][j_play + 1:j_bat]
    bat_pt = [x for x in av["text_pt"][i_bat:] if not x.startswith("- **Tela apaga")]
    bat_en = [x for x in av["text_en"][j_bat:] if not x.startswith("- **Screen off")]
    av["text_pt"] = ["Ajustes → Aparelho → Avançado. São ajustes do próprio Android. Para mudá-los, o Stagebook precisa de uma permissão dada uma vez por um computador com o adb. Sem ela, os ajustes aparecem mas não mudam, e o último cartão da página mostra o comando e Copiar o comando. Alguns só existem em certos aparelhos e só aparecem neles.",
                     "Eles valem para o aparelho inteiro, não só para o Stagebook. O app grava de novo sempre que abre, porque o Android às vezes volta atrás sozinho. Por isso, mude por estas chaves: o que você mudar nos ajustes do Android volta na próxima abertura."] + bat_pt
    av["text_en"] = ["Settings → Device → Advanced. These are Android's own settings. To change them, Stagebook needs a permission given once from a computer with adb. Without it the settings show but don't change, and the last card on the page shows the command and Copy the command. Some only exist on certain devices and only show there.",
                     "They apply to the whole device, not just Stagebook. The app writes them again every time it opens, because Android sometimes goes back by itself. So change them with these switches: what you change in Android's settings comes back the next time it opens."] + bat_en
    av["level"] = 4
    put("ajustes-biblioteca", "Importar e backup", "Import and backup", 3,
        head_pt="Ajustes → Importar e backup. Apagar tudo fica no fim, como zona de perigo.", head_en="Settings → Import and backup. Erase everything is at the end, as a danger zone.")
    put("ajustes-sites", level=4,
        head_pt="Ajustes → Importar e backup → Sites de cifra.", head_en="Settings → Import and backup → Chord sites.")
    st = by["ajustes-sites"]
    st["text_pt"] = [x.replace("Em Só letra, o item se chama Sites de letra. Em Automático ele se chama Sites de importação e mostra as duas listas, a de cifra e a de letra.", "Em Só letra, o cartão se chama Sites de letra. Em Automático há os dois cartões, Sites de cifra e Sites de letra.") for x in st["text_pt"]]
    st["text_en"] = [x.replace("In Lyrics only, the item is called Lyrics sites. In Automatic it is called Import sites and shows both lists, chords and lyrics.", "In Lyrics only, the card is called Lyrics sites. In Automatic there are both cards, Chord sites and Lyrics sites.") for x in st["text_en"]]
    put("ajustes-backup", level=4)
    put("ajustes-apagar", level=4, head_pt="Ajustes → Importar e backup → Apagar tudo, a zona de perigo no fim do grupo.", head_en="Settings → Import and backup → Erase everything, the danger zone at the end of the group.")
    put("ajustes-banda", "Banda e computador", "Band and computer", 3,
        head_pt="Ajustes → Banda e computador. Some inteiro com o modo essencial.", head_en="Settings → Band and computer. It leaves entirely with essential mode.")
    put("ajustes-computador", level=4, head_pt="Ajustes → Banda e computador → No navegador.", head_en="Settings → Band and computer → In the browser.")
    head("ajustes-aparelho", "Aparelho", "Device",
         ["Ajustes → Aparelho. O Modo essencial, o Idioma e os ajustes do Android, que valem para o aparelho inteiro."],
         ["Settings → Device. Essential mode, Language and Android's own settings, which apply to the whole device."])
    put("ajustes-essencial", level=4, head_pt="Ajustes → Aparelho → Modo essencial.", head_en="Settings → Device → Essential mode.")
    head("ajustes-idioma", "Idioma", "Language",
         ["Ajustes → Aparelho → Idioma. Automático segue o aparelho, ou escolha português ou inglês. Para ajudar a traduzir, siga o [guia de tradução](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.pt-BR.md)."],
         ["Settings → Device → Language. Automatic follows the device, or pick Portuguese or English. To help translate, follow the [translation guide](https://github.com/julianoAffonso/stagebook/blob/main/docs/TRANSLATING.md)."],
         [Fig(("tablet_5_ajustes_essencial", "celular_5_ajustes_lingua", "Aparelho: modo essencial e idioma", "Device: essential mode and language"))])
    by["ajustes-idioma"]["level"] = 4
    so = by["ajustes-sobre"]
    so["text_pt"] = [x for x in so["text_pt"] if not x.startswith("- **Idioma:**")]
    so["text_en"] = [x for x in so["text_en"] if not x.startswith("- **Language:**")]
    so["figs"] = [Fig(("tablet_5_ajustes_atualizar", "celular_5_ajustes_sobre", "Ajustes → Sobre: ajuda e atualizar", "Settings → About: help and updating"))]
    order = ["ajustes-busca", "ajustes-palco", "ajustes-letra", "ajustes-acordes", "ajustes-titulos", "ajustes-pdf",
             "ajustes-virar", "ajustes-pagina", "ajustes-pedal", "ajustes-pedal-conectar", "ajustes-pedal-botoes", "ajustes-midi-saida", "ajustes-tela",
             "ajustes-acordes-grupo", "ajustes-diagramas", "ajustes-metronomo", "ajustes-player",
             "ajustes-biblioteca", "ajustes-sites", "ajustes-backup", "ajustes-apagar", "ajustes-banda", "ajustes-computador",
             "ajustes-aparelho", "ajustes-essencial", "ajustes-idioma", "ajustes-avancado", "ajustes-sobre"]
    assert set(order) == set(by), set(by) ^ set(order)
    chapter["subs"] = [by[i] for i in order]
    chapter["text_pt"] = ["A aba Ajustes do menu mostra os grupos e a busca. Cada grupo abre na própria página, em tela cheia como o palco, sem seta: a alça na borda, como no palco, ou o voltar do Android, traz o menu de novo. Os Ajustes têm sete grupos, na ordem em que você costuma mexer, e o Sobre à parte. Cada item vem com o caminho: Ajustes → grupo → cartão. Um resultado da busca abre a página do grupo com o item piscando. Nos cartões, o ⓘ ao lado do título explica o que eles fazem. Com o modo essencial, alguns grupos, cartões e opções somem até você religar. Para achar algo sem saber onde está, use a [busca](#ajustes-busca)."]
    chapter["text_en"] = ["The menu's Settings tab shows the groups and the search. Each group opens on its own page, full screen like the stage, with no arrow: the handle on the edge, as on the stage, or Android's back, brings the menu again. Settings has seven groups, in the order you usually reach for them, and About apart. Each item comes with its path: Settings → group → card. A search result opens its group's page with the item flashing. On the cards, the ⓘ next to the title explains what they do. With essential mode, some groups, cards and options disappear until you turn them back on. To find something without knowing where it is, use the [search](#ajustes-busca)."]

    pairs = [
        ("[Visual do palco](#ajustes-letra)", "[Letra e cifra](#ajustes-palco)"), ("[Stage look](#ajustes-letra)", "[Lyrics and chords](#ajustes-palco)"),
        ("[Visual do palco](#ajustes-pagina)", "[Virar a página](#ajustes-virar)"), ("[Stage look](#ajustes-pagina)", "[Turn the page](#ajustes-virar)"),
        ("Banda e transmissão", "Banda e computador"), ("Band and broadcast", "Band and computer"),
        ("Metrônomo e player", "Player e metrônomo"), ("Metronome and player", "Player and metronome"),
        ("Ajustes → Editar no computador", "Ajustes → Banda e computador → No navegador"),
        ("Settings → Edit on the computer", "Settings → Band and computer → In the browser"),
        ("Biblioteca e backup", "Importar e backup"), ("Library and backup", "Import and backup"),
        ("[Para tocar sem falhas](#ajustes-avancado)", "[Para tocar sem falhas](#ajustes-player)"),
        ("[For playing without failures](#ajustes-avancado)", "[For playing without failures](#ajustes-player)"),
        ("o cartão Para tocar sem falhas em Avançado", "o cartão Para tocar sem falhas em Player e metrônomo"),
        ("the For playing without failures card in Advanced", "the For playing without failures card in Player and metronome"),
        ("o MIDI da música em Pedal e MIDI", "a Saída MIDI em Virar a página"), ("the song MIDI in Pedal and MIDI", "the MIDI out in Turn the page"),
        ("Ajustes → Modo essencial", "Ajustes → Aparelho → Modo essencial"), ("Settings → Essential mode", "Settings → Device → Essential mode"),
    ]

    def fix(v):
        if isinstance(v, str):
            for a, b in pairs: v = v.replace(a, b)
            return v
        if isinstance(v, list): return [fix(x) for x in v]
        if isinstance(v, tuple): return tuple(fix(x) for x in v)
        if isinstance(v, dict): return {k: fix(x) for k, x in v.items()}
        return v

    SECTIONS[:] = [fix(c) for c in SECTIONS]


_regroup_settings()


UI = {
    "pt-BR": {"title": "Ajuda do Stagebook", "lead": "Como usar cada parte do app.", "contents": "Nesta página",
              "tablet": "Tablet", "phone": "Celular", "other": "English", "other_href": "../help/", "lang": "pt-BR",
              "home": "Stagebook — página inicial", "repo": "Código do Stagebook no GitHub",
              "search": "Buscar na ajuda", "none": "Nada encontrado."},
    "en": {"title": "Stagebook help", "lead": "How to use each part of the app.", "contents": "On this page",
           "tablet": "Tablet", "phone": "Phone", "other": "Português", "other_href": "../ajuda/", "lang": "en",
           "home": "Stagebook — home page", "repo": "Stagebook source code on GitHub",
           "search": "Search the help", "none": "Nothing found."},
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
/* the search: the results take the place of the contents while there is something typed */
nav.toc input { width:100%; margin: 0 0 10px; padding: 8px 12px; border-radius: 999px; border:1px solid var(--line); background:var(--raised);
                color:var(--fg); font: inherit; font-size:15px; }
nav.toc input:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
nav.toc.searching #links { display:none; }
#res { display:none; } nav.toc.searching #res { display:block; }
#res a { padding: 6px 8px; } #res a small { display:block; color:var(--muted); font-size:12px; }
#res a span { display:block; font-size:14px; overflow-wrap:anywhere; }
#res p { color:var(--muted); margin:4px 8px; font-size:14px; }
mark { background: var(--accent); color: var(--on-accent); border-radius: 4px; padding: 0 2px; }
.hit { outline: 2px solid var(--accent); outline-offset: 4px; border-radius: 6px; }
/* standing tablet or phone: the contents become a compact strip of links instead of a tall list */
@media (max-width: 800px) { .wrap { grid-template-columns: 1fr; } nav.toc { position: static; padding: 12px; }
  nav.toc { max-height: none; }
  nav.toc a, nav.toc a.sub { display:inline-block; background:var(--raised); margin: 0 4px 6px 0; padding: 4px 12px; border-radius: 999px; font-size: 14px; } }
"""

JS = r"""
document.querySelectorAll('figure[data-tablet][data-phone]').forEach(f => {
  const img = f.querySelector('img');
  f.querySelectorAll('.switch button').forEach(b => b.onclick = () => {
    const phone = b.dataset.d === 'phone';
    img.src = phone ? f.dataset.phone : f.dataset.tablet;
    f.classList.toggle('tall', phone);
    f.querySelectorAll('.switch button').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
  });
});

// search: every heading, paragraph, list item and caption of the page, matched without accents or case; all the words
// must be in it. A result jumps to its place (and lights it for a moment); the contents come back when the box empties
(() => {
  const q = document.getElementById('q'), res = document.getElementById('res'), nav = q.closest('nav');
  const norm = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const items = []; let head = '';
  document.querySelectorAll('main h2, main h3, main h4, main p, main li, main figcaption').forEach(el => {
    const isHead = /^H/.test(el.tagName), text = el.textContent.replace(/\s+/g, ' ').trim();
    if (!text) return;
    if (isHead) head = text;
    items.push({ el, text, n: norm(text), head: isHead ? '' : head, isHead });
  });
  const close = () => { q.value = ''; nav.classList.remove('searching'); res.replaceChildren(); };
  const show = el => { close(); el.scrollIntoView({ block: 'center' }); el.classList.add('hit'); setTimeout(() => el.classList.remove('hit'), 2200); };
  const snippet = (it, words) => {
    const out = document.createElement('span');
    const same = it.n.length === it.text.length;
    const at = Math.max(0, Math.min(...words.map(w => it.n.indexOf(w)).filter(i => i >= 0)));
    const from = same ? Math.max(0, at - 40) : 0;
    const piece = it.text.slice(from, from + 150), n = norm(piece);
    const marks = [];
    words.forEach(w => { for (let i = n.indexOf(w); i >= 0; i = n.indexOf(w, i + w.length)) marks.push([i, i + w.length]); });
    marks.sort((a, b) => a[0] - b[0]);
    let pos = 0;
    if (from > 0) out.append('… ');
    marks.forEach(([a, b]) => { if (a < pos) return; out.append(piece.slice(pos, a)); const m = document.createElement('mark'); m.textContent = piece.slice(a, b); out.append(m); pos = b; });
    out.append(piece.slice(pos) + (from + 150 < it.text.length ? ' …' : ''));
    return out;
  };
  const run = () => {
    const words = norm(q.value).split(/\s+/).filter(w => w.length > 1);
    res.replaceChildren();
    nav.classList.toggle('searching', q.value.trim().length > 0);
    if (!words.length) return;
    const hits = items.filter(it => words.every(w => it.n.includes(w))).sort((a, b) => b.isHead - a.isHead).slice(0, 40);
    if (!hits.length) { const p = document.createElement('p'); p.textContent = q.dataset.none; res.append(p); return; }
    hits.forEach(it => {
      const a = document.createElement('a'); a.href = '#';
      if (it.head) { const h = document.createElement('small'); h.textContent = it.head; a.append(h); }
      a.append(snippet(it, words));
      a.onclick = e => { e.preventDefault(); show(it.el); };
      res.append(a);
    });
  };
  q.addEventListener('input', run);
  q.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });
  document.addEventListener('keydown', e => { if (e.key === '/' && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)) { e.preventDefault(); q.focus(); } });
})();
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
<div class="wrap"><nav class="toc"><input id="q" type="search" placeholder="{ui['search']}" aria-label="{ui['search']}" data-none="{ui['none']}" autocomplete="off"><div id="res" role="region" aria-live="polite"></div><div id="links"><b>{ui['contents']}</b>{''.join(toc)}</div></nav><main>{''.join(body)}</main></div>
<script>{JS}</script>
</body></html>
"""
    (out / "index.html").write_text(page, encoding="utf-8")
    size = sum(f.stat().st_size for f in img_dir.iterdir()) / 1e6
    print(f"{lang}: {out.relative_to(ROOT)}/index.html, {len(list(img_dir.iterdir()))} imagens ({size:.1f} MB)")


if __name__ == "__main__":
    for lang in OUT:
        build(lang)
