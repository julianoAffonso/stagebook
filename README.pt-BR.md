# Stagebook

[English](README.md) · **Português (Brasil)**

[![Release](https://img.shields.io/github/v/release/julianoAffonso/stagebook?label=release)](../../releases/latest)
[![License: PolyForm Shield 1.0.0](https://img.shields.io/badge/license-PolyForm%20Shield%201.0.0-blue)](LICENSE.md)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)](../../releases/latest)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/julianoaffonso)

Letra, cifra e backing track num app só, para tocar ao vivo no Android. Sozinho ou com a banda.

## O que ele faz
- **Palco:** tela cheia, acesa, em páginas ou rolando. Vira com pedal Bluetooth ou MIDI (USB e Bluetooth), com toque
  ou com a rolagem automática. Cada botão do pedal pode fazer outra coisa.
- **Cifras:** acordes em cima da letra (ChordPro), transpor, capotraste, tablatura e o diagrama de cada acorde no
  violão, guitarra, ukulele, cavaquinho ou teclado, com som. Mostra também o campo harmônico do tom.
- **Setlists:** monte o show e arraste para mudar a ordem. Mande para quem está do lado por QR, para longe com 3
  palavras pela internet, ou para a banda toda no mesmo Wi-Fi.
- **Banda:** com o "Quem conduz", quem puxa o setlist troca de música e o palco de todo mundo acompanha. Antes de
  começar, o app avisa quem não tem alguma música.
- **Backing track:** OGG com quantos canais tiver, MP3 ou WAV, numa interface de áudio USB ou na saída do aparelho. Tem
  mixer, roteamento por saída, EQ e normalização.
- **Metrônomo**, importação de letras e cifras pelos sites, **Now Playing** para OBS e Resolume
  ([formato](docs/now-playing.pt-BR.md)), backup num arquivo `.stagebook` e um **modo essencial** que desliga o que você não
  vai usar.
- Sem conta, sem propaganda, sem rastreio. Suas músicas ficam no aparelho. O que vai para a banda ou pelas 3 palavras
  vai cifrado.

## Player pela USB
- Interface de áudio USB **class compliant** (USB Audio Class 2.0, funciona no computador sem driver).
- Que aceite **48 kHz**.
- Celular ou tablet com **USB OTG** e o cabo ou adaptador certo.

Interfaces só USB Audio 1.0, ou só de 44,1 kHz, ainda não funcionam. Sem interface, o player toca no fone, no
Bluetooth ou no alto-falante do aparelho.

## Instalar
Baixe o `stagebook.apk` nos [Releases](../../releases). Depois disso o app avisa quando sai versão nova e se atualiza.
Precisa de Android 9 ou mais novo.

## Apoie
O Stagebook é gratuito e sem propaganda. Se ele ajuda nos seus ensaios e shows, que tal me pagar um café no
[Buy Me a Coffee](https://buymeacoffee.com/julianoaffonso)? Qualquer valor ajuda a manter o app.

## Traduzir
O app sai em inglês e português do Brasil. Fala outro idioma e toca? O passo a passo está em
[docs/TRANSLATING.pt-BR.md](docs/TRANSLATING.pt-BR.md). Não precisa saber programar.

## Para quem programa
Kotlin, Jetpack Compose e um motor de áudio em C. `./gradlew assembleDebug` compila e
`./gradlew testDebugUnitTest` roda os testes (Java 21). As versões são publicadas pelo GitHub Actions. Todo mundo aqui segue o
[Código de Conduta](CODE_OF_CONDUCT.pt-BR.md).

## Licença
Código disponível sob a **PolyForm Shield 1.0.0** ([LICENSE.md](LICENSE.md)). Pode usar à vontade, até em show pago,
mudar e redistribuir. Só não vale usar o código para fazer um app concorrente.

`Required Notice: Copyright Stagebook (https://stagebook.app.br)`

Decodificadores de áudio de terceiros, em `app/src/main/cpp/third_party`, cada um com a licença no próprio arquivo:
stb_vorbis (domínio público ou MIT), dr_mp3 e dr_wav (domínio público ou MIT-0).
