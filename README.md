# Stagebook

Letras, cifras e faixas de acompanhamento num app só, para tocar ao vivo no Android.

> Em construção: este repositório nasceu em 24/10/2026 juntando dois apps de uso pessoal (letras com
> setlist e pedal, e um player de faixas para a USB interface). O roteiro está em
> `.claude/memoria/roteiro.md`.

## O que já faz
- Biblioteca de músicas, busca, setlists com ordem por arrastar.
- Palco em tela cheia, paginado, com pedal Bluetooth (teclado) e pedal MIDI (USB e Bluetooth), com o
  comando de cada botão escolhido nos ajustes.
- Importação de letras de sites pelo navegador do app.
- Setlist por QR code entre aparelhos.

## A caminho
- Cifras (formato do another app e acordes acima da letra) e perfil "só letra / letra e cifra".
- Player nativo por música: OGG multicanal, MP3, WAV; USB interface por USB e interfaces multicanal.
- Português, español, italiano e English.

## Build
`./gradlew assembleDebug` (Android 9+, `minSdk 28`). Testes: `./gradlew testDebugUnitTest` (Java 21).
O release é assinado por uma chave que não está no repositório.

## Instalar
Pelos [Releases](../../releases) deste repositório (o app avisa e instala as versões novas sozinho).
