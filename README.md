# Stagebook

Letras, cifras e faixas de acompanhamento num app só, para tocar ao vivo no Android.

> Em construção: este repositório nasceu em 24/10/2026 juntando dois apps de uso pessoal (letras com
> setlist e pedal, e um player de faixas). O roteiro está em
> `.claude/memoria/roteiro.md`.

## O que já faz
- Biblioteca de músicas, busca, setlists com ordem por arrastar.
- Palco em tela cheia, paginado, com pedal Bluetooth (teclado) e pedal MIDI (USB e Bluetooth), com o
  comando de cada botão escolhido nos ajustes.
- Importação de letras de sites pelo navegador do app.
- Setlist por QR code entre aparelhos.

## A caminho
- Cifras (ChordPro e acordes acima da letra) e perfil "só letra / letra e cifra".
- Player nativo por música: OGG multicanal, MP3, WAV; qualquer interface de áudio USB (multicanal) ou a saída do aparelho.

## Pré-requisitos do player (saída USB)
- Interface de áudio USB **"class compliant"** (USB Audio Class 2.0): funciona no computador sem instalar driver.
- Que aceite **48 kHz**.
- Celular ou tablet com **USB OTG** e o cabo ou adaptador certo para ligar a interface.
- Ainda não funcionam: interfaces só USB Audio 1.0, ou só de 44,1 kHz.
- Sem interface USB, o player toca na saída do próprio aparelho (fone, Bluetooth ou alto-falante).
- Português, español, italiano e English.

## Build
`./gradlew assembleDebug` (Android 9+, `minSdk 28`). Testes: `./gradlew testDebugUnitTest` (Java 21).
O release é assinado por uma chave que não está no repositório.

## Instalar
Pelos [Releases](../../releases) deste repositório (o app avisa e instala as versões novas sozinho).

## Licença
Código disponível sob a **PolyForm Shield 1.0.0** ([LICENSE.md](LICENSE.md)): qualquer uso, inclusive em shows pagos,
mudanças e redistribuição; só não vale usar o código para fazer um produto concorrente.
`Required Notice: Copyright Stagebook (https://stagebook.app.br)`

Decodificadores de áudio de terceiros, em `app/src/main/cpp/third_party` (cada um com a licença no próprio arquivo):
stb_vorbis (domínio público ou MIT), dr_mp3 e dr_wav (domínio público ou MIT-0).
