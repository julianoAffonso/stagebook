<p align="center"><img src="logo.png" alt="Stagebook" width="64"></p>

# Traduzir o Stagebook

[English](TRANSLATING.md) · **Português (Brasil)**

O Stagebook é feito por músico para músico. Se você toca e fala outro idioma, a sua tradução põe o app no palco de
mais gente. Não precisa saber programar: é um arquivo de texto.

## Como fazer
1. Copie `app/src/main/res/values/strings.xml` (inglês, a base) para `app/src/main/res/values-<código>/strings.xml`.
   O código é o do idioma: `es` (espanhol), `fr` (francês), `de` (alemão). Para uma variante, `es-rMX`, `fr-rCA`.
2. Traduza só o texto entre as tags. Não mexa no `name="…"`, nos `%1$s` e `%1$d` (são o nome da música, um número…)
   nem no `\n` (quebra de linha).
3. Abra um pull request. Pode mandar pela metade: o idioma entra no app quando estiver completo, e outra pessoa pode
   continuar de onde você parou.

Sem GitHub? Abra uma issue com o arquivo anexado, a gente coloca no lugar.

## Como escrever
Pense em alguém da banda explicando no ensaio, não num manual.

- **Frase curta.** Uma ideia por frase. Ponto final no lugar de dois-pontos, travessão ou parênteses.
- **Palavra do dia a dia.** "Apagar", não "excluir permanentemente". Os termos que o músico já usa ficam: setlist,
  backing track, BPM, capo, cifra.
- **Fale com a pessoa.** "Você", direto, sem "por favor" e sem "o usuário".
- **Botão começa com verbo** e tem de uma a três palavras: "Salvar", "Ler QR", "Pedir à banda".
- **Explicação em até duas frases.** Se precisa de mais, o texto está tentando fazer coisa demais.
- **Nada de tom de propaganda** ("incrível", "poderoso", "experiência").
- **Não traduza nomes:** Stagebook, Now Playing, OBS, ChordPro.
- **Seções da letra** (Refrão, Verso, Ponte…) usam o nome que o músico daquele idioma escreve na cifra.

## Português do Brasil
O app é em **pt-BR** (`values-pt-rBR`). Sem "ecrã", "telemóvel" ou "utilizador". Português de Portugal seria outra
tradução, em `values-pt-rPT`.

## O editor no computador
Os textos que começam com `web_` são da página do editor web (Ajustes → Editar no computador). Ficam no mesmo
`strings.xml` e a página segue o idioma do app: traduzindo o arquivo, o editor sai traduzido junto.
