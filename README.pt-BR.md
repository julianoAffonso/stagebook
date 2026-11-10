<p align="center"><img src="docs/logo.png" alt="Stagebook" width="120"></p>

<h1 align="center">Stagebook</h1>

[English](README.md) · **Português (Brasil)**

[![Release](https://img.shields.io/github/v/release/julianoAffonso/stagebook?label=release)](../../releases/latest)
[![License: PolyForm Shield 1.0.0](https://img.shields.io/badge/license-PolyForm%20Shield%201.0.0-blue)](LICENSE.md)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](../../releases/latest)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/julianoaffonso)

Letra, cifra e backing track num app só, para tocar ao vivo no Android. Sozinho ou com a banda.

**Ajuda, com imagens de cada tela:** [docs/ajuda](docs/ajuda/index.html)

## O que ele faz
- **Palco:** tela cheia, acesa, em páginas ou rolando. Vira com pedal Bluetooth ou MIDI (USB e Bluetooth), com toque
  ou com a rolagem automática. No modo Rolar, toque na metade de baixo ou arraste para cima para seguir, na de cima ou
  arraste para baixo para voltar. Cada botão do pedal pode fazer outra coisa.
- **Cifras:** acordes em cima da letra (ChordPro), transpor, capotraste, tablatura e o diagrama de cada acorde no
  violão, guitarra, ukulele, cavaquinho ou teclado, com som. Mostra também o campo harmônico do tom. Anotações na
  linha dos acordes (2x, Intro:, as casas de um acorde como B/F# 224442) ficam nela, e as casas mudam ao transpor.
  Toque num acorde, na letra ou na lista de acordes, para ver o diagrama dele.
- **Setlists:** monte o show e arraste para mudar a ordem; liste os mais recentes primeiro ou de A a Z. Mande para
  quem está do lado por QR, para longe com 3 palavras pela internet, ou para a banda toda no mesmo Wi-Fi.
- **Banda:** com o "Conduzir a banda", quem puxa o setlist troca de música e o palco de todo mundo acompanha. Antes de
  começar, o app avisa quem não tem alguma música.
- **Backing track:** OGG com quantos canais tiver, MP3 ou WAV, numa interface de áudio USB ou na saída do aparelho. Tem
  mixer, roteamento por saída, EQ e normalização.
- **Metrônomo**, importação de letras e cifras pelos sites, **Now Playing** para OBS e Resolume
  ([formato](docs/now-playing.pt-BR.md)), backup num arquivo `.stagebook` (toque num deles numa conversa, num download
  ou no gerenciador de arquivos e ele abre no app) e um **modo essencial** que desliga o que você não vai usar.
- Sem conta, sem propaganda, sem rastreio. Suas músicas ficam no aparelho. O que vai para a banda ou pelas 3 palavras
  vai cifrado.

## Player pela USB
- Interface de áudio USB **class compliant** (USB Audio Class 1.0 ou 2.0, funciona no computador sem driver).
- Que aceite **44,1 ou 48 kHz**.
- Celular ou tablet com **USB OTG** e o cabo ou adaptador certo.

Interfaces USB Audio 2.0 e USB Audio 1.0 funcionam. Sem interface, o player toca no fone, no Bluetooth ou no
alto-falante do aparelho.

Dois jeitos de tocar na interface:
- **Pelo motor do Stagebook (padrão):** fala direto com a interface.
- **Pelo Android:** Ajustes > Metrônomo e player > Tocar pela interface através do Android. Use só se a interface
  conecta e o som não sai ou sai picotado.

Cada interface guarda a sua escolha em cada aparelho: com a interface conectada, a chave vale só para ela. Se o som
falha seguido no motor, o app liga a chave para aquela interface e avisa; se o som não sai pelo Android, ele desliga de
novo. A sua escolha sempre vale mais.

Na primeira vez pelo motor, o Android pede permissão para usar a interface USB: toque em OK. Antes da primeira nota o
app confere o relógio da interface por cerca de 2 segundos (o palco pede para aguardar se demorar mais). Depois de 10
segundos sem tocar, o fluxo USB fecha para poupar bateria e abre de novo no play.

O player avisa quando a interface está carregando o aparelho (assim algumas interfaces falham no motor) e quando o cabo
USB está ligado mas a interface não responde. Os dois avisos dependem de o Android deixar o app ler a porta USB; onde
não deixa, eles nunca aparecem.

### Testado
Tudo passou na versão 0.1.900 do app. Passar quer dizer: uma música inteira ouvida limpa, pausar e retomar, e tirar e
pôr o cabo com a música tocando voltou sozinho. Pelo motor do Stagebook, salvo aviso.

| Aparelho | Mixer Behringer FLOW 8 (USB Audio 2.0, 4 saídas) | Placa Nanocore (USB Audio 1.0, 44,1 kHz) | Behringer UMC404HD (USB Audio 2.0, 4 saídas) | Mixer digital Behringer XR18 (USB Audio 2.0, 18 saídas) |
|---|---|---|---|---|
| TCL NXTPAPER 11 Plus (tablet, 8 núcleos, Android 14) | Passou | Passou | Passou (4) | Não testado |
| Chuwi Hi10 XPro (tablet simples, Android 13) | Passou, com nota (1) | Passou | Passou | Falhou (5) |
| Samsung Galaxy S26 Ultra (celular, Android 17) | Passou, com nota (2) | Passou | Passou | Passou |
| OnePlus 10 Pro (celular, Android 16) | Passou pelos dois caminhos (3) | Passou | Passou | Não testado |

1. O USB desse tablet perde leituras na entrada da interface, então o motor marca o ritmo da saída pelo relógio do
   sistema. O som sai limpo, com um soluço curto e raro a cada 15 a 60 s. É o limite do USB do tablet; acontece também
   pelo áudio do próprio Android.
2. A entrada da interface parava de responder depois de cerca de um segundo, e o motor marcava o ritmo sozinho. As
   versões mais novas começam com transferências curtas na entrada, e nesse celular ela continua respondendo.
3. Pelo áudio do Android e pelo motor. O motor pede a janela de permissão USB do Android na primeira vez.
4. Na versão 0.1.1011, ligada direto e alimentada pelo tablet: uma música inteira ouvida limpa pelo motor, no relógio da
   própria interface; parar e o cabo voltaram sozinhos. Nos outros aparelhos, nas versões 0.1.1038 a 0.1.1066.
5. Na versão 0.1.1066 o USB do tablet entregou só de 96 a 99,5% dos pacotes para essa mesa de 18 canais, e o som
   engasgou, pelos dois caminhos. É o limite do tablet: o app mede isso quando a interface abre e avisa que usar assim
   é por sua conta e risco.

Não testado: as células acima que dizem isso, outras interfaces e um fone USB simples (suportados, mas fora desta tabela).

## Quanto o app pesa
Medido em aparelhos de verdade, um minuto por tela (processador em % de **um** núcleo; os quatro têm 8 núcleos):

| Tela | TCL NXTPAPER 11 Plus | Galaxy S26 Ultra | Chuwi Hi10 XPro | OnePlus 10 Pro |
|---|---|---|---|---|
| Lista de músicas, parada | 0,4% · 112 MB | 0,1% · 132 MB | 0,1% · 68 MB | 0,1% · 32 MB |
| Palco, parado | 0,6% · 143 MB | 0,2% · 131 MB | 0,4% · 73 MB | 0,4% · 39 MB |
| Palco, virando página a cada 3 s | 3,9% · 132 MB | 5,4% · 126 MB | 5,1% · 74 MB | 14,7% · 38 MB |
| Palco, acompanhando a banda | — | 0,2% · 253 MB | 8,8% · 114 MB | — |
| Conduzindo a banda por 5 minutos (9 músicas, uma página a cada 2 s) | 5,0% · 194 MB | 0,8% · 186 MB (acompanhando) | 2,4% · 124 MB (acompanhando) | — |
| Mandando um setlist para a banda | 2,6% · 198 MB | 0,0% · 160 MB (recebendo) | — | — |
| Backing track numa interface USB (mixer USB Audio 2.0) | 5,1%¹ · 160 MB | 6,7% · 140 MB | 29,3% · 80 MB | 31,3% · 42 MB |
| Backing track numa interface USB (placa USB Audio 1.0) | 3,0%¹ · 101 MB | 3,7% · 86 MB | 16,9% · 83 MB | 22,2% · 41 MB |
| Player ligado, nada tocando (o fluxo USB fecha em 10 s) | 0,4% · 142 MB | 0,1% · 112 MB | 0,1% · 96 MB | 0,4% · 46 MB |
| Backing track tocando pela saída do aparelho | 17,1% · 147 MB | 3,9% · 140 MB | 13,9% · 71 MB | 18,9% · 44 MB |
| Ajustes da banda, parado | 0,2% · 175 MB | 0,2% · 145 MB | 0,3% · 75 MB | 0,6% · 41 MB |

Mesmo tocando uma faixa, o Stagebook usa menos de 4% do processador inteiro (o pior caso medido, 31,3% de um núcleo, é 3,9% de oito). O modo essencial quase não muda esses
números: o ganho dele é a tela mais simples.

¹ No TCL o Android mantém seis dos oito núcleos em frequência baixa mesmo com bateria boa (71% e 70% na medição), então o uso bruto fica inflado (19,5% e 10,0%). Estes dois números contam só os dois núcleos disponíveis, ponderados pela frequência, como um núcleo na velocidade máxima. Com o player ligado e uma interface conectada, mas nada tocando, o motor mantém o fluxo USB vivo com silêncio e gasta cerca de 19% de um núcleo no OnePlus.

## Usar com assistentes de IA (MCP)
O Stagebook tem um [servidor MCP](mcp/README.pt-BR.md) que deixa o Claude Code, o Claude Desktop, o Cursor, o VS Code, o
Gemini CLI, o Codex e outros assistentes de IA lerem e editarem a sua biblioteca: "monta um setlist de 15 músicas para
sábado", "passa as músicas deste artista meio tom abaixo", "confere os acordes desta música", "importa esta página". Ele
roda no seu computador e conversa com o tablet pelo Wi-Fi, por **Ajustes → Editar no computador**; você autoriza o
computador uma vez no tablet. Está no npm como [`stagebook-mcp`](https://www.npmjs.com/package/stagebook-mcp): com o [Node.js](https://nodejs.org) no
computador, o Claude Code se liga com `claude mcp add stagebook -- npx -y stagebook-mcp`. Configuração de cada
assistente: [mcp/README.pt-BR.md](mcp/README.pt-BR.md).

## Instalar
O Stagebook não está na Play Store; ele sai só por aqui, de graça. Precisa de Android 9 ou mais novo.

1. No celular ou tablet, abra a [versão mais nova](../../releases/latest) e baixe o `stagebook.apk`.
2. Abra o arquivo baixado. Na primeira vez o Android pede para permitir instalar apps dessa fonte (o navegador ou o
   app de arquivos): permita, volte e toque em **Instalar**. Se o Play Protect disser que não conhece o app, escolha
   instalar mesmo assim.
3. Pronto. Daí em diante o app avisa quando sai versão nova e se atualiza; na primeira atualização o Android pede a
   mesma permissão, agora para o Stagebook. No Android 12 ou mais novo, as seguintes entram sem pedir de novo e o app
   reinicia para terminar. No Android 11 ou mais antigo, o Android pede a confirmação a cada versão. Se o servidor
   falha por um instante, procurar e baixar tentam de novo sozinhos.

**Por que fora da Play Store?** O app importa letras e cifras de sites, e as regras da loja sobre conteúdo com
direito autoral deixam esse tipo de app sujeito a ser retirado de uma hora para outra. Além disso, ele se atualiza
direto por estes releases, o que a loja não permite. Assim as versões saem sem esperar revisão, e o código fica aberto
para quem quiser conferir.

## Apoie
O Stagebook é gratuito e sem propaganda. Se ele ajuda nos seus ensaios e shows, que tal me pagar um café no
[Buy Me a Coffee](https://buymeacoffee.com/julianoaffonso)? Qualquer valor ajuda a manter o app.

## Traduzir
O app sai em inglês e português do Brasil. Fala outro idioma e toca? O passo a passo está em
[docs/TRANSLATING.pt-BR.md](docs/TRANSLATING.pt-BR.md). Não precisa saber programar. Outros idiomas vêm da comunidade, por pull
request aqui no GitHub.

## Para quem programa
Kotlin, Jetpack Compose e um motor de áudio em C. `./gradlew assembleDebug` compila e
`./gradlew testDebugUnitTest` roda os testes (Java 21 e o NDK do Android). As versões são publicadas pelo GitHub
Actions. Todo mundo aqui segue o [Código de Conduta](CODE_OF_CONDUCT.pt-BR.md). Achou uma falha de segurança? Veja a [política de segurança](SECURITY.pt-BR.md).

### Pull requests
Os pull requests vão para a `main`. Eles passam por estas verificações:
- **Check:** compilação, testes de unidade e lint, e as novidades da versão (`tools/release_notes.py`).
- **Help:** uma mudança que a pessoa percebe vem com a ajuda nas duas línguas, uma tela que mudou com capturas novas
  (`tools/help_check.py`).
- **Privacy:** nada de link de sessão, credencial ou dado pessoal (`tools/privacy-check.sh`).
- **Engine simulator** (só quando o motor de áudio muda) e **MCP check** (só quando `mcp/` muda).

Cada commit que muda algo que a pessoa percebe termina com `Release-Note: English sentence · Frase em português`,
curta e com as palavras do músico; um que ninguém percebe termina com `Release-Note: none`. Quando a ajuda ou as
capturas não precisam mudar, diga isso numa linha própria: `Help: none` ou `Screens: none`. Quando um commit mais novo
muda de novo algo que um anterior já descreveu no mesmo pull request, ele leva `Release-Note-Replaces: <commit>`, e só
a frase dele entra na versão. O Check lista todas as frases do pull request, para ler contra o que ele faz no fim.

### Hotfix
Um bug numa versão publicada que não pode esperar (uma atualização que não instala, um travamento no palco) não espera
o trabalho da `dev`:
1. Crie a branch `hotfix/<nome>` a partir da `main` (a última versão) só com a correção e o teste dela. O commit leva
   um `Release-Note` que diz à pessoa o que fazer, se precisar (ele aparece na janela de atualização).
2. Abra um pull request para a `main`. A verificação obrigatória é o **Check** (uns 7 minutos); o simulador não é
   obrigatório.
3. Faça o merge e rode **Actions → Release** na `main` com "publish" marcado. A correção sai em uns 10 minutos.
4. Traga a `main` de volta para a `dev`, onde a mesma correção também entra.

Uma versão publicada com um bug que trava as pessoas (por exemplo, atualizar pelo app) sai das Releases, com a tag, para
ninguém novo baixar, e uma issue fixada nas duas línguas explica quem foi afetado e como sair. A versão seguinte conta as
novidades a partir da anterior, e ninguém perde nenhuma mudança.

### Simulador do motor
O `tools/engine-sim/run.sh` roda o motor de áudio num Mac com uma interface USB falsa e compara o som com a versão
validada numa mesa de verdade. Também testa quedas, os planos do relógio e carga. No Actions ele roda com faixas
sintéticas (as de verdade nunca vão para o repositório) e não é uma verificação obrigatória. Quando até a quarta
abertura é julgada ruidosa, o runner é marcado como barulhento e os quatro testes de plano A aparecem como pulados; os
de plano B rodam em qualquer lugar. Ele não substitui o teste na mesa.

### Avisos no Telegram
Quem mantém o projeto pode receber uma mensagem no Telegram quando uma versão ou o servidor MCP é publicado ou falha,
quando uma verificação falha num pull request ou na `main`, quando o site da ajuda cai, num alerta de segurança e quando outra pessoa abre
ou comenta uma issue, um pull request ou uma discussão (mais um resumo diário, só quando há novidade). O GitHub não
dispara workflows por alerta de segurança, então eles e o site da ajuda são conferidos pelas rodadas agendadas, três
vezes por dia, e não na hora em que acontecem. Para ligar num fork:
1. No Telegram, fale com o `@BotFather`, mande `/newbot` e copie o token. Mande uma mensagem qualquer ao bot novo.
2. Pegue o id do seu chat (por exemplo em `https://api.telegram.org/bot<token>/getUpdates`, em `message.chat.id`).
3. No repositório, Settings → Secrets and variables → Actions, crie os segredos `TELEGRAM_BOT_TOKEN` e
   `TELEGRAM_CHAT_ID`. Opcional: o segredo `SECURITY_ALERTS_TOKEN` (um token que lê os alertas do Dependabot) e a
   variável `SITE_URL` (o site da ajuda a vigiar).

Sem os segredos o workflow roda e não faz nada. Ele nunca baixa nem executa código de um pull request; títulos e
textos são só dados, enviados como texto simples.

### Ritmo do motor USB
- **Plano A:** seguir o relógio da entrada da interface (feedback implícito), para saída e interface nunca se afastarem.
- **Plano B:** marcar o ritmo da saída pelo relógio monotônico quando a entrada falta ou não é confiável.
- **A escolha é feita na abertura, antes de qualquer som:** a saída fica muda enquanto a entrada é lida em 8 janelas
  de 0,25 s (no máximo 3 s). Relógio errando mais de 0,3% na média, ou leituras tão ruidosas que um segundo pode errar
  mais de 0,6%, quer dizer plano B enquanto o fluxo ficar aberto. Relógio certo mas ruidoso fica no plano A, com uma
  tolerância ajustada ao ruído do aparelho.
- **O plano A só é deixado por falha de verdade:** 3 janelas de um segundo a mais de 1% do certo, a entrada 0,3 s sem
  responder, ou a contagem da entrada e o que foi enviado a 100 ms de distância. Um fluxo que saiu do plano A assim lê
  a entrada de novo a cada 60 s e volta depois de 5 s no tempo certo; um fluxo julgado fora na abertura fica no plano B.
- Os URBs de saída levam 8 pacotes (1 ms), até 32 na fila. Os URBs de entrada começam com 16 pacotes; se a conferência
  na abertura perde mais de 3% dos pacotes, passam para 128 e o relógio é conferido de novo.

## Licença
Código disponível sob a **PolyForm Shield 1.0.0** ([LICENSE.md](LICENSE.md)). Pode usar à vontade, até em show pago,
mudar e redistribuir. Só não vale usar o código para fazer um app concorrente.

`Required Notice: Copyright Stagebook (https://stagebook.app.br)`

Decodificadores de áudio de terceiros, em `app/src/main/cpp/third_party`, cada um com a licença no próprio arquivo:
stb_vorbis (domínio público ou MIT), dr_mp3 e dr_wav (domínio público ou MIT-0).
