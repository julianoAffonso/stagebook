# Traduzir o Stagebook · Translating Stagebook

[Português](#português) · [English](#english)

## Português

O Stagebook é feito por músico para músico. Se você toca e fala outra língua, a sua tradução põe o app no palco de
mais gente. Não precisa saber programar: é um arquivo de texto.

### Como fazer
1. Copie `app/src/main/res/values/strings.xml` (inglês, a base) para `app/src/main/res/values-<código>/strings.xml`.
   O código é o da língua: `es` (espanhol), `fr` (francês), `de` (alemão). Para uma variante, `es-rMX`, `fr-rCA`.
2. Traduza só o texto entre as tags. Não mexa no `name="…"`, nos `%1$s` e `%1$d` (são o nome da música, um número…)
   nem no `\n` (quebra de linha).
3. Abra um pull request. Pode mandar pela metade: a língua entra no app quando estiver completa, e outra pessoa pode
   continuar de onde você parou.

Sem GitHub? Abra uma issue com o arquivo anexado, a gente coloca no lugar.

### Como escrever
Pense em alguém da banda explicando no ensaio, não num manual.

- **Frase curta.** Uma ideia por frase. Ponto final no lugar de dois-pontos, travessão ou parênteses.
- **Palavra do dia a dia.** "Apagar", não "excluir permanentemente". Os termos que o músico já usa ficam: setlist,
  backing track, BPM, capo, cifra.
- **Fale com a pessoa.** "Você", direto, sem "por favor" e sem "o usuário".
- **Botão começa com verbo** e tem de uma a três palavras: "Salvar", "Ler QR", "Pedir à banda".
- **Explicação em até duas frases.** Se precisa de mais, o texto está tentando fazer coisa demais.
- **Nada de tom de propaganda** ("incrível", "poderoso", "experiência").
- **Não traduza nomes:** Stagebook, Now Playing, OBS, ChordPro.
- **Seções da letra** (Refrão, Verso, Ponte…) usam o nome que o músico daquela língua escreve na cifra.

### Português do Brasil
O app é em **pt-BR** (`values-pt-rBR`). Sem "ecrã", "telemóvel" ou "utilizador". Português de Portugal seria outra
tradução, em `values-pt-rPT`.

## English

Stagebook is made by a musician for musicians. If you play and speak another language, your translation puts the app
on more stages. No coding needed: it's one text file.

### How to
1. Copy `app/src/main/res/values/strings.xml` (English, the base) to `app/src/main/res/values-<code>/strings.xml`.
   The code is the language's: `es` (Spanish), `fr` (French), `de` (German). For a variant, `es-rMX`, `fr-rCA`.
2. Translate only the text between the tags. Leave `name="…"`, `%1$s` and `%1$d` (a song name, a number…) and `\n`
   (line break) as they are.
3. Open a pull request. Half done is fine: the language shows up in the app once it's complete, and someone else can
   pick up where you stopped.

No GitHub? Open an issue with the file attached and we'll put it in place.

### How to write
Think of a bandmate explaining at rehearsal, not a manual.

- **Short sentences.** One idea each. Use a period instead of colons, dashes or brackets.
- **Everyday words.** "Delete", not "permanently remove". Keep the words musicians already use: setlist, backing
  track, BPM, capo, chords.
- **Talk to the person.** "You", plain, no "please" and no "the user".
- **Buttons start with a verb** and have one to three words: "Save", "Scan QR", "Ask the band".
- **Explanations in two sentences at most.** If it needs more, the text is trying to do too much.
- **No ad talk** ("amazing", "powerful", "experience").
- **Don't translate names:** Stagebook, Now Playing, OBS, ChordPro.
- **Lyrics sections** (Chorus, Verse, Bridge…) use the word musicians in that language write on their charts.
